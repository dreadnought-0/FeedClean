package io.github.dreadnought0.feedclean.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Reads the interactive window list (`flagRetrieveInteractiveWindows`; TikTok's block dialog is its own window).
 * Thin Android layer; the ordering and foreground rules are pure ([WindowOrdering], [ForegroundTracker]).
 */
internal class WindowSource(private val service: AccessibilityService) {

    /**
     * Root nodes to parse: every interactive window except accessibility overlays (FeedClean's own), topmost
     * first, keeping only [packageFilter]'s windows when given. Falls back to `rootInActiveWindow` when the
     * window list is empty. Every node is charged to [budget].
     */
    fun roots(budget: TraversalBudget, packageFilter: String?): List<A11yUiNode> {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val ordered = WindowOrdering.forParsing(windows) { it.facts(readPackage = false) }
        val infos = ordered.mapNotNull { w -> runCatching { w.root }.getOrNull() }
            .ifEmpty { listOfNotNull(runCatching { service.rootInActiveWindow }.getOrNull()) }
        return infos
            .filter { packageFilter.isNullOrBlank() || it.packageName?.toString() == packageFilter }
            .mapNotNull { A11yUiNode.wrapRoot(it, budget) }
    }

    /** Window facts for foreground tracking (the package is read only for active/focused app windows). */
    fun foregroundFacts(): List<WindowFacts> =
        runCatching { service.windows }.getOrNull().orEmpty().map { it.facts(readPackage = true) }

    private fun AccessibilityWindowInfo.facts(readPackage: Boolean): WindowFacts {
        val kind = when (type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.APPLICATION
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.INPUT_METHOD
            AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.SYSTEM
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowKind.ACCESSIBILITY_OVERLAY
            else -> WindowKind.OTHER
        }
        val pkg = if (readPackage && kind == WindowKind.APPLICATION && (isActive || isFocused)) {
            runCatching { root?.packageName?.toString() }.getOrNull()
        } else {
            null
        }
        return WindowFacts(id, kind, layer, isActive, isFocused, pkg)
    }
}
