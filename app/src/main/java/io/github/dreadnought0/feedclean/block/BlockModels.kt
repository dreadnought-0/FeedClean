package io.github.dreadnought0.feedclean.block

/**
 * The account the user asked to block, as shown on the overlay card.
 *
 * At least one of [expectedDisplayName] or [expectedHandle] is required: before anything that can block is
 * tapped, the executor checks that the profile it opened is this account.
 */
data class BlockTarget(
    /** Local `accounts.id` if the account is known. Recorded with the attempt in the action log. */
    val accountId: Long?,
    /** Display name from the feed (a provisional identity). Compared using the executor's `namesMatch`. */
    val expectedDisplayName: String?,
    /** Known @handle (with or without "@"), if any. Must equal the profile's handle (case-insensitive). */
    val expectedHandle: String?,
)

/**
 * Steps of the assisted block flow, in flow order except for entries appended later (see
 * [DISMISS_OVERLAY]). Reported through the progress callback when each step starts, and carried by
 * [BlockResult.Aborted].
 *
 * Persisted by name (`block_events.step_reached`); never rename entries. Append new entries at the end, so don't
 * compare ordinals to mean "later in the flow": use [isAfterDialogCheck].
 */
enum class BlockStep {
    /** Checking the shared rate limit. Nothing on screen has been touched yet. */
    CHECK_RATE_LIMIT,

    /** Checking the feed video and tapping the creator's avatar (or name). */
    OPEN_PROFILE,

    /** Waiting for the profile, checking it is the expected account, and learning its @handle. */
    READ_HANDLE,

    /** Tapping the profile's Share button and waiting for the share sheet. */
    OPEN_SHARE_SHEET,

    /** Tapping Block in the share sheet. */
    TAP_BLOCK,

    /** Checking that TikTok's confirmation dialog names the same @handle. */
    CHECK_DIALOG,

    /** Stop-at-dialog style only: waiting for the user to tap TikTok's own Block or Cancel. */
    AWAITING_USER_CONFIRMATION,

    /** One-tap style only: tapping Block in TikTok's dialog. */
    CONFIRM,

    /** Waiting for the profile to show the account as blocked. */
    VERIFY,

    /** Going back to the feed. */
    RETURN,

    /**
     * Closing a sheet that covers the profile (TikTok's product sheet on a Shop advertiser's profile), at most once
     * per run: one tap on its outside-touch scrim, or one Back. Happens between READ_HANDLE and OPEN_SHARE_SHEET
     * (appended here only to keep persisted names and older entries stable).
     */
    DISMISS_OVERLAY,
    ;

    /** True for steps after TikTok's dialog was checked, i.e. once Block may already have been confirmed. */
    val isAfterDialogCheck: Boolean
        get() = this == AWAITING_USER_CONFIRMATION || this == CONFIRM || this == VERIFY || this == RETURN
}

/** Why the block flow stopped. Persisted by name (`block_events.failure_reason`); never rename entries. */
enum class BlockAbortReason {
    /** The current screen is not a feed video. Nothing was tapped. */
    NOT_ON_FEED_VIDEO,

    /** The feed now shows a different creator than the card (the feed probably scrolled). Nothing was tapped. */
    FEED_SHOWS_DIFFERENT_ACCOUNT,

    /** No avatar or name to tap on the feed video (or only a LIVE avatar). Nothing was tapped. */
    NO_PROFILE_BUTTON,

    /** The screen driver reported that a tap could not be performed. */
    TAP_FAILED,

    /** The profile didn't appear in time. */
    PROFILE_DID_NOT_OPEN,

    /** A profile appeared but its @handle (or display name) couldn't be read. */
    PROFILE_UNREADABLE,

    /** The profile's display name doesn't match the expected one. */
    PROFILE_NAME_MISMATCH,

    /** The profile's @handle doesn't match the expected one. */
    PROFILE_HANDLE_MISMATCH,

    /** The profile says it is blocked, but "You blocked …" names a different handle. */
    BLOCKED_MESSAGE_MISMATCH,

    /** The profile has no Share button (or the profile was no longer showing when Share was about to be tapped). */
    NO_SHARE_BUTTON,

    /** The share sheet didn't appear in time. */
    SHARE_SHEET_DID_NOT_OPEN,

    /** The share sheet appeared without a Block option. */
    SHARE_SHEET_HAS_NO_BLOCK,

    /** TikTok's confirmation dialog didn't appear in time. */
    BLOCK_DIALOG_DID_NOT_OPEN,

    /** The confirmation dialog appeared but its @handle couldn't be read. Cancel was tapped. */
    DIALOG_UNREADABLE,

    /** The confirmation dialog names a different @handle. Cancel was tapped. */
    DIALOG_HANDLE_MISMATCH,

    /** The confirmation dialog has no Block button. Cancel was tapped. */
    DIALOG_HAS_NO_CONFIRM,

    /** Block was tapped, but the profile never showed the account as blocked. */
    BLOCK_NOT_VERIFIED,

    /** The screen driver threw an unexpected error. The screen was left as is. */
    ERROR,

    /**
     * A sheet covering the profile (TikTok's product sheet) didn't close after the run's single dismissal, came
     * back, the dismissal couldn't be performed, or it covered something other than the expected profile. Reported
     * at [BlockStep.DISMISS_OVERLAY]. Share was not tapped, and the sheet was left for the user.
     */
    OVERLAY_NOT_DISMISSED,
}

/**
 * Outcome of one [BlockFlowExecutor.run]. The caller persists it (block event, account status, learned handle);
 * the executor itself never touches the database.
 */
sealed interface BlockResult {
    /**
     * The profile showed the account as blocked by me, with a matching handle.
     *
     * @property alreadyBlocked the account was already blocked when its profile opened; nothing but the avatar
     *   (and Back) was tapped.
     * @property returnedToFeed the feed was showing when the executor finished.
     */
    data class Blocked(
        val handle: String,
        val displayName: String?,
        val alreadyBlocked: Boolean,
        val returnedToFeed: Boolean,
    ) : BlockResult

    /**
     * The flow stopped at [step] because of [reason], and backed out of the screens it had opened as far as was
     * safe. It never retries.
     *
     * @property handleLearned the target's @handle if READ_HANDLE had already confirmed the profile is the
     *   expected account; null otherwise (a mismatched profile's handle is never reported).
     * @property returnedToFeed the feed was showing when the executor finished. If false, the user may need to
     *   press Back themselves.
     * @property detail diagnostic only, e.g. the kind of screen seen at the time. Never contains screen content.
     */
    data class Aborted(
        val step: BlockStep,
        val reason: BlockAbortReason,
        val handleLearned: String?,
        val returnedToFeed: Boolean,
        val detail: String? = null,
    ) : BlockResult {
        /** True when the stop happened after the dialog check, so a block may have gone through unverified. */
        val mayHaveBlocked: Boolean
            get() = step.isAfterDialogCheck
    }

    /** The shared rate limit denied the attempt. Nothing was touched and nothing was logged. */
    data class RateLimited(val retryAfterMillis: Long, val reason: DenyReason) : BlockResult

    /** Stop-at-dialog style: the user dismissed TikTok's dialog without blocking. */
    data class UserCancelled(val handle: String, val returnedToFeed: Boolean) : BlockResult

    /**
     * Stop-at-dialog style: the user didn't answer TikTok's dialog in time. The dialog was left open for them
     * and nothing more was tapped.
     */
    data class TimedOut(val handle: String) : BlockResult

    /** Refused before doing anything (missing/stale/used token, or no expected identity). Nothing was logged. */
    data class Rejected(val reason: RejectReason) : BlockResult
}
