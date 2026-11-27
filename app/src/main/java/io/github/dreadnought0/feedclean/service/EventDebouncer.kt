package io.github.dreadnought0.feedclean.service

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns the stream of accessibility events into one parse per burst. Pure.
 *
 * After the first event of a burst it waits until [quietMillis] pass without another event, but never longer
 * than [maxWaitMillis] after that first event: a playing video sends content-changed events continuously, so a
 * plain debounce would never fire.
 *
 * Use with a CONFLATED channel: events that arrive while [run]'s callback is busy collapse into one, which
 * starts the next burst as soon as the callback returns.
 */
class EventDebouncer(
    val quietMillis: Long = DEFAULT_QUIET_MILLIS,
    val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
) {
    init {
        require(quietMillis > 0 && maxWaitMillis >= quietMillis) { "need 0 < quietMillis <= maxWaitMillis" }
    }

    /**
     * Calls [onSettled] once per burst of [events] until the channel is closed.
     *
     * @param nowMillis monotonic clock in ms (virtual time in tests).
     */
    suspend fun run(events: ReceiveChannel<Unit>, nowMillis: () -> Long, onSettled: suspend () -> Unit) {
        while (true) {
            if (events.receiveCatching().isClosed) return
            val firstAt = nowMillis()
            var closed = false
            while (true) {
                val untilMax = firstAt + maxWaitMillis - nowMillis()
                if (untilMax <= 0) break
                val next = withTimeoutOrNull(minOf(quietMillis, untilMax)) { events.receiveCatching() } ?: break
                if (next.isClosed) {
                    closed = true
                    break
                }
            }
            onSettled()
            if (closed) return
        }
    }

    companion object {
        // Kept short so flagged posts flick past like a fast scroll.
        const val DEFAULT_QUIET_MILLIS = 150L
        const val DEFAULT_MAX_WAIT_MILLIS = 400L
    }
}
