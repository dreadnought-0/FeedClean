package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyChartModelTest {
    private val today = LocalDate.of(2026, 11, 22)

    private fun day(daysAgo: Long, blocked: Int = 0, autoSkipped: Int = 0, organicShop: Int = 0) = DayStats(
        date = today.minusDays(daysAgo),
        counts = DailyCounts(blocked = blocked, autoSkipped = autoSkipped, organicShop = organicShop),
    )

    private fun DailyChartModel.valuesOf(metric: DailyMetric) = series.single { it.metric == metric }.values

    @Test
    fun emptyInputGivesFourteenZeroDaysEndingToday() {
        val model = buildDailyChartModel(emptyList(), today)

        assertEquals(CHART_DAYS, model.dates.size)
        assertEquals(today.minusDays(13), model.dates.first())
        assertEquals(today, model.dates.last())
        assertEquals(DailyMetric.entries, model.series.map { it.metric })
        model.series.forEach { series ->
            assertEquals(List(CHART_DAYS) { 0 }, series.values)
            assertTrue(series.isAllZero)
            assertNull(series.peakIndex)
            assertEquals(1, series.axisMax)
            assertEquals(0, series.total)
        }
    }

    @Test
    fun missingDaysAreZeroAndValuesLandOnTheirDates() {
        val model = buildDailyChartModel(
            listOf(day(13, blocked = 1), day(5, blocked = 3, autoSkipped = 9), day(0, organicShop = 4)),
            today,
        )

        val blocks = model.valuesOf(DailyMetric.BLOCKS)
        assertEquals(1, blocks[0])
        assertEquals(3, blocks[8])
        assertEquals(12, blocks.count { it == 0 })
        assertEquals(9, model.valuesOf(DailyMetric.AUTO_SKIPS)[8])
        assertEquals(4, model.valuesOf(DailyMetric.ORGANIC_SHOP).last())
        assertEquals(4, model.series.single { it.metric == DailyMetric.BLOCKS }.total)
    }

    @Test
    fun daysOutsideTheWindowAreIgnored() {
        val model = buildDailyChartModel(listOf(day(14, blocked = 50), day(-1, blocked = 50), day(2, blocked = 2)), today)
        assertEquals(2, model.series.single { it.metric == DailyMetric.BLOCKS }.total)
    }

    @Test
    fun aSeriesFromYesterdaysWindowShiftsByOneDay() {
        // Computed just before midnight: its last day is yesterday, so today's column is zero.
        val model = buildDailyChartModel(listOf(day(1, autoSkipped = 7)), today)
        val skips = model.valuesOf(DailyMetric.AUTO_SKIPS)
        assertEquals(7, skips[12])
        assertEquals(0, skips[13])
    }

    @Test
    fun peakIsTheMostRecentHighestDay() {
        val series = DailySeries(DailyMetric.BLOCKS, listOf(0, 4, 1, 4, 0))
        assertEquals(3, series.peakIndex)
        assertEquals(4, series.max)
        assertFalse(series.isAllZero)
    }

    @Test
    fun customDayCountIsAtLeastOne() {
        assertEquals(listOf(today), buildDailyChartModel(emptyList(), today, days = 0).dates)
        assertEquals(3, buildDailyChartModel(emptyList(), today, days = 3).dates.size)
    }

    @Test
    fun niceAxisMaxIsCleanAndNeverZero() {
        assertEquals(1, niceAxisMax(0))
        assertEquals(1, niceAxisMax(-3))
        assertEquals(1, niceAxisMax(1))
        assertEquals(7, niceAxisMax(7))
        assertEquals(10, niceAxisMax(10))
        assertEquals(15, niceAxisMax(11))
        assertEquals(15, niceAxisMax(12))
        assertEquals(20, niceAxisMax(17))
        assertEquals(60, niceAxisMax(60))
        assertEquals(80, niceAxisMax(61))
        assertEquals(100, niceAxisMax(81))
        assertEquals(150, niceAxisMax(130))
        assertEquals(1000, niceAxisMax(801))
    }

    @Test
    fun tickIndicesAreFirstMiddleLast() {
        assertEquals(listOf(0, 6, 13), axisTickIndices(14))
        assertEquals(listOf(0, 1, 2), axisTickIndices(3))
        assertEquals(listOf(0, 1), axisTickIndices(2))
        assertEquals(listOf(0), axisTickIndices(1))
        assertEquals(emptyList<Int>(), axisTickIndices(0))
    }

    @Test
    fun countsOnFindsTheDayOrZeros() {
        val stats = listOf(day(0, blocked = 2))
        assertEquals(2, countsOn(stats, today).blocked)
        assertEquals(DailyCounts(), countsOn(stats, today.minusDays(1)))
    }
}
