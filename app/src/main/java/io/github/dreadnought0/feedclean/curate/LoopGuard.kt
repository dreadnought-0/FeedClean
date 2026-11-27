package io.github.dreadnought0.feedclean.curate

/**
 * Runaway-scroll protection for auto-skip. Pure state; not thread-safe (used from one coroutine).
 *
 *  - **One attempt per page.** A page signature that auto-skip has already tried (successfully or not) is never
 *    auto-skipped again in this service run, so scrolling back to a skipped post shows it.
 *  - **Consecutive cap.** After `cap` auto-skips in a row with no kept page in between, auto-skip pauses. Only a
 *    user click ([resume]) un-pauses it; kept pages reset the count but not the pause.
 *
 * @param maxRemembered how many attempted signatures to remember (oldest forgotten first).
 */
class LoopGuard(private val maxRemembered: Int = DEFAULT_MAX_REMEMBERED) {
    private val attempted = LinkedHashSet<String>()

    /** Auto-skips since the last kept page. */
    var consecutiveSkips: Int = 0
        private set

    /** True after the consecutive cap was reached, until the user resumes. */
    var isPaused: Boolean = false
        private set

    /** Count that triggered the current pause (for the bubble's "Paused after N skips"). */
    var pausedAfter: Int = 0
        private set

    fun wasAttempted(signature: String): Boolean = signature in attempted

    /** Remembers that auto-skip is about to act on [signature]. */
    fun markAttempt(signature: String) {
        attempted.remove(signature)
        attempted.add(signature)
        while (attempted.size > maxRemembered) attempted.remove(attempted.first())
    }

    /** Counts one completed auto-skip. Returns true if this one reached [cap] and paused auto-skip. */
    fun onAutoSkipped(cap: Int): Boolean {
        consecutiveSkips++
        if (!isPaused && consecutiveSkips >= cap.coerceAtLeast(1)) {
            isPaused = true
            pausedAfter = consecutiveSkips
            return true
        }
        return false
    }

    /** A page was shown without being auto-skipped: the run of consecutive skips is over. */
    fun onPageKept() {
        consecutiveSkips = 0
    }

    /** The user tapped "resume". Clears the pause and the count; remembered signatures stay. */
    fun resume() {
        isPaused = false
        pausedAfter = 0
        consecutiveSkips = 0
    }

    companion object {
        const val DEFAULT_MAX_REMEMBERED = 500
    }
}
