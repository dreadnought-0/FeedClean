package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraversalAndPollingTest {

    @Test
    fun budgetCapsNodes() {
        val b = TraversalBudget(maxNodes = 3, maxDepth = 10)
        repeat(3) { assertTrue(b.tryAdmit(1)) }
        assertFalse(b.exhausted)
        assertFalse(b.tryAdmit(1))
        assertTrue(b.exhausted)
        assertEquals(3, b.nodeCount)
    }

    @Test
    fun budgetCapsDepth() {
        val b = TraversalBudget(maxNodes = 100, maxDepth = 2)
        assertTrue(b.tryAdmit(2))
        assertFalse(b.tryAdmit(3))
        assertTrue(b.exhausted)
    }

    @Test
    fun budgetCapsTime() {
        var now = 0L
        val b = TraversalBudget(maxNodes = 100, maxDepth = 10, maxNanos = 1_000, nanoTime = { now })
        assertTrue(b.tryAdmit(0))
        now = 2_000
        assertFalse(b.tryAdmit(1))
        assertTrue(b.exhausted)
    }

    @Test
    fun pollingReturnsTheFirstMatch() = runTest {
        var n = 0
        val result = pollForSnapshot(
            snapshot = { ScreenSnapshot.Unknown(n++.toLong()) },
            timeoutMillis = 3_000,
            pollMillis = 100,
            nowMillis = { testScheduler.currentTime },
        ) { it.capturedAtMillis == 3L }
        assertEquals(3L, result?.capturedAtMillis)
        assertEquals(300L, testScheduler.currentTime)
    }

    @Test
    fun pollingTimesOutWithNull() = runTest {
        var reads = 0
        val result = pollForSnapshot({ ScreenSnapshot.Unknown(0).also { reads++ } }, 1_000, 150, { testScheduler.currentTime }) { false }
        assertNull(result)
        assertEquals(1_000L, testScheduler.currentTime)
        assertTrue(reads in 7..9)
    }

    @Test
    fun pollingAlwaysReadsOnce() = runTest {
        var reads = 0
        pollForSnapshot({ ScreenSnapshot.Unknown(0).also { reads++ } }, 0, 100, { testScheduler.currentTime }) { false }
        assertEquals(1, reads)
    }
}
