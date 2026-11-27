package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.Session
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import io.github.dreadnought0.feedclean.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Europe/London")
    private val base = LocalDateTime.parse("2026-11-22T23:59:00").atZone(zone).toInstant().toEpochMilli()
    private val stats = FakeStatsRepository()
    private val settings = InMemorySettingsRepository()

    private fun TestScope.viewModel() = StatsViewModel(stats, settings, clock = { base + currentTime }, zone = { zone })

    private fun TestScope.collect(vm: StatsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    @Test
    fun buildsTodayChartAndTotalsFromTheSeries() = runTest {
        val today = LocalDate.of(2026, 11, 22)
        stats.series.value = listOf(
            DayStats(today.minusDays(1), DailyCounts(blocked = 2, autoSkipped = 3)),
            DayStats(today, DailyCounts(blocked = 1, organicShop = 6, detected = 9)),
        )
        val vm = viewModel()
        collect(vm)

        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals(today, state.today)
        assertEquals(listOf(CHART_DAYS to base), stats.seriesRequests)
        assertEquals(9, state.todayCounts.detected)
        assertEquals(CHART_DAYS, state.chart.dates.size)
        assertEquals(3, state.totals.blocked)
        assertEquals(3, state.totals.autoSkipped)
        assertEquals(1, state.chart.series.single { it.metric == DailyMetric.BLOCKS }.values.last())
    }

    @Test
    fun sessionsAreNotQueriedWhileTrackingIsOff() = runTest {
        val vm = viewModel()
        collect(vm)

        assertFalse(vm.uiState.value.sessionTrackingEnabled)
        assertTrue(vm.uiState.value.sessions.isEmpty())
        assertTrue(stats.sessionRequests.isEmpty())

        stats.sessions.value = listOf(
            SessionRatio.of(Session(1, 0L, 60_000L, videosSeen = 10, ads = 1, promoted = 0, shop = 2, live = 0, skipped = 2)),
        )
        settings.update { it.copy(sessionTrackingEnabled = true) }
        runCurrent()

        assertTrue(vm.uiState.value.sessionTrackingEnabled)
        assertEquals(listOf(SESSION_LIMIT), stats.sessionRequests)
        assertEquals(20, vm.uiState.value.sessions.single().shop.percent)

        settings.update { it.copy(sessionTrackingEnabled = false) }
        runCurrent()
        assertTrue(vm.uiState.value.sessions.isEmpty())
    }

    @Test
    fun requeriesTheSeriesJustAfterMidnight() = runTest {
        val vm = viewModel()
        collect(vm)
        assertEquals(LocalDate.of(2026, 11, 22), vm.uiState.value.today)

        advanceTimeBy(60_000L + ROLLOVER_SLACK_MILLIS + 1)
        runCurrent()

        assertEquals(2, stats.seriesRequests.size)
        assertEquals(LocalDate.of(2026, 11, 23), localDateOf(stats.seriesRequests[1].second, zone))
        assertEquals(LocalDate.of(2026, 11, 23), vm.uiState.value.today)
        assertEquals(LocalDate.of(2026, 11, 23), vm.uiState.value.chart.dates.last())
    }

    @Test
    fun trackingOnFromTheStartShowsSeen() = runTest {
        settings.update { FeedCleanSettings(sessionTrackingEnabled = true) }
        stats.series.value = listOf(DayStats(LocalDate.of(2026, 11, 22), DailyCounts(seen = 40)))
        val vm = viewModel()
        collect(vm)
        assertEquals(40, vm.uiState.value.todayCounts.seen)
        assertTrue(vm.uiState.value.sessionTrackingEnabled)
    }
}
