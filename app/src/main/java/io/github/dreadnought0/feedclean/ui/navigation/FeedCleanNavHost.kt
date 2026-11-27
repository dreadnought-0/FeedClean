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
import io.github.dreadnought0.feedclean.ui.home.HomeRoute
import io.github.dreadnought0.feedclean.ui.settings.SettingsRoute
import io.github.dreadnought0.feedclean.ui.setup.SetupRoute
import io.github.dreadnought0.feedclean.ui.stats.StatsScreen
import io.github.dreadnought0.feedclean.ui.suggested.SuggestedScreen

/**
 * The app shell: bottom navigation bar + Navigation 3 [NavDisplay] over one saveable back stack.
 *
 * Each entry gets its own saveable state and ViewModelStore (entry decorators), so ViewModels are scoped to
 * the destination and cleared when it is popped. Screens draw their own top bars; the shell only reserves
 * room for the bottom bar.
 */
@Composable
fun FeedCleanNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    val currentTab by remember(backStack) { derivedStateOf { backStack.currentTopLevel() } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            FeedCleanBottomBar(selected = currentTab, onSelect = { backStack.selectTopLevel(it) })
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

    entry<HomeKey> {
        HomeRoute(
            onOpenSetup = { backStack.push(SetupKey) },
            onOpenDebug = { backStack.push(DebugKey) },
            onOpenAudit = { backStack.push(AuditKey) },
            onOpenSuggested = { backStack.push(SuggestedKey) },
        )
    }
    entry<AccountsKey> {
        AccountsScreen(onOpenAccount = { id -> backStack.push(AccountDetailKey(id)) })
    }
    entry<StatsKey> { StatsScreen() }
    entry<SettingsKey> {
        SettingsRoute(
            onOpenDataTransfer = { action -> backStack.push(DataTransferKey(action)) },
            onOpenDebug = { backStack.push(DebugKey) },
        )
    }
    entry<AccountDetailKey> { key -> AccountDetailScreen(accountId = key.accountId, onBack = back) }
    entry<AuditKey> { AuditScreen(onBack = back) }
    entry<SuggestedKey> { SuggestedScreen(onBack = back) }
    entry<SetupKey> { SetupRoute(onBack = back) }
    entry<DebugKey> { DebugRoute(onBack = back) }
    entry<DataTransferKey> { key -> DataTransferScreen(action = key.action, onBack = back) }
}
