package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.SignalDetector
import io.github.dreadnought0.feedclean.detect.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTrackerTest {
    private fun page(name: String? = "Creator", caption: String? = "caption", sound: String? = "Sound: x", at: Long = 0) =
        FeedVideo(
            capturedAtMillis = at,
            displayName = name,
            probableHandle = null,
            caption = caption,
            soundDescription = sound,
            signals = Signals(),
            pageSignature = SignalDetector.pageSignature(name, caption, sound),
            isForYouTab = true,
        )

    @Test
    fun samePageIsProcessedOnce() {
        val t = PageTracker()
        assertTrue(t.accept(page()))
        assertFalse(t.accept(page(at = 5_000)))
    }

    @Test
    fun newSignatureIsANewPage() {
        val t = PageTracker()
        assertTrue(t.accept(page(caption = "one")))
        assertTrue(t.accept(page(caption = "two")))
        assertTrue("scrolling back is a new page again", t.accept(page(caption = "one")))
    }

    @Test
    fun pageWithoutDisplayNameIsIncomplete() {
        val t = PageTracker()
        assertFalse(t.accept(page(name = null)))
        assertTrue("not remembered, so the full page counts", t.accept(page()))
    }

    @Test
    fun lateCaptionIsARefinementNotANewPage() {
        val t = PageTracker()
        assertTrue(t.accept(page(caption = null, at = 0)))
        assertFalse(t.accept(page(caption = "caption", at = 400)))
        assertFalse(t.accept(page(caption = "caption", at = 800)))
    }

    @Test
    fun lateSoundIsARefinement() {
        val t = PageTracker()
        assertTrue(t.accept(page(sound = null, at = 0)))
        assertFalse(t.accept(page(at = 300)))
    }

    @Test
    fun refinementOnlyWithinTheWindowAndForTheSameCreator() {
        val late = PageTracker()
        assertTrue(late.accept(page(caption = null, at = 0)))
        assertTrue(late.accept(page(caption = "caption", at = 5_000)))

        val other = PageTracker()
        assertTrue(other.accept(page(name = "A", caption = null, at = 0)))
        assertTrue(other.accept(page(name = "B", caption = "caption", at = 100)))
    }

    @Test
    fun changedCaptionIsNotARefinement() {
        val t = PageTracker()
        assertTrue(t.accept(page(caption = "one", sound = null, at = 0)))
        assertTrue(t.accept(page(caption = "two", sound = "Sound: x", at = 100)))
    }

    @Test
    fun classifyTellsNewSameRefinedAndIncomplete() {
        val t = PageTracker()
        assertEquals(PageTracker.Change.INCOMPLETE, t.classify(page(name = null)))
        assertEquals(PageTracker.Change.NEW, t.classify(page(caption = null, at = 0)))
        assertEquals(PageTracker.Change.SAME, t.classify(page(caption = null, at = 100)))
        assertEquals(PageTracker.Change.REFINED, t.classify(page(caption = "caption", at = 400)))
        assertEquals("the refined signature is now the page's", PageTracker.Change.SAME, t.classify(page(caption = "caption", at = 900)))
        assertEquals(PageTracker.Change.NEW, t.classify(page(caption = "other", at = 1_000)))
    }

    @Test
    fun resetForgetsTheLastPage() {
        val t = PageTracker()
        assertTrue(t.accept(page()))
        t.reset()
        assertTrue(t.accept(page()))
    }
}
