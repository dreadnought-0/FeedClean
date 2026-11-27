package io.github.dreadnought0.feedclean.detect

import java.util.IdentityHashMap

/**
 * One pre-order traversal of a window forest, so each parse reads the (possibly IPC-backed) tree once.
 *
 * Nodes are keyed by identity: two different on-screen nodes may compare equal structurally. Traversal is capped
 * at [MAX_NODES] nodes and [MAX_DEPTH] levels so a pathological tree can't stall the service.
 */
internal class NodeIndex private constructor(
    /** All nodes in document order (root 0's subtree, then root 1's, …). */
    val nodes: List<UiNode>,
    private val parents: List<Int>,
    private val depths: IntArray,
    private val subtreeEnds: IntArray,
    private val rootOf: IntArray,
    private val positions: IdentityHashMap<UiNode, Int>,
) {
    fun indexOf(node: UiNode): Int = positions[node] ?: -1

    fun depthOf(node: UiNode): Int = indexOf(node).let { if (it < 0) -1 else depths[it] }

    fun parentOf(node: UiNode): UiNode? = indexOf(node).let { if (it < 0 || parents[it] < 0) null else nodes[parents[it]] }

    /** Index of the window root (0 = topmost) containing [node], or -1. */
    fun rootIndexOf(node: UiNode): Int = indexOf(node).let { if (it < 0) -1 else rootOf[it] }

    /** [node] and all its descendants, document order. */
    fun subtree(node: UiNode): List<UiNode> {
        val i = indexOf(node)
        return if (i < 0) emptyList() else nodes.subList(i, subtreeEnds[i])
    }

    /** True if [descendant] is [ancestor] or inside its subtree. */
    fun isWithin(descendant: UiNode, ancestor: UiNode): Boolean {
        val a = indexOf(ancestor)
        val d = indexOf(descendant)
        return a >= 0 && d >= a && d < subtreeEnds[a]
    }

    companion object {
        const val MAX_NODES = 5_000
        const val MAX_DEPTH = 200

        fun build(roots: List<UiNode>): NodeIndex {
            val nodes = ArrayList<UiNode>()
            val parents = ArrayList<Int>()
            val depths = ArrayList<Int>()
            val rootOf = ArrayList<Int>()
            val ends = ArrayList<Int>()
            val positions = IdentityHashMap<UiNode, Int>()

            // Iterative DFS; a frame is (node, parentIndex, depth, rootIndex). End markers close subtrees.
            data class Frame(val node: UiNode?, val parent: Int, val depth: Int, val root: Int, val closes: Int = -1)

            val stack = ArrayDeque<Frame>()
            for (r in roots.indices.reversed()) stack.addLast(Frame(roots[r], -1, 0, r))
            while (stack.isNotEmpty()) {
                val f = stack.removeLast()
                if (f.closes >= 0) {
                    ends[f.closes] = nodes.size
                    continue
                }
                val node = f.node ?: continue
                if (nodes.size >= MAX_NODES || positions.containsKey(node)) continue
                val i = nodes.size
                nodes += node
                parents += f.parent
                depths += f.depth
                rootOf += f.root
                ends += i + 1
                positions[node] = i
                stack.addLast(Frame(null, -1, 0, 0, closes = i))
                if (f.depth < MAX_DEPTH) {
                    val children = node.children
                    for (c in children.indices.reversed()) stack.addLast(Frame(children[c], i, f.depth + 1, f.root))
                }
            }
            return NodeIndex(
                nodes = nodes,
                parents = parents,
                depths = depths.toIntArray(),
                subtreeEnds = ends.toIntArray(),
                rootOf = rootOf.toIntArray(),
                positions = positions,
            )
        }
    }
}

/** Shown on screen: visible to the user with non-empty bounds. */
internal val UiNode.isShown: Boolean
    get() = isVisibleToUser && !bounds.isEmpty

/** Intersection of two rectangles, or [Bounds.EMPTY] if they don't overlap. */
internal fun Bounds.intersect(other: Bounds): Bounds {
    if (!intersects(other)) return Bounds.EMPTY
    return Bounds(maxOf(left, other.left), maxOf(top, other.top), minOf(right, other.right), minOf(bottom, other.bottom))
}

internal val Bounds.area: Long
    get() = if (isEmpty) 0L else width.toLong() * height.toLong()
