package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.Signals

/**
 * The post the overlay card was opened for. Card actions act on this post only; if the feed has moved on,
 * the executors notice (page signature / display name) and stop without touching anything.
 */
data class CardTarget(
    val accountId: Long,
    val sightingId: Long?,
    val pageSignature: String,
    val displayName: String?,
    /** Confirmed @handle from the database, if known (never the sound-credit heuristic). */
    val knownHandle: String?,
    val signals: Signals,
    /** The player the post was in (for the decision log). */
    val feedContext: FeedContext? = null,
) {
    companion object {
        fun from(page: ProcessedPage): CardTarget = CardTarget(
            accountId = page.accountId,
            sightingId = page.sightingId,
            pageSignature = page.pageSignature,
            displayName = page.page.displayName,
            knownHandle = page.knownHandle,
            signals = page.signals,
            feedContext = page.page.feedContext,
        )
    }
}
