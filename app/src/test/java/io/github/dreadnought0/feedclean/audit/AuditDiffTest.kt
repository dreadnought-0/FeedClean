package io.github.dreadnought0.feedclean.audit

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind.IN_EXPORT_NOT_IN_DB
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind.MISSING_FROM_BLOCKLIST
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind.SEEN_AFTER_BLOCK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditDiffTest {

    private val day = 24 * 60 * 60 * 1000L
    private val t0 = 1_767_225_600_000L // 2026-01-01T00:00:00Z

    private fun ours(id: Long, handle: String, at: Long, status: AccountStatus = AccountStatus.BLOCKED, after: List<Long> = emptyList()) =
        OurBlock(id, handle, at, status, after)

    @Test
    fun classifiesEveryKind() {
        val result = AuditDiff.diff(
            ourBlocks = listOf(
                ours(1, "still_blocked", t0),
                ours(2, "gone_missing", t0 + day),
                ours(3, "seen_again", t0, after = listOf(t0 + 2 * day, t0 + 5 * day)),
            ),
            exported = listOf(
                ExportedBlock("still_blocked", t0),
                ExportedBlock("seen_again", t0),
                ExportedBlock("only_in_tiktok", t0 + 3 * day),
                ExportedBlock("known_here", null),
            ),
            known = listOf(KnownAccount(9, "known_here")),
        )

        assertEquals(
            listOf(
                MISSING_FROM_BLOCKLIST to "gone_missing",
                SEEN_AFTER_BLOCK to "seen_again",
                IN_EXPORT_NOT_IN_DB to "known_here",
                IN_EXPORT_NOT_IN_DB to "only_in_tiktok",
            ),
            result.findings.map { it.kind to it.handle },
        )
        assertEquals(setOf(2L), result.markUnblockedSuspected)
        assertEquals(emptySet<Long>(), result.markBlockedAgain)
        assertEquals(1, result.count(MISSING_FROM_BLOCKLIST))
        assertEquals(1, result.count(SEEN_AFTER_BLOCK))

        val seen = AuditDetail.parse(result.findings.single { it.kind == SEEN_AFTER_BLOCK }.detail)
        assertEquals(2, seen.sightingsAfter)
        assertEquals(t0 + 5 * day, seen.lastSeenAtMillis)
        assertEquals(t0, seen.blockedAtMillis)

        val known = result.findings.single { it.handle == "known_here" }
        assertEquals(9L, known.accountId)
        assertTrue(AuditDetail.parse(known.detail).knownAccount)
        val unknown = result.findings.single { it.handle == "only_in_tiktok" }
        assertNull(unknown.accountId)
        assertEquals(t0 + 3 * day, AuditDetail.parse(unknown.detail).exportDateMillis)
    }

    @Test
    fun blockNewerThanTheExportIsReportedButNotMarked() {
        val result = AuditDiff.diff(
            ourBlocks = listOf(ours(1, "after_export", t0 + 10 * day), ours(2, "before_export", t0)),
            exported = listOf(ExportedBlock("someone_else", t0 + 5 * day)),
            known = emptyList(),
        )
        val missing = result.findings.filter { it.kind == MISSING_FROM_BLOCKLIST }
        assertEquals(listOf("after_export", "before_export"), missing.map { it.handle })
        assertTrue(AuditDetail.parse(missing[0].detail).newerThanExport)
        assertFalse(AuditDetail.parse(missing[1].detail).newerThanExport)
        assertEquals(setOf(2L), result.markUnblockedSuspected)
    }

    @Test
    fun suspectedAccountBackInTheExportIsMarkedBlockedAgain() {
        val result = AuditDiff.diff(
            ourBlocks = listOf(
                ours(1, "back_again", t0, AccountStatus.UNBLOCKED_SUSPECTED),
                ours(2, "still_missing", t0, AccountStatus.UNBLOCKED_SUSPECTED),
            ),
            exported = listOf(ExportedBlock("back_again", t0)),
            known = emptyList(),
        )
        assertEquals(setOf(1L), result.markBlockedAgain)
        assertEquals("already suspected: no change", emptySet<Long>(), result.markUnblockedSuspected)
        assertEquals(listOf("still_missing"), result.findings.map { it.handle })
    }

    @Test
    fun emptyInputsGiveNoFindings() {
        val result = AuditDiff.diff(emptyList(), emptyList(), emptyList())
        assertEquals(emptyList<Any>(), result.findings)
        AuditFindingKind.entries.forEach { assertEquals(0, result.count(it)) }
    }

    @Test
    fun withoutExportDatesEveryMissingBlockIsMarked() {
        val result = AuditDiff.diff(
            ourBlocks = listOf(ours(1, "a_user", t0 + 100 * day)),
            exported = listOf(ExportedBlock("b_user", null)),
            known = emptyList(),
        )
        assertEquals(setOf(1L), result.markUnblockedSuspected)
    }

    @Test
    fun detailRoundTrips() {
        val detail = AuditDetail(
            blockedAtMillis = t0,
            exportDateMillis = t0 + day,
            sightingsAfter = 3,
            lastSeenAtMillis = t0 + 2 * day,
            newerThanExport = true,
            knownAccount = true,
        )
        val text = checkNotNull(detail.encode())
        assertTrue(text.startsWith("blocked_at=2026-01-01T00:00:00Z;"))
        assertEquals(detail, AuditDetail.parse(text))
        assertEquals(AuditDetail(), AuditDetail.parse(null))
        assertEquals(AuditDetail(sightingsAfter = 1), AuditDetail.parse("sightings_after=1;unknown=x;blocked_at=bad"))
        assertNull(AuditDetail().encode())
    }
}
