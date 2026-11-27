package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE handle = :handle")
    suspend fun byHandle(handle: String): AccountEntity?

    /** Most recently seen provisional account with this skeleton. */
    @Query(
        "SELECT * FROM accounts WHERE handle IS NULL AND display_name_skeleton = :skeleton " +
            "ORDER BY last_seen_at DESC, id DESC LIMIT 1",
    )
    suspend fun provisionalBySkeleton(skeleton: String): AccountEntity?

    /** The shared provisional bucket for feed pages without a display name. */
    @Query("SELECT * FROM accounts WHERE handle IS NULL AND display_name IS NULL ORDER BY id LIMIT 1")
    suspend fun unknownProvisional(): AccountEntity?

    /** Every account (provisional or not) with this skeleton. */
    @Query("SELECT * FROM accounts WHERE display_name_skeleton = :skeleton ORDER BY last_seen_at DESC, id DESC")
    suspend fun allBySkeleton(skeleton: String): List<AccountEntity>

    @Query(
        "SELECT * FROM accounts WHERE handle IS NOT NULL AND status IN (:statuses) " +
            "ORDER BY handle",
    )
    suspend fun withHandleAndStatus(statuses: List<String>): List<AccountEntity>

    /** Extends the seen range to include [now]; never moves it backwards. */
    @Query(
        "UPDATE accounts SET last_seen_at = MAX(last_seen_at, :now), " +
            "first_seen_at = MIN(first_seen_at, :now) WHERE id = :id",
    )
    suspend fun touchSeen(id: Long, now: Long)

    /** Fills in the display name only when the account has none yet. */
    @Query(
        "UPDATE accounts SET display_name = :displayName, display_name_skeleton = :skeleton " +
            "WHERE id = :id AND display_name IS NULL",
    )
    suspend fun fillDisplayNameIfMissing(id: Long, displayName: String, skeleton: String)

    @Query("UPDATE accounts SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: AccountStatus)

    @Query("UPDATE accounts SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String?)

    @Query("UPDATE accounts SET account_category = :category WHERE id = :id")
    suspend fun setAccountCategory(id: Long, category: String?)

    @Query("UPDATE accounts SET suspected_original_of = :originalId WHERE id = :id")
    suspend fun setSuspectedOriginal(id: Long, originalId: Long?)

    /** Re-points "suspected original" references from one account to another (merge). */
    @Query("UPDATE accounts SET suspected_original_of = :toId WHERE suspected_original_of = :fromId")
    suspend fun repointSuspectedOriginal(fromId: Long, toId: Long)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Accounts list. Empty patterns / filters are disabled by passing null / a zero count.
     * Tag filter: the account must carry ALL [tags] (any source).
     */
    @Query(
        """
        SELECT a.*,
            (SELECT COUNT(*) FROM sightings s WHERE s.account_id = a.id) AS sighting_count,
            (SELECT s.user_action FROM sightings s
                WHERE s.account_id = a.id AND s.user_action != 'NONE'
                ORDER BY s.seen_at DESC, s.id DESC LIMIT 1) AS last_action
        FROM accounts a
        WHERE (:handlePattern IS NULL
                OR a.handle LIKE :handlePattern ESCAPE '\'
                OR a.display_name LIKE :namePattern ESCAPE '\'
                OR a.display_name_skeleton LIKE :skeletonPattern ESCAPE '\')
            AND (:statusCount = 0 OR a.status IN (:statuses))
            AND (:tagCount = 0 OR (
                SELECT COUNT(DISTINCT t.name) FROM account_tags atg
                INNER JOIN tags t ON t.id = atg.tag_id
                WHERE atg.account_id = a.id AND t.name IN (:tags)) = :tagCount)
        ORDER BY a.last_seen_at DESC, a.id DESC
        """,
    )
    fun observeSummaries(
        handlePattern: String?,
        namePattern: String?,
        skeletonPattern: String?,
        statuses: List<String>,
        statusCount: Int,
        tags: List<String>,
        tagCount: Int,
    ): Flow<List<AccountSummaryRow>>
}
