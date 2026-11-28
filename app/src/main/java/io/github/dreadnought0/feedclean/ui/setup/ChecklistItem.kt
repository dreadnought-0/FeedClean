package io.github.dreadnought0.feedclean.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.dreadnought0.feedclean.ui.common.GlassButton
import io.github.dreadnought0.feedclean.ui.common.PillStyle
import io.github.dreadnought0.feedclean.ui.common.TagPill
import io.github.dreadnought0.feedclean.ui.theme.Glass
import androidx.compose.foundation.layout.PaddingValues
import io.github.dreadnought0.feedclean.ui.common.CardTone
import io.github.dreadnought0.feedclean.ui.common.GlassCard
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
    tone: CardTone = CardTone.NORMAL,
) {
    val stateLabel = stringResource(
        when (state) {
            CheckState.OK -> R.string.ui_setup_state_ok
            CheckState.ACTION_NEEDED -> R.string.ui_setup_state_action_needed
            CheckState.INFO -> R.string.ui_setup_state_info
        },
    )
    GlassCard(modifier = modifier.fillMaxWidth(), tone = tone, contentPadding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val mark = Modifier.size(26.dp).clip(CircleShape)
            when (state) {
                CheckState.OK -> Box(mark.background(FeedCleanTheme.extraColors.statusOk), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = stateLabel, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                CheckState.ACTION_NEEDED -> Box(mark.border(1.5.dp, Glass.Faint, CircleShape).semantics { contentDescription = stateLabel })
                CheckState.INFO -> Box(mark.border(1.dp, Glass.Faint, CircleShape).semantics { contentDescription = stateLabel })
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                TagPill(
                    stateLabel,
                    style = when (state) {
                        CheckState.OK -> PillStyle.ACCENT
                        CheckState.ACTION_NEEDED -> PillStyle.AMBER
                        CheckState.INFO -> PillStyle.NEUTRAL
                    },
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                details.forEach { line ->
                    Text(line, style = MaterialTheme.typography.labelMedium, color = Glass.Muted)
                }
                if (actionLabel != null && onAction != null) {
                    GlassButton(actionLabel, onAction, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChecklistItemPreview() {
    FeedCleanTheme {
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
