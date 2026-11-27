package io.github.dreadnought0.feedclean.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubbleGestureTrackerTest {
    private val t = BubbleGestureTracker(touchSlopPx = 10)

    @Test
    fun stillPressAndReleaseIsATap() {
        t.down(100f, 100f)
        assertNull(t.move(104f, 97f))
        assertEquals(BubbleGesture.Tap, t.up())
    }

    @Test
    fun movementBeyondSlopIsADragFromTheDownPoint() {
        t.down(100f, 100f)
        assertNull(t.move(105f, 100f))
        assertEquals(BubbleGesture.Drag(30, -20), t.move(130f, 80f))
        assertEquals(BubbleGesture.Drag(2, 1), t.move(102f, 101f))
        assertEquals(BubbleGesture.DragEnd, t.up())
    }

    @Test
    fun holdIsALongPressAndNotATap() {
        t.down(100f, 100f)
        assertEquals(BubbleGesture.LongPress, t.longPressElapsed())
        assertNull("only once", t.longPressElapsed())
        assertNull("no drag after a long-press", t.move(200f, 200f))
        assertNull(t.up())
    }

    @Test
    fun noLongPressDuringADrag() {
        t.down(100f, 100f)
        t.move(150f, 100f)
        assertNull("dragging must never toggle the kill-switch", t.longPressElapsed())
        assertEquals(BubbleGesture.DragEnd, t.up())
    }

    @Test
    fun longPressTimerAfterReleaseIsIgnored() {
        t.down(100f, 100f)
        t.up()
        assertNull(t.longPressElapsed())
    }

    @Test
    fun cancelEndsADragOnly() {
        t.down(0f, 0f)
        assertNull(t.cancel())
        t.down(0f, 0f)
        t.move(50f, 0f)
        assertEquals(BubbleGesture.DragEnd, t.cancel())
        assertNull(t.up())
    }
}
