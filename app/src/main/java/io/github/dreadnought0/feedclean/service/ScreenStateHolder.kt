package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide live state published by the accessibility service and read by the app UI (Debug screen,
 * Home dashboard) and the share target. Same process, so a plain singleton is enough.
 */
object ScreenStateHolder {
    private val _snapshot = MutableStateFlow<ScreenSnapshot?>(null)
    private val _lastFeedVideo = MutableStateFlow<ScreenSnapshot.FeedVideo?>(null)
    private val _serviceConnected = MutableStateFlow(false)
    private val _tiktokForeground = MutableStateFlow(false)
    private val _autoSkip = MutableStateFlow(AutoSkipStatus())
    private val _action = MutableStateFlow<ActionStatus?>(null)
    private val _recentEvents = MutableStateFlow<List<String>>(emptyList())
    private val _commands = MutableSharedFlow<RuntimeCommand>(extraBufferCapacity = 4)

    /** Latest parsed TikTok screen, or null if none yet. */
    val snapshot: StateFlow<ScreenSnapshot?> = _snapshot.asStateFlow()

    /** Most recent FeedVideo snapshot (kept even after an overlay/sheet appears on top). */
    val lastFeedVideo: StateFlow<ScreenSnapshot.FeedVideo?> = _lastFeedVideo.asStateFlow()

    /** True while the accessibility service is bound and running. */
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** True while a TikTok window is in the foreground. */
    val tiktokForeground: StateFlow<Boolean> = _tiktokForeground.asStateFlow()

    /** Auto-skip loop-guard state and last outcome. */
    val autoSkip: StateFlow<AutoSkipStatus> = _autoSkip.asStateFlow()

    /** The overlay card's running or last assisted action, or null. */
    val action: StateFlow<ActionStatus?> = _action.asStateFlow()

    /** Recent runtime events, newest first (at most [MAX_RECENT_EVENTS]); diagnostic text without screen content. */
    val recentEvents: StateFlow<List<String>> = _recentEvents.asStateFlow()

    /** Requests from the app UI to the running service (e.g. Home's "Resume" after the safety stop). */
    val commands: SharedFlow<RuntimeCommand> = _commands.asSharedFlow()

    /**
     * Asks the running service to do [command]. Returns false when no service is running to receive it. Only call
     * from a user's tap.
     */
    fun send(command: RuntimeCommand): Boolean = _serviceConnected.value && _commands.tryEmit(command)

    fun publishSnapshot(snapshot: ScreenSnapshot) {
        _snapshot.value = snapshot
        if (snapshot is ScreenSnapshot.FeedVideo) _lastFeedVideo.value = snapshot
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        if (!connected) {
            _tiktokForeground.value = false
            _lastFeedVideo.value = null
        }
    }

    fun setTikTokForeground(foreground: Boolean) {
        _tiktokForeground.value = foreground
    }

    fun publishAutoSkip(status: AutoSkipStatus) {
        _autoSkip.value = status
    }

    fun publishAction(status: ActionStatus?) {
        _action.value = status
    }

    /** Adds one line to [recentEvents]. */
    fun addEvent(line: String) {
        _recentEvents.update { (listOf(line) + it).take(MAX_RECENT_EVENTS) }
    }

    const val MAX_RECENT_EVENTS = 30
}

/** Commands the app UI can send to the running service. Each one must come from a user's tap. */
enum class RuntimeCommand {
    /** Clear the safety stop (too many skips in a row), same as tapping the paused bubble. */
    RESUME_AUTO_SKIP,
}
