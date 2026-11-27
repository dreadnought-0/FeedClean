package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.block.BlockAbortReason
import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.block.DenyReason
import io.github.dreadnought0.feedclean.block.RejectReason
import io.github.dreadnought0.feedclean.curate.NotInterestedAbortReason
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.NotInterestedStep
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.testutil.FakeAccountRepository
import io.github.dreadnought0.feedclean.testutil.FakeBlockEventRepository
import io.github.dreadnought0.feedclean.testutil.FakeSightingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardActionPersistenceTest {
    private val accounts = FakeAccountRepository()
    private val sightings = FakeSightingRepository()
    private val events = FakeBlockEventRepository(accounts)
    private val persistence = CardActionPersistence(accounts, sightings, events) { NOW }

    private suspend fun target(withSighting: Boolean = true): CardTarget {
        val id = accounts.upsertFromFeed("Creator One", null, NOW)
        val sightingId = if (withSighting) {
            sightings.recordSighting(id, "page-0", SIGNALS, SightingSource.OVERLAY, nowMillis = NOW).id
        } else {
            null
        }
        return CardTarget(id, sightingId, "page-0", "Creator One", null, SIGNALS)
    }

    // ── Block ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun blockedLearnsHandleRecordsVerifiedEventAndMarksSighting() = runTest {
        val t = target()
        val survivor = persistence.persistBlock(t, BlockResult.Blocked("creator.one", "Creator One", alreadyBlocked = false, returnedToFeed = true))

        assertTrue("learnHandle:${t.accountId}:creator.one:DIALOG" in accounts.calls)
        val e = events.events.single()
        assertEquals(survivor, e.accountId)
        assertTrue(e.verified)
        assertEquals(BlockMethod.ASSISTED, e.method)
        assertEquals(BlockStep.VERIFY.name, e.stepReached)
        assertEquals(AccountStatus.BLOCKED, accounts.accounts.getValue(survivor).status)
        assertEquals("creator.one", accounts.accounts.getValue(survivor).handle)
        val s = sightings.sightings.getValue(checkNotNull(t.sightingId))
        assertEquals(UserAction.BLOCK, s.userAction)
        assertEquals(true, s.actionVerified)
    }

    @Test
    fun blockedMergesTheProvisionalAccountIntoAnExistingHandleAccount() = runTest {
        val known = accounts.seed(handle = "creator.one", displayName = "Creator One")
        val t = target()
        val survivor = persistence.persistBlock(t, BlockResult.Blocked("creator.one", null, alreadyBlocked = false, returnedToFeed = true))
        assertEquals(known.id, survivor)
        assertFalse("provisional merged away", t.accountId in accounts.accounts)
        assertEquals(known.id, events.events.single().accountId)
    }

    @Test
    fun alreadyBlockedComesFromTheProfile() = runTest {
        val t = target()
        persistence.persistBlock(t, BlockResult.Blocked("creator.one", null, alreadyBlocked = true, returnedToFeed = true))
        assertTrue(accounts.calls.any { it.endsWith(":creator.one:${HandleSource.PROFILE}") })
        assertEquals(BlockStep.READ_HANDLE.name, events.events.single().stepReached)
    }

    @Test
    fun abortBeforeAnyTapIsNotRecorded() = runTest {
        val t = target()
        for (reason in listOf(
            BlockAbortReason.NOT_ON_FEED_VIDEO,
            BlockAbortReason.FEED_SHOWS_DIFFERENT_ACCOUNT,
            BlockAbortReason.NO_PROFILE_BUTTON,
        )) {
            persistence.persistBlock(t, BlockResult.Aborted(BlockStep.OPEN_PROFILE, reason, null, returnedToFeed = true))
        }
        assertTrue(events.events.isEmpty())
        assertEquals(UserAction.NONE, sightings.sightings.values.single().userAction)
    }

    @Test
    fun abortAfterATapRecordsAnUnverifiedEventWithStepAndReason() = runTest {
        val t = target()
        persistence.persistBlock(
            t,
            BlockResult.Aborted(BlockStep.CHECK_DIALOG, BlockAbortReason.DIALOG_HANDLE_MISMATCH, "creator.one", returnedToFeed = true),
        )
        val e = events.events.single()
        assertFalse(e.verified)
        assertEquals("DIALOG_HANDLE_MISMATCH", e.failureReason)
        assertEquals("CHECK_DIALOG", e.stepReached)
        assertTrue("confirmed handle still learned", accounts.calls.any { it.endsWith(":creator.one:${HandleSource.PROFILE}") })
        assertEquals(AccountStatus.SEEN, accounts.accounts.values.single().status)
    }

    @Test
    fun abortWithoutLearnedHandleRecordsOnTheCardAccount() = runTest {
        val t = target()
        persistence.persistBlock(t, BlockResult.Aborted(BlockStep.READ_HANDLE, BlockAbortReason.PROFILE_DID_NOT_OPEN, null, true))
        assertEquals(t.accountId, events.events.single().accountId)
        assertTrue(accounts.calls.none { it.startsWith("learnHandle") })
    }

    @Test
    fun stopAtDialogOutcomes() = runTest {
        val t = target()
        persistence.persistBlock(t, BlockResult.UserCancelled("creator.one", returnedToFeed = true))
        persistence.persistBlock(t.copy(accountId = accounts.findByHandle("creator.one")!!.id), BlockResult.TimedOut("creator.one"))
        assertEquals(
            listOf(CardActionPersistence.FAILURE_USER_CANCELLED, CardActionPersistence.FAILURE_TIMED_OUT),
            events.events.map { it.failureReason },
        )
        assertTrue(events.events.all { it.stepReached == BlockStep.AWAITING_USER_CONFIRMATION.name && !it.verified })
    }

    @Test
    fun rateLimitedOrRejectedWritesNothing() = runTest {
        val t = target()
        persistence.persistBlock(t, BlockResult.RateLimited(1_000, DenyReason.MIN_INTERVAL))
        persistence.persistBlock(t, BlockResult.Rejected(RejectReason.STALE_TOKEN))
        assertTrue(events.events.isEmpty())
        assertTrue(accounts.calls.none { it.startsWith("learnHandle") })
    }

    @Test
    fun interruptedBlockIsRecordedOnlyOnceSomethingMayHaveBeenTapped() = runTest {
        val t = target()
        persistence.persistBlockInterrupted(t, BlockStep.CHECK_RATE_LIMIT)
        assertTrue(events.events.isEmpty())
        persistence.persistBlockInterrupted(t, BlockStep.OPEN_SHARE_SHEET)
        assertEquals(CardActionPersistence.FAILURE_CANCELLED, events.events.single().failureReason)
        assertEquals("OPEN_SHARE_SHEET", events.events.single().stepReached)
    }

    // ── Not interested, skip, log, false positive, tags ────────────────────────────────────────────────────

    @Test
    fun notInterestedResults() = runTest {
        val t = target()
        val id = checkNotNull(t.sightingId)
        persistence.persistNotInterested(t, NotInterestedResult.Aborted(NotInterestedStep.LONG_PRESS, NotInterestedAbortReason.MENU_DID_NOT_OPEN))
        assertEquals(UserAction.NONE, sightings.sightings.getValue(id).userAction)
        persistence.persistNotInterested(t, NotInterestedResult.RateLimited(1, DenyReason.HOURLY_CAP))
        assertEquals(UserAction.NONE, sightings.sightings.getValue(id).userAction)
        persistence.persistNotInterested(t, NotInterestedResult.Aborted(NotInterestedStep.TAP_NOT_INTERESTED, NotInterestedAbortReason.TAP_FAILED))
        assertEquals(UserAction.NOT_INTERESTED, sightings.sightings.getValue(id).userAction)
        assertEquals(false, sightings.sightings.getValue(id).actionVerified)
        persistence.persistNotInterested(t, NotInterestedResult.Done(verified = true, reasonChosen = null))
        assertEquals(true, sightings.sightings.getValue(id).actionVerified)
    }

    @Test
    fun skipOnAPageWithoutSightingLogsOne() = runTest {
        val t = target(withSighting = false)
        persistence.persistSkip(t, advanced = true)
        val s = sightings.sightings.values.single()
        assertEquals(UserAction.SKIP, s.userAction)
        assertEquals(true, s.actionVerified)
        assertEquals("page-0", s.pageSignature)
    }

    @Test
    fun logOnlyFillsAnEmptyActionButKeepsAnExistingOne() = runTest {
        val t = target()
        persistence.persistLogOnly(t)
        assertEquals(UserAction.LOG, sightings.sightings.values.single().userAction)
        sightings.setUserAction(checkNotNull(t.sightingId), UserAction.SKIP, true)
        persistence.persistLogOnly(t)
        assertEquals(UserAction.SKIP, sightings.sightings.values.single().userAction)
    }

    @Test
    fun falsePositiveSetsStatusAndAction() = runTest {
        val t = target()
        persistence.persistFalsePositive(t)
        assertEquals(AccountStatus.FALSE_POSITIVE, accounts.accounts.getValue(t.accountId).status)
        val s = sightings.sightings.values.single()
        assertEquals(UserAction.FALSE_POSITIVE, s.userAction)
        assertNull(s.actionVerified)
    }

    @Test
    fun manualTagsAddAndRemove() = runTest {
        val t = target()
        persistence.setManualTag(t.accountId, "bot_like", enabled = true)
        assertEquals(listOf(Triple(t.accountId, "bot_like", TagSource.MANUAL)), accounts.tags)
        persistence.setManualTag(t.accountId, "bot_like", enabled = false)
        assertTrue(accounts.tags.isEmpty())
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        val SIGNALS = Signals(hasShopAnchor = true)
    }
}
