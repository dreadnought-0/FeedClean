package io.github.dreadnought0.feedclean.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.stats.currentLocale
import io.github.dreadnought0.feedclean.ui.stats.relativeDateTimeText
import io.github.dreadnought0.feedclean.ui.stats.resolve
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.time.ZoneId

/** Display name, or "Unknown creator" for the shared no-name bucket. */
@Composable
fun accountTitle(displayName: String?, handle: String?): String =
    displayName ?: handle?.let { stringResource(R.string.screens_handle, it) } ?: stringResource(R.string.screens_unknown_creator)

/** "@handle", "Handle unknown — possible match", or the unknown-creator explanation. */
@Composable
fun identityLine(identity: Identity, handle: String?): String = when (identity) {
    Identity.CONFIRMED -> stringResource(R.string.screens_handle, handle.orEmpty())
    Identity.POSSIBLE -> stringResource(R.string.screens_handle_possible)
    Identity.UNKNOWN_CREATOR -> stringResource(R.string.screens_unknown_creator_summary)
}

/** Joined display labels of [tags]. */
@Composable
fun tagsLine(tags: List<String>): String =
    tags.map { tagLabel(it).resolve() }.joinToString(stringResource(R.string.screens_separator))

/**
 * One account row: name, @handle or the "possible match" label, default-handle hint, tags, sighting count
 * and last seen, with the status pill trailing. The whole row is one click target.
 */
@Composable
fun AccountListItem(
    row: AccountRowModel,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val lastSeen = relativeDateTimeText(row.lastSeenAt, nowMillis, ZoneId.systemDefault(), locale).resolve()
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = accountTitle(row.displayName, row.handle),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = identityLine(row.identity, row.handle),
                style = MaterialTheme.typography.bodyMedium,
                color = variant,
                fontStyle = if (row.identity == Identity.CONFIRMED) FontStyle.Normal else FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.isDefaultHandle) {
                Text(
                    text = stringResource(R.string.screens_default_handle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (row.tags.isNotEmpty()) {
                Text(
                    text = tagsLine(row.tags),
                    style = MaterialTheme.typography.bodyMedium,
                    color = variant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.screens_sighting_count, row.sightingCount, row.sightingCount) +
                    stringResource(R.string.screens_separator) +
                    stringResource(R.string.screens_last_seen, lastSeen),
                style = MaterialTheme.typography.bodySmall,
                color = variant,
            )
        }
        AccountStatusPill(row.status)
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountListItemPreview() {
    val now = 1_795_000_000_000L
    FeedCleanTheme {
        Column(horizontalAlignment = Alignment.Start) {
            AccountListItem(
                row = AccountRowModel(
                    id = 1, displayName = "Example Shop One", handle = "example_shop_01",
                    identity = Identity.CONFIRMED, isDefaultHandle = false, status = AccountStatus.BLOCKED,
                    tags = listOf("organic_shop", "shop_anchor"), sightingCount = 7, lastSeenAt = now - 3_600_000L,
                ),
                nowMillis = now,
                onClick = {},
            )
            AccountListItem(
                row = AccountRowModel(
                    id = 2, displayName = "Sample Creator", handle = null,
                    identity = Identity.POSSIBLE, isDefaultHandle = false, status = AccountStatus.SEEN,
                    tags = listOf("paid_ad"), sightingCount = 1, lastSeenAt = now - 30 * 3_600_000L,
                ),
                nowMillis = now,
                onClick = {},
            )
            AccountListItem(
                row = AccountRowModel(
                    id = 3, displayName = "Placeholder Name", handle = "user1234567",
                    identity = Identity.CONFIRMED, isDefaultHandle = true, status = AccountStatus.UNBLOCKED_SUSPECTED,
                    tags = emptyList(), sightingCount = 3, lastSeenAt = now - 9 * 86_400_000L,
                ),
                nowMillis = now,
                onClick = {},
            )
        }
    }
}
