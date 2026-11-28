package io.github.dreadnought0.feedclean.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.time.LocalDate

/** Tabular figures, so numbers line up down the columns. */
private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * Table twin of the daily charts: one row per day (newest first), one column per measure. Every value the
 * charts draw is readable here without relying on color or tapping.
 */
@Composable
fun DailyTable(model: DailyChartModel, today: LocalDate, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val todayLabel = stringResource(R.string.screens_time_today)
    val header = MaterialTheme.typography.labelMedium
    val body = MaterialTheme.typography.bodyMedium.merge(TabularNumbers)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.screens_table_date), style = header, modifier = Modifier.weight(1.4f))
            model.series.forEach { series ->
                Text(
                    text = stringResource(series.metric.titleRes),
                    style = header,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider()
        for (index in model.dates.indices.reversed()) {
            val date = model.dates[index]
            Row(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (date == today) todayLabel else weekdayDateLabel(date, locale),
                    style = body,
                    modifier = Modifier.weight(1.4f),
                )
                model.series.forEach { series ->
                    Text(
                        text = series.values[index].toString(),
                        style = body,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyTablePreview() {
    val today = LocalDate.of(2026, 11, 22)
    val model = DailyChartModel(
        dates = List(4) { today.minusDays(3L - it) },
        series = listOf(
            DailySeries(DailyMetric.BLOCKS, listOf(0, 1, 0, 2)),
            DailySeries(DailyMetric.AUTO_SKIPS, listOf(4, 9, 12, 7)),
            DailySeries(DailyMetric.ORGANIC_SHOP, listOf(6, 11, 15, 9)),
        ),
    )
    FeedCleanTheme {
        DailyTable(model = model, today = today, modifier = Modifier.padding(16.dp))
    }
}
