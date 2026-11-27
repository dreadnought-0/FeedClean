package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.settings.DependentSettings
import io.github.dreadnought0.feedclean.ui.settings.SettingsFootnote
import io.github.dreadnought0.feedclean.ui.settings.SwitchSettingRow
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * The toggles flipped most often: "Curate feed" (master), "Auto-skip organic Shop posts"
 * and "Skip-cover". The two sub-toggles are dimmed (still editable) while the master is off.
 */
@Composable
fun QuickTogglesCard(
    state: HomeUiState,
    onCurateFeedChange: (Boolean) -> Unit,
    onAutoSkipOrganicShopChange: (Boolean) -> Unit,
    onSkipCoverChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.ui_home_quick_toggles),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            SwitchSettingRow(
                title = stringResource(R.string.ui_curate_feed_title),
                summary = stringResource(R.string.ui_curate_feed_summary),
                checked = state.curateFeedEnabled,
                onCheckedChange = onCurateFeedChange,
            )
            val forYou = stringResource(R.string.ui_home_where_for_you)
            val otherHome = stringResource(R.string.ui_home_where_other_home)
            val search = stringResource(R.string.ui_home_where_search)
            val otherFeeds = stringResource(R.string.ui_home_where_other_feeds)
            val where = autoSkipSummary(state.autoSkipPlaces) {
                when (it) {
                    AutoSkipPlace.FOR_YOU -> forYou
                    AutoSkipPlace.OTHER_HOME_TABS -> otherHome
                    AutoSkipPlace.SEARCH -> search
                    AutoSkipPlace.OTHER_FEEDS -> otherFeeds
                }
            }
            SettingsFootnote(
                if (where != null) {
                    stringResource(R.string.ui_home_where_summary, where)
                } else {
                    stringResource(R.string.ui_home_where_none)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            DependentSettings(
                active = state.curateFeedEnabled,
                inactiveNote = stringResource(R.string.ui_settings_curate_off_note),
            ) {
                SwitchSettingRow(
                    title = stringResource(R.string.ui_home_skip_organic_shop),
                    summary = stringResource(R.string.ui_settings_skip_organic_shop_summary),
                    checked = state.autoSkipOrganicShop,
                    onCheckedChange = onAutoSkipOrganicShopChange,
                )
                SwitchSettingRow(
                    title = stringResource(R.string.ui_skip_cover_title),
                    summary = stringResource(R.string.ui_skip_cover_summary),
                    checked = state.skipCoverEnabled,
                    onCheckedChange = onSkipCoverChange,
                )
            }
            when {
                state.curateFeedEnabled && state.killSwitchEngaged -> SettingsFootnote(
                    stringResource(R.string.ui_settings_paused_by_kill_switch),
                    color = MaterialTheme.colorScheme.error,
                )

                state.autoSkipArmed && state.autoSkipOrganicShop -> SettingsFootnote(
                    stringResource(R.string.ui_home_armed),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickTogglesCardPreview() {
    FeedCleanTheme(dynamicColor = false) {
        QuickTogglesCard(
            state = HomeUiState(curateFeedEnabled = true),
            onCurateFeedChange = {},
            onAutoSkipOrganicShopChange = {},
            onSkipCoverChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
