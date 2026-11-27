package io.github.dreadnought0.feedclean.data.room

import androidx.room.withTransaction
import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.db.toModel
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AccountSummary
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.Handles
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room-backed [AccountRepository].
 *
 * @param skeletonOf display-name skeleton function (production: the detector's NameNormalizer skeleton).
 *   A blank skeleton falls back to the trimmed, lowercased name.
 * @param clock time source for writes whose API has no explicit timestamp.
 */
class RoomAccountRepository(
    private val db: FeedCleanDatabase,
    private val skeletonOf: (String) -> String = DEFAULT_SKELETON,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountRepository {
    private val accountDao = db.accountDao()
    private val tagDao = db.tagDao()
    private val sightingDao = db.sightingDao()
    private val blockEventDao = db.blockEventDao()
    private val actionLogDao = db.actionLogDao()
    private val auditDao = db.auditDao()
    private val tagWriter = TagWriter(tagDao)

    override fun observeAccounts(
        query: String,
        statuses: Set<AccountStatus>,
        tags: Set<String>,
    ): Flow<List<AccountSummary>> {
        val q = query.trim()
        val tagNames = tags.map(TagNames::normalize).filter { it.isNotEmpty() }.distinct()
        val summaries = accountDao.observeSummaries(
            handlePattern = if (q.isEmpty()) null else LikePatterns.contains(Handles.normalize(q) ?: q),
            namePattern = if (q.isEmpty()) null else LikePatterns.contains(q),
            skeletonPattern = if (q.isEmpty()) null else LikePatterns.contains(skeletonKey(q)),
            statuses = statuses.map { it.name },
            statusCount = statuses.size,
            tags = tagNames,
            tagCount = tagNames.size,
        )
        return combine(summaries, tagDao.observeAllAccountTagNames()) { rows, tagRows ->
            val namesByAccount = tagRows.groupBy({ it.accountId }, { it.name })
            rows.map { row ->
                AccountSummary(
                    account = row.account.toModel(),
                    tagNames = namesByAccount[row.account.id].orEmpty().distinct().sorted(),
                    sightingCount = row.sightingCount,
                    lastSeenAt = row.account.lastSeenAt,
                    lastAction = row.lastAction,
                )
            }
        }
    }

    override fun observeAccount(id: Long): Flow<Account?> =
        accountDao.observeById(id).map { it?.toModel() }

    override fun observeTimeline(accountId: Long): Flow<List<TimelineEntry>> = combine(
        sightingDao.observeForAccount(accountId),
        blockEventDao.observeForAccount(accountId),
    ) { sightings, blocks ->
        (sightings.map { TimelineEntry.SightingEntry(it.toModel()) } +
            blocks.map { TimelineEntry.BlockEntry(it.toModel()) })
            .sortedByDescending { it.atMillis }
    }

    override suspend fun getAccount(id: Long): Account? = accountDao.byId(id)?.toModel()

    override suspend fun findByHandle(handle: String): Account? =
        Handles.normalize(handle)?.let { accountDao.byHandle(it)?.toModel() }

    override suspend fun findByDisplayName(displayName: String): List<Account> {
        val name = displayName.trim()
        if (name.isEmpty()) return emptyList()
        return accountDao.allBySkeleton(skeletonKey(name)).map { it.toModel() }
    }

    override suspend fun upsertFromFeed(displayName: String?, probableHandle: String?, nowMillis: Long): Long {
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        val probable = Handles.normalize(probableHandle)
        return db.withTransaction {
            val byHandle = probable?.let { accountDao.byHandle(it) }
            when {
                byHandle != null -> touch(byHandle.id, name, nowMillis)
                name == null -> {
                    val bucket = accountDao.unknownProvisional()
                    if (bucket != null) touch(bucket.id, null, nowMillis) else insertProvisional(null, nowMillis)
                }
                else -> {
                    val provisional = accountDao.provisionalBySkeleton(skeletonKey(name))
                    if (provisional != null) touch(provisional.id, null, nowMillis) else insertProvisional(name, nowMillis)
                }
            }
        }
    }

    override suspend fun ensureHandleAccount(
        handle: String,
        source: HandleSource,
        displayName: String?,
        nowMillis: Long,
    ): Long {
        val normalized = requireHandle(handle)
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        return db.withTransaction { ensureHandleAccountInTx(normalized, source, name, nowMillis) }
    }

    override suspend fun learnHandle(accountId: Long, handle: String, source: HandleSource): Long {
        val normalized = requireHandle(handle)
        return db.withTransaction {
            val src = accountDao.byId(accountId)
            val existing = accountDao.byHandle(normalized)
            when {
                src == null -> ensureHandleAccountInTx(normalized, source, null, clock())
                src.handle == normalized -> src.id
                // Never overwrite a confirmed handle, and never merge the shared unknown-creator bucket.
                src.handle != null || src.displayName == null ->
                    existing?.id ?: ensureHandleAccountInTx(normalized, source, null, clock())
                existing == null -> {
                    accountDao.update(
                        src.copy(
                            handle = normalized,
                            handleSource = source,
                            isDefaultHandle = Handles.isDefault(normalized),
                        ),
                    )
                    src.id
                }
                else -> {
                    mergeProvisionalInto(src, existing)
                    existing.id
                }
            }
        }
    }

    override suspend fun setStatus(accountId: Long, status: AccountStatus) =
        accountDao.setStatus(accountId, status)

    override suspend fun setNotes(accountId: Long, notes: String?) =
        accountDao.setNotes(accountId, notes?.takeIf { it.isNotBlank() })

    override suspend fun setAccountCategory(accountId: Long, category: String?) =
        accountDao.setAccountCategory(accountId, category?.trim()?.takeIf { it.isNotEmpty() })

    override suspend fun setSuspectedOriginal(accountId: Long, originalId: Long?) {
        if (originalId == accountId) return
        accountDao.setSuspectedOriginal(accountId, originalId)
    }

    override suspend fun addTag(accountId: Long, tagName: String, source: TagSource, nowMillis: Long) {
        db.withTransaction { tagWriter.assign(accountId, tagName, source, nowMillis) }
    }

    override suspend fun removeTag(accountId: Long, tagName: String, source: TagSource?) {
        db.withTransaction {
            val tag = tagDao.byName(TagNames.normalize(tagName)) ?: return@withTransaction
            if (source == null) {
                tagDao.deleteAssignmentsForTag(accountId, tag.id)
            } else {
                tagDao.deleteAssignment(accountId, tag.id, source)
            }
        }
    }

    override fun observeTags(accountId: Long): Flow<List<TagAssignment>> =
        tagDao.observeForAccount(accountId).map { rows ->
            rows.map { TagAssignment(tag = it.tag.toModel(), source = it.source, createdAt = it.createdAt) }
        }

    override fun observeAllTagNames(): Flow<List<String>> = tagDao.observeAllNames()

    override fun observeAllTags(): Flow<List<Tag>> = tagDao.observeAll().map { tags -> tags.map { it.toModel() } }

    // ── internals (call inside a transaction) ────────────────────────────────

    private suspend fun touch(id: Long, displayName: String?, nowMillis: Long): Long {
        accountDao.touchSeen(id, nowMillis)
        if (displayName != null) accountDao.fillDisplayNameIfMissing(id, displayName, skeletonKey(displayName))
        return id
    }

    private suspend fun insertProvisional(displayName: String?, nowMillis: Long): Long = accountDao.insert(
        AccountEntity(
            handle = null,
            handleSource = null,
            displayName = displayName,
            displayNameSkeleton = displayName?.let(::skeletonKey),
            firstSeenAt = nowMillis,
            lastSeenAt = nowMillis,
        ),
    )

    private suspend fun ensureHandleAccountInTx(
        handle: String,
        source: HandleSource,
        displayName: String?,
        nowMillis: Long,
    ): Long {
        val existing = accountDao.byHandle(handle)
        if (existing != null) {
            if (displayName != null) accountDao.fillDisplayNameIfMissing(existing.id, displayName, skeletonKey(displayName))
            return existing.id
        }
        return accountDao.insert(
            AccountEntity(
                handle = handle,
                handleSource = source,
                displayName = displayName,
                displayNameSkeleton = displayName?.let(::skeletonKey),
                firstSeenAt = nowMillis,
                lastSeenAt = nowMillis,
                isDefaultHandle = Handles.isDefault(handle),
            ),
        )
    }

    /** Moves every row that references [src] to [target], merges fields, and deletes [src]. */
    private suspend fun mergeProvisionalInto(src: AccountEntity, target: AccountEntity) {
        val merged = mergeAccountFields(target, src)
        sightingDao.reassign(src.id, target.id)
        blockEventDao.reassign(src.id, target.id)
        actionLogDao.reassign(src.id, target.id)
        auditDao.reassignFindings(src.id, target.id)
        tagDao.copyAssignments(src.id, target.id)
        tagDao.deleteAssignmentsForAccount(src.id)
        accountDao.repointSuspectedOriginal(src.id, target.id)
        accountDao.delete(src.id)
        accountDao.update(merged)
    }

    private fun skeletonKey(name: String): String =
        skeletonOf(name).ifBlank { name.trim().lowercase() }

    private fun requireHandle(handle: String): String =
        requireNotNull(Handles.normalize(handle)) { "Handle is blank" }

    companion object {
        /** Skeleton used when no NameNormalizer is supplied (tests): trimmed and lowercased. */
        val DEFAULT_SKELETON: (String) -> String = { it.trim().lowercase() }
    }
}
