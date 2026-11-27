package io.github.dreadnought0.feedclean.detect

/**
 * Screen-space rectangle in pixels. Pure Kotlin (no android.graphics.Rect) so the parser runs on the JVM.
 */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun intersects(other: Bounds): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun contains(other: Bounds): Boolean =
        other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/**
 * Read-only view of one node in an on-screen view tree.
 *
 * Two implementations:
 *  - `service.A11yUiNode` wraps `AccessibilityNodeInfo` (live, on device).
 *  - `XmlUiNode` (test sources) parses `uiautomator dump` XML, so scrubbed uiautomator dumps are JVM test fixtures.
 *
 * The parser (detect/) must only ever see this interface, never AccessibilityNodeInfo.
 */
interface UiNode {
    val text: String?
    val contentDescription: String?

    /** Full resource name as reported by Android, e.g. "com.zhiliaoapp.musically:id/title". Null if none. */
    val viewId: String?
    val className: String?
    val packageName: String?
    val bounds: Bounds
    val isClickable: Boolean
    val isLongClickable: Boolean
    val isScrollable: Boolean
    val isSelected: Boolean
    val isVisibleToUser: Boolean
    val children: List<UiNode>

    /** The part of [viewId] after ":id/", e.g. "title". Null if no view ID. */
    val viewIdName: String?
        get() = viewId?.substringAfter(":id/", missingDelimiterValue = viewId ?: "")?.ifEmpty { null }
}

/** Depth-first traversal of this node and all descendants. */
fun UiNode.walk(): Sequence<UiNode> = sequence {
    val stack = ArrayDeque<UiNode>()
    stack.addLast(this@walk)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        yield(node)
        // Push children in reverse so traversal is in document order.
        for (i in node.children.indices.reversed()) stack.addLast(node.children[i])
    }
}
