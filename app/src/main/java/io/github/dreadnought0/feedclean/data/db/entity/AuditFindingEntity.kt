package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind

/** `audit_findings` table. */
@Entity(
    tableName = "audit_findings",
    indices = [
        Index(value = ["audit_id"]),
        Index(value = ["account_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AuditEntity::class,
            parentColumns = ["id"],
            childColumns = ["audit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class AuditFindingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "audit_id") val auditId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    val handle: String,
    val kind: AuditFindingKind,
    val detail: String?,
)
