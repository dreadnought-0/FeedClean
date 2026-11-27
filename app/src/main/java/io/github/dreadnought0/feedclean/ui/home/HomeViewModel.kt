package io.github.dreadnought0.feedclean.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A place auto-skip can be enabled for (the four "Where to auto-skip" switches). */
enum class AutoSkipPlace { FOR_YOU, OTHER_HOME_TABS, SEARCH, OTHER_FEEDS }

/** The enabled places, in display order. */
fun autoSkipPlaces(forYou: Boolean, otherHome: Boolean, search: Boolean, otherFeeds: Boolean): List<AutoSkipPlace> =
    listOfNotNull(
        AutoSkipPlace.FOR_YOU.takeIf { forYou },
        AutoSkipPlace.OTHER_HOME_TABS.takeIf { otherHome },
        AutoSkipPlace.SEARCH.takeIf { search },
        AutoSkipPlace.OTHER_FEEDS.takeIf { otherFeeds },
    )

/** Joins place labels as "For You · Search · Profiles"; null when no place is enabled. */
fun autoSkipSummary(places: List<AutoSkipPlace>, label: (AutoSkipPlace) -> String): String? =
    places.takeIf { it.isNotEmpty() }?.joinToString(" · ", transform = label)

/** Everything the Home dashboard header and quick toggles show. */
data class HomeUiState(
    val serviceConnected: Boolean = false,
    val tiktokForeground: Boolean = false,
    val curateFeedEnabled: Boolean = false,
    val autoSkipOrganicShop: Boolean = true,
    val skipCoverEnabled: Boolean = true,
    val killSwitchEngaged: Boolean = false,
    val autoSkipInForYou: Boolean = true,
    val autoSkipInOtherHomeTabs: Boolean = false,
    val autoSkipInSearch: Boolean = false,
    val autoSkipInOtherFeeds: Boolean = false,
) {
    /** Places where auto-skip is allowed, in display order. */
    val autoSkipPlaces: List<AutoSkipPlace>
        get() = autoSkipPlaces(autoSkipInForYou, autoSkipInOtherHomeTabs, autoSkipInSearch, autoSkipInOtherFeeds)

    /** True when auto-skip may run right now (mirrors [FeedCleanSettings.autoSkipArmed]). */
    val autoSkipArmed: Boolean get() = curateFeedEnabled && !killSwitchEngaged

    companion object {
        /** Builds the state from its sources. */
        fun from(settings: FeedCleanSettings, serviceConnected: Boolean, tiktokForeground: Boolean) = HomeUiState(
            serviceConnected = serviceConnected,
            tiktokForeground = tiktokForeground,
            curateFeedEnabled = settings.curateFeedEnabled,
            autoSkipOrganicShop = settings.autoSkipOrganicShop,
            skipCoverEnabled = settings.skipCoverEnabled,
            killSwitchEngaged = settings.killSwitchEngaged,
            autoSkipInForYou = settings.autoSkipInForYou,
            autoSkipInOtherHomeTabs = settings.autoSkipInOtherHomeTabs,
            autoSkipInSearch = settings.autoSkipInSearch,
            autoSkipInOtherFeeds = settings.autoSkipInOtherFeeds,
        )
    }
}

/**
 * Backs the Home quick toggles. Live service state defaults to [ScreenStateHolder]; tests inject flows.
 * All writes go through `SettingsRepository.update`.
 */
class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    serviceConnected: StateFlow<Boolean> = ScreenStateHolder.serviceConnected,
    tiktokForeground: StateFlow<Boolean> = ScreenStateHolder.tiktokForeground,
) : ViewModel() {

    /** Combined settings + live service state. */
    val uiState: StateFlow<HomeUiState> =
        combine(settingsRepository.settings, serviceConnected, tiktokForeground, HomeUiState::from)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HomeUiState.from(
                    settingsRepository.settings.value,
                    serviceConnected.value,
                    tiktokForeground.value,
                ),
            )

    /** "Curate feed" master switch. */
    fun setCurateFeedEnabled(enabled: Boolean) = edit { it.copy(curateFeedEnabled = enabled) }

    /** "Auto-skip organic Shop posts". */
    fun setAutoSkipOrganicShop(enabled: Boolean) = edit { it.copy(autoSkipOrganicShop = enabled) }

    /** "Skip-cover". */
    fun setSkipCoverEnabled(enabled: Boolean) = edit { it.copy(skipCoverEnabled = enabled) }

    /** "Pause all automation" kill-switch. */
    fun setKillSwitchEngaged(engaged: Boolean) = edit { it.copy(killSwitchEngaged = engaged) }

    private fun edit(transform: (FeedCleanSettings) -> FeedCleanSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
