package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.armed.ArmedLevel
import io.github.dreadnought0.feedclean.armed.ArmedStatus
import io.github.dreadnought0.feedclean.armed.HeroOutline
import io.github.dreadnought0.feedclean.armed.Behaviour
import io.github.dreadnought0.feedclean.armed.DetectionKind
import io.github.dreadnought0.feedclean.armed.Requirement
import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.NotInterestedReason
import io.github.dreadnought0.feedclean.ui.common.CardBody
import io.github.dreadnought0.feedclean.ui.common.CardHeader
import io.github.dreadnought0.feedclean.ui.common.CardTone
import io.github.dreadnought0.feedclean.ui.common.GlassCard
import io.github.dreadnought0.feedclean.ui.common.GlassDivider
import io.github.dreadnought0.feedclean.ui.common.GlassNest
import io.github.dreadnought0.feedclean.ui.common.GlassScreen
import io.github.dreadnought0.feedclean.ui.common.GlassSwitch
import io.github.dreadnought0.feedclean.ui.common.LinkRow
import io.github.dreadnought0.feedclean.ui.common.ArmedPill
import io.github.dreadnought0.feedclean.ui.common.OptionRow
import io.github.dreadnought0.feedclean.ui.common.PrimaryButton
import io.github.dreadnought0.feedclean.ui.common.WarnBar
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.settings.NotInterestedRiskDialog
import io.github.dreadnought0.feedclean.ui.settings.SettingsActions
import io.github.dreadnought0.feedclean.ui.settings.SettingsViewModel
import io.github.dreadnought0.feedclean.ui.settings.previewSettingsActions
import io.github.dreadnought0.feedclean.ui.setup.SetupIntents
import io.github.dreadnought0.feedclean.ui.setup.SetupStatus
import io.github.dreadnought0.feedclean.ui.setup.SetupStep
import io.github.dreadnought0.feedclean.ui.setup.SetupStepId
import io.github.dreadnought0.feedclean.ui.setup.heroOutline
import io.github.dreadnought0.feedclean.ui.setup.heroStartsCollapsed
import io.github.dreadnought0.feedclean.ui.setup.launchSystemScreen
import io.github.dreadnought0.feedclean.ui.setup.setupProgress
import io.github.dreadnought0.feedclean.ui.setup.rememberSetupStatus
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass
import io.github.dreadnought0.feedclean.ui.theme.GlassBackground

/** Navigation out of Home. */
class HomeNavigation(
    val openSetup: () -> Unit,
    val openAudit: () -> Unit,
    val openGuide: () -> Unit,
    val openWhere: () -> Unit,
    val openDetectionRules: () -> Unit,
    val openTiming: () -> Unit,
    val openNotInterested: () -> Unit,
    val openBlocking: () -> Unit,
    val openBubbleAndCover: () -> Unit,
)

/** Home tab destination: the control center. */
@Composable
fun HomeRoute(navigation: HomeNavigation, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(container.settingsRepository) }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val riskDialogVisible by settingsViewModel.riskDialogVisible.collectAsStateWithLifecycle()
    val setupStatus = rememberSetupStatus()
    HomeScreen(
        state = state,
        setupStatus = setupStatus,
        actions = settingsViewModel,
        navigation = navigation,
        onResumeSafetyStop = { homeViewModel.resumeAfterSafetyStop() },
        modifier = modifier,
    )
    if (riskDialogVisible) {
        NotInterestedRiskDialog(
            minSeconds = state.settings.minSecondsBetweenActions,
            maxPerHour = state.settings.maxActionsPerHour,
            onAccept = settingsViewModel::acceptNotInterestedRisk,
            onDismiss = settingsViewModel::dismissNotInterestedRisk,
        )
    }
}

/** What the fix sheet is about: one of the seven behaviours or one of the two master switches. */
internal sealed interface SheetTarget {
    data class Of(val behaviour: Behaviour) : SheetTarget
    data object Detection : SheetTarget
    data class Kind(val kind: DetectionKind) : SheetTarget
    data object AutoSkip : SheetTarget
    data object NotInterested : SheetTarget
    data object Helpers : SheetTarget
}

