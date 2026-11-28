package io.github.dreadnought0.feedclean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dreadnought0.feedclean.armed.ArmedEvaluator
import io.github.dreadnought0.feedclean.armed.ArmedReport
import io.github.dreadnought0.feedclean.armed.LiveFacts
import io.github.dreadnought0.feedclean.service.AutoSkipStatus
import io.github.dreadnought0.feedclean.service.RuntimeCommand
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A place auto-skip can be enabled for (the four "Where it skips" switches). */
enum class AutoSkipPlace { FOR_YOU, OTHER_HOME_TABS, SEARCH, OTHER_FEEDS }

/** The enabled places, in display order. */
fun autoSkipPlaces(forYou: Boolean, otherHome: Boolean, search: Boolean, otherFeeds: Boolean): List<AutoSkipPlace> =
    listOfNotNull(
        AutoSkipPlace.FOR_YOU.takeIf { forYou },
        AutoSkipPlace.OTHER_HOME_TABS.takeIf { otherHome },
        AutoSkipPlace.SEARCH.takeIf { search },
        AutoSkipPlace.OTHER_FEEDS.takeIf { otherFeeds },
    )

/** The places enabled in [settings], in display order. */
fun FeedCleanSettings.autoSkipPlaceList(): List<AutoSkipPlace> =
    autoSkipPlaces(autoSkipInForYou, autoSkipInOtherHomeTabs, autoSkipInSearch, autoSkipInOtherFeeds)

/** Joins place labels as "For You · Search · Profiles"; null when no place is enabled. */
fun autoSkipSummary(places: List<AutoSkipPlace>, label: (AutoSkipPlace) -> String): String? =
    places.takeIf { it.isNotEmpty() }?.joinToString(" · ", transform = label)

/**
 * Everything Home (and the sub-screens' Armed pills) show: the settings, the honest [armed] report, and the live
 * service facts for the one-line status under the count.
 */
data class HomeUiState(
    val settings: FeedCleanSettings,
    val armed: ArmedReport,
    val serviceConnected: Boolean,
    val tiktokForeground: Boolean,
) {
    companion object {
        fun from(
            settings: FeedCleanSettings,
            serviceConnected: Boolean,
            tiktokForeground: Boolean,
            autoSkip: AutoSkipStatus = AutoSkipStatus(),
        ): HomeUiState {
            val facts = LiveFacts(
                serviceConnected = serviceConnected,
                loopGuardPaused = serviceConnected && autoSkip.pausedByLoopGuard,
                loopGuardPausedAfter = if (serviceConnected) autoSkip.pausedAfter else 0,
            )
            return HomeUiState(
                settings = settings,
                armed = ArmedEvaluator.evaluate(settings, facts),
                serviceConnected = serviceConnected,
                tiktokForeground = serviceConnected && tiktokForeground,
            )
        }
    }
}

/**
 * Combines settings with the live service state into [HomeUiState] through [ArmedEvaluator] (the single source of
 * truth for armed states). Settings edits go through [io.github.dreadnought0.feedclean.ui.settings.SettingsViewModel].
 * Live flows default to [ScreenStateHolder]; tests inject their own.
 */
class HomeViewModel(
    settingsRepository: SettingsRepository,
    serviceConnected: StateFlow<Boolean> = ScreenStateHolder.serviceConnected,
    tiktokForeground: StateFlow<Boolean> = ScreenStateHolder.tiktokForeground,
    autoSkip: StateFlow<AutoSkipStatus> = ScreenStateHolder.autoSkip,
    private val sendCommand: (RuntimeCommand) -> Boolean = ScreenStateHolder::send,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(settingsRepository.settings, serviceConnected, tiktokForeground, autoSkip, HomeUiState::from)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HomeUiState.from(
                    settingsRepository.settings.value,
                    serviceConnected.value,
                    tiktokForeground.value,
                    autoSkip.value,
                ),
            )

    /** "Resume" after the safety stop (too many skips in a row). Returns false if no service is running. */
    fun resumeAfterSafetyStop(): Boolean = sendCommand(RuntimeCommand.RESUME_AUTO_SKIP)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
