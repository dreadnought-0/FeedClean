package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.common.StatusPill
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Home header: live service status pills, plus a warning card linking to Setup when the accessibility
 * service is off or not running.
 *
 * @param serviceEnabled system setting check; null before the first check (then only [serviceConnected] counts).
 * @param serviceConnected live `ScreenStateHolder.serviceConnected`.
 * @param tiktokForeground live `ScreenStateHolder.tiktokForeground`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceStatusCard(
    serviceEnabled: Boolean?,
    serviceConnected: Boolean,
    tiktokForeground: Boolean,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(
                text = stringResource(
                    if (serviceConnected) R.string.ui_status_service_connected else R.string.ui_status_service_disconnected,
                ),
                ok = serviceConnected,
            )
            StatusPill(
                text = stringResource(
                    if (tiktokForeground) R.string.ui_status_target_foreground else R.string.ui_status_target_background,
                ),
                ok = tiktokForeground,
            )
        }
        if (serviceEnabled == false || !serviceConnected) {
            ServiceWarningCard(serviceEnabled = serviceEnabled != false, onOpenSetup = onOpenSetup)
        }
    }
}

@Composable
private fun ServiceWarningCard(serviceEnabled: Boolean, onOpenSetup: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(
                    text = stringResource(
                        if (serviceEnabled) R.string.ui_home_service_not_running else R.string.ui_home_service_off,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(stringResource(R.string.ui_home_service_warning_body), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onOpenSetup,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.ui_home_open_setup)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServiceStatusCardPreview() {
    FeedCleanTheme(dynamicColor = false) {
        ServiceStatusCard(
            serviceEnabled = false,
            serviceConnected = false,
            tiktokForeground = false,
            onOpenSetup = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
