package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.BlockEventRepository
import io.github.dreadnought0.feedclean.data.RecordResult
import io.github.dreadnought0.feedclean.data.SessionRepository
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AccountSummary
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.Handles
import io.github.dreadnought0.feedclean.data.model.Sighting
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory repositories for the runtime tests (service/, curate/, overlay/). They model the documented
 * behaviour the runtime relies on (provisional vs handle accounts, sighting dedupe, learnHandle merge) and
 * record every call in [calls] so tests can assert exactly what was written. Not thread-safe.
 */
class FakeAccountRepository(private val skeleton: (String) -> String = { it.trim().lowercase() }) : AccountRepository {
    val accounts = linkedMapOf<Long, Account>()
    val tags = mutableListOf<Triple<Long, String, TagSource>>()
    val calls = mutableListOf<String>()
    private var nextId = 1L

    /** Inserts an account directly (test setup). */
    fun seed(
        handle: String? = null,
        displayName: String? = null,
        status: AccountStatus = AccountStatus.SEEN,
        category: String? = null,
        firstSeenAt: Long = 0L,
    ): Account {
        val a = account(nextId++, handle, displayName, firstSeenAt).copy(status = status, accountCategory = category)
        accounts[a.id] = a
        return a
    }

    private fun account(id: Long, handle: String?, name: String?, now: Long) = Account(
        id = id,
        handle = handle?.let(Handles::normalize),
        handleSource = handle?.let { HandleSource.PROFILE },
        displayName = name,
        displayNameSkeleton = name?.let(skeleton),
        firstSeenAt = now,
        lastSeenAt = now,
        status = AccountStatus.SEEN,
        accountCategory = null,
        shopTier = null,
        isDefaultHandle = false,
        notes = null,
        suspectedOriginalOf = null,
        profilePhotoHash = null,
    )

    override fun observeAccounts(query: String, statuses: Set<AccountStatus>, tags: Set<String>): Flow<List<AccountSummary>> =
        emptyFlow()

    override fun observeAccount(id: Long): Flow<Account?> = flowOf(accounts[id])

    var timeline: Map<Long, List<TimelineEntry>> = emptyMap()

    override fun observeTimeline(accountId: Long): Flow<List<TimelineEntry>> = flowOf(timeline[accountId].orEmpty())

    override suspend fun getAccount(id: Long): Account? = accounts[id]

    override suspend fun findByHandle(handle: String): Account? {
        val h = Handles.normalize(handle) ?: return null
        return accounts.values.firstOrNull { it.handle == h }
    }

    override suspend fun findByDisplayName(displayName: String): List<Account> {
        val s = skeleton(displayName)
        return accounts.values.filter { it.displayNameSkeleton == s }
    }

    override suspend fun upsertFromFeed(displayName: String?, probableHandle: String?, nowMillis: Long): Long {
        calls += "upsertFromFeed"
        probableHandle?.let { findByHandle(it) }?.let { return it.id }
        val s = displayName?.let(skeleton)
        accounts.values.firstOrNull { it.handle == null && it.displayNameSkeleton == s }?.let { return it.id }
        val a = account(nextId++, null, displayName, nowMillis)
        accounts[a.id] = a
        return a.id
    }

    override suspend fun ensureHandleAccount(handle: String, source: HandleSource, displayName: String?, nowMillis: Long): Long {
        calls += "ensureHandleAccount:$handle"
        findByHandle(handle)?.let { return it.id }
        val a = account(nextId++, handle, displayName, nowMillis).copy(handleSource = source)
        accounts[a.id] = a
        return a.id
    }

    override suspend fun learnHandle(accountId: Long, handle: String, source: HandleSource): Long {
        calls += "learnHandle:$accountId:$handle:$source"
        val h = requireNotNull(Handles.normalize(handle))
        val existing = findByHandle(h)
        val src = accounts[accountId]
        return when {
            src == null -> ensureHandleAccount(h, source, null, 0)
            src.handle == h -> src.id
            existing != null -> {
                accounts.remove(accountId)
                existing.id
            }
            else -> {
                accounts[accountId] = src.copy(handle = h, handleSource = source)
                accountId
            }
        }
    }

    override suspend fun setStatus(accountId: Long, status: AccountStatus) {
        calls += "setStatus:$accountId:$status"
        accounts[accountId]?.let { accounts[accountId] = it.copy(status = status) }
    }

    override suspend fun setNotes(accountId: Long, notes: String?) = Unit

    override suspend fun setAccountCategory(accountId: Long, category: String?) {
        calls += "setAccountCategory:$accountId:$category"
        accounts[accountId]?.let { accounts[accountId] = it.copy(accountCategory = category) }
    }

    override suspend fun setSuspectedOriginal(accountId: Long, originalId: Long?) = Unit

    override suspend fun addTag(accountId: Long, tagName: String, source: TagSource, nowMillis: Long) {
        calls += "addTag:$accountId:$tagName:$source"
        tags += Triple(accountId, tagName, source)
    }

