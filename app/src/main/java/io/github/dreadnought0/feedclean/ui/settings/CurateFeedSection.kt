package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** Settings → Curate feed. Auto-skip is navigation only. */
@Composable
fun CurateFeedSection(settings: FeedCleanSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_curate))
        SwitchSettingRow(
            title = stringResource(R.string.ui_curate_feed_title),
            summary = stringResource(R.string.ui_curate_feed_summary),
            checked = settings.curateFeedEnabled,
            onCheckedChange = actions::setCurateFeedEnabled,
        )
        if (settings.killSwitchEngaged) {
            SettingsFootnote(
                stringResource(R.string.ui_settings_paused_by_kill_switch),
                color = MaterialTheme.colorScheme.error,
            )
        }
        DependentSettings(
            active = settings.curateFeedEnabled,
            inactiveNote = stringResource(R.string.ui_settings_curate_off_note),
        ) {
            SettingsSubheader(stringResource(R.string.ui_settings_skip_targets))
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_organic_shop),
                summary = stringResource(R.string.ui_settings_skip_organic_shop_summary),
                checked = settings.autoSkipOrganicShop,
                onCheckedChange = actions::setAutoSkipOrganicShop,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_paid_ads),
                summary = stringResource(R.string.ui_settings_skip_paid_ads_summary),
                checked = settings.autoSkipPaidAds,
                onCheckedChange = actions::setAutoSkipPaidAds,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_promoted),
                checked = settings.autoSkipPromoted,
                onCheckedChange = actions::setAutoSkipPromoted,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_live_shopping),
                checked = settings.autoSkipLiveShopping,
                onCheckedChange = actions::setAutoSkipLiveShopping,
            )

            SettingsSubheader(stringResource(R.string.ui_settings_skip_where))
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_where_for_you),
                checked = settings.autoSkipInForYou,
                onCheckedChange = actions::setAutoSkipInForYou,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_where_other_home),
                checked = settings.autoSkipInOtherHomeTabs,
                onCheckedChange = actions::setAutoSkipInOtherHomeTabs,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_where_search),
                checked = settings.autoSkipInSearch,
                onCheckedChange = actions::setAutoSkipInSearch,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_skip_where_other_feeds),
                checked = settings.autoSkipInOtherFeeds,
                onCheckedChange = actions::setAutoSkipInOtherFeeds,
            )
            SettingsFootnote(stringResource(R.string.ui_settings_skip_where_note))

            SettingsSubheader(stringResource(R.string.ui_settings_skip_behaviour))
            SliderSettingRow(
                title = stringResource(R.string.ui_settings_settle_delay),
                value = settings.skipSettleDelayMs,
                valueRange = 0..FeedCleanSettings.MAX_SETTLE_DELAY_MS,
                step = SETTLE_DELAY_STEP_MS,
                valueLabel = { stringResource(R.string.ui_unit_ms, it) },
                onValueCommitted = actions::setSkipSettleDelayMs,
                summary = stringResource(
                    R.string.ui_settings_settle_delay_summary,
                    FeedCleanSettings.MAX_SETTLE_DELAY_MS,
                ),
            )
            StepperSettingRow(
                title = stringResource(R.string.ui_settings_skip_cap),
                value = settings.consecutiveSkipCap,
                range = 1..FeedCleanSettings.MAX_CONSECUTIVE_SKIP_CAP,
                valueLabel = { it.toString() },
                onValueChange = actions::setConsecutiveSkipCap,
                summary = stringResource(
                    R.string.ui_settings_skip_cap_summary,
                    FeedCleanSettings.MAX_CONSECUTIVE_SKIP_CAP,
                ),
            )

            SettingsSubheader(stringResource(R.string.ui_settings_cover_subheader))
            SwitchSettingRow(
                title = stringResource(R.string.ui_skip_cover_title),
                summary = stringResource(R.string.ui_skip_cover_summary),
                checked = settings.skipCoverEnabled,
                onCheckedChange = actions::setSkipCoverEnabled,
            )
            DependentSettings(
                active = settings.skipCoverEnabled,
                inactiveNote = stringResource(R.string.ui_settings_cover_off_note),
            ) {
                RadioGroupSettingRow(
                    title = stringResource(R.string.ui_settings_cover_style),
                    options = coverStyleOptions(),
                    selected = settings.coverStyle,
                    onSelect = actions::setCoverStyle,
                )
            }
        }
    }
}

/** Slider granularity for the skip settle delay. */
private const val SETTLE_DELAY_STEP_MS = 50
