package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.TagNames

/** The Accounts list's search and filter chips, as the user set them. */
data class AccountFilter(
    val query: String = "",
    val statuses: Set<AccountStatus> = emptySet(),
    val tags: Set<String> = emptySet(),
) {
    /** True when anything narrows the list (decides between the two empty states). */
    val isActive: Boolean
        get() = query.isNotBlank() || statuses.isNotEmpty() || tags.isNotEmpty()

    /** Adds or removes [status]. */
    fun toggleStatus(status: AccountStatus): AccountFilter =
        copy(statuses = if (status in statuses) statuses - status else statuses + status)

    /** Adds or removes [tag]. */
    fun toggleTag(tag: String): AccountFilter = copy(tags = if (tag in tags) tags - tag else tags + tag)

    /**
     * Arguments for `AccountRepository.observeAccounts`: the query trimmed (blank = no text filter), tags
     * normalized with blanks dropped. Statuses pass through (empty = any; several = any of them; tags =
     * all of them, as the repository defines).
     */
    fun toQueryArgs(): AccountQueryArgs = AccountQueryArgs(
        query = query.trim(),
        statuses = statuses,
        tags = tags.map(TagNames::normalize).filter { it.isNotEmpty() }.toSet(),
    )
}

/** Exactly what is passed to `AccountRepository.observeAccounts`. */
data class AccountQueryArgs(
    val query: String,
    val statuses: Set<AccountStatus>,
    val tags: Set<String>,
)

/** Status chips in display order. */
val statusFilterOrder: List<AccountStatus> = listOf(
    AccountStatus.SEEN,
    AccountStatus.BLOCKED,
    AccountStatus.FALSE_POSITIVE,
    AccountStatus.UNBLOCKED_SUSPECTED,
)

/**
 * Tag chips to show: every known tag plus any selected tag that is no longer known (so it can still be
 * deselected), sorted.
 */
fun tagChipNames(available: List<String>, selected: Set<String>): List<String> =
    (available + selected).distinct().sorted()
