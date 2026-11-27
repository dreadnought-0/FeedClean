package io.github.dreadnought0.feedclean.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.detect.Signals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditRepositoryTest : DbTest() {

    @Test
    fun blockedAccountsWithHandlesPairsLatestVerifiedBlock() = runTest {
        val blocked = accounts.ensureHandleAccount("blocked.one", HandleSource.PROFILE, "Blocked", 0)
        blockEvents.record(blocked, 100, BlockMethod.ASSISTED, verified = true)
        blockEvents.record(blocked, 200, BlockMethod.ASSISTED, verified = true)
        blockEvents.record(blocked, 300, BlockMethod.ASSISTED, verified = false)

        val failed = accounts.ensureHandleAccount("failed.one", HandleSource.PROFILE, "Failed", 0)
        blockEvents.record(failed, 100, BlockMethod.ASSISTED, verified = false)

        val provisional = accounts.upsertFromFeed("No Handle", null, 0)
        blockEvents.record(provisional, 100, BlockMethod.MANUAL, verified = true)

        val unblocked = accounts.ensureHandleAccount("again", HandleSource.IMPORT, null, 0)
        blockEvents.record(unblocked, 50, BlockMethod.IMPORTED, verified = true)
        accounts.setStatus(unblocked, AccountStatus.UNBLOCKED_SUSPECTED)

        val falsePositive = accounts.ensureHandleAccount("fp", HandleSource.PROFILE, null, 0)
        blockEvents.record(falsePositive, 60, BlockMethod.ASSISTED, verified = true)
        accounts.setStatus(falsePositive, AccountStatus.FALSE_POSITIVE)

        val result = audits.blockedAccountsWithHandles()
        assertEquals(listOf("again", "blocked.one"), result.map { it.first.handle })
        assertEquals(200L, result.single { it.first.id == blocked }.second.attemptedAt)
        assertEquals(BlockMethod.IMPORTED, result.single { it.first.id == unblocked }.second.method)
    }

    @Test
    fun sightingsAfterIsStrictAndOrdered() = runTest {
        val a = accounts.ensureHandleAccount("someone", HandleSource.PROFILE, "Someone", 0)
        listOf(150L, 300L, 250L, 200L).forEachIndexed { i, t ->
            sightings.recordSighting(a, "p$i", Signals(isPaidAd = true), SightingSource.OVERLAY, nowMillis = t)
        }
        assertEquals(listOf(250L, 300L), audits.sightingsAfter(a, 200).map { it.seenAt })
    }

    @Test
    fun auditRunsAndFindings() = runTest {
        val a = accounts.ensureHandleAccount("blocked.one", HandleSource.PROFILE, "Blocked", 0)
        val older = audits.createAudit(10, AuditSource.MANUAL, null)
        val id = audits.createAudit(1_000, AuditSource.DATA_EXPORT, "user_data.json")
        audits.addFindings(
            id,
            listOf(
                AuditFinding(id = 99, auditId = 12345, accountId = a, handle = "@Blocked.One", kind = AuditFindingKind.MISSING_FROM_BLOCKLIST),
                AuditFinding(accountId = null, handle = "stranger", kind = AuditFindingKind.IN_EXPORT_NOT_IN_DB, detail = "2026-01"),
            ),
        )
        audits.addFindings(id, emptyList())
        audits.finishAudit(id, missingCount = 1, seenAfterCount = 0)

        assertEquals(listOf(id, older), audits.observeAudits().first().map { it.id })
        val audit = checkNotNull(audits.observeAudit(id).first())
        assertEquals("user_data.json", audit.exportFileName)
        assertEquals(1, audit.missingFromBlocklistCount)
        assertEquals(0, audit.seenAfterBlockCount)

        val findings = audits.observeFindings(id).first()
        assertEquals(listOf("stranger", "blocked.one"), findings.map { it.handle })
        assertEquals(setOf(id), findings.map { it.auditId }.toSet())
        assertEquals(a, findings.single { it.handle == "blocked.one" }.accountId)
        assertEquals("2026-01", findings.single { it.handle == "stranger" }.detail)
        assertEquals(emptyList<AuditFinding>(), audits.observeFindings(older).first())
    }
}
