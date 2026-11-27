package io.github.dreadnought0.feedclean.data.db

import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditFindingEntity
import io.github.dreadnought0.feedclean.data.db.entity.BlockEventEntity
import io.github.dreadnought0.feedclean.data.db.entity.SessionEntity
import io.github.dreadnought0.feedclean.data.db.entity.SightingEntity
import io.github.dreadnought0.feedclean.data.db.entity.TagEntity
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.Session
import io.github.dreadnought0.feedclean.data.model.Sighting
import io.github.dreadnought0.feedclean.data.model.Tag

/** Entity → domain model mappers. Entities never leave the data layer. */
internal fun AccountEntity.toModel() = Account(
    id = id,
    handle = handle,
    handleSource = handleSource,
    displayName = displayName,
    displayNameSkeleton = displayNameSkeleton,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    status = status,
    accountCategory = accountCategory,
    shopTier = shopTier,
    isDefaultHandle = isDefaultHandle,
    notes = notes,
    suspectedOriginalOf = suspectedOriginalOf,
    profilePhotoHash = profilePhotoHash,
)

internal fun SightingEntity.toModel() = Sighting(
    id = id,
    accountId = accountId,
    seenAt = seenAt,
    videoUrl = videoUrl,
    pageSignature = pageSignature,
    signals = signals,
    source = source,
    userAction = userAction,
    actionVerified = actionVerified,
    skipReason = skipReason,
    screenshotPath = screenshotPath,
    sessionId = sessionId,
)

internal fun BlockEventEntity.toModel() = BlockEvent(
    id = id,
    accountId = accountId,
    attemptedAt = attemptedAt,
    method = method,
    verified = verified,
    failureReason = failureReason,
    stepReached = stepReached,
)

internal fun TagEntity.toModel() = Tag(id = id, name = name, kind = kind)

internal fun SessionEntity.toModel() = Session(
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

internal fun AuditEntity.toModel() = Audit(
    id = id,
    runAt = runAt,
    source = source,
    exportFileName = exportFileName,
    missingFromBlocklistCount = missingFromBlocklistCount,
    seenAfterBlockCount = seenAfterBlockCount,
)

internal fun AuditFindingEntity.toModel() = AuditFinding(
    id = id,
    auditId = auditId,
    accountId = accountId,
    handle = handle,
    kind = kind,
    detail = detail,
)
