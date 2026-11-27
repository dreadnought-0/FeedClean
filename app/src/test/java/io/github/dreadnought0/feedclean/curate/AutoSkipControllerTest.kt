package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.block.DenyReason
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeFeedDriver
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver
import io.github.dreadnought0.feedclean.testutil.FakeSkipCover
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSkipControllerTest {

    private class Harness(scope: TestScope) {
        val clock = { scope.testScheduler.currentTime }
        val driver = FakeFeedDriver(clock)
        val cover = FakeSkipCover(clock)
        val guard = LoopGuard()
        var settings = ARMED
        val niTriggers = mutableListOf<NotInterestedTrigger>()
        var ni: suspend () -> NotInterestedResult = { NotInterestedResult.Done(verified = true, reasonChosen = null) }
        val controller = AutoSkipController(
            driver = driver,
            cover = cover,
            loopGuard = guard,
            settings = { settings },
            notInterested = NotInterestedRunner { trigger, _, _ ->
                niTriggers += trigger
                ni()
            },
        )

        fun request(reason: SkipReason = SkipReason.ORGANIC_SHOP, page: ScreenSnapshot.FeedVideo = driver.page()) =
            AutoSkipRequest(page, reason, accountId = 7L, settings = settings)

        suspend fun skip(reason: SkipReason = SkipReason.ORGANIC_SHOP) = controller.skip(request(reason))
    }

    // ── Happy path and cover ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun happyPathScrollsOnceBehindTheCover() = runTest {
        val h = Harness(this)

        val outcome = h.skip()

        assertEquals(AutoSkipOutcome.Skipped(scrolled = true, advanced = true, notInterested = null), outcome)
        assertEquals(listOf("scrollForward:feed.pager"), h.driver.actions)
        assertEquals(1, h.driver.pageIndex)
        assertEquals(listOf("show:BLANK:ORGANIC_SHOP", "hide"), h.cover.calls)
        assertFalse(h.cover.showing)
        assertTrue("settle delay respected", h.clock() >= 300)
        assertTrue(h.niTriggers.isEmpty())
    }

    @Test
    fun defaultsFlickPastImmediatelyWithoutACover() = runTest {
        // The shipped defaults: no cover, no settle wait, just one fast scroll.
        val h = Harness(this)
        h.settings = FeedCleanSettings(curateFeedEnabled = true)

        val outcome = h.skip()

        assertEquals(AutoSkipOutcome.Skipped(scrolled = true, advanced = true, notInterested = null), outcome)
        assertEquals(listOf("scrollForward:feed.pager"), h.driver.actions)
        assertTrue("no cover by default", h.cover.calls.isEmpty())
    }

    @Test
    fun coverStyleFromSettings() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(coverStyle = CoverStyle.LABELLED)
        h.skip()
        assertEquals("show:LABELLED:ORGANIC_SHOP", h.cover.calls.first())
    }

    @Test
    fun noCoverWhenDisabledOrNoBounds() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(skipCoverEnabled = false)
        h.skip()
        h.settings = ARMED
        h.driver.pageBounds = Bounds.EMPTY
        h.skip()
        assertTrue(h.cover.calls.isEmpty())
        assertEquals(2, h.driver.actions.size)
    }

    @Test
    fun scrollThatDoesNotAdvanceIsStillOneScroll() = runTest {
        val h = Harness(this)
        h.driver.scrollAdvances = false
        val outcome = h.skip()
        assertEquals(AutoSkipOutcome.Skipped(scrolled = true, advanced = false, notInterested = null), outcome)
        assertEquals(1, h.driver.actions.size)
        assertFalse(h.cover.showing)
    }

    // ── Cancel branches ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun userScrolledDuringSettleCancels() = runTest {
        val h = Harness(this)
        h.driver.at(100) { pageIndex++ }

        val outcome = h.skip()

        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.USER_SCROLLED, null), outcome)
        assertTrue("no scroll", h.driver.actions.isEmpty())
        assertFalse(h.cover.showing)
    }

    @Test
    fun samePageNowInADisallowedContextCancels() = runTest {
        val h = Harness(this)
        h.driver.at(100) { forYou = false } // same signature, but now an OTHER player (off by default)
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.CONTEXT_OFF, null), h.skip())
        assertTrue(h.driver.actions.isEmpty())
    }

    @Test
    fun contextToggleSwitchedOffDuringSettleCancels() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(autoSkipInSearch = true)
        h.driver.feedContext = FeedContext.SEARCH
        h.driver.at(100) { h.settings = ARMED.copy(autoSkipInSearch = false) }
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.CONTEXT_OFF, null), h.skip())
        assertTrue(h.driver.actions.isEmpty())
        assertFalse(h.cover.showing)
    }

    @Test
    fun searchAndProfilePlayersAreSkippedWithTheSameSingleScrollWhenAllowed() = runTest {
        for (ctx in listOf(FeedContext.SEARCH, FeedContext.OTHER, FeedContext.HOME_OTHER_TAB)) {
            val h = Harness(this)
            h.settings = ARMED.copy(autoSkipInSearch = true, autoSkipInOtherFeeds = true, autoSkipInOtherHomeTabs = true)
            h.driver.forYou = false
            h.driver.feedContext = ctx
            assertEquals("$ctx", AutoSkipOutcome.Skipped(scrolled = true, advanced = true, notInterested = null), h.skip())
            assertEquals("$ctx", listOf("scrollForward:feed.pager"), h.driver.actions)
        }
    }

    @Test
    fun menuOrOtherScreenDuringSettleCancels() = runTest {
        val h = Harness(this)
        h.driver.at(100) { onFeed = false }
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.LEFT_FEED, null), h.skip())
        assertTrue(h.driver.actions.isEmpty())
    }

    @Test
    fun noPagerCancels() = runTest {
        val h = Harness(this)
        h.driver.hasPager = false
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.NO_PAGER, null), h.skip())
    }

    @Test
    fun failedScrollCancelsAndDropsCover() = runTest {
        val h = Harness(this)
        h.driver.scrollWorks = false
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.SCROLL_FAILED, null), h.skip())
        assertFalse(h.cover.showing)
        assertEquals(0, h.guard.consecutiveSkips)
    }

    // ── Loop guard ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun sameSignatureIsNeverSkippedTwice() = runTest {
        val h = Harness(this)
        val first = h.driver.page()
        h.controller.skip(h.request(page = first))

        val again = h.controller.skip(h.request(page = first))

        assertEquals(AutoSkipOutcome.Refused(SkipHold.ALREADY_ATTEMPTED), again)
        assertEquals("only the first scroll", 1, h.driver.actions.size)
    }

    @Test
    fun cancelledAttemptIsRememberedToo() = runTest {
        val h = Harness(this)
        val page = h.driver.page()
        h.driver.at(100) { pageIndex++ }
        h.controller.skip(h.request(page = page))
        assertEquals(AutoSkipOutcome.Refused(SkipHold.ALREADY_ATTEMPTED), h.controller.skip(h.request(page = page)))
    }

    @Test
    fun consecutiveCapPausesUntilUserResumes() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(consecutiveSkipCap = 3)
        val outcomes = List(3) { h.skip() }
        assertEquals(listOf(false, false, true), outcomes.map { (it as AutoSkipOutcome.Skipped).pausedNow })
        assertTrue(h.guard.isPaused)

        assertEquals(AutoSkipOutcome.Refused(SkipHold.PAUSED), h.skip())
        assertEquals(3, h.driver.actions.size)

        h.guard.resume()
        assertTrue(h.skip() is AutoSkipOutcome.Skipped)
    }

    @Test
    fun keptPageResetsTheConsecutiveCount() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(consecutiveSkipCap = 3)
        h.skip()
        h.skip()
        h.guard.onPageKept()
        h.skip()
        h.skip()
        assertFalse(h.guard.isPaused)
        assertEquals(2, h.guard.consecutiveSkips)
    }

    // ── Arming and kill-switch ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun refusedWhenNotArmedOrKillSwitch() = runTest {
        val h = Harness(this)
        h.settings = FeedCleanSettings()
        assertEquals(AutoSkipOutcome.Refused(SkipHold.NOT_ARMED), h.skip())
        h.settings = ARMED.copy(killSwitchEngaged = true)
        assertEquals(AutoSkipOutcome.Refused(SkipHold.KILL_SWITCH), h.skip())
        assertTrue(h.driver.actions.isEmpty())
        assertTrue(h.cover.calls.isEmpty())
    }

    @Test
    fun killSwitchEngagedDuringSettleCancelsBeforeScrolling() = runTest {
        val h = Harness(this)
        h.driver.at(100) { h.settings = ARMED.copy(killSwitchEngaged = true) }
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.KILL_SWITCH, null), h.skip())
        assertTrue(h.driver.actions.isEmpty())
        assertFalse(h.cover.showing)
    }

    @Test
    fun curateOffDuringSettleCancels() = runTest {
        val h = Harness(this)
        h.driver.at(100) { h.settings = FeedCleanSettings() }
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.DISARMED, null), h.skip())
    }

    @Test
    fun cancellingTheCoroutineStopsAtOnceAndDropsTheCover() = runTest {
        val h = Harness(this)
        val job = launch { h.skip() }
        advanceTimeBy(100)
        runCurrent()
        assertTrue(h.cover.showing)

        job.cancel()
        runCurrent()

        assertFalse(h.cover.showing)
        advanceTimeBy(5_000)
        assertTrue("never scrolled", h.driver.actions.isEmpty())
    }

    @Test
    fun failsafeDropsTheCoverAfter2500ms() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED
        h.ni = {
            delay(10_000)
            NotInterestedResult.Done(verified = true, reasonChosen = null)
        }
        val job = launch { h.skip() }
        advanceTimeBy(2_400)
        runCurrent()
        assertTrue(h.cover.showing)
        advanceTimeBy(200)
        runCurrent()
        assertFalse("failsafe", h.cover.showing)
        assertEquals(2_500L, h.cover.lastHiddenAt)
        assertTrue(job.isActive)
        job.cancel()
    }

    // ── Automated Not interested ───────────────────────────────────────────────────────────────────────────

    @Test
    fun notInterestedDoneAndTikTokAdvancedMeansNoScroll() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED
        h.ni = {
            h.driver.pageIndex++
            NotInterestedResult.Done(verified = true, reasonChosen = null)
        }

        val outcome = h.skip()

        assertEquals(
            AutoSkipOutcome.Skipped(scrolled = false, advanced = true, notInterested = NotInterestedResult.Done(true, null)),
            outcome,
        )
        assertTrue("no scroll on top of TikTok's own move", h.driver.actions.isEmpty())
        assertTrue(h.niTriggers.single() is NotInterestedTrigger.Automated)
        assertFalse(h.cover.showing)
    }

    @Test
    fun notInterestedDoneAndTikTokAdvancesDuringSettleStillNoScroll() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED
        h.ni = {
            h.driver.at(h.clock() + 100) { pageIndex++ }
            NotInterestedResult.Done(verified = true, reasonChosen = null)
        }
        val outcome = h.skip()
        assertEquals(false, (outcome as AutoSkipOutcome.Skipped).scrolled)
        assertTrue(h.driver.actions.isEmpty())
    }

    @Test
    fun notInterestedDoneWithoutAdvanceThenPlainSkip() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED
        val outcome = h.skip() as AutoSkipOutcome.Skipped
        assertTrue(outcome.scrolled)
        assertEquals(listOf("scrollForward:feed.pager"), h.driver.actions)
    }

    @Test
    fun rateLimitedNotInterestedFallsBackToPlainSkip() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED
        val limited = NotInterestedResult.RateLimited(4_000, DenyReason.MIN_INTERVAL)
        h.ni = { limited }
        val outcome = h.skip()
        assertEquals(AutoSkipOutcome.Skipped(scrolled = true, advanced = true, notInterested = limited), outcome)
        assertEquals(listOf("scrollForward:feed.pager"), h.driver.actions)
    }

    @Test
    fun abortedNotInterestedFallsBackToPlainSkip() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED
        h.ni = { NotInterestedResult.Aborted(NotInterestedStep.LONG_PRESS, NotInterestedAbortReason.MENU_DID_NOT_OPEN) }
        assertTrue((h.skip() as AutoSkipOutcome.Skipped).scrolled)
    }

    @Test
    fun notInterestedOnlyForOrganicShopAndOnlyWhenArmed() = runTest {
        val h = Harness(this)
        h.settings = AUTOMATED.copy(autoSkipPaidAds = true)
        h.skip(SkipReason.PAID_AD)
        h.settings = ARMED
        h.skip(SkipReason.ORGANIC_SHOP)
        assertTrue(h.niTriggers.isEmpty())
        h.settings = ARMED.copy(notInterestedAutomated = true) // risk note not accepted
        h.skip(SkipReason.ORGANIC_SHOP)
        assertTrue(h.niTriggers.isEmpty())
    }

    // ── With the real NotInterestedFlow and rate limiter ───────────────────────────────────────────────────

    @Test
    fun realFlowDoneAndAdvancedIssuesNoScroll() = runTest {
        val clock = { testScheduler.currentTime }
        val driver = FakeScreenDriver(clock)
        val log = FakeActionLogRepository()
        val settings = AUTOMATED
        val limiter = RateLimiter(log, { settings }, clock)
        val controller = AutoSkipController(
            driver, FakeSkipCover(clock), LoopGuard(), { settings },
            NotInterestedRunner { t, a, s -> NotInterestedFlow(driver, limiter, { settings }, clock).run(t, a, s) },
        )
        val page = driver.snapshot() as ScreenSnapshot.FeedVideo

        val outcome = controller.skip(AutoSkipRequest(page, SkipReason.ORGANIC_SHOP, 7L, settings))

        assertEquals(false, (outcome as AutoSkipOutcome.Skipped).scrolled)
        assertEquals(listOf("longPress:feed.video", "click:menu.notInterested"), driver.actions)
        assertEquals(ActionKind.NOT_INTERESTED, log.entries.single().kind)
    }

    @Test
    fun realFlowRateLimitedSkipsWithoutTouchingTheVideo() = runTest {
        val clock = { testScheduler.currentTime }
        val driver = FakeScreenDriver(clock)
        val log = FakeActionLogRepository().apply { seed(0L) } // an action "just now"
        val settings = AUTOMATED
        val limiter = RateLimiter(log, { settings }, clock)
        val controller = AutoSkipController(
            driver, FakeSkipCover(clock), LoopGuard(), { settings },
            NotInterestedRunner { t, a, s -> NotInterestedFlow(driver, limiter, { settings }, clock).run(t, a, s) },
        )
        val page = driver.snapshot() as ScreenSnapshot.FeedVideo

        val outcome = controller.skip(AutoSkipRequest(page, SkipReason.ORGANIC_SHOP, 7L, settings))

        assertTrue(outcome.notInterested is NotInterestedResult.RateLimited)
        assertEquals(listOf("scrollForward:feed.pager"), driver.actions)
        assertEquals("nothing new logged", 1, log.entries.size)
        assertNull((outcome as AutoSkipOutcome.Skipped).notInterested.let { it as? NotInterestedResult.Done })
    }

    private companion object {
        /** Cover + settle explicitly on: these tests exercise the cover/settle mechanics (defaults are off/0). */
        val ARMED = FeedCleanSettings(curateFeedEnabled = true, skipCoverEnabled = true, skipSettleDelayMs = 300)
        val AUTOMATED = ARMED.copy(notInterestedAutomated = true, notInterestedRiskAcknowledged = true)
    }
}
