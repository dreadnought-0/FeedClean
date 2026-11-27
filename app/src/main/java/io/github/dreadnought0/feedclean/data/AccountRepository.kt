package io.github.dreadnought0.feedclean.data

import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AccountSummary
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import kotlinx.coroutines.flow.Flow

/**
 * Accounts, their identity (handle vs provisional display name), tags and timeline.
 *
 * Handles passed in are normalized (trimmed, leading "@" removed, lowercased) before use.
 */
interface AccountRepository {
    /**
     * Accounts list, most recently seen first.
     *
     * @param query matches handle or display name (substring, case-insensitive; a leading "@" is ignored for
     *   handles). Blank = no text filter.
     * @param statuses keep only these statuses. Empty = any status.
     * @param tags keep only accounts carrying ALL of these tag names (any source). Empty = no tag filter.
     */
    fun observeAccounts(
        query: String = "",
        statuses: Set<AccountStatus> = emptySet(),
        tags: Set<String> = emptySet(),
    ): Flow<List<AccountSummary>>

    fun observeAccount(id: Long): Flow<Account?>

    /** Sightings and block events of one account, newest first. */
    fun observeTimeline(accountId: Long): Flow<List<TimelineEntry>>

    suspend fun getAccount(id: Long): Account?

    suspend fun findByHandle(handle: String): Account?

    /**
     * Every account (provisional or handle-keyed) whose display-name skeleton equals that of [displayName].
     * Used for the overlay's "possible match of a blocked account" warning. Most recently seen first.
     */
    suspend fun findByDisplayName(displayName: String): List<Account>

    /**
     * Resolves the account for a feed page and marks it seen at [nowMillis]. Returns its id.
     *
     * 1. [probableHandle] given and an account with that handle exists → that account.
     * 2. Else a PROVISIONAL account (no handle) with the same display-name skeleton → that account.
     * 3. Else a new provisional account.
     *
     * [probableHandle] is a heuristic and is never stored as the handle. A null/blank [displayName] maps to
     * one shared "unknown creator" provisional account.
     */
    suspend fun upsertFromFeed(displayName: String?, probableHandle: String?, nowMillis: Long): Long

    /**
     * Returns the id of the account with [handle], creating it (status SEEN) if missing. For imports, Resolve,
     * and audits. Fills in [displayName] if the account has none.
     */
    suspend fun ensureHandleAccount(
        handle: String,
        source: HandleSource,
        displayName: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long

    /**
     * Records that account [accountId] has [handle], learned from [source]. Returns the surviving account id.
     *
     * - If no account has [handle] and [accountId] is provisional: the handle is set on it.
     * - If another account already has [handle] and [accountId] is provisional: the provisional row's
     *   sightings, tags, block events, action-log rows and audit findings are moved into that account and the
     *   provisional row is deleted, all in one transaction.
     * - If [accountId] already has a different handle, or is the shared "unknown creator" bucket, nothing is
     *   merged; the handle-keyed account is found or created and its id returned.
     */
    suspend fun learnHandle(accountId: Long, handle: String, source: HandleSource): Long

    suspend fun setStatus(accountId: Long, status: AccountStatus)

    suspend fun setNotes(accountId: Long, notes: String?)

    suspend fun setAccountCategory(accountId: Long, category: String?)

    /** Marks [accountId] as a suspected impersonator of [originalId] (null clears). Self-references are ignored. */
    suspend fun setSuspectedOriginal(accountId: Long, originalId: Long?)

    /**
     * Puts tag [tagName] (normalized, see [io.github.dreadnought0.feedclean.data.model.TagNames.normalize]) on
     * the account, creating the tag if needed (kind SIGNAL when [source] is AUTO_DETECTED, else MANUAL).
     * Idempotent.
     */
    suspend fun addTag(
        accountId: Long,
        tagName: String,
        source: TagSource,
        nowMillis: Long = System.currentTimeMillis(),
    )

    /** Removes the tag from the account for [source], or for every source when [source] is null. */
    suspend fun removeTag(accountId: Long, tagName: String, source: TagSource? = null)

    fun observeTags(accountId: Long): Flow<List<TagAssignment>>

    /** Every tag name, sorted. */
    fun observeAllTagNames(): Flow<List<String>>

    /** Every tag with its kind (signal tags first, then by name). */
    fun observeAllTags(): Flow<List<Tag>>
}
