package io.github.dreadnought0.feedclean.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTrackerTest {
    private val target = "target.app"

    private fun app(pkg: String?, active: Boolean = true, focused: Boolean = true, layer: Int = 1, id: Int = layer) =
        WindowFacts(id, WindowKind.APPLICATION, layer, active, focused, pkg)

    private fun overlay(active: Boolean = false) = WindowFacts(99, WindowKind.ACCESSIBILITY_OVERLAY, 50, active, false)

    private fun shade() = WindowFacts(7, WindowKind.SYSTEM, 40, isActive = true, isFocused = true)

    private fun tracker(misses: Int = 2) = ForegroundTracker({ target }, misses)

    @Test
    fun targetWindowActiveOrFocusedIsForeground() {
        val t = tracker()
        assertEquals(true, t.update(listOf(app(target))))
        assertNull("unchanged → null", t.update(listOf(app(target, active = false))))
        assertTrue(t.isForeground)
    }

    @Test
    fun anotherAppInFrontLeavesImmediately() {
        val t = tracker()
        t.update(listOf(app(target)))
        assertEquals(false, t.update(listOf(app("other.app"), app(target, active = false, focused = false, layer = 0))))
    }

    @Test
    fun notificationShadeLeavesImmediately() {
        val t = tracker()
        t.update(listOf(app(target)))
        assertEquals(false, t.update(listOf(shade(), app(target, active = false, focused = false, layer = 0))))
    }

    @Test
    fun unclearReadingsNeedConsecutiveMisses() {
        val t = tracker(misses = 2)
        t.update(listOf(app(target)))
        assertNull(t.update(emptyList()))
        assertTrue(t.isForeground)
        assertEquals(false, t.update(emptyList()))
    }

    @Test
    fun aHitResetsTheMissCount() {
        val t = tracker(misses = 2)
        t.update(listOf(app(target)))
        t.update(emptyList())
        t.update(listOf(app(target)))
        assertNull(t.update(emptyList()))
        assertTrue(t.isForeground)
    }

    @Test
    fun ourOwnOverlayNeverCounts() {
        val t = tracker()
        t.update(listOf(app(target)))
        // The card is touched (active overlay) while TikTok keeps focus: still TikTok.
        assertNull(t.update(listOf(overlay(active = true), app(target, active = false, focused = true))))
        assertTrue(t.isForeground)
        assertEquals(ForegroundTracker.Reading.UNCLEAR, ForegroundTracker.classify(listOf(overlay(active = true)), target))
    }

    @Test
    fun unknownTargetPackageIsUnclear() {
        assertEquals(ForegroundTracker.Reading.UNCLEAR, ForegroundTracker.classify(listOf(app(target)), null))
        val t = ForegroundTracker({ null }, 1)
        assertNull(t.update(listOf(app(target))))
        assertFalse(t.isForeground)
    }

    @Test
    fun resetForgets() {
        val t = tracker()
        t.update(listOf(app(target)))
        t.reset()
        assertFalse(t.isForeground)
    }

    @Test
    fun windowOrderingExcludesOverlaysAndSortsTopmostFirst() {
        val windows = listOf(
            app(target, layer = 1, id = 1),
            overlay(),
            WindowFacts(2, WindowKind.SYSTEM, 30, false, false),
            app(target, layer = 5, id = 3),
            WindowFacts(4, WindowKind.INPUT_METHOD, 5, false, false),
        )
        val ordered = WindowOrdering.forParsing(windows) { it }
        assertEquals(listOf(2, 3, 4, 1), ordered.map { it.id })
    }
}
