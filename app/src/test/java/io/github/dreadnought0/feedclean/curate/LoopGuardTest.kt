package io.github.dreadnought0.feedclean.curate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopGuardTest {
    @Test
    fun remembersAttemptsAndForgetsOldest() {
        val guard = LoopGuard(maxRemembered = 2)
        guard.markAttempt("a")
        guard.markAttempt("b")
        assertTrue(guard.wasAttempted("a"))
        guard.markAttempt("c")
        assertFalse("oldest forgotten", guard.wasAttempted("a"))
        assertTrue(guard.wasAttempted("b"))
        assertTrue(guard.wasAttempted("c"))
    }

    @Test
    fun pausesAtCapAndOnlyResumeClearsIt() {
        val guard = LoopGuard()
        assertFalse(guard.onAutoSkipped(cap = 3))
        assertFalse(guard.onAutoSkipped(cap = 3))
        assertTrue(guard.onAutoSkipped(cap = 3))
        assertTrue(guard.isPaused)
        assertEquals(3, guard.pausedAfter)
        guard.onPageKept()
        assertTrue("a kept page resets the count, not the pause", guard.isPaused)
        assertEquals(0, guard.consecutiveSkips)
        guard.resume()
        assertFalse(guard.isPaused)
        assertEquals(0, guard.pausedAfter)
    }

    @Test
    fun keptPageResetsTheRun() {
        val guard = LoopGuard()
        guard.onAutoSkipped(cap = 3)
        guard.onAutoSkipped(cap = 3)
        guard.onPageKept()
        assertFalse(guard.onAutoSkipped(cap = 3))
        assertFalse(guard.onAutoSkipped(cap = 3))
        assertFalse(guard.isPaused)
    }

    @Test
    fun capBelowOneTreatedAsOne() {
        val guard = LoopGuard()
        assertTrue(guard.onAutoSkipped(cap = 0))
    }
}
