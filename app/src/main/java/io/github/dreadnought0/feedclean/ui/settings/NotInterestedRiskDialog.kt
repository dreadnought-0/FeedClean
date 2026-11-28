package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.common.GlassDialog
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass

/**
 * Account-risk note shown before automated "Not interested" is switched on.
 * [onAccept] turns it on and records the acknowledgement; [onDismiss] leaves it off.
 *
 * @param minSeconds / [maxPerHour] the current shared pace limit, so the note states the real numbers.
 */
@Composable
fun NotInterestedRiskDialog(
    minSeconds: Int,
    maxPerHour: Int,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        title = stringResource(R.string.ui_risk_dialog_title),
        confirm = stringResource(R.string.ui_risk_dialog_accept),
        onConfirm = onAccept,
        dismiss = stringResource(R.string.ui_risk_dialog_decline),
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.ui_risk_dialog_body_seen), style = MaterialTheme.typography.bodyMedium, color = Glass.Muted)
        Text(stringResource(R.string.ui_risk_dialog_body_rate_limit, minSeconds, maxPerHour), style = MaterialTheme.typography.bodyMedium, color = Glass.Muted)
        Text(stringResource(R.string.ui_risk_dialog_body_off), style = MaterialTheme.typography.bodyMedium, color = Glass.Muted)
    }
}

@Preview
@Composable
private fun NotInterestedRiskDialogPreview() {
    FeedCleanTheme {
        NotInterestedRiskDialog(minSeconds = 5, maxPerHour = 60, onAccept = {}, onDismiss = {})
    }
}
