package io.github.dreadnought0.feedclean.data

import android.content.Context
import androidx.room.withTransaction
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.SeedCallback
import io.github.dreadnought0.feedclean.data.room.RoomAccountRepository
import io.github.dreadnought0.feedclean.data.room.RoomActionLogRepository
import io.github.dreadnought0.feedclean.data.room.RoomAuditRepository
import io.github.dreadnought0.feedclean.data.room.RoomBlockEventRepository
import io.github.dreadnought0.feedclean.data.room.RoomSessionRepository
import io.github.dreadnought0.feedclean.data.room.RoomSightingRepository
import io.github.dreadnought0.feedclean.data.room.RoomStatsRepository
import io.github.dreadnought0.feedclean.settings.DataStoreSettingsRepository
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the database once and exposes every repository plus settings. Construct exactly one per process
 * (in `AppContainer`); UI and service code depend on the repository interfaces only.
 *
 * Primary constructor: for tests (pass an in-memory database and any [SettingsRepository]).
 */
class DataLayer(
    /** The Room database. Exposed for full export/import; prefer the repositories for everything else. */
    val database: FeedCleanDatabase,
    val settingsRepository: SettingsRepository,
    skeletonOf: (String) -> String = RoomAccountRepository.DEFAULT_SKELETON,
) {
    /**
     * Production constructor.
     *
     * @param scope long-lived (application) scope that keeps the settings StateFlow hot.
     * @param skeletonOf display-name skeleton for identity matching (pass `NameNormalizer::skeleton`).
     */
    constructor(context: Context, scope: CoroutineScope, skeletonOf: (String) -> String) : this(
        database = FeedCleanDatabase.create(context),
        settingsRepository = DataStoreSettingsRepository(context, scope),
        skeletonOf = skeletonOf,
    )

    val accountRepository: AccountRepository = RoomAccountRepository(database, skeletonOf)
    val sightingRepository: SightingRepository = RoomSightingRepository(database)
    val blockEventRepository: BlockEventRepository = RoomBlockEventRepository(database)
    val actionLogRepository: ActionLogRepository = RoomActionLogRepository(database)
    val sessionRepository: SessionRepository = RoomSessionRepository(database)
    val statsRepository: StatsRepository = RoomStatsRepository(database)
    val auditRepository: AuditRepository = RoomAuditRepository(database)

    /**
     * Settings → Data & privacy → "Clear all data": deletes every row of every table and re-seeds the default
     * tags. Settings are kept.
     *
     * Clearing data must not reset the rate limiter, so action-log rows from the last
     * [RATE_LIMIT_RETENTION_MILLIS] are re-inserted without their account link (kind, time, verified only).
     */
    suspend fun clearAllData(nowMillis: Long = System.currentTimeMillis()) {
        val actionLogDao = database.actionLogDao()
        val recentActions = actionLogDao.entriesSince(nowMillis - RATE_LIMIT_RETENTION_MILLIS)
        withContext(Dispatchers.IO) { database.clearAllTables() }
        database.withTransaction {
            database.tagDao().insertTags(SeedCallback.defaultTagEntities)
            actionLogDao.insertAll(recentActions.map { it.copy(id = 0, accountId = null) })
        }
    }

    companion object {
        /** How much action-log history survives "Clear all data" (the RateLimiter's hourly window). */
        const val RATE_LIMIT_RETENTION_MILLIS: Long = 60 * 60 * 1000L
    }
}
