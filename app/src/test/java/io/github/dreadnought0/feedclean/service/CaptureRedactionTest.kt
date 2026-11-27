package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.Fixtures
import io.github.dreadnought0.feedclean.detect.TreeXmlWriter
import io.github.dreadnought0.feedclean.detect.TreeXmlWriter.Field.CONTENT_DESCRIPTION
import io.github.dreadnought0.feedclean.detect.TreeXmlWriter.Field.TEXT
import io.github.dreadnought0.feedclean.detect.walk
import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneOffset

class CaptureRedactionTest {
    private val redactor = SelectorRedactor(Fixtures.config.forLocale("en"))
    private val r = SelectorRedactor.REDACTED

    @get:Rule
    val tmp = TemporaryFolder()

    // ── Redactor ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun knownLabelsAndFixedStringsAreKept() {
        assertEquals("Ad", redactor.redactValue(TEXT, "Ad"))
        assertEquals("Promotional content", redactor.redactValue(TEXT, "Promotional content"))
        assertEquals("Shop now", redactor.redactValue(TEXT, "Shop now"))
        assertEquals("Product description", redactor.redactValue(CONTENT_DESCRIPTION, "Product description"))
        assertEquals("Not interested", redactor.redactValue(CONTENT_DESCRIPTION, "Not interested"))
        assertEquals("Block", redactor.redactValue(TEXT, "Block"))
        assertEquals("For You", redactor.redactValue(TEXT, "For You"))
    }

    @Test
    fun everythingElseIsRedacted() {
        assertEquals(r, redactor.redactValue(TEXT, "My weekend trip #fyp"))
        assertEquals(r, redactor.redactValue(TEXT, "Jane Friend"))
        assertEquals(r, redactor.redactValue(CONTENT_DESCRIPTION, "Jane Friend"))
        assertEquals("fixed strings are field-specific", r, redactor.redactValue(CONTENT_DESCRIPTION, "For You Page fan"))
    }

    @Test
    fun namesAndHandlesInsideKnownPatternsAreMasked() {
        assertEquals("Block $r ($r)?", redactor.redactValue(TEXT, "Block Jane Doe (jane.doe)?"))
        assertEquals("$r profile", redactor.redactValue(CONTENT_DESCRIPTION, "Jane Doe profile"))
        assertEquals("Follow $r", redactor.redactValue(CONTENT_DESCRIPTION, "Follow Jane Doe"))
        assertEquals("You blocked $r", redactor.redactValue(TEXT, "You blocked jane.doe"))
        assertEquals("@$r", redactor.redactValue(TEXT, "@jane.doe"))
    }

    @Test
    fun theMostRedactingMatchWins() {
        val sound = redactor.redactValue(CONTENT_DESCRIPTION, "Sound: original sound - jane.doe by Jane Doe")
        assertFalse(sound, sound.contains("jane"))
        assertFalse(sound, sound.contains("Jane"))
        assertTrue(sound.startsWith("Sound: "))
    }

    @Test
    fun onlyTheMatchedPartOfAFreeTextIsKept() {
        assertEquals("LIVE Event$r", redactor.redactValue(TEXT, "LIVE Event · Today 1:00 PM"))
    }

    @Test
    fun structuralStrategiesNeverKeepText() {
        // feed.displayName uses viewId=title + textRegex=\S: that must not keep every title.
        assertEquals(r, redactor.redactValue(TEXT, "SELLER B"))
    }

    @Test
    fun fixtureCaptureContainsNoCreatorNamesOrCaptions() {
        val roots = Fixtures.roots("d_shop_affiliate")
        val xml = TreeXmlWriter.toXml(roots, redactor)
        assertFalse(xml.contains("SELLER B"))
        assertFalse(xml.contains("Product Title E"))
        assertTrue("signals survive", xml.contains("Product description"))
        // Round trip: the redacted capture still parses as a tree of the same shape.
        val parsed = XmlUiNode.parseString(xml)
        assertEquals(roots.sumOf { it.walk().count() }, parsed.sumOf { it.walk().count() })
    }

    @Test
    fun shareSheetContactsAreRedacted() {
        val xml = TreeXmlWriter.toXml(Fixtures.roots("f_profile_share_sheet"), redactor)
        val texts = Regex("""text="([^"]*)"""").findAll(xml).map { it.groupValues[1] }.filter { it.isNotEmpty() }.toSet()
        val selectors = Fixtures.config.forLocale("en")
        val fixed = selectors.strategies.values.flatten().mapNotNull { it.textEquals }.toSet() +
            selectors.labels.values.flatten()
        val leaked = texts.filterNot { t -> t in fixed || t.contains(r) }
        assertTrue("unexpected text kept: $leaked", leaked.isEmpty())
    }

    // ── Store ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun storeWritesListsOpensAndDeletes() {
        val store = CaptureStore(tmp.root.resolve("captures"), ZoneOffset.UTC)
        val a = store.write("<a/>", 0L)
        val b = store.write("<b/>", 0L)
        assertEquals("19700101-000000-000.xml", a.name)
        assertEquals("19700101-000000-000-1.xml", b.name)
        assertEquals(2, store.list().size)
        assertEquals("<a/>", store.open(a.name)!!.bufferedReader().readText())
        assertTrue(store.delete(a.name))
        assertEquals(listOf(b.name), store.list().map { it.name })
    }

    @Test
    fun storeRejectsPathTricks() {
        val store = CaptureStore(tmp.root.resolve("captures"), ZoneOffset.UTC)
        store.write("<a/>", 0L)
        tmp.newFile("secret.xml")
        assertNull(store.open("../secret.xml"))
        assertFalse(store.delete("../secret.xml"))
        assertNull(store.open("sub/x.xml"))
        assertNull(store.open("notes.txt"))
        assertTrue(tmp.root.resolve("secret.xml").exists())
    }

    @Test
    fun emptyStoreListsNothing() {
        assertTrue(CaptureStore(tmp.root.resolve("none")).list().isEmpty())
    }
}
