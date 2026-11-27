package io.github.dreadnought0.feedclean.ui.data

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar

/**
 * Export full / export public / import / clear all data, all through the system file picker.
 * Hosts [DataTransferContent], which scrolls itself (do not wrap it in another vertical scroll).
 *
 * Pushed destination: draws its own Scaffold/top bar with a back arrow.
 *
 * @param action the action the user picked in Settings, or null. All actions are on one screen, so this is
 *   currently informational only.
 * @param onBack pop this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransferScreen(action: DataTransferAction?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = stringResource(R.string.ui_data_transfer_title), onBack = onBack) },
    ) { innerPadding ->
        DataTransferContent(Modifier.fillMaxSize().padding(innerPadding))
    }
}