    override suspend fun removeTag(accountId: Long, tagName: String, source: TagSource?) {
        calls += "removeTag:$accountId:$tagName:$source"
        tags.removeAll { it.first == accountId && it.second == tagName && (source == null || it.third == source) }
    }

    override fun observeTags(accountId: Long): Flow<List<TagAssignment>> = flowOf(
        tags.filter { it.first == accountId }.map { TagAssignment(Tag(0, it.second, TagKind.MANUAL), it.third, 0L) },
    )

    override fun observeAllTagNames(): Flow<List<String>> = emptyFlow()

    override fun observeAllTags(): Flow<List<Tag>> = emptyFlow()
}

/** In-memory [SightingRepository] with the repository's 10-minute dedupe. */
class FakeSightingRepository : SightingRepository {
    val sightings = linkedMapOf<Long, Sighting>()
    val calls = mutableListOf<String>()
    private var nextId = 1L

    override suspend fun recordSighting(
        accountId: Long,
        pageSignature: String,
        signals: Signals,
        source: SightingSource,
        userAction: UserAction,
        skipReason: SkipReason?,
        videoUrl: String?,
        nowMillis: Long,
        sessionId: Long?,
        screenshotPath: String?,
    ): RecordResult {
        calls += "record:$pageSignature:$userAction:${skipReason ?: "-"}"
        val existing = sightings.values.lastOrNull {
            it.pageSignature == pageSignature && it.seenAt >= nowMillis - SightingRepository.DEDUPE_WINDOW_MILLIS
        }
        if (existing != null) {
            if (existing.userAction == UserAction.NONE && userAction != UserAction.NONE) {
                sightings[existing.id] = existing.copy(userAction = userAction, skipReason = skipReason ?: existing.skipReason)
            }
            return RecordResult(existing.id, deduplicated = true)
        }
        val s = Sighting(
            id = nextId++, accountId = accountId, seenAt = nowMillis, videoUrl = videoUrl, pageSignature = pageSignature,
            signals = signals, source = source, userAction = userAction, actionVerified = null, skipReason = skipReason,
            screenshotPath = screenshotPath, sessionId = sessionId,
        )
        sightings[s.id] = s
        return RecordResult(s.id, deduplicated = false)
    }

    override suspend fun setUserAction(sightingId: Long, action: UserAction, verified: Boolean?) {
        calls += "setUserAction:$sightingId:$action:$verified"
        sightings[sightingId]?.let { sightings[sightingId] = it.copy(userAction = action, actionVerified = verified) }
    }

    override suspend fun updateSignals(sightingId: Long, signals: Signals, nowMillis: Long) {
        calls += "updateSignals:$sightingId"
        sightings[sightingId]?.let { sightings[sightingId] = it.copy(signals = signals) }
    }

    override fun observeRecent(limit: Int): Flow<List<Sighting>> = flowOf(sightings.values.toList().takeLast(limit))

    override suspend fun latestForAccount(accountId: Long): Sighting? = sightings.values.lastOrNull { it.accountId == accountId }

    override suspend fun getSighting(id: Long): Sighting? = sightings[id]
}

/** In-memory [SessionRepository]. */
class FakeSessionRepository : SessionRepository {
    data class Page(val sessionId: Long, val signals: Signals, val skipped: Boolean)

    val open = mutableSetOf<Long>()
    val ended = mutableListOf<Long>()
    val pages = mutableListOf<Page>()
    var endAllOpenCalls = 0
    private var nextId = 1L

    override suspend fun start(nowMillis: Long): Long = nextId++.also { open += it }

    override suspend fun end(id: Long, nowMillis: Long) {
        if (open.remove(id)) ended += id
    }

    override suspend fun endAllOpen(nowMillis: Long): Int {
        endAllOpenCalls++
        val n = open.size
        ended += open
        open.clear()
        return n
    }

    override suspend fun recordPage(id: Long, signals: Signals, skipped: Boolean) {
        pages += Page(id, signals, skipped)
    }
}

/** In-memory [BlockEventRepository]; a verified event sets the account BLOCKED in [accounts] (if given). */
class FakeBlockEventRepository(private val accounts: FakeAccountRepository? = null) : BlockEventRepository {
    val events = mutableListOf<BlockEvent>()
    private var nextId = 1L
    private val flow = MutableStateFlow<List<BlockEvent>>(emptyList())

    override suspend fun record(
        accountId: Long,
        atMillis: Long,
        method: BlockMethod,
        verified: Boolean,
        failureReason: String?,
        stepReached: String?,
    ): Long {
        val e = BlockEvent(nextId++, accountId, atMillis, method, verified, failureReason, stepReached)
        events += e
        flow.value = events.toList()
        if (verified) accounts?.setStatus(accountId, AccountStatus.BLOCKED)
        return e.id
    }

    override fun observeForAccount(accountId: Long): Flow<List<BlockEvent>> = flow.map { l -> l.filter { it.accountId == accountId } }

    override suspend fun latestVerifiedBlock(accountId: Long): BlockEvent? = events.lastOrNull { it.accountId == accountId && it.verified }
}
