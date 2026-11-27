package io.github.dreadnought0.feedclean.overlay

import android.content.Context
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.block.RateDecision
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.service.ActionStatus
import io.github.dreadnought0.feedclean.service.AssistedActions
import io.github.dreadnought0.feedclean.service.CardActionPersistence
import io.github.dreadnought0.feedclean.service.CardTarget
import io.github.dreadnought0.feedclean.service.ProcessedPage
import io.github.dreadnought0.feedclean.service.RuntimeLog
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

/**
 * The overlay card's state and button handlers, and the one assisted action that may run at a time.
 *
 * Every screen action here starts from a card button: Block, Not interested, and Skip receive a
 * [UserIntentToken] created in the button's click handler and hand it to [AssistedActions], whose executors
 * consume it. Nothing else calls those methods. Before an action drives TikTok the card is closed, so the
 * executor sees (and gesture-taps) TikTok, not our window.
 *
 * Main thread only.
 *
 * @param currentPage the last processed feed page (what the card shows when opened).
 * @param capture performs the "Capture screen" debug action and returns its result message.
 * @param highlightConfirm stop-at-dialog style: draws the ring over TikTok's Block button.
 * @param onChanged something the bubble shows changed (busy state, results).
 * @param onResumeAutoSkip the user tapped "Resume auto-skip".
 */
