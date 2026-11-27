package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.db.entity.ActionLogEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditFindingEntity
import io.github.dreadnought0.feedclean.data.db.entity.BlockEventEntity
import io.github.dreadnought0.feedclean.data.db.entity.SessionEntity
import io.github.dreadnought0.feedclean.data.db.entity.SightingEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity
import kotlinx.coroutines.flow.first

/**
 * Reads every table of the database through its DAOs (read-only) into an [ExportBundle], sorted by id.
 *
 * Child tables are read before `accounts`, so a row written by the service while the snapshot is taken
 * normally finds its account in the snapshot. Rows that still end up orphaned are skipped by the importer.
 */
class RoomSnapshotReader(private val db: FeedCleanDatabase) {

    /** Takes the snapshot. */
    suspend fun read(): ExportBundle {
        val sightings = db.sightingDao().observeRecent(Int.MAX_VALUE).first().map { it.toRow() }
        val sessions = db.sessionDao().observeRecent(Int.MAX_VALUE).first().map { it.toRow() }
        val actionLog = db.actionLogDao().entriesSince(Long.MIN_VALUE).map { it.toRow() }
        val auditEntities = db.auditDao().observeAudits().first()
        val findings = auditEntities.flatMap { db.auditDao().observeFindings(it.id).first() }.map { it.toRow() }
        val tags = db.tagDao().observeAll().first().map { it.toRow() }
        val accounts = db.accountDao()
            .observeSummaries(null, null, null, emptyList(), 0, emptyList(), 0)
            .first()
            .map { it.account }
        val accountTags = accounts.flatMap { account ->
            db.tagDao().observeForAccount(account.id).first().map {
                AccountTagRow(account.id, it.tag.id, it.source.name, it.createdAt)
            }
        }
        val blockEvents = accounts.flatMap { db.blockEventDao().observeForAccount(it.id).first() }.map { it.toRow() }

        return ExportBundle(
            accounts = accounts.map { it.toRow() }.sortedBy { it.id },
            tags = tags.sortedBy { it.id },
            accountTags = accountTags.sortedWith(compareBy({ it.accountId }, { it.tagId }, { it.source })),
            sightings = sightings.sortedBy { it.id },
            blockEvents = blockEvents.sortedBy { it.id },
            actionLog = actionLog.sortedBy { it.id },
            sessions = sessions.sortedBy { it.id },
            audits = auditEntities.map { it.toRow() }.sortedBy { it.id },
            auditFindings = findings.sortedBy { it.id },
        )
    }
}

internal fun AccountEntity.toRow() = AccountRow(
    id = id,
    handle = handle,
    handleSource = handleSource?.name,
    displayName = displayName,
    displayNameSkeleton = displayNameSkeleton,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    status = status.name,
    accountCategory = accountCategory,
    shopTier = shopTier,
    isDefaultHandle = isDefaultHandle,
    notes = notes,
    suspectedOriginalOf = suspectedOriginalOf,
    profilePhotoHash = profilePhotoHash,
)

internal fun TagEntity.toRow() = TagRow(id = id, name = name, kind = kind.name)

internal fun SightingEntity.toRow() = SightingRow(
    id = id,
    accountId = accountId,
    seenAt = seenAt,
    videoUrl = videoUrl,
    pageSignature = pageSignature,
    signals = signals,
    source = source.name,
    userAction = userAction.name,
    actionVerified = actionVerified,
    skipReason = skipReason?.name,
    screenshotPath = screenshotPath,
    sessionId = sessionId,
)

internal fun BlockEventEntity.toRow() = BlockEventRow(
    id = id,
    accountId = accountId,
    attemptedAt = attemptedAt,
    method = method.name,
    verified = verified,
    failureReason = failureReason,
    stepReached = stepReached,
)

internal fun ActionLogEntity.toRow() = ActionLogRow(
    id = id,
    kind = kind.name,
    at = at,
    accountId = accountId,
    verified = verified,
)

internal fun SessionEntity.toRow() = SessionRow(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    videosSeen = videosSeen,
    ads = ads,
    promoted = promoted,
    shop = shop,
    live = live,
    skipped = skipped,
)

internal fun AuditEntity.toRow() = AuditRow(
    id = id,
    runAt = runAt,
    source = source.name,
    exportFileName = exportFileName,
    missingFromBlocklistCount = missingFromBlocklistCount,
    seenAfterBlockCount = seenAfterBlockCount,
)

internal fun AuditFindingEntity.toRow() = AuditFindingRow(
    id = id,
    auditId = auditId,
    accountId = accountId,
    handle = handle,
    kind = kind.name,
    detail = detail,
)
