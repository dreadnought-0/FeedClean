package io.github.dreadnought0.feedclean.share

import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLogicTest {

    private fun feed(at: Long, probable: String? = null, signals: Signals = Signals()) = ScreenSnapshot.FeedVideo(
        capturedAtMillis = at,
        displayName = "Example Name",
        probableHandle = probable,
        caption = null,
        soundDescription = null,
        signals = signals,
        pageSignature = "sig-$at",
        isForYouTab = true,
    )

    // ── ShareContext ──

    @Test
    fun pickTakesOnlyFreshFeedSnapshots() {
        val now = 100_000L
        val max = ShareContext.MAX_AGE_MILLIS
        assertEquals(feed(now - (max - 1)), ShareContext.pick(listOf(feed(now - (max - 1))), now))
        assertNull(ShareContext.pick(listOf(feed(now - max)), now))
        assertNull(ShareContext.pick(listOf(feed(now + 10)), now))
        assertNull(ShareContext.pick(listOf(ScreenSnapshot.Unknown(now)), now))
        assertNull(ShareContext.pick(listOf(null), now))
        assertEquals(feed(now - 100), ShareContext.pick(listOf(feed(now - 3_000), null, feed(now - 100)), now))
    }

    @Test
    fun contextContradictsOnlyOnDifferentHandles() {
        assertTrue(ShareContext.contradicts(feed(0, probable = "someone"), "other"))
        assertFalse(ShareContext.contradicts(feed(0, probable = "@Someone"), "someone"))
        assertFalse(ShareContext.contradicts(feed(0, probable = null), "someone"))
        assertFalse(ShareContext.contradicts(feed(0, probable = "someone"), null))
        assertFalse(ShareContext.contradicts(null, "someone"))
    }

    @Test
    fun urlSignatureIsStableAndIgnoresTrackingParameters() {
        val a = ShareContext.urlSignature("https://vm.tiktok.com/ZMabc/?_t=1&_r=1")
        val b = ShareContext.urlSignature("https://VM.tiktok.com/ZMabc")
        val other = ShareContext.urlSignature("https://vm.tiktok.com/ZMxyz/")
        assertEquals(a, b)
        assertNotEquals(a, other)
        assertTrue(a.startsWith("share:"))
        assertEquals("share:".length + 32, a.length)
    }

    // ── ShareTagSelection ──

    @Test
    fun detectedTagsArePreselectedFromSignals() {
        val selection = ShareTagSelection(Signals(hasShopAnchor = true))
        assertEquals(setOf(TagNames.SHOP_ANCHOR, TagNames.ORGANIC_SHOP), selection.selectedTags)
        assertEquals(emptySet<String>(), selection.manualTags)
    }

    @Test
    fun deselectingDetectedTagClearsTheSignal() {
        val paid = ShareTagSelection(Signals(isPaidAd = true, hasShopAnchor = true))
        val toggled = paid.toggle(TagNames.PAID_AD)

        assertFalse(toggled.signals.isPaidAd)
        assertTrue(toggled.signals.hasShopAnchor)
        // Without the paid label, a Shop anchor is organic Shop: the chips show what will be recorded.
        assertEquals(setOf(TagNames.SHOP_ANCHOR, TagNames.ORGANIC_SHOP), toggled.selectedTags)

        val noOrganic = ShareTagSelection(Signals(hasShopAnchor = true)).toggle(TagNames.ORGANIC_SHOP)
        assertEquals(emptySet<String>(), noOrganic.selectedTags)
    }

    @Test
    fun otherTagsToggleAsManual() {
        val selection = ShareTagSelection()
            .toggle(TagNames.AI_GENERATED)
            .toggle(TagNames.PAID_AD)
            .toggle(TagNames.BOT_LIKE)
            .toggle(TagNames.BOT_LIKE)

        assertEquals(setOf(TagNames.AI_GENERATED, TagNames.PAID_AD), selection.manualTags)
        assertEquals(Signals(), selection.signals)
    }

    @Test
    fun manualChipsUseTheDataLayerTagNames() {
        assertEquals(
            listOf("ai_generated", "sexual_content", "impersonator", "reposted", "bot_like"),
            ShareTagSelection.MANUAL_TAGS,
        )
        val defaults = TagNames.DEFAULTS.map { it.first }.toSet()
        assertTrue(defaults.containsAll(ShareTagSelection.MANUAL_TAGS + ShareTagSelection.SIGNAL_TAGS))
    }

    // ── notes ──

    @Test
    fun appendNoteKeepsExistingAndSkipsRepeats() {
        assertEquals("new", ShareSaver.appendNote(null, "new"))
        assertEquals("old\nnew", ShareSaver.appendNote("old", "new"))
        assertEquals("old\nnew", ShareSaver.appendNote("old\nnew", "new"))
    }
}
