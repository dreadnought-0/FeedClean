package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.service.BlockedMatch
import io.github.dreadnought0.feedclean.service.ProcessedPage
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLookMapperTest {
    private val defaults = FeedCleanSettings()

    private fun page(signals: Signals, match: BlockedMatch = BlockedMatch.NONE) = ProcessedPage(
        page = FeedVideo(
            capturedAtMillis = 0, displayName = "C", probableHandle = null, caption = null, soundDescription = null,
            signals = signals, pageSignature = "s", isForYouTab = true,
        ),
        accountId = 1,
        knownHandle = null,
        sightingId = null,
        decision = CurationDecision.Keep,
        blockedMatch = match,
        processedAtMillis = 0,
    )

    private fun map(
        signals: Signals?,
        settings: FeedCleanSettings = defaults,
        paused: Boolean = false,
        busy: Boolean = false,
        match: BlockedMatch = BlockedMatch.NONE,
    ) = BubbleLookMapper.map(signals?.let { page(it, match) }, settings, paused, busy)

    @Test
    fun eachSignalHasItsColour() {
        assertEquals(BubbleKind.NEUTRAL, map(null).kind)
        assertEquals(BubbleKind.NEUTRAL, map(Signals()).kind)
        assertEquals(BubbleKind.AD, map(Signals(isPaidAd = true)).kind)
        assertEquals(BubbleKind.PROMO, map(Signals(isPromoted = true)).kind)
        assertEquals(BubbleKind.SHOP, map(Signals(hasShopAnchor = true)).kind)
        assertEquals(BubbleKind.SHOP, map(Signals(isShopCreator = true)).kind)
        assertEquals(BubbleKind.LIVE, map(Signals(isLiveShopping = true)).kind)
    }

    @Test
    fun strongestSignalWinsWithACount() {
        val look = map(Signals(isPaidAd = true, hasShopAnchor = true))
        assertEquals(BubbleKind.AD, look.kind)
        assertEquals(2, look.signalCount)
        assertEquals(BubbleKind.SHOP, map(Signals(hasShopAnchor = true, isLiveShopping = true)).kind)
    }

    @Test
    fun detectionTogglesAreHonoured() {
        assertEquals(BubbleKind.SHOP, map(Signals(isPaidAd = true, hasShopAnchor = true), defaults.copy(detectPaidAd = false)).kind)
        assertEquals(BubbleKind.NEUTRAL, map(Signals(isPaidAd = true), defaults.copy(detectionEnabled = false)).kind)
    }

    @Test
    fun skipGlyphFollowsArming() {
        assertFalse(map(Signals()).skipArmed)
        assertTrue(map(Signals(), defaults.copy(curateFeedEnabled = true)).skipArmed)
        assertFalse(map(Signals(), defaults.copy(curateFeedEnabled = true, killSwitchEngaged = true)).skipArmed)
        // Honest glyph: no place selected, or the only skip type's detection off, means nothing can be skipped.
        assertFalse(map(Signals(), defaults.copy(curateFeedEnabled = true, autoSkipInForYou = false)).skipArmed)
        assertFalse(map(Signals(), defaults.copy(curateFeedEnabled = true, detectShopAnchor = false)).skipArmed)
    }

    @Test
    fun pausedLookForKillSwitchOrLoopGuard() {
        val kill = map(Signals(), defaults.copy(killSwitchEngaged = true))
        assertTrue(kill.paused)
        assertTrue(kill.killSwitch)
        val loop = map(Signals(), paused = true)
        assertTrue(loop.paused)
        assertTrue(loop.loopGuardPaused)
        assertFalse(map(Signals()).paused)
    }

    @Test
    fun warningBadgeAndBusy() {
        assertEquals(BlockedMatch.STRONG, map(Signals(), match = BlockedMatch.STRONG).warning)
        assertEquals(BlockedMatch.POSSIBLE, map(Signals(), match = BlockedMatch.POSSIBLE).warning)
        assertTrue(map(Signals(), busy = true).busy)
    }
}
