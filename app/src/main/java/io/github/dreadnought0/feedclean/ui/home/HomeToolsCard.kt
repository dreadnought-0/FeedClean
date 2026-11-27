package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.common.FeedCleanRow
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/** Links from Home to the pushed destinations: Setup, Unblock audit, Suggested, Debug. */
@Composable
fun HomeToolsCard(
    onOpenSetup: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenSuggested: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.ui_home_tools),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            ToolRow(Icons.Filled.CheckCircle, stringResource(R.string.ui_setup_title), onOpenSetup)
            ToolRow(Icons.Filled.Search, stringResource(R.string.ui_audit_title), onOpenAudit)
            ToolRow(Icons.AutoMirrored.Filled.List, stringResource(R.string.ui_suggested_title), onOpenSuggested)
            ToolRow(FeedCleanIcons.Code, stringResource(R.string.ui_debug_title), onOpenDebug)
        }
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    FeedCleanRow(
        title = title,
        leading = { Icon(icon, contentDescription = null) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeToolsCardPreview() {
    FeedCleanTheme(dynamicColor = false) {
        HomeToolsCard(onOpenSetup = {}, onOpenAudit = {}, onOpenSuggested = {}, onOpenDebug = {})
    }
}
