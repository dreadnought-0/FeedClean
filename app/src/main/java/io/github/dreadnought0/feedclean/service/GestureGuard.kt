package io.github.dreadnought0.feedclean.service

/**
 * Lets injected gestures reach TikTok even where a FeedClean overlay (bubble, card) sits on top.
 *
 * A gesture is delivered to whatever window is at that point on screen, so before a fallback tap or long-press
 * the overlay layer makes its touchable windows pass touches through, and restores them afterwards.
 * Accessibility actions (ACTION_CLICK, …) go straight to the node and don't need this.
 */
interface GestureGuard {
    /** Runs [block] with every FeedClean overlay window untouchable. */
    suspend fun <T> passThrough(block: suspend () -> T): T

    companion object {
        /** No overlays to worry about (tests). */
        val NONE: GestureGuard = object : GestureGuard {
            override suspend fun <T> passThrough(block: suspend () -> T): T = block()
        }
    }
}
