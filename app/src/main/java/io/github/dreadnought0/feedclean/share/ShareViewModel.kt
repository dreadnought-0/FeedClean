package io.github.dreadnought0.feedclean.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of the Resolve control. */
sealed interface ResolveUi {
    /** Resolve is available and has not been tapped yet. */
    data object Idle : ResolveUi

    /** The single request is in flight. */
    data object Running : ResolveUi

    data class Done(val result: ResolveResult) : ResolveUi
}

/** Everything the share sheet shows. */
data class ShareUiState(
    val url: String,
    /** Further TikTok links in the shared text that are not logged. */
    val extraLinkCount: Int,
    /** Display name from the screen ("possible": not confirmed). */
    val displayName: String?,
    /** Heuristic handle from the screen ("possible"). */
    val probableHandle: String?,
    /** Screen context was found but ignored because the link names a different account. */
    val contextDiscarded: Boolean,
    /** Handle read from the shared link itself (no request needed). */
    val linkHandle: String?,
    val resolve: ResolveUi,
    val tags: ShareTagSelection,
    val notes: String = "",
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Saved; the activity finishes. */
    val done: Boolean = false,
) {
    /** Handle from the link or from a successful Resolve. */
    val confirmedHandle: String?
        get() = linkHandle ?: ((resolve as? ResolveUi.Done)?.result as? ResolveResult.Resolved)?.handle

    /** Resolve makes sense: no confirmed handle yet, an https allow-listed link, and no conclusive answer yet. */
    val canResolve: Boolean
        get() = confirmedHandle == null && TikTokLinks.isResolvable(url) &&
            (resolve == ResolveUi.Idle || (resolve as? ResolveUi.Done)?.result is ResolveResult.Failed)
}

/**
 * Share sheet logic. Nothing here starts a network request on its own: [onResolveTapped] needs the
 * [ResolveTap] created in the Resolve button's click handler.
 */
class ShareViewModel(
    url: String,
    extraLinkCount: Int,
    context: ScreenSnapshot.FeedVideo?,
    private val saver: ShareSaver,
    private val resolver: ShortLinkResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private var screenContext: ScreenSnapshot.FeedVideo? = context

    private val _state = MutableStateFlow(initialState(url, extraLinkCount))

    /** Current sheet state. */
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private fun initialState(url: String, extraLinkCount: Int): ShareUiState {
        val linkHandle = TikTokLinks.handleFromUrl(url)
        val discarded = ShareContext.contradicts(screenContext, linkHandle)
        if (discarded) screenContext = null
        return ShareUiState(
            url = url,
            extraLinkCount = extraLinkCount,
            displayName = screenContext?.displayName,
            probableHandle = screenContext?.probableHandle,
            contextDiscarded = discarded,
            linkHandle = linkHandle,
            resolve = ResolveUi.Idle,
            tags = ShareTagSelection(signals = screenContext?.signals ?: Signals()),
        )
    }

    /** The user tapped Resolve. [tap] must come from that click. Sends at most one request. */
    fun onResolveTapped(tap: ResolveTap) {
        val current = _state.value
        if (!current.canResolve) return
        _state.update { it.copy(resolve = ResolveUi.Running) }
        viewModelScope.launch {
            val result = resolver.resolve(current.url, tap)
            _state.update { state ->
                val resolvedHandle = (result as? ResolveResult.Resolved)?.handle
                if (ShareContext.contradicts(screenContext, resolvedHandle)) {
                    // The screen showed someone else: drop its name and signals, keep the user's own tags.
                    screenContext = null
                    state.copy(
                        resolve = ResolveUi.Done(result),
                        displayName = null,
                        probableHandle = null,
                        contextDiscarded = true,
                        tags = state.tags.copy(signals = Signals()),
                    )
                } else {
                    state.copy(resolve = ResolveUi.Done(result))
                }
            }
        }
    }

    fun onTagToggled(tag: String) = _state.update { it.copy(tags = it.tags.toggle(tag)) }

    fun onNotesChanged(notes: String) = _state.update { it.copy(notes = notes.take(MAX_NOTES_LENGTH)) }

    /** Saves the share. On success [ShareUiState.done] becomes true. */
    fun onSave() {
        val current = _state.value
        if (current.saving || current.done) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                saver.save(
                    ShareSaveRequest(
                        url = current.url,
                        context = screenContext,
                        confirmedHandle = current.confirmedHandle,
                        signals = current.tags.signals,
                        manualTags = current.tags.manualTags,
                        notes = current.notes,
                    ),
                    nowMillis = clock(),
                )
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    companion object {
        /** Notes longer than this are cut off. */
        const val MAX_NOTES_LENGTH = 2_000

        /** Factory wired to the app's repositories. */
        fun factory(
            container: AppContainer,
            url: String,
            extraLinkCount: Int,
            context: ScreenSnapshot.FeedVideo?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ShareViewModel(
                    url = url,
                    extraLinkCount = extraLinkCount,
                    context = context,
                    saver = ShareSaver(container.accountRepository, container.sightingRepository),
                    resolver = ShortLinkResolver(),
                )
            }
        }
    }
}
