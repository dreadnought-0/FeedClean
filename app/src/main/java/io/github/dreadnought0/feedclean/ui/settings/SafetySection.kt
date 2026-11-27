package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.ui.common.KillSwitchCard

/** Settings → Automation safety: the global kill-switch and the screen-on guard. */
@Composable
fun SafetySection(settings: FeedCleanSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_safety))
        KillSwitchCard(
            engaged = settings.killSwitchEngaged,
            onEngagedChange = actions::setKillSwitchEngaged,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_screen_on_only),
            summary = stringResource(R.string.ui_settings_screen_on_only_summary),
            checked = settings.autoSkipOnlyWhenScreenOn,
            onCheckedChange = actions::setAutoSkipOnlyWhenScreenOn,
        )
    }
}
