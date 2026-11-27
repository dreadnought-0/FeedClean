package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorConfig
import io.github.dreadnought0.feedclean.selectors.SelectorConfigLoader
import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pre-loaded pages, mid-swipe, foreign windows, and broken/empty configs. */
class ScreenParserRobustnessTest {

    @Test
    fun preloadedPagesWithSignals_neverLeakIntoCurrentPage() {
        val roots = XmlUiNode.parseResource("${Fixtures.SYNTHETIC}/preloaded_pages.xml")
        val s = Fixtures.parser.parse(roots, Fixtures.NOW) as ScreenSnapshot.FeedVideo
        assertEquals("Creator A", s.displayName)
        assertEquals("creator_a123", s.probableHandle)
        assertEquals(Signals(), s.signals)
        assertEquals(Bounds(0, 0, 1080, 2376), s.pageBounds)
        assertEquals(Bounds(0, 0, 1080, 2376), s.videoNode?.bounds)
    }

    @Test
    fun midSwipe_picksThePageWithTheLargestVisibleArea() {
        val xml = """
            <hierarchy rotation="0">
              <node class="androidx.viewpager.widget.ViewPager" resource-id="p:id/viewpager" package="com.zhiliaoapp.musically" scrollable="true" bounds="[0,0][1080,2376]">
                <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" bounds="[0,-1000][1080,1376]">
                  <node class="android.widget.Button" resource-id="p:id/title" package="com.zhiliaoapp.musically" text="Creator A" bounds="[34,1200][246,1250]" />
                  <node class="android.view.View" resource-id="p:id/long_press_layout" package="com.zhiliaoapp.musically" content-desc="Video" clickable="true" bounds="[0,-1000][1080,1376]" />
                </node>
                <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" bounds="[0,1376][1080,3752]">
                  <node class="android.widget.Button" resource-id="p:id/ibp" package="com.zhiliaoapp.musically" text="Ad" bounds="[34,1500][84,1536]" />
                  <node class="android.widget.Button" resource-id="p:id/title" package="com.zhiliaoapp.musically" text="Brand D" bounds="[34,1400][300,1450]" />
                  <node class="android.view.View" resource-id="p:id/long_press_layout" package="com.zhiliaoapp.musically" content-desc="Video" clickable="true" bounds="[0,1376][1080,3752]" />
                </node>
              </node>
            </hierarchy>
        """.trimIndent()
        val s = Fixtures.parser.parse(XmlUiNode.parseString(xml), Fixtures.NOW) as ScreenSnapshot.FeedVideo
        assertEquals("Creator A", s.displayName)
        assertFalse(s.signals.isPaidAd)
        assertEquals(Bounds(0, 0, 1080, 1376), s.pageBounds)
    }

    @Test
    fun foreignWindows_areIgnored_evenWithMatchingText() {
        val overlay = XmlUiNode.parseString(
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="io.github.dreadnought0.feedclean" content-desc="Bottom sheet" bounds="[0,1000][1080,2000]">
                <node class="android.widget.Button" package="io.github.dreadnought0.feedclean" content-desc="Not interested" clickable="true" bounds="[0,1000][300,1200]" />
                <node class="android.widget.Button" package="io.github.dreadnought0.feedclean" content-desc="Block" clickable="true" text="Block" bounds="[300,1000][600,1200]" />
                <node class="android.widget.TextView" package="io.github.dreadnought0.feedclean" text="@someone" bounds="[0,1200][600,1300]" />
              </node>
            </hierarchy>
            """.trimIndent(),
        )
        val roots = overlay + Fixtures.roots("a_normal")
        val s = Fixtures.parser.parse(roots, Fixtures.NOW)
        assertTrue("got $s", s is ScreenSnapshot.FeedVideo)
    }

    @Test
    fun noTikTokWindow_isUnknown() {
        val s = Fixtures.parser.parse(emptyList(), Fixtures.NOW)
        assertTrue(s is ScreenSnapshot.Unknown)
    }

    @Test
    fun unrecognizedBottomSheetOverFeed_isUnknown() {
        val sheet = XmlUiNode.parseString(
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" content-desc="Bottom sheet" bounds="[0,1000][1080,2486]">
                <node class="android.widget.TextView" package="com.zhiliaoapp.musically" text="42 comments" bounds="[0,1000][1080,1100]" />
              </node>
            </hierarchy>
            """.trimIndent(),
        )
        val s = Fixtures.parser.parse(sheet + Fixtures.roots("a_normal"), Fixtures.NOW)
        assertTrue("automation must not act on the feed under an unknown sheet: $s", s is ScreenSnapshot.Unknown)
    }

    @Test
    fun emptyConfig_degradesToUnknown_forEveryFixture() {
        val parser = ScreenParser(SelectorConfig.EMPTY)
        for (name in Fixtures.ALL_COVER) {
            assertTrue(name, parser.parse(Fixtures.roots(name), Fixtures.NOW) is ScreenSnapshot.Unknown)
        }
    }

    @Test
    fun partialConfig_withBrokenRegexAndMissingKeys_neverThrows() {
        val config = SelectorConfigLoader.parse(
            """
            { "schema_version": 1, "package": "com.zhiliaoapp.musically",
              "locales": { "en": {
                "feed.pager": [ { "class": "androidx.viewpager.widget.ViewPager", "scrollable": true } ],
                "feed.video": [ { "descEquals": "Video" } ],
                "feed.displayName": [ { "descRegex": "([unclosed" }, { "viewId": "title" } ],
                "feed.probableHandle": [ { "descRegex": "^Sound: original sound - (\\S+) by (.+)$", "group": 9, "group2Equals": "feed.displayName" } ],
                "profile.displayName": [ { "above": "profile.displayName", "textRegex": "x" } ]
              } } }
            """.trimIndent(),
        )
        assertTrue(config.warnings.any { it.contains("invalid descRegex") })
        val parser = ScreenParser(config)
        for (name in Fixtures.ALL_COVER) parser.parse(Fixtures.roots(name), Fixtures.NOW)
        val s = parser.parse(Fixtures.roots("a_normal"), Fixtures.NOW) as ScreenSnapshot.FeedVideo
        assertEquals("Creator A", s.displayName)
        assertTrue(s.matchedStrategies["feed.displayName"]!!.startsWith("2/2"))
        assertEquals(null, s.probableHandle)
        assertEquals(Signals(), s.signals)
    }

    @Test
    fun parserHelpers_useTextOnlyStrategies() {
        val p = Fixtures.parser
        assertTrue(p.isShoppingReason("I don't want to see shopping content"))
        assertFalse(p.isShoppingReason("Not relevant to me"))
        assertTrue(p.isNotInterestedConfirmation("Thanks for your feedback"))
        assertFalse(p.isNotInterestedConfirmation(null))
    }
}
