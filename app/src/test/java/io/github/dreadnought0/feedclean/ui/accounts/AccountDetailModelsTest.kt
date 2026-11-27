package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDetailModelsTest {
    private val paidAd = Tag(1, "paid_ad", TagKind.SIGNAL)
    private val impersonator = Tag(2, "impersonator", TagKind.MANUAL)
    private val botLike = Tag(3, "bot_like", TagKind.MANUAL)
    private val custom = Tag(4, "copy_cat", TagKind.MANUAL)

    @Test
    fun tagsSplitBySourceWithSuggestions() {
        val groups = groupTags(
            assignments = listOf(
                TagAssignment(paidAd, TagSource.AUTO_DETECTED, 1L),
                TagAssignment(paidAd, TagSource.MANUAL, 2L),
                TagAssignment(impersonator, TagSource.MANUAL, 3L),
            ),
            allTags = listOf(paidAd, impersonator, botLike, custom),
        )
        assertEquals(listOf("paid_ad"), groups.autoDetected)
        assertEquals(listOf("impersonator", "paid_ad"), groups.manual)
        // Signal-kind tags are not suggested; manual ones already on the account are not either.
        assertEquals(listOf("bot_like", "copy_cat"), groups.suggestions)
    }

    @Test
    fun noTags() {
        assertEquals(AccountTagGroups(), groupTags(emptyList(), emptyList()))
    }

    @Test
    fun manualTagInputIsNormalizedAndBounded() {
        assertEquals("copy_cat", normalizeManualTag("  Copy   Cat "))
        assertNull(normalizeManualTag("   "))
        assertNull(normalizeManualTag("x".repeat(MAX_TAG_LENGTH + 1)))
        assertEquals("x".repeat(MAX_TAG_LENGTH), normalizeManualTag("x".repeat(MAX_TAG_LENGTH)))
    }

    @Test
    fun statusActionsExcludeTheCurrentStatus() {
        assertEquals(
            listOf(AccountStatus.FALSE_POSITIVE, AccountStatus.UNBLOCKED_SUSPECTED),
            statusActions(AccountStatus.SEEN),
        )
        assertEquals(
            listOf(AccountStatus.FALSE_POSITIVE, AccountStatus.SEEN, AccountStatus.UNBLOCKED_SUSPECTED),
            statusActions(AccountStatus.BLOCKED),
        )
        assertEquals(listOf(AccountStatus.FALSE_POSITIVE, AccountStatus.SEEN), statusActions(AccountStatus.UNBLOCKED_SUSPECTED))
    }

    @Test
    fun notesAreTrimmedAndBlankClears() {
        assertEquals("note", normalizeNotes("  note \n"))
        assertNull(normalizeNotes(" \n "))
    }

    @Test
    fun profileUrlUsesTheNormalizedHandle() {
        val template = "https://example.invalid/@%1\$s"
        assertEquals("https://example.invalid/@example.shop_01", profileUrl(" @Example.Shop_01 ", template))
        assertNull(profileUrl(null, template))
        assertNull(profileUrl("  @ ", template))
        assertNull(profileUrl("bad/handle?x=1", template))
        assertNull(profileUrl("two words", template))
    }
}
