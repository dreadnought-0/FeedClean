package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.detect.Bounds
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayGeometryTest {
    /** Fold 7 cover screen. */
    private val cover = Bounds(0, 0, 1080, 2520)
    private val size = 116
    private val margin = 16

    @Test
    fun defaultIsRightEdgeVerticalMiddle() {
        // Left edge, 38% down: clear of TikTok's right-rail buttons, top tabs, and bottom-left caption.
        assertEquals(PxPoint(16, (2520 * 0.38f).toInt() - 116 / 2), OverlayGeometry.defaultBubble(cover, size, margin))
    }

    @Test
    fun unsetSavedPositionUsesTheDefault() {
        assertEquals(OverlayGeometry.defaultBubble(cover, size, margin), OverlayGeometry.resolveBubble(-1, -1, size, cover, margin))
        assertEquals(OverlayGeometry.defaultBubble(cover, size, margin), OverlayGeometry.resolveBubble(200, -1, size, cover, margin))
    }

    @Test
    fun savedPositionIsClampedToTheCurrentScreen() {
        assertEquals(PxPoint(300, 400), OverlayGeometry.resolveBubble(300, 400, size, cover, margin))
        // Saved on the wider inner screen, now folded: pulled back on screen.
        assertEquals(PxPoint(1080 - 116, 2520 - 116), OverlayGeometry.resolveBubble(2000, 5000, size, cover, margin))
    }

    @Test
    fun dragIsClampedOnEverySide() {
        val start = PxPoint(500, 1000)
        assertEquals(PxPoint(550, 900), OverlayGeometry.drag(start, 50, -100, size, cover))
        assertEquals(PxPoint(0, 0), OverlayGeometry.drag(start, -9_999, -9_999, size, cover))
        assertEquals(PxPoint(964, 2404), OverlayGeometry.drag(start, 9_999, 9_999, size, cover))
    }

    @Test
    fun clampSurvivesAScreenSmallerThanTheBubble() {
        assertEquals(PxPoint(0, 0), OverlayGeometry.clampBubble(50, 50, 200, Bounds(0, 0, 100, 100)))
    }

    @Test
    fun cardOpensTowardsTheScreenCentre() {
        val rightBubble = Bounds(948, 1202, 1064, 1318)
        // Left of the bubble: 948 - 16 - 900 = 32; vertically centred on it: 1260 - 300.
        assertEquals(PxPoint(32, 960), OverlayGeometry.placeCard(rightBubble, 900, 600, cover, margin))

        val leftBubble = Bounds(16, 1202, 132, 1318)
        // Right of the bubble: 132 + 16 = 148.
        assertEquals(PxPoint(148, 960), OverlayGeometry.placeCard(leftBubble, 900, 600, cover, margin))
    }

    @Test
    fun cardIsKeptInsideTheScreen() {
        val topBubble = Bounds(948, 0, 1064, 116)
        assertEquals(margin, OverlayGeometry.placeCard(topBubble, 900, 1200, cover, margin).y)
        val bottomBubble = Bounds(948, 2404, 1064, 2520)
        assertEquals(2520 - margin - 1200, OverlayGeometry.placeCard(bottomBubble, 900, 1200, cover, margin).y)
        val wide = OverlayGeometry.placeCard(topBubble, 5_000, 100, cover, margin)
        assertEquals(margin, wide.x)
    }

    @Test
    fun hintSitsBesideTheBubble() {
        val rightBubble = Bounds(948, 1202, 1064, 1318)
        assertEquals(PxPoint(948 - 12 - 400, 1260 - 40), OverlayGeometry.placeHint(rightBubble, 400, 80, cover, 12))
        val leftBubble = Bounds(0, 1202, 116, 1318)
        assertEquals(PxPoint(116 + 12, 1260 - 40), OverlayGeometry.placeHint(leftBubble, 400, 80, cover, 12))
    }
}
