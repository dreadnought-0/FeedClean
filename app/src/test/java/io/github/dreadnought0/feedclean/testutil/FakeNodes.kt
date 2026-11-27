package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.UiNode

/**
 * Minimal [UiNode] for executor tests.
 *
 * @property tag stable name the fake driver uses to recognise what was clicked, e.g. "feed.avatar".
 * @property scope the fake screen the node was created for; a click on a node from another screen is stale.
 */
class FakeUiNode(
    val tag: String,
    val scope: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val viewId: String? = null,
    override val className: String? = null,
    override val packageName: String? = null,
    override val bounds: Bounds = Bounds(0, 0, 100, 100),
    override val isClickable: Boolean = true,
    override val isLongClickable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isSelected: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    override val children: List<UiNode> = emptyList(),
) : UiNode {
    override fun toString(): String = "FakeUiNode($tag)"
}
