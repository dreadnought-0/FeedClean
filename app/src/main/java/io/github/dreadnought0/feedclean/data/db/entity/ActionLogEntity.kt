package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.ActionKind

/** `action_log` table: the RateLimiter's source of truth. Attempts count, verified or not. */
@Entity(
    tableName = "action_log",
    indices = [
        Index(value = ["at"]),
        Index(value = ["account_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            // Keep the row (and the rate-limit budget it represents) if the account is deleted.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: ActionKind,
    val at: Long,
    @ColumnInfo(name = "account_id") val accountId: Long?,
    val verified: Boolean,
)
