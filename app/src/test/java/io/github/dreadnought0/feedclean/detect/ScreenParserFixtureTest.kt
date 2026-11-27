package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.detect.Fixtures.parse
import io.github.dreadnought0.feedclean.detect.Fixtures.parseAs
import io.github.dreadnought0.feedclean.selectors.SelectorKeys
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser + signals against every scrubbed 47.7.3 cover-screen fixture. */
class ScreenParserFixtureTest {

    private fun assertPrimary(snapshot: ScreenSnapshot, vararg keys: String) {
        for (key in keys) {
            val description = snapshot.matchedStrategies[key]
            assertNotNull("$key should match", description)
            assertTrue("$key should match its primary strategy, got '$description'", description!!.startsWith("1/"))
        }
    }

    // (a) normal For You video
    @Test
    fun normalVideo_isFeedVideoWithoutSignals() {
        val s = parseAs<ScreenSnapshot.FeedVideo>("a_normal")
        assertEquals(Fixtures.NOW, s.capturedAtMillis)
        assertEquals("Creator A", s.displayName)
        assertEquals("creator_a123", s.probableHandle)
        assertEquals("Sound: original sound - creator_a123 by Creator A", s.soundDescription)
        assertEquals("placeholder caption for video A #fyp #viral #foryoupage", s.caption)
        assertFalse(s.signals.hasAnySignal)
        assertFalse(s.signals.isLive)
        assertFalse(s.signals.isLiveEvent)
        assertNull(s.signals.disclosureLabel)
        assertNull(s.signals.ctaText)
        assertTrue(s.isForYouTab)
        assertEquals(Bounds(0, 0, 1080, 2376), s.pageBounds)
        assertEquals("androidx.viewpager.widget.ViewPager", s.pagerNode?.className)
        assertTrue(s.pagerNode!!.isScrollable)
        assertEquals(Bounds(0, 0, 1080, 2376), s.videoNode?.bounds)
        assertEquals("Creator A profile", s.avatarNode?.contentDescription)
        assertEquals("Creator A", s.displayNameNode?.text)
        assertEquals(CurationDecision.Keep, CurationPolicy.decide(s.signals, FeedCleanSettings()))
        assertPrimary(
            s, SelectorKeys.FEED_PAGER, SelectorKeys.FEED_VIDEO, SelectorKeys.FEED_DISPLAY_NAME,
            SelectorKeys.FEED_AVATAR, SelectorKeys.FEED_PROBABLE_HANDLE, SelectorKeys.FEED_CAPTION,
            SelectorKeys.FEED_SOUND, SelectorKeys.FEED_FOR_YOU_TAB,
        )
    }

    // (b) paid ad, labelled "Ad"
    @Test
    fun paidAd_isPaidAdAndNotOrganicShop() {
        val s = parseAs<ScreenSnapshot.FeedVideo>("b_paid_ad")
        assertEquals("Brand D", s.displayName)
        assertTrue(s.signals.isPaidAd)
        assertFalse(s.signals.isPromoted)
        assertFalse(s.signals.isOrganicShop)
        assertEquals("Ad", s.signals.disclosureLabel)
        assertEquals("Shop now", s.signals.ctaText)
        assertNull("Promoted Music is not an original-sound credit", s.probableHandle)
        assertTrue(s.isForYouTab)
        assertPrimary(s, SelectorKeys.FEED_DISCLOSURE_LABEL, SelectorKeys.FEED_CTA, SelectorKeys.FEED_PROMOTED_MUSIC)
    }

    @Test
    fun paidAd_corroboratingEvidenceRaisesConfidence() {
        val page = feedPageNodes("b_paid_ad")
        val report = SignalDetector(Fixtures.parser.matcher).detect(page)
        assertEquals(AdConfidence.HIGH, report.adConfidence)
        assertEquals(listOf(SelectorKeys.FEED_PROMOTED_MUSIC, SelectorKeys.FEED_CTA), report.corroborating)

        val normal = SignalDetector(Fixtures.parser.matcher).detect(feedPageNodes("a_normal"))
        assertEquals(AdConfidence.NONE, normal.adConfidence)
    }

