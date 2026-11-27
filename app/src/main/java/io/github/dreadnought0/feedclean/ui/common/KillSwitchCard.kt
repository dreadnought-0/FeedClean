package io.github.dreadnought0.feedclean.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * The global "Pause all automation" kill-switch (`killSwitchEngaged`), styled in the error palette so it
 * stands apart from ordinary toggles. Used on Home and in Settings → Automation safety.
 *
 * @param engaged true while all automation is paused.
 * @param onEngagedChange called with the new value when the user flips it.
 */
@Composable
fun KillSwitchCard(
    engaged: Boolean,
    onEngagedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (engaged) colors.errorContainer else colors.surfaceContainerHigh
    val content = if (engaged) colors.onErrorContainer else colors.onSurface
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        // The toggle target sits inside the Surface so its ripple is clipped to the card shape.
        Row(
            modifier = Modifier
                .toggleable(value = engaged, role = Role.Switch, onValueChange = onEngagedChange)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp),
            ) {
                Surface(shape = CircleShape, color = colors.error, contentColor = colors.onError) {
                    Icon(
                        FeedCleanIcons.Pause,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ui_kill_switch_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (engaged) R.string.ui_kill_switch_engaged else R.string.ui_kill_switch_summary,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = engaged,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onError,
                    checkedTrackColor = colors.error,
                    checkedBorderColor = colors.error,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun KillSwitchCardPreview() {
    FeedCleanTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            KillSwitchCard(engaged = false, onEngagedChange = {})
            KillSwitchCard(engaged = true, onEngagedChange = {})
        }
    }
}
