package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorKeys
import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import io.github.dreadnought0.feedclean.testutil.XmlUiNode.Companion.mapTrees
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FeedContext] of every feed page (TikTok 47.9.3): For You, another Home tab, the search-results player, or any
 * other player. Decided from the screen chrome outside the pager; [ScreenSnapshot.FeedVideo.isForYouTab] always
 * equals `feedContext == FOR_YOU`.
 */
class FeedContextTest {

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun feed(roots: List<XmlUiNode>): ScreenSnapshot.FeedVideo {
        val s = Fixtures.parseRootsAs<ScreenSnapshot.FeedVideo>(roots)
        assertEquals("isForYouTab must equal feedContext == FOR_YOU", s.feedContext == FeedContext.FOR_YOU, s.isForYouTab)
        return s
    }

    private fun contextOf(roots: List<XmlUiNode>): FeedContext = feed(roots).feedContext

    private fun isForYouLabel(n: XmlUiNode) = n.text == FOR_YOU || n.contentDescription == FOR_YOU

    /** Selects the Home top tab labelled [label] and deselects "For You" (its label, desc and tab container). */
    private fun selectTab(roots: List<XmlUiNode>, label: String): List<XmlUiNode> = roots.mapTrees { n ->
        val forYouTab = isForYouLabel(n) || n.children.any { isForYouLabel(it as XmlUiNode) }
        when {
            n.text == label || n.contentDescription == label -> n.copy(isSelected = true)
            forYouTab && label != FOR_YOU -> n.copy(isSelected = false)
            else -> n
        }
    }

    /** Drops the Home top-tab row (every tab label/desc in the top area). */
    private fun withoutHomeTabs(roots: List<XmlUiNode>): List<XmlUiNode> = roots.mapTrees { n ->
        val label = n.text ?: n.contentDescription
        if (label in TAB_LABELS && n.bounds.bottom <= TOP_AREA) null else n
    }

    /** Appends [extra] to the topmost window root (i.e. into the chrome, outside the pager). */
    private fun withChrome(roots: List<XmlUiNode>, vararg extra: XmlUiNode): List<XmlUiNode> =
        listOf(roots[0].copy(children = roots[0].children + extra)) + roots.drop(1)

    private fun node(
        cls: String,
        bounds: Bounds,
        text: String? = null,
        desc: String? = null,
        id: String? = null,
        clickable: Boolean = false,
        selected: Boolean = false,
    ) = XmlUiNode(
        text, desc, id?.let { "$PKG:id/$it" }, cls, PKG, bounds, clickable, false, false, selected, true, emptyList(),
    )

    private fun assertRecorded(s: ScreenSnapshot, key: String, prefix: String = "1/") {
        val d = s.matchedStrategies[key]
        assertNotNull("$key should be recorded", d)
        assertTrue("$key: expected '$prefix…', got '$d'", d!!.startsWith(prefix))
    }

    // ── For You ─────────────────────────────────────────────────────────────

    @Test
    fun forYouFixtures_includingLivePreviews_areForYou() {
        val all = Fixtures.FOR_YOU_FEEDS.map { it to Fixtures.roots(it) } +
            ("live_shopping_preview" to Fixtures.syntheticRoots("live_shopping_preview"))
        for ((name, roots) in all) {
            val s = feed(roots)
            assertEquals(name, FeedContext.FOR_YOU, s.feedContext)
            assertTrue(name, s.isForYouTab)
            assertRecorded(s, SelectorKeys.FEED_FOR_YOU_TAB)
            assertRecorded(s, SelectorKeys.FEED_HOME_TOP_TABS)
            assertNull("$name: the search bar is only checked off the Home tabs", s.matchedStrategies[SelectorKeys.FEED_SEARCH_PLAYER_BAR])
        }
        assertTrue(feed(Fixtures.roots("k_feed_live_preview")).signals.isLivePreview)
    }

