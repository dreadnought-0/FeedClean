package io.github.dreadnought0.feedclean.selectors

/**
 * Names of the selector keys the parser reads from `tiktok.json`. These are FeedClean's own identifiers; the
 * TikTok strings and IDs they resolve to live only in the JSON file.
 */
object SelectorKeys {
    // ── Feed video page ─────────────────────────────────────────────────────
    const val FEED_PAGER = "feed.pager"
    const val FEED_VIDEO = "feed.video"
    const val FEED_DISPLAY_NAME = "feed.displayName"
    const val FEED_AVATAR = "feed.avatar"
    const val FEED_PROBABLE_HANDLE = "feed.probableHandle"
    const val FEED_CAPTION = "feed.caption"
    const val FEED_SOUND = "feed.sound"
    const val FEED_FOR_YOU_TAB = "feed.forYouTab"

    /** Any Home top tab (For You, Following, …): the Home tab row is on screen. Feeds `FeedContext`. */
    const val FEED_HOME_TOP_TABS = "feed.homeTopTabs"

    /** The search-results player's TOP search bar (never the in-page "Search · …" suggestion bar). */
    const val FEED_SEARCH_PLAYER_BAR = "feed.searchPlayerBar"
    const val FEED_DISCLOSURE_LABEL = "feed.disclosureLabel"
    const val FEED_PAID_AD_LABELS = "feed.paidAdLabels"
    const val FEED_PROMOTED_LABELS = "feed.promotedLabels"
    const val FEED_PROMOTED_MUSIC = "feed.promotedMusic"
    const val FEED_CTA = "feed.cta"
    const val FEED_CTA_LABELS = "feed.ctaLabels"
    const val FEED_ANCHOR = "feed.anchor"
    const val FEED_SHOP_ANCHOR = "feed.shopAnchor"
    const val FEED_LIVE_BADGE = "feed.liveBadge"
    const val FEED_LIVE_EVENT_ANCHOR = "feed.liveEventAnchor"
    const val FEED_LIVE_SHOPPING = "feed.liveShopping"

    // ── In-feed LIVE preview (a stream playing in the For You pager) ────────
    const val FEED_LIVE_VIDEO = "feed.liveVideo"
    const val FEED_LIVE_TIPS = "feed.liveTips"
    const val FEED_LIVE_NOW = "feed.liveNow"
    const val FEED_LIVE_NICKNAME = "feed.liveNickname"
    const val FEED_LIVE_SHOPPING_TEXT = "feed.liveShoppingText"

    // ── Profile ─────────────────────────────────────────────────────────────
    const val PROFILE_HANDLE = "profile.handle"
    const val PROFILE_DISPLAY_NAME = "profile.displayName"
    const val PROFILE_CATEGORY = "profile.category"
    const val PROFILE_SHOP_CATEGORY_LABELS = "profile.shopCategoryLabels"
    const val PROFILE_SHOP_CREATOR = "profile.shopCreator"
    const val PROFILE_SHARE = "profile.share"
    const val PROFILE_BACK = "profile.back"

    // ── Share sheet ("Send to") ─────────────────────────────────────────────
    const val SHEET_CONTAINER = "sheet.container"
    const val SHEET_TITLE = "sheet.title"
    const val SHEET_BLOCK = "sheet.block"
    const val SHEET_CLOSE = "sheet.close"

    // ── Other bottom sheets (DismissibleSheet) ──────────────────────────────
    /** Clickable outside-touch scrim of a modal bottom sheet: closes it, like a tap above the sheet. */
    const val SHEET_DISMISS_SCRIM = "sheet.dismissScrim"

    /** Commerce markers inside a sheet ("Add to cart", "Buy now", cart icon). */
    const val SHEET_COMMERCE = "sheet.commerce"

    // ── Block confirmation dialog ───────────────────────────────────────────
    const val DIALOG_CONTAINER = "dialog.container"
    const val DIALOG_TITLE = "dialog.title"
    const val DIALOG_DISPLAY_NAME = "dialog.displayName"
    const val DIALOG_CONFIRM = "dialog.confirm"
    const val DIALOG_CANCEL = "dialog.cancel"

    // ── Block verification ──────────────────────────────────────────────────
    const val VERIFY_UNBLOCK = "verify.unblock"
    const val VERIFY_BLOCKED_MSG = "verify.blockedMsg"

    // ── Long-press menu / Not interested ────────────────────────────────────
    const val LONGPRESS_CONTAINER = "longpress.container"
    const val LONGPRESS_NOT_INTERESTED = "longpress.notInterested"
    const val LONGPRESS_AD_OPTION = "longpress.adOption"
    const val LONGPRESS_REASON_TITLE = "longpress.reasonTitle"
    const val LONGPRESS_REASON_OPTION = "longpress.reasonOption"
    const val LONGPRESS_REASON_SHOP = "longpress.reasonShop"
    const val VERIFY_NOT_INTERESTED = "verify.notInterested"

    /** Every key the parser and detector read. A unit test checks that `tiktok.json` defines all of them. */
    val ALL: List<String> = listOf(
        FEED_PAGER, FEED_VIDEO, FEED_DISPLAY_NAME, FEED_AVATAR, FEED_PROBABLE_HANDLE, FEED_CAPTION, FEED_SOUND,
        FEED_FOR_YOU_TAB, FEED_HOME_TOP_TABS, FEED_SEARCH_PLAYER_BAR, FEED_DISCLOSURE_LABEL, FEED_PAID_AD_LABELS, FEED_PROMOTED_LABELS, FEED_PROMOTED_MUSIC,
        FEED_CTA, FEED_CTA_LABELS, FEED_ANCHOR, FEED_SHOP_ANCHOR, FEED_LIVE_BADGE, FEED_LIVE_EVENT_ANCHOR,
        FEED_LIVE_SHOPPING,
        FEED_LIVE_VIDEO, FEED_LIVE_TIPS, FEED_LIVE_NOW, FEED_LIVE_NICKNAME, FEED_LIVE_SHOPPING_TEXT,
        PROFILE_HANDLE, PROFILE_DISPLAY_NAME, PROFILE_CATEGORY, PROFILE_SHOP_CATEGORY_LABELS, PROFILE_SHOP_CREATOR,
        PROFILE_SHARE, PROFILE_BACK,
        SHEET_CONTAINER, SHEET_TITLE, SHEET_BLOCK, SHEET_CLOSE, SHEET_DISMISS_SCRIM, SHEET_COMMERCE,
        DIALOG_CONTAINER, DIALOG_TITLE, DIALOG_DISPLAY_NAME, DIALOG_CONFIRM, DIALOG_CANCEL,
        VERIFY_UNBLOCK, VERIFY_BLOCKED_MSG,
        LONGPRESS_CONTAINER, LONGPRESS_NOT_INTERESTED, LONGPRESS_AD_OPTION, LONGPRESS_REASON_TITLE,
        LONGPRESS_REASON_OPTION, LONGPRESS_REASON_SHOP, VERIFY_NOT_INTERESTED,
    )
}
