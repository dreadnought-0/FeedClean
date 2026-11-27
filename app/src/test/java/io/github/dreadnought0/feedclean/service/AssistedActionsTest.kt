package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.RejectReason
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeAccountRepository
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeBlockEventRepository
import io.github.dreadnought0.feedclean.testutil.FakeFeedDriver
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card's actions end-to-end: real executors + shared rate limiter + persistence, fake screen and DB. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistedActionsTest {

    private class Harness(scope: TestScope, useFeedDriver: Boolean = false) {
        val clock = { scope.testScheduler.currentTime }
        val tiktok = FakeScreenDriver(clock)
        val feed = FakeFeedDriver(clock)
        val actionLog = FakeActionLogRepository()
        /** Cover explicitly on: the manual-skip test exercises the cover (the default is off). */
        var settings = FeedCleanSettings(skipCoverEnabled = true)
        val limiter = RateLimiter(actionLog, { settings }, clock)
        val accounts = FakeAccountRepository()
        val sightings = FakeSightingRepository()
        val events = FakeBlockEventRepository(accounts)
        val cover = FakeSkipCover(clock)
        val actions = AssistedActions(
            driver = if (useFeedDriver) feed else tiktok,
            rateLimiter = limiter,
            settings = { settings },
            persistence = CardActionPersistence(accounts, sightings, events, clock),
            cover = cover,
            clock = clock,
        )

        suspend fun target(name: String = FakeScreenDriver.DEFAULT_NAME): CardTarget {
            val id = accounts.upsertFromFeed(name, null, clock())
            return CardTarget(id, null, "page-0", name, null, Signals(hasShopAnchor = true))
        }

        fun click(source: String) = UserIntentToken.fromUserClick(source, clock())
    }

    // ── Block ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun blockFromACardClickRunsTheVerifiedFlowAndPersists() = runTest {
        val h = Harness(this)
        val steps = mutableListOf<BlockStep>()

        val result = h.actions.block(h.click("overlay_card.block"), h.target()) { steps += it }

        assertTrue(result is BlockResult.Blocked)
        assertTrue("click:dialog.confirm" in h.tiktok.actions)
        assertTrue(BlockStep.VERIFY in steps)
        val account = h.accounts.findByHandle(FakeScreenDriver.DEFAULT_HANDLE)!!
        assertEquals(AccountStatus.BLOCKED, account.status)
        assertTrue(h.events.events.single().verified)
        assertEquals(UserAction.BLOCK, h.sightings.sightings.values.single().userAction)
        assertEquals("one shared rate-limit entry", ActionKind.BLOCK, h.actionLog.entries.single().kind)
    }

    @Test
    fun oneClickStartsAtMostOneBlock() = runTest {
        val h = Harness(this)
        val token = h.click("overlay_card.block")
        h.actions.block(token, h.target())
        val actionsBefore = h.tiktok.actions.size

        val again = h.actions.block(token, h.target())

        assertEquals(BlockResult.Rejected(RejectReason.TOKEN_ALREADY_USED), again)
        assertEquals(actionsBefore, h.tiktok.actions.size)
        assertEquals(1, h.events.events.size)
    }

    @Test
    fun staleClickIsRejectedWithoutTouchingTheScreen() = runTest {
        val h = Harness(this)
        val token = h.click("overlay_card.block")
        advanceTimeBy(UserIntentToken.DEFAULT_MAX_AGE_MILLIS + 1)
        assertEquals(BlockResult.Rejected(RejectReason.STALE_TOKEN), h.actions.block(token, h.target()))
        assertTrue(h.tiktok.actions.isEmpty())
        assertTrue(h.events.events.isEmpty())
    }

    @Test
    fun cancelledBlockIsRecordedAsCancelledAtItsStep() = runTest {
        val h = Harness(this)
        val target = h.target()
        val job = launch { h.actions.block(h.click("overlay_card.block"), target) }
        advanceTimeBy(150)
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        val e = h.events.events.single()
        assertEquals(CardActionPersistence.FAILURE_CANCELLED, e.failureReason)
        assertEquals(BlockStep.READ_HANDLE.name, e.stepReached)
        assertEquals("no tap after cancellation", listOf("click:feed.avatar"), h.tiktok.actions)
    }

    // ── Not interested ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun manualNotInterestedIsVerifiedAndPersisted() = runTest {
        val h = Harness(this)
        val result = h.actions.notInterested(h.click("overlay_card.not_interested"), h.target())
        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = null), result)
        val s = h.sightings.sightings.values.single()
        assertEquals(UserAction.NOT_INTERESTED, s.userAction)
        assertEquals(true, s.actionVerified)
        assertEquals(ActionKind.NOT_INTERESTED, h.actionLog.entries.single().kind)
    }

    // ── Manual skip ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun manualSkipScrollsOnceBehindTheCover() = runTest {
        val h = Harness(this, useFeedDriver = true)
        val result = h.actions.skip(h.click("overlay_card.skip"), h.target())
        assertEquals(ManualSkipResult.SKIPPED, result)
        assertEquals(listOf("scrollForward:feed.pager"), h.feed.actions)
        assertEquals(listOf("show:BLANK:-", "hide"), h.cover.calls)
        val s = h.sightings.sightings.values.single()
        assertEquals(UserAction.SKIP, s.userAction)
        assertEquals(true, s.actionVerified)
        assertTrue("navigation is not rate-limited", h.actionLog.entries.isEmpty())
    }

    @Test
    fun manualSkipNeedsAFreshUnusedClick() = runTest {
        val h = Harness(this, useFeedDriver = true)
        val stale = h.click("overlay_card.skip")
        advanceTimeBy(UserIntentToken.DEFAULT_MAX_AGE_MILLIS + 1)
        assertEquals(ManualSkipResult.REJECTED, h.actions.skip(stale, h.target()))

        val token = h.click("overlay_card.skip")
        h.actions.skip(token, h.target())
        h.feed.pageIndex = 0
        assertEquals(ManualSkipResult.REJECTED, h.actions.skip(token, h.target()))
        assertEquals(1, h.feed.actions.size)
    }

    @Test
    fun manualSkipNeverScrollsAPageTheCardWasNotOpenedFor() = runTest {
        val h = Harness(this, useFeedDriver = true)
        h.feed.pageIndex = 4
        assertEquals(ManualSkipResult.PAGE_CHANGED, h.actions.skip(h.click("overlay_card.skip"), h.target()))
        h.feed.pageIndex = 0
        h.feed.onFeed = false
        assertEquals(ManualSkipResult.NOT_ON_FEED, h.actions.skip(h.click("overlay_card.skip"), h.target()))
        assertTrue(h.feed.actions.isEmpty())
        assertTrue(h.cover.calls.isEmpty())
    }
}
