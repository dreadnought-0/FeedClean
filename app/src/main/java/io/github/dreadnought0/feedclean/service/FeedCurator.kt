package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.curate.AutoSkipContext
import io.github.dreadnought0.feedclean.curate.AutoSkipController
import io.github.dreadnought0.feedclean.curate.AutoSkipGate
import io.github.dreadnought0.feedclean.curate.AutoSkipOutcome
import io.github.dreadnought0.feedclean.curate.AutoSkipRequest
import io.github.dreadnought0.feedclean.curate.LoopGuard
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.SkipCancel
import io.github.dreadnought0.feedclean.curate.SkipGateResult
import io.github.dreadnought0.feedclean.curate.SkipHold
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.Profile
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-snapshot curation logic: new-page detection → [FeedPagePipeline] → [AutoSkipGate] →
 * [AutoSkipController] → persistence of the outcome and the session count. Pure: no Android types.
 *
 * **Watch window.** A page that wasn't skipped on its first read stays *watched* for [watchWindowMillis] (~3 s) from
 * that read. TikTok often slides the "Ad" tag or the product card in ~1 s late, and a pager can be missing from the
 * first read of a settling page, so each later read of the SAME page (same signature, or a caption/sound refinement
 * of it) is checked:
 *  - a detection signal appeared ([DetectionRules.grew]) → the sighting is updated (or logged, if the page had none),
 *    the policy re-decides, and the gate is re-evaluated;
 *  - the policy said skip and the gate held only for a transient reason ([TRANSIENT_HOLDS]) → the gate is re-evaluated.
 * If the gate now says Go, the page is skipped (once: the loop guard still refuses a signature it tried). The watch
 * ends when the page changes, the window expires, the page was skipped, the user opens another screen (profile, menu,
 * sheet, dialog), or TikTok leaves the foreground. A page is attributed, logged as a new sighting, and counted in the
 * session at most once, on its first read; a kept page counts for the loop guard when its watch ends.
 *
 * Call [onSnapshot] from ONE coroutine, in order. While an auto-skip runs, [onSnapshot] suspends, so the next
 * page is only looked at once the skip has finished (consecutive Shop posts are skipped one after another).
 * [cancelActiveSkip] (kill-switch, TikTok leaving the foreground) stops a running skip at once.
 *
 * Every first read and every watch re-evaluation that changed something goes to [decisionLog] (enums only).
 *
 * Detection can never start an account action from here: the only screen actions reachable are
 * [AutoSkipController]'s (one scroll; automated Not-interested only when the user armed it).
 */
