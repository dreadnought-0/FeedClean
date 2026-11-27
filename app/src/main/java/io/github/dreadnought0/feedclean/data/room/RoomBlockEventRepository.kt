package io.github.dreadnought0.feedclean.data.room

import androidx.room.withTransaction
import io.github.dreadnought0.feedclean.data.BlockEventRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.BlockEventEntity
import io.github.dreadnought0.feedclean.data.db.toModel
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [BlockEventRepository]. */
class RoomBlockEventRepository(private val db: FeedCleanDatabase) : BlockEventRepository {
    private val blockEventDao = db.blockEventDao()
    private val accountDao = db.accountDao()

    override suspend fun record(
        accountId: Long,
        atMillis: Long,
        method: BlockMethod,
        verified: Boolean,
        failureReason: String?,
        stepReached: String?,
    ): Long = db.withTransaction {
        val id = blockEventDao.insert(
            BlockEventEntity(
                accountId = accountId,
                attemptedAt = atMillis,
                method = method,
                verified = verified,
                failureReason = failureReason,
                stepReached = stepReached,
            ),
        )
        if (verified) accountDao.setStatus(accountId, AccountStatus.BLOCKED)
        id
    }

    override fun observeForAccount(accountId: Long): Flow<List<BlockEvent>> =
        blockEventDao.observeForAccount(accountId).map { rows -> rows.map { it.toModel() } }

    override suspend fun latestVerifiedBlock(accountId: Long): BlockEvent? =
        blockEventDao.latestVerified(accountId)?.toModel()
}
