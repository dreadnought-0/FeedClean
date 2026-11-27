package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.model.BlockMethod

/** `block_events` table: every block attempt, verified or not. */
@Entity(
    tableName = "block_events",
    indices = [
        Index(value = ["account_id", "attempted_at"]),
        Index(value = ["attempted_at"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "attempted_at") val attemptedAt: Long,
    val method: BlockMethod,
    val verified: Boolean,
    @ColumnInfo(name = "failure_reason") val failureReason: String?,
    @ColumnInfo(name = "step_reached") val stepReached: String?,
)
