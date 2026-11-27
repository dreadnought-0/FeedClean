package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.ActionLogRepository

/** In-memory [ActionLogRepository]. Survives "restarts" in tests by sharing one instance across limiters. */
class FakeActionLogRepository : ActionLogRepository {
    data class Entry(val kind: ActionKind, val atMillis: Long, val accountId: Long?, val verified: Boolean)

    private val _entries = mutableListOf<Entry>()

    /** Everything recorded so far, in insertion order. */
    val entries: List<Entry>
        get() = synchronized(_entries) { _entries.toList() }

    /** Pre-populates the log (as if from an earlier app session). */
    fun seed(vararg atMillis: Long, kind: ActionKind = ActionKind.BLOCK) {
        synchronized(_entries) { atMillis.forEach { _entries += Entry(kind, it, null, true) } }
    }

    override suspend fun record(kind: ActionKind, atMillis: Long, accountId: Long?, verified: Boolean) {
        synchronized(_entries) { _entries += Entry(kind, atMillis, accountId, verified) }
    }

    override suspend fun countSince(sinceMillis: Long): Int =
        synchronized(_entries) { _entries.count { it.atMillis >= sinceMillis } }

    override suspend fun lastActionAt(): Long? = synchronized(_entries) { _entries.maxOfOrNull { it.atMillis } }
}
