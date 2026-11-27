package io.github.dreadnought0.feedclean.block

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.ActionLogRepository
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Human-pace budget shared by every action TikTok can see: assisted blocks and "Not interested" taps, manual and
 * automated. Use ONE instance per process, shared by [BlockFlowExecutor] and
 * `NotInterestedFlow`, so they draw on the same budget.
 *
 * Semantics:
 *  - **Persisted source of truth.** Every attempt that was allowed to run is written to [ActionLogRepository], so
 *    restarting the app or the service doesn't reset the budget. An in-memory mirror of this process's own
 *    attempts is consulted too, and the stricter of the two wins, so a failed or late write can't open the gate
 *    early.
 *  - **Minimum interval.** An action may start only when at least `minSecondsBetweenActions` (floor 3 s) have
 *    passed since the most recent logged attempt, and no other action is in flight. Attempts are timestamped
 *    when they *finish*, so the interval is measured from the end of the previous action.
 *  - **Hourly cap.** At most `maxActionsPerHour` (1 to 120) attempts with a timestamp at or after `now - 1 h`.
 *  - **One at a time.** [runWithPermit] holds a [Mutex] for the whole action. A concurrent request is denied
 *    with [DenyReason.ACTION_IN_PROGRESS] rather than queued, so a second tap never runs later, unattended.
 *  - **Attempts count, not just successes.** The attempt is recorded when the action finishes, whether it
 *    succeeded, failed, threw, or was cancelled. Only denied requests are not recorded.
 *  - **Floors are re-applied here** ([FeedCleanSettings.sanitized]) even if the settings source forgot to.
 *  - Settings are read on every check, so changes apply immediately.
 *  - If the wall clock jumps backwards past the last logged attempt, actions stay denied until it catches up
 *    (fail-safe).
 *
 * @param settings current settings, read on every check.
 * @param clock wall-clock epoch millis. Must be the clock the persisted timestamps come from.
 */
