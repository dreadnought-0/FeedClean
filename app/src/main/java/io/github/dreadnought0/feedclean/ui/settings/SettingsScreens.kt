package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.armed.ArmedLevel
import io.github.dreadnought0.feedclean.armed.Behaviour
import io.github.dreadnought0.feedclean.armed.Requirement
import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import io.github.dreadnought0.feedclean.settings.NotInterestedReason
import io.github.dreadnought0.feedclean.ui.common.ArmedPill
import io.github.dreadnought0.feedclean.ui.common.CardHeader
import io.github.dreadnought0.feedclean.ui.common.GlassCard
import io.github.dreadnought0.feedclean.ui.common.GlassDivider
import io.github.dreadnought0.feedclean.ui.common.GlassScreen
import io.github.dreadnought0.feedclean.ui.common.GroupLabel
import io.github.dreadnought0.feedclean.ui.common.LinkRow
import io.github.dreadnought0.feedclean.ui.common.OptionRow
import io.github.dreadnought0.feedclean.ui.common.PillStyle
import io.github.dreadnought0.feedclean.ui.common.RadioOption
import io.github.dreadnought0.feedclean.ui.common.SectionNote
import io.github.dreadnought0.feedclean.ui.common.Segmented
import io.github.dreadnought0.feedclean.ui.common.Stepper
import io.github.dreadnought0.feedclean.ui.common.TagPill
import io.github.dreadnought0.feedclean.ui.common.ValueChip
import io.github.dreadnought0.feedclean.ui.common.WarnBar
import io.github.dreadnought0.feedclean.ui.home.HomeUiState
import io.github.dreadnought0.feedclean.ui.home.HomeViewModel
import io.github.dreadnought0.feedclean.ui.home.autoSkipPlaceList
import io.github.dreadnought0.feedclean.ui.home.detectionKindCount
import io.github.dreadnought0.feedclean.ui.home.label
import io.github.dreadnought0.feedclean.ui.home.shortLabel
import io.github.dreadnought0.feedclean.ui.home.unmetText
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass
import io.github.dreadnought0.feedclean.ui.theme.GlassBackground

/**
 * Hosts a settings sub-screen: the entry-scoped [SettingsViewModel] for edits and a [HomeViewModel] for the
 * honest Armed pills, plus the automated-Not-interested risk dialog.
 */
@Composable
fun SettingsHost(content: @Composable (state: HomeUiState, actions: SettingsActions) -> Unit) {
    val container = LocalContext.current.appContainer
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(container.settingsRepository) }
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val riskDialogVisible by settingsViewModel.riskDialogVisible.collectAsStateWithLifecycle()
    content(state, settingsViewModel)
    if (riskDialogVisible) {
        NotInterestedRiskDialog(
            minSeconds = state.settings.minSecondsBetweenActions,
            maxPerHour = state.settings.maxActionsPerHour,
            onAccept = settingsViewModel::acceptNotInterestedRisk,
            onDismiss = settingsViewModel::dismissNotInterestedRisk,
        )
    }
}

// ── Where it skips ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun WhereItSkipsScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    val master = state.armed.autoSkip
    GlassScreen(stringResource(R.string.where_title), onBack = onBack) {
        val places = s.autoSkipPlaceList().map { stringResource(it.shortLabel()) }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArmedPill(if (places == null && s.curateFeedEnabled) ArmedLevel.BLOCKED else master.level)
                val text = when {
                    places == null -> stringResource(R.string.where_status_none)
                    master.isArmed -> stringResource(R.string.where_status_armed, places.replace(" · ", ", "))
                    master.level == ArmedLevel.OFF -> stringResource(R.string.where_status_other, stringResource(R.string.req_unmet_auto_skip))
                    else -> stringResource(R.string.where_status_other, stringResource(master.reason!!.unmetText()))
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.Ink)
            }
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            OptionRow(stringResource(R.string.where_for_you), description = stringResource(R.string.where_for_you_sub), checked = s.autoSkipInForYou, onCheckedChange = actions::setAutoSkipInForYou)
            GlassDivider()
            OptionRow(stringResource(R.string.where_other_home), description = stringResource(R.string.where_other_home_sub), checked = s.autoSkipInOtherHomeTabs, onCheckedChange = actions::setAutoSkipInOtherHomeTabs)
            GlassDivider()
            OptionRow(stringResource(R.string.where_search), description = stringResource(R.string.where_search_sub), checked = s.autoSkipInSearch, onCheckedChange = actions::setAutoSkipInSearch)
            GlassDivider()
            OptionRow(stringResource(R.string.where_other_feeds), description = stringResource(R.string.where_other_feeds_sub), checked = s.autoSkipInOtherFeeds, onCheckedChange = actions::setAutoSkipInOtherFeeds)
        }
        SectionNote(stringResource(R.string.where_note))
    }
}

