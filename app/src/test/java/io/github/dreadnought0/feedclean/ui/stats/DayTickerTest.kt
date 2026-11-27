package io.github.dreadnought0.feedclean.ui.stats

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayTickerTest {
    private val newYork = ZoneId.of("America/New_York")

    private fun millis(text: String, zone: ZoneId): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun nextMidnightOnAnOrdinaryDay() {
        val now = millis("2026-11-22T15:30", newYork)
        assertEquals(millis("2026-11-23T00:00", newYork), nextLocalMidnightMillis(now, newYork))
    }

    @Test
    fun nextMidnightOnDstDaysUsesTheZoneRules() {
        val springStart = millis("2026-03-08T00:00", newYork)
        // 23-hour day.
        assertEquals(23 * HOUR, nextLocalMidnightMillis(springStart, newYork) - springStart)
        val fallStart = millis("2026-11-01T00:00", newYork)
        // 25-hour day.
        assertEquals(25 * HOUR, nextLocalMidnightMillis(fallStart, newYork) - fallStart)
    }

    @Test
    fun nextMidnightExactlyAtMidnightIsTheFollowingOne() {
        val midnight = millis("2026-11-22T00:00", newYork)
        assertEquals(millis("2026-11-23T00:00", newYork), nextLocalMidnightMillis(midnight, newYork))
    }

    @Test
    fun ticksNowThenJustAfterEachMidnight() = runTest {
        val base = millis("2026-11-22T22:00", newYork)
        val ticks = midnightTicks(clock = { base + currentTime }, zone = { newYork }).take(3).toList()

        assertEquals(base, ticks[0])
        assertEquals(millis("2026-11-23T00:00", newYork) + ROLLOVER_SLACK_MILLIS, ticks[1])
        assertEquals(millis("2026-11-24T00:00", newYork) + ROLLOVER_SLACK_MILLIS, ticks[2])
        assertEquals(LocalDate.of(2026, 11, 23), localDateOf(ticks[1], newYork))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
