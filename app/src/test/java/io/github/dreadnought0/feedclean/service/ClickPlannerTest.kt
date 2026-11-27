package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickPlannerTest {
    private fun node(b: Bounds, clickable: Boolean = false, visible: Boolean = true) = ClickCandidate(clickable, visible, b)

    /** The profile Share icon as uiautomator reports it (clickable=false). */
    private val share = Bounds(972, 102, 1062, 192)

    @Test
    fun clickableTargetGetsActionClick() =
        assertEquals(ClickPlan.PerformClick(0), ClickPlanner.plan(node(share, clickable = true), emptyList()))

    @Test
    fun smallClickableParentGetsActionClick() {
        val parent = node(Bounds(960, 90, 1074, 204), clickable = true)
        assertEquals(ClickPlan.PerformClick(1), ClickPlanner.plan(node(share), listOf(parent)))
    }

    @Test
    fun bigClickableAncestorIsNotUsedTapInstead() {
        val toolbar = node(Bounds(0, 80, 1080, 220), clickable = true)
        assertEquals(ClickPlan.GestureTap(1017, 147), ClickPlanner.plan(node(share), listOf(toolbar)))
    }

    @Test
    fun ancestorMustContainTheCenter() {
        val sideways = node(Bounds(800, 102, 900, 192), clickable = true)
        assertTrue(ClickPlanner.plan(node(share), listOf(sideways)) is ClickPlan.GestureTap)
    }

    @Test
    fun onlyThreeHopsUp() {
        val nonClickable = node(Bounds(960, 90, 1074, 204))
        val fourth = node(Bounds(965, 95, 1070, 200), clickable = true)
        assertTrue(ClickPlanner.plan(node(share), listOf(nonClickable, nonClickable, nonClickable, fourth)) is ClickPlan.GestureTap)
        assertEquals(ClickPlan.PerformClick(3), ClickPlanner.plan(node(share), listOf(nonClickable, nonClickable, fourth)))
    }

    @Test
    fun invisibleAncestorIgnored() {
        val hidden = node(Bounds(960, 90, 1074, 204), clickable = true, visible = false)
        assertTrue(ClickPlanner.plan(node(share), listOf(hidden)) is ClickPlan.GestureTap)
    }

    @Test
    fun invisibleOrEmptyTargetIsNeverTapped() {
        assertTrue(ClickPlanner.plan(node(share, clickable = true, visible = false), emptyList()) is ClickPlan.Impossible)
        assertTrue(ClickPlanner.plan(node(Bounds(0, 2376, 1080, 2376)), emptyList()) is ClickPlan.Impossible)
    }
}