private fun SheetTarget.key(): String = when (this) {
    is SheetTarget.Of -> behaviour.name
    SheetTarget.Detection -> "DETECTION"
    is SheetTarget.Kind -> "KIND_${kind.name}"
    SheetTarget.AutoSkip -> "AUTO_SKIP"
    SheetTarget.NotInterested -> "NOT_INTERESTED"
    SheetTarget.Helpers -> "HELPERS"
}

private fun sheetTargetOf(key: String?): SheetTarget? = when {
    key == null -> null
    key == "DETECTION" -> SheetTarget.Detection
    key == "AUTO_SKIP" -> SheetTarget.AutoSkip
    key == "NOT_INTERESTED" -> SheetTarget.NotInterested
    key == "HELPERS" -> SheetTarget.Helpers
    key.startsWith("KIND_") ->
        DetectionKind.entries.firstOrNull { it.name == key.removePrefix("KIND_") }?.let { SheetTarget.Kind(it) }
    else -> Behaviour.entries.firstOrNull { it.name == key }?.let { SheetTarget.Of(it) }
}

/**
 * Stateless Home control center (v2): setup-steps hero, Pause everything, Detection, Auto-skip (with the skip choices and
 * links nested inside), Not interested, Blocking, on-screen helpers, Setup / Audit. Every Armed / Off / Blocked /
 * Paused pill comes from [HomeUiState.armed] ([io.github.dreadnought0.feedclean.armed.ArmedEvaluator]).
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    setupStatus: SetupStatus?,
    actions: SettingsActions,
    navigation: HomeNavigation,
    onResumeSafetyStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetKey by rememberSaveable { mutableStateOf<String?>(null) }
    val fixHandlers = remember(actions, navigation, onResumeSafetyStop) {
        FixHandlers(
            actions = actions,
            navigate = {
                when (it) {
                    FixNavigation.SETUP -> navigation.openSetup()
                    FixNavigation.DETECTION_RULES -> navigation.openDetectionRules()
                    FixNavigation.WHERE_IT_SKIPS -> navigation.openWhere()
                }
            },
            resumeAfterSafetyStop = onResumeSafetyStop,
        )
    }
    val openSheet: (SheetTarget) -> Unit = { sheetKey = it.key() }
    val s = state.settings
    val armed = state.armed

    GlassScreen(
        title = stringResource(R.string.ui_home_title),
        modifier = modifier,
        actions = { ArmedBadge(armed.actionArmedCount, armed.actionTotal, armed.actionCount(ArmedLevel.BLOCKED) + armed.actionCount(ArmedLevel.PAUSED) > 0, navigation.openGuide) },
    ) {
        SummaryCard(state, setupStatus, navigation)
        PauseCard(engaged = s.killSwitchEngaged, onChange = actions::setKillSwitchEngaged)
        DetectionCard(state, actions, fixHandlers, openSheet)
        AutoSkipCard(state, actions, navigation, fixHandlers, openSheet)
        NotInterestedCard(state, actions, navigation, fixHandlers, openSheet)
        BlockingCard(state, navigation, fixHandlers, openSheet)
        HelpersCard(state, actions, navigation, openSheet)
        ToolsCard(navigation)
    }

    sheetTargetOf(sheetKey)?.let { target ->
        val (name, status) = when (target) {
            is SheetTarget.Of -> stringResource(target.behaviour.label()) to armed.of(target.behaviour)
            SheetTarget.Detection -> stringResource(R.string.behaviour_detection) to armed.detection
            is SheetTarget.Kind -> stringResource(target.kind.label()) to armed.of(target.kind)
            SheetTarget.AutoSkip -> stringResource(R.string.behaviour_auto_skip) to armed.autoSkip
            SheetTarget.NotInterested -> stringResource(R.string.home_ni_title) to armed.notInterestedCard
            SheetTarget.Helpers -> stringResource(R.string.home_helpers_title) to armed.helpersCard
        }
        val manual = when (target) {
            is SheetTarget.Of -> target.behaviour == Behaviour.BLOCK_BUTTON || target.behaviour == Behaviour.NOT_INTERESTED_BUTTON
            SheetTarget.NotInterested -> status === armed.of(Behaviour.NOT_INTERESTED_BUTTON)
            else -> false
        }
        FixSheet(
            name = name,
            status = status,
            settings = s,
            safetyStopAfter = armed.facts.loopGuardPausedAfter,
            manual = manual,
            showSwitchLine = when (target) {
                is SheetTarget.Of -> target.behaviour != Behaviour.BLOCK_BUTTON
                SheetTarget.Helpers -> false
                else -> true
            },
            onFix = { req ->
                sheetKey = null
                req.applyFix(fixHandlers)
            },
            onDismiss = { sheetKey = null },
        )
    }
}

// ── Header badge ────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArmedBadge(armed: Int, total: Int, needsAttention: Boolean, onClick: () -> Unit) {
    val dot = when {
        armed == total -> Glass.Accent
        armed == 0 && !needsAttention -> Glass.Faint
        needsAttention -> Glass.Amber
        else -> Glass.Faint
    }
    val cd = stringResource(R.string.armed_header_badge_cd, armed, total)
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(CircleShape)
            .background(Glass.CardStrong)
            .border(1.dp, Glass.Border, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Text(
            stringResource(R.string.armed_header_badge, armed, total),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = Glass.Ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ── Summary (setup steps) ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(state: HomeUiState, setupStatus: SetupStatus?, navigation: HomeNavigation) {
    val context = LocalContext.current
    val progress = setupProgress(setupStatus ?: SetupStatus.Unknown, state.serviceConnected)
    var userExpanded by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userExpanded ?: !heroStartsCollapsed(progress)
    val canToggle = progress.allComplete
    val toggle = if (canToggle) ({ userExpanded = !expanded }) else null

    GlassCard(
        tone = when (heroOutline(progress)) {
            HeroOutline.SETUP_MISSING -> CardTone.DESTRUCTIVE
            HeroOutline.READY -> CardTone.ACCENT
            HeroOutline.NORMAL -> CardTone.NORMAL
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (expanded) 16.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (toggle != null) Modifier.clickable(onClick = toggle) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Glass.Ink,
                )
                if (expanded) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            progress.completed.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = Glass.Ink,
                        )
                        Text(
                            stringResource(R.string.home_steps_count, progress.total),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp),
                            color = Glass.Muted,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.home_steps_completed, progress.completed, progress.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Glass.Muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (canToggle) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.home_steps_collapse_cd else R.string.home_steps_expand_cd),
                    tint = Glass.Muted,
                    modifier = Modifier.size(22.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }
        if (expanded) {
            progress.steps.forEach { step ->
                SetupStepRow(
                    step = step,
                    serviceEnabled = (setupStatus ?: SetupStatus.Unknown).serviceEnabled,
                    serviceConnected = state.serviceConnected,
                    onFix = { fixSetupStep(context, step.id, navigation.openSetup) },
                )
            }
            GlassDivider(Modifier.padding(top = 10.dp))
            LinkRow(stringResource(R.string.home_steps_open_full), navigation.openSetup)
        }
    }
}

@Composable
private fun SetupStepRow(
    step: SetupStep,
    serviceEnabled: Boolean,
    serviceConnected: Boolean,
    onFix: () -> Unit,
) {
    val title = stringResource(
        when (step.id) {
            SetupStepId.TARGET_APP -> R.string.ui_setup_target_title
            SetupStepId.ACCESSIBILITY -> R.string.ui_setup_service_title
            SetupStepId.BATTERY -> R.string.ui_setup_battery_title
        },
    )
    val subtitle = when {
        step.done -> null
        step.id == SetupStepId.ACCESSIBILITY && serviceEnabled && !serviceConnected ->
            stringResource(R.string.ui_setup_service_not_running)
        step.id == SetupStepId.ACCESSIBILITY -> stringResource(R.string.req_unmet_service_short)
        step.id == SetupStepId.TARGET_APP -> stringResource(R.string.ui_setup_target_missing)
        step.id == SetupStepId.BATTERY -> stringResource(R.string.ui_setup_battery_off)
        else -> null
    }
    val action = if (step.done) null else stringResource(
        when (step.id) {
            SetupStepId.TARGET_APP -> R.string.home_steps_fix_target
            SetupStepId.ACCESSIBILITY -> R.string.ui_setup_service_action
            SetupStepId.BATTERY -> R.string.ui_setup_battery_action
        },
    )
    GlassDivider(Modifier.padding(top = 10.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).background(if (step.done) Glass.Accent else Glass.AmberStrong),
            contentAlignment = Alignment.Center,
        ) {
            if (step.done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            } else {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Glass.Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (step.done) Glass.Muted else Glass.AmberText, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    if (action != null) {
        PrimaryButton(action, onFix, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Opens the same system screen the Setup checklist uses, or the Setup screen if that isn't available. */
