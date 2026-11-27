package io.github.dreadnought0.feedclean.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Bottom navigation bar for the four top-level tabs.
 *
 * @param selected the tab that owns the current screen.
 * @param onSelect called with the tapped tab (including re-taps of the selected one).
 */
@Composable
fun FeedCleanBottomBar(
    selected: TopLevelKey,
    onSelect: (TopLevelKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination.key == selected,
                onClick = { onSelect(destination.key) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Preview
@Composable
private fun FeedCleanBottomBarPreview() {
    FeedCleanTheme(dynamicColor = false) {
        FeedCleanBottomBar(selected = SettingsKey, onSelect = {})
    }
}
