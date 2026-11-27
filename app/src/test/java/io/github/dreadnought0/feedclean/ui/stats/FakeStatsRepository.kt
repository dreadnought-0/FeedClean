package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.data.StatsRepository
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId

/** In-memory [StatsRepository] that records every query's arguments. */
class FakeStatsRepository : StatsRepository {
    val today = MutableStateFlow(DailyCounts())
    val series = MutableStateFlow<List<DayStats>>(emptyList())
    val sessions = MutableStateFlow<List<SessionRatio>>(emptyList())

    /** nowMillis of each observeToday call. */
    val todayRequests = mutableListOf<Long>()

    /** (days, nowMillis) of each observeDailySeries call. */
    val seriesRequests = mutableListOf<Pair<Int, Long>>()

    /** limit of each observeSessionRatios call. */
    val sessionRequests = mutableListOf<Int>()

    override fun observeToday(nowMillis: Long, zone: ZoneId): Flow<DailyCounts> {
        todayRequests += nowMillis
        return today
    }

    override fun observeDailySeries(days: Int, nowMillis: Long, zone: ZoneId): Flow<List<DayStats>> {
        seriesRequests += days to nowMillis
        return series
    }

    override fun observeSessionRatios(limit: Int): Flow<List<SessionRatio>> {
        sessionRequests += limit
        return sessions
    }
}