    @Test
    fun noPagerFallback_keepsTheForYouContext() {
        // A transient read without a scrollable pager falls back to the video node as the page; the top tabs must
        // still count (AutoSkipGate checks NOT_FOR_YOU before NO_PAGER, and the NO_PAGER hold is retried).
        val roots = Fixtures.roots("a_normal").mapTrees { n ->
            if (n.className == ANDROIDX_PAGER) n.copy(isScrollable = false) else n
        }
        val s = feed(roots)
        assertNull(s.pagerNode)
        assertEquals("Creator A", s.displayName)
        assertEquals(FeedContext.FOR_YOU, s.feedContext)
    }

    // ── Other Home tabs ─────────────────────────────────────────────────────

    @Test
    fun followingSelected_isHomeOtherTab_forVideosAndLivePreviews() {
        for (name in listOf("a_normal", "d_shop_affiliate", "k_feed_live_preview")) {
            val s = feed(selectTab(Fixtures.roots(name), FOLLOWING))
            assertEquals(name, FeedContext.HOME_OTHER_TAB, s.feedContext)
            assertFalse(name, s.isForYouTab)
            assertNull(name, s.matchedStrategies[SelectorKeys.FEED_FOR_YOU_TAB])
            assertRecorded(s, SelectorKeys.FEED_HOME_TOP_TABS)
        }
    }

    @Test
    fun locationTabSelected_isHomeOtherTab() {
        assertEquals(FeedContext.HOME_OTHER_TAB, contextOf(selectTab(Fixtures.roots("a_normal"), "Nearby")))
    }

    @Test
    fun tabOrderAndTheShopTab_neverDecideTheContext() {
        // 47.9.3 (screenshot, 2026-11-25): "<location>" · "Following" · "Shop" (TEXT now) · "For You". Model it: the
        // Shop tab becomes a text1 label, and For You / Following swap places.
        val forYouBounds = Bounds(705, 89, 878, 220)
        val followingBounds = Bounds(373, 89, 576, 219)
        val reordered = Fixtures.roots("a_normal").mapTrees { n ->
            when {
                n.contentDescription == SHOP && n.bounds.bottom <= TOP_AREA ->
                    n.copy(text = SHOP, viewId = "android:id/text1", className = "android.widget.TextView")
                n.bounds == forYouBounds -> n.copy(bounds = followingBounds)
                n.bounds == followingBounds -> n.copy(bounds = forYouBounds)
                else -> n
            }
        }
        assertEquals(FeedContext.FOR_YOU, contextOf(reordered))
        assertEquals(FeedContext.HOME_OTHER_TAB, contextOf(selectTab(reordered, FOLLOWING)))
        assertEquals(FeedContext.HOME_OTHER_TAB, contextOf(selectTab(reordered, SHOP)))
        // The Shop tab is never a commerce signal.
        assertFalse(feed(reordered).signals.hasAnySignal)
    }

    @Test
    fun homeTabs_winOverATopSearchIcon() {
        val icon = node("android.widget.ImageView", Bounds(972, 102, 1062, 192), desc = "Search", clickable = true)
        assertEquals(FeedContext.FOR_YOU, contextOf(withChrome(Fixtures.roots("a_normal"), icon)))
        assertEquals(FeedContext.HOME_OTHER_TAB, contextOf(withChrome(selectTab(Fixtures.roots("a_normal"), FOLLOWING), icon)))
    }

    // ── Search results player ───────────────────────────────────────────────

    @Test
    fun searchResultsPlayer_isSearch() {
        val s = feed(Fixtures.roots4793("m_search_player"))
        assertEquals(FeedContext.SEARCH, s.feedContext)
        assertFalse(s.isForYouTab)
        assertRecorded(s, SelectorKeys.FEED_SEARCH_PLAYER_BAR)
        assertNull(s.matchedStrategies[SelectorKeys.FEED_FOR_YOU_TAB])
        assertNull(s.matchedStrategies[SelectorKeys.FEED_HOME_TOP_TABS])
    }

    @Test
    fun searchBarFallbacks_stillMeanSearch() {
        val m = Fixtures.roots4793("m_search_player")
        val noId = m.mapTrees { n -> if (n.viewIdName == "tv_bar_search") n.copy(viewId = null) else n }
        assertRecorded(feed(noId), SelectorKeys.FEED_SEARCH_PLAYER_BAR, "2/")
        val noButton = m.mapTrees { n -> if (n.viewIdName == "tv_bar_search") null else n }
        assertRecorded(feed(noButton), SelectorKeys.FEED_SEARCH_PLAYER_BAR, "3/")
        assertEquals(FeedContext.SEARCH, feed(noButton).feedContext)
    }

