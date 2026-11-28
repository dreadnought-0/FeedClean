package io.github.dreadnought0.feedclean

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.dreadnought0.feedclean.ui.navigation.FeedCleanNavHost
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.GlassBackground

/** Single activity hosting the Compose UI (edge-to-edge over the glass gradient, Navigation 3). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The glass look is always light: dark status/nav bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            FeedCleanTheme {
                GlassBackground {
                    FeedCleanNavHost()
                }
            }
        }
    }
}
