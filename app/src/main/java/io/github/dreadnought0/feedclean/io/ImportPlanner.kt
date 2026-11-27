package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.DataLayer
import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.Handles
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.SkipReason

/**
 * Plans a full-export import. Pure: compares the database's current rows ([existing], read by
 * [RoomSnapshotReader]) with a file's rows ([incoming]) and decides what to insert, merge, or skip.
 *
 * Rules (idempotent: importing the same file twice adds nothing the second time):
 * - **Accounts with a handle** merge by handle into the existing account; otherwise they are inserted with
 *   their handle and handle source.
 * - **Provisional accounts** (no handle) stay provisional. One is matched to an existing account only when
 *   that is certain: same display name and first-seen time (the same row exported earlier), or every one of
 *   its sightings is already in the database under one account (it was merged since). The shared
 *   "unknown creator" bucket maps to the existing bucket. Otherwise a new provisional row is inserted; names
 *   alone never merge accounts.
 * - **Foreign keys** are remapped through [Ref]s.
 * - **Sightings** with the same page signature and seen-at time as an existing one are skipped; **block
 *   events** with the same account, time, method and outcome are skipped; **sessions** match by start time;
 *   **audits** by run time, source and file name; **tags** by name.
 * - **Action log**: rows inside the rate limiter's window (the last [rateLimitWindowMillis] before
 *   `nowMillis`, or in the future) are never imported, so an import cannot change the live rate-limit budget.
 *   Older rows are history only and are added unless already present. Existing rows are never changed.
 */
object ImportPlanner {

