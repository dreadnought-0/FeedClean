package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.curate.AutoSkipContext
import io.github.dreadnought0.feedclean.curate.AutoSkipController
import io.github.dreadnought0.feedclean.curate.AutoSkipOutcome
import io.github.dreadnought0.feedclean.curate.LoopGuard
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.NotInterestedRunner
import io.github.dreadnought0.feedclean.curate.SkipCancel
import io.github.dreadnought0.feedclean.curate.SkipGateResult
import io.github.dreadnought0.feedclean.curate.SkipHold
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.Fixtures
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.detect.UiNode
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeAccountRepository
import io.github.dreadnought0.feedclean.testutil.FakeDecisionLog
import io.github.dreadnought0.feedclean.testutil.FakeFeedDriver
import io.github.dreadnought0.feedclean.testutil.FakeSessionRepository
import io.github.dreadnought0.feedclean.testutil.FakeSightingRepository
import io.github.dreadnought0.feedclean.testutil.FakeSkipCover
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole per-snapshot decision path (new page → DB → policy → every auto-skip gate → controller →
 * persistence), with fakes for the screen and the database.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedCuratorTest {

    private class Harness(scope: TestScope, driver: ScreenDriver? = null) {
        val clock = { scope.testScheduler.currentTime }
        val feed = FakeFeedDriver(clock)
        val cover = FakeSkipCover(clock)
        val guard = LoopGuard()
        val accounts = FakeAccountRepository()
        val sightings = FakeSightingRepository()
        val sessionRepo = FakeSessionRepository()
        val session = SessionTracker(sessionRepo, clock)
        var settings = ARMED
        var context = AutoSkipContext(tiktokForeground = true, screenInteractive = true)
        var ni: suspend () -> NotInterestedResult = { NotInterestedResult.Done(verified = true, reasonChosen = null) }
        val pages = mutableListOf<Pair<ProcessedPage, SkipGateResult>>()
        val updates = mutableListOf<Pair<ProcessedPage, SkipGateResult>>()
        val outcomes = mutableListOf<AutoSkipOutcome>()
        val decisions = FakeDecisionLog()
        val controller = AutoSkipController(
            driver ?: feed, cover, guard, { settings }, NotInterestedRunner { _, _, _ -> ni() },
        )
        val curator = FeedCurator(
            pipeline = FeedPagePipeline(accounts, sightings, clock),
            autoSkip = controller,
            loopGuard = guard,
            sightings = sightings,
            session = session,
            settings = { settings },
            context = { context },
            listener = object : FeedCurator.Listener {
                override fun onPage(page: ProcessedPage, gate: SkipGateResult) {
                    pages += page to gate
                }

                override fun onPageUpdated(page: ProcessedPage, gate: SkipGateResult) {
                    updates += page to gate
                }

                override fun onAutoSkip(page: ProcessedPage, outcome: AutoSkipOutcome) {
                    outcomes += outcome
                }
            },
            decisionLog = decisions,
            clock = clock,
        )

        suspend fun showCurrent(incomplete: Boolean = false) = curator.onSnapshot(feed.snapshot(), incomplete)

        val scrolls: Int get() = feed.actions.count { it.startsWith("scrollForward") }
    }

    // ── New page vs same page ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun samePageIsProcessedOnceEvenWithOtherScreensInBetween() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals()
        assertNotNull(h.showCurrent())
        assertNull(h.curator.onSnapshot(ScreenSnapshot.Unknown(0)))
        assertNull(h.showCurrent())
        assertEquals(1, h.accounts.calls.count { it == "upsertFromFeed" })
    }

    @Test
    fun incompleteReadIsNotProcessed() = runTest {
        val h = Harness(this)
        assertNull(h.showCurrent(incomplete = true))
        assertTrue(h.accounts.calls.isEmpty())
        assertNotNull("processed once a full read arrives", h.showCurrent())
    }

    // ── Signals → logged or not ────────────────────────────────────────────────────────────────────────────

    @Test
    fun normalPageIsNotLoggedAndNotSkipped() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals()
        h.showCurrent()
        assertTrue(h.sightings.sightings.isEmpty())
        assertEquals(0, h.scrolls)
        assertEquals(SkipGateResult.Hold(SkipHold.KEEP), h.pages.single().second)
    }

    @Test
    fun paidAdIsLoggedButKeptByDefault() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals(isPaidAd = true, hasShopAnchor = true, disclosureLabel = "Ad")
        h.showCurrent()
        assertEquals(UserAction.NONE, h.sightings.sightings.values.single().userAction)
        assertEquals(0, h.scrolls)
    }

    @Test
    fun paidAdSkippedOnlyWhenThatTargetIsOn() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(autoSkipPaidAds = true)
        h.feed.signals = Signals(isPaidAd = true, disclosureLabel = "Ad")
        h.showCurrent()
        assertEquals(1, h.scrolls)
        assertEquals(SkipReason.PAID_AD, h.sightings.sightings.values.single().skipReason)
    }

    // ── Organic Shop → auto-skip, persisted ────────────────────────────────────────────────────────────────

    @Test
    fun organicShopIsSkippedOnceAndRecorded() = runTest {
        val h = Harness(this)
        h.session.sync(trackingEnabled = true, foreground = true)

        h.showCurrent()

        assertEquals(1, h.scrolls)
        assertEquals(1, h.feed.pageIndex)
        val s = h.sightings.sightings.values.single()
        assertEquals(UserAction.AUTO_SKIP, s.userAction)
        assertEquals(SkipReason.ORGANIC_SHOP, s.skipReason)
        assertEquals(true, s.actionVerified)
        assertEquals(listOf(true), h.sessionRepo.pages.map { it.skipped })
        assertFalse(h.cover.showing)
    }

    @Test
    fun consecutiveShopPagesAreSkippedOneAfterAnother() = runTest {
        val h = Harness(this)
        repeat(3) { h.showCurrent() }
        assertEquals(3, h.scrolls)
        assertEquals(3, h.feed.pageIndex)
        assertEquals(3, h.guard.consecutiveSkips)
    }

    // ── Every gate ─────────────────────────────────────────────────────────────────────────────────────────

    private fun assertHeld(why: SkipHold, configure: Harness.() -> Unit) = runTest {
        val h = Harness(this)
        h.configure()
        h.showCurrent()
        assertEquals("no scroll when $why", 0, h.scrolls)
        assertEquals(SkipGateResult.Hold(why), h.pages.single().second)
        assertTrue("still logged", h.sightings.sightings.values.single().userAction == UserAction.NONE)
        assertTrue(h.cover.calls.isEmpty())
    }

    @Test
    fun gateNotArmed() = assertHeld(SkipHold.NOT_ARMED) { settings = FeedCleanSettings() }

    @Test
    fun gateKillSwitch() = assertHeld(SkipHold.KILL_SWITCH) { settings = ARMED.copy(killSwitchEngaged = true) }

    @Test
    fun gateNotForeground() = assertHeld(SkipHold.NOT_FOREGROUND) { context = context.copy(tiktokForeground = false) }

    @Test
    fun gateContextOffForOtherPlayersByDefault() = assertHeld(SkipHold.CONTEXT_OFF) { feed.forYou = false }

    @Test
    fun gateContextOffInSearchByDefault() = assertHeld(SkipHold.CONTEXT_OFF) { feed.feedContext = FeedContext.SEARCH }

    @Test
    fun gateContextOffWhenForYouIsSwitchedOff() =
        assertHeld(SkipHold.CONTEXT_OFF) { settings = ARMED.copy(autoSkipInForYou = false) }

    @Test
    fun searchResultsPlayerIsSkippedWhenAllowed() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(autoSkipInSearch = true)
        h.feed.forYou = false
        h.feed.feedContext = FeedContext.SEARCH
        h.showCurrent()
        assertEquals(1, h.scrolls)
        assertEquals(UserAction.AUTO_SKIP, h.sightings.sightings.values.single().userAction)
        val e = h.decisions.of(DecisionEvent.PAGE).single()
        assertEquals(FeedContext.SEARCH, e.context)
        assertEquals("go", e.gate)
        assertEquals("skipped", e.outcome)
    }

    @Test
    fun heldContextIsVisibleInTheDecisionLog() = runTest {
        val h = Harness(this)
        h.feed.feedContext = FeedContext.SEARCH
        h.showCurrent()
        val e = h.decisions.entries.single()
        assertEquals(DecisionEvent.PAGE, e.event)
        assertEquals(FeedContext.SEARCH, e.context)
        assertEquals(listOf(DetectionRules.Kind.SHOP), e.kinds)
        assertEquals("skip:ORGANIC_SHOP", e.decision)
        assertEquals("hold:CONTEXT_OFF", e.gate)
        assertNull(e.outcome)
        assertEquals(true, e.logged)
    }

    @Test
    fun gateScreenOff() = assertHeld(SkipHold.SCREEN_OFF) { context = context.copy(screenInteractive = false) }

    @Test
    fun gateCardOpen() = assertHeld(SkipHold.CARD_OPEN) { context = context.copy(cardOpen = true) }

    @Test
    fun gateActionInProgress() = assertHeld(SkipHold.ACTION_IN_PROGRESS) { context = context.copy(actionInProgress = true) }

    @Test
    fun gateNoPager() = assertHeld(SkipHold.NO_PAGER) { feed.hasPager = false }

    // ── Transient hold retry (on-device finding: NO_PAGER on the first read of a settling page) ─────────────

    @Test
    fun noPagerOnFirstReadIsRetriedWhenTheSamePageSettles() = runTest {
        val h = Harness(this)
        h.feed.hasPager = false
        h.showCurrent()
        assertEquals(SkipGateResult.Hold(SkipHold.NO_PAGER), h.pages.single().second)
        assertEquals(0, h.scrolls)

        h.feed.hasPager = true // same page, next read: the pager is there now
        h.showCurrent()

        assertEquals("skipped exactly once on retry", 1, h.scrolls)
        assertEquals(1, h.pages.size) // not re-processed as a new page
        assertEquals(UserAction.AUTO_SKIP, h.sightings.sightings.values.single().userAction)
        assertFalse(h.cover.showing)
    }

    @Test
    fun noPagerRetryIsLoggedAsATransientHoldWatch() = runTest {
        val h = Harness(this)
        h.feed.hasPager = false
        h.showCurrent()
        testScheduler.advanceTimeBy(400)
        h.showCurrent() // still no pager: keep waiting, nothing logged
        h.feed.hasPager = true
        h.showCurrent()
        assertEquals(1, h.scrolls)
        val watch = h.decisions.of(DecisionEvent.WATCH).single()
        assertEquals(WatchTrigger.TRANSIENT_HOLD, watch.trigger)
        assertEquals("go", watch.gate)
        assertEquals("skipped", watch.outcome)
        assertEquals(400L, watch.sinceFirstReadMillis)
        assertEquals("hold:NO_PAGER", h.decisions.of(DecisionEvent.PAGE).single().gate)
    }

    @Test
    fun noPagerRetryExpiresAfterTheWindow() = runTest {
        val h = Harness(this)
        h.feed.hasPager = false
        h.showCurrent()
        testScheduler.advanceTimeBy(FeedCurator.WATCH_WINDOW_MILLIS + 100)
        h.feed.hasPager = true
        h.showCurrent()
        assertEquals("too late: no skip", 0, h.scrolls)
    }

    // ── Watch window: labels TikTok slides in after the first read ─────────────────────────────────────────

    @Test
    fun lateShopCardAt800msIsSkippedOnce() = runTest {
        val h = Harness(this)
        h.session.sync(trackingEnabled = true, foreground = true)
        h.feed.signals = Signals() // first read: the product card isn't there yet
        h.showCurrent()
        assertEquals(SkipGateResult.Hold(SkipHold.KEEP), h.pages.single().second)
        assertTrue(h.sightings.sightings.isEmpty())

        testScheduler.advanceTimeBy(800)
        h.feed.signals = Signals(hasShopAnchor = true)
        assertNotNull(h.showCurrent())

        assertEquals(1, h.scrolls)
        assertEquals(1, h.feed.pageIndex)
        assertEquals("not re-processed as a new page", 1, h.pages.size)
        val s = h.sightings.sightings.values.single()
        assertEquals(UserAction.AUTO_SKIP, s.userAction)
        assertEquals(SkipReason.ORGANIC_SHOP, s.skipReason)
        assertTrue(s.signals.hasShopAnchor)
        assertEquals("session counted once", 1, h.sessionRepo.pages.size)
        assertEquals(1, h.accounts.calls.count { it == "upsertFromFeed" })
        assertEquals(1, h.updates.size)

        val w = h.decisions.of(DecisionEvent.WATCH).single()
        assertEquals(WatchTrigger.SIGNALS_GREW, w.trigger)
        assertEquals(listOf(DetectionRules.Kind.SHOP), w.newKinds)
        assertEquals(800L, w.sinceFirstReadMillis)
        assertEquals("skip:ORGANIC_SHOP", w.decision)
        assertEquals("go", w.gate)
        assertEquals("skipped", w.outcome)
        assertEquals(true, w.logged)
    }

    @Test
    fun latePaidShopComboIsSkippedAsPaidAdWhenThatTargetIsOn() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(autoSkipPaidAds = true)
        h.feed.signals = Signals()
        h.showCurrent()
        testScheduler.advanceTimeBy(900)
        h.feed.signals = Signals(isPaidAd = true, hasShopAnchor = true, disclosureLabel = "Ad")
        h.showCurrent()
        assertEquals(1, h.scrolls)
        val s = h.sightings.sightings.values.single()
        assertEquals(SkipReason.PAID_AD, s.skipReason)
        assertTrue(s.signals.isPaidAd && s.signals.hasShopAnchor)
    }

    @Test
    fun lateShopAnchorOnALoggedAdUpdatesTheSameSighting() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals(isPaidAd = true, disclosureLabel = "Ad") // first logged as [PAID_AD]
        h.showCurrent()
        val id = h.sightings.sightings.keys.single()

        testScheduler.advanceTimeBy(1_000)
        h.feed.signals = Signals(isPaidAd = true, hasShopAnchor = true, disclosureLabel = "Ad")
        h.showCurrent()

        assertEquals("no duplicate sighting", listOf(id), h.sightings.sightings.keys.toList())
        assertTrue("updateSignals:$id" in h.sightings.calls)
        assertTrue(h.sightings.sightings.getValue(id).signals.hasShopAnchor)
        val updated = h.updates.single().first
        assertEquals(id, updated.sightingId)
        assertTrue(updated.signals.isPaidAd && updated.signals.hasShopAnchor)
        // Whatever the policy says about paid Shop ads, the watch follows it (and skips at most once).
        val expectedScrolls = if (updated.decision is CurationDecision.Skip) 1 else 0
        assertEquals(expectedScrolls, h.scrolls)
        assertEquals(listOf(DetectionRules.Kind.SHOP), h.decisions.of(DecisionEvent.WATCH).single().newKinds)
    }

    @Test
    fun labelAfterTheWindowIsNotSkippedOrLogged() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals()
        h.showCurrent()
        testScheduler.advanceTimeBy(FeedCurator.WATCH_WINDOW_MILLIS + 100)
        h.feed.signals = Signals(hasShopAnchor = true)
        assertNull(h.showCurrent())
        assertEquals(0, h.scrolls)
        assertTrue(h.sightings.sightings.isEmpty())
        assertTrue(h.updates.isEmpty())
        assertEquals(listOf(DecisionEvent.PAGE), h.decisions.entries.map { it.event })
    }

    @Test
    fun shrinkingSignalsChangeNothing() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals(isPaidAd = true, hasShopAnchor = true, disclosureLabel = "Ad")
        h.showCurrent()
        val before = h.sightings.sightings.values.single()
        h.sightings.calls.clear()

        testScheduler.advanceTimeBy(500)
        h.feed.signals = Signals(isPaidAd = true, disclosureLabel = "Ad") // the product card animated out
        assertNull(h.showCurrent())
        testScheduler.advanceTimeBy(500)
        h.feed.signals = Signals()
        assertNull(h.showCurrent())

        assertTrue(h.sightings.calls.isEmpty())
        assertEquals(before, h.sightings.sightings.values.single())
        assertTrue(h.updates.isEmpty())
        assertEquals(0, h.scrolls)
        assertTrue(h.decisions.of(DecisionEvent.WATCH).isEmpty())
    }

    @Test
    fun watchedPageIsSkippedOnlyOnce() = runTest {
        val h = Harness(this)
        h.feed.scrollAdvances = false // the scroll doesn't move the feed: the same page stays on screen
        h.feed.signals = Signals()
        h.showCurrent()
        testScheduler.advanceTimeBy(500)
        h.feed.signals = Signals(hasShopAnchor = true)
        h.showCurrent()
        assertEquals(1, h.scrolls)

        testScheduler.advanceTimeBy(300)
        h.feed.signals = Signals(hasShopAnchor = true, isLiveShopping = true) // grows again
        assertNull(h.showCurrent())
        h.showCurrent()
        assertEquals("never a second scroll for the same page", 1, h.scrolls)
        assertEquals(1, h.outcomes.size)
    }

    @Test
    fun lateLabelInADisallowedContextIsLoggedButNotSkipped() = runTest {
        val h = Harness(this)
        h.feed.feedContext = FeedContext.SEARCH
        h.feed.signals = Signals()
        h.showCurrent()
        testScheduler.advanceTimeBy(700)
        h.feed.signals = Signals(hasShopAnchor = true)
        assertNotNull(h.showCurrent())
        assertEquals(0, h.scrolls)
        assertEquals(UserAction.NONE, h.sightings.sightings.values.single().userAction)
        val w = h.decisions.of(DecisionEvent.WATCH).single()
        assertEquals(FeedContext.SEARCH, w.context)
        assertEquals("hold:CONTEXT_OFF", w.gate)
        assertNull(w.outcome)
    }

    @Test
    fun openingAnotherScreenEndsTheWatch() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals()
        h.showCurrent()
        h.curator.onSnapshot(
            ScreenSnapshot.LongPressMenu(capturedAtMillis = 0, notInterestedNode = null), // the user long-pressed
        )
        testScheduler.advanceTimeBy(500)
        h.feed.signals = Signals(hasShopAnchor = true)
        assertNull(h.showCurrent())
        assertEquals(0, h.scrolls)
    }

    @Test
    fun transitionFramesDoNotEndTheWatch() = runTest {
        val h = Harness(this)
        h.feed.signals = Signals()
        h.showCurrent()
        h.curator.onSnapshot(ScreenSnapshot.Unknown(0))
        testScheduler.advanceTimeBy(500)
        h.feed.signals = Signals(hasShopAnchor = true)
        h.showCurrent()
        assertEquals(1, h.scrolls)
    }

    @Test
    fun watchSkipsStillCountTowardsTheConsecutiveCap() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(consecutiveSkipCap = 2)
        repeat(2) {
            h.feed.signals = Signals()
            h.showCurrent() // a new page, nothing detected yet (held: KEEP)
            testScheduler.advanceTimeBy(500)
            h.feed.signals = Signals(hasShopAnchor = true)
            h.showCurrent() // late Shop card → skipped
        }
        assertEquals(2, h.scrolls)
        assertTrue("a late-skipped page is not a kept page", h.guard.isPaused)
    }

    @Test
    fun refinedSignatureKeepsTheWatchAndTheFirstReadsSightingKey() = runTest {
        val h = Harness(this)
        h.settings = FeedCleanSettings() // not armed: only the logging path
        fun page(caption: String?, signature: String, signals: Signals, at: Long) = ScreenSnapshot.FeedVideo(
            capturedAtMillis = at, displayName = "Creator", probableHandle = null, caption = caption,
            soundDescription = "Sound: x", signals = signals, pageSignature = signature, isForYouTab = true,
        )
        h.curator.onSnapshot(page(caption = null, signature = "sig-a", signals = Signals(), at = 0))
        testScheduler.advanceTimeBy(400)
        // Caption bound late (new signature, same page) together with the Shop anchor.
        assertNotNull(h.curator.onSnapshot(page("caption", "sig-b", Signals(hasShopAnchor = true), at = 400)))
        assertEquals(1, h.pages.size)
        assertEquals("sig-a", h.sightings.sightings.values.single().pageSignature)
    }

    @Test
    fun noPagerRetryNeverAppliesToADifferentPage() = runTest {
        val h = Harness(this)
        h.feed.hasPager = false
        h.showCurrent()
        h.feed.signals = Signals() // the user scrolled on: a different, normal page
        h.feed.pageIndex++
        h.feed.hasPager = true
        h.showCurrent()
        assertEquals(0, h.scrolls)
    }

    @Test
    fun loopGuardCapPausesAndKeptPageResets() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(consecutiveSkipCap = 2)
        h.showCurrent()
        h.showCurrent()
        assertTrue(h.guard.isPaused)
        assertTrue((h.outcomes.last() as AutoSkipOutcome.Skipped).pausedNow)

        h.showCurrent()
        assertEquals(SkipGateResult.Hold(SkipHold.PAUSED), h.pages.last().second)
        assertEquals(2, h.scrolls)

        h.guard.resume()
        h.feed.pageIndex++
        h.feed.signals = Signals() // a kept page
        h.showCurrent()
        assertEquals(0, h.guard.consecutiveSkips)
    }

    @Test
    fun scrollingBackToASkippedPageShowsIt() = runTest {
        val h = Harness(this)
        h.showCurrent() // page-0 skipped → page-1
        val shop = h.feed.signals
        h.feed.signals = Signals()
        h.showCurrent() // page-1 (a normal post) is watched
        h.feed.signals = shop
        h.feed.pageIndex = 0 // the user scrolls back up
        h.showCurrent()
        assertEquals(SkipGateResult.Hold(SkipHold.ALREADY_ATTEMPTED), h.pages.last().second)
        assertEquals(1, h.scrolls)
    }

    // ── Interruption and automated Not interested ──────────────────────────────────────────────────────────

    @Test
    fun cancelActiveSkipInterruptsWithoutScrolling() = runTest {
        val h = Harness(this)
        val job = launch { h.showCurrent() }
        advanceTimeBy(100)
        runCurrent()
        assertTrue(h.curator.isSkipping)

        assertTrue(h.curator.cancelActiveSkip())
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(AutoSkipOutcome.Cancelled(SkipCancel.INTERRUPTED, null), h.outcomes.single())
        assertEquals(0, h.scrolls)
        assertFalse(h.cover.showing)
        assertFalse(h.curator.isSkipping)
        assertEquals(UserAction.NONE, h.sightings.sightings.values.single().userAction)
    }

    @Test
    fun automatedNotInterestedIsRecordedAsNotInterested() = runTest {
        val h = Harness(this)
        h.settings = ARMED.copy(notInterestedAutomated = true, notInterestedRiskAcknowledged = true)
        h.ni = {
            h.feed.pageIndex++
            NotInterestedResult.Done(verified = true, reasonChosen = null)
        }
        h.showCurrent()
        val s = h.sightings.sightings.values.single()
        assertEquals(UserAction.NOT_INTERESTED, s.userAction)
        assertEquals(true, s.actionVerified)
        assertEquals(SkipReason.ORGANIC_SHOP, s.skipReason)
        assertEquals(0, h.scrolls)
    }

    @Test
    fun profileSnapshotRecordsShopCategoryOncePerHandle() = runTest {
        val h = Harness(this)
        val profile = ScreenSnapshot.Profile(
            capturedAtMillis = 0, displayName = "Seller", handle = "seller", accountCategory = "Shop",
            isShopCreator = true, isBlockedByMe = false,
        )
        h.curator.onSnapshot(profile)
        h.curator.onSnapshot(profile)
        assertEquals(1, h.accounts.calls.count { it.startsWith("ensureHandleAccount") })
    }

    // ── Real device fixtures ───────────────────────────────────────────────────────────────────────────────

    /** Serves a fixed parsed screen; a scroll replaces it with [next]. */
    private class FixtureDriver(var current: ScreenSnapshot, val next: ScreenSnapshot, val clock: () -> Long) :
        ScreenDriver {
        val actions = mutableListOf<String>()
        override suspend fun snapshot() = current
        override suspend fun click(node: UiNode) = true.also { actions += "click" }
        override suspend fun tap(x: Int, y: Int) = true.also { actions += "tap" }
        override suspend fun longPress(node: UiNode) = true.also { actions += "longPress" }
        override suspend fun scrollForward(node: UiNode): Boolean {
            actions += "scrollForward:${node.viewIdName}"
            current = next
            return true
        }
        override suspend fun back() = true.also { actions += "back" }
        override suspend fun awaitSnapshot(timeoutMillis: Long, pollMillis: Long, predicate: (ScreenSnapshot) -> Boolean) =
            pollForSnapshot(::snapshot, timeoutMillis, pollMillis, clock, predicate)
    }

    private fun fixtureRun(name: String, settings: FeedCleanSettings = ARMED, check: (Harness, FixtureDriver) -> Unit) =
        runTest {
            val screen = Fixtures.parse(name)
            val driver = FixtureDriver(screen, Fixtures.parse("a_normal"), { testScheduler.currentTime })
            val h = Harness(this, driver)
            h.settings = settings
            h.curator.onSnapshot(screen)
            check(h, driver)
        }

    @Test
    fun fixtureOrganicShopIsSkippedWithOneScrollOnThePager() = fixtureRun("d_shop_affiliate") { h, d ->
        assertEquals(listOf("scrollForward:viewpager"), d.actions)
        assertEquals(UserAction.AUTO_SKIP, h.sightings.sightings.values.single().userAction)
    }

    @Test
    fun fixturePaidAdIsLoggedAndKept() = fixtureRun("b_paid_ad") { h, d ->
        assertTrue(d.actions.isEmpty())
        assertTrue(h.sightings.sightings.values.single().signals.isPaidAd)
    }

    @Test
    fun fixturePromotedIsLoggedAndKept() = fixtureRun("c_promoted") { h, d ->
        assertTrue(d.actions.isEmpty())
        assertTrue(h.sightings.sightings.values.single().signals.isPromoted)
    }

    @Test
    fun fixtureNormalPageIsNeitherLoggedNorSkipped() = fixtureRun("a_normal") { h, d ->
        assertTrue(d.actions.isEmpty())
        assertTrue(h.sightings.sightings.isEmpty())
        assertEquals(1, h.pages.size)
    }

    @Test
    fun fixtureShopNotSkippedWhileCurationIsOff() = fixtureRun("d_shop_affiliate", FeedCleanSettings()) { h, d ->
        assertTrue(d.actions.isEmpty())
        assertEquals(1, h.sightings.sightings.size)
    }

    @Test
    fun fixtureMenuAndDialogScreensNeverStartAnything() = runTest {
        for (name in listOf("j_longpress_menu", "f_profile_share_sheet", "g_block_confirm", "h_profile_blocked")) {
            val screen = Fixtures.parse(name)
            val driver = FixtureDriver(screen, screen, { testScheduler.currentTime })
            val h = Harness(this, driver)
            assertNull(name, h.curator.onSnapshot(screen))
            assertTrue(name, driver.actions.isEmpty())
            assertTrue(name, h.sightings.sightings.isEmpty())
        }
    }

    private companion object {
        /** Cover + settle explicitly on: several tests exercise the cover/settle mechanics (defaults are off/0). */
        val ARMED = FeedCleanSettings(curateFeedEnabled = true, skipCoverEnabled = true, skipSettleDelayMs = 300)
    }
}
