package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.ui.theme.Glass
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.service.BlockedMatch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What the card's buttons call. Buttons that act on TikTok receive a [UserIntentToken] created inside the
 * button's own click handler (every account action starts from a user tap); nothing else in the app can
 * produce one for them.
 */
interface CardCallbacks {
    fun onBlock(token: UserIntentToken)
    fun onNotInterested(token: UserIntentToken)
    fun onSkip(token: UserIntentToken)
    fun onLogOnly()
    fun onFalsePositive()
    fun onToggleTag(tag: String, enabled: Boolean)
    fun onResumeAutoSkip()
    fun onCapture()
    fun onStop()
    fun onDismiss()
}

/** The compact overlay card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardContent(model: CardModel, callbacks: CardCallbacks, maxHeight: Dp, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        // Opaque frosted surface: the card sits over a moving video, so it must stay readable.
        colors = CardDefaults.cardColors(containerColor = Glass.Sheet.copy(alpha = 0.97f), contentColor = Glass.Ink),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(model, callbacks)
            val target = model.target
            if (target == null) {
                Text(stringResource(R.string.overlay_card_no_post), style = MaterialTheme.typography.bodyMedium)
            } else {
                DetectedSignals(model)
                BlockedWarning(model.blockedMatch)
            }
            Progress(model, callbacks)
            if (target != null) ActionButtons(model, callbacks)
            if (model.loopGuardPaused) {
                FilledTonalButton(onClick = callbacks::onResumeAutoSkip) {
                    Text(stringResource(R.string.overlay_card_resume))
                }
            }
            if (target != null) ManualTags(model, callbacks)
            TextButton(onClick = callbacks::onCapture, enabled = !model.busy) {
                Text(stringResource(R.string.overlay_card_capture))
            }
        }
    }
}

@Composable
private fun Header(model: CardModel, callbacks: CardCallbacks) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName ?: stringResource(R.string.overlay_card_unknown_creator),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.target != null) {
                Text(
                    text = model.knownHandle?.let { stringResource(R.string.overlay_card_handle, it) }
                        ?: stringResource(R.string.overlay_card_handle_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = callbacks::onDismiss) { Text(stringResource(R.string.overlay_card_close)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedSignals(model: CardModel) {
    if (model.kinds.isEmpty()) {
        Text(
            stringResource(R.string.overlay_card_nothing_detected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            model.kinds.forEach { kind ->
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(OverlayTexts.kind(kind))) })
            }
        }
    }
    val since = model.stats.firstSeenAtMillis
    Text(
        text = if (model.stats.seenCount > 0 && since != null) {
            stringResource(R.string.overlay_card_seen, model.stats.seenCount, formatDate(since))
        } else {
            stringResource(R.string.overlay_card_seen_first)
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun BlockedWarning(match: BlockedMatch) {
    val text = when (match) {
        BlockedMatch.STRONG -> stringResource(R.string.overlay_card_blocked_strong)
        BlockedMatch.POSSIBLE -> stringResource(R.string.overlay_card_blocked_possible)
        BlockedMatch.NONE -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (match == BlockedMatch.STRONG) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun Progress(model: CardModel, callbacks: CardCallbacks) {
    val context = LocalContext.current
    when (val p = model.progress) {
        CardProgress.Idle -> Unit
        is CardProgress.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(OverlayTexts.step(p.kind, p.step)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = callbacks::onStop) { Text(stringResource(R.string.overlay_card_stop)) }
        }
        is CardProgress.Finished -> Text(
            text = OverlayTexts.message(context, p.message),
            style = MaterialTheme.typography.bodyMedium,
            color = if (p.message.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionButtons(model: CardModel, callbacks: CardCallbacks) {
    val waitSeconds = model.rateLimitWaitMillis?.let { ActionMessages.waitSeconds(it).toInt() }
    val rateOk = waitSeconds == null
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Button(
            // The token is created HERE, inside the click handler of the user's tap.
            onClick = { callbacks.onBlock(UserIntentToken.fromUserClick("overlay_card.block", System.currentTimeMillis())) },
            enabled = !model.busy && rateOk,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text(
                if (waitSeconds == null) {
                    stringResource(R.string.overlay_card_block)
                } else {
                    stringResource(R.string.overlay_card_block_wait, waitSeconds)
                },
            )
        }
        FilledTonalButton(
            onClick = { callbacks.onSkip(UserIntentToken.fromUserClick("overlay_card.skip", System.currentTimeMillis())) },
            enabled = !model.busy,
        ) { Text(stringResource(R.string.overlay_card_skip)) }
        if (model.showNotInterested) {
            FilledTonalButton(
                onClick = {
                    callbacks.onNotInterested(
                        UserIntentToken.fromUserClick("overlay_card.not_interested", System.currentTimeMillis()),
                    )
                },
                enabled = !model.busy && rateOk,
            ) {
                Text(
                    if (waitSeconds == null) {
                        stringResource(R.string.overlay_card_not_interested)
                    } else {
                        stringResource(R.string.overlay_card_not_interested_wait, waitSeconds)
                    },
                )
            }
        }
        OutlinedButton(onClick = callbacks::onLogOnly, enabled = !model.busy) {
            Text(stringResource(R.string.overlay_card_log_only))
        }
        OutlinedButton(onClick = callbacks::onFalsePositive, enabled = !model.busy) {
            Text(stringResource(R.string.overlay_card_false_positive))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualTags(model: CardModel, callbacks: CardCallbacks) {
    Text(stringResource(R.string.overlay_card_tags_title), style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CardModel.QUICK_TAGS.forEach { tag ->
            val selected = tag in model.manualTags
            FilterChip(
                selected = selected,
                onClick = { callbacks.onToggleTag(tag, !selected) },
                enabled = !model.busy,
                label = { Text(OverlayTexts.tag(tag)?.let { stringResource(it) } ?: tag) },
            )
        }
    }
}

private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