private fun fixSetupStep(context: android.content.Context, id: SetupStepId, openSetup: () -> Unit) {
    val opened = when (id) {
        SetupStepId.TARGET_APP -> false
        SetupStepId.ACCESSIBILITY -> context.launchSystemScreen(SetupIntents.accessibilitySettings())
        SetupStepId.BATTERY -> context.launchSystemScreen(
            SetupIntents.requestIgnoreBatteryOptimizations(context.packageName),
            SetupIntents.batteryOptimizationSettings(),
            SetupIntents.appDetails(context.packageName),
        )
    }
    if (!opened) openSetup()
}

// ── Pause everything ────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PauseCard(engaged: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard(tone = if (engaged) CardTone.AMBER else CardTone.NORMAL, onClick = { onChange(!engaged) }) {
        CardHeader(
            title = stringResource(R.string.home_pause_title),
            icon = FeedCleanIcons.Pause,
            amber = engaged,
            trailing = { GlassSwitch(checked = engaged, onCheckedChange = onChange, amber = true) },
        )
        CardBody(stringResource(R.string.home_pause_body), color = if (engaged) Glass.AmberText else Glass.Muted)
    }
}

// ── Detection ───────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetectionCard(state: HomeUiState, actions: SettingsActions, fix: FixHandlers, openSheet: (SheetTarget) -> Unit) {
    val s = state.settings
    val status = state.armed.detection
    GlassCard {
        CardHeader(
            title = stringResource(R.string.home_detection_title),
            icon = FeedCleanIcons.Scan,
            pill = { ClickablePill(status.level) { openSheet(SheetTarget.Detection) } },
            trailing = { GlassSwitch(state.settings.detectionEnabled, actions::setDetectionEnabled) },
        )
        CardBody(stringResource(R.string.home_detection_p1))
        CardBody(stringResource(R.string.home_detection_p2))
        ReasonBar(status, fix, Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(12.dp))
        GlassNest {
            DetectRow(DetectionKind.SHOP, R.string.home_detect_shop_sub, R.string.info_shop_detect, s.detectShopAnchor, actions::setDetectShopAnchor, state, fix, openSheet)
            GlassDivider()
            DetectRow(DetectionKind.PAID_AD, R.string.home_detect_paid_sub, R.string.info_detect_paid, s.detectPaidAd, actions::setDetectPaidAd, state, fix, openSheet)
            GlassDivider()
            DetectRow(DetectionKind.PROMOTED, R.string.home_detect_promoted_sub, R.string.info_detect_promoted, s.detectPromoted, actions::setDetectPromoted, state, fix, openSheet)
            GlassDivider()
            DetectRow(DetectionKind.LIVE_SHOPPING, R.string.home_detect_live_sub, R.string.info_detect_live, s.detectLiveShopping, actions::setDetectLiveShopping, state, fix, openSheet)
        }
    }
}

