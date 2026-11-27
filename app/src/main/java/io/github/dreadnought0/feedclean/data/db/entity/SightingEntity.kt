package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason

/** `sightings` table. [signals] is stored as JSON in `signals_json`. */
@Entity(
    tableName = "sightings",
    indices = [
        Index(value = ["account_id", "seen_at"]),
        Index(value = ["page_signature", "seen_at"]),
        Index(value = ["seen_at"]),
        Index(value = ["session_id"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "seen_at") val seenAt: Long,
    @ColumnInfo(name = "video_url") val videoUrl: String?,
    @ColumnInfo(name = "page_signature") val pageSignature: String,
    @ColumnInfo(name = "signals_json") val signals: Signals,
    val source: SightingSource,
    @ColumnInfo(name = "user_action") val userAction: UserAction,
    @ColumnInfo(name = "action_verified") val actionVerified: Boolean?,
    @ColumnInfo(name = "skip_reason") val skipReason: SkipReason?,
    @ColumnInfo(name = "screenshot_path") val screenshotPath: String?,
    @ColumnInfo(name = "session_id") val sessionId: Long?,
)
