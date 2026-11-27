package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Date and time labels shared by the Stats, Accounts and Home screens. Everything is computed from LOCAL
// calendar dates in the given zone, never by dividing a millisecond difference by 24 h, so midnight and DST
// days (23 h / 25 h long) land on the right day.

/** How a timestamp relates to "now", in local calendar days. */
enum class DayRelation { TODAY, YESTERDAY, THIS_YEAR, OTHER_YEAR }

/** The local date of [millis] in [zone]. */
fun localDateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** Where [atMillis] falls relative to the local day of [nowMillis]. */
fun dayRelation(atMillis: Long, nowMillis: Long, zone: ZoneId): DayRelation {
    val day = localDateOf(atMillis, zone)
    val today = localDateOf(nowMillis, zone)
    return when {
        day == today -> DayRelation.TODAY
        day == today.minusDays(1) -> DayRelation.YESTERDAY
        day.year == today.year -> DayRelation.THIS_YEAR
        else -> DayRelation.OTHER_YEAR
    }
}

/** Locale-formatted short clock time, e.g. "14:05" (UK) or "2:05 PM" (US). */
fun clockTimeLabel(atMillis: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        .format(Instant.ofEpochMilli(atMillis).atZone(zone))

/** Short date, e.g. "Nov 14"; the year is added when [date] is not in [today]'s year ("Nov 14, 2025"). */
fun shortDateLabel(date: LocalDate, today: LocalDate, locale: Locale): String {
    val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** Date with weekday, e.g. "Sun, Nov 22". */
fun weekdayDateLabel(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date)

/** "Today, 14:05", "Yesterday, 23:50" or "Nov 3, 09:12" (year added for other years). */
fun relativeDateTimeText(atMillis: Long, nowMillis: Long, zone: ZoneId, locale: Locale): UiText {
    val time = clockTimeLabel(atMillis, zone, locale)
    return when (dayRelation(atMillis, nowMillis, zone)) {
        DayRelation.TODAY -> UiText.Res(R.string.screens_time_today_at, listOf(time))
        DayRelation.YESTERDAY -> UiText.Res(R.string.screens_time_yesterday_at, listOf(time))
        DayRelation.THIS_YEAR, DayRelation.OTHER_YEAR -> UiText.Res(
            R.string.screens_time_date_at,
            listOf(shortDateLabel(localDateOf(atMillis, zone), localDateOf(nowMillis, zone), locale), time),
        )
    }
}

/** "Today", "Yesterday" or a short date. */
fun relativeDateText(atMillis: Long, nowMillis: Long, zone: ZoneId, locale: Locale): UiText =
    when (dayRelation(atMillis, nowMillis, zone)) {
        DayRelation.TODAY -> UiText.Res(R.string.screens_time_today)
        DayRelation.YESTERDAY -> UiText.Res(R.string.screens_time_yesterday)
        DayRelation.THIS_YEAR, DayRelation.OTHER_YEAR ->
            UiText.Plain(shortDateLabel(localDateOf(atMillis, zone), localDateOf(nowMillis, zone), locale))
    }
