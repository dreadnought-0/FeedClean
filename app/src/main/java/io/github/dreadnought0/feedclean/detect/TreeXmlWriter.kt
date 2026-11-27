package io.github.dreadnought0.feedclean.detect

/**
 * Writes a [UiNode] forest as `uiautomator dump`-compatible XML, for in-app screen captures that become test
 * fixtures. Pure; no Android dependencies.
 *
 * Output: `<hierarchy rotation="…">` with one top-level `<node>` per window root, and the same attributes as
 * uiautomator dumps (index, text, resource-id, class, package, content-desc, checkable, checked, clickable, enabled,
 * focusable, focused, scrollable, long-clickable, password, selected, bounds, drawing-order, hint). Values the
 * [UiNode] interface doesn't carry are written as uiautomator's defaults. One extra attribute, `visible-to-user`,
 * preserves [UiNode.isVisibleToUser] (uiautomator itself skips invisible nodes).
 *
 * Privacy: captures can contain friends' names and locations. Pass a [Redactor] and never persist unredacted
 * output outside app-private storage.
 */
object TreeXmlWriter {
    /** Attribute a [Redactor] is asked about. */
    enum class Field { TEXT, CONTENT_DESCRIPTION }

    /** Replaces a text/content-desc value before it is written. Return the value unchanged to keep it. */
    fun interface Redactor {
        fun redact(field: Field, value: String, node: UiNode): String
    }

    private const val MAX_DEPTH = 200

    /** XML for [roots] (topmost window first). */
    fun toXml(roots: List<UiNode>, redactor: Redactor? = null, rotation: Int = 0, includeInvisible: Boolean = true): String =
        buildString { write(roots, this, redactor, rotation, includeInvisible) }

    /**
     * Writes [roots] to [out].
     *
     * @param includeInvisible also write nodes with `isVisibleToUser == false` (uiautomator omits them). Keep this
     *   on for fixtures: the pre-loaded next page is exactly what the parser must learn to ignore.
     */
    fun write(
        roots: List<UiNode>,
        out: Appendable,
        redactor: Redactor? = null,
        rotation: Int = 0,
        includeInvisible: Boolean = true,
    ) {
        out.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>")
        out.append("<hierarchy rotation=\"").append(rotation.toString()).append("\">")
        roots.forEachIndexed { i, root -> writeNode(root, i, 0, out, redactor, includeInvisible) }
        out.append("</hierarchy>")
    }

    private fun writeNode(node: UiNode, index: Int, depth: Int, out: Appendable, redactor: Redactor?, includeInvisible: Boolean) {
        if (!includeInvisible && !node.isVisibleToUser) return
        val text = node.text.orEmpty().let { if (redactor != null && it.isNotEmpty()) redactor.redact(Field.TEXT, it, node) else it }
        val desc = node.contentDescription.orEmpty()
            .let { if (redactor != null && it.isNotEmpty()) redactor.redact(Field.CONTENT_DESCRIPTION, it, node) else it }
        val b = node.bounds
        out.append("<node")
        attr(out, "index", index.toString())
        attr(out, "text", text)
        attr(out, "resource-id", node.viewId.orEmpty())
        attr(out, "class", node.className.orEmpty())
        attr(out, "package", node.packageName.orEmpty())
        attr(out, "content-desc", desc)
        attr(out, "checkable", "false")
        attr(out, "checked", "false")
        attr(out, "clickable", node.isClickable.toString())
        attr(out, "enabled", "true")
        attr(out, "focusable", "false")
        attr(out, "focused", "false")
        attr(out, "scrollable", node.isScrollable.toString())
        attr(out, "long-clickable", node.isLongClickable.toString())
        attr(out, "password", "false")
        attr(out, "selected", node.isSelected.toString())
        attr(out, "visible-to-user", node.isVisibleToUser.toString())
        attr(out, "bounds", "[${b.left},${b.top}][${b.right},${b.bottom}]")
        attr(out, "drawing-order", "0")
        attr(out, "hint", "")
        val children = if (depth < MAX_DEPTH) node.children else emptyList()
        if (children.isEmpty()) {
            out.append(" />")
            return
        }
        out.append(">")
        children.forEachIndexed { i, child -> writeNode(child, i, depth + 1, out, redactor, includeInvisible) }
        out.append("</node>")
    }

    private fun attr(out: Appendable, name: String, value: String) {
        out.append(' ').append(name).append("=\"")
        escapeTo(value, out)
        out.append('"')
    }

    /** XML 1.0 attribute escaping. Characters XML 1.0 can't represent at all become U+FFFD. */
    private fun escapeTo(value: String, out: Appendable) {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '&' -> out.append("&amp;")
                c == '<' -> out.append("&lt;")
                c == '>' -> out.append("&gt;")
                c == '"' -> out.append("&quot;")
                c == '\n' -> out.append("&#10;")
                c == '\r' -> out.append("&#13;")
                c == '\t' -> out.append("&#9;")
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                    out.append(c).append(value[i + 1])
                    i++
                }
                c.isSurrogate() -> out.append('�')
                c < ' ' || c == '￾' || c == '￿' -> out.append('�')
                else -> out.append(c)
            }
            i++
        }
    }
}
