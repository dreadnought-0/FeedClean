package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.NameNormalizer
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo

/**
 * Tells when the feed shows a NEW page, so each video is processed once. Pure.
 *
 * Rules:
 *  - A page without a display name is incomplete (still binding, or the selector broke): never processed.
 *  - The same [FeedVideo.pageSignature] as the last accepted page is the same page, whatever snapshots of other
 *    screens (profile, menus, transitions) came in between.
 *  - A *refinement* is the same page: same creator, seen within [refineWindowMillis] of the previous page, and the
 *    only difference is a caption or sound that was missing before and has now appeared. TikTok can bind those
 *    a frame late; without this rule one video could be logged twice under two signatures.
 */
class PageTracker(private val refineWindowMillis: Long = DEFAULT_REFINE_WINDOW_MILLIS) {
    /** How a feed read relates to the last accepted page. */
    enum class Change {
        /** A new page: process it. */
        NEW,

        /** The same page as before (same signature). */
        SAME,

        /** The same page, refined: a late caption/sound changed its signature. The tracker now uses the new one. */
        REFINED,

        /** No display name yet: ignored and not remembered. */
        INCOMPLETE,
    }

    private var last: FeedVideo? = null

    /** Signature of the last accepted page, or null. */
    val lastSignature: String?
        get() = last?.pageSignature

    /** True if [feed] is a new page that should be processed now. Updates the tracker. */
    fun accept(feed: FeedVideo): Boolean = classify(feed) == Change.NEW

    /** Classifies [feed] against the last accepted page. Updates the tracker. */
    fun classify(feed: FeedVideo): Change {
        if (feed.displayName.isNullOrBlank()) return Change.INCOMPLETE
        val prev = last
        if (prev != null && prev.pageSignature == feed.pageSignature) return Change.SAME
        last = feed
        return if (prev != null && isRefinement(prev, feed)) Change.REFINED else Change.NEW
    }

    /** Forgets the last page (TikTok left the foreground, service restarted). */
    fun reset() {
        last = null
    }

    private fun isRefinement(prev: FeedVideo, next: FeedVideo): Boolean {
        if (next.capturedAtMillis - prev.capturedAtMillis !in 0..refineWindowMillis) return false
        if (!sameText(prev.displayName, next.displayName)) return false
        val captionFilled = prev.caption == null && next.caption != null
        val soundFilled = prev.soundDescription == null && next.soundDescription != null
        if (!captionFilled && !soundFilled) return false
        val captionOk = prev.caption == null || sameText(prev.caption, next.caption)
        val soundOk = prev.soundDescription == null || sameText(prev.soundDescription, next.soundDescription)
        return captionOk && soundOk
    }

    private fun sameText(a: String?, b: String?): Boolean =
        a != null && b != null && NameNormalizer.normalize(a) == NameNormalizer.normalize(b)

    companion object {
        const val DEFAULT_REFINE_WINDOW_MILLIS = 1_500L
    }
}
