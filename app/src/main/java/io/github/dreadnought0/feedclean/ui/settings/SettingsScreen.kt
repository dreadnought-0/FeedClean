package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.ui.home.allSet
import io.github.dreadnought0.feedclean.ui.setup.rememberSetupStatus

/**
 * Settings tab: the slimmed root list (Detection rules, Timing & safety, Pace limits, Data & privacy, Export &
 * import, Advanced). Everything you turn on or off lives on Home.
 */
@Composable
fun SettingsRoute(navigation: SettingsNavigation, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsRootScreen(settings = settings, navigation = navigation, modifier = modifier)
}

/** Settings → Advanced. */
@Composable
fun AdvancedRoute(navigation: AdvancedNavigation, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connected by ScreenStateHolder.serviceConnected.collectAsStateWithLifecycle()
    val setup = rememberSetupStatus()
    AdvancedScreen(
        settings = settings,
        actions = viewModel,
        setupAllSet = setup?.allSet(connected),
        navigation = navigation,
        onBack = onBack,
    )
}
