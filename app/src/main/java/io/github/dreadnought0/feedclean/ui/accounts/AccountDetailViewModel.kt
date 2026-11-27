package io.github.dreadnought0.feedclean.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.ui.stats.midnightTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Most accounts listed in the "suspected original" picker. */
const val PICKER_LIMIT: Int = 50

/** A short reference to another account (the suspected original). */
data class AccountRef(val id: Long, val displayName: String?, val handle: String?, val identity: Identity) {
    companion object {
        /** Reference to [account]. */
        fun of(account: Account) = AccountRef(
            id = account.id,
            displayName = account.displayName?.takeIf { it.isNotBlank() },
            handle = account.handle,
            identity = identityOf(account),
        )
    }
}

/** Everything the Account detail screen shows. */
data class AccountDetailUiState(
    /** False until the first database emission. */
    val loaded: Boolean = false,
    /** Null after loading means the account no longer exists (e.g. merged into the handle-keyed account). */
    val account: Account? = null,
    val identity: Identity = Identity.POSSIBLE,
    val isDefaultHandle: Boolean = false,
    val tags: AccountTagGroups = AccountTagGroups(),
    val timeline: List<TimelineRowText> = emptyList(),
    val sightingCount: Int = 0,
    val suspectedOriginal: AccountRef? = null,
    val statusActions: List<AccountStatus> = emptyList(),
    /** "Now" for relative date labels; advances at local midnight. */
    val nowMillis: Long = 0L,
)

/**
 * Backs the Account detail screen. Every action here only changes FeedClean's own records (status, tags,
 * notes, suspected original); none of them can trigger anything in TikTok.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    clock: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel(), AccountDetailActions {

    private val account = accountRepository.observeAccount(accountId)

    private val original = account.map { it?.suspectedOriginalOf }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else accountRepository.observeAccount(id)
    }

    private val tags = combine(accountRepository.observeTags(accountId), accountRepository.observeAllTags(), ::groupTags)

    /** Combined state; collection starts and stops with the screen. */
    val uiState: StateFlow<AccountDetailUiState> = combine(
        account,
        original,
        tags,
        accountRepository.observeTimeline(accountId),
        midnightTicks(clock, zone),
    ) { a, o, t, entries, now ->
        AccountDetailUiState(
            loaded = true,
            account = a,
            identity = a?.let(::identityOf) ?: Identity.POSSIBLE,
            isDefaultHandle = a?.let(::hasDefaultHandle) ?: false,
            tags = t,
            timeline = timelineRows(entries),
            sightingCount = entries.count { it is TimelineEntry.SightingEntry },
            suspectedOriginal = o?.let(AccountRef::of),
            statusActions = a?.let { statusActions(it.status) }.orEmpty(),
            nowMillis = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountDetailUiState())

    private val pickerOpen = MutableStateFlow(false)
    private val pickerQuery = MutableStateFlow("")

    /** Whether the "suspected original of…" picker is open. */
    val pickerVisible: StateFlow<Boolean> = pickerOpen.asStateFlow()

    /** Other accounts matching the picker's search (never this account); empty while the picker is closed. */
    val pickerCandidates: StateFlow<List<AccountRowModel>> =
        combine(pickerOpen, pickerQuery.map { it.trim() }.distinctUntilChanged(), ::Pair)
            .flatMapLatest { (open, query) ->
                if (!open) {
                    flowOf(emptyList())
                } else {
                    accountRepository.observeAccounts(query).map { list ->
                        list.asSequence()
                            .filter { it.account.id != accountId }
                            .take(PICKER_LIMIT)
                            .map(AccountRowModel::from)
                            .toList()
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Records the status (FeedClean's record only). */
    override fun setStatus(status: AccountStatus) = launch { accountRepository.setStatus(accountId, status) }

    /** Saves the notes; blank clears them. */
    override fun saveNotes(text: String) = launch { accountRepository.setNotes(accountId, normalizeNotes(text)) }

    /** Adds a manual tag. Returns false (and does nothing) when [raw] is blank or too long. */
    override fun addManualTag(raw: String): Boolean {
        val name = normalizeManualTag(raw) ?: return false
        launch { accountRepository.addTag(accountId, name, TagSource.MANUAL) }
        return true
    }

    /** Removes the MANUAL assignment of [name]; an auto-detected assignment of the same tag stays. */
    override fun removeManualTag(name: String) =
        launch { accountRepository.removeTag(accountId, name, TagSource.MANUAL) }

    /** Opens the picker with an empty search. */
    override fun openPicker() {
        pickerQuery.value = ""
        pickerOpen.value = true
    }

    /** Picker search text changed. */
    fun setPickerQuery(query: String) {
        pickerQuery.value = query
    }

    /** Closes the picker without changing anything. */
    fun dismissPicker() {
        pickerOpen.value = false
    }

    /** Marks this account as a suspected impersonator of [originalId] and closes the picker. */
    fun pickOriginal(originalId: Long) {
        pickerOpen.value = false
        launch { accountRepository.setSuspectedOriginal(accountId, originalId) }
    }

    /** Clears the suspected-original link. */
    override fun clearOriginal() = launch { accountRepository.setSuspectedOriginal(accountId, null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Factory for [accountId], reading the repository from [container]. */
        fun factory(accountId: Long, container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountDetailViewModel(accountId, container.accountRepository) }
        }
    }
}
