package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.db.dao.SessionSeenRow
import io.github.dreadnought0.feedclean.data.db.dao.SightingStatRow
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsAggregatorTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun row(time: String, signals: Signals, action: UserAction = UserAction.NONE, verified: Boolean? = null) =
        SightingStatRow(at(time), signals, action, verified)

    @Test
    fun windowCoversLocalDays() {
        val window = StatsAggregator.window(3, at("2026-11-22T15:00"), zone)
        assertEquals(LocalDate.of(2026, 11, 20), window.firstDay)
        assertEquals(3, window.days)
        assertEquals(at("2026-11-20T00:00"), window.fromMillis)
        assertEquals(at("2026-11-23T00:00"), window.toMillis)
        assertEquals(1, StatsAggregator.window(0, at("2026-11-22T15:00"), zone).days)
    }

    @Test
    fun bucketsCountsPerLocalDay() {
        val window = StatsAggregator.window(3, at("2026-11-22T15:00"), zone)
        val sightings = listOf(
            row("2026-11-22T00:30", Signals(isPaidAd = true, disclosureLabel = "Ad")),
            row("2026-11-22T10:00", Signals(hasShopAnchor = true), UserAction.AUTO_SKIP),
            row("2026-11-22T11:00", Signals(hasShopAnchor = true), UserAction.NOT_INTERESTED, verified = true),
            row("2026-11-22T11:30", Signals(hasShopAnchor = true), UserAction.NOT_INTERESTED, verified = false),
            row("2026-11-22T12:00", Signals(isPromoted = true), UserAction.SKIP),
            row("2026-11-22T13:00", Signals(), UserAction.LOG),
            row("2026-11-21T23:59", Signals(hasShopAnchor = true), UserAction.NOT_INTERESTED, verified = null),
            row("2026-11-19T23:00", Signals(isPaidAd = true)), // before the window
        )
        val blocks = listOf(at("2026-11-22T09:00"), at("2026-11-22T09:10"), at("2026-11-20T08:00"))
        val sessions = listOf(SessionSeenRow(at("2026-11-22T08:00"), 30), SessionSeenRow(at("2026-11-22T20:00"), 5))

        val series = StatsAggregator.series(window, zone, sightings, blocks, sessions)

        assertEquals(listOf(20, 21, 22).map { LocalDate.of(2026, 11, it) }, series.map { it.date })
        assertEquals(DailyCounts(blocked = 1), series[0].counts)
        assertEquals(DailyCounts(detected = 1, organicShop = 1, notInterested = 1), series[1].counts)
        assertEquals(
            DailyCounts(
                seen = 35,
                detected = 5,
                organicShop = 3,
                paidAds = 1,
                promoted = 1,
                autoSkipped = 1,
                manuallySkipped = 1,
                notInterested = 1,
                blocked = 2,
            ),
            series[2].counts,
        )
    }

    @Test
    fun handlesDaylightSavingDay() {
        // 2026-11-01 is 25 hours long in New York.
        val window = StatsAggregator.window(3, at("2026-11-02T12:00"), zone)
        val sightings = listOf(
            row("2026-11-01T00:10", Signals(isPaidAd = true)),
            row("2026-11-01T23:50", Signals(isPaidAd = true)),
            row("2026-11-02T00:10", Signals(isPaidAd = true)),
        )
        val series = StatsAggregator.series(window, zone, sightings, emptyList(), emptyList())
        assertEquals(listOf(0, 2, 1), series.map { it.counts.paidAds })
    }

    @Test
    fun sessionIncrements() {
        assertEquals(PageIncrements(0, 0, 0, 0, 0), PageIncrements.of(Signals(), skipped = false))
        assertEquals(PageIncrements(0, 0, 1, 0, 1), PageIncrements.of(Signals(hasShopAnchor = true), skipped = true))
        assertEquals(
            PageIncrements(1, 0, 0, 1, 0),
            PageIncrements.of(Signals(isPaidAd = true, hasShopAnchor = true, isLiveShopping = true), skipped = false),
        )
        assertEquals(PageIncrements(0, 1, 0, 1, 0), PageIncrements.of(Signals(isPromoted = true, isLive = true), false))
    }

    @Test
    fun likePatternEscapesWildcards() {
        assertEquals("%a\\_b\\%c\\\\d%", LikePatterns.contains("a_b%c\\d"))
        assertEquals("%plain.name%", LikePatterns.contains("plain.name"))
    }
}
