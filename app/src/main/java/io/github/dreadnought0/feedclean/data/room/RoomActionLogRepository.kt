package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.ActionLogRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.ActionLogEntity

/** Room-backed [ActionLogRepository]: the persisted RateLimiter budget. Attempts count, verified or not. */
class RoomActionLogRepository(db: FeedCleanDatabase) : ActionLogRepository {
    private val dao = db.actionLogDao()

    override suspend fun record(kind: ActionKind, atMillis: Long, accountId: Long?, verified: Boolean) {
        dao.insert(ActionLogEntity(kind = kind, at = atMillis, accountId = accountId, verified = verified))
    }

    override suspend fun countSince(sinceMillis: Long): Int = dao.countSince(sinceMillis)

    override suspend fun lastActionAt(): Long? = dao.lastAt()
}
