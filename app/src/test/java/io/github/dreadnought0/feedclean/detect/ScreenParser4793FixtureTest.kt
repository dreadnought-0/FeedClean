package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorConfig
import io.github.dreadnought0.feedclean.selectors.SelectorKeys
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parser against the scrubbed TikTok 47.9.3 cover-screen fixtures (captured 2026-11-25). */
class ScreenParser4793FixtureTest {

    private fun assertPrimary(snapshot: ScreenSnapshot, vararg keys: String) {
        for (key in keys) {
            val description = snapshot.matchedStrategies[key]
            assertNotNull("$key should match", description)
            assertTrue("$key should match its primary strategy, got '$description'", description!!.startsWith("1/"))
        }
    }

    private fun search() = Fixtures.parseRootsAs<ScreenSnapshot.FeedVideo>(Fixtures.roots4793("m_search_player"))

    @Test
    fun searchResultsPlayer_isAFeedVideoInTheSearchContext() {
        val s = search()
        assertEquals("Creator M", s.displayName)
        assertEquals("placeholder caption for video M from a search result #fyp…more", s.caption)
        assertEquals("Original sound by Creator M", s.soundDescription)
        assertTrue(s.matchedStrategies[SelectorKeys.FEED_SOUND]!!.startsWith("2/"))
        assertNull("the 47.9.3 sound credit has no handle", s.probableHandle)
        assertEquals(Signals(), s.signals)
        assertEquals(AdConfidence.NONE, s.adConfidence)
        assertEquals(FeedContext.SEARCH, s.feedContext)
        assertFalse(s.isForYouTab)
        assertEquals(Bounds(0, 0, 1080, 2376), s.pageBounds)
        assertEquals("androidx.viewpager.widget.ViewPager", s.pagerNode?.className)
        assertEquals("viewpager", s.pagerNode?.viewIdName)
        assertTrue(s.pagerNode!!.isScrollable)
        assertEquals("Video", s.videoNode?.contentDescription)
        assertEquals("Creator M profile", s.avatarNode?.contentDescription)
        assertEquals("Creator M", s.displayNameNode?.text)
        assertTrue(Regex("^[0-9a-f]{40}$").matches(s.pageSignature))
        assertEquals(CurationDecision.Keep, CurationPolicy.decide(s.signals, FeedCleanSettings()))
        assertPrimary(
            s, SelectorKeys.FEED_PAGER, SelectorKeys.FEED_VIDEO, SelectorKeys.FEED_DISPLAY_NAME, SelectorKeys.FEED_AVATAR,
            SelectorKeys.FEED_CAPTION, SelectorKeys.FEED_SEARCH_PLAYER_BAR,
        )
    }

    @Test
    fun searchResultsPlayer_outerPagerAndResourceRefDescs_areIgnored() {
        val all = Fixtures.roots4793("m_search_player").flatMap { it.walk().toList() }
        // The outer pager (obfuscated class, was X.06v5 in 47.7.3) is never the skip target.
        val outer = all.filter { it.viewIdName == "viewpager" && it.className != "androidx.viewpager.widget.ViewPager" }
        assertTrue(outer.isNotEmpty())
        assertTrue(outer.none { it === search().pagerNode })
        // "@<digits>" resource-reference descs look like handles but are descs: never a Profile.
        assertTrue(all.any { it.contentDescription?.startsWith("@21318") == true })
        assertTrue(Fixtures.parseRoots(Fixtures.roots4793("m_search_player")) is ScreenSnapshot.FeedVideo)
    }

    @Test
    fun every4793Fixture_parsesToAKnownSnapshot_withKnownKeys() {
        val expected = mapOf(
            "m_search_player" to ScreenSnapshot.FeedVideo::class,
            "n_profile_product_popup" to ScreenSnapshot.DismissibleSheet::class,
        )
        for (name in Fixtures.ALL_COVER_47_9_3) {
            val s = Fixtures.parseRoots(Fixtures.roots4793(name))
            assertEquals(name, expected[name], s::class)
            for (key in s.matchedStrategies.keys) assertTrue("$name: unexpected key $key", key in SelectorKeys.ALL)
        }
        val synthetic = Fixtures.parseRoots(Fixtures.syntheticRoots("profile_with_product_sheet"))
        assertTrue(synthetic is ScreenSnapshot.DismissibleSheet)
    }

    @Test
    fun emptyConfig_degradesToUnknown_for4793Fixtures() {
        val parser = ScreenParser(SelectorConfig.EMPTY)
        for (name in Fixtures.ALL_COVER_47_9_3) {
            assertTrue(name, parser.parse(Fixtures.roots4793(name), Fixtures.NOW) is ScreenSnapshot.Unknown)
        }
    }

    @Test
    fun pageSignatures_differAcrossVersionsAndPlayers() {
        val videos = listOf("a_normal", "b_paid_ad", "c_promoted", "d_shop_affiliate")
            .map { Fixtures.parseAs<ScreenSnapshot.FeedVideo>(it).pageSignature }
        assertFalse(search().pageSignature in videos)
        assertNotEquals(search().pageSignature, SignalDetector.pageSignature("Creator M", null, null))
    }
}
