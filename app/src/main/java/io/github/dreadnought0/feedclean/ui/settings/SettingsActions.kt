package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.runtime.Stable
import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.NotInterestedReason

/**
 * Every edit the Settings screen can make, one per [io.github.dreadnought0.feedclean.settings.FeedCleanSettings]
 * field (plus the risk-dialog and reset actions). Implemented by [SettingsViewModel]; all writes go through
 * `SettingsRepository.update`, which applies the hard floors/caps.
 *
 * Marked [Stable]: implementations hold no Compose-observable state of their own, so sections that receive
 * the same instance can skip recomposition.
 */
@Stable
interface SettingsActions {
    // ── Detection ──────────────────────────────────────────────────────────
    fun setDetectionEnabled(enabled: Boolean)
    fun setDetectPaidAd(enabled: Boolean)
    fun setDetectPromoted(enabled: Boolean)
    fun setDetectShopAnchor(enabled: Boolean)
    fun setDetectLiveShopping(enabled: Boolean)
    fun setOcrFallbackEnabled(enabled: Boolean)

    /** Stores a user-picked selector file (persisted SAF `content://` URI string). */
    fun setSelectorOverrideUri(uri: String)

    /** Back to the bundled selectors. */
    fun clearSelectorOverride()

    // ── Curate feed ────────────────────────────────────────────────────────
    fun setCurateFeedEnabled(enabled: Boolean)
    fun setAutoSkipOrganicShop(enabled: Boolean)
    fun setAutoSkipPaidAds(enabled: Boolean)
    fun setAutoSkipPromoted(enabled: Boolean)
    fun setAutoSkipLiveShopping(enabled: Boolean)
    fun setAutoSkipInForYou(enabled: Boolean)
    fun setAutoSkipInOtherHomeTabs(enabled: Boolean)
    fun setAutoSkipInSearch(enabled: Boolean)
    fun setAutoSkipInOtherFeeds(enabled: Boolean)
    fun setSkipSettleDelayMs(delayMs: Int)
    fun setConsecutiveSkipCap(cap: Int)
    fun setSkipCoverEnabled(enabled: Boolean)
    fun setCoverStyle(style: CoverStyle)

    // ── Not interested ─────────────────────────────────────────────────────
    fun setNotInterestedManualEnabled(enabled: Boolean)

    /**
     * The user flipped "Automated with auto-skip". Turning it off applies at once. Turning it on applies at once
     * only if the account-risk note was already accepted; otherwise it opens the risk dialog and changes nothing.
     */
    fun requestNotInterestedAutomated(enabled: Boolean)

    /** Risk dialog "Turn on": sets `notInterestedAutomated` and `notInterestedRiskAcknowledged`. */
    fun acceptNotInterestedRisk()

    /** Risk dialog dismissed / "Keep off": nothing changes. */
    fun dismissNotInterestedRisk()

    /** Withdraws the earlier acceptance: clears `notInterestedRiskAcknowledged` and turns automation off. */
    fun revokeNotInterestedRisk()
    fun setNotInterestedReason(reason: NotInterestedReason)

    // ── Overlay ────────────────────────────────────────────────────────────
    fun setShowBubble(enabled: Boolean)

    /** Sets `bubbleX` and `bubbleY` back to -1 (default position). */
    fun resetBubblePosition()

    // ── Blocking ───────────────────────────────────────────────────────────
    fun setBlockStyle(style: BlockStyle)
    fun setMinSecondsBetweenActions(seconds: Int)
    fun setMaxActionsPerHour(perHour: Int)

    // ── Automation safety ──────────────────────────────────────────────────
    fun setKillSwitchEngaged(engaged: Boolean)
    fun setAutoSkipOnlyWhenScreenOn(enabled: Boolean)

    // ── Data & privacy ─────────────────────────────────────────────────────
    fun setSessionTrackingEnabled(enabled: Boolean)
    fun setSaveScreenshots(enabled: Boolean)
    fun setDecisionLogEnabled(enabled: Boolean)
    fun setPublicExportIncludeSubjectiveTags(enabled: Boolean)
}
