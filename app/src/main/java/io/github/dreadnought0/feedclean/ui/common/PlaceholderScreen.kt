package io.github.dreadnought0.feedclean.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Temporary full screen for destinations that are not built yet. Shows a top bar and a short
 * "coming later" card.
 *
 * @param title screen title.
 * @param message what will appear here.
 * @param onBack back arrow handler for pushed destinations; null for top-level tabs.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = title, onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlaceholderCard(message = message)
        }
    }
}

/** A simple outlined-looking card with one line of explanatory text. */
@Composable
fun PlaceholderCard(message: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    FeedCleanTheme(dynamicColor = false) {
        PlaceholderScreen(title = "Stats", message = "Stats appear here.")
    }
}
