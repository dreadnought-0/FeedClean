package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** Settings → Overlay. */
@Composable
fun OverlaySection(settings: FeedCleanSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    val isDefaultPosition = settings.bubbleX == -1 && settings.bubbleY == -1
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_overlay))
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_show_bubble),
            summary = stringResource(R.string.ui_settings_show_bubble_summary),
            checked = settings.showBubble,
            onCheckedChange = actions::setShowBubble,
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_reset_bubble),
            summary = if (isDefaultPosition) {
                stringResource(R.string.ui_settings_bubble_default)
            } else {
                stringResource(R.string.ui_settings_bubble_custom, settings.bubbleX, settings.bubbleY)
            },
            enabled = !isDefaultPosition,
            onClick = actions::resetBubblePosition,
        )
    }
}
