package io.github.dreadnought0.feedclean.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.NotInterestedReason
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen. Exposes [settings] straight from the repository and turns each UI edit into one
 * `SettingsRepository.update` call (which sanitizes, so floors/caps are always enforced).
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel(), SettingsActions {

    /** Current settings (already sanitized by the repository). */
    val settings: StateFlow<FeedCleanSettings> = settingsRepository.settings

    private val _riskDialogVisible = MutableStateFlow(false)

    /** True while the automated-Not-interested account-risk dialog should be shown. */
    val riskDialogVisible: StateFlow<Boolean> = _riskDialogVisible.asStateFlow()

    private fun edit(transform: (FeedCleanSettings) -> FeedCleanSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    // ── Detection ──────────────────────────────────────────────────────────
    override fun setDetectionEnabled(enabled: Boolean) = edit { it.copy(detectionEnabled = enabled) }
    override fun setDetectPaidAd(enabled: Boolean) = edit { it.copy(detectPaidAd = enabled) }
    override fun setDetectPromoted(enabled: Boolean) = edit { it.copy(detectPromoted = enabled) }
    override fun setDetectShopAnchor(enabled: Boolean) = edit { it.copy(detectShopAnchor = enabled) }
    override fun setDetectLiveShopping(enabled: Boolean) = edit { it.copy(detectLiveShopping = enabled) }
    override fun setOcrFallbackEnabled(enabled: Boolean) = edit { it.copy(ocrFallbackEnabled = enabled) }
    override fun setSelectorOverrideUri(uri: String) = edit { it.copy(selectorOverrideUri = uri) }
    override fun clearSelectorOverride() = edit { it.copy(selectorOverrideUri = null) }

    // ── Curate feed ────────────────────────────────────────────────────────
    override fun setCurateFeedEnabled(enabled: Boolean) = edit { it.copy(curateFeedEnabled = enabled) }
    override fun setAutoSkipOrganicShop(enabled: Boolean) = edit { it.copy(autoSkipOrganicShop = enabled) }
    override fun setAutoSkipPaidAds(enabled: Boolean) = edit { it.copy(autoSkipPaidAds = enabled) }
    override fun setAutoSkipPromoted(enabled: Boolean) = edit { it.copy(autoSkipPromoted = enabled) }
    override fun setAutoSkipLiveShopping(enabled: Boolean) = edit { it.copy(autoSkipLiveShopping = enabled) }
    override fun setSkipSettleDelayMs(delayMs: Int) = edit { it.copy(skipSettleDelayMs = delayMs) }
    override fun setConsecutiveSkipCap(cap: Int) = edit { it.copy(consecutiveSkipCap = cap) }
    override fun setSkipCoverEnabled(enabled: Boolean) = edit { it.copy(skipCoverEnabled = enabled) }
    override fun setCoverStyle(style: CoverStyle) = edit { it.copy(coverStyle = style) }

    // ── Not interested ─────────────────────────────────────────────────────
    override fun setNotInterestedManualEnabled(enabled: Boolean) =
        edit { it.copy(notInterestedManualEnabled = enabled) }

    override fun requestNotInterestedAutomated(enabled: Boolean) {
        when {
            !enabled -> edit { it.copy(notInterestedAutomated = false) }
            settings.value.notInterestedRiskAcknowledged -> edit { it.copy(notInterestedAutomated = true) }
            else -> _riskDialogVisible.value = true
        }
    }

    override fun acceptNotInterestedRisk() {
        _riskDialogVisible.value = false
        edit { it.copy(notInterestedAutomated = true, notInterestedRiskAcknowledged = true) }
    }

    override fun dismissNotInterestedRisk() {
        _riskDialogVisible.value = false
    }

    override fun revokeNotInterestedRisk() =
        edit { it.copy(notInterestedAutomated = false, notInterestedRiskAcknowledged = false) }

    override fun setNotInterestedReason(reason: NotInterestedReason) = edit { it.copy(notInterestedReason = reason) }

    // ── Overlay ────────────────────────────────────────────────────────────
    override fun setShowBubble(enabled: Boolean) = edit { it.copy(showBubble = enabled) }
    override fun resetBubblePosition() = edit { it.copy(bubbleX = -1, bubbleY = -1) }

    // ── Blocking ───────────────────────────────────────────────────────────
    override fun setBlockStyle(style: BlockStyle) = edit { it.copy(blockStyle = style) }
    override fun setMinSecondsBetweenActions(seconds: Int) = edit { it.copy(minSecondsBetweenActions = seconds) }
    override fun setMaxActionsPerHour(perHour: Int) = edit { it.copy(maxActionsPerHour = perHour) }

    // ── Automation safety ──────────────────────────────────────────────────
    override fun setKillSwitchEngaged(engaged: Boolean) = edit { it.copy(killSwitchEngaged = engaged) }
    override fun setAutoSkipOnlyWhenScreenOn(enabled: Boolean) = edit { it.copy(autoSkipOnlyWhenScreenOn = enabled) }

    // ── Data & privacy ─────────────────────────────────────────────────────
    override fun setSessionTrackingEnabled(enabled: Boolean) = edit { it.copy(sessionTrackingEnabled = enabled) }
    override fun setSaveScreenshots(enabled: Boolean) = edit { it.copy(saveScreenshots = enabled) }
    override fun setDecisionLogEnabled(enabled: Boolean) = edit { it.copy(decisionLogEnabled = enabled) }
    override fun setAutoSkipInForYou(enabled: Boolean) = edit { it.copy(autoSkipInForYou = enabled) }
    override fun setAutoSkipInOtherHomeTabs(enabled: Boolean) = edit { it.copy(autoSkipInOtherHomeTabs = enabled) }
    override fun setAutoSkipInSearch(enabled: Boolean) = edit { it.copy(autoSkipInSearch = enabled) }
    override fun setAutoSkipInOtherFeeds(enabled: Boolean) = edit { it.copy(autoSkipInOtherFeeds = enabled) }
    override fun setPublicExportIncludeSubjectiveTags(enabled: Boolean) =
        edit { it.copy(publicExportIncludeSubjectiveTags = enabled) }
}
