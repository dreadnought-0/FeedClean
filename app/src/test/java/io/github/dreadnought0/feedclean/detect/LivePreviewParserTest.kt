package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorKeys
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-feed LIVE previews: the scrubbed 47.7.3 capture of a plain (gaming) LIVE preview, and the synthetic
 * LIVE-shopping preview derived from it.
 */
class LivePreviewParserTest {

    private val plainXml: String by lazy { Fixtures.resourceText("${Fixtures.COVER_47_7_3}/k_feed_live_preview.xml") }
    private val shoppingXml: String by lazy { Fixtures.resourceText("${Fixtures.SYNTHETIC}/live_shopping_preview.xml") }

    private fun parseXml(xml: String): ScreenSnapshot = Fixtures.parser.parse(XmlUiNode.parseString(xml), Fixtures.NOW)

    private fun feedOf(xml: String): ScreenSnapshot.FeedVideo {
        val s = parseXml(xml)
        check(s is ScreenSnapshot.FeedVideo) { "Expected FeedVideo, got $s" }
        return s
    }

    private fun plain() = Fixtures.parseAs<ScreenSnapshot.FeedVideo>("k_feed_live_preview")

    private fun shopping() = feedOf(shoppingXml)

    /** Adds a TextView with [text] to the current LIVE page, right after the "LIVE" layout. */
    private fun withPageText(xml: String, text: String): String {
        val anchor = """content-desc="LIVE" clickable="true" bounds="[0,0][1080,2376]" />"""
        check(anchor in xml) { "LIVE layout not found" }
        val node = """<node text="$text" class="android.widget.TextView" package="com.zhiliaoapp.musically" """ +
            """bounds="[27,1500][760,1550]" />"""
        return xml.replace(anchor, anchor + node)
    }

    private fun String.replaceOnce(old: String, new: String): String {
        check(old in this) { "'$old' not found" }
        return replace(old, new)
    }

    private fun assertPrimary(snapshot: ScreenSnapshot, vararg keys: String) {
        for (key in keys) {
            val description = snapshot.matchedStrategies[key]
            assertNotNull("$key should match", description)
            assertTrue("$key should match its primary strategy, got '$description'", description!!.startsWith("1/"))
        }
    }

    // ── Plain LIVE preview (real capture) ──────────────────────────────────────────────────────────────────

    @Test
    fun plainLivePreview_isFeedVideoWithLiveSignalsOnly() {
        val s = plain()
        assertEquals("Streamer A", s.displayName)
        assertNull(s.probableHandle)
        assertNull(s.caption)
        assertNull(s.soundDescription)
        assertEquals(Signals(isLive = true, isLivePreview = true), s.signals)
        assertFalse(s.signals.isLiveShopping)
        assertFalse(s.signals.isOrganicShop)
        assertFalse(s.signals.hasAnySignal)
        assertEquals(AdConfidence.NONE, s.adConfidence)
        assertTrue(s.isForYouTab)
        assertEquals(Bounds(0, 0, 1080, 2376), s.pageBounds)
        assertEquals("androidx.viewpager.widget.ViewPager", s.pagerNode?.className)
        assertEquals("viewpager", s.pagerNode?.viewIdName)
        assertTrue(s.pagerNode!!.isScrollable)
        assertEquals("LIVE", s.videoNode?.contentDescription)
        assertEquals("long_press_layout", s.videoNode?.viewIdName)
        assertEquals(Bounds(0, 0, 1080, 2376), s.videoNode?.bounds)
        assertPrimary(
            s, SelectorKeys.FEED_PAGER, SelectorKeys.FEED_LIVE_VIDEO, SelectorKeys.FEED_LIVE_TIPS,
            SelectorKeys.FEED_LIVE_NOW, SelectorKeys.FEED_LIVE_NICKNAME, SelectorKeys.FEED_FOR_YOU_TAB,
        )
        for (videoOnly in listOf(SelectorKeys.FEED_VIDEO, SelectorKeys.FEED_AVATAR, SelectorKeys.FEED_DISPLAY_NAME)) {
            assertNull("$videoOnly is a video selector", s.matchedStrategies[videoOnly])
        }
        assertEquals(CurationDecision.Keep, CurationPolicy.decide(s.signals, FeedCleanSettings()))
    }

    @Test
    fun livePreview_neverHandsOutANodeThatEntersTheLiveRoom() {
        val nickname = Fixtures.roots("k_feed_live_preview").flatMap { it.walk().toList() }
            .single { it.viewIdName == "tv_live_nickname" }
        assertTrue("the nickname button is clickable on screen (a tap opens the LIVE room)", nickname.isClickable)
        for (s in listOf(plain(), shopping())) {
            assertTrue(s.signals.isLivePreview)
            assertNull("avatarNode", s.avatarNode)
            assertNull("displayNameNode", s.displayNameNode)
        }
    }

