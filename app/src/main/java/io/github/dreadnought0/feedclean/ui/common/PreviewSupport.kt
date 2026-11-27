package io.github.dreadnought0.feedclean.ui.common

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat

/** Activity-result registry that ignores launches. Lets previews render composables that use SAF pickers. */
private object NoOpActivityResultRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) = Unit
    }
}

/** Wraps preview content with the locals that the Studio preview host does not provide. */
@Composable
fun PreviewHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalActivityResultRegistryOwner provides NoOpActivityResultRegistryOwner,
        content = content,
    )
}
