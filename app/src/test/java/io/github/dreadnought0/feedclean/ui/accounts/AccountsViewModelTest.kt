package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAccountRepository()

    private fun TestScope.viewModel(): AccountsViewModel {
        val vm = AccountsViewModel(repository, clock = { 1_000_000L }, zone = { ZoneId.of("UTC") })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun listsEveryAccountWithNoFilter() = runTest {
        repository.summaries.value = listOf(
            fakeSummary(fakeAccount(1, handle = "example_01")),
            fakeSummary(fakeAccount(2, displayName = "Sample Name")),
        )
        repository.tagNames.value = listOf("paid_ad")
        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertFalse(state.filter.isActive)
        assertEquals(listOf(1L, 2L), state.accounts.map { it.id })
        assertEquals(listOf(Identity.CONFIRMED, Identity.POSSIBLE), state.accounts.map { it.identity })
        assertEquals(listOf("paid_ad"), state.tagChips)
        assertEquals(1_000_000L, state.nowMillis)
        assertEquals(listOf(AccountQueryArgs("", emptySet(), emptySet())), repository.accountQueries)
    }

    @Test
    fun filterStateBecomesQueryArguments() = runTest {
        val vm = viewModel()
        vm.setQuery("  @Example ")
        vm.toggleStatus(AccountStatus.BLOCKED)
        vm.toggleTag("paid_ad")

        assertEquals(AccountQueryArgs("@Example", setOf(AccountStatus.BLOCKED), setOf("paid_ad")), repository.accountQueries.last())
        assertTrue(vm.uiState.value.filter.isActive)
        assertEquals(listOf("paid_ad"), vm.uiState.value.tagChips)

        vm.clearFilters()
        assertEquals(AccountQueryArgs("", emptySet(), emptySet()), repository.accountQueries.last())
        assertFalse(vm.uiState.value.filter.isActive)
    }

    @Test
    fun whitespaceOnlyEditsDoNotRequery() = runTest {
        val vm = viewModel()
        vm.setQuery("shop")
        val count = repository.accountQueries.size
        vm.setQuery("shop  ")
        assertEquals(count, repository.accountQueries.size)
    }

    @Test
    fun listFollowsTheDatabase() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.accounts.isEmpty())
        repository.summaries.value = listOf(fakeSummary(fakeAccount(3, status = AccountStatus.FALSE_POSITIVE)))
        assertEquals(AccountStatus.FALSE_POSITIVE, vm.uiState.value.accounts.single().status)
    }
}
