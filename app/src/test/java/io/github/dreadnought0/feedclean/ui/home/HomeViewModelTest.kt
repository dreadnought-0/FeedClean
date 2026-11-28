package io.github.dreadnought0.feedclean.ui.home

import io.github.dreadnought0.feedclean.armed.ArmedLevel
import io.github.dreadnought0.feedclean.armed.Behaviour
import io.github.dreadnought0.feedclean.armed.Requirement
import io.github.dreadnought0.feedclean.service.AutoSkipStatus
import io.github.dreadnought0.feedclean.service.RuntimeCommand
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

    private val repository = InMemorySettingsRepository(FeedCleanSettings(curateFeedEnabled = true))
    private val connected = MutableStateFlow(false)
    private val foreground = MutableStateFlow(false)
    private val autoSkip = MutableStateFlow(AutoSkipStatus())
    private val sent = mutableListOf<RuntimeCommand>()

    // Lazy: must be created after MainDispatcherRule has installed the test Main dispatcher.
    private val viewModel by lazy {
        HomeViewModel(repository, connected, foreground, autoSkip) { sent += it; true }
    }

    @Test
    fun armedReportFollowsServiceSettingsAndSafetyStop() = runTest {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val shop = { viewModel.uiState.value.armed.of(Behaviour.SKIP_SHOP) }

        assertEquals(ArmedLevel.BLOCKED, shop().level)
        assertEquals(Requirement.SERVICE_RUNNING, shop().reason)

        connected.value = true
        assertEquals(ArmedLevel.ARMED, shop().level)

        autoSkip.value = AutoSkipStatus(pausedByLoopGuard = true, pausedAfter = 8)
        assertEquals(ArmedLevel.PAUSED, shop().level)
        assertEquals(Requirement.NOT_PAUSED_BY_SAFETY_STOP, shop().reason)
        assertEquals(8, viewModel.uiState.value.armed.facts.loopGuardPausedAfter)

        autoSkip.value = AutoSkipStatus()
        repository.update { it.copy(killSwitchEngaged = true) }
        assertEquals(ArmedLevel.PAUSED, shop().level)
        assertEquals(Requirement.NOT_PAUSED_BY_USER, shop().reason)
        job.cancel()
    }

    @Test
    fun foregroundOnlyCountsWhileTheServiceRuns() {
        assertFalse(HomeUiState.from(FeedCleanSettings(), serviceConnected = false, tiktokForeground = true).tiktokForeground)
        assertTrue(HomeUiState.from(FeedCleanSettings(), serviceConnected = true, tiktokForeground = true).tiktokForeground)
    }

    @Test
    fun staleSafetyStopIsIgnoredWhenTheServiceIsGone() {
        val state = HomeUiState.from(
            FeedCleanSettings(curateFeedEnabled = true),
            serviceConnected = false,
            tiktokForeground = false,
            autoSkip = AutoSkipStatus(pausedByLoopGuard = true, pausedAfter = 8),
        )
        assertEquals(Requirement.SERVICE_RUNNING, state.armed.of(Behaviour.SKIP_SHOP).reason)
        assertFalse(state.armed.facts.loopGuardPaused)
    }

    @Test
    fun resumeSendsTheRuntimeCommand() {
        assertTrue(viewModel.resumeAfterSafetyStop())
        assertEquals(listOf(RuntimeCommand.RESUME_AUTO_SKIP), sent)
    }

    @Test
    fun autoSkipSummaryListsEnabledPlacesInOrder() {
        val label = { p: AutoSkipPlace -> p.name }
        val all = FeedCleanSettings(autoSkipInOtherHomeTabs = true, autoSkipInSearch = true, autoSkipInOtherFeeds = true)
        assertEquals(
            "FOR_YOU · OTHER_HOME_TABS · SEARCH · OTHER_FEEDS",
            autoSkipSummary(all.autoSkipPlaceList(), label),
        )
        assertEquals("FOR_YOU", autoSkipSummary(FeedCleanSettings().autoSkipPlaceList(), label))
    }

    @Test
    fun autoSkipSummaryIsNullWhenNowhere() {
        assertEquals(null, autoSkipSummary(autoSkipPlaces(false, false, false, false)) { it.name })
    }
}
