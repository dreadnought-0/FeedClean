package io.github.dreadnought0.feedclean.audit

import java.io.StringReader
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Synthetic export shapes (handles are placeholders). Every shape is checked with both the streaming scanner
 * and the whole-tree finder, which must agree.
 */
class BlockListFinderTest {

    private val jan2 = 1_767_323_045_000L // 2026-01-02T03:04:05Z
    private val jan3 = 1_767_398_400_000L // 2026-01-03T00:00:00Z

    private fun scanBoth(json: String): BlockListScan {
        val streamed = BlockListFinder.find(StringReader(json))
        val tree = BlockListFinder.find(Json.parseToJsonElement(json))
        assertEquals("streaming and tree scans differ", tree, streamed)
        return streamed
    }

    @Test
    fun tiktokLikeNestedShape() {
        val scan = scanBoth(
            """
            {
              "Activity": { "Following List": { "Following": [ { "Date": "2025-12-01 10:00:00", "UserName": "followed_one" } ] } },
              "App Settings": {
                "Block List": { "BlockList": [
                  { "Date": "2026-01-02 03:04:05", "UserName": "Creator.One" },
                  { "Date": "2026-01-03 00:00:00", "UserName": "@shop_two" }
                ] }
              }
            }
            """.trimIndent(),
        )
        assertTrue(scan.found)
        assertEquals(listOf(ExportedBlock("creator.one", jan2), ExportedBlock("shop_two", jan3)), scan.entries)
        assertEquals(0, scan.skippedEntries)
    }

    @Test
    fun differentKeyNamesAndDateFormats() {
        val scan = scanBoth(
            """
            {
              "blocked_users": [
                { "uniqueId": "seconds_user", "blockedAt": 1767323045 },
                { "user_name": "millis_user", "timestamp": 1767323045000 },
                { "Handle": "iso_user", "time": "2026-01-02T03:04:05Z" },
                { "BlockedUserName": "day_user", "BlockDate": "2026-01-03" },
                { "username": "no_date_user" }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                ExportedBlock("day_user", jan3),
                ExportedBlock("iso_user", jan2),
                ExportedBlock("millis_user", jan2),
                ExportedBlock("no_date_user", null),
                ExportedBlock("seconds_user", jan2),
            ),
            scan.entries,
        )
    }

    @Test
    fun blockListInsideArraysAndProfileUrls() {
        val scan = scanBoth(
            """
            { "data": [ { "Privacy": { "Blocks": { "list": [
                { "user_name": "https://www.tiktok.com/@url.user?lang=en", "Date": "garbage" }
            ] } } } ] }
            """.trimIndent(),
        )
        assertEquals(listOf(ExportedBlock("url.user", null)), scan.entries)
    }

    @Test
    fun emptyBlockListIsFoundButEmpty() {
        val scan = scanBoth("""{ "App Settings": { "Block List": { "BlockList": [] } } }""")
        assertTrue(scan.found)
        assertEquals(emptyList<ExportedBlock>(), scan.entries)
    }

    @Test
    fun noBlockListIsNotFound() {
        val scan = scanBoth(
            """
            { "Following": [ { "UserName": "someone" } ], "BlockSetting": "on", "blocked": 3,
              "Blocked Keywords": [ "word" ], "BlockNumbers": [ { "count": 1 } ] }
            """.trimIndent(),
        )
        assertFalse(scan.found)
        assertEquals(emptyList<ExportedBlock>(), scan.entries)
        assertEquals(BlockListScan.NOT_FOUND, BlockListFinder.find(StringReader("[]")))
    }

    @Test
    fun invalidEntriesAreCountedAndDuplicatesKeepEarliestDate() {
        val scan = scanBoth(
            """
            { "BlockList": [
                { "UserName": "x" },
                { "UserName": "bad handle!" },
                { "Nickname": "Display Only" },
                "plain string",
                { "UserName": "dupe_user", "Date": "2026-01-03 00:00:00" },
                { "UserName": "valid_user" }
              ],
              "More": { "OldBlockList": [ { "UserName": "DUPE_USER", "Date": "2026-01-02 03:04:05" } ] } }
            """.trimIndent(),
        )
        assertEquals(listOf(ExportedBlock("dupe_user", jan2), ExportedBlock("valid_user", null)), scan.entries)
        assertEquals(4, scan.skippedEntries)
    }

    @Test
    fun skippedRegionsMayContainAnyJson() {
        val scan = scanBoth(
            """
            { "Video": { "Desc": "has } and \" and é { [ block", "n": [1, -2.5e3, true, false, null, {}, []] },
              "Comments": [ { "text": "\\\"BlockList\\\": [" } ],
              "Block": [ { "UserName": "ok_user" } ] }
            """.trimIndent(),
        )
        assertEquals(listOf(ExportedBlock("ok_user", null)), scan.entries)
    }

    @Test
    fun malformedJsonIsRejected() {
        listOf(
            """{ "BlockList": [ { "UserName": "a" }""",
            """{ "BlockList": [ { "UserName": "a" } ] } trailing""",
            """{ "a": tru }""",
            """{ "a": "unterminated }""",
            """{ "a" 1 }""",
            "[".repeat(1000) + "]".repeat(1000),
        ).forEach { json ->
            try {
                BlockListFinder.find(StringReader(json))
                fail("expected ExportParseException for: ${json.take(40)}")
            } catch (_: ExportParseException) {
                // expected
            }
        }
    }

    @Test
    fun parseDateAndNormalizeHandle() {
        assertEquals(jan2, BlockListFinder.parseDate("2026-01-02 03:04:05"))
        assertEquals(jan2, BlockListFinder.parseDate("2026-01-02T05:04:05+02:00"))
        assertEquals(jan2, BlockListFinder.parseDate("1767323045"))
        assertNull(BlockListFinder.parseDate("12"))
        assertNull(BlockListFinder.parseDate("yesterday"))
        assertEquals("some.one", BlockListFinder.normalizeHandle(" @Some.One "))
        assertNull(BlockListFinder.normalizeHandle("a"))
        assertNull(BlockListFinder.normalizeHandle("has space"))
    }
}
