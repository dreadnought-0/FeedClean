package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.db.dao.SessionSeenRow
import io.github.dreadnought0.feedclean.data.db.dao.SightingStatRow
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.UserAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure bucketing of raw rows into per-local-day [DailyCounts]. */
internal object StatsAggregator {
    /** The local days [firstDay] .. [firstDay] + [days] - 1, as the epoch-millis range [fromMillis, toMillis). */
    data class Window(val firstDay: LocalDate, val days: Int, val fromMillis: Long, val toMillis: Long)

    /** The last [days] local days (at least 1) ending with the day containing [nowMillis]. */
    fun window(days: Int, nowMillis: Long, zone: ZoneId): Window {
        val count = days.coerceAtLeast(1)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val first = today.minusDays((count - 1).toLong())
        return Window(
            firstDay = first,
            days = count,
            fromMillis = first.atStartOfDay(zone).toInstant().toEpochMilli(),
            toMillis = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    /** One [DayStats] per day of [window], oldest first. Rows outside the window are ignored. */
    fun series(
        window: Window,
        zone: ZoneId,
        sightings: List<SightingStatRow>,
        verifiedBlockTimes: List<Long>,
        sessions: List<SessionSeenRow>,
    ): List<DayStats> {
        val days = Array(window.days) { Counter() }
        fun dayOf(millis: Long): Counter? {
            val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            val index = ChronoUnit.DAYS.between(window.firstDay, date)
            return if (index in 0 until window.days) days[index.toInt()] else null
        }
        for (row in sightings) {
            val day = dayOf(row.seenAt) ?: continue
            val s = row.signals
            if (s.hasAnySignal) day.detected++
            if (s.isOrganicShop) day.organicShop++
            if (s.isPaidAd) day.paidAds++
            if (s.isPromoted) day.promoted++
            when (row.userAction) {
                UserAction.AUTO_SKIP -> day.autoSkipped++
                UserAction.SKIP -> day.manuallySkipped++
                UserAction.NOT_INTERESTED -> if (row.actionVerified != false) day.notInterested++
                else -> Unit
            }
        }
        for (at in verifiedBlockTimes) dayOf(at)?.let { it.blocked++ }
        for (session in sessions) dayOf(session.startedAt)?.let { it.seen += session.videosSeen }
        return days.mapIndexed { i, c -> DayStats(window.firstDay.plusDays(i.toLong()), c.toCounts()) }
    }

    private class Counter {
        var seen = 0
        var detected = 0
        var organicShop = 0
        var paidAds = 0
        var promoted = 0
        var autoSkipped = 0
        var manuallySkipped = 0
        var notInterested = 0
        var blocked = 0

        fun toCounts() = DailyCounts(
            seen = seen,
            detected = detected,
            organicShop = organicShop,
            paidAds = paidAds,
            promoted = promoted,
            autoSkipped = autoSkipped,
            manuallySkipped = manuallySkipped,
            notInterested = notInterested,
            blocked = blocked,
        )
    }
}
