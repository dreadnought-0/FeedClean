package io.github.dreadnought0.feedclean.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.common.GlassCard
import io.github.dreadnought0.feedclean.ui.stats.UiText
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import kotlinx.coroutines.launch

/**
 * Account detail: header (identity, status, category, first/last seen, default-handle and suspected-original
 * info, "Open in TikTok"), status actions, tags, notes, and the timeline of sightings and block events,
 * newest first. Nothing here can trigger an action in TikTok; there is no Block button (blocking happens on
 * TikTok's screen via the overlay card, with the user's tap).
 *
 * Pushed destination: draws its own Scaffold/top bar with a back arrow.
 *
 * @param accountId `accounts.id` of the account to show.
 * @param onBack pop this screen.
 */
@Composable
fun AccountDetailScreen(accountId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel: AccountDetailViewModel = viewModel(
        key = "account-detail-$accountId",
        factory = AccountDetailViewModel.factory(accountId, container),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerVisible by viewModel.pickerVisible.collectAsStateWithLifecycle()
    val candidates by viewModel.pickerCandidates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openFailed = stringResource(R.string.screens_detail_open_failed)
    val notesSaved = stringResource(R.string.screens_detail_notes_saved)

    AccountDetailContent(
        state = state,
        actions = viewModel,
        onBack = onBack,
        onOpenInTikTok = { handle ->
            if (!openProfileInTikTok(context, handle)) scope.launch { snackbarHostState.showSnackbar(openFailed) }
        },
        onSaveNotes = { text ->
            viewModel.saveNotes(text)
            scope.launch { snackbarHostState.showSnackbar(notesSaved) }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
    if (pickerVisible) {
        SuspectedOriginalDialog(
            candidates = candidates,
            onQueryChange = viewModel::setPickerQuery,
            onPick = viewModel::pickOriginal,
            onDismiss = viewModel::dismissPicker,
        )
    }
}

/** Stateless Account detail screen. */
@Composable
fun AccountDetailContent(
    state: AccountDetailUiState,
    actions: AccountDetailActions,
    onBack: () -> Unit,
    onOpenInTikTok: (String) -> Unit,
    onSaveNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val account = state.account
    val title = account?.let { accountTitle(it.displayName?.takeIf(String::isNotBlank), it.handle) }
        ?: stringResource(R.string.ui_account_detail_title)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = title, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            !state.loaded -> Unit
            account == null -> Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                Text(stringResource(R.string.screens_detail_not_found_title), style = MaterialTheme.typography.titleMedium)
                GlassCard(modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.screens_detail_not_found_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") { AccountHeaderCard(state = state, onOpenInTikTok = onOpenInTikTok) }
                item(key = "status") { AccountStatusCard(state = state, actions = actions) }
                item(key = "tags") { AccountTagsCard(tags = state.tags, actions = actions) }
                item(key = "notes") { AccountNotesCard(account = account, onSave = onSaveNotes) }
                item(key = "original") { SuspectedOriginalCard(original = state.suspectedOriginal, actions = actions) }
                item(key = "timeline_title") {
                    Text(
                        text = stringResource(R.string.screens_detail_timeline_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                if (state.timeline.isEmpty()) {
                    item(key = "timeline_empty") {
                        Text(
                            text = stringResource(R.string.screens_detail_timeline_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.timeline, key = { it.key }) { row ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TimelineItem(row = row, nowMillis = state.nowMillis)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Obviously fake account for previews. */
private fun previewDetailState(): AccountDetailUiState {
    val now = 1_795_000_000_000L
    val account = Account(
        id = 7, handle = "user20240001", handleSource = HandleSource.DIALOG, displayName = "Example Shop Copy",
        displayNameSkeleton = "example shop copy", firstSeenAt = now - 9 * 86_400_000L, lastSeenAt = now - 3_600_000L,
        status = AccountStatus.BLOCKED, accountCategory = "Shop", shopTier = null, isDefaultHandle = true,
        notes = "Copies the captions of another shop.", suspectedOriginalOf = 3, profilePhotoHash = null,
    )
    return AccountDetailUiState(
        loaded = true,
        account = account,
        identity = Identity.CONFIRMED,
        isDefaultHandle = true,
        tags = AccountTagGroups(
            autoDetected = listOf("organic_shop", "shop_anchor"),
            manual = listOf("impersonator"),
            suggestions = listOf("ai_generated", "bot_like"),
        ),
        timeline = listOf(
            TimelineRowText(
                key = "block-1", kind = TimelineKind.BLOCK, atMillis = now - 3_500_000L,
                title = UiText.Res(R.string.screens_timeline_blocked), outcome = TimelineOutcome.VERIFIED,
                signalLabels = emptyList(), details = listOf(UiText.Res(R.string.screens_block_method_assisted)),
            ),
            TimelineRowText(
                key = "sighting-1", kind = TimelineKind.SIGHTING, atMillis = now - 3_600_000L,
                title = UiText.Res(R.string.screens_timeline_auto_skipped), outcome = TimelineOutcome.NONE,
                signalLabels = listOf(R.string.screens_signal_organic_shop, R.string.screens_signal_shop_anchor),
                details = listOf(
                    UiText.Res(R.string.screens_timeline_reason, listOf(UiText.Res(R.string.screens_skip_reason_organic_shop))),
                ),
            ),
        ),
        sightingCount = 1,
        suspectedOriginal = AccountRef(id = 3, displayName = "Example Shop", handle = "example_shop", identity = Identity.CONFIRMED),
        statusActions = statusActions(AccountStatus.BLOCKED),
        nowMillis = now,
    )
}

@Preview(showBackground = true, heightDp = 2200)
@Composable
private fun AccountDetailScreenPreview() {
    FeedCleanTheme {
        AccountDetailContent(
            state = previewDetailState(),
            actions = NoOpAccountDetailActions,
            onBack = {},
            onOpenInTikTok = {},
            onSaveNotes = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountDetailNotFoundPreview() {
    FeedCleanTheme {
        AccountDetailContent(
            state = AccountDetailUiState(loaded = true),
            actions = NoOpAccountDetailActions,
            onBack = {},
            onOpenInTikTok = {},
            onSaveNotes = {},
        )
    }
}