class RateLimiter(
    private val actionLog: ActionLogRepository,
    private val settings: () -> FeedCleanSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Convenience constructor reading [settings]`.value` on every check. */
    constructor(
        actionLog: ActionLogRepository,
        settings: StateFlow<FeedCleanSettings>,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(actionLog, { settings.value }, clock)

    private val permit = Mutex()

    /** This process's own recent attempt timestamps (last hour), oldest first. Guarded by itself. */
    private val recentAttempts = ArrayDeque<Long>()

    /** True while an action holds the permit. */
    val isActionInFlight: Boolean
        get() = permit.isLocked

    /**
     * Whether an action could start right now. Advisory (for example, to show a countdown on the Block button):
     * [runWithPermit] re-checks atomically before running anything.
     */
    suspend fun check(): RateDecision {
        val limits = Limits.from(settings())
        if (permit.isLocked) return inProgress(limits)
        return evaluate(clock(), limits)
    }

    /**
     * Runs [action] if the budget allows it, holding the single action permit while it runs, then records the
     * attempt (kind [kind], account [accountId], verified = [isVerified] of the result, or false if the action
     * threw or was cancelled) to the action log.
     *
     * When denied, [action] is not invoked and nothing is recorded.
     *
     * @return [PermitOutcome.Ran] with the action's result, or [PermitOutcome.Denied].
     */
    suspend fun <T> runWithPermit(
        kind: ActionKind,
        accountId: Long?,
        isVerified: (T) -> Boolean,
        action: suspend () -> T,
    ): PermitOutcome<T> {
        val limits = Limits.from(settings())
        if (!permit.tryLock()) return PermitOutcome.Denied(inProgress(limits))
        try {
            val decision = evaluate(clock(), limits)
            if (decision is RateDecision.Denied) return PermitOutcome.Denied(decision)
            var verified = false
            try {
                val value = action()
                verified = isVerified(value)
                return PermitOutcome.Ran(value)
            } finally {
                withContext(NonCancellable) { recordAttempt(kind, accountId, verified) }
            }
        } finally {
            permit.unlock()
        }
    }

    private suspend fun recordAttempt(kind: ActionKind, accountId: Long?, verified: Boolean) {
        val at = clock()
        synchronized(recentAttempts) {
            recentAttempts.addLast(at)
            while (recentAttempts.isNotEmpty() && recentAttempts.first() < at - HOUR_MILLIS) {
                recentAttempts.removeFirst()
            }
        }
        actionLog.record(kind, at, accountId, verified)
    }

    private suspend fun evaluate(now: Long, limits: Limits): RateDecision {
        val last = listOfNotNull(
            actionLog.lastActionAt(),
            synchronized(recentAttempts) { recentAttempts.lastOrNull() },
        ).maxOrNull()
        val intervalWait = if (last == null) 0L else last + limits.minIntervalMillis - now
        val capWait = if (countSince(now - HOUR_MILLIS) >= limits.maxPerHour) {
            hourlyCapWait(now, limits.maxPerHour)
        } else {
            0L
        }
        return when {
            intervalWait <= 0 && capWait <= 0 -> RateDecision.Allowed
            capWait >= intervalWait -> RateDecision.Denied(capWait, DenyReason.HOURLY_CAP)
            else -> RateDecision.Denied(intervalWait, DenyReason.MIN_INTERVAL)
        }
    }

    /** Attempts at or after [sinceMillis]: the stricter of the persisted log and this process's own memory. */
    private suspend fun countSince(sinceMillis: Long): Int {
        val inMemory = synchronized(recentAttempts) { recentAttempts.count { it >= sinceMillis } }
        return maxOf(actionLog.countSince(sinceMillis), inMemory)
    }

    /**
     * Millis until the trailing-hour count drops below [maxPerHour]. Finds the smallest window start `s` with
     * `count(s) < maxPerHour` by binary search over [countSince] (at most ~22 lookups, and only while capped);
     * the next action is allowed at `s + 1 h`.
     */
    private suspend fun hourlyCapWait(now: Long, maxPerHour: Int): Long {
        var stillCapped = now - HOUR_MILLIS
        var belowCap = now + 1
        // Entries dated in the future (clock moved backwards): fail safe with a full hour.
        if (countSince(belowCap) >= maxPerHour) return HOUR_MILLIS
        while (belowCap - stillCapped > 1) {
            val mid = stillCapped + (belowCap - stillCapped) / 2
            if (countSince(mid) < maxPerHour) belowCap = mid else stillCapped = mid
        }
        return (belowCap + HOUR_MILLIS - now).coerceAtLeast(1)
    }

    private fun inProgress(limits: Limits) = RateDecision.Denied(limits.minIntervalMillis, DenyReason.ACTION_IN_PROGRESS)

    private data class Limits(val minIntervalMillis: Long, val maxPerHour: Int) {
        companion object {
            fun from(settings: FeedCleanSettings): Limits {
                val safe = settings.sanitized()
                return Limits(safe.minSecondsBetweenActions * 1_000L, safe.maxActionsPerHour)
            }
        }
    }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
    }
}

/** Result of [RateLimiter.check]. */
sealed interface RateDecision {
    /** An action may start now. */
    data object Allowed : RateDecision

    /**
     * No action may start now.
     *
     * @property retryAfterMillis how long until the budget allows the next action. For
     *   [DenyReason.ACTION_IN_PROGRESS] this is a lower bound (the in-flight action's duration is unknown).
     */
    data class Denied(val retryAfterMillis: Long, val reason: DenyReason) : RateDecision
}

/** Which rule denied an action. Persisted by name; never rename entries. */
enum class DenyReason {
    /** Another assisted action is running right now. */
    ACTION_IN_PROGRESS,

    /** Less than `minSecondsBetweenActions` since the last attempt. */
    MIN_INTERVAL,

    /** `maxActionsPerHour` attempts already in the trailing hour. */
    HOURLY_CAP,
}

/** Result of [RateLimiter.runWithPermit]. */
sealed interface PermitOutcome<out T> {
    /** The action ran (and its attempt was recorded). */
    data class Ran<out T>(val value: T) : PermitOutcome<T>

    /** The action was not run and nothing was recorded. */
    data class Denied(val decision: RateDecision.Denied) : PermitOutcome<Nothing>
}
