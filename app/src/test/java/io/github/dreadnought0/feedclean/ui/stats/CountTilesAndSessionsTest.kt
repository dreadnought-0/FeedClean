package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.Session
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CountTilesAndSessionsTest {
    private val counts = DailyCounts(
        seen = 100, detected = 9, organicShop = 8, paidAds = 7, promoted = 6,
        autoSkipped = 5, manuallySkipped = 4, notInterested = 3, blocked = 2,
    )

    @Test
    fun seenTileOnlyWithSessionTracking() {
        val off = countTiles(counts, sessionTrackingEnabled = false)
        assertFalse(off.any { it.kind == CountKind.SEEN })
        assertEquals(8, off.size)

        val on = countTiles(counts, sessionTrackingEnabled = true)
        assertEquals(CountTile(CountKind.SEEN, 100), on.first())
        assertEquals(9, on.size)
    }

    @Test
    fun tilesKeepTheirOrderAndValues() {
        assertEquals(
            listOf(9, 8, 7, 6, 5, 4, 3, 2),
            countTiles(counts, sessionTrackingEnabled = false).map { it.value },
        )
        assertEquals(
            listOf(
                CountKind.DETECTED, CountKind.ORGANIC_SHOP, CountKind.PAID_ADS, CountKind.PROMOTED,
                CountKind.AUTO_SKIPPED, CountKind.MANUALLY_SKIPPED, CountKind.NOT_INTERESTED, CountKind.BLOCKED,
            ),
            countTiles(counts, sessionTrackingEnabled = false).map { it.kind },
        )
    }

    @Test
    fun sumCountsAddsEveryField() {
        val day = LocalDate.of(2026, 11, 22)
        val sum = sumCounts(listOf(DayStats(day, counts), DayStats(day.minusDays(1), counts)))
        assertEquals(
            DailyCounts(
                seen = 200, detected = 18, organicShop = 16, paidAds = 14, promoted = 12,
                autoSkipped = 10, manuallySkipped = 8, notInterested = 6, blocked = 4,
            ),
            sum,
        )
        assertEquals(DailyCounts(), sumCounts(emptyList()))
    }

    @Test
    fun shareRoundsAndClamps() {
        assertEquals(Share(0.125f, 13), shareOf(0.125))
        assertEquals(Share(0f, 0), shareOf(-0.2))
        assertEquals(Share(1f, 100), shareOf(1.7))
        assertEquals(Share(0f, 0), shareOf(Double.NaN))
    }

    @Test
    fun sessionRowCarriesSharesAndDuration() {
        val session = Session(
            id = 4, startedAt = 0L, endedAt = 32 * 60_000L + 59_000L, videosSeen = 200,
            ads = 10, promoted = 4, shop = 30, live = 0, skipped = 25,
        )
        val row = SessionRatio.of(session).toRowModel()
        assertEquals(15, row.shop.percent)
        assertEquals(5, row.ads.percent)
        assertEquals(2, row.promoted.percent)
        assertEquals(25, row.skipped)
        assertEquals(13, row.skippedShare.percent)
        assertEquals(32L, row.durationMinutes)
        assertFalse(row.isOpen)
    }

    @Test
    fun openSessionHasNoDurationAndEmptySessionHasZeroShares() {
        val row = SessionRatio.of(
            Session(id = 1, startedAt = 5L, endedAt = null, videosSeen = 0, ads = 0, promoted = 0, shop = 0, live = 0, skipped = 0),
        ).toRowModel()
        assertTrue(row.isOpen)
        assertNull(row.durationMinutes)
        assertEquals(0, row.shop.percent)
        assertEquals(0f, row.ads.fraction)
    }
}