// ── Detection rules ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun DetectionRulesScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    GlassScreen(stringResource(R.string.rules_title), onBack = onBack) {
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            OptionRow(
                stringResource(R.string.behaviour_detection),
                description = stringResource(R.string.rules_master_sub),
                level = state.armed.detection.level,
                info = stringResource(R.string.info_detection),
                checked = s.detectionEnabled,
                onCheckedChange = actions::setDetectionEnabled,
            )
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.rules_what), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            KindRow(state, R.string.rules_paid, R.string.rules_paid_sub, null, s.detectPaidAd, actions::setDetectPaidAd, Behaviour.SKIP_PAID_AD, Requirement.DETECT_PAID_AD_ON)
            GlassDivider()
            KindRow(state, R.string.rules_promoted, R.string.rules_promoted_sub, null, s.detectPromoted, actions::setDetectPromoted, Behaviour.SKIP_PROMOTED, Requirement.DETECT_PROMOTED_ON)
            GlassDivider()
            KindRow(state, R.string.rules_shop, R.string.rules_shop_sub, R.string.info_shop_detect, s.detectShopAnchor, actions::setDetectShopAnchor, Behaviour.SKIP_SHOP, Requirement.DETECT_SHOP_ON)
            GlassDivider()
            KindRow(state, R.string.rules_live, R.string.rules_live_sub, null, s.detectLiveShopping, actions::setDetectLiveShopping, Behaviour.SKIP_LIVE_SHOPPING, Requirement.DETECT_LIVE_SHOPPING_ON)
        }
        SectionNote(stringResource(R.string.rules_note))
    }
}

