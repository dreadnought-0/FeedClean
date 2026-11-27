package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class DateLabelsTest {
    private val newYork = ZoneId.of("America/New_York")
    private val london = ZoneId.of("Europe/London")

    private fun millis(text: String, zone: ZoneId): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun oneSecondAcrossMidnightIsYesterday() {
        val at = millis("2026-11-21T23:59:59", london)
        val now = millis("2026-11-22T00:00:01", london)
        assertEquals(DayRelation.YESTERDAY, dayRelation(at, now, london))
        assertEquals(DayRelation.TODAY, dayRelation(now, now, london))
    }

    @Test
    fun springForwardDayIsOnly23HoursButStillYesterday() {
        // 2026-03-08 is 23 h long in New York: 23 h 15 min apart, yet a calendar day earlier.
        val at = millis("2026-03-07T23:30", newYork)
        val now = millis("2026-03-08T23:45", newYork)
        assertEquals(DayRelation.YESTERDAY, dayRelation(at, now, newYork))
    }

    @Test
    fun fallBackDayIs25HoursAndStillYesterday() {
        // 2026-11-01 is 25 h long in New York: about 48 h 40 min apart, yet only one calendar day earlier.
        val at = millis("2026-10-31T00:10", newYork)
        val now = millis("2026-11-01T23:50", newYork)
        assertEquals(DayRelation.YESTERDAY, dayRelation(at, now, newYork))
    }

    @Test
    fun theSameInstantIsTodayInOneZoneAndYesterdayInAnother() {
        val at = millis("2026-11-21T23:30", london)
        val now = millis("2026-11-22T08:00", london)
        assertEquals(DayRelation.YESTERDAY, dayRelation(at, now, london))
        // In Tokyo both instants fall on 22 Nov.
        assertEquals(DayRelation.TODAY, dayRelation(at, now, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun olderDatesSplitByYear() {
        val now = millis("2026-11-22T12:00", london)
        assertEquals(DayRelation.THIS_YEAR, dayRelation(millis("2026-01-03T12:00", london), now, london))
        assertEquals(DayRelation.OTHER_YEAR, dayRelation(millis("2025-12-31T12:00", london), now, london))
    }

    @Test
    fun relativeDateTimeTextPicksTheStringAndFormatsTheTime() {
        val now = millis("2026-11-22T12:00", london)
        assertEquals(
            UiText.Res(R.string.screens_time_today_at, listOf("09:05")),
            relativeDateTimeText(millis("2026-11-22T09:05", london), now, london, Locale.UK),
        )
        assertEquals(
            UiText.Res(R.string.screens_time_yesterday_at, listOf("23:50")),
            relativeDateTimeText(millis("2026-11-21T23:50", london), now, london, Locale.UK),
        )
        assertEquals(
            UiText.Res(R.string.screens_time_date_at, listOf("Nov 3", "14:00")),
            relativeDateTimeText(millis("2026-11-03T14:00", london), now, london, Locale.UK),
        )
    }

    @Test
    fun relativeDateTextUsesTodayYesterdayOrDate() {
        val now = millis("2026-11-22T12:00", london)
        assertEquals(UiText.Res(R.string.screens_time_today), relativeDateText(now, now, london, Locale.UK))
        assertEquals(
            UiText.Res(R.string.screens_time_yesterday),
            relativeDateText(millis("2026-11-21T01:00", london), now, london, Locale.UK),
        )
        assertEquals(UiText.Plain("Nov 1, 2025"), relativeDateText(millis("2025-11-01T01:00", london), now, london, Locale.US))
    }

    @Test
    fun shortDateAddsTheYearOnlyForOtherYears() {
        val today = LocalDate.of(2026, 11, 22)
        assertEquals("Nov 14", shortDateLabel(LocalDate.of(2026, 11, 14), today, Locale.US))
        assertEquals("Dec 31, 2025", shortDateLabel(LocalDate.of(2025, 12, 31), today, Locale.US))
    }

    @Test
    fun clockTimeFollowsTheZone() {
        val instant = millis("2026-11-22T09:05", london)
        assertEquals("09:05", clockTimeLabel(instant, london, Locale.UK))
        assertEquals("04:05", clockTimeLabel(instant, newYork, Locale.UK))
    }
}
