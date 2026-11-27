package io.github.dreadnought0.feedclean.ui.home

import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.Session
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import io.github.dreadnought0.feedclean.ui.MainDispatcherRule
import io.github.dreadnought0.feedclean.ui.stats.FakeStatsRepository
import io.github.dreadnought0.feedclean.ui.stats.ROLLOVER_SLACK_MILLIS
import io.github.dreadnought0.feedclean.ui.stats.localDateOf
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeStatsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val stats = FakeStatsRepository()
    private val settings = InMemorySettingsRepository()

    private fun TestScope.viewModel(base: Long) =
        HomeStatsViewModel(stats, settings, clock = { base + currentTime }, zone = { zone })

    private fun TestScope.collect(vm: HomeStatsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

    private fun millis(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun showsTodaysCountsAndFollowsChanges() = runTest {
        val vm = viewModel(millis("2026-11-22T10:00"))
        collect(vm)
        assertTrue(vm.uiState.value.loaded)
        assertEquals(DailyCounts(), vm.uiState.value.counts)

        stats.today.value = DailyCounts(detected = 4, blocked = 1)
        assertEquals(4, vm.uiState.value.counts.detected)
        assertEquals(1, vm.uiState.value.counts.blocked)
    }

    @Test
    fun recollectsTodayAfterMidnightIncludingADstNight() = runTest {
        // 2026-11-01 is the 25-hour fall-back day in New York.
        val base = millis("2026-11-01T22:00")
        val vm = viewModel(base)
        collect(vm)
        assertEquals(listOf(base), stats.todayRequests)

        // Two wall-clock hours to midnight, plus the extra DST hour earlier that night is already behind us.
        advanceTimeBy(2 * 3_600_000L + ROLLOVER_SLACK_MILLIS + 1)
        runCurrent()

        assertEquals(2, stats.todayRequests.size)
        assertEquals(LocalDate.of(2026, 11, 2), localDateOf(stats.todayRequests[1], zone))
    }

    @Test
    fun sessionInfoOnlyWithTracking() = runTest {
        stats.sessions.value = listOf(
            SessionRatio.of(Session(1, 0L, 60_000L, videosSeen = 50, ads = 5, promoted = 0, shop = 10, live = 0, skipped = 8)),
        )
        val vm = viewModel(millis("2026-11-22T10:00"))
        collect(vm)
        assertFalse(vm.uiState.value.sessionTrackingEnabled)
        assertNull(vm.uiState.value.latestSession)
        assertTrue(stats.sessionRequests.isEmpty())

        settings.update { it.copy(sessionTrackingEnabled = true) }
        runCurrent()
        assertTrue(vm.uiState.value.sessionTrackingEnabled)
        assertEquals(20, vm.uiState.value.latestSession?.shop?.percent)
        assertEquals(listOf(1), stats.sessionRequests)
    }
}
