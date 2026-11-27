package io.github.dreadnought0.feedclean.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.data.StatsRepository
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Everything the Stats screen shows. */
data class StatsUiState(
    /** False until the first database emission. */
    val loaded: Boolean = false,
    val nowMillis: Long = 0L,
    val today: LocalDate = LocalDate.MIN,
    val todayCounts: DailyCounts = DailyCounts(),
    val chart: DailyChartModel = DailyChartModel(emptyList(), emptyList()),
    /** Sum over the chart's days. */
    val totals: DailyCounts = DailyCounts(),
    val sessionTrackingEnabled: Boolean = false,
    /** Newest first; empty when tracking is off. */
    val sessions: List<SessionRowModel> = emptyList(),
)

/**
 * Backs the Stats tab: today's counts, the [CHART_DAYS]-day series (re-queried just after each local
 * midnight, see [midnightTicks]) and, only when session tracking is on, the recent session ratios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val statsRepository: StatsRepository,
    settingsRepository: SettingsRepository,
    clock: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private data class Window(val nowMillis: Long, val today: LocalDate, val stats: List<DayStats>)

    private val window: Flow<Window> = midnightTicks(clock, zone).flatMapLatest { now ->
        val z = zone()
        statsRepository.observeDailySeries(CHART_DAYS, now, z).map { Window(now, localDateOf(now, z), it) }
    }

    private val tracking: Flow<Boolean> =
        settingsRepository.settings.map { it.sessionTrackingEnabled }.distinctUntilChanged()

    private val sessions: Flow<List<SessionRatio>> = tracking.flatMapLatest { on ->
        if (on) statsRepository.observeSessionRatios(SESSION_LIMIT) else flowOf(emptyList())
    }

    /** Combined state; collection starts and stops with the screen. */
    val uiState: StateFlow<StatsUiState> = combine(window, tracking, sessions) { w, on, ratios ->
        val chart = buildDailyChartModel(w.stats, w.today)
        StatsUiState(
            loaded = true,
            nowMillis = w.nowMillis,
            today = w.today,
            todayCounts = countsOn(w.stats, w.today),
            chart = chart,
            totals = sumCounts(w.stats.filter { it.date in chart.dates }),
            sessionTrackingEnabled = on,
            sessions = if (on) ratios.map { it.toRowModel() } else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsUiState())

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Factory reading the repositories from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatsViewModel(container.statsRepository, container.settingsRepository) }
        }
    }
}
