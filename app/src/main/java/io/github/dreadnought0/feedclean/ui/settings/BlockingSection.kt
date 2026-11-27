package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * Settings → Blocking. The rate limit is shared with automated "Not interested"; the hard
 * floor (min 3 s) and cap (max 120/h) are stated in the helper text and enforced by the repository.
 */
@Composable
fun BlockingSection(settings: FeedCleanSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_blocking))
        SettingsFootnote(stringResource(R.string.ui_settings_blocking_intro))
        RadioGroupSettingRow(
            title = stringResource(R.string.ui_settings_block_style),
            options = blockStyleOptions(),
            selected = settings.blockStyle,
            onSelect = actions::setBlockStyle,
        )
        SettingsSubheader(stringResource(R.string.ui_settings_rate_limit))
        StepperSettingRow(
            title = stringResource(R.string.ui_settings_min_seconds),
            value = settings.minSecondsBetweenActions,
            range = FeedCleanSettings.MIN_SECONDS_FLOOR..MIN_SECONDS_UI_MAX,
            valueLabel = { stringResource(R.string.ui_unit_seconds, it) },
            onValueChange = actions::setMinSecondsBetweenActions,
            summary = stringResource(R.string.ui_settings_min_seconds_summary, FeedCleanSettings.MIN_SECONDS_FLOOR),
        )
        SliderSettingRow(
            title = stringResource(R.string.ui_settings_max_per_hour),
            value = settings.maxActionsPerHour,
            valueRange = 1..FeedCleanSettings.MAX_PER_HOUR_CAP,
            step = 1,
            valueLabel = { stringResource(R.string.ui_unit_per_hour, it) },
            onValueCommitted = actions::setMaxActionsPerHour,
            summary = stringResource(R.string.ui_settings_max_per_hour_summary, FeedCleanSettings.MAX_PER_HOUR_CAP),
        )
    }
}

/** Upper bound of the stepper only (the repository has no cap here); larger imported values still display. */
private const val MIN_SECONDS_UI_MAX = 600
