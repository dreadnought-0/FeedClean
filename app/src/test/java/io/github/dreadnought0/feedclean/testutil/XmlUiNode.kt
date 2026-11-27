package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.UiNode
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [UiNode] parsed from `uiautomator dump` XML, so scrubbed device dumps run as JVM fixtures.
 *
 * Mapping: `bounds="[l,t][r,b]"`, `resource-id` → [viewId], `content-desc`, `clickable`, `long-clickable`,
 * `scrollable`, `selected`, `package`, `class`. Empty attributes become null. A node is visible to the user unless
 * its bounds are empty (uiautomator only dumps visible nodes, but clips off-screen pages to zero height), or an
 * explicit `visible-to-user` attribute (written by `TreeXmlWriter`) says otherwise.
 *
 * Multi-window screens: every `<node>` child of `<hierarchy>` is one window root, topmost first (the order the
 * parser expects). A fixture may hold several windows, e.g. `synthetic/profile_with_product_sheet.xml`.
 */
data class XmlUiNode(
    override val text: String?,
    override val contentDescription: String?,
    override val viewId: String?,
    override val className: String?,
    override val packageName: String?,
    override val bounds: Bounds,
    override val isClickable: Boolean,
    override val isLongClickable: Boolean,
    override val isScrollable: Boolean,
    override val isSelected: Boolean,
    override val isVisibleToUser: Boolean,
    override val children: List<UiNode>,
) : UiNode {

    override fun toString(): String =
        "XmlUiNode(${className?.substringAfterLast('.')} id=$viewIdName text=$text desc=$contentDescription $bounds)"

    /**
     * A copy of this tree with [transform] applied to every node, children first (so [transform] sees the already
     * transformed children). Returning null drops the node and its subtree. For tests that derive a variant of a
     * fixture (another tab selected, a bar removed, …) instead of committing a near-duplicate file.
     */
    fun mapTree(transform: (XmlUiNode) -> XmlUiNode?): XmlUiNode? {
        val newChildren = children.mapNotNull { (it as XmlUiNode).mapTree(transform) }
        return transform(copy(children = newChildren))
    }

    companion object {
        private val BOUNDS = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

        /** Window roots (children of `<hierarchy>`), topmost first. */
        fun parse(input: InputStream): List<XmlUiNode> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isExpandEntityReferences = false
                isNamespaceAware = false
            }
            val doc = input.use { factory.newDocumentBuilder().parse(it) }
            return doc.documentElement.childElements("node").map(::toNode)
        }

        fun parse(file: File): List<XmlUiNode> = parse(file.inputStream())

        fun parseString(xml: String): List<XmlUiNode> = parse(xml.byteInputStream(Charsets.UTF_8))

        /** Loads a test resource, e.g. `/fixtures/tiktok-47.7.3/cover/a_normal.xml`. */
        fun parseResource(path: String): List<XmlUiNode> {
            val stream = XmlUiNode::class.java.getResourceAsStream(path) ?: error("Missing test resource $path")
            return parse(stream)
        }

        /** [mapTree] over every window root; a root dropped by [transform] disappears from the list. */
        fun List<XmlUiNode>.mapTrees(transform: (XmlUiNode) -> XmlUiNode?): List<XmlUiNode> =
            mapNotNull { it.mapTree(transform) }

        fun parseBounds(value: String): Bounds {
            val m = BOUNDS.matchEntire(value.trim()) ?: return Bounds.EMPTY
            val (l, t, r, b) = m.destructured
            return Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
        }

        private fun toNode(e: Element): XmlUiNode {
            val bounds = parseBounds(e.getAttribute("bounds"))
            val explicitVisible = e.getAttribute("visible-to-user").takeIf { it.isNotEmpty() }
            return XmlUiNode(
                text = e.attrOrNull("text"),
                contentDescription = e.attrOrNull("content-desc"),
                viewId = e.attrOrNull("resource-id"),
                className = e.attrOrNull("class"),
                packageName = e.attrOrNull("package"),
                bounds = bounds,
                isClickable = e.getAttribute("clickable") == "true",
                isLongClickable = e.getAttribute("long-clickable") == "true",
                isScrollable = e.getAttribute("scrollable") == "true",
                isSelected = e.getAttribute("selected") == "true",
                isVisibleToUser = explicitVisible?.let { it == "true" } ?: !bounds.isEmpty,
                children = e.childElements("node").map(::toNode),
            )
        }

        private fun Element.attrOrNull(name: String): String? = getAttribute(name).ifEmpty { null }

        private fun Element.childElements(tag: String): List<Element> {
            val out = ArrayList<Element>()
            val list = childNodes
            for (i in 0 until list.length) {
                val n = list.item(i)
                if (n is Element && n.tagName == tag) out += n
            }
            return out
        }
    }
}
