package io.github.dreadnought0.feedclean.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity
import io.github.dreadnought0.feedclean.data.model.TagNames

/** Seeds the default tags ([TagNames.DEFAULTS]) when the database file is first created. Idempotent. */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        TagNames.DEFAULTS.forEach { (name, kind) ->
            db.execSQL("INSERT OR IGNORE INTO tags (name, kind) VALUES (?, ?)", arrayOf(name, kind.name))
        }
    }

    /** The default tags as entities, for re-seeding through the DAO (e.g. after "Clear all data"). */
    val defaultTagEntities: List<TagEntity>
        get() = TagNames.DEFAULTS.map { (name, kind) -> TagEntity(name = name, kind = kind) }
}
