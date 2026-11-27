package io.github.dreadnought0.feedclean.ui.suggested

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Community-list review queue. Not built yet, so
 * this is an explanatory empty state: what the queue will do, that every block stays a user tap under the
 * shared rate limit, that nothing is fetched today, and where to read more. It reads no data and makes no
 * network requests, so it needs no ViewModel.
 *
 * Pushed destination: draws its own Scaffold/top bar with a back arrow.
 *
 * @param onBack pop this screen.
 */
@Composable
fun SuggestedScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = stringResource(R.string.ui_suggested_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(R.string.screens_suggested_heading),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.screens_suggested_status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            ExplainerCard(R.string.screens_suggested_what_title, R.string.screens_suggested_what_body)
            ExplainerCard(R.string.screens_suggested_human_title, R.string.screens_suggested_human_body)
            ExplainerCard(R.string.screens_suggested_network_title, R.string.screens_suggested_network_body)
            ExplainerCard(R.string.screens_suggested_rules_title, R.string.screens_suggested_rules_body)
            ExplainerCard(R.string.screens_suggested_more_title, R.string.screens_suggested_more_body)
        }
    }
}

@Composable
private fun ExplainerCard(titleRes: Int, bodyRes: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(bodyRes), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun SuggestedScreenPreview() {
    FeedCleanTheme(dynamicColor = false) {
        SuggestedScreen(onBack = {})
    }
}
