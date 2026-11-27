package io.github.dreadnought0.feedclean.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import io.github.dreadnought0.feedclean.detect.ScreenParser
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.UiNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** One read of the screen: the parsed snapshot plus how the read went. */
data class CapturedScreen(
    val snapshot: ScreenSnapshot,
    /** The traversal budget ran out: the tree is incomplete. Never act on such a read. */
    val truncated: Boolean,
    val nodeCount: Int,
    val tookMillis: Long,
)

/**
 * [ScreenDriver] on top of the accessibility service: every read and action goes through what is on screen
 * (accessibility tree, accessibility actions, gestures) and nothing else (no TikTok internals or private APIs).
 *
 * Threading: reads and accessibility actions are blocking IPC, so they run on [ipc] (a single-threaded
 * dispatcher, so parses never overlap); gestures are dispatched on the main thread and awaited through their
 * GestureResultCallback. Every method is a suspend function safe to call from any thread.
 *
 * Every action is logged (tag "FeedClean") by kind, method, view-id name, and result: never by text.
 *
 * @param parser the active ScreenParser, or null while selectors are still loading (snapshots are then Unknown).
 * @param targetPackage TikTok's package from the selector config: only its windows are read.
 */
class A11yScreenDriver internal constructor(
    private val service: AccessibilityService,
    private val windows: WindowSource,
    private val parser: () -> ScreenParser?,
    private val targetPackage: () -> String?,
    private val gestureGuard: GestureGuard,
    private val log: RuntimeLog,
    private val ipc: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : ScreenDriver {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Reads and parses all interactive windows. */
    suspend fun capture(): CapturedScreen = withContext(ipc) {
        val started = SystemClock.uptimeMillis()
        val now = System.currentTimeMillis()
        val p = parser() ?: return@withContext CapturedScreen(
            ScreenSnapshot.Unknown(now, note = "Selectors not loaded yet"), truncated = false, nodeCount = 0, tookMillis = 0,
        )
        val budget = TraversalBudget()
        val snapshot = try {
            p.parse(windows.roots(budget, targetPackage()), now)
        } catch (e: RuntimeException) {
            log.log("snapshot failed ${e.javaClass.simpleName}")
            ScreenSnapshot.Unknown(now, note = "Read failed (${e.javaClass.simpleName})")
        }
        CapturedScreen(snapshot, budget.exhausted, budget.nodeCount, SystemClock.uptimeMillis() - started)
    }

    /**
     * Window roots of TikTok only (fully expanded within a fresh budget), for the in-app screen capture.
     * The caller serialises them with a redactor; nothing is parsed or logged here.
     */
    suspend fun readTargetRoots(): Pair<List<UiNode>, Boolean> = withContext(ipc) {
        val budget = TraversalBudget()
        val roots = windows.roots(budget, targetPackage())
        // Force expansion inside the budget while we are on the IPC thread.
        roots.forEach { root -> expand(root, budget) }
        roots to budget.exhausted
    }

    private fun expand(root: UiNode, budget: TraversalBudget) {
        val stack = ArrayDeque<UiNode>()
        stack.addLast(root)
        while (stack.isNotEmpty() && !budget.exhausted) {
            stack.removeLast().children.forEach(stack::addLast)
        }
    }

    override suspend fun snapshot(): ScreenSnapshot = capture().snapshot

    override suspend fun click(node: UiNode): Boolean {
        val target = node as? A11yUiNode ?: return false.also { log.log("click rejected: foreign node") }
        val plan = withContext(ipc) { planClick(target) } ?: return false.also { log.log("click stale id=${target.viewIdName}") }
        return when (val p = plan.first) {
            is ClickPlan.PerformClick -> {
                val actor = plan.second[p.level]
                val ok = withContext(ipc) { actor.info.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                log.log("click via=ACTION_CLICK level=${p.level} id=${target.viewIdName} ok=$ok")
                if (ok) {
                    true
                } else {
                    // The node said it was clickable but refused: tap it like a finger would.
                    val b = withContext(ipc) { target.info.screenBounds() }
                    !b.isEmpty && tap(b.centerX, b.centerY)
                }
            }
            is ClickPlan.GestureTap -> {
                val ok = tapGesture(p.x, p.y, TAP_MILLIS)
                log.log("click via=gesture id=${target.viewIdName} ok=$ok")
                ok
            }
            is ClickPlan.Impossible -> false.also { log.log("click impossible id=${target.viewIdName} (${p.why})") }
        }
    }

    /** Refreshes the target and up to [ClickPlanner.MAX_ANCESTOR_HOPS] ancestors; null if the target is gone. */
    private fun planClick(target: A11yUiNode): Pair<ClickPlan, List<A11yUiNode>>? {
        if (!target.info.refresh()) return null
        val chain = ArrayList<A11yUiNode>()
        chain += target
        var p = target.parent
        while (p != null && chain.size <= ClickPlanner.MAX_ANCESTOR_HOPS) {
            chain += p
            p = p.parent
        }
        val candidates = chain.mapIndexed { i, n ->
            val fresh = i == 0 || n.info.refresh()
            ClickCandidate(
                isClickable = fresh && n.info.isClickable,
                isVisibleToUser = fresh && n.info.isVisibleToUser,
                bounds = n.info.screenBounds(),
            )
        }
        return ClickPlanner.plan(candidates.first(), candidates.drop(1)) to chain
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        val ok = tapGesture(x, y, TAP_MILLIS)
        log.log("tap ok=$ok")
        return ok
    }

    override suspend fun longPress(node: UiNode): Boolean {
        val target = node as? A11yUiNode ?: return false.also { log.log("longPress rejected: foreign node") }
        val state = withContext(ipc) {
            if (!target.info.refresh()) {
                null
            } else {
                val viaAction = target.info.isLongClickable && target.info.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                Triple(viaAction, target.info.isVisibleToUser, target.info.screenBounds())
            }
        } ?: return false.also { log.log("longPress stale id=${target.viewIdName}") }
        val (viaAction, visible, bounds) = state
        if (viaAction) {
            log.log("longPress via=ACTION_LONG_CLICK id=${target.viewIdName} ok=true")
            return true
        }
        if (!visible || bounds.isEmpty) return false.also { log.log("longPress impossible id=${target.viewIdName}") }
        val ok = tapGesture(bounds.centerX, bounds.centerY, LONG_PRESS_MILLIS)
        log.log("longPress via=gesture id=${target.viewIdName} ok=$ok")
        return ok
    }

    override suspend fun scrollForward(node: UiNode): Boolean {
        val target = node as? A11yUiNode ?: return false.also { log.log("scroll rejected: foreign node") }
        val ok = withContext(ipc) {
            target.info.refresh() && target.info.isScrollable &&
                target.info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        log.log("scrollForward id=${target.viewIdName} ok=$ok")
        return ok
    }

    override suspend fun back(): Boolean {
        val ok = withContext(ipc) { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        log.log("back ok=$ok")
        return ok
    }

    override suspend fun awaitSnapshot(
        timeoutMillis: Long,
        pollMillis: Long,
        predicate: (ScreenSnapshot) -> Boolean,
    ): ScreenSnapshot? = pollForSnapshot(::snapshot, timeoutMillis, pollMillis, SystemClock::uptimeMillis, predicate)

    /** One-stroke gesture at ([x], [y]) held for [durationMillis]; awaits the system's callback. */
    private suspend fun tapGesture(x: Int, y: Int, durationMillis: Long): Boolean {
        if (x < 0 || y < 0) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMillis))
            .build()
        return gestureGuard.passThrough {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val callback = object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    }
                    if (!service.dispatchGesture(gesture, callback, mainHandler) && cont.isActive) cont.resume(false)
                }
            }
        }
    }

    private companion object {
        const val TAP_MILLIS = 60L

        /** TikTok opens the long-press menu after ~1 s of holding; 800 ms is past the system timeout. */
        const val LONG_PRESS_MILLIS = 800L
    }
}
