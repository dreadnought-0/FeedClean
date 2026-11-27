package io.github.dreadnought0.feedclean.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.service.GestureGuard
import io.github.dreadnought0.feedclean.service.RuntimeLog
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns FeedClean's overlay windows: the bubble, the card, the hint pill next to the bubble, the
 * stop-at-dialog highlight ring, and the skip-cover ([cover]). All are `TYPE_ACCESSIBILITY_OVERLAY` windows
 * added through the service's own WindowManager (no SYSTEM_ALERT_WINDOW), none is focusable, and only the bubble
 * and the card accept touches. Compose windows get an [OverlayLifecycleOwner] (RESUMED while shown, DESTROYED
 * when removed).
 *
 * Main thread only (except [passThrough], which switches to it). Decisions about *when* to show what live in
 * the runtime; this class only draws and reports touches to [listener].
 */
class OverlayManager(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val log: RuntimeLog,
    private val listener: Listener,
) : GestureGuard {

    /** Touches on FeedClean's own windows. */
    interface Listener {
        fun onBubbleTap()
        fun onBubbleLongPress()

        /** The user finished dragging the bubble to [position]. */
        fun onBubbleMoved(position: PxPoint)

        /** The card was closed by a touch outside it. */
        fun onCardDismissedByOutsideTouch()
    }

    private class Window(val root: View, val owner: OverlayLifecycleOwner?, val params: WindowManager.LayoutParams)

    private val wm: WindowManager = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    /** The skip-cover window (also used by the card's manual Skip). */
    val cover = SkipCoverOverlay(service, wm, log)

    private val bubbleLook = MutableStateFlow(BubbleLook())
    private val cardModel = MutableStateFlow<CardModel?>(null)
    private val hintText = MutableStateFlow<String?>(null)

    private var bubble: Window? = null
    private var card: Window? = null
    private var hint: Window? = null
    private var ring: Window? = null
    private var stickyHint: String? = null
    private var transientHintJob: Job? = null
    private var lastOutsideDismissAt = 0L

    /** Receives the card's button clicks. Set by the runtime before the first [showCard]. */
    var cardCallbacks: CardCallbacks? = null

    val isBubbleShowing: Boolean get() = bubble != null
    val isCardShowing: Boolean get() = card != null

    // ── Screen ─────────────────────────────────────────────────────────────────────────────────────────────

    /** The whole display in screen pixels (cover or inner screen, whichever is active). */
    fun screenBounds(): Bounds = runCatching { wm.maximumWindowMetrics.bounds }
        .map { Bounds(it.left, it.top, it.right, it.bottom) }
        .getOrElse {
            val dm = service.resources.displayMetrics
            Bounds(0, 0, dm.widthPixels, dm.heightPixels)
        }

    /** Display size changed (fold/unfold, rotation): keep the bubble on screen, close the card. */
    fun onScreenChanged() {
        hideCard()
        hideHighlight()
        val b = bubble ?: return
        val size = px(BUBBLE_SIZE_DP)
        val pos = OverlayGeometry.clampBubble(b.params.x, b.params.y, size, screenBounds())
        moveBubble(pos)
    }

    // ── Bubble ─────────────────────────────────────────────────────────────────────────────────────────────

    fun setBubbleLook(look: BubbleLook) {
        bubbleLook.value = look
    }

    /** Shows the bubble at the saved position (-1 = default: right edge, vertical middle), clamped to the screen. */
    fun showBubble(savedX: Int, savedY: Int) {
        if (bubble != null) return
        val size = px(BUBBLE_SIZE_DP)
        val pos = OverlayGeometry.resolveBubble(savedX, savedY, size, screenBounds(), px(BUBBLE_MARGIN_DP))
        val params = OverlayParams.bubble(pos.x, pos.y, size)
        val tracker = BubbleGestureTracker(ViewConfiguration.get(service).scaledTouchSlop)
        var dragStart = pos
        lateinit var root: OverlayRootLayout
        val longPress = Runnable {
            if (tracker.longPressElapsed() != null) {
                root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener.onBubbleLongPress()
            }
        }
        root = OverlayRootLayout(service, interceptAll = true) { ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.down(ev.rawX, ev.rawY)
                    dragStart = PxPoint(params.x, params.y)
                    main.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> tracker.move(ev.rawX, ev.rawY)?.let { drag ->
                    main.removeCallbacks(longPress)
                    moveBubble(OverlayGeometry.drag(dragStart, drag.dx, drag.dy, size, screenBounds()))
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    when (tracker.up()) {
                        BubbleGesture.Tap -> onBubbleTapped()
                        BubbleGesture.DragEnd -> listener.onBubbleMoved(PxPoint(params.x, params.y))
                        else -> Unit
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (tracker.cancel() == BubbleGesture.DragEnd) listener.onBubbleMoved(PxPoint(params.x, params.y))
                }
            }
        }
        val compose = ComposeView(service).apply {
            setContent {
                FeedCleanTheme {
                    val look by bubbleLook.collectAsState()
                    BubbleContent(look)
                }
            }
        }
        root.addView(compose, FrameLayout.LayoutParams(MATCH, MATCH))
        bubble = addWindow(root, params, compose = true)
        refreshHint()
    }

    fun hideBubble() {
        hideHint()
        removeWindow(bubble)
        bubble = null
    }

    private fun onBubbleTapped() {
        // The same tap that closed the card (outside touch) must not reopen it.
        if (SystemClock.uptimeMillis() - lastOutsideDismissAt < OUTSIDE_TAP_GUARD_MS) return
        listener.onBubbleTap()
    }

    private fun moveBubble(pos: PxPoint) {
        val b = bubble ?: return
        if (b.params.x == pos.x && b.params.y == pos.y) return
        b.params.x = pos.x
        b.params.y = pos.y
        update(b)
        placeHint()
    }

    private fun bubbleRect(): Bounds {
        val size = px(BUBBLE_SIZE_DP)
        val b = bubble?.params
        val pos = if (b != null) PxPoint(b.x, b.y) else OverlayGeometry.defaultBubble(screenBounds(), size, px(BUBBLE_MARGIN_DP))
        return Bounds(pos.x, pos.y, pos.x + size, pos.y + size)
    }

    // ── Card ───────────────────────────────────────────────────────────────────────────────────────────────

    /** Opens the card (or updates it if open). */
    fun showCard(model: CardModel) {
        cardModel.value = model
        if (card != null) return
        val screen = screenBounds()
        val margin = px(CARD_MARGIN_DP)
        val width = minOf(screen.width - 2 * margin, px(CARD_MAX_WIDTH_DP))
        val pos = OverlayGeometry.placeCard(bubbleRect(), width, px(CARD_ESTIMATED_HEIGHT_DP), screen, margin)
        val params = OverlayParams.card(pos.x, pos.y, width)
        val root = OverlayRootLayout(service, interceptAll = false) { ev ->
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                lastOutsideDismissAt = SystemClock.uptimeMillis()
                hideCard()
                listener.onCardDismissedByOutsideTouch()
            }
        }
        val maxHeight = (screen.height * CARD_MAX_HEIGHT_FRACTION / service.resources.displayMetrics.density).dp
        val proxy = CallbacksProxy()
        val compose = ComposeView(service).apply {
            setContent {
                FeedCleanTheme {
                    val current by cardModel.collectAsState()
                    current?.let { CardContent(it, proxy, maxHeight) }
                }
            }
        }
        root.addView(compose, FrameLayout.LayoutParams(MATCH, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val w = card ?: return@addOnLayoutChangeListener
            val p = OverlayGeometry.placeCard(bubbleRect(), w.params.width, v.height, screenBounds(), margin)
            if (p.x != w.params.x || p.y != w.params.y) {
                w.params.x = p.x
                w.params.y = p.y
                update(w)
            }
        }
        card = addWindow(root, params, compose = true)
    }

    /** Updates the open card's content (no-op when closed). */
    fun updateCard(model: CardModel) {
        if (card != null) cardModel.value = model
    }

    fun hideCard() {
        removeWindow(card)
        card = null
        cardModel.value = null
    }

    private inner class CallbacksProxy : CardCallbacks {
        private val target: CardCallbacks? get() = cardCallbacks
        override fun onBlock(token: UserIntentToken) { target?.onBlock(token) }
        override fun onNotInterested(token: UserIntentToken) { target?.onNotInterested(token) }
        override fun onSkip(token: UserIntentToken) { target?.onSkip(token) }
        override fun onLogOnly() { target?.onLogOnly() }
        override fun onFalsePositive() { target?.onFalsePositive() }
        override fun onToggleTag(tag: String, enabled: Boolean) { target?.onToggleTag(tag, enabled) }
        override fun onResumeAutoSkip() { target?.onResumeAutoSkip() }
        override fun onCapture() { target?.onCapture() }
        override fun onStop() { target?.onStop() }
        override fun onDismiss() { target?.onDismiss() }
    }

    // ── Hint pill ──────────────────────────────────────────────────────────────────────────────────────────

    /** A hint that stays until replaced or cleared (e.g. "Paused after 8 skips — tap to resume"). */
    fun setStickyHint(text: String?) {
        stickyHint = text
        if (transientHintJob?.isActive != true) refreshHint()
    }

    /** A hint shown for [durationMillis], then the sticky hint (if any) comes back. */
    fun showTransientHint(text: String, durationMillis: Long = DEFAULT_HINT_MILLIS) {
        transientHintJob?.cancel()
        showHintText(text)
        transientHintJob = scope.launch {
            delay(durationMillis)
            refreshHint()
        }
    }

    private fun refreshHint() {
        val text = stickyHint
        if (text == null) hideHint() else showHintText(text)
    }

    private fun showHintText(text: String) {
        if (bubble == null) return
        hintText.value = text
        if (hint != null) {
            placeHint()
            return
        }
        val rect = bubbleRect()
        val root = FrameLayout(service)
        val compose = ComposeView(service).apply {
            setContent {
                FeedCleanTheme {
                    val current by hintText.collectAsState()
                    current?.let { HintContent(it) }
                }
            }
        }
        root.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placeHint() }
        hint = addWindow(root, OverlayParams.passiveWrap(rect.left, rect.top), compose = true)
    }

    private fun placeHint() {
        val h = hint ?: return
        val p = OverlayGeometry.placeHint(bubbleRect(), h.root.width, h.root.height, screenBounds(), px(HINT_GAP_DP))
        if (p.x != h.params.x || p.y != h.params.y) {
            h.params.x = p.x
            h.params.y = p.y
            update(h)
        }
    }

    private fun hideHint() {
        removeWindow(hint)
        hint = null
        hintText.value = null
    }

    // ── Highlight ring (stop-at-dialog block style) ────────────────────────────────────────────────────────

    /** Draws a non-touchable ring around [bounds] (TikTok's own Block button). */
    fun showHighlight(bounds: Bounds) {
        hideHighlight()
        if (bounds.isEmpty) return
        val pad = px(RING_PAD_DP)
        val area = Bounds(bounds.left - pad, bounds.top - pad, bounds.right + pad, bounds.bottom + pad)
        ring = addWindow(HighlightRingView(service, px(RING_STROKE_DP).toFloat()), OverlayParams.passive(area), compose = false)
    }

    fun hideHighlight() {
        removeWindow(ring)
        ring = null
    }

    /** Removes every overlay window. */
    fun hideAll() {
        transientHintJob?.cancel()
        hideCard()
        hideHighlight()
        cover.hide()
        hideBubble()
    }

    // ── Gesture pass-through ───────────────────────────────────────────────────────────────────────────────

    override suspend fun <T> passThrough(block: suspend () -> T): T {
        val changed = withContext(Dispatchers.Main.immediate) { setTouchable(false) }
        // Window flag changes reach the input system with the next frame.
        if (changed) delay(PASS_THROUGH_SETTLE_MS)
        try {
            return block()
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) { setTouchable(true) }
        }
    }

    private fun setTouchable(touchable: Boolean): Boolean {
        var changed = false
        listOfNotNull(bubble, card).forEach { w ->
            OverlayParams.setTouchable(w.params, touchable)
            update(w)
            changed = true
        }
        return changed
    }

    // ── Window plumbing ────────────────────────────────────────────────────────────────────────────────────

    private fun addWindow(root: View, params: WindowManager.LayoutParams, compose: Boolean): Window? {
        val owner = if (compose) OverlayLifecycleOwner().also { it.attach(root) } else null
        return try {
            wm.addView(root, params)
            Window(root, owner, params)
        } catch (e: RuntimeException) {
            log.log("overlay add failed ${e.javaClass.simpleName}")
            owner?.destroy()
            null
        }
    }

    private fun removeWindow(w: Window?) {
        w ?: return
        try {
            wm.removeViewImmediate(w.root)
        } catch (e: RuntimeException) {
            log.log("overlay remove failed ${e.javaClass.simpleName}")
        }
        w.owner?.destroy()
    }

    private fun update(w: Window) {
        try {
            wm.updateViewLayout(w.root, w.params)
        } catch (e: RuntimeException) {
            log.log("overlay update failed ${e.javaClass.simpleName}")
        }
    }

    private fun px(dp: Int): Int = (dp * service.resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        const val BUBBLE_MARGIN_DP = 6
        const val CARD_MARGIN_DP = 8
        const val CARD_MAX_WIDTH_DP = 360
        const val CARD_ESTIMATED_HEIGHT_DP = 320
        const val CARD_MAX_HEIGHT_FRACTION = 0.75f
        const val HINT_GAP_DP = 6
        const val RING_PAD_DP = 6
        const val RING_STROKE_DP = 3
        const val OUTSIDE_TAP_GUARD_MS = 400L
        const val PASS_THROUGH_SETTLE_MS = 64L
        const val DEFAULT_HINT_MILLIS = 2_500L
    }
}
