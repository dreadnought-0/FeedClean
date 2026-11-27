package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * Settings → Not interested. The automated switch goes through
 * [SettingsActions.requestNotInterestedAutomated], which shows the account-risk dialog first.
 */
@Composable
fun NotInterestedSection(settings: FeedCleanSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_not_interested))
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_ni_manual),
            summary = stringResource(R.string.ui_settings_ni_manual_summary),
            checked = settings.notInterestedManualEnabled,
            onCheckedChange = actions::setNotInterestedManualEnabled,
        )
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_ni_automated),
            summary = stringResource(R.string.ui_settings_ni_automated_summary),
            checked = settings.notInterestedAutomated,
            onCheckedChange = actions::requestNotInterestedAutomated,
        )
        if (settings.notInterestedAutomated && !settings.curateFeedEnabled) {
            SettingsFootnote(
                stringResource(R.string.ui_settings_ni_needs_curate),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (settings.notInterestedRiskAcknowledged) {
            ActionSettingRow(
                title = stringResource(R.string.ui_settings_ni_revoke),
                summary = stringResource(R.string.ui_settings_ni_revoke_summary),
                onClick = actions::revokeNotInterestedRisk,
            )
        }
        RadioGroupSettingRow(
            title = stringResource(R.string.ui_settings_ni_reason),
            options = notInterestedReasonOptions(),
            selected = settings.notInterestedReason,
            onSelect = actions::setNotInterestedReason,
        )
    }
}
