package io.github.dreadnought0.feedclean.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.stats.midnightTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId

/** Everything the Accounts list shows. */
data class AccountsUiState(
    /** False until the first database emission. */
    val loaded: Boolean = false,
    /** The filter the [accounts] were queried with. */
    val filter: AccountFilter = AccountFilter(),
    val tagChips: List<String> = emptyList(),
    val accounts: List<AccountRowModel> = emptyList(),
    /** "Now" for relative "last seen" labels; advances at local midnight. */
    val nowMillis: Long = 0L,
)

/**
 * Backs the Accounts tab: search text and status/tag chips → `AccountRepository.observeAccounts`.
 * The search field keeps its own text state in Compose and reports changes through [setQuery].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountsViewModel(
    private val accountRepository: AccountRepository,
    clock: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {

    private val filter = MutableStateFlow(AccountFilter())

    private val accounts = filter.map { it.toQueryArgs() }.distinctUntilChanged().flatMapLatest { args ->
        accountRepository.observeAccounts(args.query, args.statuses, args.tags).map { list ->
            list.map(AccountRowModel::from)
        }
    }

    /** Combined state; collection starts and stops with the screen. */
    val uiState: StateFlow<AccountsUiState> = combine(
        filter,
        accounts,
        accountRepository.observeAllTagNames(),
        midnightTicks(clock, zone),
    ) { f, rows, tagNames, now ->
        AccountsUiState(
            loaded = true,
            filter = f,
            tagChips = tagChipNames(tagNames, f.tags),
            accounts = rows,
            nowMillis = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsUiState())

    /** Search text changed. */
    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    /** Status chip tapped. */
    fun toggleStatus(status: AccountStatus) = filter.update { it.toggleStatus(status) }

    /** Tag chip tapped. */
    fun toggleTag(tag: String) = filter.update { it.toggleTag(tag) }

    /** "Clear filters": search text and every chip. */
    fun clearFilters() = filter.update { AccountFilter() }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Factory reading the repository from [container]. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountsViewModel(container.accountRepository) }
        }
    }
}
