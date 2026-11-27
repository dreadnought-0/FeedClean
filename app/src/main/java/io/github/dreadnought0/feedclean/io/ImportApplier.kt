package io.github.dreadnought0.feedclean.io

import androidx.room.withTransaction
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.AccountTagEntity
import io.github.dreadnought0.feedclean.data.db.entity.ActionLogEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditFindingEntity
import io.github.dreadnought0.feedclean.data.db.entity.BlockEventEntity
import io.github.dreadnought0.feedclean.data.db.entity.SessionEntity
import io.github.dreadnought0.feedclean.data.db.entity.SightingEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity

/**
 * Writes an [ImportPlan] to the database in **one transaction** (all or nothing). Database writes only:
 * importing never triggers anything on screen and never touches TikTok.
 */
class ImportApplier(private val db: FeedCleanDatabase) {

    /** Applies [plan]; [manifest] is copied into the report. */
    suspend fun apply(plan: ImportPlan, manifest: ExportManifest): ImportReport = db.withTransaction {
        val accountDao = db.accountDao()
        val tagDao = db.tagDao()
        val newAccountIds = HashMap<Long, Long>()
        val existingRemap = HashMap<Long, Long>()
        var accountsAdded = 0
        var provisionalAdded = 0
        var accountsMatched = 0

        suspend fun insertAccount(row: AccountRow): Long {
            accountsAdded++
            if (row.handle == null) provisionalAdded++
            return accountDao.insert(ImportPlanner.newAccountEntity(row))
        }

        // Accounts: merges into existing rows (re-read inside the transaction), then inserts.
        plan.accountMerges.forEach { (existingId, row) ->
            val current = accountDao.byId(existingId)
            if (current == null) {
                existingRemap[existingId] = insertAccount(row)
            } else {
                accountDao.update(ImportPlanner.mergeImportedAccount(current, row))
                accountsMatched++
            }
        }
        plan.newAccounts.forEach { row ->
            // A handle may have appeared since the snapshot (the service runs concurrently): merge then.
            val sameHandle = row.handle?.let { accountDao.byHandle(it) }
            newAccountIds[row.id] = if (sameHandle != null) {
                accountDao.update(ImportPlanner.mergeImportedAccount(sameHandle, row))
                accountsMatched++
                sameHandle.id
            } else {
                insertAccount(row)
            }
        }

        fun accountId(ref: Ref): Long? = when (ref) {
            is Ref.Existing -> existingRemap[ref.id] ?: ref.id
            is Ref.New -> newAccountIds[ref.incomingId]
        }

        plan.suspectedOriginals.forEach { (accountRef, originalRef) ->
            val id = accountId(accountRef) ?: return@forEach
            val original = accountId(originalRef) ?: return@forEach
            if (id != original && accountDao.byId(id)?.suspectedOriginalOf == null) {
                accountDao.setSuspectedOriginal(id, original)
            }
        }

        // Tags and assignments.
        var tagsAdded = 0
        plan.newTags.forEach { (name, kind) ->
            if (tagDao.insertTag(TagEntity(name = name, kind = kind)) > 0) tagsAdded++
        }
        val tagIds = HashMap<String, Long?>()
        var assignmentsAdded = 0
        plan.tagAssignments.forEach { a ->
            val account = accountId(a.account) ?: return@forEach
            val tagId = tagIds.getOrPut(a.tagName) { tagDao.byName(a.tagName)?.id } ?: return@forEach
            tagDao.insertAssignment(AccountTagEntity(account, tagId, a.source, a.createdAt))
            assignmentsAdded++
        }

        // Sessions.
        val newSessionIds = HashMap<Long, Long>()
        plan.newSessions.forEach { s ->
            newSessionIds[s.id] = db.sessionDao().insert(
                SessionEntity(
                    startedAt = s.startedAt,
                    endedAt = s.endedAt,
                    videosSeen = s.videosSeen,
                    ads = s.ads,
                    promoted = s.promoted,
                    shop = s.shop,
                    live = s.live,
                    skipped = s.skipped,
                ),
            )
        }
        fun sessionId(ref: Ref?): Long? = when (ref) {
            null -> null
            is Ref.Existing -> ref.id
            is Ref.New -> newSessionIds[ref.incomingId]
        }

        // Sightings.
        var sightingsAdded = 0
        plan.sightings.forEach { p ->
            val account = accountId(p.account) ?: return@forEach
            db.sightingDao().insert(
                SightingEntity(
                    accountId = account,
                    seenAt = p.row.seenAt,
                    videoUrl = p.row.videoUrl,
                    pageSignature = p.row.pageSignature,
                    signals = p.row.signals,
                    source = p.source,
                    userAction = p.userAction,
                    actionVerified = p.row.actionVerified,
                    skipReason = p.skipReason,
                    screenshotPath = p.row.screenshotPath,
                    sessionId = sessionId(p.session),
                ),
            )
            sightingsAdded++
        }

        // Block events (history: account status comes from the merged account row, not from these).
        var blocksAdded = 0
        plan.blockEvents.forEach { p ->
            val account = accountId(p.account) ?: return@forEach
            db.blockEventDao().insert(
                BlockEventEntity(
                    accountId = account,
                    attemptedAt = p.row.attemptedAt,
                    method = p.method,
                    verified = p.row.verified,
                    failureReason = p.row.failureReason,
                    stepReached = p.row.stepReached,
                ),
            )
            blocksAdded++
        }

        // Action log: only rows older than the rate-limit window are in the plan.
        val actions = plan.actionLog.map { p ->
            ActionLogEntity(kind = p.kind, at = p.row.at, accountId = p.account?.let(::accountId), verified = p.row.verified)
        }
        if (actions.isNotEmpty()) db.actionLogDao().insertAll(actions)

        // Audits and findings.
        var findingsAdded = 0
        plan.audits.forEach { p ->
            val auditId = db.auditDao().insertAudit(
                AuditEntity(
                    runAt = p.row.runAt,
                    source = p.source,
                    exportFileName = p.row.exportFileName,
                    missingFromBlocklistCount = p.row.missingFromBlocklistCount,
                    seenAfterBlockCount = p.row.seenAfterBlockCount,
                ),
            )
            val findings = p.findings.map { f ->
                AuditFindingEntity(
                    auditId = auditId,
                    accountId = f.account?.let(::accountId),
                    handle = f.handle,
                    kind = f.kind,
                    detail = f.row.detail,
                )
            }
            if (findings.isNotEmpty()) db.auditDao().insertFindings(findings)
            findingsAdded += findings.size
        }

        ImportReport(
            exportAppVersion = manifest.appVersion,
            exportedAt = manifest.exportedAt,
            accountsAdded = accountsAdded,
            provisionalAccountsAdded = provisionalAdded,
            accountsMatched = accountsMatched,
            tagsAdded = tagsAdded,
            tagAssignmentsAdded = assignmentsAdded,
            sightingsAdded = sightingsAdded,
            blockEventsAdded = blocksAdded,
            actionLogAdded = actions.size,
            sessionsAdded = newSessionIds.size,
            auditsAdded = plan.audits.size,
            auditFindingsAdded = findingsAdded,
            skipped = plan.skipped,
        )
    }
}
