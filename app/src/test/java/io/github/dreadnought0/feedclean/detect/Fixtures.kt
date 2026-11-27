package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorConfig
import io.github.dreadnought0.feedclean.selectors.SelectorConfigLoader
import io.github.dreadnought0.feedclean.testutil.XmlUiNode
import java.io.File

/** Scrubbed uiautomator-dump fixtures + the real bundled selector file. Unit tests run with the app/ module as working dir. */
object Fixtures {
    const val COVER_47_7_3 = "/fixtures/tiktok-47.7.3/cover"
    const val COVER_47_9_3 = "/fixtures/tiktok-47.9.3/cover"
    const val SYNTHETIC = "/fixtures/synthetic"
    const val NOW = 1_700_000_000_000L

    val ASSET_FILE = File("src/main/assets/${SelectorConfigLoader.ASSET_PATH}")

    val config: SelectorConfig by lazy { ASSET_FILE.inputStream().use { SelectorConfigLoader.load(it) } }

    val parser: ScreenParser by lazy { ScreenParser(config) }

    /** Window roots of a 47.7.3 cover-screen fixture, e.g. "a_normal". */
    fun roots(name: String): List<XmlUiNode> = XmlUiNode.parseResource("$COVER_47_7_3/$name.xml")

    /** Window roots of a 47.9.3 cover-screen fixture, e.g. "m_search_player". */
    fun roots4793(name: String): List<XmlUiNode> = XmlUiNode.parseResource("$COVER_47_9_3/$name.xml")

    /** Window roots of a hand-written synthetic fixture, e.g. "live_shopping_preview". */
    fun syntheticRoots(name: String): List<XmlUiNode> = XmlUiNode.parseResource("$SYNTHETIC/$name.xml")

    /** Parses already-loaded window roots (topmost first). */
    fun parseRoots(roots: List<UiNode>): ScreenSnapshot = parser.parse(roots, NOW)

    inline fun <reified T : ScreenSnapshot> parseRootsAs(roots: List<UiNode>): T {
        val snapshot = parseRoots(roots)
        check(snapshot is T) { "Expected ${T::class.simpleName}, got $snapshot" }
        return snapshot
    }

    /** Raw XML text of a fixture resource, e.g. `"$COVER_47_7_3/a_normal.xml"`, for tests that mutate it. */
    fun resourceText(path: String): String =
        checkNotNull(Fixtures::class.java.getResourceAsStream(path)) { "Missing test resource $path" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    /** Parses one or more fixtures as a single multi-window screen (first = topmost). */
    fun parse(vararg names: String): ScreenSnapshot = parser.parse(names.flatMap { roots(it) }, NOW)

    inline fun <reified T : ScreenSnapshot> parseAs(vararg names: String): T {
        val snapshot = parse(*names)
        check(snapshot is T) { "Expected ${T::class.simpleName} for ${names.toList()}, got $snapshot" }
        return snapshot
    }

    val ALL_COVER = listOf(
        "a_normal", "b_paid_ad", "c_promoted", "d_shop_affiliate", "e_profile_shop", "f_profile_share_sheet",
        "g_block_confirm", "h_profile_blocked", "j_longpress_menu", "k_feed_live_preview",
    )

    /** 47.9.3 cover-screen fixtures (scrubbed captures). */
    val ALL_COVER_47_9_3 = listOf("m_search_player", "n_profile_product_popup")

    /** 47.7.3 fixtures that are feed pages (For You tab selected in every one). */
    val FOR_YOU_FEEDS = listOf("a_normal", "b_paid_ad", "c_promoted", "d_shop_affiliate", "k_feed_live_preview")
}
