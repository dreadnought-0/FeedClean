package io.github.dreadnought0.feedclean.share

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proof that a network request was asked for by the user's tap on the share sheet's **Resolve** button.
 * [ShortLinkResolver.resolve] refuses to run without a fresh, unused one.
 *
 * **Create one ONLY inside the Resolve button's `onClick`**, at the moment of the click. Never create one from
 * a timer, a `LaunchedEffect`, restored state, or any other code path, and never keep one for later. A guard
 * test (`NetworkCallSiteGuardTest`) checks that exactly one source file calls [fromResolveButton].
 *
 * Enforced at runtime: a tap is accepted only while fresh (up to [MAX_AGE_MILLIS] after the click) and only
 * once, so one tap sends at most one request.
 */
class ResolveTap private constructor(
    /** Time of the click in epoch millis. */
    val createdAtMillis: Long,
) {
    private val consumed = AtomicBoolean(false)

    /** True once the resolver has used this tap. */
    val isConsumed: Boolean
        get() = consumed.get()

    /** True if the tap is at most [MAX_AGE_MILLIS] old at [nowMillis] (a tap from the future is not fresh). */
    fun isFresh(nowMillis: Long): Boolean = (nowMillis - createdAtMillis) in 0..MAX_AGE_MILLIS

    /** Marks the tap as used. Returns false if it had already been used. Resolver only. */
    internal fun consume(): Boolean = consumed.compareAndSet(false, true)

    override fun toString(): String = "ResolveTap(createdAtMillis=$createdAtMillis)"

    companion object {
        /** How long after the click the tap is accepted. */
        const val MAX_AGE_MILLIS: Long = 10_000

        /** Creates the tap for a click on Resolve that just happened. Call ONLY from that button's `onClick`. */
        fun fromResolveButton(nowMillis: Long): ResolveTap = ResolveTap(nowMillis)
    }
}