@Composable
private fun KindRow(
    state: HomeUiState,
    title: Int,
    sub: Int,
    info: Int?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    skip: Behaviour,
    requirement: Requirement,
) {
    OptionRow(stringResource(title), description = stringResource(sub), info = info?.let { stringResource(it) }, checked = checked, onCheckedChange = onChange)
    // A skip option that's on but held back by exactly this detection being off.
    val skipStatus = state.armed.of(skip)
    val waiting = skipStatus.level == ArmedLevel.BLOCKED && skipStatus.checks.any { it.requirement == requirement && !it.met }
    if (waiting) {
        WarnBar(
            text = stringResource(R.string.rules_waiting, stringResource(skip.label())),
            action = stringResource(R.string.req_fix_short_turn_on),
            onAction = { onChange(true) },
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

// ── Not interested ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun NotInterestedScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    val button = state.armed.of(Behaviour.NOT_INTERESTED_BUTTON)
    val auto = state.armed.of(Behaviour.AUTO_NOT_INTERESTED)
    GlassScreen(stringResource(R.string.nis_title), onBack = onBack) {
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            OptionRow(
                stringResource(R.string.behaviour_ni_button),
                description = stringResource(R.string.home_ni_button_sub),
                level = button.level,
                checked = s.notInterestedManualEnabled,
                onCheckedChange = actions::setNotInterestedManualEnabled,
            )
            button.reason?.takeIf { button.level == ArmedLevel.BLOCKED }?.let {
                WarnBar(stringResource(it.unmetText()), modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            OptionRow(
                stringResource(R.string.behaviour_auto_ni),
                description = stringResource(R.string.home_ni_auto_sub),
                level = auto.level,
                info = stringResource(R.string.info_auto_ni),
                checked = s.notInterestedAutomated,
                onCheckedChange = actions::requestNotInterestedAutomated,
            )
            NeedsLine(s)
            auto.reason?.takeIf { auto.level != ArmedLevel.ARMED }?.let {
                WarnBar(stringResource(it.unmetText()), paused = auto.level == ArmedLevel.PAUSED, modifier = Modifier.padding(top = 8.dp))
            }
        }
        GlassCard(spacing = 8.dp) {
            Text(stringResource(R.string.nis_reason_title), style = MaterialTheme.typography.titleSmall)
            RadioOption(stringResource(R.string.nis_reason_first), s.notInterestedReason == NotInterestedReason.FIRST_OFFERED, { actions.setNotInterestedReason(NotInterestedReason.FIRST_OFFERED) }, description = stringResource(R.string.nis_reason_first_sub))
            RadioOption(stringResource(R.string.nis_reason_shopping), s.notInterestedReason == NotInterestedReason.PREFER_SHOPPING, { actions.setNotInterestedReason(NotInterestedReason.PREFER_SHOPPING) }, description = stringResource(R.string.nis_reason_shopping_sub))
            RadioOption(stringResource(R.string.nis_reason_none), s.notInterestedReason == NotInterestedReason.SKIP_REASON, { actions.setNotInterestedReason(NotInterestedReason.SKIP_REASON) }, description = stringResource(R.string.nis_reason_none_sub))
        }
        if (s.notInterestedRiskAcknowledged) {
            GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                OptionRow(stringResource(R.string.nis_revoke), description = stringResource(R.string.nis_revoke_sub), onClick = actions::revokeNotInterestedRisk)
            }
        }
    }
}

/** "Needs: Auto-skip on · Skip Shop posts on · your OK on the risk note" with ✓ on the met ones. */
@Composable
private fun NeedsLine(s: FeedCleanSettings) {
    val shape = RoundedCornerShape(12.dp)
    val tick = { met: Boolean -> if (met) " ✓" else "" }
    val text = stringResource(R.string.nis_needs_prefix) + " " + listOf(
        stringResource(R.string.nis_needs_autoskip) + tick(s.curateFeedEnabled),
        stringResource(R.string.nis_needs_skip_shop) + tick(s.autoSkipOrganicShop),
        stringResource(R.string.nis_needs_risk) + tick(s.notInterestedRiskAcknowledged),
    ).joinToString(" · ")
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Glass.Muted,
        modifier = Modifier.fillMaxWidth().clip(shape).background(Glass.Nest).border(1.dp, Glass.Border, shape).padding(10.dp),
    )
}

// ── Blocking ────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun BlockingScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit, onOpenPace: () -> Unit) {
    val s = state.settings
    val block = state.armed.of(Behaviour.BLOCK_BUTTON)
    GlassScreen(stringResource(R.string.blk_title), onBack = onBack) {
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
            OptionRow(stringResource(R.string.behaviour_block_button), description = stringResource(R.string.home_block_button_sub), level = block.level)
            if (block.level == ArmedLevel.BLOCKED) {
                when (block.reason) {
                    Requirement.BUBBLE_SHOWN -> WarnBar(
                        stringResource(R.string.blk_no_bubble),
                        action = stringResource(R.string.req_fix_short_show),
                        onAction = { actions.setShowBubble(true) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    null -> Unit
                    else -> WarnBar(stringResource(block.reason.unmetText()), modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
        GlassCard {
            Text(stringResource(R.string.blk_how_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            val full = s.blockStyle == BlockStyle.ONE_TAP_FULL_FLOW
            StepLine(1, stringResource(R.string.blk_how_1))
            StepLine(2, stringResource(R.string.blk_how_2))
            StepLine(3, stringResource(if (full) R.string.blk_how_3 else R.string.blk_how_3_confirm))
        }
        GlassCard(spacing = 8.dp) {
            Text(stringResource(R.string.blk_mode_title), style = MaterialTheme.typography.titleSmall)
            RadioOption(stringResource(R.string.blk_mode_full), s.blockStyle == BlockStyle.ONE_TAP_FULL_FLOW, { actions.setBlockStyle(BlockStyle.ONE_TAP_FULL_FLOW) }, description = stringResource(R.string.blk_mode_full_sub))
            RadioOption(stringResource(R.string.blk_mode_confirm), s.blockStyle == BlockStyle.STOP_AT_TIKTOK_DIALOG, { actions.setBlockStyle(BlockStyle.STOP_AT_TIKTOK_DIALOG) }, description = stringResource(R.string.blk_mode_confirm_sub))
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            LinkRow(stringResource(R.string.blk_pace), onOpenPace, accent = false, value = stringResource(R.string.blk_pace_value, s.minSecondsBetweenActions, s.maxActionsPerHour))
        }
    }
}

@Composable
private fun StepLine(n: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Glass.CardStrong).border(1.dp, Glass.TrackBorder, CircleShape), contentAlignment = Alignment.Center) {
            Text(n.toString(), style = MaterialTheme.typography.labelSmall, color = Glass.Ink)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = Glass.Ink, modifier = Modifier.weight(1f))
    }
}

// ── Bubble & cover ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun BubbleCoverScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    GlassScreen(stringResource(R.string.look_title), onBack = onBack) {
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            OptionRow(stringResource(R.string.home_bubble_title), description = stringResource(R.string.look_bubble_sub), info = stringResource(R.string.info_bubble), checked = s.showBubble, onCheckedChange = actions::setShowBubble)
            if (!s.showBubble) WarnBar(stringResource(R.string.look_bubble_off_warn), modifier = Modifier.padding(bottom = 10.dp))
        }
        GlassCard {
            CardHeader(stringResource(R.string.look_using), icon = FeedCleanIcons.TouchApp)
            Spacer(Modifier.height(6.dp))
            GestureLine(stringResource(R.string.look_tap), stringResource(R.string.look_tap_body))
            GestureLine(stringResource(R.string.look_hold), stringResource(R.string.look_hold_body))
            GestureLine(stringResource(R.string.look_drag), stringResource(R.string.look_drag_body))
            GlassDivider(Modifier.padding(top = 6.dp))
            val moved = s.bubbleX >= 0 || s.bubbleY >= 0
            OptionRow(
                stringResource(R.string.look_reset),
                description = stringResource(if (moved) R.string.look_reset_moved else R.string.look_reset_default),
                onClick = actions::resetBubblePosition,
                enabled = moved,
            )
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            OptionRow(stringResource(R.string.home_cover_title), description = stringResource(R.string.home_cover_sub), info = stringResource(R.string.info_cover), checked = s.skipCoverEnabled, onCheckedChange = actions::setSkipCoverEnabled)
            GroupLabel(stringResource(R.string.look_cover_style))
            Segmented(
                options = listOf(
                    CoverStyle.BLANK to stringResource(R.string.look_cover_blank),
                    CoverStyle.BLUR to stringResource(R.string.look_cover_blur),
                    CoverStyle.LABELLED to stringResource(R.string.look_cover_labelled),
                ),
                selected = s.coverStyle,
                onSelect = actions::setCoverStyle,
            )
            Text(
                stringResource(
                    when (s.coverStyle) {
                        CoverStyle.BLANK -> R.string.look_cover_blank_sub
                        CoverStyle.BLUR -> R.string.look_cover_blur_sub
                        CoverStyle.LABELLED -> R.string.look_cover_labelled_sub
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun GestureLine(gesture: String, body: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(48.dp)) { TagPill(gesture, style = PillStyle.NEUTRAL) }
        Text(body, style = MaterialTheme.typography.bodySmall, color = Glass.Ink, modifier = Modifier.weight(1f))
    }
}

// ── Timing & safety ─────────────────────────────────────────────────────────────────────────────────────────

/** Slider granularity for the skip settle delay. */
private const val SETTLE_DELAY_STEP_MS = 50

@Composable
fun TimingScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    GlassScreen(stringResource(R.string.set_timing), onBack = onBack) {
        GlassCard {
            GlassIntSlider(
                value = s.skipSettleDelayMs,
                valueRange = 0..FeedCleanSettings.MAX_SETTLE_DELAY_MS,
                step = SETTLE_DELAY_STEP_MS,
                onValueCommitted = actions::setSkipSettleDelayMs,
                label = { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.tim_delay), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        ValueChip(stringResource(R.string.tim_delay_value, v))
                    }
                },
            )
            Text(stringResource(R.string.tim_delay_sub), style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tim_cap), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.tim_cap_sub), style = MaterialTheme.typography.bodySmall, color = Glass.Muted, modifier = Modifier.padding(top = 2.dp))
                }
                Stepper(
                    value = s.consecutiveSkipCap.toString(),
                    onDecrease = { actions.setConsecutiveSkipCap(s.consecutiveSkipCap - 1) },
                    onIncrease = { actions.setConsecutiveSkipCap(s.consecutiveSkipCap + 1) },
                    canDecrease = s.consecutiveSkipCap > 1,
                    canIncrease = s.consecutiveSkipCap < FeedCleanSettings.MAX_CONSECUTIVE_SKIP_CAP,
                )
            }
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            OptionRow(stringResource(R.string.tim_screen), description = stringResource(R.string.tim_screen_sub), checked = s.autoSkipOnlyWhenScreenOn, onCheckedChange = actions::setAutoSkipOnlyWhenScreenOn)
        }
    }
}

