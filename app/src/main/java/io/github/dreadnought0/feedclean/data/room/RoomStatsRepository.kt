package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.StatsRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.toModel
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats
import io.github.dreadnought0.feedclean.data.model.SessionRatio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.ZoneId

/** Room-backed [StatsRepository]. Rows are loaded per window and bucketed by [StatsAggregator]. */
class RoomStatsRepository(db: FeedCleanDatabase) : StatsRepository {
    private val sightingDao = db.sightingDao()
    private val blockEventDao = db.blockEventDao()
    private val sessionDao = db.sessionDao()

    override fun observeToday(nowMillis: Long, zone: ZoneId): Flow<DailyCounts> =
        observeDailySeries(1, nowMillis, zone).map { it.lastOrNull()?.counts ?: DailyCounts() }

    override fun observeDailySeries(days: Int, nowMillis: Long, zone: ZoneId): Flow<List<DayStats>> {
        val window = StatsAggregator.window(days, nowMillis, zone)
        return combine(
            sightingDao.observeStatRows(window.fromMillis, window.toMillis),
            blockEventDao.observeVerifiedTimes(window.fromMillis, window.toMillis),
            sessionDao.observeSeenRows(window.fromMillis, window.toMillis),
        ) { sightings, blocks, sessions ->
            StatsAggregator.series(window, zone, sightings, blocks, sessions)
        }
    }

    override fun observeSessionRatios(limit: Int): Flow<List<SessionRatio>> =
        sessionDao.observeRecent(limit.coerceAtLeast(0)).map { rows -> rows.map { SessionRatio.of(it.toModel()) } }
}
