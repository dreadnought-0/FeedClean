package io.github.dreadnought0.feedclean.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import java.text.NumberFormat

/**
 * Compact grid of count tiles (value over label), [columns] per row. Each tile is one TalkBack stop that
 * reads its value and label together. Used on Home and Stats.
 */
@Composable
fun CountTileGrid(tiles: List<CountTile>, modifier: Modifier = Modifier, columns: Int = 3) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { tile ->
                    CountTileView(
                        tile = tile,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CountTileView(tile: CountTile, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = NumberFormat.getIntegerInstance(locale).format(tile.value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(tile.kind.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CountTileGridPreview() {
    FeedCleanTheme(dynamicColor = false) {
        CountTileGrid(
            tiles = countTiles(
                DailyCounts(seen = 212, detected = 31, organicShop = 18, paidAds = 9, autoSkipped = 17, blocked = 2),
                sessionTrackingEnabled = true,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}