    // (c) "Promotional content", LIVE Event anchor, creator currently live
    @Test
    fun promoted_isPromotedLiveAndLiveEvent() {
        val s = parseAs<ScreenSnapshot.FeedVideo>("c_promoted")
        assertEquals("Creator C", s.displayName)
        assertEquals("creator.c", s.probableHandle)
        assertTrue(s.signals.isPromoted)
        assertFalse(s.signals.isPaidAd)
        assertTrue(s.signals.isLive)
        assertTrue(s.signals.isLiveEvent)
        assertFalse(s.signals.hasShopAnchor)
        assertFalse(s.signals.isOrganicShop)
        assertEquals("Promotional content", s.signals.disclosureLabel)
        assertEquals("LIVE Event · Today 1:00 PM", s.signals.anchorText)
        assertNull("LIVE avatar has no profile desc and opens the LIVE room", s.avatarNode)
        assertPrimary(
            s, SelectorKeys.FEED_LIVE_BADGE, SelectorKeys.FEED_LIVE_EVENT_ANCHOR, SelectorKeys.FEED_DISPLAY_NAME,
        )
        // The label TEXT decides (promoted-labels strategy), never the obfuscated slot ID (last-resort hint).
        assertEquals(
            "2/3 textIn=feed.promotedLabels excludeViewIds=title,desc",
            s.matchedStrategies[SelectorKeys.FEED_DISCLOSURE_LABEL],
        )
    }

    // (d) organic Shop post with product anchor (photo carousel)
    @Test
    fun shopPost_hasShopAnchorAndIsOrganicShop() {
        val s = parseAs<ScreenSnapshot.FeedVideo>("d_shop_affiliate")
        assertEquals("SELLER B", s.displayName)
        assertTrue(s.signals.hasShopAnchor)
        assertTrue(s.signals.isOrganicShop)
        assertFalse(s.signals.isPaidAd)
        assertFalse(s.signals.isPromoted)
        assertNull(s.signals.disclosureLabel)
        assertEquals("Product Title E · Earn 5X points · Free shipping", s.signals.anchorText)
        assertNull("sound credit is not an original sound", s.probableHandle)
        assertEquals(
            CurationDecision.Skip(SkipReason.ORGANIC_SHOP),
            CurationPolicy.decide(s.signals, FeedCleanSettings()),
        )
        assertPrimary(s, SelectorKeys.FEED_SHOP_ANCHOR, SelectorKeys.FEED_ANCHOR)
    }

    // (e) profile of a Shop seller
    @Test
    fun shopProfile_hasHandleCategoryAndShopCreator() {
        val s = parseAs<ScreenSnapshot.Profile>("e_profile_shop")
        assertEquals("user0000000000001", s.handle)
        assertEquals("SELLER B", s.displayName)
        assertEquals("Shop", s.accountCategory)
        assertTrue(s.isShopCreator)
        assertFalse(s.isBlockedByMe)
        assertNull(s.blockedMessageHandle)
        assertEquals("Share", s.shareNode?.contentDescription)
        assertTrue(NameNormalizer.isDefaultHandle(s.handle))
        assertPrimary(s, SelectorKeys.PROFILE_HANDLE, SelectorKeys.PROFILE_DISPLAY_NAME, SelectorKeys.PROFILE_CATEGORY)
        assertTrue(s.matchedStrategies[SelectorKeys.PROFILE_SHARE]!!.contains("tapFallback"))
    }

    // (f) profile share sheet
    @Test
    fun profileShareSheet_isShareSheetWithBlockNode() {
        val s = parseAs<ScreenSnapshot.ShareSheet>("f_profile_share_sheet")
        val block = s.blockNode
        assertNotNull(block)
        assertEquals("Block", block!!.contentDescription)
        assertTrue(block.isClickable)
        assertEquals("Close", s.closeNode?.contentDescription)
        assertPrimary(s, SelectorKeys.SHEET_CONTAINER, SelectorKeys.SHEET_BLOCK, SelectorKeys.SHEET_TITLE)
    }

    @Test
    fun profileShareSheet_overProfile_isStillShareSheet() {
        assertTrue(parse("f_profile_share_sheet", "e_profile_shop") is ScreenSnapshot.ShareSheet)
    }

    // (g) block confirmation dialog
    @Test
    fun blockDialog_hasHandleNameAndButtons() {
        val s = parseAs<ScreenSnapshot.BlockDialog>("g_block_confirm")
        assertEquals("Block SELLER B (user0000000000001)?", s.titleText)
        assertEquals("SELLER B", s.displayName)
        assertEquals("user0000000000001", s.handle)
        assertEquals("Block", s.confirmNode?.text)
        assertEquals("android.widget.Button", s.confirmNode?.className)
        assertEquals("Cancel", s.cancelNode?.text)
        assertPrimary(s, SelectorKeys.DIALOG_CONTAINER, SelectorKeys.DIALOG_TITLE, SelectorKeys.DIALOG_CONFIRM)
    }

    @Test
    fun blockDialog_overProfileWindow_winsByPriority() {
        assertTrue(parse("g_block_confirm", "e_profile_shop") is ScreenSnapshot.BlockDialog)
    }

