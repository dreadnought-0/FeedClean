package io.github.dreadnought0.feedclean.detect

import kotlinx.serialization.Serializable

/**
 * What the detector found on one feed page. Stored as JSON in `sightings.signals_json`.
 *
 * Paid ads and organic Shop posts are separable on screen: a paid ad carries the "Ad"/"Sponsored"
 * disclosure label; an organic Shop push has the Shop anchor without a paid/promoted label.
 *
 * New fields must have defaults, for JSON compatibility with stored sightings.
 */
@Serializable
data class Signals(
    val isPaidAd: Boolean = false,
    val isPromoted: Boolean = false,
    val hasShopAnchor: Boolean = false,
    /** Known Shop seller/creator. On the feed this comes from the DB (account_category), not the screen. */
    val isShopCreator: Boolean = false,
    /** Creator is currently live: the LIVE ring on a video's avatar, or an in-feed LIVE preview ([isLivePreview]). */
    val isLive: Boolean = false,
    /** LIVE shopping: a "LIVE shopping" label, or commerce text (auction, price, shipping, …) on a LIVE preview. */
    val isLiveShopping: Boolean = false,
    /** Anchor reads "LIVE Event · …". */
    val isLiveEvent: Boolean = false,
    /** Raw disclosure-label text, e.g. "Ad", "Promotional content". */
    val disclosureLabel: String? = null,
    /**
     * Raw anchor text (product title / subline), for logging and the card. On a LIVE-shopping preview: the first
     * commerce text matched (`feed.liveShoppingText`).
     */
    val anchorText: String? = null,
    /** Ad call-to-action text, e.g. "Shop now". */
    val ctaText: String? = null,
    /**
     * The page is an in-feed LIVE preview (a stream playing in the For You pager), not a video. Implies [isLive].
     *
     * Never organic Shop ([isOrganicShop] is false, whatever [hasShopAnchor] / [isShopCreator] say): commerce on a
     * LIVE preview is [isLiveShopping], which has its own skip target. Tapping the preview or the streamer's name
     * ENTERS the LIVE room (and joining is visible to the streamer), so executors must not tap or long-press
     * anything on such a page; the pager scroll (navigation) is the only safe action.
     */
    val isLivePreview: Boolean = false,
) {
    /**
     * Algorithm-pushed Shop content that is NOT a paid ad or disclosed promotion. The default skip target.
     * Never true for a LIVE preview ([isLivePreview]).
     */
    val isOrganicShop: Boolean
        get() = (hasShopAnchor || isShopCreator) && !isPaidAd && !isPromoted && !isLivePreview

    val hasAnySignal: Boolean
        get() = isPaidAd || isPromoted || hasShopAnchor || isShopCreator || isLiveShopping
}

/** Why a page was (or would be) auto-skipped. Persisted by name; never rename entries. */
enum class SkipReason { ORGANIC_SHOP, PAID_AD, PROMOTED, LIVE_SHOPPING }

/**
 * Which vertical video player a [ScreenSnapshot.FeedVideo] page is in. Auto-skip is allowed per context
 * (Settings → Curate feed → "Where to auto-skip"). Persisted by name; never rename entries.
 */
enum class FeedContext {
    /** Home → For You (the "For You" top tab is selected). */
    FOR_YOU,

    /** Home → another top tab (Following, the local/nearby tab, …). */
    HOME_OTHER_TAB,

    /** The player opened from search results (search bar at the top). */
    SEARCH,

    /** Any other video player: a profile's videos, hashtag/sound pages, … */
    OTHER,
}

/** Output of `CurationPolicy`. */
sealed interface CurationDecision {
    data object Keep : CurationDecision
    data class Skip(val reason: SkipReason) : CurationDecision
}

/**
 * One parse of the current TikTok screen (all interactive windows).
 *
 * Nodes referenced here are the handles the executors click. They are only valid briefly; executors must
 * re-snapshot after every action rather than reuse old nodes.
 */
sealed interface ScreenSnapshot {
    val capturedAtMillis: Long

    /** selector key → human-readable description of the strategy that matched. Shown on the Debug screen. */
    val matchedStrategies: Map<String, String>

