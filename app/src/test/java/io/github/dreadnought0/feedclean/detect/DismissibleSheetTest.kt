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
 * [ScreenSnapshot.DismissibleSheet] (TikTok 47.9.3): the PRODUCT bottom sheet that opens over a Shop advertiser's
 * profile. Its own window is the only one uiautomator dumped (`n_profile_product_popup`); the synthetic
 * `profile_with_product_sheet` puts it on top of the 47.7.3 Shop-seller profile window.
 */
class DismissibleSheetTest {

    private fun sheetWindow(): List<XmlUiNode> = Fixtures.roots4793("n_profile_product_popup")

    private fun profileWindow(): List<XmlUiNode> = Fixtures.roots("e_profile_shop")

    private fun synthetic(): List<XmlUiNode> = Fixtures.syntheticRoots("profile_with_product_sheet")

    private fun sheetOf(roots: List<XmlUiNode>) = Fixtures.parseRootsAs<ScreenSnapshot.DismissibleSheet>(roots)

    private fun assertPrimary(s: ScreenSnapshot, vararg keys: String) {
        for (key in keys) {
            val d = s.matchedStrategies[key]
            assertNotNull("$key should match", d)
            assertTrue("$key should match its primary strategy, got '$d'", d!!.startsWith("1/"))
        }
    }

    private fun List<XmlUiNode>.all(): List<UiNode> = flatMap { it.walk().toList() }

    // ── The product sheet ───────────────────────────────────────────────────

    @Test
    fun productSheetAlone_isACommerceDismissibleSheet_withTheScrimAsItsOnlyNode() {
        val s = sheetOf(sheetWindow())
        val scrim = s.dismissNode
        assertNotNull(scrim)
        assertEquals("touch_outside", scrim!!.viewIdName)
        assertTrue(scrim.isClickable)
        assertEquals(Bounds(0, 0, 1080, 2486), scrim.bounds)
        assertTrue(s.isCommerce)
        assertNull("no profile window in this dump", s.underlyingProfile)
        assertPrimary(s, SelectorKeys.SHEET_CONTAINER, SelectorKeys.SHEET_DISMISS_SCRIM, SelectorKeys.SHEET_COMMERCE)
        assertEquals(Fixtures.NOW, s.capturedAtMillis)
    }

    @Test
    fun synthetic_isTheSheetWindowOverTheProfileWindow() {
        val roots = synthetic()
        assertEquals(2, roots.size)
        assertEquals(sheetWindow().single(), roots[0])
        assertEquals(sheetOf(sheetWindow() + profileWindow()), sheetOf(roots).copy())
    }

    @Test
    fun productSheetOverProfile_exposesTheUnderlyingProfileReadOnly() {
        val s = sheetOf(synthetic())
        assertTrue(s.isCommerce)
        assertEquals("touch_outside", s.dismissNode?.viewIdName)
        val p = s.underlyingProfile
        assertNotNull(p)
        assertEquals("user0000000000001", p!!.handle)
        assertEquals("SELLER B", p.displayName)
        assertEquals("Shop", p.accountCategory)
        assertTrue(p.isShopCreator)
        assertFalse(p.isBlockedByMe)
        assertNull("covered by the sheet: dismiss and re-snapshot first", p.shareNode)
        assertNull("covered by the sheet: dismiss and re-snapshot first", p.backNode)
        assertEquals("1/1 descEquals=Share tapFallback", p.matchedStrategies[SelectorKeys.PROFILE_SHARE])
    }

    @Test
    fun afterDismissal_theProfileIsActionableAgain() {
        val p = Fixtures.parseRootsAs<ScreenSnapshot.Profile>(profileWindow())
        assertEquals("Share", p.shareNode?.contentDescription)
        assertEquals(Bounds(972, 102, 1062, 192), p.shareNode?.bounds)
        assertEquals(sheetOf(synthetic()).underlyingProfile, p.copy(shareNode = null, backNode = null))
    }

    // ── The share / Share trap and the sheet's other contents ──────────────

    @Test
    fun profileShare_neverMatchesTheSheetsLowercaseShare() {
        val sheetNodes = sheetWindow().all().filter { it.isShown }
        assertTrue("the sheet has its own lowercase 'share'", sheetNodes.any { it.contentDescription == "share" })
        assertNull("case-sensitive", Fixtures.parser.matcher.first(SelectorKeys.PROFILE_SHARE, sheetNodes))

        // Even if TikTok capitalised the sheet's icon, the profile is parsed from the other windows only.
        val capitalised = sheetWindow().mapTrees { n -> if (n.contentDescription == "share") n.copy(contentDescription = "Share") else n }
        val profileWithoutShare = profileWindow().mapTrees { n -> if (n.contentDescription == "Share") null else n }
        val s = sheetOf(capitalised + profileWithoutShare)
        assertEquals("user0000000000001", s.underlyingProfile?.handle)
        assertNull(s.underlyingProfile!!.matchedStrategies[SelectorKeys.PROFILE_SHARE])
    }

    @Test
    fun nothingInsideTheSheet_isHandedOut() {
        val roots = synthetic()
        val s = sheetOf(roots)
        val sheet = roots[0].walk().single { it.contentDescription == "Bottom sheet" }
        val insideSheet = sheet.walk().toList()
        assertFalse("dismissNode is the scrim beside the sheet, not inside it", insideSheet.any { it === s.dismissNode })
        assertNull(s.underlyingProfile?.shareNode)
        assertNull(s.underlyingProfile?.backNode)
        // The sheet's store name, title texts and "@<digits>" resource descs never become names or handles.
        assertEquals("SELLER B", s.underlyingProfile?.displayName)
        assertNull(sheetOf(sheetWindow()).underlyingProfile)
    }

