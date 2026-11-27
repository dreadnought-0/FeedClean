package io.github.dreadnought0.feedclean.overlay

import kotlin.math.abs

/** What a touch sequence on the bubble meant. */
sealed interface BubbleGesture {
    /** Move the bubble by ([dx], [dy]) from where it was when the finger went down. */
    data class Drag(val dx: Int, val dy: Int) : BubbleGesture

    /** The drag ended: persist the position. */
    data object DragEnd : BubbleGesture

    data object Tap : BubbleGesture

    data object LongPress : BubbleGesture
}

/**
 * Tap / long-press / drag recognition for the bubble, from RAW screen coordinates. Pure state machine.
 *
 * Raw coordinates matter: the window moves with the finger while dragging, so window-local coordinates barely
 * change and a stock GestureDetector would fire a long-press (the kill-switch!) in the middle of a drag.
 *
 * Usage: [down] on ACTION_DOWN and schedule [longPressElapsed] after the long-press timeout; [move] on
 * ACTION_MOVE; [up] on ACTION_UP; [cancel] on ACTION_CANCEL.
 */
class BubbleGestureTracker(private val touchSlopPx: Int) {
    private var downX = 0f
    private var downY = 0f
    private var pressed = false
    private var dragging = false
    private var longPressed = false

    /** Finger down at raw ([rawX], [rawY]). */
    fun down(rawX: Float, rawY: Float) {
        downX = rawX
        downY = rawY
        pressed = true
        dragging = false
        longPressed = false
    }

    /** Finger moved. Returns a [BubbleGesture.Drag] once the movement exceeds the touch slop. */
    fun move(rawX: Float, rawY: Float): BubbleGesture.Drag? {
        if (!pressed || longPressed) return null
        val dx = rawX - downX
        val dy = rawY - downY
        if (!dragging && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) dragging = true
        return if (dragging) BubbleGesture.Drag(dx.toInt(), dy.toInt()) else null
    }

    /** The long-press timeout elapsed. Returns [BubbleGesture.LongPress] if the finger is still down and still. */
    fun longPressElapsed(): BubbleGesture.LongPress? {
        if (!pressed || dragging || longPressed) return null
        longPressed = true
        return BubbleGesture.LongPress
    }

    /** Finger up: a tap, the end of a drag, or nothing (after a long-press). */
    fun up(): BubbleGesture? {
        if (!pressed) return null
        pressed = false
        return when {
            dragging -> BubbleGesture.DragEnd
            longPressed -> null
            else -> BubbleGesture.Tap
        }
    }

    /** Gesture cancelled by the system. */
    fun cancel(): BubbleGesture? {
        if (!pressed) return null
        pressed = false
        return if (dragging) BubbleGesture.DragEnd else null
    }
}
