package io.github.dreadnought0.feedclean.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class StatsAndSessionTest : DbTest() {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 22, 12, 0).toInstant(zone).toEpochMilli()
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    @Test
    fun todayCountsAndDailySeries() = runTest {
        val a = accounts.upsertFromFeed("A", null, now - day)
        sightings.recordSighting(a, "p1", Signals(isPaidAd = true), SightingSource.OVERLAY, nowMillis = now - 60 * minute)
        sightings.recordSighting(
            a, "p2", Signals(hasShopAnchor = true), SightingSource.OVERLAY,
            userAction = UserAction.AUTO_SKIP, skipReason = SkipReason.ORGANIC_SHOP, nowMillis = now - 30 * minute,
        )
        val ni = sightings.recordSighting(a, "p3", Signals(hasShopAnchor = true), SightingSource.OVERLAY, nowMillis = now - 20 * minute)
        sightings.setUserAction(ni.id, UserAction.NOT_INTERESTED, verified = true)
        sightings.recordSighting(a, "p4", Signals(isPromoted = true), SightingSource.OVERLAY, nowMillis = now - day)
        blockEvents.record(a, now - 10 * minute, BlockMethod.ASSISTED, verified = true)
        blockEvents.record(a, now - 5 * minute, BlockMethod.ASSISTED, verified = false)
        blockEvents.record(a, now - minute, BlockMethod.IMPORTED, verified = true)
        val session = sessions.start(now - 120 * minute)
        repeat(3) { sessions.recordPage(session, Signals(), skipped = false) }
        sessions.recordPage(session, Signals(hasShopAnchor = true), skipped = true)

        assertEquals(
            DailyCounts(seen = 4, detected = 3, organicShop = 2, paidAds = 1, autoSkipped = 1, notInterested = 1, blocked = 1),
            stats.observeToday(now, zone).first(),
        )
        val series = stats.observeDailySeries(2, now, zone).first()
        assertEquals(listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22)), series.map { it.date })
        assertEquals(DailyCounts(detected = 1, promoted = 1), series[0].counts)
    }

    @Test
    fun todayReemitsWhenDataChanges() = runTest {
        val a = accounts.upsertFromFeed("A", null, now)
        val flow = stats.observeToday(now, zone)
        assertEquals(DailyCounts(), flow.first())
        sightings.recordSighting(a, "p", Signals(isPaidAd = true), SightingSource.OVERLAY, nowMillis = now)
        assertEquals(1, flow.first { it.paidAds == 1 }.detected)
    }

    @Test
    fun sessionCountersAndRatios() = runTest {
        val older = sessions.start(now - day)
        sessions.end(older, now - day + minute)
        val current = sessions.start(now)
        sessions.recordPage(current, Signals(isPaidAd = true), skipped = false)
        sessions.recordPage(current, Signals(hasShopAnchor = true), skipped = true)
        sessions.recordPage(current, Signals(hasShopAnchor = true, isLiveShopping = true), skipped = true)
        sessions.recordPage(current, Signals(isPromoted = true), skipped = false)

        val ratios = stats.observeSessionRatios(10).first()
        assertEquals(listOf(current, older), ratios.map { it.session.id })
        val r = ratios.first()
        assertEquals(4, r.session.videosSeen)
        assertEquals(1, r.session.ads)
        assertEquals(1, r.session.promoted)
        assertEquals(2, r.session.shop)
        assertEquals(1, r.session.live)
        assertEquals(2, r.session.skipped)
        assertEquals(0.5, r.shopRatio, 1e-9)
        assertEquals(0.5, r.skippedRatio, 1e-9)
        assertNull(r.session.endedAt)
        assertNotNull(ratios[1].session.endedAt)

        assertEquals(1, sessions.endAllOpen(now + minute))
        assertEquals(now + minute, stats.observeSessionRatios(1).first().single().session.endedAt)
        assertEquals(0, sessions.endAllOpen(now + 2 * minute))
    }
}
