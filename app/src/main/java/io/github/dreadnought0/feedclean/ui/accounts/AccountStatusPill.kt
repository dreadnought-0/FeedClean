package io.github.dreadnought0.feedclean.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Small rounded label with an account's status. The text carries the meaning; the container tint only
 * groups statuses at a glance (blocked = secondary, false positive = tertiary, unblocked-suspected = error).
 */
@Composable
fun AccountStatusPill(status: AccountStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        AccountStatus.SEEN -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        AccountStatus.BLOCKED -> colors.secondaryContainer to colors.onSecondaryContainer
        AccountStatus.FALSE_POSITIVE -> colors.tertiaryContainer to colors.onTertiaryContainer
        AccountStatus.UNBLOCKED_SUSPECTED -> colors.errorContainer to colors.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content, modifier = modifier) {
        Text(
            text = stringResource(status.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview
@Composable
private fun AccountStatusPillPreview() {
    FeedCleanTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(8.dp)) {
            AccountStatus.entries.forEach { AccountStatusPill(it) }
        }
    }
}
