package io.github.dreadnought0.feedclean.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * "Suspected impersonator of…" picker: search the other accounts and tap the one this account copies.
 * Only records the link (`AccountRepository.setSuspectedOriginal`); nothing else happens.
 *
 * @param candidates matching accounts, never the current one.
 * @param onQueryChange search text changed (the text itself is held here).
 * @param onPick the chosen original's id.
 */
@Composable
fun SuspectedOriginalDialog(
    candidates: List<AccountRowModel>,
    onQueryChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screens_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.screens_picker_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.screens_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.id }) { row -> CandidateRow(row, onClick = { onPick(row.id) }) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.screens_picker_cancel)) }
        },
    )
}

@Composable
private fun CandidateRow(row: AccountRowModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = accountTitle(row.displayName, row.handle),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = identityLine(row.identity, row.handle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun SuspectedOriginalDialogPreview() {
    FeedCleanTheme {
        SuspectedOriginalDialog(
            candidates = listOf(
                AccountRowModel(
                    id = 5, displayName = "Example Original", handle = "example_original", identity = Identity.CONFIRMED,
                    isDefaultHandle = false, status = AccountStatus.SEEN, tags = emptyList(), sightingCount = 4,
                    lastSeenAt = 0L,
                ),
                AccountRowModel(
                    id = 6, displayName = "Example Original", handle = null, identity = Identity.POSSIBLE,
                    isDefaultHandle = false, status = AccountStatus.SEEN, tags = emptyList(), sightingCount = 1,
                    lastSeenAt = 0L,
                ),
            ),
            onQueryChange = {},
            onPick = {},
            onDismiss = {},
        )
    }
}
