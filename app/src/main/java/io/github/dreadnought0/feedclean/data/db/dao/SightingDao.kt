package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.dreadnought0.feedclean.data.db.entity.SightingEntity
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {
    @Insert
    suspend fun insert(sighting: SightingEntity): Long

    @Query("SELECT * FROM sightings WHERE id = :id")
    suspend fun byId(id: Long): SightingEntity?

    /** Latest sighting of [pageSignature] at or after [since] (dedupe window). */
    @Query(
        "SELECT * FROM sightings WHERE page_signature = :pageSignature AND seen_at >= :since " +
            "ORDER BY seen_at DESC, id DESC LIMIT 1",
    )
    suspend fun latestWithSignatureSince(pageSignature: String, since: Long): SightingEntity?

    @Query("UPDATE sightings SET user_action = :action, action_verified = :verified WHERE id = :id")
    suspend fun setUserAction(id: Long, action: UserAction, verified: Boolean?)

    @Query("UPDATE sightings SET user_action = :action, skip_reason = :skipReason WHERE id = :id")
    suspend fun setUserActionAndReason(id: Long, action: UserAction, skipReason: SkipReason?)

    /** Replaces the signals of one sighting (late-appearing labels on the same page). */
    @Query("UPDATE sightings SET signals_json = :signals WHERE id = :id")
    suspend fun setSignals(id: Long, signals: Signals)

    @Query("SELECT * FROM sightings ORDER BY seen_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE account_id = :accountId ORDER BY seen_at DESC, id DESC LIMIT 1")
    suspend fun latestForAccount(accountId: Long): SightingEntity?

    @Query("SELECT * FROM sightings WHERE account_id = :accountId ORDER BY seen_at DESC, id DESC")
    fun observeForAccount(accountId: Long): Flow<List<SightingEntity>>

    @Query("SELECT * FROM sightings WHERE account_id = :accountId AND seen_at > :after ORDER BY seen_at")
    suspend fun forAccountAfter(accountId: Long, after: Long): List<SightingEntity>

    @Query(
        "SELECT seen_at, signals_json, user_action, action_verified FROM sightings " +
            "WHERE seen_at >= :from AND seen_at < :to",
    )
    fun observeStatRows(from: Long, to: Long): Flow<List<SightingStatRow>>

    /** Moves every sighting of one account to another (merge). */
    @Query("UPDATE sightings SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassign(fromId: Long, toId: Long)
}