    @Test
    fun inPageSearchSuggestionBar_neverMakesAPageSearch() {
        // For You pages carry "Search · <suggestion>" (ImageView desc "Search" + TextView) inside the page.
        val d = Fixtures.roots("d_shop_affiliate")
        assertTrue(d.flatMap { it.walk().toList() }.any { it.text?.startsWith("Search ·") == true })
        assertEquals(FeedContext.OTHER, contextOf(withoutHomeTabs(d)))

        // The search player without its top bar: the in-page suggestion bar remains, and it is not enough.
        val m = Fixtures.roots4793("m_search_player")
        val bottomBar = m.flatMap { it.walk().toList() }.single { it.viewIdName == "vwi" }
        assertTrue(bottomBar.text!!.startsWith("Search ·"))
        assertEquals(FeedContext.OTHER, contextOf(m.mapTrees { n -> if (n.viewIdName == "j8v") null else n }))
    }

    @Test
    fun searchLookAlikes_belowTheTopOfTheWindow_neverCount() {
        // Even in the chrome (outside the pager), a search bar near the bottom is not the search player's top bar.
        val low = Bounds(880, 2300, 1044, 2380)
        val lookAlikes = arrayOf(
            node("android.widget.Button", low, text = "Search", id = "tv_bar_search", clickable = true),
            node("android.widget.ImageView", Bounds(27, 2317, 63, 2353), desc = "Search"),
            node("android.widget.TextView", Bounds(72, 2317, 800, 2353), text = "placeholder", id = "tv_search_sug_word"),
        )
        assertEquals(FeedContext.OTHER, contextOf(withChrome(withoutHomeTabs(Fixtures.roots("a_normal")), *lookAlikes)))
    }

    // ── Other players ───────────────────────────────────────────────────────

    @Test
    fun noHomeTabsAndNoSearchBar_isOther() {
        assertEquals(FeedContext.OTHER, contextOf(withoutHomeTabs(Fixtures.roots("a_normal"))))
        assertEquals(FeedContext.OTHER, contextOf(withoutHomeTabs(Fixtures.roots("k_feed_live_preview"))))
        assertEquals(FeedContext.OTHER, contextOf(Fixtures.syntheticRoots("preloaded_pages")))
    }

    @Test
    fun profileFollowingCountLabel_isNotAHomeTab() {
        val profileLabel = node("android.widget.TextView", Bounds(201, 578, 426, 614), text = FOLLOWING, id = "td1")
        val lowTab = node("android.widget.TextView", Bounds(373, 1500, 576, 1560), text = FOLLOWING, id = "text1")
        val roots = withChrome(withoutHomeTabs(Fixtures.roots("a_normal")), profileLabel, lowTab)
        assertEquals(FeedContext.OTHER, contextOf(roots))
    }

    @Test
    fun tabsInsideTheVideoPage_neverCount() {
        // A selected "For You" text drawn inside the page (e.g. a sticker) is page content, not chrome.
        val roots = withoutHomeTabs(Fixtures.roots("a_normal")).mapTrees { n ->
            if (n.viewIdName == "long_press_layout" && n.bounds.height > 0) {
                n.copy(
                    children = n.children + node(
                        "android.widget.TextView", Bounds(705, 89, 878, 220), text = FOR_YOU, id = "text1", selected = true,
                    ),
                )
            } else {
                n
            }
        }
        assertEquals(FeedContext.OTHER, contextOf(roots))
    }

    private companion object {
        const val PKG = "com.zhiliaoapp.musically"
        const val FOR_YOU = "For You"
        const val FOLLOWING = "Following"
        const val SHOP = "Shop"
        const val ANDROIDX_PAGER = "androidx.viewpager.widget.ViewPager"

        /** The Home tab row ends at y≈220 on the 2520 px cover screen. */
        const val TOP_AREA = 300
        val TAB_LABELS = setOf(FOR_YOU, FOLLOWING, SHOP, "Nearby")
    }
}
