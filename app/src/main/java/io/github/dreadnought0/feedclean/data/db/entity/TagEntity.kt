package io.github.dreadnought0.feedclean.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.dreadnought0.feedclean.data.model.TagKind

/** `tags` table. Default tags are seeded on creation (see `SeedCallback`). */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: TagKind,
)
