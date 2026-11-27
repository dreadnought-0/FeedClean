package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.UiNode

/**
 * What the executors (block/, curate/) are allowed to do to the screen. Implemented by the accessibility
 * service on device, and by a scripted fake in unit tests.
 *
 * Every method acts on what is ON SCREEN only (no TikTok internals or private APIs). No method may be called
 * from detection code.
 */
interface ScreenDriver {
    /** Re-read all interactive windows and parse them. Always returns a fresh snapshot. */
    suspend fun snapshot(): ScreenSnapshot

    /** ACTION_CLICK on the node; falls back to a gesture tap at its bounds center if the node isn't clickable. */
    suspend fun click(node: UiNode): Boolean

    /** Gesture tap at a screen coordinate. */
    suspend fun tap(x: Int, y: Int): Boolean

    /** Long-press (ACTION_LONG_CLICK, falling back to a long-press gesture). */
    suspend fun longPress(node: UiNode): Boolean

    /** ACTION_SCROLL_FORWARD on a scrollable node (the feed pager): moves to the next video. */
    suspend fun scrollForward(node: UiNode): Boolean

    /** GLOBAL_ACTION_BACK. */
    suspend fun back(): Boolean

    /**
     * Polls [snapshot] until [predicate] matches or [timeoutMillis] elapses. Returns the matching snapshot,
     * or null on timeout. Executors use this for every "expect this screen next" step.
     */
    suspend fun awaitSnapshot(
        timeoutMillis: Long = 3_000,
        pollMillis: Long = 150,
        predicate: (ScreenSnapshot) -> Boolean,
    ): ScreenSnapshot?
}
