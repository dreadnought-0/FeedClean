package io.github.dreadnought0.feedclean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.data.StatsRepository
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import io.github.dreadnought0.feedclean.ui.stats.SessionRowModel
import io.github.dreadnought0.feedclean.ui.stats.midnightTicks
import io.github.dreadnought0.feedclean.ui.stats.toRowModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/** What the Home "Today" card shows. */
data class HomeStatsUiState(
    /** False until the first database emission. */
    val loaded: Boolean = false,
    val counts: DailyCounts = DailyCounts(),
    val sessionTrackingEnabled: Boolean = false,
    /** Most recent session, only when tracking is on. */
    val latestSession: SessionRowModel? = null,
)

/**
 * Backs [HomeStatsSection]: today's counts from `StatsRepository.observeToday`, re-collected just after
 * each local midnight (see [midnightTicks]) so "today" rolls over while Home stays open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeStatsViewModel(
    statsRepository: StatsRepository,
    settingsRepository: SettingsRepository,
    clock: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val tracking = settingsRepository.settings.map { it.sessionTrackingEnabled }.distinctUntilChanged()

    /** Combined state; collection starts and stops with the screen. */
    val uiState: StateFlow<HomeStatsUiState> = combine(
        midnightTicks(clock, zone).flatMapLatest { now -> statsRepository.observeToday(now, zone()) },
        tracking,
        tracking.flatMapLatest { on -> if (on) statsRepository.observeSessionRatios(1) else flowOf(emptyList()) },
    ) { counts, on, sessions ->
        HomeStatsUiState(
            loaded = true,
            counts = counts,
            sessionTrackingEnabled = on,
            latestSession = if (on) sessions.firstOrNull()?.toRowModel() else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeStatsUiState())

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Factory reading the repositories from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeStatsViewModel(container.statsRepository, container.settingsRepository) }
        }
    }
}
