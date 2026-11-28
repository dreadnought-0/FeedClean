package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** Live facts auto-skip depends on, gathered by the runtime when a new page arrives. */
data class AutoSkipContext(
    /** TikTok is the app in front (ForegroundTracker). */
    val tiktokForeground: Boolean,
    /** The screen is on and interactive (PowerManager.isInteractive). */
    val screenInteractive: Boolean,
    /** An assisted action (block, Not interested, manual skip) is running. */
    val actionInProgress: Boolean = false,
    /** The overlay card is open: the user is interacting with FeedClean. */
    val cardOpen: Boolean = false,
    /** The snapshot was cut short by the traversal budget, so a keep-signal may be missing. */
    val incompleteRead: Boolean = false,
)

/**
 * Why auto-skip did not run for a page. Shown on the Debug screen and written BY NAME to the on-phone decision log
 * (`hold:<NAME>`), so never rename entries.
 */
enum class SkipHold {
    /** CurationPolicy said Keep. */
    KEEP,

    /** The kill-switch is engaged. */
    KILL_SWITCH,

    /** "Curate feed" is off. */
    NOT_ARMED,
    NOT_FOREGROUND,

    /**
     * Auto-skip is switched off for the player this page is in ([FeedVideo.feedContext]: For You, another Home tab,
     * search results, other), per Settings → Curate feed → "Where to auto-skip". The decision log records the context.
     */
    CONTEXT_OFF,
    SCREEN_OFF,
    INCOMPLETE_READ,
    ACTION_IN_PROGRESS,
    CARD_OPEN,

    /** The feed pager (the scroll target) wasn't found. */
    NO_PAGER,

    /** The consecutive-skip cap paused auto-skip until the user resumes. */
    PAUSED,

    /** This page was already auto-skipped once (the user scrolled back to it). */
    ALREADY_ATTEMPTED,
}

/** Result of [AutoSkipGate.evaluate]. */
sealed interface SkipGateResult {
    data class Go(val reason: SkipReason) : SkipGateResult

    data class Hold(val why: SkipHold) : SkipGateResult
}

/**
 * Every condition auto-skip needs (navigation only, foreground only, a player the user allowed
 * auto-skip in, armed, screen on) in one pure function, so each gate is a unit test. [CurationPolicy] decides
 * *whether a page would be skipped*; this decides *whether it may be skipped right now*.
 *
 * Players: the For You feed, the other Home tabs, the search-results player, and profile/hashtag/sound players all
 * use the same vertical `viewpager`, so the skip is the same single scroll; each context has its own toggle
 * ([FeedCleanSettings.autoSkipAllowedIn]).
 */
object AutoSkipGate {
    fun evaluate(
        decision: CurationDecision,
        page: FeedVideo,
        settings: FeedCleanSettings,
        context: AutoSkipContext,
        loopGuard: LoopGuard,
    ): SkipGateResult {
        val reason = (decision as? CurationDecision.Skip)?.reason ?: return hold(SkipHold.KEEP)
        return when {
            settings.killSwitchEngaged -> hold(SkipHold.KILL_SWITCH)
            !settings.autoSkipAllowedBySettings -> hold(SkipHold.NOT_ARMED)
            !context.tiktokForeground -> hold(SkipHold.NOT_FOREGROUND)
            !settings.autoSkipAllowedIn(page.feedContext) -> hold(SkipHold.CONTEXT_OFF)
            settings.autoSkipOnlyWhenScreenOn && !context.screenInteractive -> hold(SkipHold.SCREEN_OFF)
            context.incompleteRead -> hold(SkipHold.INCOMPLETE_READ)
            context.actionInProgress -> hold(SkipHold.ACTION_IN_PROGRESS)
            context.cardOpen -> hold(SkipHold.CARD_OPEN)
            page.pagerNode == null -> hold(SkipHold.NO_PAGER)
            loopGuard.isPaused -> hold(SkipHold.PAUSED)
            loopGuard.wasAttempted(page.pageSignature) -> hold(SkipHold.ALREADY_ATTEMPTED)
            else -> SkipGateResult.Go(reason)
        }
    }

    private fun hold(why: SkipHold) = SkipGateResult.Hold(why)
}
