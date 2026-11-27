package io.github.dreadnought0.feedclean.data.model

/** One block attempt. Failed attempts are kept with [verified] = false. */
data class BlockEvent(
    val id: Long,
    val accountId: Long,
    val attemptedAt: Long,
    val method: BlockMethod,
    val verified: Boolean,
    val failureReason: String?,
    /** Executor step reached (e.g. "CHECK_DIALOG") when the attempt stopped. */
    val stepReached: String?,
)

/** Persisted by name; never rename entries. */
enum class BlockMethod {
    /** Run by FeedClean's verified executor after the user's tap. */
    ASSISTED,

    /** The user blocked in TikTok themselves and logged it. */
    MANUAL,

    /** Imported from an export (full export or TikTok's data export). */
    IMPORTED,
}
