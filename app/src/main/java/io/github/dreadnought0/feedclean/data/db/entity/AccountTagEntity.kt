package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.github.dreadnought0.feedclean.data.model.TagSource

/** `account_tags` join table; PK(account_id, tag_id, source). */
@Entity(
    tableName = "account_tags",
    primaryKeys = ["account_id", "tag_id", "source"],
    indices = [Index(value = ["tag_id"])],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AccountTagEntity(
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    val source: TagSource,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