// ── Pace limits ─────────────────────────────────────────────────────────────────────────────────────────────

/** Upper bound of the stepper only (the repository has no cap here); larger imported values still display. */
private const val MIN_SECONDS_UI_MAX = 600

@Composable
fun PaceScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    GlassScreen(stringResource(R.string.set_pace), onBack = onBack) {
        GlassCard {
            Text(stringResource(R.string.pace_intro), style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
            Row(Modifier.padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pace_wait), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.pace_wait_sub), style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
                }
                Stepper(
                    value = stringResource(R.string.pace_wait_value, s.minSecondsBetweenActions),
                    onDecrease = { actions.setMinSecondsBetweenActions(s.minSecondsBetweenActions - 1) },
                    onIncrease = { actions.setMinSecondsBetweenActions(s.minSecondsBetweenActions + 1) },
                    canDecrease = s.minSecondsBetweenActions > FeedCleanSettings.MIN_SECONDS_FLOOR,
                    canIncrease = s.minSecondsBetweenActions < MIN_SECONDS_UI_MAX,
                )
            }
            GlassDivider()
            Spacer(Modifier.height(10.dp))
            GlassIntSlider(
                value = s.maxActionsPerHour,
                valueRange = 1..FeedCleanSettings.MAX_PER_HOUR_CAP,
                step = 1,
                onValueCommitted = actions::setMaxActionsPerHour,
                label = { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pace_max), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        ValueChip(v.toString())
                    }
                },
            )
            Text(stringResource(R.string.pace_max_sub), style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
        }
    }
}

