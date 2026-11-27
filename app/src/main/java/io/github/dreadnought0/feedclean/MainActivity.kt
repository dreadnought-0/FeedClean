package io.github.dreadnought0.feedclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dreadnought0.feedclean.ui.navigation.FeedCleanNavHost
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme

/** Single activity hosting the Compose UI (edge-to-edge, Navigation 3). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FeedCleanTheme {
                FeedCleanNavHost()
            }
        }
    }
}
