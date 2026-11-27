package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAccountRepository()

    private fun TestScope.viewModel(id: Long = 7L): AccountDetailViewModel {
        val vm = AccountDetailViewModel(id, repository, clock = { 5_000L }, zone = { ZoneId.of("UTC") })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.pickerCandidates.collect {} }
        return vm
    }

    @Test
    fun stateCombinesAccountTagsTimelineAndOriginal() = runTest {
        repository.summaries.value = listOf(
            fakeSummary(fakeAccount(7, handle = "user20240001", status = AccountStatus.BLOCKED, suspectedOriginalOf = 3)),
            fakeSummary(fakeAccount(3, handle = "example_original")),
        )
        val manualTag = Tag(1, "impersonator", TagKind.MANUAL)
        repository.allTags.value = listOf(manualTag, Tag(2, "paid_ad", TagKind.SIGNAL))
        repository.tagAssignments.value = mapOf(7L to listOf(TagAssignment(manualTag, TagSource.MANUAL, 1L)))
        repository.timelines.value = mapOf(
            7L to listOf(TimelineEntry.BlockEntry(BlockEvent(1, 7, 4_000L, BlockMethod.ASSISTED, true, null, null))),
        )
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals(7L, state.account?.id)
        assertEquals(Identity.CONFIRMED, state.identity)
        assertTrue(state.isDefaultHandle)
        assertEquals(listOf("impersonator"), state.tags.manual)
        assertEquals(listOf("block-1"), state.timeline.map { it.key })
        assertEquals(0, state.sightingCount)
        assertEquals(AccountRef(3, "Example Creator 3", "example_original", Identity.CONFIRMED), state.suspectedOriginal)
        assertEquals(listOf(AccountStatus.FALSE_POSITIVE, AccountStatus.SEEN, AccountStatus.UNBLOCKED_SUSPECTED), state.statusActions)
    }

    @Test
    fun missingAccountLoadsAsNotFound() = runTest {
        val vm = viewModel(id = 99L)
        assertTrue(vm.uiState.value.loaded)
        assertNull(vm.uiState.value.account)
        assertTrue(vm.uiState.value.statusActions.isEmpty())
    }

    @Test
    fun editsOnlyTouchLocalRecordsWithManualSource() = runTest {
        repository.summaries.value = listOf(fakeSummary(fakeAccount(7)))
        val vm = viewModel()

        vm.setStatus(AccountStatus.FALSE_POSITIVE)
        vm.saveNotes("  ")
        vm.saveNotes(" checked twice ")
        assertTrue(vm.addManualTag("  Copy Cat "))
        assertFalse(vm.addManualTag("   "))
        vm.removeManualTag("impersonator")
        vm.clearOriginal()

        assertEquals(
            listOf(
                "setStatus(7, FALSE_POSITIVE)",
                "setNotes(7, null)",
                "setNotes(7, checked twice)",
                "addTag(7, copy_cat, MANUAL)",
                "removeTag(7, impersonator, MANUAL)",
                "setSuspectedOriginal(7, null)",
            ),
            repository.writes,
        )
    }

    @Test
    fun pickerSearchesOtherAccountsOnlyWhileOpen() = runTest {
        repository.summaries.value = listOf(
            fakeSummary(fakeAccount(7, displayName = "Example Copy")),
            fakeSummary(fakeAccount(5, displayName = "Example Original")),
            fakeSummary(fakeAccount(6, displayName = "Unrelated Sample")),
        )
        val vm = viewModel()
        assertFalse(vm.pickerVisible.value)
        assertTrue(vm.pickerCandidates.value.isEmpty())
        val queriesBeforeOpen = repository.accountQueries.size

        vm.openPicker()
        assertTrue(vm.pickerVisible.value)
        assertEquals(listOf(5L, 6L), vm.pickerCandidates.value.map { it.id })
        assertTrue(repository.accountQueries.size > queriesBeforeOpen)

        vm.setPickerQuery("example")
        assertEquals(listOf(5L), vm.pickerCandidates.value.map { it.id })

        vm.pickOriginal(5L)
        assertFalse(vm.pickerVisible.value)
        assertTrue(vm.pickerCandidates.value.isEmpty())
        assertEquals("setSuspectedOriginal(7, 5)", repository.writes.last())
    }
}