class FeedCurator(
    private val pipeline: FeedPagePipeline,
    private val autoSkip: AutoSkipController,
    private val loopGuard: LoopGuard,
    private val sightings: SightingRepository,
    private val session: SessionTracker,
    private val settings: () -> FeedCleanSettings,
    private val context: () -> AutoSkipContext,
    private val listener: Listener = Listener.NONE,
    private val log: RuntimeLog = RuntimeLog.NONE,
    private val decisionLog: DecisionLog = DecisionLog.NONE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val watchWindowMillis: Long = WATCH_WINDOW_MILLIS,
) {
    /** Observes what the curator did, for the overlay and the Debug screen. Called on the curator's coroutine. */
    interface Listener {
        /** A new feed page was processed (first read). */
        fun onPage(page: ProcessedPage, gate: SkipGateResult) = Unit

        /** The watched page's detection grew on a later read (new signals, sighting, decision). */
        fun onPageUpdated(page: ProcessedPage, gate: SkipGateResult) = Unit

        fun onAutoSkip(page: ProcessedPage, outcome: AutoSkipOutcome) = Unit

        companion object {
            val NONE: Listener = object : Listener {}
        }
    }

    private val pages = PageTracker()
    private var lastProfileHandle: String? = null

    /**
     * The page on screen that wasn't skipped on its first read, watched until [watchWindowMillis] after it.
     *
     * @property page what is known about it so far (merged signals, latest read, sighting id, decision).
     * @property recordSignature the first read's signature, which its sightings are keyed by.
     * @property gate the latest gate result.
     */
    private class Watch(
        var page: ProcessedPage,
        val recordSignature: String,
        val firstReadAtMillis: Long,
        var gate: SkipGateResult,
    )

    private var watch: Watch? = null

    @Volatile
    private var activeSkip: Deferred<AutoSkipOutcome>? = null

    /** True while an auto-skip is running. */
    val isSkipping: Boolean
        get() = activeSkip?.isActive == true

    /** Stops a running auto-skip (it drops its cover and taps nothing more). Returns true if one was running. */
    fun cancelActiveSkip(): Boolean {
        val job = activeSkip ?: return false
        if (!job.isActive) return false
        job.cancel()
        return true
    }

    /** Forgets the current page, so the page on screen is processed again when TikTok comes back. */
    fun reset() {
        pages.reset()
        lastProfileHandle = null
        endWatch()
    }

    /**
     * Handles one parsed snapshot.
     *
     * @param incompleteRead the read was cut short by the traversal budget; such feed pages are not processed.
     * @return the processed page if [snapshot] was a new feed page, or a later read that changed the watched page's
     *   decision inputs (late signal, retried transient hold); else null.
     */
    suspend fun onSnapshot(snapshot: ScreenSnapshot, incompleteRead: Boolean = false): ProcessedPage? = when (snapshot) {
        is FeedVideo -> onFeed(snapshot, incompleteRead)
        is Profile -> {
            endWatch()
            onProfile(snapshot)
            null
        }
        // Transition frames and unrecognised screens: the page may still be settling, keep watching it.
        is ScreenSnapshot.Unknown -> null
        // Menus, sheets, dialogs: the user is doing something with the page; never skip it from under them later.
        else -> {
            endWatch()
            null
        }
    }

    private suspend fun onFeed(feed: FeedVideo, incompleteRead: Boolean): ProcessedPage? {
        if (incompleteRead) {
            log.log("pipeline skip: incomplete read")
            return null
        }
        return when (pages.classify(feed)) {
            PageTracker.Change.NEW -> onNewPage(feed)
            PageTracker.Change.SAME, PageTracker.Change.REFINED -> onWatchedRead(feed)
            PageTracker.Change.INCOMPLETE -> null
        }
    }

    private suspend fun onNewPage(feed: FeedVideo): ProcessedPage {
        endWatch()
        lastProfileHandle = null
        val s = settings()
        val processed = pipeline.process(feed, s, session.currentId)
        val gate = AutoSkipGate.evaluate(processed.decision, processed.page, s, context(), loopGuard)
        val kinds = DetectionRules.detectedKinds(processed.signals, s)
        log.log(
            "page new ctx=${processed.page.feedContext} detected=$kinds " +
                "decision=${DecisionLabels.decision(processed.decision)} gate=${DecisionLabels.gate(gate)} " +
                "logged=${processed.sightingId != null} blockedMatch=${processed.blockedMatch}",
        )
        listener.onPage(processed, gate)

        if (gate !is SkipGateResult.Go) {
            watch = Watch(processed, processed.pageSignature, clock(), gate)
            session.recordPage(processed.signals, skipped = false)
            decisionLog.record(entry(DecisionEvent.PAGE, processed, kinds, gate, outcome = null, atMillis = processed.processedAtMillis))
            return processed
        }
        val outcome = runSkip(AutoSkipRequest(processed.page, gate.reason, processed.accountId, s))
        afterSkip(processed, processed.pageSignature, gate.reason, outcome)
        session.recordPage(processed.signals, skipped = outcome is AutoSkipOutcome.Skipped)
        decisionLog.record(entry(DecisionEvent.PAGE, processed, kinds, gate, outcome, atMillis = processed.processedAtMillis))
        listener.onAutoSkip(processed, outcome)
        return processed
    }

    /** A later read of the watched page. See the class docs. */
    private suspend fun onWatchedRead(feed: FeedVideo): ProcessedPage? {
        val w = watch ?: return null
        val now = clock()
        val sinceFirstRead = now - w.firstReadAtMillis
        if (sinceFirstRead > watchWindowMillis) {
            endWatch()
            return null
        }
        val s = settings()
        val grown = pipeline.refine(w.page, feed, s, session.currentId, w.recordSignature)
        val retry = grown == null && w.page.decision is CurationDecision.Skip && w.gate.isTransientHold()
        if (grown == null && !retry) return null

        val current = grown?.page ?: w.page.copy(page = feed.copy(signals = w.page.signals))
        val gate = AutoSkipGate.evaluate(current.decision, current.page, s, context(), loopGuard)
        if (grown == null && gate.isTransientHold()) return null // still settling: keep waiting
        w.page = current
        w.gate = gate

        val trigger = if (grown != null) WatchTrigger.SIGNALS_GREW else WatchTrigger.TRANSIENT_HOLD
        val kinds = DetectionRules.detectedKinds(current.signals, s)
        log.log(
            "page watch dt=$sinceFirstRead why=$trigger ctx=${current.page.feedContext} detected=$kinds " +
                "new=${grown?.newKinds.orEmpty()} decision=${DecisionLabels.decision(current.decision)} " +
                "gate=${DecisionLabels.gate(gate)} logged=${current.sightingId != null}",
        )
        if (grown != null) listener.onPageUpdated(current, gate)

        if (gate !is SkipGateResult.Go) {
            decisionLog.record(watchEntry(current, kinds, grown, sinceFirstRead, trigger, gate, outcome = null, now))
            return current
        }
        watch = null // One skip per page, whatever its outcome (the loop guard remembers the attempt too).
        val outcome = runSkip(AutoSkipRequest(current.page, gate.reason, current.accountId, s))
        afterSkip(current, w.recordSignature, gate.reason, outcome)
        decisionLog.record(watchEntry(current, kinds, grown, sinceFirstRead, trigger, gate, outcome, now))
        listener.onAutoSkip(current, outcome)
        return current
    }

    /** The watched page was shown without being skipped: the loop guard's run of consecutive skips is over. */
    private fun endWatch() {
        if (watch == null) return
        watch = null
        loopGuard.onPageKept()
    }

    private fun SkipGateResult.isTransientHold(): Boolean = this is SkipGateResult.Hold && why in TRANSIENT_HOLDS

    private suspend fun onProfile(profile: Profile) {
        val handle = profile.handle ?: return
        if (handle == lastProfileHandle) return
        lastProfileHandle = handle
        if (pipeline.recordProfile(profile) != null) log.log("profile shop category recorded")
    }

    private suspend fun runSkip(request: AutoSkipRequest): AutoSkipOutcome = coroutineScope {
        val job = async { autoSkip.skip(request) }
        activeSkip = job
        try {
            job.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive() // We were cancelled ourselves: propagate.
            log.log("autoskip interrupted")
            AutoSkipOutcome.Cancelled(SkipCancel.INTERRUPTED, null)
        } finally {
            activeSkip = null
        }
    }

    private suspend fun afterSkip(page: ProcessedPage, recordSignature: String, reason: SkipReason, outcome: AutoSkipOutcome) {
        if (outcome !is AutoSkipOutcome.Skipped) loopGuard.onPageKept()
        persistSkip(page, recordSignature, reason, outcome)
    }

    /**
     * Records what happened to the page: NOT_INTERESTED when automated Not-interested went through (it is the
     * stronger signal), else AUTO_SKIP when it was skipped. Cancelled skips leave the sighting as it was.
     * [recordSignature] is the page's first-read signature, so this lands on the sighting logged then (dedupe).
     */
    private suspend fun persistSkip(page: ProcessedPage, recordSignature: String, reason: SkipReason, outcome: AutoSkipOutcome) {
        val ni = outcome.notInterested
        val (action, verified) = when {
            ni is NotInterestedResult.Done -> UserAction.NOT_INTERESTED to ni.verified
            outcome is AutoSkipOutcome.Skipped -> UserAction.AUTO_SKIP to outcome.advanced
            ni is NotInterestedResult.Aborted && ni.mayHaveReachedTikTok -> UserAction.NOT_INTERESTED to false
            else -> return
        }
        val id = sightings.recordSighting(
            accountId = page.accountId,
            pageSignature = recordSignature,
            signals = page.signals,
            source = SightingSource.OVERLAY,
            userAction = action,
            skipReason = reason,
            nowMillis = clock(),
            sessionId = session.currentId,
        ).id
        sightings.setUserAction(id, action, verified)
    }

    private fun entry(
        event: DecisionEvent,
        page: ProcessedPage,
        kinds: List<DetectionRules.Kind>,
        gate: SkipGateResult,
        outcome: AutoSkipOutcome?,
        atMillis: Long,
    ) = DecisionLogEntry(
        atMillis = atMillis,
        event = event,
        context = page.page.feedContext,
        kinds = kinds,
        decision = DecisionLabels.decision(page.decision),
        gate = DecisionLabels.gate(gate),
        outcome = outcome?.let(DecisionLabels::outcome),
        notInterested = outcome?.notInterested?.let(DecisionLabels::notInterested),
        logged = page.sightingId != null,
    )

    private fun watchEntry(
        page: ProcessedPage,
        kinds: List<DetectionRules.Kind>,
        grown: Refinement?,
        sinceFirstRead: Long,
        trigger: WatchTrigger,
        gate: SkipGateResult,
        outcome: AutoSkipOutcome?,
        atMillis: Long,
    ) = entry(DecisionEvent.WATCH, page, kinds, gate, outcome, atMillis).copy(
        newKinds = grown?.newKinds,
        sinceFirstReadMillis = sinceFirstRead,
        trigger = trigger,
    )

    companion object {
        /** How long after a page's first read later reads of it are still evaluated (late labels, settling pager). */
        const val WATCH_WINDOW_MILLIS = 3_000L

        /** Holds that can clear within a moment on the same page. */
        val TRANSIENT_HOLDS = setOf(SkipHold.NO_PAGER)
    }
}
