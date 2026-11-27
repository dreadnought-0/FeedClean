package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import kotlinx.coroutines.delay

/**
 * The polling loop behind [ScreenDriver.awaitSnapshot]: takes a fresh snapshot, returns it if [predicate]
 * matches, otherwise waits [pollMillis] and tries again until [timeoutMillis] has passed (measured with
 * [nowMillis]). At least one snapshot is always taken. Returns null on timeout. Pure.
 */
suspend fun pollForSnapshot(
    snapshot: suspend () -> ScreenSnapshot,
    timeoutMillis: Long,
    pollMillis: Long,
    nowMillis: () -> Long,
    predicate: (ScreenSnapshot) -> Boolean,
): ScreenSnapshot? {
    val deadline = nowMillis() + timeoutMillis.coerceAtLeast(0)
    val step = pollMillis.coerceAtLeast(MIN_POLL_MILLIS)
    while (true) {
        val current = snapshot()
        if (predicate(current)) return current
        val remaining = deadline - nowMillis()
        if (remaining <= 0) return null
        delay(minOf(step, remaining))
    }
}

/** Never poll faster than this, whatever the caller asks. */
private const val MIN_POLL_MILLIS = 20L
