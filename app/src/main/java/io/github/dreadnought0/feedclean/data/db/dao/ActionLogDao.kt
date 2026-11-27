package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.dreadnought0.feedclean.data.db.entity.ActionLogEntity

@Dao
interface ActionLogDao {
    @Insert
    suspend fun insert(entry: ActionLogEntity): Long

    @Insert
    suspend fun insertAll(entries: List<ActionLogEntity>)

    /** Every attempt (any kind, verified or not) at or after [since]. */
    @Query("SELECT COUNT(*) FROM action_log WHERE at >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT * FROM action_log WHERE at >= :since ORDER BY at")
    suspend fun entriesSince(since: Long): List<ActionLogEntity>

    @Query("SELECT MAX(at) FROM action_log")
    suspend fun lastAt(): Long?

    @Query("UPDATE action_log SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassign(fromId: Long, toId: Long)
}