    // (h) profile after the block
    @Test
    fun blockedProfile_isBlockedByMeWithMessageHandle() {
        val s = parseAs<ScreenSnapshot.Profile>("h_profile_blocked")
        assertEquals("user0000000000001", s.handle)
        assertEquals("SELLER B", s.displayName)
        assertTrue(s.isBlockedByMe)
        assertEquals("user0000000000001", s.blockedMessageHandle)
        assertNull("category label disappears after a block", s.accountCategory)
        assertFalse(s.isShopCreator)
        assertPrimary(s, SelectorKeys.VERIFY_UNBLOCK, SelectorKeys.VERIFY_BLOCKED_MSG)
    }

    // (j) long-press menu on a paid ad
    @Test
    fun longPressMenu_hasNotInterestedNodeAndOptionRowOnly() {
        val s = parseAs<ScreenSnapshot.LongPressMenu>("j_longpress_menu")
        val ni = s.notInterestedNode
        assertNotNull(ni)
        assertEquals("Not interested", ni!!.contentDescription)
        assertEquals("android.widget.Button", ni.className)
        assertTrue(ni.isClickable)
        assertEquals(listOf("About this ad", "Report", "Not interested", "Playback speed"), s.optionLabels)
        assertTrue(s.reasonNodes.isEmpty())
        assertPrimary(s, SelectorKeys.LONGPRESS_CONTAINER, SelectorKeys.LONGPRESS_NOT_INTERESTED, SelectorKeys.LONGPRESS_AD_OPTION)
        assertTrue(Fixtures.parser.isAdMenu(s))
    }

    @Test
    fun longPressMenu_neverExposesContacts() {
        val s = parseAs<ScreenSnapshot.LongPressMenu>("j_longpress_menu")
        val everything = s.optionLabels + s.reasonNodes.keys + s.matchedStrategies.values
        for (value in everything) {
            assertFalse("contact leaked: $value", value.contains("Contact"))
            assertFalse("contact row leaked: $value", value == "Send" || value == "Share with")
        }
    }

    @Test
    fun longPressMenu_overFeed_winsByPriority() {
        val s = parse("j_longpress_menu", "b_paid_ad")
        assertTrue(s is ScreenSnapshot.LongPressMenu)
    }

    @Test
    fun profileShareSheet_isNotALongPressMenu() {
        val s = parse("f_profile_share_sheet")
        assertTrue(s is ScreenSnapshot.ShareSheet)
        assertFalse(Fixtures.parser.isAdMenu(ScreenSnapshot.LongPressMenu(0, notInterestedNode = null)))
    }

    // Cross-fixture properties
    @Test
    fun pageSignature_stableAcrossReparse_andDistinctBetweenPages() {
        val feeds = listOf("a_normal", "b_paid_ad", "c_promoted", "d_shop_affiliate")
        val first = feeds.map { parseAs<ScreenSnapshot.FeedVideo>(it).pageSignature }
        val second = feeds.map { parseAs<ScreenSnapshot.FeedVideo>(it).pageSignature }
        assertEquals(first, second)
        assertEquals(feeds.size, first.toSet().size)
        first.forEach { assertTrue("SHA-1 hex: $it", Regex("^[0-9a-f]{40}$").matches(it)) }
    }

    @Test
    fun preloadedNextPage_inRealFixtures_isIgnored() {
        for (name in listOf("a_normal", "c_promoted", "d_shop_affiliate")) {
            val all = Fixtures.roots(name).flatMap { it.walk().toList() }
            val videos = all.filter { it.viewIdName == "long_press_layout" }
            assertTrue("$name has a zero-height pre-loaded page", videos.any { it.bounds.height == 0 })
            val s = parseAs<ScreenSnapshot.FeedVideo>(name)
            assertEquals(Bounds(0, 0, 1080, 2376), s.videoNode?.bounds)
        }
    }

    @Test
    fun everyFixture_parsesWithoutThrowing_andEveryMatchUsesAKnownKey() {
        for (name in Fixtures.ALL_COVER) {
            val s = parse(name)
            assertNotEquals(ScreenSnapshot.Unknown::class, s::class)
            for (key in s.matchedStrategies.keys) assertTrue("$name: unexpected key $key", key in SelectorKeys.ALL)
        }
    }

    /** Shown nodes inside the visible page, from the same tree the parser saw (node handles are identities). */
    private fun feedPageNodes(name: String): List<UiNode> {
        val roots = Fixtures.roots(name)
        val s = Fixtures.parser.parse(roots, Fixtures.NOW) as ScreenSnapshot.FeedVideo
        return s.pagerNode!!.walk()
            .filter { it.isVisibleToUser && !it.bounds.isEmpty && it.bounds.intersects(s.pageBounds) }
            .toList()
    }
}
