package io.github.dreadnought0.feedclean.data.room

import androidx.room.withTransaction
import io.github.dreadnought0.feedclean.data.RecordResult
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.SightingEntity
import io.github.dreadnought0.feedclean.data.db.toModel
import io.github.dreadnought0.feedclean.data.model.Sighting
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [SightingRepository]. */
class RoomSightingRepository(private val db: FeedCleanDatabase) : SightingRepository {
    private val sightingDao = db.sightingDao()
    private val accountDao = db.accountDao()
    private val tagWriter = TagWriter(db.tagDao())

    override suspend fun recordSighting(
        accountId: Long,
        pageSignature: String,
        signals: Signals,
        source: SightingSource,
        userAction: UserAction,
        skipReason: SkipReason?,
        videoUrl: String?,
        nowMillis: Long,
        sessionId: Long?,
        screenshotPath: String?,
    ): RecordResult = db.withTransaction {
        accountDao.touchSeen(accountId, nowMillis)
        val existing = sightingDao.latestWithSignatureSince(
            pageSignature = pageSignature,
            since = nowMillis - SightingRepository.DEDUPE_WINDOW_MILLIS,
        )
        if (existing != null) {
            if (existing.userAction == UserAction.NONE && userAction != UserAction.NONE) {
                sightingDao.setUserActionAndReason(existing.id, userAction, skipReason ?: existing.skipReason)
            }
            RecordResult(existing.id, deduplicated = true)
        } else {
            val id = sightingDao.insert(
                SightingEntity(
                    accountId = accountId,
                    seenAt = nowMillis,
                    videoUrl = videoUrl,
                    pageSignature = pageSignature,
                    signals = signals,
                    source = source,
                    userAction = userAction,
                    actionVerified = null,
                    skipReason = skipReason,
                    screenshotPath = screenshotPath,
                    sessionId = sessionId,
                ),
            )
            TagNames.forSignals(signals).forEach { tag ->
                tagWriter.assign(accountId, tag, TagSource.AUTO_DETECTED, nowMillis)
            }
            RecordResult(id, deduplicated = false)
        }
    }

    override suspend fun setUserAction(sightingId: Long, action: UserAction, verified: Boolean?) =
        sightingDao.setUserAction(sightingId, action, verified)

    override suspend fun updateSignals(sightingId: Long, signals: Signals, nowMillis: Long) = db.withTransaction {
        val existing = sightingDao.byId(sightingId) ?: return@withTransaction
        sightingDao.setSignals(sightingId, signals)
        TagNames.forSignals(signals).forEach { tag ->
            tagWriter.assign(existing.accountId, tag, TagSource.AUTO_DETECTED, nowMillis)
        }
    }

    override fun observeRecent(limit: Int): Flow<List<Sighting>> =
        sightingDao.observeRecent(limit.coerceAtLeast(0)).map { rows -> rows.map { it.toModel() } }

    override suspend fun latestForAccount(accountId: Long): Sighting? =
        sightingDao.latestForAccount(accountId)?.toModel()

    override suspend fun getSighting(id: Long): Sighting? = sightingDao.byId(id)?.toModel()
}
