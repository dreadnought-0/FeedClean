package io.github.dreadnought0.feedclean.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.UiNode

/**
 * [UiNode] over a live [AccessibilityNodeInfo]: the only place the parser's world meets the framework's.
 *
 * Attributes are copied once at construction (they are local reads of data the system already sent), so the
 * node behaves like an immutable snapshot even after [info] is refreshed for an action. Children are fetched
 * lazily (that is the IPC part) and every child admission is charged to the shared [TraversalBudget].
 *
 * [info] and [parent] are kept so the screen driver can act on the node (and find a clickable ancestor) later.
 * Nodes are only valid briefly; the executors re-snapshot after every action.
 */
class A11yUiNode private constructor(
    /** The live node, for accessibility actions. */
    val info: AccessibilityNodeInfo,
    /** The node this one was read from, or null for a window root. */
    val parent: A11yUiNode?,
    /** 0 for a window root. */
    val depth: Int,
    private val budget: TraversalBudget,
) : UiNode {
    override val text: String? = info.text?.toString()
    override val contentDescription: String? = info.contentDescription?.toString()
    override val viewId: String? = info.viewIdResourceName
    override val className: String? = info.className?.toString()
    override val packageName: String? = info.packageName?.toString()
    override val bounds: Bounds = info.screenBounds()
    override val isClickable: Boolean = info.isClickable
    override val isLongClickable: Boolean = info.isLongClickable
    override val isScrollable: Boolean = info.isScrollable
    override val isSelected: Boolean = info.isSelected
    override val isVisibleToUser: Boolean = info.isVisibleToUser

    override val children: List<UiNode> by lazy { loadChildren() }

    private fun loadChildren(): List<UiNode> {
        val count = info.childCount
        if (count <= 0) return emptyList()
        val out = ArrayList<UiNode>(count)
        for (i in 0 until count) {
            if (!budget.tryAdmit(depth + 1)) break
            val child = try {
                info.getChild(i)
            } catch (_: RuntimeException) {
                // The window went away mid-read; treat the rest of this subtree as absent.
                null
            } ?: continue
            out += A11yUiNode(child, this, depth + 1, budget)
        }
        return out
    }

    override fun toString(): String = "A11yUiNode(id=$viewIdName depth=$depth $bounds)"

    companion object {
        /** Wraps a window root, or returns null if [budget] has no room left. */
        fun wrapRoot(info: AccessibilityNodeInfo, budget: TraversalBudget): A11yUiNode? =
            if (budget.tryAdmit(0)) A11yUiNode(info, null, 0, budget) else null
    }
}

/** Current on-screen bounds of the node (a local read; call [AccessibilityNodeInfo.refresh] first for fresh values). */
internal fun AccessibilityNodeInfo.screenBounds(): Bounds {
    val r = Rect()
    getBoundsInScreen(r)
    return Bounds(r.left, r.top, r.right, r.bottom)
}
