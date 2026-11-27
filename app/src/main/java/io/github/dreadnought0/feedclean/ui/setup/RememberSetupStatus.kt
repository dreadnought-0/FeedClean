package io.github.dreadnought0.feedclean.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Current [SetupStatus], re-checked every time the screen resumes (e.g. coming back from system settings).
 * Returns null until the first check has run.
 */
@Composable
fun rememberSetupStatus(): SetupStatus? {
    val context = LocalContext.current
    val checker = remember(context) { SetupChecker(context) }
    var status by remember { mutableStateOf<SetupStatus?>(null) }
    LifecycleResumeEffect(checker) {
        status = checker.check()
        onPauseOrDispose { }
    }
    return status
}
