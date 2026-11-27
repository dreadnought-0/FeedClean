package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** Settings → Detection. */
@Composable
fun DetectionSection(settings: FeedCleanSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.ui_settings_group_detection))
        SwitchSettingRow(
            title = stringResource(R.string.ui_settings_detection_master),
            summary = stringResource(R.string.ui_settings_detection_master_summary),
            checked = settings.detectionEnabled,
            onCheckedChange = actions::setDetectionEnabled,
        )
        DependentSettings(
            active = settings.detectionEnabled,
            inactiveNote = stringResource(R.string.ui_settings_detection_off_note),
        ) {
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_detect_paid_ad),
                summary = stringResource(R.string.ui_settings_detect_paid_ad_summary),
                checked = settings.detectPaidAd,
                onCheckedChange = actions::setDetectPaidAd,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_detect_promoted),
                summary = stringResource(R.string.ui_settings_detect_promoted_summary),
                checked = settings.detectPromoted,
                onCheckedChange = actions::setDetectPromoted,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_detect_shop_anchor),
                summary = stringResource(R.string.ui_settings_detect_shop_anchor_summary),
                checked = settings.detectShopAnchor,
                onCheckedChange = actions::setDetectShopAnchor,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_detect_live_shopping),
                checked = settings.detectLiveShopping,
                onCheckedChange = actions::setDetectLiveShopping,
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_settings_ocr_fallback),
                summary = stringResource(R.string.ui_settings_ocr_fallback_summary),
                checked = settings.ocrFallbackEnabled,
                onCheckedChange = actions::setOcrFallbackEnabled,
            )
        }
        SettingsSubheader(stringResource(R.string.ui_settings_selectors_subheader))
        SelectorOverrideRows(
            overrideUri = settings.selectorOverrideUri,
            onOverrideSelected = actions::setSelectorOverrideUri,
            onUseBuiltIn = actions::clearSelectorOverride,
        )
    }
}
