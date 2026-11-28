package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.block.DenyReason
import io.github.dreadnought0.feedclean.block.RejectReason
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** What started a [NotInterestedFlow] run. */
sealed interface NotInterestedTrigger {
    /** The user tapped Not interested on the overlay card. [token] must come from that click handler. */
    data class Manual(val token: UserIntentToken) : NotInterestedTrigger

    /**
     * Auto-skip is about to skip an organic Shop post. Rejected unless
     * [FeedCleanSettings.automatedNotInterestedAllowedBySettings] is true both in [settings] (the settings the auto-skip
     * decision was made with) and in the flow's current settings.
     */
    data class Automated(val settings: FeedCleanSettings) : NotInterestedTrigger
}

/**
 * Steps of the "Not interested" flow, in order. Reported through the progress callback when each
 * step starts. Persisted by name; never rename entries.
 */
enum class NotInterestedStep {
    /** Checking the shared rate limit. Nothing on screen has been touched yet. */
    CHECK_RATE_LIMIT,

    /** Checking that the expected feed video is on screen. */
    FIND_VIDEO,

    /** Long-pressing the video and waiting for its menu. */
    LONG_PRESS,

    /** Tapping "Not interested" in the menu. */
    TAP_NOT_INTERESTED,

    /** TikTok asked for a reason; choosing one per the user's setting (or dismissing the picker). */
    CHOOSE_REASON,

    /** Waiting for the feed to come back. */
    VERIFY,
}

/** Why the flow stopped. Persisted by name; never rename entries. */
enum class NotInterestedAbortReason {
    /** The current screen is not a feed video. Nothing was touched. */
    NOT_ON_FEED_VIDEO,

    /** The feed shows a different video than the one the request was for. Nothing was touched. */
    PAGE_CHANGED,

    /** The feed video has no long-press target. Nothing was touched. */
    NO_VIDEO_NODE,

    /** The screen driver reported the long-press could not be performed. */
    LONG_PRESS_FAILED,

    /** Nothing recognisable opened after the long-press. */
    MENU_DID_NOT_OPEN,

    /** A menu opened, but without a "Not interested" option. It was dismissed with one Back. */
    NOT_INTERESTED_MISSING,

    /** The screen driver reported the "Not interested" tap could not be performed. */
    TAP_FAILED,

    /** The screen driver threw an unexpected error. The screen was left as is. */
    ERROR,

    /**
     * The page is an in-feed LIVE preview. Nothing was touched: a long-press there can register as a tap and enter
     * the live room (and show the user as "joined"). Persisted by name; appended last.
     */
    LIVE_PREVIEW,
}

/** Outcome of one [NotInterestedFlow.run]. The caller logs the sighting (`user_action = not_interested`). */
sealed interface NotInterestedResult {
    /**
     * "Not interested" was tapped.
     *
     * @property verified the feed came back after the tap (and after the reason, if TikTok asked for one).
     *   False if it didn't within the step timeout; then at most one Back was pressed to close a menu that was
     *   still open.
     * @property reasonChosen label of the reason tapped, or null if none was asked for or the picker was
     *   dismissed ([io.github.dreadnought0.feedclean.settings.NotInterestedReason.SKIP_REASON]).
     */
    data class Done(val verified: Boolean, val reasonChosen: String?) : NotInterestedResult

    /**
     * Stopped at [step] because of [reason], having pressed Back at most once to close a menu it opened.
     *
     * @property detail diagnostic only, e.g. the kind of screen seen. Never contains screen content.
     */
    data class Aborted(
        val step: NotInterestedStep,
        val reason: NotInterestedAbortReason,
        val detail: String? = null,
    ) : NotInterestedResult {
        /** True when the stop happened at or after the "Not interested" tap, so it may have reached TikTok. */
        val mayHaveReachedTikTok: Boolean
            get() = step >= NotInterestedStep.TAP_NOT_INTERESTED
    }

    /** The shared rate limit denied the attempt. Nothing was touched; an automated caller should just skip. */
    data class RateLimited(val retryAfterMillis: Long, val reason: DenyReason) : NotInterestedResult

    /** Refused before doing anything (token problem, manual mode off, automation not armed). */
    data class Rejected(val reason: RejectReason) : NotInterestedResult
}

/** Timeouts for [NotInterestedFlow]. */
data class NotInterestedConfig(
    /** How long each step waits for its expected screen. */
    val stepTimeoutMillis: Long = 3_000,
    /** Snapshot polling interval while waiting. */
    val pollMillis: Long = 150,
    /** Maximum age of a manual trigger's [UserIntentToken] when the run starts. */
    val tokenMaxAgeMillis: Long = UserIntentToken.DEFAULT_MAX_AGE_MILLIS,
) {
    init {
        require(stepTimeoutMillis > 0 && pollMillis > 0 && tokenMaxAgeMillis > 0) { "timeouts must be positive" }
    }
}
