package io.github.dreadnought0.feedclean.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.time.LocalDate

/**
 * Stats tab: today's counts, blocks / auto-skips / organic Shop per day over the last [CHART_DAYS] days,
 * totals for that window, and per-session Shop and ad shares when session tracking is on.
 *
 * Top-level tab: draws its own Scaffold/top bar (no back arrow); the bottom bar is provided by the shell.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(state = state, modifier = modifier)
}

/** Stateless Stats screen. */
@Composable
fun StatsContent(state: StatsUiState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = stringResource(R.string.ui_tab_stats)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.loaded) return@Column
            StatsSectionCard(title = stringResource(R.string.screens_stats_today)) {
                CountTileGrid(countTiles(state.todayCounts, state.sessionTrackingEnabled))
                if (!state.sessionTrackingEnabled) SectionNote(stringResource(R.string.screens_seen_needs_tracking))
            }
            StatsSectionCard(title = stringResource(R.string.screens_stats_last_days, state.chart.dates.size)) {
                DailyChartsBody(state.chart, state.today)
            }
            StatsSectionCard(title = stringResource(R.string.screens_stats_totals, state.chart.dates.size)) {
                CountTileGrid(countTiles(state.totals, state.sessionTrackingEnabled))
            }
            StatsSectionCard(title = stringResource(R.string.screens_stats_sessions)) {
                SessionRatiosSection(
                    trackingEnabled = state.sessionTrackingEnabled,
                    sessions = state.sessions,
                    nowMillis = state.nowMillis,
                )
            }
        }
    }
}

/** Selected-day readout, the three small charts (or the table) and the chart/table switch. */
@Composable
private fun DailyChartsBody(chart: DailyChartModel, today: LocalDate) {
    if (chart.dates.isEmpty()) return
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var showTable by rememberSaveable { mutableStateOf(false) }
    val selectedIndex = chart.dates.indexOf(LocalDate.ofEpochDay(selectedEpochDay))
        .takeIf { it >= 0 } ?: chart.dates.lastIndex

    if (showTable) {
        DailyTable(model = chart, today = today)
    } else {
        // One gutter for the whole stack, so a day's columns line up across the three charts.
        val gutter = rememberChartGutter(chart.series.map { it.axisMax })
        SelectedDayReadout(chart, selectedIndex, today)
        chart.series.forEach { series ->
            DailyColumnChart(
                series = series,
                dates = chart.dates,
                today = today,
                selectedIndex = selectedIndex,
                onSelect = { selectedEpochDay = chart.dates[it].toEpochDay() },
                gutter = gutter,
            )
        }
        SectionNote(stringResource(R.string.screens_chart_tap_hint))
    }
    TextButton(onClick = { showTable = !showTable }) {
        Text(stringResource(if (showTable) R.string.screens_show_charts else R.string.screens_show_table))
    }
}

@Composable
private fun SelectedDayReadout(chart: DailyChartModel, index: Int, today: LocalDate) {
    val date = chart.dates[index]
    val dateText = weekdayDateLabel(date, currentLocale())
    val values = chart.series.map { metricCountText(it.metric, it.values[index]) }
    Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = if (date == today) stringResource(R.string.screens_selected_day_today, dateText) else dateText,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = values.joinToString(separator = stringResource(R.string.screens_separator)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fake data for previews (obviously made up). */
internal fun previewStatsState(trackingOn: Boolean): StatsUiState {
    val today = LocalDate.of(2026, 11, 22)
    val stats = List(CHART_DAYS) { i ->
        DayStats(
            date = today.minusDays(CHART_DAYS - 1L - i),
            counts = DailyCounts(
                seen = if (trackingOn) 120 + i * 5 else 0,
                detected = 10 + (i * 7) % 13,
                organicShop = 6 + (i * 5) % 11,
                paidAds = 2 + i % 4,
                promoted = i % 3,
                autoSkipped = 5 + (i * 3) % 10,
                manuallySkipped = i % 2,
                notInterested = i % 3,
                blocked = if (i % 4 == 0) 2 else i % 2,
            ),
        )
    }
    val now = today.atTime(15, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val chart = buildDailyChartModel(stats, today)
    return StatsUiState(
        loaded = true,
        nowMillis = now,
        today = today,
        todayCounts = countsOn(stats, today),
        chart = chart,
        totals = sumCounts(stats),
        sessionTrackingEnabled = trackingOn,
        sessions = if (!trackingOn) {
            emptyList()
        } else {
            listOf(
                SessionRowModel(
                    id = 1, startedAt = now - 50 * 60_000L, endedAt = now - 10 * 60_000L, videosSeen = 132,
                    shop = shareOf(0.17), ads = shareOf(0.05), promoted = shareOf(0.02), skipped = 21,
                    skippedShare = shareOf(0.16),
                ),
            )
        },
    )
}

@Preview(showBackground = true, heightDp = 2000)
@Composable
private fun StatsScreenPreview() {
    FeedCleanTheme(dynamicColor = false) {
        StatsContent(state = previewStatsState(trackingOn = true))
    }
}

@Preview(showBackground = true, heightDp = 2000, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsScreenTrackingOffDarkPreview() {
    FeedCleanTheme(darkTheme = true, dynamicColor = false) {
        StatsContent(state = previewStatsState(trackingOn = false))
    }
}