// ── Data & privacy ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
fun DataPrivacyScreen(state: HomeUiState, actions: SettingsActions, onBack: () -> Unit) {
    val s = state.settings
    GlassScreen(stringResource(R.string.set_data), onBack = onBack) {
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            OptionRow(stringResource(R.string.dp_session), description = stringResource(R.string.dp_session_sub), info = stringResource(R.string.info_session), checked = s.sessionTrackingEnabled, onCheckedChange = actions::setSessionTrackingEnabled)
            GlassDivider()
            OptionRow(stringResource(R.string.dp_log), description = stringResource(R.string.dp_log_sub), info = stringResource(R.string.info_log), checked = s.decisionLogEnabled, onCheckedChange = actions::setDecisionLogEnabled)
            GlassDivider()
            // saveScreenshots is stored but nothing reads it yet: no switch, honestly labelled.
            OptionRow(
                stringResource(R.string.dp_shots),
                description = stringResource(R.string.dp_shots_sub),
                trailing = { TagPill(stringResource(R.string.coming_soon)) },
            )
        }
        SectionNote(stringResource(R.string.dp_note))
    }
}

// ── Settings root ───────────────────────────────────────────────────────────────────────────────────────────

/** Navigation out of the Settings root. */
class SettingsNavigation(
    val openDetectionRules: () -> Unit,
    val openTiming: () -> Unit,
    val openPace: () -> Unit,
    val openData: () -> Unit,
    val openExport: () -> Unit,
    val openAdvanced: () -> Unit,
)

