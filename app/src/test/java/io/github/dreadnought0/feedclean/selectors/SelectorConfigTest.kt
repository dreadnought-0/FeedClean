package io.github.dreadnought0.feedclean.selectors

import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SelectorConfigTest {

    /** Unit tests run with the app/ module as the working directory. */
    private val assetFile = File("src/main/assets/${SelectorConfigLoader.ASSET_PATH}")
    private val config by lazy { assetFile.inputStream().use { SelectorConfigLoader.load(it) } }

    @Test
    fun bundledAsset_loadsCleanly() {
        assertEquals(1, config.schemaVersion)
        assertEquals("com.zhiliaoapp.musically", config.packageName)
        assertTrue("47.7.3" in config.verifiedAppVersions)
        assertTrue("47.9.3" in config.verifiedAppVersions)
        assertEquals("no warnings expected: ${config.warnings}", emptyList<String>(), config.warnings)
        assertEquals("en", config.forLocale("en").locale)
    }

    @Test
    fun bundledAsset_isStrictStandardJson() {
        // No // comments or trailing commas in the bundled file, so any JSON tool can read it.
        val root = Json.parseToJsonElement(assetFile.readText()).jsonObject
        assertTrue(root["locales"] is JsonObject)
    }

    @Test
    fun bundledAsset_definesEveryKeyTheParserReads() {
        val en = config.forLocale("en")
        val missing = SelectorKeys.ALL.filterNot { en.hasKey(it) }
        assertEquals("missing keys: $missing", emptyList<String>(), missing)
        val empty = SelectorKeys.ALL.filter { it in en.strategies && en.strategies.getValue(it).isEmpty() }
        assertEquals("keys without strategies: $empty", emptyList<String>(), empty)
    }

    @Test
    fun bundledAsset_labelLists() {
        val en = config.forLocale("en")
        assertEquals(listOf("Ad", "Sponsored"), en.labels[SelectorKeys.FEED_PAID_AD_LABELS])
        assertEquals(listOf("Promotional content", "Promoted", "Paid partnership"), en.labels[SelectorKeys.FEED_PROMOTED_LABELS])
        assertTrue("Shop now" in en.labels.getValue(SelectorKeys.FEED_CTA_LABELS))
        assertEquals(listOf("Shop"), en.labels[SelectorKeys.PROFILE_SHOP_CATEGORY_LABELS])
    }

    @Test
    fun bundledAsset_placeholdersAreMarkedUnverified_capturedKeysAreNot() {
        val en = config.forLocale("en")
        val placeholders = listOf(
            SelectorKeys.LONGPRESS_REASON_TITLE, SelectorKeys.LONGPRESS_REASON_OPTION, SelectorKeys.LONGPRESS_REASON_SHOP,
            SelectorKeys.VERIFY_NOT_INTERESTED, SelectorKeys.FEED_LIVE_SHOPPING, SelectorKeys.FEED_LIVE_SHOPPING_TEXT,
        )
        for (key in placeholders) {
            assertTrue("$key should be unverified", en.strategies.getValue(key).all { it.unverified })
        }
        for (key in SelectorKeys.ALL - placeholders.toSet()) {
            val strategies = en.strategies[key] ?: continue
            assertFalse("$key was captured on device", strategies.any { it.unverified })
        }
    }

    @Test
    fun obfuscatedViewIds_areNeverThePrimaryStrategy() {
        // TikTok's obfuscated IDs are 2-3 characters (ibp, t92, tei -> tmm in 47.9.3) and shift every release, so they
        // may only be hints after a semantic strategy. Semantic IDs (title, desc, text1, viewpager, ...) are longer.
        val en = config.forLocale("en")
        val offenders = en.strategies.filter { (_, list) ->
            val id = list.firstOrNull()?.viewId
            id != null && id.length <= OBFUSCATED_ID_MAX_LENGTH
        }.keys
        assertEquals("primary strategy uses an obfuscated ID: $offenders", emptySet<String>(), offenders)
    }

    @Test
    fun topFractionStrategies_alwaysHaveARealPredicate_andAFractionInRange() {
        val en = config.forLocale("en")
        for ((key, list) in en.strategies) {
            for (s in list) {
                val fraction = s.topFraction ?: continue
                assertTrue("$key: $s", s.hasPredicate)
                assertTrue("$key: $fraction", fraction > 0.0 && fraction <= 1.0)
            }
        }
        val keys = en.strategies.filterValues { list -> list.any { it.topFraction != null } }.keys
        assertEquals(setOf(SelectorKeys.FEED_HOME_TOP_TABS, SelectorKeys.FEED_SEARCH_PLAYER_BAR), keys)
    }

    @Test
    fun unknownFields_areIgnoredAtEveryLevel() {
        val cfg = SelectorConfigLoader.parse(
            """
            {
              "schema_version": 1, "package": "pkg", "future_top_level": { "x": [1, 2] },
              "locales": {
                "en": {
                  "_comment": "ignored",
                  "k": [ { "textEquals": "Hello", "futurePredicate": "ignored", "weight": 3 } ],
                  "labels": ["A", "B"]
                },
                "future_locale_meta": {}
              }
            }
            """.trimIndent(),
        )
        assertEquals(emptyList<String>(), cfg.warnings)
        val matcher = SelectorMatcher(cfg.forLocale("en"))
        val nodes = XmlUiNode.parseString(
            """<hierarchy><node text="Hello" package="pkg" bounds="[0,0][10,10]" /></hierarchy>""",
        )
        assertEquals("Hello", matcher.first("k", nodes)?.value)
        assertEquals(listOf("A", "B"), matcher.labels("labels"))
        assertFalse(cfg.forLocale("en").hasKey("_comment"))
    }

    @Test
    fun missingKey_isNoMatch_neverThrows() {
        val matcher = SelectorMatcher(config.forLocale("en"))
        val nodes = XmlUiNode.parseString("""<hierarchy><node text="x" bounds="[0,0][10,10]" /></hierarchy>""")
        assertNull(matcher.first("does.not.exist", nodes))
        assertTrue(matcher.all("does.not.exist", nodes).isEmpty())
        assertTrue(matcher.labels("does.not.exist").isEmpty())
        assertFalse(matcher.isLabel("does.not.exist", "x"))
        assertFalse(matcher.matchesText("does.not.exist", "x"))
        assertNull(SelectorMatcher(SelectorConfig.EMPTY.forLocale()).first(SelectorKeys.FEED_VIDEO, nodes))
    }

    @Test
    fun overrideFiles_mayUseCommentsAndTrailingCommas() {
        val cfg = SelectorConfigLoader.parse(
            """
            {
              // user override
              "schema_version": 1,
              "package": "pkg",
              "locales": { "en": { "k": [ { "textEquals": "x", }, ], }, },
            }
            """.trimIndent(),
        )
        assertEquals(1, cfg.forLocale().strategies.getValue("k").size)
    }

    @Test
    fun malformedEntries_areDroppedWithWarnings() {
        val cfg = SelectorConfigLoader.parse(
            """
            { "schema_version": 7, "package": "pkg", "locales": { "en": {
                "notAList": { "textEquals": "x" },
                "mixed": [ "label", { "textEquals": "x" }, 42 ],
                "badType": [ { "clickable": "sometimes" }, { "textEquals": "ok" } ],
                "noPredicate": [ { "tapFallback": true } ],
                "badRegex": [ { "textRegex": "(" } ]
            } } }
            """.trimIndent(),
        )
        val en = cfg.forLocale()
        assertTrue(cfg.warnings.any { it.contains("schema_version 7") })
        assertTrue(cfg.warnings.any { it.contains("notAList") })
        assertFalse(en.hasKey("notAList"))
        assertEquals(1, en.strategies.getValue("mixed").size)
        assertEquals(listOf("ok"), en.strategies.getValue("badType").map { it.textEquals })
        assertTrue(cfg.warnings.any { it.contains("noPredicate") })
        assertTrue(cfg.warnings.any { it.contains("badRegex") })
        val nodes = XmlUiNode.parseString("""<hierarchy><node text="x" bounds="[0,0][10,10]" /></hierarchy>""")
        val matcher = SelectorMatcher(en)
        assertNull(matcher.first("noPredicate", nodes))
        assertNull(matcher.first("badRegex", nodes))
    }

    @Test(expected = SelectorConfigException::class)
    fun notJson_throwsSelectorConfigException() {
        SelectorConfigLoader.parse("this is not json")
    }

    @Test(expected = SelectorConfigException::class)
    fun wrongTopLevelShape_throwsSelectorConfigException() {
        SelectorConfigLoader.parse("""{ "locales": [1, 2, 3] }""")
    }

    private companion object {
        const val OBFUSCATED_ID_MAX_LENGTH = 3
    }

    @Test
    fun forLocale_fallsBackToLanguageThenEnglish() {
        assertEquals("en", config.forLocale("en-US").locale)
        assertEquals("en", config.forLocale("en_GB").locale)
        assertEquals("en", config.forLocale("fr").locale)
        assertEquals("", SelectorConfig.EMPTY.forLocale("en").locale)
    }
}
