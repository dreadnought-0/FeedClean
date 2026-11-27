package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.testutil.FakeSessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {
    private val repo = FakeSessionRepository()
    private val tracker = SessionTracker(repo) { 0L }

    @Test
    fun openOnlyWhileTrackingAndForeground() = runTest {
        tracker.sync(trackingEnabled = false, foreground = true)
        assertNull(tracker.currentId)
        tracker.sync(trackingEnabled = true, foreground = false)
        assertNull(tracker.currentId)
        tracker.sync(trackingEnabled = true, foreground = true)
        val id = checkNotNull(tracker.currentId)
        tracker.sync(trackingEnabled = true, foreground = true)
        assertEquals("no second session", id, tracker.currentId)
        tracker.sync(trackingEnabled = true, foreground = false)
        assertNull(tracker.currentId)
        assertEquals(listOf(id), repo.ended)
    }

    @Test
    fun pagesCountOnlyInAnOpenSession() = runTest {
        tracker.recordPage(Signals(), skipped = false)
        assertTrue(repo.pages.isEmpty())
        tracker.sync(trackingEnabled = true, foreground = true)
        tracker.recordPage(Signals(hasShopAnchor = true), skipped = true)
        assertEquals(1, repo.pages.size)
        assertTrue(repo.pages.single().skipped)
    }

    @Test
    fun recoverOnStartClosesLeftovers() = runTest {
        repo.start(0)
        tracker.recoverOnStart()
        assertEquals(1, repo.endAllOpenCalls)
        assertTrue(repo.open.isEmpty())
    }

    @Test
    fun closeEndsTheOpenSession() = runTest {
        tracker.sync(trackingEnabled = true, foreground = true)
        tracker.close()
        assertNull(tracker.currentId)
        assertEquals(1, repo.ended.size)
    }
}
