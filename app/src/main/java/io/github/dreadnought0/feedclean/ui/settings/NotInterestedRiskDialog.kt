package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Account-risk note shown before automated "Not interested" is switched on.
 * [onAccept] turns it on and records the acknowledgement; [onDismiss] leaves it off.
 *
 * @param minSeconds / [maxPerHour] the current shared rate limit, so the note states the real numbers.
 */
@Composable
fun NotInterestedRiskDialog(
    minSeconds: Int,
    maxPerHour: Int,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.ui_risk_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.ui_risk_dialog_body_seen))
                Text(stringResource(R.string.ui_risk_dialog_body_rate_limit, minSeconds, maxPerHour))
                Text(stringResource(R.string.ui_risk_dialog_body_off))
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.ui_risk_dialog_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_risk_dialog_decline)) }
        },
    )
}

@Preview
@Composable
private fun NotInterestedRiskDialogPreview() {
    FeedCleanTheme(dynamicColor = false) {
        NotInterestedRiskDialog(minSeconds = 5, maxPerHour = 60, onAccept = {}, onDismiss = {})
    }
}
