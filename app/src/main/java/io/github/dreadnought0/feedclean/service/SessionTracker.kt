package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.data.SessionRepository
import io.github.dreadnought0.feedclean.detect.Signals
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Opt-in session tracking (Settings → Data & privacy → Session tracking). Pure over
 * [SessionRepository].
 *
 * A session is open exactly while tracking is enabled AND TikTok is in the foreground. Every new page is
 * counted once, skipped or not. Nothing is recorded while tracking is off.
 */
class SessionTracker(
    private val sessions: SessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** The open session, or null. */
    @Volatile
    var currentId: Long? = null
        private set

    /** Closes sessions left open by a previous run (the service was killed). Call once on start. */
    suspend fun recoverOnStart() = mutex.withLock {
        sessions.endAllOpen(clock())
        currentId = null
    }

    /** Opens or closes the session to match [trackingEnabled] && [foreground]. */
    suspend fun sync(trackingEnabled: Boolean, foreground: Boolean) = mutex.withLock {
        val want = trackingEnabled && foreground
        val open = currentId
        when {
            want && open == null -> currentId = sessions.start(clock())
            !want && open != null -> {
                currentId = null
                sessions.end(open, clock())
            }
        }
    }

    /** Counts one page in the open session (no-op without one). */
    suspend fun recordPage(signals: Signals, skipped: Boolean) = mutex.withLock {
        currentId?.let { sessions.recordPage(it, signals, skipped) }
    }

    /** Closes the open session (service stopping). */
    suspend fun close() = sync(trackingEnabled = false, foreground = false)
}
