package io.github.dreadnought0.feedclean.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons

/** The four bottom-bar tabs, in display order. */
enum class TopLevelDestination(
    val key: TopLevelKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(HomeKey, R.string.ui_tab_home, Icons.Filled.Home),
    ACCOUNTS(AccountsKey, R.string.ui_tab_accounts, Icons.Filled.AccountCircle),
    STATS(StatsKey, R.string.ui_tab_stats, FeedCleanIcons.BarChart),
    SETTINGS(SettingsKey, R.string.ui_tab_settings, Icons.Filled.Settings),
}