@Composable
private fun DetectRow(
    kind: DetectionKind,
    description: Int,
    info: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    state: HomeUiState,
    fix: FixHandlers,
    openSheet: (SheetTarget) -> Unit,
) {
    val status = state.armed.of(kind)
    val (desc, color) = rowDescription(status, stringResource(description))
    // Shared with the Detection header; don't repeat it on every kind row.
    val shared = setOf(Requirement.DETECTION_ON)
    Column {
        OptionRow(
            title = stringResource(kind.label()),
            description = desc,
            descriptionColor = color,
            level = status.level,
            info = stringResource(info),
            checked = checked,
            onCheckedChange = onChange,
            onLevelClick = { openSheet(SheetTarget.Kind(kind)) },
        )
        ReasonBar(status, fix, Modifier.padding(bottom = 10.dp), hide = { it in shared })
        val skipStatus = state.armed.of(kind.skip)
        val waiting = !checked && skipStatus.level == ArmedLevel.BLOCKED &&
            skipStatus.checks.any { it.requirement == kind.skipRequirement && !it.met }
        if (waiting) {
            WarnBar(
                text = stringResource(R.string.rules_waiting, stringResource(kind.skip.label())),
                action = stringResource(R.string.req_fix_short_turn_on),
                onAction = { onChange(true) },
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun ClickablePill(level: ArmedLevel, onClick: () -> Unit) {
    ArmedPill(level, Modifier.clip(CircleShape).clickable(onClick = onClick))
}

/**
 * The amber bar with the reason and fix for a Blocked item. Not shown when the reason is the missing service
 * (the summary card already says so, with the setup button) or when [hide] says the reason is shown elsewhere.
 */
@Composable
private fun ReasonBar(status: ArmedStatus, fix: FixHandlers, modifier: Modifier = Modifier, hide: (Requirement) -> Boolean = { false }) {
    val reason = status.reason ?: return
    if (status.level != ArmedLevel.BLOCKED || reason == Requirement.SERVICE_RUNNING || hide(reason)) return
    WarnBar(
        text = stringResource(reason.unmetText()),
        action = reason.shortFixText()?.let { stringResource(it) },
        onAction = reason.shortFixText()?.let { { reason.applyFix(fix) } },
        modifier = modifier,
    )
}

/** Description for a row: the "waiting for accessibility access" line when that's what blocks it. */
@Composable
private fun rowDescription(status: ArmedStatus, normal: String): Pair<String, Color> =
    if (status.level == ArmedLevel.BLOCKED && status.reason == Requirement.SERVICE_RUNNING) {
        stringResource(R.string.req_unmet_service_short) to Glass.AmberText
    } else {
        normal to Glass.Muted
    }

// ── Auto-skip ───────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AutoSkipCard(
    state: HomeUiState,
    actions: SettingsActions,
    navigation: HomeNavigation,
    fix: FixHandlers,
    openSheet: (SheetTarget) -> Unit,
) {
    val s = state.settings
    val master = state.armed.autoSkip
    // Reasons shared by every skip row are shown once (on the master), not on each row.
    val shared = setOf(Requirement.DETECTION_ON, Requirement.AUTO_SKIP_ON, Requirement.PLACE_SELECTED)
    GlassCard {
        CardHeader(
            title = stringResource(R.string.home_autoskip_title),
            icon = FeedCleanIcons.FastForward,
            pill = { ClickablePill(master.level) { openSheet(SheetTarget.AutoSkip) } },
            trailing = { GlassSwitch(s.curateFeedEnabled, actions::setCurateFeedEnabled) },
        )
        CardBody(stringResource(R.string.home_autoskip_p1))
        CardBody(stringResource(R.string.home_autoskip_p2))
        ReasonBar(master, fix, Modifier.padding(top = 10.dp), hide = { it == Requirement.SKIP_TYPE_READY })
        Spacer(Modifier.height(12.dp))
        GlassNest {
            SkipRow(Behaviour.SKIP_SHOP, R.string.home_skip_shop_sub, R.string.info_skip_shop, s.autoSkipOrganicShop, actions::setAutoSkipOrganicShop, state, fix, shared, openSheet)
            GlassDivider()
            SkipRow(Behaviour.SKIP_PAID_AD, R.string.home_skip_paid_sub, R.string.info_skip_paid, s.autoSkipPaidAds, actions::setAutoSkipPaidAds, state, fix, shared, openSheet)
            GlassDivider()
            SkipRow(Behaviour.SKIP_PROMOTED, R.string.home_skip_promoted_sub, R.string.info_skip_promoted, s.autoSkipPromoted, actions::setAutoSkipPromoted, state, fix, shared, openSheet)
            GlassDivider()
            SkipRow(Behaviour.SKIP_LIVE_SHOPPING, R.string.home_skip_live_sub, R.string.info_skip_live, s.autoSkipLiveShopping, actions::setAutoSkipLiveShopping, state, fix, shared, openSheet)
            GlassDivider()
            val where = s.autoSkipPlaceList().map { stringResource(it.shortLabel()) }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            LinkRow(stringResource(R.string.home_link_where), navigation.openWhere, value = where ?: stringResource(R.string.home_link_where_none))
            GlassDivider()
            LinkRow(stringResource(R.string.home_link_rules), navigation.openDetectionRules, value = stringResource(R.string.home_link_rules_value, s.detectionKindCount()))
            GlassDivider()
            LinkRow(stringResource(R.string.home_link_timing), navigation.openTiming, value = stringResource(R.string.home_link_timing_value, s.consecutiveSkipCap))
        }
    }
}

@Composable
private fun SkipRow(
    behaviour: Behaviour,
    description: Int,
    info: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    state: HomeUiState,
    fix: FixHandlers,
    shared: Set<Requirement>,
    openSheet: (SheetTarget) -> Unit,
) {
    val status = state.armed.of(behaviour)
    val (desc, color) = rowDescription(status, stringResource(description))
    Column {
        OptionRow(
            title = stringResource(behaviour.label()),
            description = desc,
            descriptionColor = color,
            level = status.level,
            info = stringResource(info),
            checked = checked,
            onCheckedChange = onChange,
            onLevelClick = { openSheet(SheetTarget.Of(behaviour)) },
        )
        ReasonBar(status, fix, Modifier.padding(bottom = 10.dp), hide = { it in shared })
    }
}

/** Short place label for the link-row value ("For You · Search"). */
internal fun AutoSkipPlace.shortLabel(): Int = when (this) {
    AutoSkipPlace.FOR_YOU -> R.string.where_for_you
    AutoSkipPlace.OTHER_HOME_TABS -> R.string.where_other_home
    AutoSkipPlace.SEARCH -> R.string.where_search
    AutoSkipPlace.OTHER_FEEDS -> R.string.where_other_feeds
}

/** How many of the four detection kinds are on. */
fun FeedCleanSettings.detectionKindCount(): Int =
    listOf(detectPaidAd, detectPromoted, detectShopAnchor, detectLiveShopping).count { it }

// ── Not interested ──────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotInterestedCard(
    state: HomeUiState,
    actions: SettingsActions,
    navigation: HomeNavigation,
    fix: FixHandlers,
    openSheet: (SheetTarget) -> Unit,
) {
    val s = state.settings
    val button = state.armed.of(Behaviour.NOT_INTERESTED_BUTTON)
    val auto = state.armed.of(Behaviour.AUTO_NOT_INTERESTED)
    GlassCard {
        CardHeader(
            stringResource(R.string.home_ni_title),
            icon = FeedCleanIcons.ThumbDown,
            pill = { ClickablePill(state.armed.notInterestedCard.level) { openSheet(SheetTarget.NotInterested) } },
        )
        CardBody(stringResource(R.string.home_ni_p1))
        CardBody(stringResource(R.string.home_ni_p2))
        val (d1, c1) = rowDescription(button, stringResource(R.string.home_ni_button_sub))
        OptionRow(
            title = stringResource(R.string.behaviour_ni_button),
            description = d1,
            descriptionColor = c1,
            level = button.level,
            info = stringResource(R.string.info_ni_button),
            checked = s.notInterestedManualEnabled,
            onCheckedChange = actions::setNotInterestedManualEnabled,
            onLevelClick = { openSheet(SheetTarget.Of(Behaviour.NOT_INTERESTED_BUTTON)) },
        )
        ReasonBar(button, fix, Modifier.padding(bottom = 8.dp))
        GlassDivider()
        val (d2, c2) = rowDescription(auto, stringResource(R.string.home_ni_auto_sub))
        OptionRow(
            title = stringResource(R.string.behaviour_auto_ni),
            description = d2,
            descriptionColor = c2,
            level = auto.level,
            info = stringResource(R.string.info_auto_ni),
            checked = s.notInterestedAutomated,
            onCheckedChange = actions::requestNotInterestedAutomated,
            onLevelClick = { openSheet(SheetTarget.Of(Behaviour.AUTO_NOT_INTERESTED)) },
        )
        ReasonBar(auto, fix, Modifier.padding(bottom = 8.dp))
        GlassDivider()
        LinkRow(stringResource(R.string.home_link_ni), navigation.openNotInterested, value = stringResource(s.notInterestedReason.shortLabel()))
    }
}

internal fun NotInterestedReason.shortLabel(): Int = when (this) {
    NotInterestedReason.FIRST_OFFERED -> R.string.nis_reason_short_first
    NotInterestedReason.PREFER_SHOPPING -> R.string.nis_reason_short_shopping
    NotInterestedReason.SKIP_REASON -> R.string.nis_reason_short_none
}

// ── Blocking ────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlockingCard(state: HomeUiState, navigation: HomeNavigation, fix: FixHandlers, openSheet: (SheetTarget) -> Unit) {
    val s = state.settings
    val block = state.armed.of(Behaviour.BLOCK_BUTTON)
    GlassCard {
        CardHeader(
            stringResource(R.string.home_block_title),
            icon = FeedCleanIcons.Block,
            pill = { ClickablePill(state.armed.blockingCard.level) { openSheet(SheetTarget.Of(Behaviour.BLOCK_BUTTON)) } },
        )
        CardBody(stringResource(R.string.home_block_p1))
        val (d, c) = rowDescription(block, stringResource(R.string.home_block_button_sub))
        OptionRow(
            title = stringResource(R.string.behaviour_block_button),
            description = d,
            descriptionColor = c,
            level = block.level,
            info = stringResource(R.string.info_block_button),
            onClick = navigation.openBlocking,
            onLevelClick = { openSheet(SheetTarget.Of(Behaviour.BLOCK_BUTTON)) },
        )
        ReasonBar(block, fix, Modifier.padding(bottom = 8.dp))
        GlassDivider()
        LinkRow(
            stringResource(R.string.home_link_block),
            navigation.openBlocking,
            value = stringResource(
                R.string.home_link_block_value,
                stringResource(if (s.blockStyle == BlockStyle.ONE_TAP_FULL_FLOW) R.string.home_block_mode_full_short else R.string.home_block_mode_confirm_short),
                s.minSecondsBetweenActions,
            ),
        )
    }
}

// ── Helpers & tools ─────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun HelpersCard(
    state: HomeUiState,
    actions: SettingsActions,
    navigation: HomeNavigation,
    openSheet: (SheetTarget) -> Unit,
) {
    val s = state.settings
    GlassCard {
        CardHeader(
            stringResource(R.string.home_helpers_title),
            icon = FeedCleanIcons.Bubble,
            pill = { ClickablePill(state.armed.helpersCard.level) { openSheet(SheetTarget.Helpers) } },
        )
        CardBody(stringResource(R.string.home_helpers_p1))
        CardBody(stringResource(R.string.home_helpers_p2))
        OptionRow(
            title = stringResource(R.string.home_bubble_title),
            description = stringResource(R.string.home_bubble_sub),
            info = stringResource(R.string.info_bubble),
            checked = s.showBubble,
            onCheckedChange = actions::setShowBubble,
        )
        GlassDivider()
        OptionRow(
            title = stringResource(R.string.home_cover_title),
            description = stringResource(R.string.home_cover_sub),
            info = stringResource(R.string.info_cover),
            checked = s.skipCoverEnabled,
            onCheckedChange = actions::setSkipCoverEnabled,
        )
        GlassDivider()
        LinkRow(stringResource(R.string.home_link_helpers), navigation.openBubbleAndCover)
    }
}

