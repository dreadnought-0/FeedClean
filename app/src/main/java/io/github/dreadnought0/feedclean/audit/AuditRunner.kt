package io.github.dreadnought0.feedclean.audit

import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.AuditRepository
import io.github.dreadnought0.feedclean.data.BlockEventRepository
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource

/** Result of [AuditRunner.run]. */
data class AuditRunResult(
    val auditId: Long,
    /** Handles in the export's block list. */
    val exportedCount: Int,
    val diff: AuditDiffResult,
)

/**
 * Runs the unblock audit against the database and stores it. Database only: it never acts on
 * TikTok. Status changes it makes (UNBLOCKED_SUSPECTED, or back to BLOCKED) are labels in FeedClean.
 */
class AuditRunner(
    private val accounts: AccountRepository,
    private val audits: AuditRepository,
    private val blockEvents: BlockEventRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Diffs [scan] (from [DataExportReader]) against our blocks, stores the audit and its findings. */
    suspend fun run(scan: BlockListScan, exportFileName: String?): AuditRunResult {
        val ourBlocks = audits.blockedAccountsWithHandles().mapNotNull { (account, event) ->
            val handle = account.handle ?: return@mapNotNull null
            OurBlock(
                accountId = account.id,
                handle = handle,
                blockedAtMillis = event.attemptedAt,
                status = account.status,
                sightingsAfter = audits.sightingsAfter(account.id, event.attemptedAt).map { it.seenAt },
            )
        }
        val ourHandles = ourBlocks.map { it.handle }.toSet()
        val known = scan.entries.filter { it.handle !in ourHandles }.mapNotNull { entry ->
            accounts.findByHandle(entry.handle)?.let { KnownAccount(it.id, entry.handle) }
        }
        val diff = AuditDiff.diff(ourBlocks, scan.entries, known)

        val auditId = audits.createAudit(clock(), AuditSource.DATA_EXPORT, exportFileName)
        audits.addFindings(auditId, diff.findings)
        audits.finishAudit(
            auditId,
            missingCount = diff.count(AuditFindingKind.MISSING_FROM_BLOCKLIST),
            seenAfterCount = diff.count(AuditFindingKind.SEEN_AFTER_BLOCK),
        )
        diff.markUnblockedSuspected.forEach { accounts.setStatus(it, AccountStatus.UNBLOCKED_SUSPECTED) }
        diff.markBlockedAgain.forEach { accounts.setStatus(it, AccountStatus.BLOCKED) }
        return AuditRunResult(auditId, scan.entries.size, diff)
    }

    /**
     * Imports IN_EXPORT_NOT_IN_DB [findings] after the user confirmed: each handle gets an account (if new)
     * and a verified block event with method IMPORTED, dated from the export when it had a date. Accounts that
     * already have a verified block are only marked BLOCKED. Database only; idempotent. Returns how many
     * accounts were recorded as blocked.
     */
    suspend fun importMissing(findings: List<AuditFinding>): Int {
        var imported = 0
        findings.filter { it.kind == AuditFindingKind.IN_EXPORT_NOT_IN_DB }.distinctBy { it.handle }.forEach { finding ->
            val now = clock()
            val accountId = accounts.ensureHandleAccount(finding.handle, HandleSource.IMPORT, null, now)
            if (blockEvents.latestVerifiedBlock(accountId) != null) {
                if (accounts.getAccount(accountId)?.status == AccountStatus.BLOCKED) return@forEach
                accounts.setStatus(accountId, AccountStatus.BLOCKED)
            } else {
                val at = AuditDetail.parse(finding.detail).exportDateMillis ?: now
                blockEvents.record(accountId, at, BlockMethod.IMPORTED, verified = true)
            }
            imported++
        }
        return imported
    }
}
