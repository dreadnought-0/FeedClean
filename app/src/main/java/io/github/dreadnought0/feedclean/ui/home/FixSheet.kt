package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.armed.ArmedLevel
import io.github.dreadnought0.feedclean.armed.ArmedStatus
import io.github.dreadnought0.feedclean.armed.Requirement
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.ui.common.CheckLine
import io.github.dreadnought0.feedclean.ui.common.GlassButton
import io.github.dreadnought0.feedclean.ui.common.PrimaryButton
import io.github.dreadnought0.feedclean.ui.theme.Glass

/**
 * "Why isn't X armed?": every condition the behaviour needs (from [ArmedStatus.checks]), ✓ / ✗ / ‖, and one
 * button that fixes the first missing one.
 *
 * @param manual Block / Not interested button: different live-conditions footnote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FixSheet(
    name: String,
    status: ArmedStatus,
    settings: FeedCleanSettings,
    safetyStopAfter: Int,
    manual: Boolean,
    showSwitchLine: Boolean,
    onFix: (Requirement) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Glass.Sheet,
        contentColor = Glass.Ink,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            val (title, sub) = when (status.level) {
                ArmedLevel.BLOCKED -> R.string.fix_sheet_title_blocked to R.string.fix_sheet_sub_blocked
                ArmedLevel.PAUSED -> R.string.fix_sheet_title_paused to R.string.fix_sheet_sub_paused
                else -> R.string.fix_sheet_title_armed to R.string.fix_sheet_sub_armed
            }
            if (status.level == ArmedLevel.OFF) {
                Text(stringResource(R.string.guide_off), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                GlassButton(stringResource(R.string.fix_sheet_done), onDismiss)
                return@Column
            }
            Text(stringResource(title, name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(sub), style = MaterialTheme.typography.bodyMedium, color = Glass.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            if (showSwitchLine) CheckLine(stringResource(R.string.req_met_switch, name), met = true)
            status.checks.forEach { check ->
                val r = check.requirement
                val text = when {
                    check.met && r == Requirement.PLACE_SELECTED -> stringResource(r.metText(), placesText(settings))
                    check.met -> stringResource(r.metText())
                    r == Requirement.NOT_PAUSED_BY_SAFETY_STOP && safetyStopAfter > 0 ->
                        stringResource(R.string.req_unmet_safety_stop_n, safetyStopAfter)
                    else -> stringResource(r.unmetText())
                }
                CheckLine(
                    text = text,
                    met = if (check.met) true else if (r.pause) null else false,
                    emphasize = !check.met && r == status.reason,
                )
            }
            Spacer(Modifier.height(16.dp))
            val reason = status.reason
            if (reason != null && reason != Requirement.SKIP_TYPE_READY) {
                PrimaryButton(stringResource(reason.fixText()), onClick = { onFix(reason) })
            } else {
                GlassButton(stringResource(R.string.fix_sheet_done), onDismiss)
            }
            Text(
                stringResource(if (manual) R.string.fix_sheet_live_manual else R.string.fix_sheet_live_skip),
                style = MaterialTheme.typography.bodySmall,
                color = Glass.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun placesText(settings: FeedCleanSettings): String {
    val labels = settings.autoSkipPlaceList().map { stringResource(it.shortLabel()) }
    return when (labels.size) {
        0 -> ""
        1 -> labels[0]
        else -> labels.dropLast(1).joinToString(", ") + " " + stringResource(R.string.and_word) + " " + labels.last()
    }
}
