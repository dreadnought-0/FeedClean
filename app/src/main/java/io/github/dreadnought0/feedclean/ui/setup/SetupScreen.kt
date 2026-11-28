package io.github.dreadnought0.feedclean.ui.setup

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass

/**
 * Setup checklist destination: live checks (refreshed on every resume) plus deep links into system settings.
 */
@Composable
fun SetupRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status = rememberSetupStatus()
    val serviceConnected by ScreenStateHolder.serviceConnected.collectAsStateWithLifecycle()
    SetupScreen(
        status = status ?: SetupStatus.Unknown,
        serviceConnected = serviceConnected,
        onBack = onBack,
        onOpenAccessibilitySettings = {
            context.launchSystemScreen(SetupIntents.accessibilitySettings())
        },
        onRequestBatteryUnrestricted = {
            context.launchSystemScreen(
                SetupIntents.requestIgnoreBatteryOptimizations(context.packageName),
                SetupIntents.batteryOptimizationSettings(),
                SetupIntents.appDetails(context.packageName),
            )
        },
        onOpenAppInfo = { context.launchSystemScreen(SetupIntents.appDetails(context.packageName)) },
        modifier = modifier,
    )
}

/**
 * Stateless Setup checklist.
 *
 * @param status latest system checks.
 * @param serviceConnected live `ScreenStateHolder.serviceConnected`.
 */
@Composable
fun SetupScreen(
    status: SetupStatus,
    serviceConnected: Boolean,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestBatteryUnrestricted: () -> Unit,
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = setupProgress(status, serviceConnected)
    val serviceOk = progress.done(SetupStepId.ACCESSIBILITY)
    val yes = stringResource(R.string.ui_yes)
    val no = stringResource(R.string.ui_no)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = stringResource(R.string.ui_setup_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
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
            Text(
                text = stringResource(R.string.ui_setup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ChecklistItem(
                state = if (progress.done(SetupStepId.TARGET_APP)) CheckState.OK else CheckState.ACTION_NEEDED,
                title = stringResource(R.string.ui_setup_target_title),
                body = stringResource(
                    if (progress.done(SetupStepId.TARGET_APP)) R.string.ui_setup_target_ok else R.string.ui_setup_target_missing,
                ),
                tone = setupStepTone(progress, SetupStepId.TARGET_APP),
            )

            ChecklistItem(
                state = if (serviceOk) CheckState.OK else CheckState.ACTION_NEEDED,
                title = stringResource(R.string.ui_setup_service_title),
                body = stringResource(
                    when {
                        serviceOk -> R.string.ui_setup_service_ok
                        status.serviceEnabled -> R.string.ui_setup_service_not_running
                        else -> R.string.ui_setup_service_off
                    },
                ),
                details = listOf(
                    stringResource(R.string.ui_setup_service_enabled_line, if (status.serviceEnabled) yes else no),
                    stringResource(R.string.ui_setup_service_running_line, if (serviceConnected) yes else no),
                ),
                actionLabel = stringResource(R.string.ui_setup_service_action),
                onAction = onOpenAccessibilitySettings,
                tone = setupStepTone(progress, SetupStepId.ACCESSIBILITY),
            )

            ChecklistItem(
                state = if (progress.done(SetupStepId.BATTERY)) CheckState.OK else CheckState.ACTION_NEEDED,
                title = stringResource(R.string.ui_setup_battery_title),
                body = stringResource(
                    if (progress.done(SetupStepId.BATTERY)) R.string.ui_setup_battery_ok else R.string.ui_setup_battery_off,
                ),
                actionLabel = if (progress.done(SetupStepId.BATTERY)) null else stringResource(R.string.ui_setup_battery_action),
                onAction = onRequestBatteryUnrestricted,
                tone = setupStepTone(progress, SetupStepId.BATTERY),
            )

            ChecklistItem(
                state = CheckState.INFO,
                title = stringResource(R.string.ui_setup_restricted_title),
                body = stringResource(R.string.ui_setup_restricted_body),
                actionLabel = stringResource(R.string.ui_setup_restricted_action),
                onAction = onOpenAppInfo,
            )

            ChecklistItem(
                state = CheckState.INFO,
                title = stringResource(R.string.ui_setup_advanced_protection_title),
                body = stringResource(R.string.ui_setup_advanced_protection_body),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun SetupScreenPreview() {
    FeedCleanTheme {
        SetupScreen(
            status = SetupStatus(targetAppInstalled = true, serviceEnabled = true, batteryUnrestricted = false),
            serviceConnected = false,
            onBack = {},
            onOpenAccessibilitySettings = {},
            onRequestBatteryUnrestricted = {},
            onOpenAppInfo = {},
        )
    }
}