    @Test
    fun withoutCommerceMarkers_isANonCommerceSheet() {
        val plain = sheetWindow().mapTrees { n ->
            when {
                n.text?.startsWith("Add to cart") == true || n.text?.startsWith("Buy now") == true -> null
                n.contentDescription == "cart" -> null
                else -> n
            }
        }
        val s = sheetOf(plain)
        assertFalse(s.isCommerce)
        assertNull(s.matchedStrategies[SelectorKeys.SHEET_COMMERCE])
        assertEquals("touch_outside", s.dismissNode?.viewIdName)
    }

    // ── When it is NOT a DismissibleSheet ───────────────────────────────────

    @Test
    fun withoutAClickableScrim_staysUnknown() {
        val noScrim = sheetWindow().mapTrees { n -> if (n.viewIdName == "touch_outside") null else n }
        assertTrue(Fixtures.parseRoots(noScrim + profileWindow()) is ScreenSnapshot.Unknown)
        val inert = sheetWindow().mapTrees { n -> if (n.viewIdName == "touch_outside") n.copy(isClickable = false) else n }
        assertTrue(Fixtures.parseRoots(inert + profileWindow()) is ScreenSnapshot.Unknown)
    }

    @Test
    fun aSheetThatIsNotTheTopmostWindow_staysUnknown() {
        val popup = XmlUiNode.parseString(
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" bounds="[100,1000][980,1200]">
                <node class="android.widget.TextView" package="com.zhiliaoapp.musically" text="Placeholder tip" bounds="[120,1020][960,1180]" />
              </node>
            </hierarchy>
            """.trimIndent(),
        )
        assertTrue(Fixtures.parseRoots(popup + synthetic()) is ScreenSnapshot.Unknown)
    }

    @Test
    fun aHalfRenderedKnownSheet_isNeverDismissible() {
        // "Send to" outside the container (so the ShareSheet parser can't see it) but inside the sheet's scope.
        val withTitle = sheetWindow().mapTrees { n ->
            if (n.viewIdName == "co3") {
                n.copy(
                    children = n.children + XmlUiNode(
                        "Send to", null, null, "android.widget.TextView", "com.zhiliaoapp.musically",
                        Bounds(36, 2320, 400, 2350), false, false, false, false, true, emptyList(),
                    ),
                )
            } else {
                n
            }
        }
        assertTrue(Fixtures.parseRoots(withTitle + profileWindow()) is ScreenSnapshot.Unknown)
    }

    @Test
    fun knownSheetsWithAScrim_keepTheirOwnSnapshots() {
        // The 47.7.3 share sheet and long-press menu use the same Material scrim (touch_outside).
        for (name in listOf("f_profile_share_sheet", "j_longpress_menu")) {
            assertTrue(Fixtures.roots(name).all().any { it.viewIdName == "touch_outside" && it.isClickable })
        }
        assertTrue(Fixtures.parse("f_profile_share_sheet", "e_profile_shop") is ScreenSnapshot.ShareSheet)
        assertTrue(Fixtures.parse("j_longpress_menu", "b_paid_ad") is ScreenSnapshot.LongPressMenu)
    }

    // ── Sheets over the feed ────────────────────────────────────────────────

    @Test
    fun productSheetOverTheFeed_isNeverAFeedVideo() {
        for (feed in Fixtures.FOR_YOU_FEEDS) {
            val s = Fixtures.parseRoots(sheetWindow() + Fixtures.roots(feed))
            assertTrue("$feed: $s", s is ScreenSnapshot.DismissibleSheet)
            assertNull("$feed: no profile under the sheet", (s as ScreenSnapshot.DismissibleSheet).underlyingProfile)
            assertTrue(s.isCommerce)
        }
    }

    @Test
    fun otherSheetWithAScrimOverTheFeed_isANonCommerceDismissibleSheet() {
        val comments = XmlUiNode.parseString(
            """
            <hierarchy rotation="0">
              <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" bounds="[0,0][1080,2520]">
                <node class="android.view.ViewGroup" package="com.zhiliaoapp.musically" bounds="[0,0][1080,2486]">
                  <node class="android.view.View" resource-id="com.zhiliaoapp.musically:id/touch_outside" package="com.zhiliaoapp.musically" clickable="true" bounds="[0,0][1080,2486]" />
                  <node class="android.widget.FrameLayout" package="com.zhiliaoapp.musically" content-desc="Bottom sheet" bounds="[0,1000][1080,2486]">
                    <node class="android.widget.TextView" package="com.zhiliaoapp.musically" text="42 comments" bounds="[0,1000][1080,1100]" />
                    <node class="android.widget.Button" resource-id="com.zhiliaoapp.musically:id/title" package="com.zhiliaoapp.musically" text="Viewer 1" clickable="true" bounds="[30,1150][300,1200]" />
                  </node>
                </node>
              </node>
            </hierarchy>
            """.trimIndent(),
        )
        val s = Fixtures.parseRoots(comments + Fixtures.roots("a_normal"))
        assertTrue("automation must not act on the feed under a sheet: $s", s is ScreenSnapshot.DismissibleSheet)
        s as ScreenSnapshot.DismissibleSheet
        assertFalse(s.isCommerce)
        assertNull(s.underlyingProfile)
        assertEquals("touch_outside", s.dismissNode?.viewIdName)
    }
}
