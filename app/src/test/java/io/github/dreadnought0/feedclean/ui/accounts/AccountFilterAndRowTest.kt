package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.stats.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFilterAndRowTest {
    @Test
    fun defaultFilterQueriesEverything() {
        val filter = AccountFilter()
        assertFalse(filter.isActive)
        assertEquals(AccountQueryArgs("", emptySet(), emptySet()), filter.toQueryArgs())
    }

    @Test
    fun queryIsTrimmedAndBlankMeansNoTextFilter() {
        assertEquals("@shop_01", AccountFilter(query = "  @shop_01 ").toQueryArgs().query)
        val blank = AccountFilter(query = "   ")
        assertEquals("", blank.toQueryArgs().query)
        assertFalse(blank.isActive)
    }

    @Test
    fun chipsToggleAndPassThrough() {
        val filter = AccountFilter()
            .toggleStatus(AccountStatus.BLOCKED)
            .toggleStatus(AccountStatus.UNBLOCKED_SUSPECTED)
            .toggleStatus(AccountStatus.BLOCKED)
            .toggleTag("paid_ad")
        assertTrue(filter.isActive)
        assertEquals(
            AccountQueryArgs("", setOf(AccountStatus.UNBLOCKED_SUSPECTED), setOf("paid_ad")),
            filter.toQueryArgs(),
        )
        assertEquals(emptySet<String>(), filter.toggleTag("paid_ad").tags)
    }

    @Test
    fun tagsAreNormalizedAndBlankTagsDropped() {
        val args = AccountFilter(tags = setOf(" Paid Ad ", "", "   ")).toQueryArgs()
        assertEquals(setOf("paid_ad"), args.tags)
    }

    @Test
    fun selectedTagsStayVisibleEvenIfUnknown() {
        assertEquals(listOf("a_tag", "gone_tag", "z_tag"), tagChipNames(listOf("z_tag", "a_tag"), setOf("gone_tag", "a_tag")))
    }

    @Test
    fun statusChipsInDisplayOrder() {
        assertEquals(
            listOf(AccountStatus.SEEN, AccountStatus.BLOCKED, AccountStatus.FALSE_POSITIVE, AccountStatus.UNBLOCKED_SUSPECTED),
            statusFilterOrder,
        )
    }

    @Test
    fun identityDistinguishesHandlePossibleAndUnknownCreator() {
        assertEquals(Identity.CONFIRMED, identityOf(fakeAccount(1, handle = "example_01")))
        assertEquals(Identity.POSSIBLE, identityOf(fakeAccount(2, displayName = "Example Name")))
        assertEquals(Identity.UNKNOWN_CREATOR, identityOf(fakeAccount(3, displayName = null)))
        assertEquals(Identity.UNKNOWN_CREATOR, identityOf(fakeAccount(4, displayName = "  ")))
    }

    @Test
    fun defaultHandleFromFlagOrHandle() {
        assertTrue(hasDefaultHandle(fakeAccount(1, handle = "user1234567")))
        assertTrue(hasDefaultHandle(fakeAccount(2, handle = "example_01", isDefaultHandle = true)))
        assertFalse(hasDefaultHandle(fakeAccount(3, handle = "user12")))
        assertFalse(hasDefaultHandle(fakeAccount(4)))
    }

    @Test
    fun rowModelFromSummary() {
        val row = AccountRowModel.from(
            fakeSummary(fakeAccount(9, displayName = "Example Shop", status = AccountStatus.BLOCKED), tags = listOf("shop_anchor"), sightings = 5),
        )
        assertEquals(9L, row.id)
        assertEquals("Example Shop", row.displayName)
        assertNull(row.handle)
        assertEquals(Identity.POSSIBLE, row.identity)
        assertEquals(AccountStatus.BLOCKED, row.status)
        assertEquals(listOf("shop_anchor"), row.tags)
        assertEquals(5, row.sightingCount)
        assertEquals(2_000L, row.lastSeenAt)
    }

    @Test
    fun blankDisplayNameBecomesNull() {
        assertNull(AccountRowModel.from(fakeSummary(fakeAccount(1, displayName = " "))).displayName)
    }

    @Test
    fun knownTagsTranslateAndOthersAreHumanized() {
        assertEquals(UiText.Res(R.string.screens_tag_paid_ad), tagLabel("paid_ad"))
        assertEquals(UiText.Res(R.string.screens_tag_impersonator), tagLabel("impersonator"))
        assertEquals(UiText.Plain("Made up tag"), tagLabel("made_up_tag"))
        assertEquals("", humanizeTag(""))
    }

    @Test
    fun statusLabels() {
        assertEquals(R.string.screens_status_unblocked_suspected, AccountStatus.UNBLOCKED_SUSPECTED.labelRes())
        assertEquals(R.string.screens_status_false_positive, AccountStatus.FALSE_POSITIVE.labelRes())
    }
}
