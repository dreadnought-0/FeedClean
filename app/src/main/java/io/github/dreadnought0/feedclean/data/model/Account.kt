package io.github.dreadnought0.feedclean.data.model

/**
 * A TikTok account as FeedClean knows it.
 *
 * Identity is keyed by [handle]. Feed sightings only carry a display name, so an account created from the
 * feed is **provisional** ([handle] == null) and must be labelled "possible" in the UI. When the handle is
 * learned, the provisional row is merged into the handle-keyed row (see `AccountRepository.learnHandle`).
 */
data class Account(
    val id: Long,
    /** Lowercase, without "@". Null for provisional accounts. */
    val handle: String?,
    /** How [handle] was learned; null while provisional. */
    val handleSource: HandleSource?,
    val displayName: String?,
    /** Normalized display name used to match provisional accounts and impersonators. */
    val displayNameSkeleton: String?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val status: AccountStatus,
    /** Business category shown on the profile, e.g. "Shop". */
    val accountCategory: String?,
    val shopTier: String?,
    /** True when [handle] looks like a TikTok default handle (`user` + 6 or more digits). */
    val isDefaultHandle: Boolean,
    val notes: String?,
    /** Id of the account this one is suspected of impersonating (set by the user only). */
    val suspectedOriginalOf: Long?,
    /** 64-bit avatar dHash (not built yet). */
    val profilePhotoHash: Long?,
) {
    /** True while the handle is unknown; matches are by display name only and must be shown as "possible". */
    val isProvisional: Boolean
        get() = handle == null
}

/** Persisted by name; never rename entries. */
enum class AccountStatus { SEEN, BLOCKED, FALSE_POSITIVE, UNBLOCKED_SUSPECTED }

/** Where a handle came from. Persisted by name; never rename entries. */
enum class HandleSource {
    /** Read from the profile screen (`@handle`). */
    PROFILE,

    /** Read from TikTok's "Block <name> (<handle>)?" dialog. */
    DIALOG,

    /** Inferred from "original sound - <handle> by <name>". Heuristic. */
    SOUND_HEURISTIC,

    /** From the user-tapped Resolve of a shared short link. */
    RESOLVE,

    /** From an import (full export or TikTok data export). */
    IMPORT,
}

/** One row of the Accounts list. */
data class AccountSummary(
    val account: Account,
    /** Distinct tag names assigned to the account (any source), sorted. */
    val tagNames: List<String>,
    val sightingCount: Int,
    val lastSeenAt: Long,
    /** Most recent non-[UserAction.NONE] action recorded on one of the account's sightings, if any. */
    val lastAction: UserAction?,
)
