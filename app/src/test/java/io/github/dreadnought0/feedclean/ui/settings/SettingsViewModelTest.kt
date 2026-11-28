package io.github.dreadnought0.feedclean.ui.settings

import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import io.github.dreadnought0.feedclean.settings.NotInterestedReason
import io.github.dreadnought0.feedclean.ui.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(initial: FeedCleanSettings = FeedCleanSettings()): Pair<SettingsViewModel, InMemorySettingsRepository> {
        val repository = InMemorySettingsRepository(initial)
        return SettingsViewModel(repository) to repository
    }

    /** Applies [action] to a fresh ViewModel and returns the resulting stored settings. */
    private fun after(initial: FeedCleanSettings = FeedCleanSettings(), action: SettingsViewModel.() -> Unit): FeedCleanSettings {
        val (vm, repository) = viewModel(initial)
        vm.action()
        return repository.settings.value
    }

    /** The edit must change exactly the expected fields and nothing else. */
    private fun assertOnlyChanged(
        initial: FeedCleanSettings = FeedCleanSettings(),
        expected: FeedCleanSettings,
        action: SettingsViewModel.() -> Unit,
    ) {
        assertEquals(expected, after(initial, action))
    }

    @Test
    fun exposesRepositorySettings() = runTest {
        val (vm, repository) = viewModel(FeedCleanSettings(curateFeedEnabled = true))
        assertTrue(vm.settings.value.curateFeedEnabled)
        repository.update { it.copy(curateFeedEnabled = false) }
        assertFalse(vm.settings.value.curateFeedEnabled)
    }

    // ── Detection ──────────────────────────────────────────────────────────

    @Test
    fun detectionToggles() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(detectionEnabled = false)) { setDetectionEnabled(false) }
        assertOnlyChanged(expected = d.copy(detectPaidAd = false)) { setDetectPaidAd(false) }
        assertOnlyChanged(expected = d.copy(detectPromoted = false)) { setDetectPromoted(false) }
        assertOnlyChanged(expected = d.copy(detectShopAnchor = false)) { setDetectShopAnchor(false) }
        assertOnlyChanged(expected = d.copy(detectLiveShopping = false)) { setDetectLiveShopping(false) }
        assertOnlyChanged(expected = d.copy(ocrFallbackEnabled = true)) { setOcrFallbackEnabled(true) }
    }

    @Test
    fun selectorOverrideSetAndClear() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3Aselectors.json"
        assertEquals(uri, after { setSelectorOverrideUri(uri) }.selectorOverrideUri)
        assertNull(after(FeedCleanSettings(selectorOverrideUri = uri)) { clearSelectorOverride() }.selectorOverrideUri)
    }

    // ── Curate feed ────────────────────────────────────────────────────────

    @Test
    fun curateFeedToggles() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(curateFeedEnabled = true)) { setCurateFeedEnabled(true) }
        assertOnlyChanged(expected = d.copy(autoSkipOrganicShop = false)) { setAutoSkipOrganicShop(false) }
        assertOnlyChanged(expected = d.copy(autoSkipPaidAds = true)) { setAutoSkipPaidAds(true) }
        assertOnlyChanged(expected = d.copy(autoSkipPromoted = true)) { setAutoSkipPromoted(true) }
        assertOnlyChanged(expected = d.copy(autoSkipLiveShopping = true)) { setAutoSkipLiveShopping(true) }
        assertOnlyChanged(expected = d.copy(autoSkipInForYou = false)) { setAutoSkipInForYou(false) }
        assertOnlyChanged(expected = d.copy(autoSkipInOtherHomeTabs = true)) { setAutoSkipInOtherHomeTabs(true) }
        assertOnlyChanged(expected = d.copy(autoSkipInSearch = true)) { setAutoSkipInSearch(true) }
        assertOnlyChanged(expected = d.copy(autoSkipInOtherFeeds = true)) { setAutoSkipInOtherFeeds(true) }
        assertOnlyChanged(expected = d.copy(skipCoverEnabled = false)) { setSkipCoverEnabled(false) }
        assertOnlyChanged(expected = d.copy(coverStyle = CoverStyle.LABELLED)) { setCoverStyle(CoverStyle.LABELLED) }
        assertOnlyChanged(expected = d.copy(skipSettleDelayMs = 650)) { setSkipSettleDelayMs(650) }
        assertOnlyChanged(expected = d.copy(consecutiveSkipCap = 12)) { setConsecutiveSkipCap(12) }
    }

    @Test
    fun settleDelayIsClampedByRepository() {
        assertEquals(FeedCleanSettings.MAX_SETTLE_DELAY_MS, after { setSkipSettleDelayMs(10_000) }.skipSettleDelayMs)
        assertEquals(0, after { setSkipSettleDelayMs(-50) }.skipSettleDelayMs)
    }

    @Test
    fun consecutiveSkipCapIsClampedByRepository() {
        assertEquals(1, after { setConsecutiveSkipCap(0) }.consecutiveSkipCap)
        assertEquals(FeedCleanSettings.MAX_CONSECUTIVE_SKIP_CAP, after { setConsecutiveSkipCap(500) }.consecutiveSkipCap)
    }

    // ── Not interested ─────────────────────────────────────────────────────

    @Test
    fun manualNotInterestedAndReason() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(notInterestedManualEnabled = false)) { setNotInterestedManualEnabled(false) }
        assertOnlyChanged(expected = d.copy(notInterestedReason = NotInterestedReason.PREFER_SHOPPING)) {
            setNotInterestedReason(NotInterestedReason.PREFER_SHOPPING)
        }
    }

    @Test
    fun enablingAutomatedNotInterestedShowsRiskDialogFirst() {
        val (vm, repository) = viewModel()
        vm.requestNotInterestedAutomated(true)
        assertTrue(vm.riskDialogVisible.value)
        assertFalse(repository.settings.value.notInterestedAutomated)
        assertFalse(repository.settings.value.notInterestedRiskAcknowledged)
    }

    @Test
    fun acceptingRiskDialogSetsBothFlags() {
        val (vm, repository) = viewModel()
        vm.requestNotInterestedAutomated(true)
        vm.acceptNotInterestedRisk()
        assertFalse(vm.riskDialogVisible.value)
        assertTrue(repository.settings.value.notInterestedAutomated)
        assertTrue(repository.settings.value.notInterestedRiskAcknowledged)
        // Only those two fields changed.
        assertEquals(
            FeedCleanSettings(notInterestedAutomated = true, notInterestedRiskAcknowledged = true),
            repository.settings.value,
        )
    }

    @Test
    fun decliningRiskDialogLeavesItOff() {
        val (vm, repository) = viewModel()
        vm.requestNotInterestedAutomated(true)
        vm.dismissNotInterestedRisk()
        assertFalse(vm.riskDialogVisible.value)
        assertEquals(FeedCleanSettings(), repository.settings.value)
    }

    @Test
    fun alreadyAcknowledgedRiskEnablesWithoutDialog() {
        val (vm, repository) = viewModel(FeedCleanSettings(notInterestedRiskAcknowledged = true))
        vm.requestNotInterestedAutomated(true)
        assertFalse(vm.riskDialogVisible.value)
        assertTrue(repository.settings.value.notInterestedAutomated)
    }

    @Test
    fun disablingAutomatedNotInterestedNeedsNoDialogAndKeepsAcknowledgement() {
        val initial = FeedCleanSettings(notInterestedAutomated = true, notInterestedRiskAcknowledged = true)
        val (vm, repository) = viewModel(initial)
        vm.requestNotInterestedAutomated(false)
        assertFalse(vm.riskDialogVisible.value)
        assertEquals(initial.copy(notInterestedAutomated = false), repository.settings.value)
    }

    @Test
    fun revokingRiskClearsBothFlags() {
        val initial = FeedCleanSettings(notInterestedAutomated = true, notInterestedRiskAcknowledged = true)
        assertOnlyChanged(initial, expected = FeedCleanSettings()) { revokeNotInterestedRisk() }
    }

    // ── Overlay ────────────────────────────────────────────────────────────

    @Test
    fun overlayToggleAndBubbleReset() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(showBubble = false)) { setShowBubble(false) }
        assertOnlyChanged(initial = d.copy(bubbleX = 120, bubbleY = 900), expected = d) { resetBubblePosition() }
    }

    // ── Blocking + rate-limit floors ───────────────────────────────────────

    @Test
    fun blockingEdits() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(blockStyle = BlockStyle.STOP_AT_TIKTOK_DIALOG)) {
            setBlockStyle(BlockStyle.STOP_AT_TIKTOK_DIALOG)
        }
        assertOnlyChanged(expected = d.copy(minSecondsBetweenActions = 10)) { setMinSecondsBetweenActions(10) }
        assertOnlyChanged(expected = d.copy(maxActionsPerHour = 30)) { setMaxActionsPerHour(30) }
    }

    @Test
    fun minSecondsFloorIsEnforced() {
        assertEquals(FeedCleanSettings.MIN_SECONDS_FLOOR, after { setMinSecondsBetweenActions(1) }.minSecondsBetweenActions)
        assertEquals(FeedCleanSettings.MIN_SECONDS_FLOOR, after { setMinSecondsBetweenActions(-5) }.minSecondsBetweenActions)
    }

    @Test
    fun maxPerHourCapIsEnforced() {
        assertEquals(FeedCleanSettings.MAX_PER_HOUR_CAP, after { setMaxActionsPerHour(500) }.maxActionsPerHour)
        assertEquals(1, after { setMaxActionsPerHour(0) }.maxActionsPerHour)
    }

    // ── Automation safety ──────────────────────────────────────────────────

    @Test
    fun safetyToggles() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(killSwitchEngaged = true)) { setKillSwitchEngaged(true) }
        assertOnlyChanged(expected = d.copy(autoSkipOnlyWhenScreenOn = false)) { setAutoSkipOnlyWhenScreenOn(false) }
    }

    @Test
    fun killSwitchDisarmsAutomation() {
        val armed = FeedCleanSettings(
            curateFeedEnabled = true,
            notInterestedAutomated = true,
            notInterestedRiskAcknowledged = true,
        )
        val result = after(armed) { setKillSwitchEngaged(true) }
        assertFalse(result.autoSkipAllowedBySettings)
        assertFalse(result.automatedNotInterestedAllowedBySettings)
    }

    // ── Data & privacy ─────────────────────────────────────────────────────

    @Test
    fun dataPrivacyToggles() {
        val d = FeedCleanSettings()
        assertOnlyChanged(expected = d.copy(sessionTrackingEnabled = true)) { setSessionTrackingEnabled(true) }
        assertOnlyChanged(expected = d.copy(saveScreenshots = true)) { setSaveScreenshots(true) }
        assertOnlyChanged(expected = d.copy(decisionLogEnabled = false)) { setDecisionLogEnabled(false) }
        assertOnlyChanged(expected = d.copy(publicExportIncludeSubjectiveTags = true)) {
            setPublicExportIncludeSubjectiveTags(true)
        }
    }
}
