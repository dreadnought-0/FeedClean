package io.github.dreadnought0.feedclean.selectors

import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.UiNode
import io.github.dreadnought0.feedclean.detect.walk
import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorMatcherTest {

    private fun matcher(keysJson: String): SelectorMatcher {
        val cfg = SelectorConfigLoader.parse("""{ "schema_version": 1, "package": "p", "locales": { "en": { $keysJson } } }""")
        return SelectorMatcher(cfg.forLocale())
    }

    private fun nodes(vararg nodeXml: String): List<UiNode> =
        XmlUiNode.parseString("<hierarchy><node bounds=\"[0,0][1080,2520]\">${nodeXml.joinToString("")}</node></hierarchy>")
            .flatMap { it.walk().toList() }

    @Test
    fun firstStrategyThatMatchesWins_andIsDescribed() {
        val m = matcher(""""k": [ { "viewId": "missing" }, { "descRegex": "^(.+) profile$", "group": 1 } ]""")
        val n = nodes("""<node content-desc="Creator A profile" bounds="[0,0][10,10]" />""")
        val match = m.first("k", n)!!
        assertEquals("Creator A", match.value)
        assertEquals(1, match.strategyIndex)
        assertTrue(match.isFallback)
        assertEquals("2/2 descRegex=^(.+) profile$ group=1", match.description)
    }

    @Test
    fun textComparisons_ignoreInvisibleCharacters() {
        val m = matcher(""""k": [ { "textEquals": "Contact 5" } ], "l": [ { "textIn": "labels" } ], "labels": ["promoted"]""")
        val n = nodes(
            """<node text="&#8206;Contact 5&#8206;" bounds="[0,0][10,10]" />""",
            """<node text="Promoted&#8203;" bounds="[0,20][10,30]" />""",
        )
        assertEquals("Contact 5", m.first("k", n)?.value)
        assertEquals("Promoted", m.first("l", n)?.value)
        assertTrue(m.isLabel("labels", "PROMOTED"))
        assertFalse(m.isLabel("labels", "Promotional"))
    }

    @Test
    fun group2Equals_requiresTheReferencedValue() {
        val m = matcher(
            """
            "name": [ { "viewId": "title" } ],
            "handle": [ { "descRegex": "^Sound: original sound - ([a-z0-9._]+) by (.+)$", "group": 1, "group2Equals": "name" } ]
            """,
        )
        val matching = nodes(
            """<node resource-id="p:id/title" text="Creator A" bounds="[0,0][10,10]" />""",
            """<node content-desc="Sound: original sound - creator_a123 by &#120330;reator A" bounds="[0,20][10,30]" />""",
        )
        assertEquals("creator_a123", m.first("handle", matching)?.value) // U+1D60A (sans-serif italic C) NFKC→ C
        val other = nodes(
            """<node resource-id="p:id/title" text="Creator C" bounds="[0,0][10,10]" />""",
            """<node content-desc="Sound: original sound - creator_a123 by Creator A" bounds="[0,20][10,30]" />""",
        )
        assertNull(m.first("handle", other))
        assertEquals("creator_a123", m.first("handle", other, resolved = mapOf("name" to "Creator A"))?.value)
        assertNull(m.first("handle", matching, resolved = mapOf("name" to null)))
    }

    @Test
    fun aboveAndBelow_pickTheNearestOverlappingNode() {
        val m = matcher(
            """
            "handle": [ { "textRegex": "^@(.+)$", "group": 1 } ],
            "name": [ { "textRegex": "\\S", "above": "handle", "maxGap": 80 } ],
            "category": [ { "textRegex": "\\S", "below": "handle", "maxGap": 20 } ]
            """,
        )
        val n = nodes(
            """<node text="Far above" bounds="[348,100][732,150]" />""",
            """<node text="Display Name" bounds="[348,386][732,436]" />""",
            """<node text="Side" bounds="[800,386][1000,436]" />""",
            """<node text="@handle" bounds="[388,436][692,470]" />""",
            """<node text="Shop" bounds="[414,472][666,512]" />""",
            """<node text="Too far below" bounds="[414,600][666,640]" />""",
        )
        assertEquals("Display Name", m.first("name", n)?.value)
        assertEquals("Shop", m.first("category", n)?.value)
        assertNull(m.first("category", n.filterNot { it.text == "Shop" }))
    }

    @Test
    fun inside_and_excludeViewIds() {
        val m = matcher(
            """
            "anchor": [ { "viewId": "anchor" } ],
            "shop": [ { "textRegex": "Free shipping", "inside": "anchor" } ],
            "label": [ { "textIn": "labels", "excludeViewIds": ["title"] } ],
            "labels": ["Ad"]
            """,
        )
        val n = nodes(
            """<node text="Free shipping in my caption" resource-id="p:id/desc" bounds="[0,0][500,50]" />""",
            """<node resource-id="p:id/anchor" bounds="[0,100][500,200]"><node text="Free shipping" bounds="[10,110][300,150]" /></node>""",
            """<node text="Ad" resource-id="p:id/title" bounds="[0,300][100,350]" />""",
        )
        assertEquals(110, m.first("shop", n)?.node?.bounds?.top)
        assertNull("a creator named 'Ad' is not a disclosure label", m.first("label", n))
    }

    @Test
    fun all_returnsEveryNodeOfTheWinningStrategy() {
        val m = matcher(""""k": [ { "class": "no.such.Class" }, { "clickable": true } ]""")
        val n = nodes(
            """<node clickable="true" text="1" bounds="[0,0][10,10]" />""",
            """<node clickable="false" text="2" bounds="[0,20][10,30]" />""",
            """<node clickable="true" text="3" bounds="[0,40][10,50]" />""",
        )
        assertEquals(listOf("1", "3"), m.all("k", n).map { it.value })
    }

    @Test
    fun selfAndCyclicReferences_neverMatchOrLoop() {
        val m = matcher(
            """
            "a": [ { "textRegex": "x", "inside": "b" } ],
            "b": [ { "textRegex": "x", "inside": "a" } ],
            "c": [ { "textRegex": "x", "above": "c" } ]
            """,
        )
        val n = nodes("""<node text="x" bounds="[0,0][10,10]" />""")
        assertNull(m.first("a", n))
        assertNull(m.first("c", n))
    }

    @Test
    fun matchesText_onlyUsesTextOnlyStrategies() {
        val m = matcher(""""k": [ { "viewId": "toast", "textEquals": "Done" }, { "textRegex": "(?i)thanks" } ]""")
        assertTrue(m.matchesText("k", "Thanks for the feedback"))
        assertFalse("structural strategy can't apply to a bare string", m.matchesText("k", "Done"))
    }

    @Test
    fun topFraction_keepsMatchesInTheTopOfTheFrame_andNeedsAFrame() {
        val m = matcher(""""k": [ { "textEquals": "Search", "topFraction": 0.2 } ]""")
        val frame = Bounds(0, 0, 1080, 2520)
        val top = nodes("""<node text="Search" bounds="[880,107][1044,188]" />""")
        val bottom = nodes("""<node text="Search" bounds="[27,2317][63,2353]" />""")
        val straddling = nodes("""<node text="Search" bounds="[0,480][100,520]" />""")
        assertEquals(107, m.first("k", top, frame = frame)?.node?.bounds?.top)
        assertNull(m.first("k", bottom, frame = frame))
        assertNull("must lie entirely in the top fraction", m.first("k", straddling, frame = frame))
        assertNull("no frame, no match", m.first("k", top))
        assertNull(m.first("k", top, frame = Bounds.EMPTY))
        // The frame's own offset counts (e.g. a window that doesn't start at y=0).
        assertNull(m.first("k", top, frame = Bounds(0, 200, 1080, 2720)))
        assertEquals("1/1 textEquals=Search topFraction=0.2", m.first("k", top, frame = frame)?.description)
        assertFalse("position-dependent: not text-only", m.matchesText("k", "Search"))
    }

    @Test
    fun topFraction_aloneIsNotAPredicate() {
        val cfg = SelectorConfigLoader.parse(
            """{ "schema_version": 1, "package": "p", "locales": { "en": { "k": [ { "topFraction": 0.5 } ] } } }""",
        )
        assertTrue(cfg.warnings.any { it.contains("no predicate") })
        val n = nodes("""<node text="anything" bounds="[0,0][10,10]" />""")
        assertNull(SelectorMatcher(cfg.forLocale()).first("k", n, frame = Bounds(0, 0, 1080, 2520)))
    }
}
