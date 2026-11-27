package io.github.dreadnought0.feedclean.audit

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind

/** One account FeedClean recorded as blocked (verified), with what was seen after the block. */
data class OurBlock(
    val accountId: Long,
    /** Normalized handle. */
    val handle: String,
    /** Time of the latest verified block event. */
    val blockedAtMillis: Long,
    val status: AccountStatus,
    /** Seen-at times of sightings strictly after [blockedAtMillis]. */
    val sightingsAfter: List<Long>,
)

/** An account FeedClean knows by handle (used to tell "unknown" from "known but not recorded as blocked"). */
data class KnownAccount(val accountId: Long, val handle: String)

/** Output of [AuditDiff.diff]. */
data class AuditDiffResult(
    /** Sorted by kind, then handle. Ids are 0 (assigned when stored). */
    val findings: List<AuditFinding>,
    /** Accounts to mark UNBLOCKED_SUSPECTED (missing from the export, and the export is not older than the block). */
    val markUnblockedSuspected: Set<Long>,
    /** Accounts marked UNBLOCKED_SUSPECTED earlier that are back in the export: mark BLOCKED again. */
    val markBlockedAgain: Set<Long>,
) {
    fun count(kind: AuditFindingKind): Int = findings.count { it.kind == kind }
}

/**
 * The unblock-audit diff. Pure.
 *
 * - **MISSING_FROM_BLOCKLIST**: a verified block of ours whose handle is not in TikTok's exported block list.
 *   The account is marked UNBLOCKED_SUSPECTED unless the block is newer than every dated entry of the export
 *   (then the export may simply predate it; the finding says so with `newer_than_export`).
 * - **SEEN_AFTER_BLOCK**: one of our blocked accounts has sightings after the block time (report only).
 * - **IN_EXPORT_NOT_IN_DB**: a handle in TikTok's block list that FeedClean has no verified block for. The
 *   user may import these (see [AuditRunner.importMissing]); the diff itself changes nothing.
 */
object AuditDiff {

    /**
     * @param ourBlocks from `AuditRepository.blockedAccountsWithHandles` and `sightingsAfter`.
     * @param exported the export's block list.
     * @param known accounts FeedClean has for handles of [exported] (any status).
     */
    fun diff(ourBlocks: List<OurBlock>, exported: List<ExportedBlock>, known: List<KnownAccount>): AuditDiffResult {
        val exportedByHandle = exported.associateBy { it.handle }
        val newestExportDate = exported.mapNotNull { it.blockedAtMillis }.maxOrNull()
        val knownByHandle = known.associateBy { it.handle }
        val findings = ArrayList<AuditFinding>()
        val suspected = LinkedHashSet<Long>()
        val restored = LinkedHashSet<Long>()

        ourBlocks.sortedBy { it.handle }.forEach { block ->
            if (block.handle !in exportedByHandle) {
                val newer = newestExportDate != null && block.blockedAtMillis > newestExportDate
                findings += AuditFinding(
                    accountId = block.accountId,
                    handle = block.handle,
                    kind = AuditFindingKind.MISSING_FROM_BLOCKLIST,
                    detail = AuditDetail(blockedAtMillis = block.blockedAtMillis, newerThanExport = newer).encode(),
                )
                if (!newer && block.status != AccountStatus.UNBLOCKED_SUSPECTED) suspected += block.accountId
            } else if (block.status == AccountStatus.UNBLOCKED_SUSPECTED) {
                restored += block.accountId
            }
            if (block.sightingsAfter.isNotEmpty()) {
                findings += AuditFinding(
                    accountId = block.accountId,
                    handle = block.handle,
                    kind = AuditFindingKind.SEEN_AFTER_BLOCK,
                    detail = AuditDetail(
                        blockedAtMillis = block.blockedAtMillis,
                        sightingsAfter = block.sightingsAfter.size,
                        lastSeenAtMillis = block.sightingsAfter.max(),
                    ).encode(),
                )
            }
        }

        val ourHandles = ourBlocks.map { it.handle }.toSet()
        exported.sortedBy { it.handle }.filter { it.handle !in ourHandles }.forEach { entry ->
            val account = knownByHandle[entry.handle]
            findings += AuditFinding(
                accountId = account?.accountId,
                handle = entry.handle,
                kind = AuditFindingKind.IN_EXPORT_NOT_IN_DB,
                detail = AuditDetail(exportDateMillis = entry.blockedAtMillis, knownAccount = account != null).encode(),
            )
        }

        return AuditDiffResult(
            findings = findings.sortedWith(compareBy({ it.kind.ordinal }, { it.handle })),
            markUnblockedSuspected = suspected,
            markBlockedAgain = restored,
        )
    }
}
