package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AccountSummary
import io.github.dreadnought0.feedclean.data.model.Handles

/**
 * How sure FeedClean is of who an account is. Feed sightings carry only a display name, so
 * anything without a handle is a "possible" match and is labelled that way everywhere.
 */
enum class Identity {
    /** Has a handle: identity is certain. */
    CONFIRMED,

    /** No handle yet; matched by display name only. */
    POSSIBLE,

    /** The shared bucket for feed pages that showed neither a name nor a handle. */
    UNKNOWN_CREATOR,
}

/** The identity class of [account]. */
fun identityOf(account: Account): Identity = when {
    account.handle != null -> Identity.CONFIRMED
    account.displayName.isNullOrBlank() -> Identity.UNKNOWN_CREATOR
    else -> Identity.POSSIBLE
}

/** True for a TikTok-style default handle (`user` + digits), from the stored flag or the handle itself. */
fun hasDefaultHandle(account: Account): Boolean =
    account.isDefaultHandle || account.handle?.let(Handles::isDefault) == true

/** One row of the Accounts list (also used by the suspected-original picker). */
data class AccountRowModel(
    val id: Long,
    val displayName: String?,
    /** Lowercase, without "@"; null unless [identity] is CONFIRMED. */
    val handle: String?,
    val identity: Identity,
    val isDefaultHandle: Boolean,
    val status: AccountStatus,
    /** Tag names (any source), sorted. */
    val tags: List<String>,
    val sightingCount: Int,
    val lastSeenAt: Long,
) {
    companion object {
        /** Maps a list summary to its row. */
        fun from(summary: AccountSummary): AccountRowModel {
            val account = summary.account
            return AccountRowModel(
                id = account.id,
                displayName = account.displayName?.takeIf { it.isNotBlank() },
                handle = account.handle,
                identity = identityOf(account),
                isDefaultHandle = hasDefaultHandle(account),
                status = account.status,
                tags = summary.tagNames,
                sightingCount = summary.sightingCount,
                lastSeenAt = summary.lastSeenAt,
            )
        }
    }
}
