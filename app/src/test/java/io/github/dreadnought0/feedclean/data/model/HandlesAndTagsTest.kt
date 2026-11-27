package io.github.dreadnought0.feedclean.data.model

import io.github.dreadnought0.feedclean.detect.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandlesAndTagsTest {
    @Test
    fun handleNormalization() {
        assertEquals("some.user_1", Handles.normalize("  @Some.User_1 "))
        assertEquals("plain", Handles.normalize("plain"))
        assertNull(Handles.normalize("@"))
        assertNull(Handles.normalize("   "))
        assertNull(Handles.normalize(null))
    }

    @Test
    fun defaultHandleDetection() {
        assertTrue(Handles.isDefault("user123456"))
        assertTrue(Handles.isDefault("user1234567890123"))
        assertFalse(Handles.isDefault("user12345"))
        assertFalse(Handles.isDefault("myuser123456"))
        assertFalse(Handles.isDefault("user123456a"))
        assertFalse(Handles.isDefault("username"))
    }

    @Test
    fun tagNameNormalization() {
        assertEquals("ai_generated", TagNames.normalize("  AI   Generated "))
        assertEquals("paid_ad", TagNames.normalize("paid_ad"))
        assertEquals("", TagNames.normalize("   "))
    }

    @Test
    fun defaultTagsAreUniqueAndKinded() {
        val names = TagNames.DEFAULTS.map { it.first }
        assertEquals(10, names.toSet().size)
        assertEquals(
            setOf(TagNames.PAID_AD, TagNames.PROMOTED, TagNames.SHOP_ANCHOR, TagNames.ORGANIC_SHOP, TagNames.LIVE_SHOPPING),
            TagNames.DEFAULTS.filter { it.second == TagKind.SIGNAL }.map { it.first }.toSet(),
        )
        TagNames.DEFAULTS.forEach { (name, _) -> assertEquals(name, TagNames.normalize(name)) }
    }

    @Test
    fun signalTagsFollowTheDetector() {
        assertEquals(emptyList<String>(), TagNames.forSignals(Signals()))
        assertEquals(
            listOf(TagNames.SHOP_ANCHOR, TagNames.ORGANIC_SHOP),
            TagNames.forSignals(Signals(hasShopAnchor = true)),
        )
        // A paid ad with a Shop anchor is not organic Shop.
        assertEquals(
            listOf(TagNames.PAID_AD, TagNames.SHOP_ANCHOR),
            TagNames.forSignals(Signals(isPaidAd = true, hasShopAnchor = true)),
        )
        assertEquals(
            listOf(TagNames.PROMOTED, TagNames.LIVE_SHOPPING),
            TagNames.forSignals(Signals(isPromoted = true, isLiveShopping = true)),
        )
    }

    @Test
    fun sessionRatios() {
        val session = Session(1, 0, null, videosSeen = 40, ads = 4, promoted = 2, shop = 10, live = 1, skipped = 8)
        val ratio = SessionRatio.of(session)
        assertEquals(0.1, ratio.adRatio, 1e-9)
        assertEquals(0.05, ratio.promotedRatio, 1e-9)
        assertEquals(0.25, ratio.shopRatio, 1e-9)
        assertEquals(0.025, ratio.liveRatio, 1e-9)
        assertEquals(0.2, ratio.skippedRatio, 1e-9)

        val empty = SessionRatio.of(session.copy(videosSeen = 0))
        assertEquals(0.0, empty.adRatio, 0.0)
        assertEquals(0.0, empty.skippedRatio, 0.0)
    }

    @Test
    fun timelineEntryTimes() {
        val sighting = Sighting(1, 1, 500, null, "sig", Signals(), SightingSource.OVERLAY, UserAction.NONE, null, null, null, null)
        val block = BlockEvent(1, 1, 700, BlockMethod.ASSISTED, true, null, null)
        assertEquals(500, TimelineEntry.SightingEntry(sighting).atMillis)
        assertEquals(700, TimelineEntry.BlockEntry(block).atMillis)
    }
}
