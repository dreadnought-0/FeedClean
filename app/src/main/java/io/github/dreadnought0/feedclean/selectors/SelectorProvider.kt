package io.github.dreadnought0.feedclean.selectors

import android.content.Context
import androidx.core.net.toUri
import io.github.dreadnought0.feedclean.detect.ScreenParser
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Keeps the active [SelectorConfig] and [ScreenParser] in sync with Settings → Detection → selector override.
 *
 * Loads off the main thread. If the override file can't be read or parsed, falls back to the bundled
 * `assets/selectors/tiktok.json` and reports why in [loadError] (shown on the Debug screen).
 */
class SelectorProvider(
    private val appContext: Context,
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
) {
    private val _config = MutableStateFlow<SelectorConfig?>(null)
    private val _parser = MutableStateFlow<ScreenParser?>(null)
    private val _loadError = MutableStateFlow<String?>(null)
    private val _source = MutableStateFlow("bundled")

    /** Active selector config, or null until the first load completes. */
    val config: StateFlow<SelectorConfig?> = _config.asStateFlow()

    /** Parser built from [config]. Stateless and thread-safe. Null until the first load completes. */
    val parser: StateFlow<ScreenParser?> = _parser.asStateFlow()

    /** Why the override could not be used, or null. */
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** "bundled" or the override URI in use. */
    val source: StateFlow<String> = _source.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            settingsRepository.settings
                .map { it.selectorOverrideUri }
                .distinctUntilChanged()
                .collect { overrideUri -> reload(overrideUri) }
        }
    }

    private fun reload(overrideUri: String?) {
        var error: String? = null
        val override = overrideUri?.let { uri ->
            runCatching {
                appContext.contentResolver.openInputStream(uri.toUri())
                    ?.use { SelectorConfigLoader.load(it) }
                    ?: error("could not open the file")
            }.onFailure { error = "Selector override not used: ${it.message}" }.getOrNull()
        }
        val config = override ?: loadBundledSelectors(appContext)
        _source.value = overrideUri?.takeIf { override != null } ?: "bundled"
        _loadError.value = error
        _config.value = config
        _parser.value = ScreenParser(config, Locale.getDefault().language)
    }
}
