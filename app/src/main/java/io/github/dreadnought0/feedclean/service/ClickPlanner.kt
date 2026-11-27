package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.Bounds

/** Fresh (just refreshed) state of a node or one of its ancestors, as the click planner sees it. */
data class ClickCandidate(
    val isClickable: Boolean,
    val isVisibleToUser: Boolean,
    val bounds: Bounds,
)

/** How [ClickPlanner] decided to click. */
sealed interface ClickPlan {
    /** ACTION_CLICK on the target (`level == 0`) or on its `level`-th ancestor. */
    data class PerformClick(val level: Int) : ClickPlan

    /** Gesture tap at a screen point (the target's bounds center). */
    data class GestureTap(val x: Int, val y: Int) : ClickPlan

    /** Nothing safe to do (node not visible, or no bounds). Nothing is tapped. */
    data class Impossible(val why: String) : ClickPlan
}

/**
 * Picks the least surprising way to "tap" a node (ScreenDriver.click). Pure.
 *
 * 1. The node itself is clickable → ACTION_CLICK on it.
 * 2. Else the nearest clickable ancestor within [MAX_ANCESTOR_HOPS] that is visible, contains the node's center,
 *    and is not much bigger than the node (at most [MAX_AREA_RATIO]× its area, or [SMALL_CONTAINER_AREA] for tiny
 *    icons) → ACTION_CLICK on that ancestor. The size limit keeps us from "clicking" a whole page or toolbar that
 *    happens to be clickable and would do something else.
 * 3. Else, if the node is visible with non-empty bounds → gesture tap at its center (the profile's
 *    Share icon is `clickable=false` and needs this).
 * 4. Else nothing.
 */
object ClickPlanner {
    const val MAX_ANCESTOR_HOPS = 3
    const val MAX_AREA_RATIO = 4L

    /** About 160×160 px: an icon button's touch target on the Fold cover screen. */
    const val SMALL_CONTAINER_AREA = 160L * 160L

    /**
     * @param target the node to click.
     * @param ancestors the node's ancestors, nearest first.
     */
    fun plan(target: ClickCandidate, ancestors: List<ClickCandidate>): ClickPlan {
        if (!target.isVisibleToUser || target.bounds.isEmpty) return ClickPlan.Impossible("target not shown")
        if (target.isClickable) return ClickPlan.PerformClick(0)
        val maxArea = maxOf(MAX_AREA_RATIO * area(target.bounds), SMALL_CONTAINER_AREA)
        val cx = target.bounds.centerX
        val cy = target.bounds.centerY
        ancestors.take(MAX_ANCESTOR_HOPS).forEachIndexed { i, a ->
            if (a.isClickable && a.isVisibleToUser && a.bounds.containsPoint(cx, cy) && area(a.bounds) <= maxArea) {
                return ClickPlan.PerformClick(i + 1)
            }
        }
        return ClickPlan.GestureTap(cx, cy)
    }

    private fun area(b: Bounds): Long = if (b.isEmpty) 0L else b.width.toLong() * b.height.toLong()

    private fun Bounds.containsPoint(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}
