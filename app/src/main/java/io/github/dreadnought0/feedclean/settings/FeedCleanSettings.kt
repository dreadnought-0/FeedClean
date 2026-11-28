package io.github.dreadnought0.feedclean.settings

import io.github.dreadnought0.feedclean.detect.FeedContext

/**
 * Every user-facing setting. Each one has a UI control on the Settings screen.
 * Defaults keep automation OFF until the user opts in.
 *
 * New fields must have defaults.
 */
data class FeedCleanSettings(
    // ── Detection ─────────────────────────────────────────────────────────────
    val detectionEnabled: Boolean = true,
    val detectPaidAd: Boolean = true,
    val detectPromoted: Boolean = true,
    val detectShopAnchor: Boolean = true,
    val detectLiveShopping: Boolean = true,
    /** OCR fallback for image-only labels (not built yet). */
    val ocrFallbackEnabled: Boolean = false,
    /** content:// URI of a user-loaded selector override file, or null for the bundled tiktok.json. */
    val selectorOverrideUri: String? = null,

    // ── Curate feed ──────────────────────────────────────────────────────────
    /** Master switch. Off by default. Turning it on arms the targets below (organic Shop + cover by default). */
    val curateFeedEnabled: Boolean = false,
    val autoSkipOrganicShop: Boolean = true,
    val autoSkipPaidAds: Boolean = false,
    val autoSkipPromoted: Boolean = false,
    val autoSkipLiveShopping: Boolean = false,
    /**
     * Wait after the cover goes up and before scrolling, in ms. Default 0, so flagged posts flick straight past
     * like a fast scroll.
     */
    val skipSettleDelayMs: Int = 0,
    /** Pause auto-skip after this many consecutive skips (loop guard). */
    val consecutiveSkipCap: Int = 8,
    /** Off by default: a cover can read as a glitch; a plain fast scroll looks natural. Still available. */
    val skipCoverEnabled: Boolean = false,
    val coverStyle: CoverStyle = CoverStyle.BLANK,

    // ── Where to auto-skip (each its own toggle) ─────────────────────────────
    val autoSkipInForYou: Boolean = true,
    /** Following and the other Home top tabs. */
    val autoSkipInOtherHomeTabs: Boolean = false,
    /** The video player opened from search results. */
    val autoSkipInSearch: Boolean = false,
    /** Any other player: a profile's videos, hashtag and sound pages. */
    val autoSkipInOtherFeeds: Boolean = false,

    // ── Not interested ───────────────────────────────────────────────────────
    val notInterestedManualEnabled: Boolean = true,
    /** Send "Not interested" automatically before an auto-skip of organic Shop. Off by default; risk note. */
    val notInterestedAutomated: Boolean = false,
    /** Set when the user has seen and accepted the account-risk note for automated Not-interested. */
    val notInterestedRiskAcknowledged: Boolean = false,
    val notInterestedReason: NotInterestedReason = NotInterestedReason.FIRST_OFFERED,

    // ── Overlay ──────────────────────────────────────────────────────────────
    val showBubble: Boolean = true,
    /** Last bubble position in px; -1 = default position. */
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,

    // ── Blocking ─────────────────────────────────────────────────────────────
    val blockStyle: BlockStyle = BlockStyle.ONE_TAP_FULL_FLOW,
    val minSecondsBetweenActions: Int = 5,
    val maxActionsPerHour: Int = 60,

    // ── Automation safety ───────────────────────────────────────────────────
    /** When true, ALL automation (auto-skip, automated Not-interested) is paused. */
    val killSwitchEngaged: Boolean = false,
    val autoSkipOnlyWhenScreenOn: Boolean = true,

    // ── Data & privacy ──────────────────────────────────────────────────────
    val sessionTrackingEnabled: Boolean = false,
    /**
     * Keep a 7-day on-phone log of curation decisions (context, detected kinds, decision, gate, outcome; never names,
     * handles, or captions), viewable/exportable on the Debug screen. Answers "why wasn't this skipped?".
     */
    val decisionLogEnabled: Boolean = true,
    val saveScreenshots: Boolean = false,
    val publicExportIncludeSubjectiveTags: Boolean = false,
) {
    /**
     * Runtime gate only: the Auto-skip master is on and "Pause everything" is off. This is NOT "armed": whether a
     * skip will actually happen also depends on the service, detection, the skip type, its detection kind, the
     * places and the safety stop. The UI reads [io.github.dreadnought0.feedclean.armed.ArmedEvaluator] for that.
     */
    val autoSkipAllowedBySettings: Boolean
        get() = curateFeedEnabled && !killSwitchEngaged

    /** Whether auto-skip is switched on for pages in [context] (independent of arming). */
    fun autoSkipAllowedIn(context: FeedContext): Boolean = when (context) {
        FeedContext.FOR_YOU -> autoSkipInForYou
        FeedContext.HOME_OTHER_TAB -> autoSkipInOtherHomeTabs
        FeedContext.SEARCH -> autoSkipInSearch
        FeedContext.OTHER -> autoSkipInOtherFeeds
    }

    /**
     * Runtime gate only: [autoSkipAllowedBySettings], the switch, and the accepted risk note. It also only ever runs
     * on a skipped organic Shop post; see [io.github.dreadnought0.feedclean.armed.ArmedEvaluator] for the honest
     * armed state.
     */
    val automatedNotInterestedAllowedBySettings: Boolean
        get() = autoSkipAllowedBySettings && notInterestedAutomated && notInterestedRiskAcknowledged

    /** Clamp to hard floors/caps. The repository applies this on every write. */
    fun sanitized(): FeedCleanSettings = copy(
        minSecondsBetweenActions = minSecondsBetweenActions.coerceAtLeast(MIN_SECONDS_FLOOR),
        maxActionsPerHour = maxActionsPerHour.coerceIn(1, MAX_PER_HOUR_CAP),
        skipSettleDelayMs = skipSettleDelayMs.coerceIn(0, MAX_SETTLE_DELAY_MS),
        consecutiveSkipCap = consecutiveSkipCap.coerceIn(1, MAX_CONSECUTIVE_SKIP_CAP),
    )

    companion object {
        const val MIN_SECONDS_FLOOR = 3
        const val MAX_PER_HOUR_CAP = 120
        const val MAX_SETTLE_DELAY_MS = 2000
        const val MAX_CONSECUTIVE_SKIP_CAP = 50
    }
}

enum class CoverStyle { BLANK, BLUR, LABELLED }

enum class BlockStyle {
    /** One tap on FeedClean's Block runs the whole verified flow (default). */
    ONE_TAP_FULL_FLOW,

    /** Navigate to TikTok's confirm dialog, check the handle, and let the user tap TikTok's Block. */
    STOP_AT_TIKTOK_DIALOG,
}

enum class NotInterestedReason {
    /** Tap whichever reason TikTok offers first (or none if it doesn't ask). */
    FIRST_OFFERED,

    /** Prefer a reason mentioning shopping/products when offered. */
    PREFER_SHOPPING,

    /** Dismiss any reason picker without choosing. */
    SKIP_REASON,
}
