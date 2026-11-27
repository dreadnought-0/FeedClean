package io.github.dreadnought0.feedclean.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.stats.SectionNote
import io.github.dreadnought0.feedclean.ui.stats.currentLocale
import io.github.dreadnought0.feedclean.ui.stats.relativeDateTimeText
import io.github.dreadnought0.feedclean.ui.stats.resolve
import java.time.ZoneId

/**
 * Account header: name, handle (or "unconfirmed — possible match"), status, sighting count, handle source,
 * category / Shop tier, first and last seen, the default-handle note, and "Open in TikTok" for accounts with
 * a confirmed handle. There is deliberately no Block button: blocking happens on TikTok's own screen via
 * the overlay card.
 *
 * @param onOpenInTikTok opens the profile in TikTok; only shown when the account has a handle.
 */
@Composable
fun AccountHeaderCard(state: AccountDetailUiState, onOpenInTikTok: (String) -> Unit, modifier: Modifier = Modifier) {
    val account = state.account ?: return
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = accountTitle(account.displayName?.takeIf { it.isNotBlank() }, account.handle),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = when (state.identity) {
                    Identity.CONFIRMED -> stringResource(R.string.screens_handle, account.handle.orEmpty())
                    Identity.POSSIBLE -> stringResource(R.string.screens_detail_handle_unconfirmed)
                    Identity.UNKNOWN_CREATOR -> stringResource(R.string.screens_unknown_creator_summary)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = if (state.identity == Identity.CONFIRMED) FontStyle.Normal else FontStyle.Italic,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccountStatusPill(account.status)
                Text(
                    text = pluralStringResource(R.plurals.screens_sighting_count, state.sightingCount, state.sightingCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            account.handleSource?.let { source ->
                DetailLine(stringResource(R.string.screens_detail_handle_source, stringResource(source.labelRes())))
            }
            account.accountCategory?.takeIf { it.isNotBlank() }?.let {
                DetailLine(stringResource(R.string.screens_detail_category, it))
            }
            account.shopTier?.takeIf { it.isNotBlank() }?.let {
                DetailLine(stringResource(R.string.screens_detail_shop_tier, it))
            }
            DetailLine(
                stringResource(
                    R.string.screens_detail_first_seen,
                    relativeDateTimeText(account.firstSeenAt, state.nowMillis, zone, locale).resolve(),
                ),
            )
            DetailLine(
                stringResource(
                    R.string.screens_detail_last_seen,
                    relativeDateTimeText(account.lastSeenAt, state.nowMillis, zone, locale).resolve(),
                ),
            )
            if (state.isDefaultHandle) {
                Text(
                    text = stringResource(R.string.screens_detail_default_handle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            account.handle?.let { handle ->
                OutlinedButton(onClick = { onOpenInTikTok(handle) }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.screens_detail_open_in_tiktok))
                }
                SectionNote(stringResource(R.string.screens_detail_open_note))
            }
        }
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
