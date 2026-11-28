package io.github.dreadnought0.feedclean.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.Glass

/**
 * Standard top app bar for every FeedClean screen: transparent over the glass gradient, centered title, and a
 * round frosted back button on pushed screens.
 *
 * @param title screen title.
 * @param onBack shows a back button when non-null (pushed destinations); top-level tabs pass null.
 * @param actions trailing actions (e.g. Home's "n/5 actions armed" badge).
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
    CenterAlignedTopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Glass.CardStrong,
                    contentColor = Glass.Ink,
                    border = BorderStroke(1.dp, Glass.Border),
                    modifier = Modifier.padding(start = 12.dp).size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.ui_nav_back),
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Glass.BgTop.copy(alpha = 0.92f),
            titleContentColor = Glass.Ink,
            navigationIconContentColor = Glass.Ink,
            actionIconContentColor = Glass.Ink,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Preview
@Composable
private fun FeedCleanTopAppBarPreview() {
    FeedCleanTheme {
        FeedCleanTopAppBar(title = "Debug", onBack = {})
    }
}
