package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.dreadnought0.feedclean.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("UPDATE sessions SET ended_at = :endedAt WHERE id = :id AND ended_at IS NULL")
    suspend fun end(id: Long, endedAt: Long)

    /** Closes every still-open session (e.g. after a service restart). Returns the number closed. */
    @Query("UPDATE sessions SET ended_at = MAX(started_at, :endedAt) WHERE ended_at IS NULL")
    suspend fun endAllOpen(endedAt: Long): Int

    /** Adds one page to the session's counters. Each increment is 0 or 1. */
    @Query(
        "UPDATE sessions SET videos_seen = videos_seen + 1, ads = ads + :ad, " +
            "promoted = promoted + :promoted, shop = shop + :shop, live = live + :live, " +
            "skipped = skipped + :skipped WHERE id = :id",
    )
    suspend fun addPage(id: Long, ad: Int, promoted: Int, shop: Int, live: Int, skipped: Int)

    @Query("SELECT * FROM sessions ORDER BY started_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT started_at, videos_seen FROM sessions WHERE started_at >= :from AND started_at < :to")
    fun observeSeenRows(from: Long, to: Long): Flow<List<SessionSeenRow>>
}
