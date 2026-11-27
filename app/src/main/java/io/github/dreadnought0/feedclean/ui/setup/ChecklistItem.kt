package io.github.dreadnought0.feedclean.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
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
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/** State of one checklist item. */
enum class CheckState {
    /** Check passes (✓). */
    OK,

    /** Check fails; the user needs to do something (✗). */
    ACTION_NEEDED,

    /** Guidance only; the app cannot check this. */
    INFO,
}

/**
 * One Setup checklist card: a ✓ / ✗ / info icon, title, explanation, optional detail lines, and an optional
 * button that deep-links to the relevant system screen.
 */
@Composable
fun ChecklistItem(
    state: CheckState,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    details: List<String> = emptyList(),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (icon, tint, stateDescription) = when (state) {
        CheckState.OK -> Triple(
            Icons.Filled.CheckCircle,
            FeedCleanTheme.extraColors.statusOk,
            stringResource(R.string.ui_setup_state_ok),
        )

        CheckState.ACTION_NEEDED -> Triple(
            Icons.Filled.Close,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.ui_setup_state_action_needed),
        )

        CheckState.INFO -> Triple(
            Icons.Filled.Info,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.ui_setup_state_info),
        )
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = stateDescription, tint = tint, modifier = Modifier.size(28.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                details.forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelLarge)
                }
                if (actionLabel != null && onAction != null) {
                    FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChecklistItemPreview() {
    FeedCleanTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            ChecklistItem(CheckState.OK, "Battery: Unrestricted", "The system won't stop FeedClean.")
            ChecklistItem(
                CheckState.ACTION_NEEDED,
                "Accessibility service",
                "Switch FeedClean on.",
                details = listOf("Enabled: no", "Running: no"),
                actionLabel = "Open accessibility settings",
                onAction = {},
            )
        }
    }
}
