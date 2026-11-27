package io.github.dreadnought0.feedclean.ui.audit

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
 * Unblock-audit report from TikTok's official "Download your data" export.
 * Hosts [AuditContent], which scrolls itself (do not wrap it in another vertical scroll).
 *
 * Pushed destination: draws its own Scaffold/top bar with a back arrow.
 *
 * @param onBack pop this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { FeedCleanTopAppBar(title = stringResource(R.string.ui_audit_title), onBack = onBack) },
    ) { innerPadding ->
        AuditContent(Modifier.fillMaxSize().padding(innerPadding))
    }
}