    @Test
    fun preloadedNextPage_isIgnored() {
        val layouts = Fixtures.roots("k_feed_live_preview").flatMap { it.walk().toList() }
            .filter { it.viewIdName == "long_press_layout" }
        assertEquals(listOf("LIVE", "Video"), layouts.map { it.contentDescription })
        assertEquals("the next page is pre-loaded at zero height", 0, layouts[1].bounds.height)
        assertEquals("LIVE", plain().videoNode?.contentDescription)

        // The synthetic next page is a Shop ad with commerce text and a display name: nothing may leak.
        val s = shopping()
        assertEquals("Seller Streamer B", s.displayName)
        assertTrue(s.signals.isLivePreview)
        assertFalse(s.signals.isPaidAd)
        assertNotEquals("\$9.99 Shop now · Free shipping", s.signals.anchorText)
    }

    @Test
    fun liveDescWithoutTipsOrTipsWithoutDesc_isStillALivePreview() {
        val noDesc = feedOf(plainXml.replaceOnce("""content-desc="LIVE" """, ""))
        assertTrue(noDesc.signals.isLivePreview)
        assertNull("no LIVE layout matched: nothing to hand out", noDesc.videoNode)
        assertNull(noDesc.matchedStrategies[SelectorKeys.FEED_LIVE_VIDEO])
        assertPrimary(noDesc, SelectorKeys.FEED_LIVE_TIPS)
        assertEquals(plain().pageSignature, noDesc.pageSignature)

        val noTips = feedOf(plainXml.replaceOnce("Tap to watch LIVE", "Watch"))
        assertTrue(noTips.signals.isLivePreview)
        assertEquals("LIVE", noTips.videoNode?.contentDescription)
    }

    @Test
    fun nicknameFallback_isTheButtonUnderLiveNow() {
        val s = feedOf(plainXml.replaceOnce("com.zhiliaoapp.musically:id/tv_live_nickname", "com.zhiliaoapp.musically:id/zz9"))
        assertEquals("Streamer A", s.displayName)
        assertTrue(s.matchedStrategies[SelectorKeys.FEED_LIVE_NICKNAME]!!.startsWith("2/2"))
        assertNull(s.displayNameNode)
    }

    @Test
    fun livePreviewWithoutAPager_isNotAFeedPage() {
        val noPager = plainXml.replaceOnce(
            """class="androidx.viewpager.widget.ViewPager" package="com.zhiliaoapp.musically" scrollable="true"""",
            """class="androidx.viewpager.widget.ViewPager" package="com.zhiliaoapp.musically"""",
        )
        assertTrue(parseXml(noPager) is ScreenSnapshot.Unknown)
    }

    // ── LIVE shopping (synthetic) ──────────────────────────────────────────────────────────────────────────

    @Test
    fun liveShoppingPreview_isLiveShoppingAndNeverOrganicShop() {
        val s = shopping()
        assertTrue(s.signals.isLive)
        assertTrue(s.signals.isLivePreview)
        assertTrue(s.signals.isLiveShopping)
        assertTrue(s.signals.hasAnySignal)
        assertFalse(s.signals.hasShopAnchor)
        assertFalse(s.signals.isOrganicShop)
        assertFalse(s.signals.isPaidAd)
        assertFalse(s.signals.isPromoted)
        assertFalse(s.signals.isLiveEvent)
        assertNull(s.signals.ctaText)
        assertEquals("LIVE auction", s.signals.anchorText)
        assertTrue(s.isForYouTab)
        assertEquals("viewpager", s.pagerNode?.viewIdName)
        assertTrue(s.matchedStrategies[SelectorKeys.FEED_LIVE_SHOPPING_TEXT]!!.contains("UNVERIFIED"))
    }

    @Test
    fun eachCommerceText_aloneMarksLiveShopping() {
        val texts = listOf(
            "LIVE auction", "\$14.88 overseas shipping", "Final sale", "Awaiting next item", "12 sold", "\$1",
            "Shop now", "Buy",
        )
        for (text in texts) {
            val s = feedOf(withPageText(plainXml, text))
            assertTrue(text, s.signals.isLiveShopping)
            assertEquals(text, text, s.signals.anchorText)
            assertFalse(text, s.signals.isOrganicShop)
        }
    }

    @Test
    fun ordinaryChatAndTheStreamerName_doNotMarkLiveShopping() {
        for (text in listOf("Viewer 2: hello", "Viewer 3 sent a gift", "Viewer 4 joined", "buy me a coffee", "Shop")) {
            assertFalse(text, feedOf(withPageText(plainXml, text)).signals.isLiveShopping)
        }
        val salesyName = feedOf(plainXml.replaceOnce("Streamer A", "Final Sale Auction \$1"))
        assertEquals("Final Sale Auction \$1", salesyName.displayName)
        assertFalse("the nickname is excluded", salesyName.signals.isLiveShopping)
    }

