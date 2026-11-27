package io.github.dreadnought0.feedclean.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Source of truth for [FeedCleanSettings]. The production implementation is DataStore-backed (data/).
 * Implementations MUST apply [FeedCleanSettings.sanitized] on every write.
 */
interface SettingsRepository {
    val settings: StateFlow<FeedCleanSettings>

    suspend fun update(transform: (FeedCleanSettings) -> FeedCleanSettings)
}

/** In-memory implementation for tests and previews. */
class InMemorySettingsRepository(
    initial: FeedCleanSettings = FeedCleanSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial.sanitized())
    override val settings: StateFlow<FeedCleanSettings> = state.asStateFlow()

    override suspend fun update(transform: (FeedCleanSettings) -> FeedCleanSettings) {
        state.update { transform(it).sanitized() }
    }
}
