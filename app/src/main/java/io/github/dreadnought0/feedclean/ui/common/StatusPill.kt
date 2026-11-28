package io.github.dreadnought0.feedclean.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Small rounded status label with a ✓ or ✗ glyph, e.g. "Service running" / "TikTok not in foreground".
 *
 * @param ok selects the ✓ (secondary container) or ✗ (surface variant) look; the text carries the meaning.
 */
@Composable
fun StatusPill(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (ok) colors.secondaryContainer else colors.surfaceContainerHighest,
        contentColor = if (ok) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun StatusPillPreview() {
    FeedCleanTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(8.dp)) {
            StatusPill("Service running", ok = true)
            StatusPill("Not in foreground", ok = false)
        }
    }
}
