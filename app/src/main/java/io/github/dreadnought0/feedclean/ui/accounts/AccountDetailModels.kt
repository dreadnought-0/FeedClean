package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource

/** Longest manual tag name accepted from the text field. */
const val MAX_TAG_LENGTH: Int = 40

/**
 * An account's tags split by who set them. A tag can be in both lists (detected AND added by hand);
 * removing the manual one leaves the detected one.
 */
data class AccountTagGroups(
    /** Set by detection (source AUTO_DETECTED); shown, never removable here. */
    val autoDetected: List<String> = emptyList(),
    /** Set by the user (source MANUAL); removable. */
    val manual: List<String> = emptyList(),
    /** Manual-kind tags the user could add with one tap (not yet on the account as manual). */
    val suggestions: List<String> = emptyList(),
)

/** Splits [assignments] by source and offers the remaining manual-kind tags from [allTags]. Sorted. */
fun groupTags(assignments: List<TagAssignment>, allTags: List<Tag>): AccountTagGroups {
    fun namesFor(source: TagSource) =
        assignments.filter { it.source == source }.map { it.tag.name }.distinct().sorted()
    val manual = namesFor(TagSource.MANUAL)
    return AccountTagGroups(
        autoDetected = namesFor(TagSource.AUTO_DETECTED),
        manual = manual,
        suggestions = allTags.filter { it.kind == TagKind.MANUAL && it.name !in manual }.map { it.name }.distinct().sorted(),
    )
}

/** A typed tag normalized like the repository does; null when blank or longer than [MAX_TAG_LENGTH]. */
fun normalizeManualTag(raw: String): String? =
    TagNames.normalize(raw).takeIf { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }

/**
 * Status buttons offered for an account currently in [current]: every one of "false positive", "seen
 * (reset)" and "unblocked-suspected" except the current status. They only change FeedClean's records.
 */
fun statusActions(current: AccountStatus): List<AccountStatus> =
    listOf(AccountStatus.FALSE_POSITIVE, AccountStatus.SEEN, AccountStatus.UNBLOCKED_SUSPECTED)
        .filter { it != current }

/** Notes as stored: trimmed, blank → null. */
fun normalizeNotes(raw: String): String? = raw.trim().ifEmpty { null }
