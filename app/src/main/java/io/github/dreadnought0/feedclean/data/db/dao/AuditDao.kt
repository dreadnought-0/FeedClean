package io.github.dreadnought0.feedclean.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.dreadnought0.feedclean.data.db.entity.AuditEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditFindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert
    suspend fun insertAudit(audit: AuditEntity): Long

    @Insert
    suspend fun insertFindings(findings: List<AuditFindingEntity>)

    @Query(
        "UPDATE audits SET missing_from_blocklist_count = :missingCount, " +
            "seen_after_block_count = :seenAfterCount WHERE id = :id",
    )
    suspend fun finish(id: Long, missingCount: Int, seenAfterCount: Int)

    @Query("SELECT * FROM audits ORDER BY run_at DESC, id DESC")
    fun observeAudits(): Flow<List<AuditEntity>>

    @Query("SELECT * FROM audits WHERE id = :id")
    fun observeAudit(id: Long): Flow<AuditEntity?>

    @Query("SELECT * FROM audit_findings WHERE audit_id = :auditId ORDER BY kind, handle, id")
    fun observeFindings(auditId: Long): Flow<List<AuditFindingEntity>>

    @Query("UPDATE audit_findings SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassignFindings(fromId: Long, toId: Long)
}
