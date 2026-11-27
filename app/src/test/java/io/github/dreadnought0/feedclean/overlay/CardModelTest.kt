package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.block.BlockAbortReason
import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.block.DenyReason
import io.github.dreadnought0.feedclean.block.RateDecision
import io.github.dreadnought0.feedclean.block.RejectReason
import io.github.dreadnought0.feedclean.curate.NotInterestedAbortReason
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.NotInterestedStep
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.service.BlockedMatch
import io.github.dreadnought0.feedclean.service.DetectionRules
import io.github.dreadnought0.feedclean.service.ManualSkipResult
import io.github.dreadnought0.feedclean.service.ProcessedPage
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardModelTest {
    private val page = ProcessedPage(
        page = FeedVideo(
            capturedAtMillis = 0, displayName = "Creator", probableHandle = "heuristic.only", caption = null,
            soundDescription = null, signals = Signals(isPaidAd = true, hasShopAnchor = true), pageSignature = "sig",
            isForYouTab = true,
        ),
        accountId = 4,
        knownHandle = null,
        sightingId = 9,
        decision = CurationDecision.Keep,
        blockedMatch = BlockedMatch.POSSIBLE,
        processedAtMillis = 0,
    )

    @Test
    fun buildsTheCardFromThePage() {
        val m = CardModel.build(page, FeedCleanSettings(), CardStats(3, 1_000), setOf(TagNames.BOT_LIKE, "custom_tag"))
        assertEquals("Creator", m.displayName)
        assertNull("the sound heuristic is never shown as the handle", m.knownHandle)
        assertEquals(listOf(DetectionRules.Kind.PAID_AD, DetectionRules.Kind.SHOP), m.kinds)
        assertEquals(BlockedMatch.POSSIBLE, m.blockedMatch)
        assertEquals(setOf(TagNames.BOT_LIKE), m.manualTags)
        assertEquals(4L, m.target?.accountId)
        assertEquals(9L, m.target?.sightingId)
        assertEquals("sig", m.target?.pageSignature)
        assertTrue(m.showNotInterested)
        assertNull(m.rateLimitWaitMillis)
        assertFalse(m.busy)
    }

    @Test
    fun noPageMeansNoTarget() {
        val m = CardModel.build(null, FeedCleanSettings())
        assertNull(m.target)
        assertTrue(m.kinds.isEmpty())
    }

    @Test
    fun notInterestedButtonFollowsTheSetting() {
        assertFalse(CardModel.build(page, FeedCleanSettings(notInterestedManualEnabled = false)).showNotInterested)
    }

    @Test
    fun rateLimitWaitShown() {
        val m = CardModel.build(page, FeedCleanSettings(), rateLimit = RateDecision.Denied(4_200, DenyReason.MIN_INTERVAL))
        assertEquals(4_200L, m.rateLimitWaitMillis)
        assertEquals(5L, ActionMessages.waitSeconds(4_200))
        assertEquals(1L, ActionMessages.waitSeconds(1))
        assertNull(CardModel.build(page, FeedCleanSettings(), rateLimit = RateDecision.Allowed).rateLimitWaitMillis)
    }

    @Test
    fun runningActionMakesTheCardBusy() {
        assertTrue(CardModel.build(page, FeedCleanSettings(), progress = CardProgress.Running(CardActionKind.BLOCK)).busy)
    }

    @Test
    fun blockResultMessages() {
        assertEquals(
            ActionMessage(ActionMessage.Type.BLOCKED, "h", ok = true),
            ActionMessages.forBlock(BlockResult.Blocked("h", null, alreadyBlocked = false, returnedToFeed = true)),
        )
        assertEquals(
            ActionMessage.Type.ALREADY_BLOCKED,
            ActionMessages.forBlock(BlockResult.Blocked("h", null, alreadyBlocked = true, returnedToFeed = true)).type,
        )
        val stopped = ActionMessages.forBlock(
            BlockResult.Aborted(BlockStep.CHECK_DIALOG, BlockAbortReason.DIALOG_HANDLE_MISMATCH, null, true),
        )
        assertEquals(ActionMessage.Type.BLOCK_STOPPED, stopped.type)
        assertEquals("CHECK_DIALOG · DIALOG_HANDLE_MISMATCH", stopped.arg)
        assertFalse(stopped.ok)
        assertEquals("3", ActionMessages.forBlock(BlockResult.RateLimited(2_500, DenyReason.MIN_INTERVAL)).arg)
        assertEquals(ActionMessage.Type.REJECTED, ActionMessages.forBlock(BlockResult.Rejected(RejectReason.STALE_TOKEN)).type)
        assertEquals(ActionMessage.Type.BLOCK_TIMED_OUT, ActionMessages.forBlock(BlockResult.TimedOut("h")).type)
        assertEquals(ActionMessage.Type.BLOCK_USER_CANCELLED, ActionMessages.forBlock(BlockResult.UserCancelled("h", true)).type)
    }

    @Test
    fun notInterestedAndSkipMessages() {
        assertTrue(ActionMessages.forNotInterested(NotInterestedResult.Done(true, null)).ok)
        assertEquals(
            ActionMessage.Type.NOT_INTERESTED_UNVERIFIED,
            ActionMessages.forNotInterested(NotInterestedResult.Done(false, null)).type,
        )
        assertEquals(
            ActionMessage.Type.NOT_INTERESTED_STOPPED,
            ActionMessages.forNotInterested(
                NotInterestedResult.Aborted(NotInterestedStep.LONG_PRESS, NotInterestedAbortReason.MENU_DID_NOT_OPEN),
            ).type,
        )
        assertEquals(ActionMessage.Type.SKIPPED, ActionMessages.forSkip(ManualSkipResult.SKIPPED).type)
        assertEquals("PAGE_CHANGED", ActionMessages.forSkip(ManualSkipResult.PAGE_CHANGED).arg)
    }
}
