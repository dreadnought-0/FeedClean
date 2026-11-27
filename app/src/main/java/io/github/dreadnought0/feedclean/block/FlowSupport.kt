package io.github.dreadnought0.feedclean.block

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.service.ScreenDriver
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

// Small helpers shared by the assisted-action executors (block/ and curate/NotInterested*).

/** Waits for a snapshot of type [T] that satisfies [accept]; null on timeout. */
internal suspend inline fun <reified T : ScreenSnapshot> ScreenDriver.awaitScreen(
    timeoutMillis: Long,
    pollMillis: Long,
    crossinline accept: (T) -> Boolean,
): T? = awaitSnapshot(timeoutMillis, pollMillis) { it is T && accept(it) } as T?

/** Snapshot type name for failure details, e.g. "Profile". Never includes on-screen content. */
internal val ScreenSnapshot.kindName: String
    get() = this::class.simpleName ?: "ScreenSnapshot"

/** Canonical handle form for comparisons: trimmed, no leading "@", lowercase. */
internal fun normalizeHandle(raw: String): String = raw.trim().removePrefix("@").lowercase(Locale.ROOT)

/**
 * Runs a UI progress callback. A failing callback must not derail a half-finished screen flow (that could leave
 * a dialog open), so its exceptions are dropped.
 */
internal inline fun reportProgress(callback: () -> Unit) {
    try {
        callback()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Intentionally ignored: progress reporting is cosmetic.
    }
}
