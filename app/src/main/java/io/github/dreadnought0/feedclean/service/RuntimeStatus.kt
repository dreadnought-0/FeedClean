package io.github.dreadnought0.feedclean.service

/**
 * Auto-skip state published by the service for the Debug screen and the app UI.
 *
 * @property pausedByLoopGuard the consecutive-skip cap paused auto-skip; only a user click resumes it.
 * @property lastOutcome short diagnostic summary of the last auto-skip (no screen content).
 */
data class AutoSkipStatus(
    val pausedByLoopGuard: Boolean = false,
    val consecutiveSkips: Int = 0,
    val skipping: Boolean = false,
    val lastOutcome: String? = null,
    val lastOutcomeAtMillis: Long? = null,
)

/**
 * An assisted action started from the overlay card (block, Not interested, skip, capture): running or finished.
 *
 * @property kind e.g. "block", "not_interested", "skip", "capture".
 * @property step current executor step name while running (BlockStep / NotInterestedStep).
 * @property result short result summary once finished (enum names only, no screen content).
 */
data class ActionStatus(
    val kind: String,
    val running: Boolean,
    val step: String? = null,
    val result: String? = null,
    val atMillis: Long,
)
