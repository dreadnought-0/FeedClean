package io.github.dreadnought0.feedclean.block

/**
 * Why an executor refused to start. When an executor rejects a request, nothing on screen was touched and
 * nothing was written to the action log.
 *
 * Persisted by name; never rename entries.
 */
enum class RejectReason {
    /** No [UserIntentToken] was supplied. */
    MISSING_TOKEN,

    /** The token is older than the allowed age (or dated in the future). */
    STALE_TOKEN,

    /** The token was already used by an earlier run. One click starts at most one action. */
    TOKEN_ALREADY_USED,

    /** Block target has neither an expected display name nor an expected handle, so it can't be verified. */
    NO_EXPECTED_IDENTITY,

    /** Manual "Not interested" is switched off in Settings. */
    MANUAL_DISABLED,

    /** Automated "Not interested" is not armed (off, risk note not accepted, curation off, or kill-switch on). */
    AUTOMATION_NOT_ARMED,
}
