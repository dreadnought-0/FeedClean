package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.dreadnought0.feedclean.data.db.entity.AccountTagEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity
import io.github.dreadnought0.feedclean.data.model.TagSource
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    /** Returns the new row id, or -1 if a tag with that name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun byName(name: String): TagEntity?

    @Query("SELECT name FROM tags ORDER BY name")
    fun observeAllNames(): Flow<List<String>>

    @Query("SELECT * FROM tags ORDER BY kind DESC, name")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignment(assignment: AccountTagEntity)

    @Query("DELETE FROM account_tags WHERE account_id = :accountId AND tag_id = :tagId AND source = :source")
    suspend fun deleteAssignment(accountId: Long, tagId: Long, source: TagSource)

    @Query("DELETE FROM account_tags WHERE account_id = :accountId AND tag_id = :tagId")
    suspend fun deleteAssignmentsForTag(accountId: Long, tagId: Long)

    @Query(
        "SELECT t.*, atg.source AS source, atg.created_at AS created_at FROM account_tags atg " +
            "INNER JOIN tags t ON t.id = atg.tag_id WHERE atg.account_id = :accountId " +
            "ORDER BY t.name, atg.source",
    )
    fun observeForAccount(accountId: Long): Flow<List<TagAssignmentRow>>

    @Query(
        "SELECT DISTINCT atg.account_id AS account_id, t.name AS name FROM account_tags atg " +
            "INNER JOIN tags t ON t.id = atg.tag_id",
    )
    fun observeAllAccountTagNames(): Flow<List<AccountTagNameRow>>

    /** Copies one account's tags to another (merge); existing (account, tag, source) rows win. */
    @Query(
        "INSERT OR IGNORE INTO account_tags (account_id, tag_id, source, created_at) " +
            "SELECT :toId, tag_id, source, created_at FROM account_tags WHERE account_id = :fromId",
    )
    suspend fun copyAssignments(fromId: Long, toId: Long)

    @Query("DELETE FROM account_tags WHERE account_id = :accountId")
    suspend fun deleteAssignmentsForAccount(accountId: Long)
}
