package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeXmlWriterTest {

    @Test
    fun roundTrip_everyFixture_isStructurallyIdentical() {
        for (name in Fixtures.ALL_COVER) {
            val roots = Fixtures.roots(name)
            val reparsed = XmlUiNode.parseString(TreeXmlWriter.toXml(roots))
            assertEquals(name, roots, reparsed)
        }
    }

    @Test
    fun roundTrip_multiWindow_keepsWindowOrder() {
        val roots = Fixtures.roots("g_block_confirm") + Fixtures.roots("e_profile_shop")
        val reparsed = XmlUiNode.parseString(TreeXmlWriter.toXml(roots))
        assertEquals(2, reparsed.size)
        assertEquals(roots, reparsed)
        assertTrue(Fixtures.parser.parse(reparsed, Fixtures.NOW) is ScreenSnapshot.BlockDialog)
    }

    @Test
    fun output_isUiautomatorShaped() {
        val xml = TreeXmlWriter.toXml(Fixtures.roots("g_block_confirm"), rotation = 1)
        assertTrue(xml.startsWith("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"1\"><node "))
        assertTrue(xml.endsWith("</hierarchy>"))
        val firstNode = Regex("<node ([^>]*)>").find(xml)!!.groupValues[1]
        val attrs = Regex("""([a-z-]+)="""").findAll(firstNode).map { it.groupValues[1] }.toList()
        val expected = listOf(
            "index", "text", "resource-id", "class", "package", "content-desc", "checkable", "checked", "clickable",
            "enabled", "focusable", "focused", "scrollable", "long-clickable", "password", "selected", "bounds",
            "drawing-order", "hint",
        )
        assertTrue("missing ${expected - attrs.toSet()}", attrs.containsAll(expected))
        assertTrue(attrs.contains("visible-to-user"))
    }

    @Test
    fun redactor_replacesTextAndDescBeforeWriting() {
        val roots = Fixtures.roots("f_profile_share_sheet")
        val redactor = TreeXmlWriter.Redactor { field, value, _ ->
            if (value.contains("Contact")) "${field.name}-REDACTED" else value
        }
        val xml = TreeXmlWriter.toXml(roots, redactor = redactor)
        assertFalse(xml.contains("Contact"))
        assertTrue(xml.contains("TEXT-REDACTED"))
        assertTrue(xml.contains("CONTENT_DESCRIPTION-REDACTED"))
        assertTrue("non-matching values kept", xml.contains("content-desc=\"Block\""))
    }

    @Test
    fun escaping_survivesSpecialAndInvalidCharacters() {
        val tricky = "a<b>&\"c'\n\t\r\u0001💎\uD800 end"
        val node = XmlUiNode(
            text = tricky, contentDescription = "desc & <more>", viewId = "p:id/x", className = "android.widget.TextView",
            packageName = "p", bounds = Bounds(0, 0, 10, 10), isClickable = true, isLongClickable = false,
            isScrollable = false, isSelected = true, isVisibleToUser = true, children = emptyList(),
        )
        val back = XmlUiNode.parseString(TreeXmlWriter.toXml(listOf(node))).single()
        assertEquals("a<b>&\"c'\n\t\r�💎� end", back.text)
        assertEquals("desc & <more>", back.contentDescription)
        assertTrue(back.isClickable)
        assertTrue(back.isSelected)
    }

    @Test
    fun invisibleNodes_keepTheirFlag_orCanBeOmitted() {
        val hidden = XmlUiNode(
            text = "hidden", contentDescription = null, viewId = null, className = "android.view.View", packageName = "p",
            bounds = Bounds(0, 2376, 1080, 4752), isClickable = false, isLongClickable = false, isScrollable = false,
            isSelected = false, isVisibleToUser = false, children = emptyList(),
        )
        val root = hidden.copy(text = "root", bounds = Bounds(0, 0, 1080, 2520), isVisibleToUser = true, children = listOf(hidden))
        val kept = XmlUiNode.parseString(TreeXmlWriter.toXml(listOf(root))).single()
        assertFalse(kept.children.single().isVisibleToUser)
        val omitted = XmlUiNode.parseString(TreeXmlWriter.toXml(listOf(root), includeInvisible = false)).single()
        assertTrue(omitted.children.isEmpty())
    }
}
