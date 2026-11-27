package io.github.dreadnought0.feedclean.audit

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.DbTest
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
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
class AuditRunnerTest : DbTest() {

    private val now = 1_795_341_600_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun runStoresFindingsMarksStatusesAndImportsOnlyOnRequest() = runTest {
        val kept = accounts.ensureHandleAccount("kept_block", HandleSource.PROFILE, null, now - 30 * day)
        blockEvents.record(kept, now - 20 * day, BlockMethod.ASSISTED, verified = true)
        val gone = accounts.ensureHandleAccount("gone_block", HandleSource.PROFILE, null, now - 30 * day)
        blockEvents.record(gone, now - 20 * day, BlockMethod.ASSISTED, verified = true)
        sightings.recordSighting(gone, "after", Signals(isPaidAd = true), SightingSource.OVERLAY, nowMillis = now - 2 * day)

        val runner = AuditRunner(accounts, audits, blockEvents, clock = { now })
        val scan = BlockListScan(
            found = true,
            entries = listOf(ExportedBlock("kept_block", now - 20 * day), ExportedBlock("tiktok_only", now - 10 * day)),
            skippedEntries = 0,
        )

        val result = runner.run(scan, "export.zip")

        val stored = audits.observeFindings(result.auditId).first().map { it.kind to it.handle }.toSet()
        assertEquals(
            setOf(
                AuditFindingKind.MISSING_FROM_BLOCKLIST to "gone_block",
                AuditFindingKind.SEEN_AFTER_BLOCK to "gone_block",
                AuditFindingKind.IN_EXPORT_NOT_IN_DB to "tiktok_only",
            ),
            stored,
        )
        val audit = checkNotNull(audits.observeAudit(result.auditId).first())
        assertEquals(1, audit.missingFromBlocklistCount)
        assertEquals(1, audit.seenAfterBlockCount)
        assertEquals(AccountStatus.UNBLOCKED_SUSPECTED, accounts.getAccount(gone)?.status)
        assertEquals(AccountStatus.BLOCKED, accounts.getAccount(kept)?.status)
        assertEquals("nothing imported before the user confirms", null, accounts.findByHandle("tiktok_only"))

        val findings = audits.observeFindings(result.auditId).first()
        assertEquals(1, runner.importMissing(findings))
        val imported = checkNotNull(accounts.findByHandle("tiktok_only"))
        assertEquals(AccountStatus.BLOCKED, imported.status)
        val event = checkNotNull(blockEvents.latestVerifiedBlock(imported.id))
        assertEquals(BlockMethod.IMPORTED, event.method)
        assertEquals(now - 10 * day, event.attemptedAt)
        assertEquals("idempotent", 0, runner.importMissing(findings))
    }
}
