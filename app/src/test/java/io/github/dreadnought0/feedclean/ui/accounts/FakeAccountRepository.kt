package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AccountSummary
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [AccountRepository] for ViewModel tests: flows from state, writes recorded as strings. */
class FakeAccountRepository : AccountRepository {
    val summaries = MutableStateFlow<List<AccountSummary>>(emptyList())
    val tagNames = MutableStateFlow<List<String>>(emptyList())
    val allTags = MutableStateFlow<List<Tag>>(emptyList())
    val tagAssignments = MutableStateFlow<Map<Long, List<TagAssignment>>>(emptyMap())
    val timelines = MutableStateFlow<Map<Long, List<TimelineEntry>>>(emptyMap())

    /** Arguments of each observeAccounts call, in order. */
    val accountQueries = mutableListOf<AccountQueryArgs>()

    /** Every write, e.g. "setStatus(7, FALSE_POSITIVE)". */
    val writes = mutableListOf<String>()

    override fun observeAccounts(
        query: String,
        statuses: Set<AccountStatus>,
        tags: Set<String>,
    ): Flow<List<AccountSummary>> {
        accountQueries += AccountQueryArgs(query, statuses, tags)
        return summaries.map { list ->
            list.filter { s ->
                val a = s.account
                (query.isBlank() || listOfNotNull(a.handle, a.displayName).any { it.contains(query, ignoreCase = true) }) &&
                    (statuses.isEmpty() || a.status in statuses) &&
                    s.tagNames.containsAll(tags)
            }
        }
    }

    override fun observeAccount(id: Long): Flow<Account?> = summaries.map { list -> list.firstOrNull { it.account.id == id }?.account }

    override fun observeTimeline(accountId: Long): Flow<List<TimelineEntry>> = timelines.map { it[accountId].orEmpty() }

    override suspend fun getAccount(id: Long): Account? = summaries.value.firstOrNull { it.account.id == id }?.account

    override suspend fun findByHandle(handle: String): Account? = unsupported()

    override suspend fun findByDisplayName(displayName: String): List<Account> = unsupported()

    override suspend fun upsertFromFeed(displayName: String?, probableHandle: String?, nowMillis: Long): Long = unsupported()

    override suspend fun ensureHandleAccount(handle: String, source: HandleSource, displayName: String?, nowMillis: Long): Long =
        unsupported()

    override suspend fun learnHandle(accountId: Long, handle: String, source: HandleSource): Long = unsupported()

    override suspend fun setStatus(accountId: Long, status: AccountStatus) {
        writes += "setStatus($accountId, $status)"
    }

    override suspend fun setNotes(accountId: Long, notes: String?) {
        writes += "setNotes($accountId, $notes)"
    }

    override suspend fun setAccountCategory(accountId: Long, category: String?) {
        writes += "setAccountCategory($accountId, $category)"
    }

    override suspend fun setSuspectedOriginal(accountId: Long, originalId: Long?) {
        writes += "setSuspectedOriginal($accountId, $originalId)"
    }

    override suspend fun addTag(accountId: Long, tagName: String, source: TagSource, nowMillis: Long) {
        writes += "addTag($accountId, $tagName, $source)"
    }

    override suspend fun removeTag(accountId: Long, tagName: String, source: TagSource?) {
        writes += "removeTag($accountId, $tagName, $source)"
    }

    override fun observeTags(accountId: Long): Flow<List<TagAssignment>> = tagAssignments.map { it[accountId].orEmpty() }

    override fun observeAllTagNames(): Flow<List<String>> = tagNames

    override fun observeAllTags(): Flow<List<Tag>> = allTags

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by the UI")
}

/** A fake account with obviously made-up values. */
fun fakeAccount(
    id: Long,
    displayName: String? = "Example Creator $id",
    handle: String? = null,
    status: AccountStatus = AccountStatus.SEEN,
    isDefaultHandle: Boolean = false,
    suspectedOriginalOf: Long? = null,
    notes: String? = null,
): Account = Account(
    id = id,
    handle = handle,
    handleSource = if (handle != null) HandleSource.PROFILE else null,
    displayName = displayName,
    displayNameSkeleton = displayName?.lowercase(),
    firstSeenAt = 1_000L,
    lastSeenAt = 2_000L,
    status = status,
    accountCategory = null,
    shopTier = null,
    isDefaultHandle = isDefaultHandle,
    notes = notes,
    suspectedOriginalOf = suspectedOriginalOf,
    profilePhotoHash = null,
)

/** A list summary for [account]. */
fun fakeSummary(account: Account, tags: List<String> = emptyList(), sightings: Int = 1): AccountSummary =
    AccountSummary(account = account, tagNames = tags, sightingCount = sightings, lastSeenAt = account.lastSeenAt, lastAction = null)
