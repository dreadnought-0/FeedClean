package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.HandleSource

/** `accounts` table. SQLite UNIQUE allows many NULL handles, i.e. many provisional rows. */
@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["handle"], unique = true),
        Index(value = ["display_name_skeleton"]),
        Index(value = ["suspected_original_of"]),
        Index(value = ["last_seen_at"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["suspected_original_of"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handle: String?,
    @ColumnInfo(name = "handle_source") val handleSource: HandleSource?,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "display_name_skeleton") val displayNameSkeleton: String?,
    @ColumnInfo(name = "first_seen_at") val firstSeenAt: Long,
    @ColumnInfo(name = "last_seen_at") val lastSeenAt: Long,
    val status: AccountStatus = AccountStatus.SEEN,
    @ColumnInfo(name = "account_category") val accountCategory: String? = null,
    @ColumnInfo(name = "shop_tier") val shopTier: String? = null,
    @ColumnInfo(name = "is_default_handle") val isDefaultHandle: Boolean = false,
    val notes: String? = null,
    @ColumnInfo(name = "suspected_original_of") val suspectedOriginalOf: Long? = null,
    @ColumnInfo(name = "profile_photo_hash") val profilePhotoHash: Long? = null,
)