    /** Plans importing [incoming] into a database whose rows are [existing]. */
    fun plan(
        existing: ExportBundle,
        incoming: ExportBundle,
        nowMillis: Long,
        rateLimitWindowMillis: Long = DataLayer.RATE_LIMIT_RETENTION_MILLIS,
    ): ImportPlan {
        var invalid = 0
        val accounts = planAccounts(existing, incoming)
        invalid += accounts.invalid
        val targets = accounts.targets

        val suspected = incoming.accounts.mapNotNull { row ->
            val original = row.suspectedOriginalOf ?: return@mapNotNull null
            val a = targets[row.id] ?: return@mapNotNull null
            val o = targets[original] ?: return@mapNotNull null
            if (a == o) null else a to o
        }.distinct()

        // ── tags ──
        val existingTagNames = existing.tags.map { TagNames.normalize(it.name) }.toSet()
        val incomingTagNames = HashMap<Long, String>()
        val newTags = LinkedHashMap<String, TagKind>()
        incoming.tags.forEach { row ->
            val name = TagNames.normalize(row.name)
            if (name.isEmpty()) {
                invalid++
                return@forEach
            }
            incomingTagNames[row.id] = name
            if (name !in existingTagNames && name !in newTags) {
                newTags[name] = enumOrNull<TagKind>(row.kind) ?: TagKind.MANUAL
            }
        }
        val existingTagNameById = existing.tags.associate { it.id to TagNames.normalize(it.name) }
        val assigned = existing.accountTags.mapNotNull { row ->
            existingTagNameById[row.tagId]?.let { Triple(Ref.Existing(row.accountId) as Ref, it, row.source) }
        }.toMutableSet()
        val assignments = incoming.accountTags.mapNotNull { row ->
            val account = targets[row.accountId]
            val tagName = incomingTagNames[row.tagId]
            val source = enumOrNull<TagSource>(row.source)
            if (account == null || tagName == null || source == null) {
                invalid++
                return@mapNotNull null
            }
            if (!assigned.add(Triple(account, tagName, source.name))) return@mapNotNull null
            PlannedTagAssignment(account, tagName, source, row.createdAt)
        }

        // ── sessions ──
        val sessionsByStart: MutableMap<Long, Ref> =
            existing.sessions.associate { it.startedAt to (Ref.Existing(it.id) as Ref) }.toMutableMap()
        val sessionTargets = HashMap<Long, Ref>()
        val newSessions = ArrayList<SessionRow>()
        incoming.sessions.sortedBy { it.id }.forEach { row ->
            val ref = sessionsByStart.getOrPut(row.startedAt) {
                newSessions += row
                Ref.New(row.id)
            }
            sessionTargets[row.id] = ref
        }

        // ── sightings ──
        val sightingKeys = existing.sightings.map { it.pageSignature to it.seenAt }.toMutableSet()
        var duplicateSightings = 0
        val sightings = incoming.sightings.sortedBy { it.id }.mapNotNull { row ->
            val account = targets[row.accountId]
            val source = enumOrNull<SightingSource>(row.source)
            if (account == null || source == null) {
                invalid++
                return@mapNotNull null
            }
            if (!sightingKeys.add(row.pageSignature to row.seenAt)) {
                duplicateSightings++
                return@mapNotNull null
            }
            PlannedSighting(
                row = row,
                account = account,
                session = row.sessionId?.let { sessionTargets[it] },
                source = source,
                userAction = enumOrNull<UserAction>(row.userAction) ?: UserAction.NONE,
                skipReason = enumOrNull<SkipReason>(row.skipReason),
            )
        }

        // ── block events ──
        val blockKeys = existing.blockEvents.map {
            BlockKey(Ref.Existing(it.accountId), it.attemptedAt, it.method, it.verified)
        }.toMutableSet()
        var duplicateBlocks = 0
        val blockEvents = incoming.blockEvents.sortedBy { it.id }.mapNotNull { row ->
            val account = targets[row.accountId]
            val method = enumOrNull<BlockMethod>(row.method)
            if (account == null || method == null) {
                invalid++
                return@mapNotNull null
            }
            if (!blockKeys.add(BlockKey(account, row.attemptedAt, method.name, row.verified))) {
                duplicateBlocks++
                return@mapNotNull null
            }
            PlannedBlockEvent(row, account, method)
        }

        // ── action log (history only; never inside the rate-limit window) ──
        val windowStart = nowMillis - rateLimitWindowMillis
        val actionKeys = existing.actionLog.map { Triple(it.kind, it.at, it.verified) }.toMutableSet()
        var duplicateActions = 0
        var actionsInWindow = 0
        val actions = incoming.actionLog.sortedBy { it.id }.mapNotNull { row ->
            val kind = enumOrNull<ActionKind>(row.kind)
            when {
                kind == null -> {
                    invalid++
                    null
                }
                row.at >= windowStart -> {
                    actionsInWindow++
                    null
                }
                !actionKeys.add(Triple(row.kind, row.at, row.verified)) -> {
                    duplicateActions++
                    null
                }
                else -> PlannedAction(row, row.accountId?.let { targets[it] }, kind)
            }
        }

        // ── audits ──
        val auditKeys = existing.audits.map { Triple(it.runAt, it.source, it.exportFileName) }.toMutableSet()
        val findingsByAudit = incoming.auditFindings.groupBy { it.auditId }
        var duplicateAudits = 0
        val audits = incoming.audits.sortedBy { it.id }.mapNotNull { row ->
            val source = enumOrNull<AuditSource>(row.source)
            if (source == null) {
                invalid++
                return@mapNotNull null
            }
            if (!auditKeys.add(Triple(row.runAt, row.source, row.exportFileName))) {
                duplicateAudits++
                return@mapNotNull null
            }
            val findings = findingsByAudit[row.id].orEmpty().sortedBy { it.id }.mapNotNull { finding ->
                val kind = enumOrNull<AuditFindingKind>(finding.kind)
                val handle = Handles.normalize(finding.handle)
                if (kind == null || handle == null) {
                    invalid++
                    null
                } else {
                    PlannedFinding(finding, finding.accountId?.let { targets[it] }, kind, handle)
                }
            }
            PlannedAudit(row, source, findings)
        }
        val knownAuditIds = incoming.audits.map { it.id }.toSet()
        invalid += incoming.auditFindings.count { it.auditId !in knownAuditIds }

        return ImportPlan(
            accountTargets = targets,
            accountMerges = accounts.merges,
            newAccounts = accounts.inserts,
            suspectedOriginals = suspected,
            newTags = newTags.toList(),
            tagAssignments = assignments,
            sessionTargets = sessionTargets,
            newSessions = newSessions,
            sightings = sightings,
            blockEvents = blockEvents,
            actionLog = actions,
            audits = audits,
            skipped = ImportSkips(
                duplicateSightings = duplicateSightings,
                duplicateBlockEvents = duplicateBlocks,
                duplicateActions = duplicateActions,
                actionsInRateLimitWindow = actionsInWindow,
                duplicateAudits = duplicateAudits,
                invalidRows = invalid,
            ),
        )
    }

    /**
     * Field merge of an imported account into the existing one. Idempotent (merging the same row twice gives
     * the same result) and conservative: the existing row's values win; gaps are filled from [incoming].
     *
     * - seen range: union; status: existing, unless it is still SEEN; notes: both kept, unless the existing
     *   notes already contain the incoming ones; handle, id and suspected-original are never changed here.
     */
    fun mergeImportedAccount(target: AccountEntity, incoming: AccountRow): AccountEntity {
        val incomingStatus = enumOrNull<AccountStatus>(incoming.status) ?: AccountStatus.SEEN
        val takeName = target.displayName == null && incoming.displayName != null
        return target.copy(
            handleSource = target.handleSource ?: if (target.handle != null) handleSourceOf(incoming) else null,
            displayName = if (takeName) incoming.displayName else target.displayName,
            displayNameSkeleton = if (takeName) skeletonOf(incoming) else target.displayNameSkeleton,
            firstSeenAt = minOf(target.firstSeenAt, incoming.firstSeenAt),
            lastSeenAt = maxOf(target.lastSeenAt, incoming.lastSeenAt),
            status = if (target.status == AccountStatus.SEEN) incomingStatus else target.status,
            accountCategory = target.accountCategory ?: incoming.accountCategory,
            shopTier = target.shopTier ?: incoming.shopTier,
            notes = mergeNotes(target.notes, incoming.notes),
            profilePhotoHash = target.profilePhotoHash ?: incoming.profilePhotoHash,
        )
    }

