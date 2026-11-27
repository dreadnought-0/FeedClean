package io.github.dreadnought0.feedclean.data

/**
 * Persisted log of every action TikTok can see (blocks, automated/manual "Not interested").
 * Single source of truth for the RateLimiter, so restarts don't reset the budget.
 *
 * Implemented by the Room-backed data layer; a fake is used in unit tests.
 */
interface ActionLogRepository {
    suspend fun record(kind: ActionKind, atMillis: Long, accountId: Long?, verified: Boolean)

    /** Number of logged actions (any kind, verified or not; attempts count) at or after [sinceMillis]. */
    suspend fun countSince(sinceMillis: Long): Int

    /** Timestamp of the most recent logged action, or null if none. */
    suspend fun lastActionAt(): Long?
}

/** Persisted by name; never rename entries. */
enum class ActionKind { BLOCK, NOT_INTERESTED }
