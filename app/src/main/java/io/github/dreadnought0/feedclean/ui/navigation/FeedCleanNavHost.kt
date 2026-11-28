package io.github.dreadnought0.feedclean.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.dreadnought0.feedclean.ui.accounts.AccountDetailScreen
import io.github.dreadnought0.feedclean.ui.accounts.AccountsScreen
import io.github.dreadnought0.feedclean.ui.audit.AuditScreen
import io.github.dreadnought0.feedclean.ui.data.DataTransferScreen
import io.github.dreadnought0.feedclean.ui.debug.DebugRoute
import io.github.dreadnought0.feedclean.ui.home.ArmedGuideScreen
import io.github.dreadnought0.feedclean.ui.home.HomeNavigation
import io.github.dreadnought0.feedclean.ui.home.HomeRoute
import io.github.dreadnought0.feedclean.ui.settings.AdvancedNavigation
import io.github.dreadnought0.feedclean.ui.settings.AdvancedRoute
import io.github.dreadnought0.feedclean.ui.settings.BlockingScreen
import io.github.dreadnought0.feedclean.ui.settings.BubbleCoverScreen
import io.github.dreadnought0.feedclean.ui.settings.DataPrivacyScreen
import io.github.dreadnought0.feedclean.ui.settings.DetectionRulesScreen
import io.github.dreadnought0.feedclean.ui.settings.NotInterestedScreen
import io.github.dreadnought0.feedclean.ui.settings.PaceScreen
import io.github.dreadnought0.feedclean.ui.settings.SettingsHost
import io.github.dreadnought0.feedclean.ui.settings.SettingsNavigation
import io.github.dreadnought0.feedclean.ui.settings.SettingsRoute
import io.github.dreadnought0.feedclean.ui.settings.TimingScreen
import io.github.dreadnought0.feedclean.ui.settings.WhereItSkipsScreen
import io.github.dreadnought0.feedclean.ui.setup.SetupRoute
import io.github.dreadnought0.feedclean.ui.stats.StatsScreen
import io.github.dreadnought0.feedclean.ui.suggested.SuggestedScreen

/**
 * The app shell: a floating glass tab bar + Navigation 3 [NavDisplay] over one saveable back stack.
 *
 * Each entry gets its own saveable state and ViewModelStore (entry decorators), so ViewModels are scoped to
 * the destination and cleared when it is popped. Screens draw their own top bars. The tab bar shows only on the
 * four top-level tabs; pushed screens (sub-screens, Setup, Debug…) use the full height and a back button.
 */
@Composable
fun FeedCleanNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    val currentTab by remember(backStack) { derivedStateOf { backStack.currentTopLevel() } }
    val onTopLevel by remember(backStack) { derivedStateOf { backStack.lastOrNull() is TopLevelKey } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTopLevel) FeedCleanBottomBar(selected = currentTab, onSelect = { backStack.selectTopLevel(it) })
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            onBack = { backStack.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                feedCleanEntries(backStack)
            },
        )
    }
}

/** Maps every destination key to its screen. */
private fun EntryProviderScope<NavKey>.feedCleanEntries(
    backStack: MutableList<NavKey>,
) {
    val back: () -> Unit = { backStack.pop() }
    fun go(key: NavKey): () -> Unit = { backStack.push(key) }

    entry<HomeKey> {
        HomeRoute(
            navigation = HomeNavigation(
                openSetup = go(SetupKey),
                openAudit = go(AuditKey),
                openGuide = go(ArmedGuideKey),
                openWhere = go(WhereItSkipsKey),
                openDetectionRules = go(DetectionRulesKey),
                openTiming = go(TimingKey),
                openNotInterested = go(NotInterestedKey),
                openBlocking = go(BlockingKey),
                openBubbleAndCover = go(BubbleCoverKey),
            ),
        )
    }
    entry<AccountsKey> {
        AccountsScreen(onOpenAccount = { id -> backStack.push(AccountDetailKey(id)) })
    }
    entry<StatsKey> { StatsScreen() }
    entry<SettingsKey> {
        SettingsRoute(
            navigation = SettingsNavigation(
                openDetectionRules = go(DetectionRulesKey),
                openTiming = go(TimingKey),
                openPace = go(PaceKey),
                openData = go(DataPrivacyKey),
                openExport = go(DataTransferKey()),
                openAdvanced = go(AdvancedKey),
            ),
        )
    }
    entry<AccountDetailKey> { key -> AccountDetailScreen(accountId = key.accountId, onBack = back) }
    entry<AuditKey> { AuditScreen(onBack = back) }
    entry<SuggestedKey> { SuggestedScreen(onBack = back) }
    entry<SetupKey> { SetupRoute(onBack = back) }
    entry<DebugKey> { DebugRoute(onBack = back) }
    entry<DataTransferKey> { key -> DataTransferScreen(action = key.action, onBack = back) }
    entry<ArmedGuideKey> { ArmedGuideScreen(onBack = back) }
    entry<WhereItSkipsKey> { SettingsHost { state, actions -> WhereItSkipsScreen(state, actions, back) } }
    entry<DetectionRulesKey> { SettingsHost { state, actions -> DetectionRulesScreen(state, actions, back) } }
    entry<NotInterestedKey> { SettingsHost { state, actions -> NotInterestedScreen(state, actions, back) } }
    entry<BlockingKey> { SettingsHost { state, actions -> BlockingScreen(state, actions, back, go(PaceKey)) } }
    entry<BubbleCoverKey> { SettingsHost { state, actions -> BubbleCoverScreen(state, actions, back) } }
    entry<TimingKey> { SettingsHost { state, actions -> TimingScreen(state, actions, back) } }
    entry<PaceKey> { SettingsHost { state, actions -> PaceScreen(state, actions, back) } }
    entry<DataPrivacyKey> { SettingsHost { state, actions -> DataPrivacyScreen(state, actions, back) } }
    entry<AdvancedKey> {
        AdvancedRoute(
            navigation = AdvancedNavigation(
                openDebug = go(DebugKey),
                openSetup = go(SetupKey),
                openAudit = go(AuditKey),
                openSuggested = go(SuggestedKey),
            ),
            onBack = back,
        )
    }
}
