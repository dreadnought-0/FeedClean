package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.ui.common.GlassCard
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.stats.resolve
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Accounts tab: searchable (handle, display name), filterable (status and tag chips) list of logged
 * accounts, most recently seen first. Provisional accounts are labelled as possible matches.
 *
 * Top-level tab: draws its own Scaffold/top bar (no back arrow); the bottom bar is provided by the shell.
 *
 * @param onOpenAccount open an account's detail screen by `accounts.id`.
 */
@Composable
fun AccountsScreen(onOpenAccount: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsContent(
        state = state,
        onQueryChange = viewModel::setQuery,
        onToggleStatus = viewModel::toggleStatus,
        onToggleTag = viewModel::toggleTag,
        onClearFilters = viewModel::clearFilters,
        onOpenAccount = onOpenAccount,
        modifier = modifier,
    )
}

/** Stateless Accounts list. The search text is held here so typing never waits on the database. */
@Composable
fun AccountsContent(
    state: AccountsUiState,
    onQueryChange: (String) -> Unit,
    onToggleStatus: (AccountStatus) -> Unit,
    onToggleTag: (String) -> Unit,
    onClearFilters: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf(state.filter.query) }
    val setQuery: (String) -> Unit = {
        query = it
        onQueryChange(it)
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = stringResource(R.string.ui_tab_accounts)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(query = query, onQueryChange = setQuery)
            ChipRow(
                description = stringResource(R.string.screens_accounts_status_filters),
                labels = statusFilterOrder.map { stringResource(it.labelRes()) },
                selected = statusFilterOrder.map { it in state.filter.statuses },
                onToggle = { onToggleStatus(statusFilterOrder[it]) },
            )
            if (state.tagChips.isNotEmpty()) {
                ChipRow(
                    description = stringResource(R.string.screens_accounts_tag_filters),
                    labels = state.tagChips.map { tagLabel(it).resolve() },
                    selected = state.tagChips.map { it in state.filter.tags },
                    onToggle = { onToggleTag(state.tagChips[it]) },
                )
            }
            Spacer(Modifier.height(6.dp))
            when {
                !state.loaded -> Unit
                state.accounts.isEmpty() -> AccountsEmptyState(
                    filtered = state.filter.isActive,
                    onClearFilters = {
                        query = ""
                        onClearFilters()
                    },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.accounts, key = { it.id }) { row ->
                        GlassCard(contentPadding = PaddingValues(0.dp)) {
                            AccountListItem(row = row, nowMillis = state.nowMillis, onClick = { onOpenAccount(row.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.screens_accounts_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.screens_accounts_clear_search))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A horizontally scrolling row of filter chips (multi-select). */
@Composable
private fun ChipRow(description: String, labels: List<String>, selected: List<Boolean>, onToggle: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = selected[index]
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(index) },
                label = { Text(label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun AccountsEmptyState(filtered: Boolean, onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                if (filtered) R.string.screens_accounts_no_match_title else R.string.screens_accounts_empty_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (filtered) R.string.screens_accounts_no_match_body else R.string.screens_accounts_empty_body,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (filtered) {
            TextButton(onClick = onClearFilters) { Text(stringResource(R.string.screens_accounts_clear_filters)) }
        }
    }
}

/** Obviously fake rows for previews. */
private fun previewAccountsState(): AccountsUiState {
    val now = 1_795_000_000_000L
    return AccountsUiState(
        loaded = true,
        filter = AccountFilter(statuses = setOf(AccountStatus.SEEN, AccountStatus.BLOCKED)),
        tagChips = listOf("ai_generated", "organic_shop", "paid_ad", "shop_anchor"),
        accounts = listOf(
            AccountRowModel(
                id = 1, displayName = "Example Shop One", handle = "example_shop_01", identity = Identity.CONFIRMED,
                isDefaultHandle = false, status = AccountStatus.BLOCKED, tags = listOf("organic_shop", "shop_anchor"),
                sightingCount = 7, lastSeenAt = now - 3_600_000L,
            ),
            AccountRowModel(
                id = 2, displayName = "Sample Creator", handle = null, identity = Identity.POSSIBLE,
                isDefaultHandle = false, status = AccountStatus.SEEN, tags = listOf("paid_ad"),
                sightingCount = 2, lastSeenAt = now - 26 * 3_600_000L,
            ),
        ),
        nowMillis = now,
    )
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun AccountsScreenPreview() {
    FeedCleanTheme {
        AccountsContent(
            state = previewAccountsState(),
            onQueryChange = {},
            onToggleStatus = {},
            onToggleTag = {},
            onClearFilters = {},
            onOpenAccount = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun AccountsScreenEmptyPreview() {
    FeedCleanTheme {
        AccountsContent(
            state = AccountsUiState(loaded = true),
            onQueryChange = {},
            onToggleStatus = {},
            onToggleTag = {},
            onClearFilters = {},
            onOpenAccount = {},
        )
    }
}
