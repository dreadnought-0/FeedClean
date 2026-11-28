package io.github.dreadnought0.feedclean.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass
import io.github.dreadnought0.feedclean.ui.theme.GlassBackground

/**
 * Floating glass pill with the four top-level tabs; the selected tab is a teal pill.
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
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .fillMaxWidth(),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Glass.Border),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(5.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val on = destination.key == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (on) Glass.Accent else Color.Transparent)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(destination.key) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        destination.icon,
                        contentDescription = null,
                        tint = if (on) Color.White else Glass.Muted,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) Color.White else Glass.Muted,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun FeedCleanBottomBarPreview() {
    FeedCleanTheme {
        GlassBackground { FeedCleanBottomBar(selected = SettingsKey, onSelect = {}) }
    }
}