    @Test
    fun shopTopTab_neverTriggersLiveShopping() {
        val tab = Fixtures.roots("k_feed_live_preview").flatMap { it.walk().toList() }
            .single { it.contentDescription == "Shop" }
        assertNull(Fixtures.parser.matcher.first(SelectorKeys.FEED_LIVE_SHOPPING_TEXT, listOf(tab)))
        assertFalse(plain().signals.isLiveShopping)

        // Even commerce TEXT in the top tabs (outside the page) is never searched.
        val tabsWithCommerce = feedOf(plainXml.replaceOnce("""text="Following"""", """text="Shop now · Final sale ${'$'}1""""))
        assertTrue(tabsWithCommerce.signals.isLivePreview)
        assertFalse(tabsWithCommerce.signals.isLiveShopping)
        assertNull(tabsWithCommerce.signals.anchorText)
    }

    // ── Page signature ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun pageSignature_isStablePerStream_whateverChatGiftOrAuctionTextShows() {
        val base = plain().pageSignature
        assertTrue(Regex("^[0-9a-f]{40}$").matches(base))
        assertEquals(base, plain().pageSignature)
        assertEquals(SignalDetector.livePageSignature("Streamer A"), base)
        assertEquals(base, feedOf(withPageText(plainXml, "Viewer 2: hello")).pageSignature)
        assertEquals(base, feedOf(withPageText(plainXml, "Viewer 4 sent a gift")).pageSignature)

        val first = shopping()
        val later = feedOf(
            shoppingXml
                .replaceOnce("Viewer 2: hello from the chat", "Viewer 5: what size is it?")
                .replaceOnce("Viewer 3 sent a gift", "Viewer 6 sent a gift")
                .replaceOnce("""text="${'$'}1"""", """text="${'$'}2"""")
                .replaceOnce("Viewer 1 won!", "Viewer 7 won!")
                .replaceOnce("Awaiting next item", "Next item soon"),
        )
        assertTrue(later.signals.isLiveShopping)
        assertEquals(first.pageSignature, later.pageSignature)
        assertNotEquals("different streams differ", base, first.pageSignature)
    }

    @Test
    fun pageSignature_ofALivePreview_neverCollidesWithAVideo() {
        val live = plain().pageSignature
        assertNotEquals(SignalDetector.pageSignature("Streamer A", null, null), live)
        val videos = listOf("a_normal", "b_paid_ad", "c_promoted", "d_shop_affiliate")
            .map { Fixtures.parseAs<ScreenSnapshot.FeedVideo>(it).pageSignature }
        assertFalse(live in videos)
    }

    // ── Curation ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun curationPolicy_liveShoppingSkipsOnlyWithItsOwnTarget_plainLiveIsKept() {
        val defaults = FeedCleanSettings()
        val everyTarget = defaults.copy(
            curateFeedEnabled = true, autoSkipOrganicShop = true, autoSkipPaidAds = true, autoSkipPromoted = true,
            autoSkipLiveShopping = true,
        )
        val plain = plain().signals
        val shop = shopping().signals
        val keep = CurationDecision.Keep
        val skipLive = CurationDecision.Skip(SkipReason.LIVE_SHOPPING)

        assertEquals(keep, CurationPolicy.decide(plain, defaults))
        assertEquals(keep, CurationPolicy.decide(plain, everyTarget))
        assertEquals(keep, CurationPolicy.decide(shop, defaults))
        assertEquals(skipLive, CurationPolicy.decide(shop, defaults.copy(autoSkipLiveShopping = true)))
        assertEquals(skipLive, CurationPolicy.decide(shop, everyTarget))
        assertEquals(keep, CurationPolicy.decide(shop, everyTarget.copy(detectLiveShopping = false)))
        assertEquals(keep, CurationPolicy.decide(shop, everyTarget.copy(detectionEnabled = false)))
    }

    @Test
    fun curationPolicy_knownShopStreamerIsStillNotOrganicShop() {
        // FeedPagePipeline may enrich isShopCreator from the database (the streamer is a known Shop seller).
        val defaults = FeedCleanSettings()
        for (signals in listOf(plain().signals, shopping().signals)) {
            val enriched = signals.copy(isShopCreator = true)
            assertFalse(enriched.isOrganicShop)
            assertEquals(CurationDecision.Keep, CurationPolicy.decide(enriched, defaults))
            assertNotEquals(
                CurationDecision.Skip(SkipReason.ORGANIC_SHOP),
                CurationPolicy.decide(enriched.copy(hasShopAnchor = true), defaults.copy(autoSkipLiveShopping = true)),
            )
        }
    }
}
