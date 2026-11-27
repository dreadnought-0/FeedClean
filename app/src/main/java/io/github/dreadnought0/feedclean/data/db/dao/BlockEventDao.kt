package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.dreadnought0.feedclean.data.db.entity.BlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity): Long

    @Query("SELECT * FROM block_events WHERE account_id = :accountId ORDER BY attempted_at DESC, id DESC")
    fun observeForAccount(accountId: Long): Flow<List<BlockEventEntity>>

    @Query(
        "SELECT * FROM block_events WHERE account_id = :accountId AND verified = 1 " +
            "ORDER BY attempted_at DESC, id DESC LIMIT 1",
    )
    suspend fun latestVerified(accountId: Long): BlockEventEntity?

    @Query("SELECT * FROM block_events WHERE verified = 1 ORDER BY attempted_at DESC, id DESC")
    suspend fun allVerified(): List<BlockEventEntity>

    /** Times of verified, non-imported blocks in [from, to). */
    @Query(
        "SELECT attempted_at FROM block_events WHERE verified = 1 AND method != 'IMPORTED' " +
            "AND attempted_at >= :from AND attempted_at < :to",
    )
    fun observeVerifiedTimes(from: Long, to: Long): Flow<List<Long>>

    @Query("UPDATE block_events SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassign(fromId: Long, toId: Long)
}