@Composable
private fun ToolsCard(navigation: HomeNavigation) {
    GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        LinkRow(stringResource(R.string.home_audit_row), navigation.openAudit, icon = FeedCleanIcons.Scan, accent = false)
    }
}

/** Every setup step is done and the service is actually running. */
fun SetupStatus.allSet(serviceConnected: Boolean): Boolean =
    setupProgress(this, serviceConnected).allComplete

// ── Previews ────────────────────────────────────────────────────────────────────────────────────────────────

internal val PreviewNavigation = HomeNavigation({}, {}, {}, {}, {}, {}, {}, {}, {})

@Preview(heightDp = 2600, widthDp = 400)
@Composable
private fun HomeScreenMixedPreview() {
    val settings = FeedCleanSettings(curateFeedEnabled = true, autoSkipPaidAds = true, detectPaidAd = false, autoSkipPromoted = true, autoSkipInSearch = true)
    FeedCleanTheme {
        GlassBackground {
            HomeScreen(
                state = HomeUiState.from(settings, serviceConnected = true, tiktokForeground = false),
                setupStatus = SetupStatus(targetAppInstalled = true, serviceEnabled = true, batteryUnrestricted = true),
                actions = previewSettingsActions(settings),
                navigation = PreviewNavigation,
                onResumeSafetyStop = {},
            )
        }
    }
}

@Preview(heightDp = 2600, widthDp = 400)
@Composable
private fun HomeScreenOffPreview() {
    val settings = FeedCleanSettings(curateFeedEnabled = true)
    FeedCleanTheme {
        GlassBackground {
            HomeScreen(
                state = HomeUiState.from(settings, serviceConnected = false, tiktokForeground = false),
                setupStatus = SetupStatus.Unknown,
                actions = previewSettingsActions(settings),
                navigation = PreviewNavigation,
                onResumeSafetyStop = {},
            )
        }
    }
}
