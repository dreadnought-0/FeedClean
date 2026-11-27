package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.ui.data.DataTransferAction

/**
 * Settings → Data & privacy. Export / import / clear only navigate to the DataTransfer
 * screen, which does the work (and asks for confirmation before clearing anything).
 */
@Composable
fun DataPrivacySection(
    settings: FeedCleanSettings,
    actions: SettingsActions,
    onOpenDataTransfer: (DataTransferAction) -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_data))
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_session_tracking),
            summary = stringResource(R.string.ui_settings_session_tracking_summary),
            checked = settings.sessionTrackingEnabled,
            onCheckedChange = actions::setSessionTrackingEnabled,
        )
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_save_screenshots),
            summary = stringResource(R.string.ui_settings_save_screenshots_summary),
            checked = settings.saveScreenshots,
            onCheckedChange = actions::setSaveScreenshots,
        )
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_decision_log),
            summary = stringResource(R.string.ui_settings_decision_log_summary),
            checked = settings.decisionLogEnabled,
            onCheckedChange = actions::setDecisionLogEnabled,
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_open_debug),
            summary = stringResource(R.string.ui_settings_open_debug_summary),
            onClick = onOpenDebug,
        )
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_subjective_tags),
            summary = stringResource(R.string.ui_settings_subjective_tags_summary),
            checked = settings.publicExportIncludeSubjectiveTags,
            onCheckedChange = actions::setPublicExportIncludeSubjectiveTags,
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_export_full),
            summary = stringResource(R.string.ui_settings_export_full_summary),
            onClick = { onOpenDataTransfer(DataTransferAction.EXPORT_FULL) },
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_export_public),
            summary = stringResource(R.string.ui_settings_export_public_summary),
            onClick = { onOpenDataTransfer(DataTransferAction.EXPORT_PUBLIC) },
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_import),
            summary = stringResource(R.string.ui_settings_import_summary),
            onClick = { onOpenDataTransfer(DataTransferAction.IMPORT) },
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_clear_all),
            summary = stringResource(R.string.ui_settings_clear_all_summary),
            destructive = true,
            onClick = { onOpenDataTransfer(DataTransferAction.CLEAR_ALL) },
        )
        SettingsFootnote(stringResource(R.string.ui_settings_privacy_note))
    }
}
