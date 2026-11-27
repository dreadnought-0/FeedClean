package io.github.dreadnought0.feedclean.data

import io.github.dreadnought0.feedclean.detect.Signals

/** Opt-in session tracking (Settings → Data & privacy → Session tracking). Callers check the setting. */
interface SessionRepository {
    /** Opens a session and returns its id. */
    suspend fun start(nowMillis: Long): Long

    /** Closes the session (no-op if already closed). */
    suspend fun end(id: Long, nowMillis: Long)

    /** Closes every open session, e.g. when the service restarts after being killed. Returns how many. */
    suspend fun endAllOpen(nowMillis: Long): Int

    /**
     * Counts one page (call once per new page signature, detections or not). [signals] increments: ads =
     * isPaidAd, promoted = isPromoted, shop = isOrganicShop, live = isLive || isLiveShopping;
     * [skipped] = the page was skipped (auto or manual).
     */
    suspend fun recordPage(id: Long, signals: Signals, skipped: Boolean)
}
