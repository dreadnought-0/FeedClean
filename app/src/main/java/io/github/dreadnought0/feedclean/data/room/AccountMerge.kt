package io.github.dreadnought0.feedclean.data.room

import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.model.AccountStatus

/**
 * Field-level merge of a provisional account ([source]) into the handle-keyed account ([target]) it turned out
 * to be. Pure; the row moves are done by the repository. The result keeps [target]'s id and handle.
 *
 * - seen range: union of both.
 * - display name: the more recently seen one (or whichever exists).
 * - status: [target]'s, unless it is still SEEN, then [source]'s (so a user's verdict on the provisional row
 *   survives).
 * - category, shop tier, photo hash, suspected original: [target]'s, else [source]'s. A suspected-original
 *   reference to either of the two rows is dropped (it would become a self-reference).
 * - notes: both kept, [target]'s first.
 */
internal fun mergeAccountFields(target: AccountEntity, source: AccountEntity): AccountEntity {
    val takeSourceName = target.displayName == null ||
        (source.displayName != null && source.lastSeenAt > target.lastSeenAt)
    val suspectedOriginal = (target.suspectedOriginalOf ?: source.suspectedOriginalOf)
        ?.takeIf { it != target.id && it != source.id }
    return target.copy(
        displayName = if (takeSourceName) source.displayName else target.displayName,
        displayNameSkeleton = if (takeSourceName) source.displayNameSkeleton else target.displayNameSkeleton,
        firstSeenAt = minOf(target.firstSeenAt, source.firstSeenAt),
        lastSeenAt = maxOf(target.lastSeenAt, source.lastSeenAt),
        status = if (target.status == AccountStatus.SEEN) source.status else target.status,
        accountCategory = target.accountCategory ?: source.accountCategory,
        shopTier = target.shopTier ?: source.shopTier,
        notes = mergeNotes(target.notes, source.notes),
        suspectedOriginalOf = suspectedOriginal,
        profilePhotoHash = target.profilePhotoHash ?: source.profilePhotoHash,
    )
}

private fun mergeNotes(target: String?, source: String?): String? {
    val t = target?.takeIf { it.isNotBlank() }
    val s = source?.takeIf { it.isNotBlank() }
    return when {
        t == null -> s
        s == null || s == t -> t
        else -> "$t\n$s"
    }
}
