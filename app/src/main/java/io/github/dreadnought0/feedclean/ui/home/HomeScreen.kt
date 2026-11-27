package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.common.KillSwitchCard
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.setup.rememberSetupStatus
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/** Home tab destination. Creates the entry-scoped [HomeViewModel] from the app container. */
@Composable
fun HomeRoute(
    onOpenSetup: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenSuggested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container.settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val setupStatus = rememberSetupStatus()
    HomeScreen(
        state = state,
        serviceEnabled = setupStatus?.serviceEnabled,
        onCurateFeedChange = viewModel::setCurateFeedEnabled,
        onAutoSkipOrganicShopChange = viewModel::setAutoSkipOrganicShop,
        onSkipCoverChange = viewModel::setSkipCoverEnabled,
        onKillSwitchChange = viewModel::setKillSwitchEngaged,
        onOpenSetup = onOpenSetup,
        onOpenDebug = onOpenDebug,
        onOpenAudit = onOpenAudit,
        onOpenSuggested = onOpenSuggested,
        modifier = modifier,
    )
}

/**
 * Stateless Home dashboard: service status (with a Setup warning when the service is off), quick toggles,
 * the kill-switch, today's counts ([HomeStatsSection]) and links to the tools.
 *
 * @param serviceEnabled accessibility service enabled in system settings; null until first checked.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    serviceEnabled: Boolean?,
    onCurateFeedChange: (Boolean) -> Unit,
    onAutoSkipOrganicShopChange: (Boolean) -> Unit,
    onSkipCoverChange: (Boolean) -> Unit,
    onKillSwitchChange: (Boolean) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenSuggested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FeedCleanTopAppBar(
                title = stringResource(R.string.ui_home_title),
                actions = {
                    IconButton(onClick = onOpenDebug) {
                        Icon(FeedCleanIcons.Code, contentDescription = stringResource(R.string.ui_debug_title))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ServiceStatusCard(
                serviceEnabled = serviceEnabled,
                serviceConnected = state.serviceConnected,
                tiktokForeground = state.tiktokForeground,
                onOpenSetup = onOpenSetup,
            )
            QuickTogglesCard(
                state = state,
                onCurateFeedChange = onCurateFeedChange,
                onAutoSkipOrganicShopChange = onAutoSkipOrganicShopChange,
                onSkipCoverChange = onSkipCoverChange,
            )
            KillSwitchCard(engaged = state.killSwitchEngaged, onEngagedChange = onKillSwitchChange)
            HomeStatsSection()
            HomeToolsCard(
                onOpenSetup = onOpenSetup,
                onOpenAudit = onOpenAudit,
                onOpenSuggested = onOpenSuggested,
                onOpenDebug = onOpenDebug,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun HomeScreenPreview() {
    FeedCleanTheme(dynamicColor = false) {
        HomeScreen(
            state = HomeUiState(serviceConnected = false, curateFeedEnabled = true),
            serviceEnabled = false,
            onCurateFeedChange = {},
            onAutoSkipOrganicShopChange = {},
            onSkipCoverChange = {},
            onKillSwitchChange = {},
            onOpenSetup = {},
            onOpenDebug = {},
            onOpenAudit = {},
            onOpenSuggested = {},
        )
    }
}
