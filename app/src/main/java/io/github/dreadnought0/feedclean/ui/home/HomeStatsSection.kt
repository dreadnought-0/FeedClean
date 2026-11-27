package io.github.dreadnought0.feedclean.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.ui.stats.CountTileGrid
import io.github.dreadnought0.feedclean.ui.stats.SectionNote
import io.github.dreadnought0.feedclean.ui.stats.SessionRowModel
import io.github.dreadnought0.feedclean.ui.stats.StatsSectionCard
import io.github.dreadnought0.feedclean.ui.stats.countTiles
import io.github.dreadnought0.feedclean.ui.stats.shareOf
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Today's counts on Home as a compact tile grid: detected, organic Shop, paid ads, promoted, auto-skipped,
 * skipped by hand, Not interested and blocked, plus "seen" and the latest session's Shop/ad share when
 * session tracking is on (and a note saying so when it is off). Rolls over at local midnight.
 *
 * In Studio previews (no app container) it renders fake data.
 */
@Composable
fun HomeStatsSection(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        HomeStatsContent(state = previewHomeStatsState, modifier = modifier)
        return
    }
    val container = LocalContext.current.appContainer
    val viewModel: HomeStatsViewModel = viewModel(factory = HomeStatsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeStatsContent(state = state, modifier = modifier)
}

/** Stateless "Today" card. */
@Composable
fun HomeStatsContent(state: HomeStatsUiState, modifier: Modifier = Modifier) {
    StatsSectionCard(title = stringResource(R.string.screens_home_today), modifier = modifier) {
        if (!state.loaded) return@StatsSectionCard
        CountTileGrid(countTiles(state.counts, state.sessionTrackingEnabled))
        val session = state.latestSession
        when {
            !state.sessionTrackingEnabled -> SectionNote(stringResource(R.string.screens_seen_needs_tracking))
            session != null -> Text(
                text = stringResource(
                    R.string.screens_home_latest_session,
                    session.shop.percent,
                    session.ads.percent,
                    pluralStringResource(R.plurals.screens_session_videos, session.videosSeen, session.videosSeen),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Obviously fake numbers for previews. */
private val previewHomeStatsState = HomeStatsUiState(
    loaded = true,
    counts = DailyCounts(
        seen = 184,
        detected = 27,
        organicShop = 15,
        paidAds = 8,
        promoted = 4,
        autoSkipped = 14,
        manuallySkipped = 1,
        notInterested = 3,
        blocked = 2,
    ),
    sessionTrackingEnabled = true,
    latestSession = SessionRowModel(
        id = 1, startedAt = 0L, endedAt = 1_800_000L, videosSeen = 96,
        shop = shareOf(0.16), ads = shareOf(0.05), promoted = shareOf(0.02), skipped = 12,
        skippedShare = shareOf(0.12),
    ),
)

@Preview(showBackground = true)
@Composable
private fun HomeStatsSectionPreview() {
    FeedCleanTheme(dynamicColor = false) {
        HomeStatsContent(state = previewHomeStatsState, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeStatsSectionTrackingOffPreview() {
    FeedCleanTheme(dynamicColor = false) {
        HomeStatsContent(
            state = previewHomeStatsState.copy(sessionTrackingEnabled = false, latestSession = null),
            modifier = Modifier.padding(16.dp),
        )
    }
}