    /**
     * A page of the For You (or Following) pager: normally a video, or an in-feed LIVE preview when
     * [Signals.isLivePreview] is set. A LIVE preview has: [displayName] = the streamer's name (provisional, like
     * every feed name); no [probableHandle], [caption], or [soundDescription]; [avatarNode] and [displayNameNode]
     * always null, because tapping the streamer's name or the preview ENTERS the LIVE room; [videoNode] = the
     * "LIVE" `long_press_layout`, which a tap would also open (never tap it; do not long-press it until that
     * gesture has been checked on a device); [pageSignature] stable per stream.
     */
    data class FeedVideo(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        val displayName: String?,
        /** Heuristic, from "original sound - <handle> by <display name>". Must be confirmed before trusting. */
        val probableHandle: String?,
        val caption: String?,
        val soundDescription: String?,
        val signals: Signals,
        /**
         * Stable-per-video hash of displayName|caption|sound. Changes when the page changes. For a LIVE preview:
         * streamer name + a LIVE marker only, so changing chat/gift/auction text never makes it look like a new page.
         */
        val pageSignature: String,
        /** True when the For You tab is the selected top tab. */
        val isForYouTab: Boolean,
        /** Avatar that opens the creator's profile. Null for a LIVE creator's avatar and for a LIVE preview. */
        val avatarNode: UiNode? = null,
        /** Display-name button that opens the profile. Null for a LIVE preview (its name opens the LIVE room). */
        val displayNameNode: UiNode? = null,
        /**
         * The `long_press_layout` node: for a video ("Video"), the long-press target for "Not interested". For a
         * LIVE preview it is the "LIVE" layout, and a tap on it enters the LIVE room: callers must check
         * [Signals.isLivePreview] before acting on it.
         */
        val videoNode: UiNode? = null,
        /** The feed's vertical ViewPager: target for ACTION_SCROLL_FORWARD (skip). */
        val pagerNode: UiNode? = null,
        /** Visible bounds of the current page (used to size the skip cover). */
        val pageBounds: Bounds = Bounds.EMPTY,
        /** How sure the detector is that this page is a paid ad (label + corroborating signals). */
        val adConfidence: AdConfidence = AdConfidence.NONE,
        /** Which player this page is in (For You, another home tab, search results, other). */
        val feedContext: FeedContext = if (isForYouTab) FeedContext.FOR_YOU else FeedContext.OTHER,
    ) : ScreenSnapshot

    data class Profile(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        val displayName: String?,
        /** Without "@", lowercase. */
        val handle: String?,
        /** e.g. "Shop" (business category label under the handle). */
        val accountCategory: String?,
        val isShopCreator: Boolean,
        /** Profile shows "Unblock" / "You blocked <handle>". */
        val isBlockedByMe: Boolean,
        /** Handle from "You blocked <handle>", when present. */
        val blockedMessageHandle: String? = null,
        val shareNode: UiNode? = null,
        val backNode: UiNode? = null,
    ) : ScreenSnapshot

    /** Profile/video share bottom sheet ("Send to"). Never read or log the contacts row. */
    data class ShareSheet(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        val blockNode: UiNode?,
        val closeNode: UiNode? = null,
    ) : ScreenSnapshot

    /** "Block <Display Name> (<handle>)?" confirmation dialog (separate window). */
    data class BlockDialog(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        val titleText: String?,
        val displayName: String?,
        /** Without "@", lowercase. */
        val handle: String?,
        val confirmNode: UiNode?,
        val cancelNode: UiNode?,
    ) : ScreenSnapshot

    /** Menu opened by long-pressing a video (contains "Not interested"). */
    data class LongPressMenu(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        val notInterestedNode: UiNode?,
        /** All visible option labels, for the Debug screen and for updating selectors. */
        val optionLabels: List<String> = emptyList(),
        /**
         * Reason choices if TikTok shows a follow-up picker: label → node. MUST preserve on-screen order
         * (top-to-bottom, use a LinkedHashMap): `NotInterestedReason.FIRST_OFFERED` depends on it.
         */
        val reasonNodes: Map<String, UiNode> = emptyMap(),
        /** The menu offers an ads-only option (e.g. "About this ad"): corroborating paid-ad evidence. */
        val isAdMenu: Boolean = false,
    ) : ScreenSnapshot

    /**
     * A modal bottom sheet FeedClean doesn't act inside, shown over another screen. Observed (TikTok 47.9.3): tapping
     * a Shop advertiser's avatar opens their profile with a PRODUCT sheet on top ("Add to cart" / "Buy now", plus its
     * own lowercase "share"), hiding the profile's Share button.
     *
     * The ONLY sanctioned interaction is closing it via [dismissNode] (Material's `touch_outside` scrim, i.e. what a
     * person taps above the sheet) with ACTION_CLICK, or one Back. Never tap anything inside the sheet.
     */
    data class DismissibleSheet(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        /** The sheet's outside-touch scrim (clickable). Null if not found; then only Back may close it. */
        val dismissNode: UiNode?,
        /** The sheet is commerce (product card, "Add to cart" / "Buy now"). */
        val isCommerce: Boolean,
        /** The profile underneath, if one is open below the sheet (its handle is readable even while covered). */
        val underlyingProfile: Profile? = null,
    ) : ScreenSnapshot

    data class Unknown(
        override val capturedAtMillis: Long,
        override val matchedStrategies: Map<String, String> = emptyMap(),
        /** Short description for the Debug screen (e.g. top-level class/window title). */
        val note: String? = null,
    ) : ScreenSnapshot
}
