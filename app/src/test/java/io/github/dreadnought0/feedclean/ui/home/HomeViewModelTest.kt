package io.github.dreadnought0.feedclean.ui.home

import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import io.github.dreadnought0.feedclean.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = InMemorySettingsRepository()
    private val connected = MutableStateFlow(false)
    private val foreground = MutableStateFlow(false)
    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy { HomeViewModel(repository, connected, foreground) }

    @Test
    fun quickTogglesWriteTheirFields() {
        viewModel.setCurateFeedEnabled(true)
        viewModel.setAutoSkipOrganicShop(false)
        viewModel.setSkipCoverEnabled(false)
        viewModel.setKillSwitchEngaged(true)
        assertEquals(
            FeedCleanSettings(
                curateFeedEnabled = true,
                autoSkipOrganicShop = false,
                skipCoverEnabled = false,
                killSwitchEngaged = true,
            ),
            repository.settings.value,
        )
    }

    @Test
    fun uiStateCombinesSettingsAndServiceState() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertFalse(viewModel.uiState.value.serviceConnected)

        connected.value = true
        foreground.value = true
        viewModel.setCurateFeedEnabled(true)

        val state = viewModel.uiState.value
        assertTrue(state.serviceConnected)
        assertTrue(state.tiktokForeground)
        assertTrue(state.curateFeedEnabled)
        assertTrue(state.autoSkipArmed)

        viewModel.setKillSwitchEngaged(true)
        assertFalse(viewModel.uiState.value.autoSkipArmed)
        job.cancel()
    }

    @Test
    fun autoSkipSummaryListsEnabledPlacesInOrder() {
        val label = { p: AutoSkipPlace -> p.name }
        val all = HomeUiState.from(
            FeedCleanSettings(autoSkipInOtherHomeTabs = true, autoSkipInSearch = true, autoSkipInOtherFeeds = true),
            serviceConnected = false,
            tiktokForeground = false,
        )
        assertEquals(
            "FOR_YOU · OTHER_HOME_TABS · SEARCH · OTHER_FEEDS",
            autoSkipSummary(all.autoSkipPlaces, label),
        )
        val some = HomeUiState(autoSkipInOtherHomeTabs = false, autoSkipInSearch = true, autoSkipInOtherFeeds = true)
        assertEquals("FOR_YOU · SEARCH · OTHER_FEEDS", autoSkipSummary(some.autoSkipPlaces, label))
        assertEquals("FOR_YOU", autoSkipSummary(HomeUiState().autoSkipPlaces, label))
    }

    @Test
    fun autoSkipSummaryIsNullWhenNowhere() {
        assertEquals(null, autoSkipSummary(autoSkipPlaces(false, false, false, false)) { it.name })
    }
}
