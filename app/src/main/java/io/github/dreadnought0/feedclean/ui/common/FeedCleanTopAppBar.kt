package io.github.dreadnought0.feedclean.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Standard top app bar for every FeedClean screen.
 *
 * @param title screen title.
 * @param onBack shows a back arrow when non-null (pushed destinations); top-level tabs pass null.
 * @param actions trailing action icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedCleanTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.ui_nav_back),
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Preview
@Composable
private fun FeedCleanTopAppBarPreview() {
    FeedCleanTheme(dynamicColor = false) {
        FeedCleanTopAppBar(title = "Debug", onBack = {})
    }
}
