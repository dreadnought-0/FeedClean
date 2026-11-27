package io.github.dreadnought0.feedclean.service

import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounds one read of the on-screen window forest, so a pathological tree (huge, very deep, or slow to answer
 * over IPC) can't hang the service. Shared by every [A11yUiNode] of one snapshot.
 *
 * Once any limit is hit, no further children are expanded and [exhausted] becomes true. Callers treat an
 * exhausted read as incomplete: it is shown on the Debug screen but never used to move the feed, because a
 * truncated tree could be missing the very label (e.g. "Ad") that keeps a post.
 *
 * Pure Kotlin; thread-safe.
 *
 * @param maxNodes maximum number of nodes (roots included).
 * @param maxDepth maximum node depth (roots are depth 0).
 * @param maxNanos maximum wall time for expanding the tree, measured from construction.
 */
class TraversalBudget(
    val maxNodes: Int = DEFAULT_MAX_NODES,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxNanos: Long = DEFAULT_MAX_NANOS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val startedAt = nanoTime()
    private val taken = AtomicInteger(0)

    @Volatile
    var exhausted: Boolean = false
        private set

    /** Number of nodes admitted so far. */
    val nodeCount: Int
        get() = taken.get().coerceAtMost(maxNodes)

    /** Admits one more node at [depth]. Returns false (and marks the budget exhausted) when a limit is hit. */
    fun tryAdmit(depth: Int): Boolean {
        val ok = depth <= maxDepth && nanoTime() - startedAt <= maxNanos && taken.incrementAndGet() <= maxNodes
        if (!ok) exhausted = true
        return ok
    }

    companion object {
        /** Matches the parser's own node cap. */
        const val DEFAULT_MAX_NODES = 5_000

        /** Matches the parser's own depth cap. */
        const val DEFAULT_MAX_DEPTH = 200

        /** Two seconds. A healthy feed page reads in tens of milliseconds. */
        const val DEFAULT_MAX_NANOS = 2_000_000_000L
    }
}
