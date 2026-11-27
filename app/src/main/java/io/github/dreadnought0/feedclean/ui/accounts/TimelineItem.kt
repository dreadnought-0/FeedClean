package io.github.dreadnought0.feedclean.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.stats.UiText
import io.github.dreadnought0.feedclean.ui.stats.currentLocale
import io.github.dreadnought0.feedclean.ui.stats.relativeDateTimeText
import io.github.dreadnought0.feedclean.ui.stats.resolve
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.time.ZoneId

/**
 * One timeline row: an icon for its kind, the title with an outcome mark (✓ verified / ⚠ not verified, each
 * with a text description), the time, signal labels and detail lines. Read by TalkBack as one item.
 */
@Composable
fun TimelineItem(row: TimelineRowText, nowMillis: Long, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (row.kind == TimelineKind.BLOCK) Icons.Filled.Lock else Icons.Filled.Info,
            contentDescription = null,
            tint = variant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title.resolve(), style = MaterialTheme.typography.titleSmall)
                OutcomeMark(row.outcome)
                Text(
                    text = relativeDateTimeText(row.atMillis, nowMillis, ZoneId.systemDefault(), locale).resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = variant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            if (row.signalLabels.isNotEmpty()) {
                Text(
                    text = row.signalLabels.map { stringResource(it) }.joinToString(stringResource(R.string.screens_separator)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            row.details.forEach { detail ->
                Text(detail.resolve(), style = MaterialTheme.typography.bodySmall, color = variant)
            }
        }
    }
}

@Composable
private fun OutcomeMark(outcome: TimelineOutcome) {
    when (outcome) {
        TimelineOutcome.VERIFIED -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.screens_outcome_verified),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )

        TimelineOutcome.FAILED -> Icon(
            Icons.Filled.Warning,
            contentDescription = stringResource(R.string.screens_outcome_failed),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )

        TimelineOutcome.NONE -> Unit
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineItemPreview() {
    val now = 1_795_000_000_000L
    FeedCleanTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(16.dp)) {
            TimelineItem(
                row = TimelineRowText(
                    key = "b1", kind = TimelineKind.BLOCK, atMillis = now - 600_000L,
                    title = UiText.Res(R.string.screens_timeline_block_failed), outcome = TimelineOutcome.FAILED,
                    signalLabels = emptyList(),
                    details = listOf(
                        UiText.Res(R.string.screens_block_method_assisted),
                        UiText.Res(R.string.screens_timeline_step, listOf("CHECK_DIALOG")),
                        UiText.Res(R.string.screens_timeline_failure, listOf("handle mismatch")),
                    ),
                ),
                nowMillis = now,
            )
            TimelineItem(
                row = TimelineRowText(
                    key = "s1", kind = TimelineKind.SIGHTING, atMillis = now - 26 * 3_600_000L,
                    title = UiText.Res(R.string.screens_timeline_auto_skipped), outcome = TimelineOutcome.NONE,
                    signalLabels = listOf(R.string.screens_signal_organic_shop, R.string.screens_signal_shop_anchor),
                    details = listOf(
                        UiText.Res(
                            R.string.screens_timeline_reason,
                            listOf(UiText.Res(R.string.screens_skip_reason_organic_shop)),
                        ),
                    ),
                ),
                nowMillis = now,
            )
        }
    }
}
