package io.github.dreadnought0.feedclean.testutil

import io.github.dreadnought0.feedclean.curate.SkipCover
import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.detect.UiNode
import io.github.dreadnought0.feedclean.service.ScreenDriver
import io.github.dreadnought0.feedclean.service.pollForSnapshot
import io.github.dreadnought0.feedclean.settings.CoverStyle

/**
 * A scripted For You feed for auto-skip / manual-skip tests. Pages are "page-<index>"; [scrollForward] moves to
 * the next page after [latencyMillis] of virtual time (use with `runTest` and `{ testScheduler.currentTime }`).
 * Every action is appended to [actions]. Knobs model the gates and failure branches.
 */
class FakeFeedDriver(private val clock: () -> Long) : ScreenDriver {
    var pageIndex = 0
    var forYou = true
    var onFeed = true
    var hasPager = true
    var scrollWorks = true
    var scrollAdvances = true
    var latencyMillis = 200L
    var signals = Signals(hasShopAnchor = true)
    var pageBounds = Bounds(0, 0, 1080, 2376)
    var displayName: String? = "Creator"

    /** The player the pages are in; null = derived from [forYou] (FOR_YOU / OTHER), like the snapshot default. */
    var feedContext: FeedContext? = null

    val actions = mutableListOf<String>()
    var snapshotCount = 0
        private set

    private class Event(val at: Long, val seq: Int, val apply: FakeFeedDriver.() -> Unit)

    private val events = mutableListOf<Event>()
    private var seq = 0

    /** Runs [event] once virtual time reaches [atMillis] (applied on the next driver call). */
    fun at(atMillis: Long, event: FakeFeedDriver.() -> Unit) {
        events += Event(atMillis, seq++, event)
    }

    fun signature(index: Int = pageIndex) = "page-$index"

    /** The feed page currently "on screen". */
    fun page(index: Int = pageIndex): ScreenSnapshot.FeedVideo = ScreenSnapshot.FeedVideo(
        capturedAtMillis = clock(),
        displayName = displayName,
        probableHandle = null,
        caption = "caption $index",
        soundDescription = null,
        signals = signals,
        pageSignature = signature(index),
        isForYouTab = forYou,
        videoNode = FakeUiNode("feed.video"),
        pagerNode = if (hasPager) FakeUiNode("feed.pager", isScrollable = true) else null,
        pageBounds = pageBounds,
        feedContext = feedContext ?: if (forYou) FeedContext.FOR_YOU else FeedContext.OTHER,
    )

    private fun settle() {
        val now = clock()
        while (true) {
            val due = events.filter { it.at <= now }.minWithOrNull(compareBy({ it.at }, { it.seq })) ?: return
            events.remove(due)
            due.apply(this)
        }
    }

    override suspend fun snapshot(): ScreenSnapshot {
        settle()
        snapshotCount++
        return if (onFeed) page() else ScreenSnapshot.Unknown(clock(), note = "fake: not the feed")
    }

    override suspend fun click(node: UiNode): Boolean {
        settle()
        actions += "click:${(node as? FakeUiNode)?.tag}"
        return true
    }

    override suspend fun tap(x: Int, y: Int): Boolean {
        actions += "tap"
        return true
    }

    override suspend fun longPress(node: UiNode): Boolean {
        actions += "longPress:${(node as? FakeUiNode)?.tag}"
        return true
    }

    override suspend fun scrollForward(node: UiNode): Boolean {
        settle()
        actions += "scrollForward:${(node as? FakeUiNode)?.tag}"
        if (!scrollWorks) return false
        if (scrollAdvances) at(clock() + latencyMillis) { pageIndex++ }
        return true
    }

    override suspend fun back(): Boolean {
        actions += "back"
        return true
    }

    override suspend fun awaitSnapshot(
        timeoutMillis: Long,
        pollMillis: Long,
        predicate: (ScreenSnapshot) -> Boolean,
    ): ScreenSnapshot? = pollForSnapshot(::snapshot, timeoutMillis, pollMillis, clock, predicate)
}

/** Records cover show/hide calls with their virtual times. */
class FakeSkipCover(private val clock: () -> Long) : SkipCover {
    val calls = mutableListOf<String>()
    var showing = false
        private set
    var lastShownAt: Long? = null
    var lastHiddenAt: Long? = null

    override fun show(bounds: Bounds, style: CoverStyle, reason: SkipReason?) {
        calls += "show:$style:${reason ?: "-"}"
        showing = true
        lastShownAt = clock()
    }

    override fun hide() {
        calls += "hide"
        if (showing) lastHiddenAt = clock()
        showing = false
    }
}
