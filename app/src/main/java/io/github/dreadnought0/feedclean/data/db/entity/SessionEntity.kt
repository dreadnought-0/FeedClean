package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** `sessions` table (opt-in session tracking). */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["started_at"])],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "videos_seen") val videosSeen: Int = 0,
    val ads: Int = 0,
    val promoted: Int = 0,
    val shop: Int = 0,
    val live: Int = 0,
    val skipped: Int = 0,
)
