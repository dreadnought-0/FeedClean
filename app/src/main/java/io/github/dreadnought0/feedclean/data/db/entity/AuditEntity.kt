package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.model.AuditSource

/** `audits` table (unblock audit runs). */
@Entity(
    tableName = "audits",
    indices = [Index(value = ["run_at"])],
)
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "run_at") val runAt: Long,
    val source: AuditSource,
    @ColumnInfo(name = "export_file_name") val exportFileName: String?,
    @ColumnInfo(name = "missing_from_blocklist_count") val missingFromBlocklistCount: Int = 0,
    @ColumnInfo(name = "seen_after_block_count") val seenAfterBlockCount: Int = 0,
)
