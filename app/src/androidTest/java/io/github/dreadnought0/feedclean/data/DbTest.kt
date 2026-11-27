package io.github.dreadnought0.feedclean.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import org.junit.After
import org.junit.Before

/** Base for data-layer instrumented tests: a fresh in-memory database (with seed tags) per test. */
abstract class DbTest {
    protected lateinit var db: FeedCleanDatabase
    protected lateinit var data: DataLayer

    protected val accounts: AccountRepository get() = data.accountRepository
    protected val sightings: SightingRepository get() = data.sightingRepository
    protected val blockEvents: BlockEventRepository get() = data.blockEventRepository
    protected val actionLog: ActionLogRepository get() = data.actionLogRepository
    protected val sessions: SessionRepository get() = data.sessionRepository
    protected val stats: StatsRepository get() = data.statsRepository
    protected val audits: AuditRepository get() = data.auditRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = FeedCleanDatabase.createInMemory(context)
        data = DataLayer(db, InMemorySettingsRepository())
    }

    @After
    fun closeDb() {
        db.close()
    }

    /** Reads one string column of the first row of [sql], bypassing Room's converters. */
    protected fun rawString(sql: String): String? =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
