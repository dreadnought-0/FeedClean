package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * The skip-cover: a non-touchable window that hides the video region while a skip runs.
 * Implementations must be idempotent and must never block the user's touches.
 */
interface SkipCover {
    /** Shows (or moves) the cover over [bounds]. [reason] is null for a manual skip from the card. */
    fun show(bounds: Bounds, style: CoverStyle, reason: SkipReason?)

    /** Removes the cover. Safe to call when it isn't showing. */
    fun hide()

    companion object {
        /** No cover (tests, cover disabled). */
        val NONE: SkipCover = object : SkipCover {
            override fun show(bounds: Bounds, style: CoverStyle, reason: SkipReason?) = Unit
            override fun hide() = Unit
        }
    }
}

/** Runs the automated "Not interested" for the page auto-skip is about to skip. Production: NotInterestedFlow. */
fun interface NotInterestedRunner {
    suspend fun run(trigger: NotInterestedTrigger, accountId: Long?, expectedPageSignature: String?): NotInterestedResult
}

/**
 * One auto-skip request.
 *
 * @property page the page the decision was made for.
 * @property settings the settings the decision was made with (re-checked live before scrolling).
 */
data class AutoSkipRequest(
    val page: FeedVideo,
    val reason: SkipReason,
    val accountId: Long?,
    val settings: FeedCleanSettings,
)

/** Timing for [AutoSkipController]. */
data class AutoSkipConfig(
    /** How long to wait for the pager to show a different page after the scroll. */
    val advanceTimeoutMillis: Long = 1_500,
    val pollMillis: Long = 100,
    /** The cover is removed after this long no matter what. */
    val coverFailsafeMillis: Long = 2_500,
) {
    init {
        require(advanceTimeoutMillis > 0 && pollMillis > 0 && coverFailsafeMillis > 0) { "timeouts must be positive" }
    }
}

/** Why an auto-skip stopped before scrolling. Written BY NAME to the decision log (`cancelled:<NAME>`). */
enum class SkipCancel {
    /** The page changed during the settle wait: the user scrolled themselves (or switched tab/player). */
    USER_SCROLLED,

    /** The screen is no longer a feed page (menu, profile, sheet, TikTok gone). */
    LEFT_FEED,

    /** Auto-skip was switched off for the page's player context mid-flow (Settings → Where to auto-skip). */
    CONTEXT_OFF,

    /** The kill-switch was engaged mid-flow. */
    KILL_SWITCH,

    /** "Curate feed" was switched off mid-flow. */
    DISARMED,

    /** No pager to scroll on the re-read screen. */
    NO_PAGER,

    /** The scroll action itself failed. */
    SCROLL_FAILED,

    /** Stopped from outside: kill-switch, TikTok left the foreground, or the service stopped. */
    INTERRUPTED,
}

/** Result of [AutoSkipController.skip]. */
sealed interface AutoSkipOutcome {
    /** Automated Not-interested result, if it ran. */
    val notInterested: NotInterestedResult?

    /**
     * The page was skipped.
     *
     * @property scrolled one ACTION_SCROLL_FORWARD was issued (false when TikTok itself moved on after
     *   "Not interested").
     * @property advanced a different page was seen afterwards.
     * @property pausedNow this skip reached the consecutive cap and paused auto-skip.
     */
    data class Skipped(
        val scrolled: Boolean,
        val advanced: Boolean,
        override val notInterested: NotInterestedResult?,
        val pausedNow: Boolean = false,
    ) : AutoSkipOutcome

    /** Nothing was scrolled. */
    data class Cancelled(val why: SkipCancel, override val notInterested: NotInterestedResult?) : AutoSkipOutcome

    /** Refused up front (already attempted, paused, not armed). Nothing was shown or touched. */
    data class Refused(val why: SkipHold) : AutoSkipOutcome {
        override val notInterested: NotInterestedResult? get() = null
    }
}
