package io.github.dreadnought0.feedclean.ui.stats

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.ZoneId

/**
 * Extra wait after midnight before re-emitting, so the new "now" is safely inside the new day even if the
 * timer fires a little early.
 */
const val ROLLOVER_SLACK_MILLIS: Long = 1_000L

/**
 * Epoch millis of the next local midnight after [nowMillis] in [zone]. Uses the zone's rules, so a DST day
 * is 23 or 25 hours long, and a day whose midnight is skipped by DST starts at the first valid instant.
 */
fun nextLocalMidnightMillis(nowMillis: Long, zone: ZoneId): Long =
    localDateOf(nowMillis, zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Emits the current time now and again just after each local midnight. `StatsRepository` fixes its day
 * window when collected, so screens `flatMapLatest` over this flow to roll "today" over at midnight.
 *
 * The zone is re-read on every tick (the user may travel). Timers do not run while the device sleeps; the
 * screens collect with `WhileSubscribed`, so coming back to the app restarts this flow with a fresh "now".
 */
fun midnightTicks(clock: () -> Long, zone: () -> ZoneId): Flow<Long> = flow {
    while (true) {
        val now = clock()
        emit(now)
        val untilMidnight = nextLocalMidnightMillis(now, zone()) - now
        delay(untilMidnight.coerceAtLeast(0L) + ROLLOVER_SLACK_MILLIS)
    }
}
