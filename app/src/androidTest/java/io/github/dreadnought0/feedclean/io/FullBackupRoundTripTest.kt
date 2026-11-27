package io.github.dreadnought0.feedclean.io

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.DataLayer
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.InMemorySettingsRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Full export → import on in-memory databases: the import reproduces the data, and is idempotent. */
@RunWith(AndroidJUnit4::class)
class FullBackupRoundTripTest {

    private val now = 1_795_341_600_000L
    private val t0 = now - 30L * 24 * 60 * 60 * 1000

    private lateinit var dbA: FeedCleanDatabase
    private lateinit var dbB: FeedCleanDatabase
    private lateinit var a: DataLayer
    private lateinit var b: DataLayer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbA = FeedCleanDatabase.createInMemory(context)
        dbB = FeedCleanDatabase.createInMemory(context)
        a = DataLayer(dbA, InMemorySettingsRepository())
        b = DataLayer(dbB, InMemorySettingsRepository())
    }

    @After
    fun tearDown() {
        dbA.close()
        dbB.close()
    }

    private suspend fun populate(data: DataLayer) {
        val shop = data.accountRepository.ensureHandleAccount("example_shop", HandleSource.PROFILE, "Example Shop", t0)
        val feed = data.accountRepository.upsertFromFeed("Feed Person", null, t0)
        val unknown = data.accountRepository.upsertFromFeed(null, null, t0)
        val copycat = data.accountRepository.ensureHandleAccount("example_shop_2", HandleSource.DIALOG, "Example Shop", t0)
        data.accountRepository.setSuspectedOriginal(copycat, shop)
        data.accountRepository.setNotes(shop, "private note")
        data.accountRepository.addTag(shop, "ai_generated", TagSource.MANUAL, t0)
        data.accountRepository.addTag(feed, "my custom tag", TagSource.MANUAL, t0)

        val session = data.sessionRepository.start(t0)
        data.sessionRepository.recordPage(session, Signals(isPaidAd = true), skipped = false)
        data.sessionRepository.end(session, t0 + 60_000)

        data.sightingRepository.recordSighting(shop, "sig-1", Signals(isPaidAd = true, disclosureLabel = "Ad"),
            SightingSource.OVERLAY, nowMillis = t0 + 1, sessionId = session)
        data.sightingRepository.recordSighting(feed, "sig-2", Signals(hasShopAnchor = true), SightingSource.OVERLAY,
            UserAction.AUTO_SKIP, SkipReason.ORGANIC_SHOP, nowMillis = t0 + 2)
        data.sightingRepository.recordSighting(unknown, "sig-3", Signals(), SightingSource.SHARE, UserAction.LOG,
            videoUrl = "https://vm.tiktok.com/ZMexample/", nowMillis = t0 + 3)

        data.blockEventRepository.record(shop, t0 + 4, BlockMethod.ASSISTED, verified = true)
        data.blockEventRepository.record(copycat, t0 + 5, BlockMethod.ASSISTED, verified = false, failureReason = "x", stepReached = "CHECK_DIALOG")
        data.actionLogRepository.record(ActionKind.BLOCK, t0 + 4, shop, verified = true)
        data.actionLogRepository.record(ActionKind.BLOCK, t0 + 5, copycat, verified = false)

        val audit = data.auditRepository.createAudit(t0 + 6, AuditSource.DATA_EXPORT, "export.zip")
        data.auditRepository.addFindings(audit, listOf(AuditFinding(accountId = shop, handle = "example_shop", kind = AuditFindingKind.MISSING_FROM_BLOCKLIST)))
        data.auditRepository.finishAudit(audit, 1, 0)
        data.accountRepository.setStatus(shop, AccountStatus.UNBLOCKED_SUSPECTED)
    }

    private suspend fun exportBytes(db: FeedCleanDatabase): ByteArray =
        ByteArrayOutputStream().also { FullBackup(db, "test", clock = { now }).export(it) }.toByteArray()

    /** Table contents without database ids (foreign keys replaced by stable keys). */
    private fun canonical(bundle: ExportBundle): Map<String, Set<String>> {
        val accountKey = bundle.accounts.associate { it.id to (it.handle ?: "name:${it.displayName}:${it.firstSeenAt}") }
        val tagName = bundle.tags.associate { it.id to it.name }
        val sessionKey = bundle.sessions.associate { it.id to it.startedAt }
        val auditKey = bundle.audits.associate { it.id to it.runAt }
        return mapOf(
            "accounts" to bundle.accounts.map { it.copy(id = 0, suspectedOriginalOf = null).toString() + accountKey[it.suspectedOriginalOf] }.toSet(),
            "tags" to bundle.tags.map { it.copy(id = 0).toString() }.toSet(),
            "account_tags" to bundle.accountTags.map { "${accountKey[it.accountId]}|${tagName[it.tagId]}|${it.source}|${it.createdAt}" }.toSet(),
            "sightings" to bundle.sightings.map {
                it.copy(id = 0, accountId = 0, sessionId = null).toString() + accountKey[it.accountId] + sessionKey[it.sessionId]
            }.toSet(),
            "block_events" to bundle.blockEvents.map { it.copy(id = 0, accountId = 0).toString() + accountKey[it.accountId] }.toSet(),
            "action_log" to bundle.actionLog.map { it.copy(id = 0, accountId = null).toString() + accountKey[it.accountId] }.toSet(),
            "sessions" to bundle.sessions.map { it.copy(id = 0).toString() }.toSet(),
            "audits" to bundle.audits.map { it.copy(id = 0).toString() }.toSet(),
            "audit_findings" to bundle.auditFindings.map {
                it.copy(id = 0, auditId = 0, accountId = null).toString() + accountKey[it.accountId] + auditKey[it.auditId]
            }.toSet(),
        )
    }

    @Test
    fun importReproducesTheDatabase() = runTest {
        populate(a)

        val report = FullBackup(dbB, "test", clock = { now }).import(ByteArrayInputStream(exportBytes(dbA)))

        assertEquals(4, report.accountsAdded)
        assertEquals(2, report.provisionalAccountsAdded)
        assertEquals(3, report.sightingsAdded)
        assertEquals(0, report.skipped.invalidRows)
        assertEquals(canonical(RoomSnapshotReader(dbA).read()), canonical(RoomSnapshotReader(dbB).read()))
    }

    @Test
    fun importingTwiceAddsNothing() = runTest {
        populate(a)
        val bytes = exportBytes(dbA)
        val backup = FullBackup(dbB, "test", clock = { now })

        backup.import(ByteArrayInputStream(bytes))
        val before = canonical(RoomSnapshotReader(dbB).read())
        val second = backup.import(ByteArrayInputStream(bytes))

        assertTrue(second.nothingNew)
        assertEquals(3, second.skipped.duplicateSightings)
        assertEquals(before, canonical(RoomSnapshotReader(dbB).read()))
    }

    @Test
    fun importMergesByHandleAndNeverAddsRecentRateLimitRows() = runTest {
        populate(a)
        a.actionLogRepository.record(ActionKind.NOT_INTERESTED, now - 60_000, null, verified = true)
        val existing = b.accountRepository.ensureHandleAccount("example_shop", HandleSource.RESOLVE, null, now)
        val countBefore = b.actionLogRepository.countSince(now - DataLayer.RATE_LIMIT_RETENTION_MILLIS)

        val report = FullBackup(dbB, "test", clock = { now }).import(ByteArrayInputStream(exportBytes(dbA)))

        assertEquals(1, report.accountsMatched)
        assertEquals(1, report.skipped.actionsInRateLimitWindow)
        assertEquals(countBefore, b.actionLogRepository.countSince(now - DataLayer.RATE_LIMIT_RETENTION_MILLIS))
        val merged = checkNotNull(b.accountRepository.findByHandle("example_shop"))
        assertEquals(existing, merged.id)
        assertEquals("Example Shop", merged.displayName)
        assertEquals("private note", merged.notes)
        assertEquals(HandleSource.RESOLVE, merged.handleSource)
    }

    @Test
    fun publicExportFromRepositories() = runTest {
        populate(a)
        val file = PublicExporter(a.accountRepository, "test", clock = { now }).build(includeSubjectiveTags = false)
        assertEquals(listOf("example_shop"), file.accounts.map { it.handle })
        assertEquals(listOf("paid_ad"), file.accounts.single().tags)
    }
}
