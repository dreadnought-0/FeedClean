package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.detect.Bounds

/** A window position in screen pixels (top-left corner). */
data class PxPoint(val x: Int, val y: Int)

/**
 * Where the overlay windows go (bubble, card, hint). Pure; all values in screen pixels.
 * The screen rectangle is the live display size, so the inner (unfolded) screen degrades gracefully.
 */
object OverlayGeometry {

    /**
     * Default bubble position: LEFT edge (inset by [marginPx]) at [DEFAULT_Y_FRACTION] of the height. That is open
     * video area on TikTok's feed: clear of the top tabs, the right-rail buttons (a right-edge default covers one
     * of them and blocks its taps), and the caption block at the bottom left.
     */
    fun defaultBubble(screen: Bounds, sizePx: Int, marginPx: Int): PxPoint =
        clampBubble(screen.left + marginPx, screen.top + (screen.height * DEFAULT_Y_FRACTION).toInt() - sizePx / 2, sizePx, screen)

    /** Vertical position of the default bubble, as a fraction of the screen height. */
    const val DEFAULT_Y_FRACTION = 0.38f

    /** Keeps a [sizePx] bubble at ([x], [y]) fully on [screen]. */
    fun clampBubble(x: Int, y: Int, sizePx: Int, screen: Bounds): PxPoint = PxPoint(
        x.coerceIn(screen.left, maxOf(screen.left, screen.right - sizePx)),
        y.coerceIn(screen.top, maxOf(screen.top, screen.bottom - sizePx)),
    )

    /** Saved position (settings bubbleX/bubbleY; -1 = never moved) → on-screen position. */
    fun resolveBubble(savedX: Int, savedY: Int, sizePx: Int, screen: Bounds, marginPx: Int): PxPoint =
        if (savedX < 0 || savedY < 0) {
            defaultBubble(screen, sizePx, marginPx)
        } else {
            clampBubble(savedX, savedY, sizePx, screen)
        }

    /** Position after dragging from [start] by ([dx], [dy]), clamped to [screen]. */
    fun drag(start: PxPoint, dx: Int, dy: Int, sizePx: Int, screen: Bounds): PxPoint =
        clampBubble(start.x + dx, start.y + dy, sizePx, screen)

    /** True when the bubble sits on the right half of the screen (cards and hints then open to its left). */
    fun isOnRightHalf(bubble: Bounds, screen: Bounds): Boolean = bubble.centerX >= screen.centerX

    /**
     * Card placement next to the bubble: horizontally on the side with more room, vertically centred on the
     * bubble, then clamped inside [screen] with [marginPx].
     */
    fun placeCard(bubble: Bounds, cardWidth: Int, cardHeight: Int, screen: Bounds, marginPx: Int): PxPoint {
        val x = if (isOnRightHalf(bubble, screen)) bubble.left - marginPx - cardWidth else bubble.right + marginPx
        val y = bubble.centerY - cardHeight / 2
        return clampInside(x, y, cardWidth, cardHeight, screen, marginPx)
    }

    /** Hint pill placement: beside the bubble (towards the screen centre), vertically centred on it. */
    fun placeHint(bubble: Bounds, hintWidth: Int, hintHeight: Int, screen: Bounds, gapPx: Int): PxPoint {
        val x = if (isOnRightHalf(bubble, screen)) bubble.left - gapPx - hintWidth else bubble.right + gapPx
        val y = bubble.centerY - hintHeight / 2
        return clampInside(x, y, hintWidth, hintHeight, screen, 0)
    }

    private fun clampInside(x: Int, y: Int, w: Int, h: Int, screen: Bounds, margin: Int): PxPoint {
        val minX = screen.left + margin
        val maxX = maxOf(minX, screen.right - margin - w)
        val minY = screen.top + margin
        val maxY = maxOf(minY, screen.bottom - margin - h)
        return PxPoint(x.coerceIn(minX, maxX), y.coerceIn(minY, maxY))
    }
}
