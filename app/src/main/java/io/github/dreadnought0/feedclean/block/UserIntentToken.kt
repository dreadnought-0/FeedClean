package io.github.dreadnought0.feedclean.block

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proof that an assisted action (a block, a manual "Not interested") was started by the user's own tap.
 *
 * **Create one ONLY inside the click handler of a user-facing control** (the overlay card's Block or
 * Not interested button, the Suggested queue's Block button), at the moment of the click. Never create one
 * from detection, curation, auto-skip, timers, restored UI state, or any other code path that is not a direct
 * response to a tap, and never cache one for later.
 *
 * The executors enforce this at runtime:
 *  - a token is only accepted while [isFresh] (default: up to 10 s after the click), so a click can't be saved
 *    and replayed later;
 *  - a token is single-use: the first executor run that accepts it consumes it, so one click starts at most one
 *    action, even if a caller tries to reuse the token in a loop.
 *
 * The constructor is private; [fromUserClick] is the only way to obtain a token.
 */
class UserIntentToken private constructor(
    /** Which control was tapped, e.g. `"overlay_card.block"`. For logs and the Debug screen. */
    val source: String,
    /** Time of the click in epoch millis, from the same clock the executors use. */
    val createdAtMillis: Long,
) {
    private val consumed = AtomicBoolean(false)

    /** True once an executor has accepted (and so used up) this token. */
    val isConsumed: Boolean
        get() = consumed.get()

    /**
     * True if the token is at most [maxAgeMillis] old at [nowMillis]. A token dated in the future (clock moved
     * backwards) is not fresh.
     */
    fun isFresh(nowMillis: Long, maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): Boolean =
        (nowMillis - createdAtMillis) in 0..maxAgeMillis

    /** Marks the token as used. Returns false if it had already been used. Executors only. */
    internal fun consume(): Boolean = consumed.compareAndSet(false, true)

    override fun toString(): String = "UserIntentToken(source=$source, createdAtMillis=$createdAtMillis)"

    companion object {
        /** How long after the click a token is accepted. */
        const val DEFAULT_MAX_AGE_MILLIS: Long = 10_000

        /**
         * Creates a token for a click that just happened. Call this ONLY from a click handler, with the current
         * time of the executors' clock (normally `System.currentTimeMillis()`).
         *
         * @param source names the tapped control, e.g. `"overlay_card.block"`; must not be blank.
         */
        fun fromUserClick(source: String, nowMillis: Long): UserIntentToken {
            require(source.isNotBlank()) { "source must name the tapped control" }
            return UserIntentToken(source, nowMillis)
        }
    }
}

/**
 * Checks [token] for an executor run and consumes it if acceptable.
 *
 * @return null when the token is accepted, otherwise why it was rejected.
 */
internal fun acceptUserIntent(token: UserIntentToken?, nowMillis: Long, maxAgeMillis: Long): RejectReason? =
    when {
        token == null -> RejectReason.MISSING_TOKEN
        !token.isFresh(nowMillis, maxAgeMillis) -> RejectReason.STALE_TOKEN
        !token.consume() -> RejectReason.TOKEN_ALREADY_USED
        else -> null
    }
