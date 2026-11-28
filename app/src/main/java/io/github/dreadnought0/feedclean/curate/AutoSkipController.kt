package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.service.ScreenDriver
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Skips one flagged feed page. **Navigation only**: the only screen actions it can issue are one
 * `ACTION_SCROLL_FORWARD` on the feed pager per page, plus — only when the user armed automated "Not interested"
 * and the page is organic Shop — one [NotInterestedFlow] run (rate-limited, verified). It never blocks, follows,
 * likes, comments, opens a profile, or taps a product.
 *
 * Sequence:
 * ```
 * refuse if attempted before / paused / not armed
 * → raise cover (if enabled)                      ┐ failsafe: cover removed after coverFailsafeMillis
 * → [organic Shop + automation armed] Not interested
 *      Done and TikTok already moved on → done, no scroll
 * → wait skipSettleDelayMs → re-read the screen
 *      not a feed page / page changed / its context no longer allowed / disarmed → cancel, no scroll
 * → ONE scrollForward(pager) → await a different page (≤ advanceTimeoutMillis)
 * → drop cover                                    ┘ (always, also on error or cancellation)
 * ```
 * The caller must already have passed [AutoSkipGate]; this re-checks what can change while it runs.
 * Cancelling the calling coroutine (kill-switch) stops it at once and still drops the cover.
 *
 * Pure: depends only on [ScreenDriver], [SkipCover], [LoopGuard], and coroutine time.
 *
 * @param settings live settings, re-read before the scroll.
 */
class AutoSkipController(
    private val driver: ScreenDriver,
    private val cover: SkipCover,
    private val loopGuard: LoopGuard,
    private val settings: () -> FeedCleanSettings,
    private val notInterested: NotInterestedRunner,
    private val log: (String) -> Unit = {},
    private val config: AutoSkipConfig = AutoSkipConfig(),
) {

    /** Skips [request]'s page once. See the class docs. */
    suspend fun skip(request: AutoSkipRequest): AutoSkipOutcome {
        val signature = request.page.pageSignature
        refusal(request)?.let {
            log("autoskip refused $it")
            return AutoSkipOutcome.Refused(it)
        }
        // Remember before acting: even a failed attempt is never retried for this page.
        loopGuard.markAttempt(signature)
        val decided = request.settings.sanitized()
        val withCover = decided.skipCoverEnabled && !request.page.pageBounds.isEmpty
        log("autoskip start reason=${request.reason} cover=$withCover")

        return coroutineScope {
            var failsafe: Job? = null
            try {
                if (withCover) {
                    cover.show(request.page.pageBounds, decided.coverStyle, request.reason)
                    failsafe = launch {
                        delay(config.coverFailsafeMillis)
                        log("autoskip cover failsafe")
                        cover.hide()
                    }
                }
                val outcome = runSteps(request, decided)
                if (outcome is AutoSkipOutcome.Skipped) {
                    outcome.copy(pausedNow = loopGuard.onAutoSkipped(decided.consecutiveSkipCap))
                } else {
                    outcome
                }.also { log("autoskip done ${describe(it)}") }
            } finally {
                failsafe?.cancel()
                if (withCover) withContext(NonCancellable) { cover.hide() }
            }
        }
    }

    private fun refusal(request: AutoSkipRequest): SkipHold? = when {
        !request.settings.autoSkipAllowedBySettings || !settings().autoSkipAllowedBySettings ->
            if (settings().killSwitchEngaged || request.settings.killSwitchEngaged) SkipHold.KILL_SWITCH else SkipHold.NOT_ARMED
        loopGuard.isPaused -> SkipHold.PAUSED
        loopGuard.wasAttempted(request.page.pageSignature) -> SkipHold.ALREADY_ATTEMPTED
        else -> null
    }

    private suspend fun runSteps(request: AutoSkipRequest, decided: FeedCleanSettings): AutoSkipOutcome {
        val signature = request.page.pageSignature
        var ni: NotInterestedResult? = null

        if (request.reason == SkipReason.ORGANIC_SHOP && decided.automatedNotInterestedAllowedBySettings) {
            ni = notInterested.run(NotInterestedTrigger.Automated(decided), request.accountId, signature)
            log("autoskip notInterested ${ni.javaClass.simpleName}")
            if (ni is NotInterestedResult.Done) {
                val now = driver.snapshot()
                if (now is FeedVideo && now.pageSignature != signature) {
                    // TikTok removed the post itself: never scroll on top of that.
                    return AutoSkipOutcome.Skipped(scrolled = false, advanced = true, notInterested = ni)
                }
                if (now !is FeedVideo) return AutoSkipOutcome.Cancelled(SkipCancel.LEFT_FEED, ni)
            }
            // RateLimited / Aborted / Rejected / Done-without-advance: fall through to a plain skip.
        }

        delay(decided.skipSettleDelayMs.toLong())

        val current = driver.snapshot()
        cancelReason(current, signature)?.let { why ->
            if (why == SkipCancel.USER_SCROLLED && ni is NotInterestedResult.Done) {
                // TikTok moved on after "Not interested", just a little later than the first check.
                return AutoSkipOutcome.Skipped(scrolled = false, advanced = true, notInterested = ni)
            }
            return AutoSkipOutcome.Cancelled(why, ni)
        }
        val pager = (current as FeedVideo).pagerNode ?: return AutoSkipOutcome.Cancelled(SkipCancel.NO_PAGER, ni)

        if (!driver.scrollForward(pager)) return AutoSkipOutcome.Cancelled(SkipCancel.SCROLL_FAILED, ni)
        val moved = driver.awaitSnapshot(config.advanceTimeoutMillis, config.pollMillis) {
            it is FeedVideo && it.pageSignature != signature
        }
        return AutoSkipOutcome.Skipped(scrolled = true, advanced = moved != null, notInterested = ni)
    }

    /**
     * What, on the freshly read screen, forbids the scroll. Null = go ahead. The scroll needs the SAME page, still
     * in a player context the user allows auto-skip in (live settings).
     */
    private fun cancelReason(current: ScreenSnapshot, signature: String): SkipCancel? {
        val live = settings()
        return when {
            live.killSwitchEngaged -> SkipCancel.KILL_SWITCH
            !live.autoSkipAllowedBySettings -> SkipCancel.DISARMED
            current !is FeedVideo -> SkipCancel.LEFT_FEED
            current.pageSignature != signature -> SkipCancel.USER_SCROLLED
            !live.autoSkipAllowedIn(current.feedContext) -> SkipCancel.CONTEXT_OFF
            else -> null
        }
    }

    private fun describe(outcome: AutoSkipOutcome): String = when (outcome) {
        is AutoSkipOutcome.Skipped ->
            "skipped scrolled=${outcome.scrolled} advanced=${outcome.advanced} paused=${outcome.pausedNow}"
        is AutoSkipOutcome.Cancelled -> "cancelled ${outcome.why}"
        is AutoSkipOutcome.Refused -> "refused ${outcome.why}"
    }
}
