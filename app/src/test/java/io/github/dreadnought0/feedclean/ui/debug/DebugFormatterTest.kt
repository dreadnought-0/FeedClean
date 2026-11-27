package io.github.dreadnought0.feedclean.ui.debug

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DebugFormatterTest {
    @Test
    fun noSnapshot() {
        val text = DebugFormatter.format(serviceConnected = false, tiktokForeground = false, snapshot = null)
        assertEquals("serviceConnected: false\ntiktokForeground: false\nsnapshot: none\n", text)
    }

    @Test
    fun feedVideoShowsFieldsSignalsAndStrategies() {
        val snapshot = ScreenSnapshot.FeedVideo(
            capturedAtMillis = 3_723_004L, // 01:02:03.004 UTC
            matchedStrategies = mapOf("feed.shopAnchor" to "descEquals", "feed.displayName" to "viewId=title"),
            displayName = "Creator",
            probableHandle = "creator.handle",
            caption = null,
            soundDescription = null,
            signals = Signals(hasShopAnchor = true),
            pageSignature = "sig-1",
            isForYouTab = true,
        )
        val text = DebugFormatter.format(true, true, snapshot, ZoneOffset.UTC)
        listOf(
            "serviceConnected: true",
            "snapshot: FeedVideo",
            "capturedAt: 01:02:03.004",
            "displayName: Creator",
            "probableHandle: creator.handle",
            "pageSignature: sig-1",
            "  isOrganicShop: true",
            "  isPaidAd: false",
            "matchedStrategies (2):",
        ).forEach { assertTrue("missing '$it' in:\n$text", text.contains(it)) }
        // Strategies are sorted by key.
        assertTrue(text.indexOf("feed.displayName → viewId=title") < text.indexOf("feed.shopAnchor → descEquals"))
    }

    @Test
    fun longPressMenuListsLabels() {
        val snapshot = ScreenSnapshot.LongPressMenu(
            capturedAtMillis = 0L,
            notInterestedNode = null,
            optionLabels = listOf("Option A", "Option B"),
        )
        val text = DebugFormatter.format(true, true, snapshot, ZoneOffset.UTC)
        assertTrue(text.contains("optionLabels: [Option A, Option B]"))
        assertTrue(text.contains("notInterestedNode: absent"))
        assertTrue(text.contains("  (none)"))
    }

    @Test
    fun typeNames() {
        assertEquals("Unknown", DebugFormatter.typeName(ScreenSnapshot.Unknown(0L)))
        assertEquals("none", DebugFormatter.typeName(null))
    }
}
