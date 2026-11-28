package io.github.dreadnought0.feedclean.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.time.ZoneId

/**
 * Session ratios (opt-in session tracking): per session, a meter list of the Shop share and the ad share,
 * plus promoted and skipped. When tracking is off, explains what it adds and where to switch it on.
 */
@Composable
fun SessionRatiosSection(
    trackingEnabled: Boolean,
    sessions: List<SessionRowModel>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !trackingEnabled -> {
                Text(stringResource(R.string.screens_sessions_off_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.screens_sessions_off_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            sessions.isEmpty() -> Text(
                stringResource(R.string.screens_sessions_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SessionRow(session, nowMillis)
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionRowModel, nowMillis: Long) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val started = relativeDateTimeText(session.startedAt, nowMillis, zone, locale).resolve()
    val length = session.durationMinutes?.let { stringResource(R.string.screens_session_minutes, it) }
        ?: stringResource(R.string.screens_session_open)
    val videos = pluralStringResource(R.plurals.screens_session_videos, session.videosSeen, session.videosSeen)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.screens_session_header, started, length, videos),
            style = MaterialTheme.typography.titleSmall,
        )
        ShareMeter(label = stringResource(R.string.screens_session_shop_share), share = session.shop)
        ShareMeter(label = stringResource(R.string.screens_session_ad_share), share = session.ads)
        Text(
            text = stringResource(
                R.string.screens_session_more,
                session.promoted.percent,
                session.skipped,
                session.skippedShare.percent,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Name, a thin meter (fill on a lighter track of the same hue) and the percentage, read as one item. */
@Composable
private fun ShareMeter(label: String, share: Share) {
    val colors = chartColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.track),
        ) {
            if (share.fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(share.fraction)
                        .clip(RoundedCornerShape(50))
                        .background(colors.series),
                )
            }
        }
        Text(
            text = stringResource(R.string.screens_percent, share.percent),
            style = MaterialTheme.typography.bodyMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionRatiosSectionPreview() {
    val now = 1_795_000_000_000L
    val sessions = listOf(
        SessionRowModel(
            id = 2, startedAt = now - 40 * 60_000L, endedAt = null, videosSeen = 96,
            shop = shareOf(0.14), ads = shareOf(0.06), promoted = shareOf(0.02), skipped = 12,
            skippedShare = shareOf(0.125),
        ),
        SessionRowModel(
            id = 1, startedAt = now - 26 * 3_600_000L, endedAt = now - 25 * 3_600_000L, videosSeen = 140,
            shop = shareOf(0.21), ads = shareOf(0.04), promoted = shareOf(0.0), skipped = 25,
            skippedShare = shareOf(0.18),
        ),
    )
    FeedCleanTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SessionRatiosSection(trackingEnabled = true, sessions = sessions, nowMillis = now)
            SessionRatiosSection(trackingEnabled = false, sessions = emptyList(), nowMillis = now)
        }
    }
}