    /** A new account row for [incoming] (id assigned by the database; suspected-original linked later). */
    fun newAccountEntity(incoming: AccountRow): AccountEntity {
        val handle = Handles.normalize(incoming.handle)
        return AccountEntity(
            handle = handle,
            handleSource = if (handle != null) handleSourceOf(incoming) else null,
            displayName = incoming.displayName,
            displayNameSkeleton = skeletonOf(incoming),
            firstSeenAt = incoming.firstSeenAt,
            lastSeenAt = maxOf(incoming.firstSeenAt, incoming.lastSeenAt),
            status = enumOrNull<AccountStatus>(incoming.status) ?: AccountStatus.SEEN,
            accountCategory = incoming.accountCategory,
            shopTier = incoming.shopTier,
            isDefaultHandle = handle != null && Handles.isDefault(handle),
            notes = incoming.notes?.takeIf { it.isNotBlank() },
            suspectedOriginalOf = null,
            profilePhotoHash = incoming.profilePhotoHash,
        )
    }

    // ── internals ────────────────────────────────────────────────────────────

    private data class BlockKey(val account: Ref, val attemptedAt: Long, val method: String, val verified: Boolean)

    private class AccountPlan(
        val targets: Map<Long, Ref>,
        val merges: List<Pair<Long, AccountRow>>,
        val inserts: List<AccountRow>,
        val invalid: Int,
    )

    private fun planAccounts(existing: ExportBundle, incoming: ExportBundle): AccountPlan {
        val byHandle = HashMap<String, Ref>()
        existing.accounts.forEach { row -> Handles.normalize(row.handle)?.let { byHandle[it] = Ref.Existing(row.id) } }
        val provisionalByIdentity = HashMap<Pair<String, Long>, Ref>()
        existing.accounts.filter { it.handle == null && it.displayName != null }.sortedBy { it.id }.forEach {
            provisionalByIdentity.putIfAbsent(it.displayName!! to it.firstSeenAt, Ref.Existing(it.id))
        }
        var unknownBucket: Ref? = existing.accounts
            .filter { it.handle == null && it.displayName == null }
            .minByOrNull { it.id }
            ?.let { Ref.Existing(it.id) }
        val sightingOwner = existing.sightings.associate { (it.pageSignature to it.seenAt) to it.accountId }
        val incomingSightings = incoming.sightings.groupBy { it.accountId }

        val targets = HashMap<Long, Ref>()
        val merges = ArrayList<Pair<Long, AccountRow>>()
        val inserts = ArrayList<AccountRow>()
        var invalid = 0

        fun route(row: AccountRow, found: Ref?, register: (Ref) -> Unit): Ref = when (found) {
            is Ref.Existing -> found.also { merges += it.id to row }
            is Ref.New -> found
            null -> Ref.New(row.id).also {
                inserts += row
                register(it)
            }
        }

        incoming.accounts.sortedBy { it.id }.forEach { row ->
            if (row.id in targets) {
                invalid++
                return@forEach
            }
            val handle = Handles.normalize(row.handle)
            val ref = when {
                handle != null -> {
                    val normalized = row.copy(handle = handle)
                    route(normalized, byHandle[handle]) { byHandle[handle] = it }
                }
                row.displayName == null -> route(row.copy(handle = null), unknownBucket) { unknownBucket = it }
                else -> {
                    val identity = row.displayName to row.firstSeenAt
                    val found = provisionalByIdentity[identity]
                        ?: ownerOfAllSightings(incomingSightings[row.id].orEmpty(), sightingOwner)
                    route(row.copy(handle = null), found) { provisionalByIdentity[identity] = it }
                }
            }
            targets[row.id] = ref
        }
        return AccountPlan(targets, merges, inserts, invalid)
    }

    /** The one existing account that already owns every sighting in [sightings], or null. */
    private fun ownerOfAllSightings(sightings: List<SightingRow>, owners: Map<Pair<String, Long>, Long>): Ref? {
        if (sightings.isEmpty()) return null
        val found = sightings.map { owners[it.pageSignature to it.seenAt] ?: return null }.distinct()
        return found.singleOrNull()?.let { Ref.Existing(it) }
    }

    private fun handleSourceOf(row: AccountRow): HandleSource =
        enumOrNull<HandleSource>(row.handleSource) ?: HandleSource.IMPORT

    private fun skeletonOf(row: AccountRow): String? =
        row.displayNameSkeleton?.takeIf { it.isNotBlank() } ?: row.displayName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun mergeNotes(target: String?, incoming: String?): String? {
        val t = target?.takeIf { it.isNotBlank() }
        val i = incoming?.takeIf { it.isNotBlank() }
        return when {
            t == null -> i
            i == null || t.contains(i) -> t
            else -> "$t\n$i"
        }
    }
}
