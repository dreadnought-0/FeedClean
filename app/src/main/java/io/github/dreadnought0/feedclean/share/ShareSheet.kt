package io.github.dreadnought0.feedclean.share

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.TagNames

/**
 * The share bottom sheet: link, detected account ("possible" until confirmed), Resolve, tag
 * chips, notes, Save / Cancel. Dismissing the sheet is Cancel: nothing is saved.
 *
 * @param onResolveTapped receives the [ResolveTap] created in the Resolve button's click handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    state: ShareUiState,
    onResolveTapped: (ResolveTap) -> Unit,
    onTagToggled: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // Opens fully (no half-expanded stop): the sheet is short and the notes field needs the keyboard.
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
            LinkSection(state)
            AccountSection(state)
            ResolveSection(state, onResolveTapped)
            TagSection(state.tags, onTagToggled)
            OutlinedTextField(
                value = state.notes,
                onValueChange = onNotesChanged,
                label = { Text(stringResource(R.string.share_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.share_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.share_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave, enabled = !state.saving && state.resolve != ResolveUi.Running) {
                    Text(stringResource(R.string.share_save))
                }
            }
        }
    }
}

@Composable
private fun LinkSection(state: ShareUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.share_link_label), style = MaterialTheme.typography.labelLarge)
        Text(
            state.url,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.extraLinkCount > 0) {
            Text(
                pluralStringResource(R.plurals.share_extra_links, state.extraLinkCount, state.extraLinkCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountSection(state: ShareUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.share_account_label), style = MaterialTheme.typography.labelLarge)
        val confirmed = state.confirmedHandle
        if (confirmed != null) {
            Text(stringResource(R.string.share_handle_confirmed, confirmed), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (state.linkHandle != null) R.string.share_handle_from_link else R.string.share_handle_from_resolve,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.displayName?.let {
            Text(stringResource(R.string.share_possible_name, it), style = MaterialTheme.typography.bodyMedium)
        }
        if (confirmed == null) {
            state.probableHandle?.let {
                Text(stringResource(R.string.share_possible_handle, it), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.displayName == null && state.probableHandle == null) {
                Text(stringResource(R.string.share_account_unknown), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.contextDiscarded) {
            Text(
                stringResource(R.string.share_context_discarded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResolveSection(state: ShareUiState, onResolveTapped: (ResolveTap) -> Unit) {
    val resolve = state.resolve
    val result = (resolve as? ResolveUi.Done)?.result
    if (!state.canResolve && resolve == ResolveUi.Idle) return
    if (result is ResolveResult.Resolved) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                // The ONLY place a ResolveTap is created (see ResolveTap and NetworkCallSiteGuardTest).
                onClick = { onResolveTapped(ResolveTap.fromResolveButton(System.currentTimeMillis())) },
                enabled = state.canResolve,
            ) {
                Text(stringResource(R.string.share_resolve))
            }
            if (resolve == ResolveUi.Running) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_resolving), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val message = result?.let(::resolveMessage)
        if (message != null) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                stringResource(R.string.share_resolve_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSection(tags: ShareTagSelection, onTagToggled: (String) -> Unit) {
    val selected = tags.selectedTags
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.share_tags_signal), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareTagSelection.SIGNAL_TAGS.forEach { tag ->
                FilterChip(
                    selected = tag in selected,
                    onClick = { onTagToggled(tag) },
                    label = { Text(tagLabel(tag)) },
                )
            }
        }
        Text(stringResource(R.string.share_tags_manual), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareTagSelection.MANUAL_TAGS.forEach { tag ->
                FilterChip(
                    selected = tag in selected,
                    onClick = { onTagToggled(tag) },
                    label = { Text(tagLabel(tag)) },
                )
            }
        }
    }
}

@StringRes
private fun resolveMessage(result: ResolveResult): Int? = when (result) {
    is ResolveResult.Resolved -> null
    is ResolveResult.Failed -> R.string.share_resolve_failed
    is ResolveResult.Refused -> R.string.share_resolve_refused
    is ResolveResult.Unresolved -> when (result.reason) {
        UnresolvedReason.NO_LOCATION -> R.string.share_resolve_no_location
        UnresolvedReason.SECOND_HOP -> R.string.share_resolve_second_hop
        UnresolvedReason.NO_HANDLE_IN_LOCATION -> R.string.share_resolve_no_handle
        UnresolvedReason.UNEXPECTED_LOCATION -> R.string.share_resolve_unexpected
    }
}

/** Human label for a tag name; unknown (custom) tags show their name. */
@Composable
internal fun tagLabel(tag: String): String = when (tag) {
    TagNames.PAID_AD -> stringResource(R.string.tag_paid_ad)
    TagNames.PROMOTED -> stringResource(R.string.tag_promoted)
    TagNames.SHOP_ANCHOR -> stringResource(R.string.tag_shop_anchor)
    TagNames.ORGANIC_SHOP -> stringResource(R.string.tag_organic_shop)
    TagNames.LIVE_SHOPPING -> stringResource(R.string.tag_live_shopping)
    TagNames.AI_GENERATED -> stringResource(R.string.tag_ai_generated)
    TagNames.SEXUAL_CONTENT -> stringResource(R.string.tag_sexual_content)
    TagNames.IMPERSONATOR -> stringResource(R.string.tag_impersonator)
    TagNames.REPOSTED -> stringResource(R.string.tag_reposted)
    TagNames.BOT_LIKE -> stringResource(R.string.tag_bot_like)
    else -> tag
}