@Composable
fun SettingsRootScreen(settings: FeedCleanSettings, navigation: SettingsNavigation, modifier: Modifier = Modifier) {
    val rows = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    GlassScreen(stringResource(R.string.ui_settings_title), modifier = modifier) {
        GlassCard(contentPadding = rows) {
            LinkRow(stringResource(R.string.rules_title), navigation.openDetectionRules, subtitle = stringResource(R.string.set_rules_sub, settings.detectionKindCount()), icon = FeedCleanIcons.Scan, accent = false)
            GlassDivider()
            LinkRow(stringResource(R.string.set_timing), navigation.openTiming, subtitle = stringResource(R.string.set_timing_sub), icon = FeedCleanIcons.Timer, accent = false)
            GlassDivider()
            LinkRow(stringResource(R.string.set_pace), navigation.openPace, subtitle = stringResource(R.string.set_pace_sub, settings.minSecondsBetweenActions, settings.maxActionsPerHour), icon = FeedCleanIcons.Speed, accent = false)
        }
        GlassCard(contentPadding = rows) {
            LinkRow(stringResource(R.string.set_data), navigation.openData, subtitle = stringResource(R.string.set_data_sub), icon = Icons.Filled.Lock, accent = false)
            GlassDivider()
            LinkRow(stringResource(R.string.set_export), navigation.openExport, subtitle = stringResource(R.string.set_export_sub), icon = FeedCleanIcons.Archive, accent = false)
        }
        GlassCard(contentPadding = rows) {
            LinkRow(stringResource(R.string.set_advanced), navigation.openAdvanced, subtitle = stringResource(R.string.set_advanced_sub), icon = FeedCleanIcons.Tune, accent = false)
        }
        SectionNote(stringResource(R.string.set_home_hint))
    }
}

// ── Advanced ────────────────────────────────────────────────────────────────────────────────────────────────

/** Navigation out of Advanced. */
class AdvancedNavigation(
    val openDebug: () -> Unit,
    val openSetup: () -> Unit,
    val openAudit: () -> Unit,
    val openSuggested: () -> Unit,
)

@Composable
fun AdvancedScreen(
    settings: FeedCleanSettings,
    actions: SettingsActions,
    setupAllSet: Boolean?,
    navigation: AdvancedNavigation,
    onBack: () -> Unit,
) {
    GlassScreen(stringResource(R.string.set_advanced), onBack = onBack) {
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.adv_rules_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 2.dp))
            SelectorOverrideRows(
                overrideUri = settings.selectorOverrideUri,
                onOverrideSelected = actions::setSelectorOverrideUri,
                onUseBuiltIn = actions::clearSelectorOverride,
            )
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            // ocrFallbackEnabled is stored but nothing reads it yet: no switch, honestly labelled.
            OptionRow(
                stringResource(R.string.rules_ocr),
                description = stringResource(R.string.rules_ocr_sub),
                trailing = { TagPill(stringResource(R.string.coming_soon)) },
            )
        }
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            LinkRow(stringResource(R.string.adv_debug), navigation.openDebug, subtitle = stringResource(R.string.adv_debug_sub), icon = FeedCleanIcons.Code, accent = false)
            GlassDivider()
            LinkRow(
                stringResource(R.string.ui_setup_title),
                navigation.openSetup,
                subtitle = setupAllSet?.let { stringResource(if (it) R.string.adv_setup_sub_ok else R.string.adv_setup_sub_todo) },
                icon = FeedCleanIcons.Checklist,
                accent = false,
            )
            GlassDivider()
            LinkRow(stringResource(R.string.home_audit_row), navigation.openAudit, subtitle = stringResource(R.string.adv_audit_sub), icon = FeedCleanIcons.Scan, accent = false)
            GlassDivider()
            LinkRow(stringResource(R.string.adv_suggested), navigation.openSuggested, subtitle = stringResource(R.string.adv_suggested_sub), icon = Icons.AutoMirrored.Filled.List, accent = false)
        }
    }
}

/** Preview-only actions backed by an in-memory repository (not a composable, so lint is happy). */
internal fun previewSettingsActions(settings: FeedCleanSettings): SettingsActions =
    SettingsViewModel(InMemorySettingsRepository(settings))

// ── Previews ────────────────────────────────────────────────────────────────────────────────────────────────

@Preview(heightDp = 900)
@Composable
private fun DetectionRulesPreview() {
    val s = FeedCleanSettings(curateFeedEnabled = true, autoSkipPaidAds = true, detectPaidAd = false)
    FeedCleanTheme {
        GlassBackground {
            DetectionRulesScreen(HomeUiState.from(s, true, true), previewSettingsActions(s), onBack = {})
        }
    }
}

@Preview(heightDp = 900)
@Composable
private fun SettingsRootPreview() {
    FeedCleanTheme {
        GlassBackground {
            SettingsRootScreen(FeedCleanSettings(), SettingsNavigation({}, {}, {}, {}, {}, {}))
        }
    }
}