class CardController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val overlays: OverlayManager,
    private val actions: AssistedActions,
    private val persistence: CardActionPersistence,
    private val accounts: AccountRepository,
    private val rateLimiter: RateLimiter,
    private val settings: () -> FeedCleanSettings,
    private val loopGuardPaused: () -> Boolean,
    private val currentPage: () -> ProcessedPage?,
    private val capture: suspend () -> ActionMessage,
    private val highlightConfirm: suspend () -> Unit,
    private val onChanged: () -> Unit,
    private val onResumeAutoSkip: () -> Unit,
    private val log: RuntimeLog,
) : CardCallbacks {

    private var cardPage: ProcessedPage? = null
    private var stats = CardStats()
    private var manualTags: Set<String> = emptySet()
    private var rate: RateDecision? = null
    private var progress: CardProgress = CardProgress.Idle
    private var refreshJob: Job? = null
    private var actionJob: Job? = null

    /** True while an assisted action (block, Not interested, skip, capture) runs. */
    var isActionRunning: Boolean = false
        private set

    // ── Opening / closing ──────────────────────────────────────────────────────────────────────────────────

    fun toggle() {
        if (overlays.isCardShowing) close() else open()
    }

    /** Opens the card for the current page. */
    fun open() {
        val page = currentPage()
        if (page?.pageSignature != cardPage?.pageSignature && !isActionRunning) progress = CardProgress.Idle
        cardPage = page
        stats = CardStats()
        manualTags = emptySet()
        rate = null
        overlays.showCard(model())
        refreshJob?.cancel()
        refreshJob = scope.launch { refreshWhileOpen(page) }
    }

    /** Closes the card (actions keep running). */
    fun close() {
        refreshJob?.cancel()
        refreshJob = null
        overlays.hideCard()
    }

    /** The card was closed by an outside touch. */
    fun onClosedExternally() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Stops the running action, if any. Returns true if one was running. */
    fun stopAction(): Boolean {
        val job = actionJob ?: return false
        if (!job.isActive) return false
        log.log("action stop requested")
        job.cancel()
        return true
    }

    /** Runs while the card is open (cancelled on close): live tag chips, stats, and the rate-limit countdown. */
    private suspend fun refreshWhileOpen(page: ProcessedPage?) = coroutineScope {
        if (page != null) {
            launch {
                accounts.observeTags(page.accountId).collect { list ->
                    manualTags = list.filter { it.source == TagSource.MANUAL }.map { it.tag.name }.toSet()
                    refresh()
                }
            }
            stats = loadStats(page.accountId)
        }
        while (isActive) {
            rate = rateLimiter.check()
            refresh()
            delay(RATE_REFRESH_MILLIS)
        }
    }

    private suspend fun loadStats(accountId: Long): CardStats {
        val account = accounts.getAccount(accountId)
        val sightings = accounts.observeTimeline(accountId).first().count { it is TimelineEntry.SightingEntry }
        return CardStats(seenCount = sightings, firstSeenAtMillis = account?.firstSeenAt)
    }

    private fun model(): CardModel = CardModel.build(
        page = cardPage,
        settings = settings(),
        stats = stats,
        manualTags = manualTags,
        rateLimit = rate,
        loopGuardPaused = loopGuardPaused(),
        progress = progress,
    )

    /** Re-renders the open card. */
    fun refresh() {
        overlays.updateCard(model())
    }

    // ── Buttons ────────────────────────────────────────────────────────────────────────────────────────────

    override fun onBlock(token: UserIntentToken) {
        val target = actionTargetOrExplain(CardActionKind.BLOCK, liveAllowed = false) ?: return
        runAction(CardActionKind.BLOCK) {
            val result = actions.block(token, target) { step ->
                showProgress(CardActionKind.BLOCK, step.name)
                if (step == BlockStep.AWAITING_USER_CONFIRMATION) {
                    launch { highlightConfirm() }
                } else {
                    overlays.hideHighlight()
                }
            }
            overlays.hideHighlight()
            ActionMessages.forBlock(result)
        }
    }

    override fun onNotInterested(token: UserIntentToken) {
        val target = actionTargetOrExplain(CardActionKind.NOT_INTERESTED, liveAllowed = false) ?: return
        runAction(CardActionKind.NOT_INTERESTED) {
            val result = actions.notInterested(token, target) { step -> showProgress(CardActionKind.NOT_INTERESTED, step.name) }
            ActionMessages.forNotInterested(result)
        }
    }

    override fun onSkip(token: UserIntentToken) {
        val target = actionTargetOrExplain(CardActionKind.SKIP, liveAllowed = true) ?: return
        runAction(CardActionKind.SKIP) { ActionMessages.forSkip(actions.skip(token, target)) }
    }

    override fun onLogOnly() {
        val target = targetOrNull() ?: return
        scope.launch {
            persistence.persistLogOnly(target)
            finish(CardActionKind.LOG, ActionMessage(ActionMessage.Type.LOGGED, ok = true))
        }
    }

    override fun onFalsePositive() {
        val target = targetOrNull() ?: return
        scope.launch {
            persistence.persistFalsePositive(target)
            finish(CardActionKind.FALSE_POSITIVE, ActionMessage(ActionMessage.Type.FALSE_POSITIVE, ok = true))
        }
    }

    override fun onToggleTag(tag: String, enabled: Boolean) {
        val target = targetOrNull() ?: return
        scope.launch { persistence.setManualTag(target.accountId, tag, enabled) }
    }

    override fun onResumeAutoSkip() {
        onResumeAutoSkip.invoke()
        refresh()
    }

    override fun onCapture() {
        runAction(CardActionKind.CAPTURE) { capture() }
    }

    override fun onStop() {
        stopAction()
    }

    override fun onDismiss() {
        close()
    }

    private fun targetOrNull(): CardTarget? = cardPage?.let(CardTarget::from)

    /**
     * The card's post for an on-screen action, or null after telling the user why not. A tap on an action button
     * must never be silently ignored (otherwise Block seems to do nothing when the card isn't attached to a feed
     * post).
     */
    private fun actionTargetOrExplain(kind: CardActionKind, liveAllowed: Boolean): CardTarget? {
        val target = targetOrNull()
        if (target == null) {
            log.log("card ${kind.name.lowercase()} ignored: no feed post")
            finish(kind, ActionMessage(ActionMessage.Type.NO_POST, ok = false))
            return null
        }
        if (!liveAllowed && target.signals.isLivePreview) {
            log.log("card ${kind.name.lowercase()} ignored: LIVE preview")
            finish(kind, ActionMessage(ActionMessage.Type.LIVE_PREVIEW_UNSUPPORTED, ok = false))
            return null
        }
        return target
    }

    // ── Running one action at a time ───────────────────────────────────────────────────────────────────────

    private fun runAction(kind: CardActionKind, block: suspend CoroutineScope.() -> ActionMessage) {
        if (isActionRunning) {
            finish(kind, ActionMessage(ActionMessage.Type.ACTION_BUSY, ok = false))
            return
        }
        // Close the card first so the executor drives (and taps) TikTok, not our window.
        close()
        isActionRunning = true
        showProgress(kind, null)
        actionJob = scope.launch {
            try {
                delay(CARD_CLOSE_SETTLE_MILLIS)
                finish(kind, block())
            } catch (e: CancellationException) {
                finish(kind, ActionMessage(ActionMessage.Type.STOPPED_BY_USER, ok = false))
                throw e
            } catch (e: Exception) {
                log.log("action $kind failed ${e.javaClass.simpleName}")
                finish(kind, ActionMessage(ActionMessage.Type.REJECTED, e.javaClass.simpleName, ok = false))
            } finally {
                overlays.hideHighlight()
                isActionRunning = false
                onChanged()
            }
        }
        onChanged()
    }

    private fun showProgress(kind: CardActionKind, stepName: String?) {
        progress = CardProgress.Running(kind, stepName)
        ScreenStateHolder.publishAction(ActionStatus(kind.name.lowercase(), running = true, step = stepName, atMillis = now()))
        val text = context.getString(OverlayTexts.step(kind, stepName))
        overlays.showTransientHint(context.getString(R.string.overlay_hint_running, text), RUNNING_HINT_MILLIS)
        refresh()
    }

    private fun finish(kind: CardActionKind, message: ActionMessage) {
        progress = CardProgress.Finished(kind, message)
        val summary = message.type.name + (message.arg?.takeIf { kind != CardActionKind.BLOCK }?.let { " $it" } ?: "")
        ScreenStateHolder.publishAction(ActionStatus(kind.name.lowercase(), running = false, result = summary, atMillis = now()))
        ScreenStateHolder.addEvent("${LocalTime.now().format(TIME)} ${kind.name.lowercase()}: $summary")
        overlays.showTransientHint(OverlayTexts.message(context, message), RESULT_HINT_MILLIS)
        refresh()
    }

    /** Cancels everything (kill-switch, TikTok left, service stopping). */
    fun cancelAll() {
        stopAction()
        close()
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        /** Lets the card window disappear before the executor's first read/tap. */
        const val CARD_CLOSE_SETTLE_MILLIS = 200L
        const val RATE_REFRESH_MILLIS = 1_000L
        const val RUNNING_HINT_MILLIS = 15_000L
        const val RESULT_HINT_MILLIS = 4_000L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
