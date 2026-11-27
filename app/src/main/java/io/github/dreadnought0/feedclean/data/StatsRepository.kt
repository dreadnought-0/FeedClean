package io.github.dreadnought0.feedclean.data

import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * Dashboard and Stats aggregates. Day boundaries are local midnights in the given zone. The day window is
 * fixed when the flow is collected; re-collect (e.g. on resume) to roll over at midnight.
 */
interface StatsRepository {
    /** Counts for the local day containing [nowMillis]. Re-emits whenever the underlying tables change. */
    fun observeToday(nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Flow<DailyCounts>

    /** One entry per local day for the last [days] days ending today (inclusive), oldest first. */
    fun observeDailySeries(
        days: Int,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<DayStats>>

    /** Ratios for the most recent [limit] sessions, newest first. */
    fun observeSessionRatios(limit: Int): Flow<List<SessionRatio>>
}
