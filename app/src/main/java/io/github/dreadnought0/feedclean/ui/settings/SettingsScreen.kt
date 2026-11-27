package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.common.PreviewHost
import io.github.dreadnought0.feedclean.ui.data.DataTransferAction
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/** Settings tab destination. Creates the entry-scoped [SettingsViewModel] from the app container. */
@Composable
fun SettingsRoute(
    onOpenDataTransfer: (DataTransferAction) -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    SettingsScreen(
        viewModel = viewModel,
        onOpenDataTransfer = onOpenDataTransfer,
        onOpenDebug = onOpenDebug,
        modifier = modifier,
    )
}

/** Settings screen bound to [viewModel], including the automated-Not-interested risk dialog. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenDataTransfer: (DataTransferAction) -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val riskDialogVisible by viewModel.riskDialogVisible.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        actions = viewModel,
        onOpenDataTransfer = onOpenDataTransfer,
        onOpenDebug = onOpenDebug,
        modifier = modifier,
    )
    if (riskDialogVisible) {
        NotInterestedRiskDialog(
            minSeconds = settings.minSecondsBetweenActions,
            maxPerHour = settings.maxActionsPerHour,
            onAccept = viewModel::acceptNotInterestedRisk,
            onDismiss = viewModel::dismissNotInterestedRisk,
        )
    }
}

/**
 * Stateless Settings list: every [FeedCleanSettings] field, grouped into sections (Detection,
 * Curate feed, Not interested, Overlay, Blocking, Automation safety, Data & privacy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: FeedCleanSettings,
    actions: SettingsActions,
    onOpenDataTransfer: (DataTransferAction) -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FeedCleanTopAppBar(
                title = stringResource(R.string.ui_settings_title),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
            item(key = "detection") { DetectionSection(settings, actions) }
            item(key = "curate") { CurateFeedSection(settings, actions) }
            item(key = "not_interested") { NotInterestedSection(settings, actions) }
            item(key = "overlay") { OverlaySection(settings, actions) }
            item(key = "blocking") { BlockingSection(settings, actions) }
            item(key = "safety") { SafetySection(settings, actions) }
            item(key = "data") { DataPrivacySection(settings, actions, onOpenDataTransfer, onOpenDebug) }
            item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Preview(showBackground = true, heightDp = 2400)
@Composable
private fun SettingsScreenPreview() {
    val viewModel = remember {
        SettingsViewModel(InMemorySettingsRepository(FeedCleanSettings(curateFeedEnabled = false, bubbleX = 40)))
    }
    PreviewHost {
        FeedCleanTheme(dynamicColor = false) {
            SettingsScreen(viewModel = viewModel, onOpenDataTransfer = {}, onOpenDebug = {})
        }
    }
}
