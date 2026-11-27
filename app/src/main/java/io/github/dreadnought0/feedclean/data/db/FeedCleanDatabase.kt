package io.github.dreadnought0.feedclean.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.dreadnought0.feedclean.data.db.dao.AccountDao
import io.github.dreadnought0.feedclean.data.db.dao.ActionLogDao
import io.github.dreadnought0.feedclean.data.db.dao.AuditDao
import io.github.dreadnought0.feedclean.data.db.dao.BlockEventDao
import io.github.dreadnought0.feedclean.data.db.dao.SessionDao
import io.github.dreadnought0.feedclean.data.db.dao.SightingDao
import io.github.dreadnought0.feedclean.data.db.dao.TagDao
import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.db.entity.AccountTagEntity
import io.github.dreadnought0.feedclean.data.db.entity.ActionLogEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditFindingEntity
import io.github.dreadnought0.feedclean.data.db.entity.BlockEventEntity
import io.github.dreadnought0.feedclean.data.db.entity.SessionEntity
import io.github.dreadnought0.feedclean.data.db.entity.SightingEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity

/**
 * FeedClean's single Room database, in app-private storage.
 *
 * Schema JSON is exported to `app/schemas/`. Bump [version] only with a migration and a migration test.
 */
@Database(
    entities = [
        AccountEntity::class,
        TagEntity::class,
        AccountTagEntity::class,
        SightingEntity::class,
        BlockEventEntity::class,
        ActionLogEntity::class,
        SessionEntity::class,
        AuditEntity::class,
        AuditFindingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FeedCleanDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun tagDao(): TagDao
    abstract fun sightingDao(): SightingDao
    abstract fun blockEventDao(): BlockEventDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun sessionDao(): SessionDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val NAME = "feedclean.db"

        /** Builds the on-disk database. Call once per process (the [io.github.dreadnought0.feedclean.data.DataLayer] does). */
        fun create(context: Context): FeedCleanDatabase =
            Room.databaseBuilder(context.applicationContext, FeedCleanDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .build()

        /** Builds an in-memory database with the same seed data, for tests. */
        fun createInMemory(context: Context): FeedCleanDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, FeedCleanDatabase::class.java)
                .addCallback(SeedCallback)
                .build()
    }
}
