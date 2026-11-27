package io.github.dreadnought0.feedclean.ui.stats

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Days shown in the Stats charts. */
const val CHART_DAYS: Int = 14

/**
 * A per-day measure drawn as its own small column chart. The measures have very different scales (a few
 * blocks vs. dozens of skips), so each gets its own chart and axis rather than sharing one.
 */
enum class DailyMetric(
    @param:StringRes val titleRes: Int,
    /** Plural for "N <unit>" in the selected-day label and the chart description. */
    @param:PluralsRes val unitPluralRes: Int,
) {
    BLOCKS(R.string.screens_chart_blocks, R.plurals.screens_unit_blocks),
    AUTO_SKIPS(R.string.screens_chart_auto_skips, R.plurals.screens_unit_auto_skips),
    ORGANIC_SHOP(R.string.screens_chart_organic_shop, R.plurals.screens_unit_organic_shop),
    ;

    /** This measure's value in [counts]. */
    fun valueIn(counts: DailyCounts): Int = when (this) {
        BLOCKS -> counts.blocked
        AUTO_SKIPS -> counts.autoSkipped
        ORGANIC_SHOP -> counts.organicShop
    }
}

/** One measure over the chart's days (oldest first, one value per day, zero days included). */
data class DailySeries(val metric: DailyMetric, val values: List<Int>) {
    val total: Int = values.sum()
    val max: Int = values.maxOrNull() ?: 0

    /** Top of the value axis: a clean number at or above [max], never 0 (so empty charts still scale). */
    val axisMax: Int = niceAxisMax(max)

    /** Index of the highest day (the most recent one on ties); null when every day is zero. */
    val peakIndex: Int? = if (max <= 0) null else values.lastIndexOf(max)

    val isAllZero: Boolean get() = max <= 0
}

/**
 * The Stats chart: [dates] (oldest first, ending today) and one [DailySeries] per measure, each with
 * exactly one value per date.
 */
data class DailyChartModel(val dates: List<LocalDate>, val series: List<DailySeries>) {
    /** Indices of the dates labelled under the columns: first, middle, last. */
    val tickIndices: List<Int> get() = axisTickIndices(dates.size)
}

/**
 * Builds the chart from the repository's day series. Exactly [days] dates ending at [today] are produced;
 * a date missing from [stats] counts as zero and entries outside the range are ignored, so gaps or a
 * series computed just before midnight never shift the columns.
 */
fun buildDailyChartModel(
    stats: List<DayStats>,
    today: LocalDate,
    days: Int = CHART_DAYS,
    metrics: List<DailyMetric> = DailyMetric.entries,
): DailyChartModel {
    val count = days.coerceAtLeast(1)
    val first = today.minusDays(count - 1L)
    val dates = List(count) { first.plusDays(it.toLong()) }
    val byDate = stats.associateBy { it.date }
    val counts = dates.map { byDate[it]?.counts ?: DailyCounts() }
    return DailyChartModel(
        dates = dates,
        series = metrics.map { metric -> DailySeries(metric, counts.map(metric::valueIn)) },
    )
}

/** The counts for [date] in [stats], or zeros. */
fun countsOn(stats: List<DayStats>, date: LocalDate): DailyCounts =
    stats.lastOrNull { it.date == date }?.counts ?: DailyCounts()

/** First, middle and last index of [days] columns (fewer when there are fewer columns). */
fun axisTickIndices(days: Int): List<Int> = when {
    days <= 0 -> emptyList()
    else -> listOf(0, (days - 1) / 2, days - 1).distinct()
}

private val NICE_FACTORS = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

/**
 * A clean axis maximum for counts: at least 1; the value itself up to 10; above that the smallest of
 * 1, 1.5, 2, 2.5, 3, 4, 5, 6, 8 × 10ⁿ that is at least [max] (12 → 15, 61 → 80, 130 → 150).
 */
fun niceAxisMax(max: Int): Int {
    if (max <= 1) return 1
    if (max <= 10) return max
    val magnitude = 10.0.pow(floor(log10(max.toDouble())))
    val factor = NICE_FACTORS.first { it * magnitude >= max }
    return ceil(factor * magnitude).toInt()
}
