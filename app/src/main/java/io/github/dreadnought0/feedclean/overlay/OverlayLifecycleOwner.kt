package io.github.dreadnought0.feedclean.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Minimal lifecycle for a ComposeView hosted in a service window (no Activity): Compose needs a
 * [LifecycleOwner], a [SavedStateRegistryOwner], and a [ViewModelStoreOwner] on the view tree.
 *
 * Lifecycle: [attach] → RESUMED when the window is shown; [destroy] → DESTROYED (and the ViewModelStore
 * cleared) when it is removed. One owner per window; not reusable after [destroy]. Main thread only.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    /** Installs this owner on [root] and moves to RESUMED. Call right before adding the window. */
    fun attach(root: View) {
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        registry.currentState = Lifecycle.State.RESUMED
    }

    /** Moves to DESTROYED. Call after removing the window. */
    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
