package io.github.dreadnought0.feedclean.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/**
 * Share target: TikTok → Share → "Log in FeedClean". Translucent; shows [ShareSheet] over TikTok.
 *
 * Reads the TikTok links in `EXTRA_TEXT`, attaches the service's latest feed snapshot when it is fresh
 * ([ShareContext.MAX_AGE_MILLIS]), and saves only when the user taps Save. Cancel or dismiss = nothing saved.
 * No network request happens unless the user taps Resolve.
 */
class ShareReceiverActivity : ComponentActivity() {

    private var sharedUrl: String = ""
    private var extraLinks: Int = 0
    private var screenContext: ScreenSnapshot.FeedVideo? = null

    private val viewModel: ShareViewModel by viewModels {
        ShareViewModel.factory(appContainer, sharedUrl, extraLinks, screenContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val links = TikTokLinks.extract(text)
        if (links.isEmpty()) {
            Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        sharedUrl = links.first()
        extraLinks = links.size - 1
        screenContext = ShareContext.pick(screenCandidates(), System.currentTimeMillis())

        setContent {
            FeedCleanTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.done) { if (state.done) finish() }
                ShareSheet(
                    state = state,
                    onResolveTapped = viewModel::onResolveTapped,
                    onTagToggled = viewModel::onTagToggled,
                    onNotesChanged = viewModel::onNotesChanged,
                    onSave = viewModel::onSave,
                    onCancel = ::finish,
                )
            }
        }
    }

    /**
     * Snapshots that may describe the shared post. By the time the share arrives, the service has usually
     * published TikTok's share sheet, so the latest snapshot alone is often not a FeedVideo; the last feed page
     * seen before the sheet opened is the shared post.
     */
    private fun screenCandidates(): List<ScreenSnapshot?> =
        listOf(ScreenStateHolder.snapshot.value, ScreenStateHolder.lastFeedVideo.value)
}
