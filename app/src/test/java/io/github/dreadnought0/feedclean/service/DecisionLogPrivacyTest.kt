package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.curate.AutoSkipContext
import io.github.dreadnought0.feedclean.curate.AutoSkipController
import io.github.dreadnought0.feedclean.curate.LoopGuard
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.NotInterestedRunner
import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeAccountRepository
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeBlockEventRepository
import io.github.dreadnought0.feedclean.testutil.FakeDecisionLog
import io.github.dreadnought0.feedclean.testutil.FakeFeedDriver
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver
import io.github.dreadnought0.feedclean.testutil.FakeSessionRepository
import io.github.dreadnought0.feedclean.testutil.FakeSightingRepository
import io.github.dreadnought0.feedclean.testutil.FakeSkipCover
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision log must never contain screen text: pages and card targets full of names, handles, captions, and
 * product/label text go through the real curator and card actions, and every encoded line is checked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DecisionLogPrivacyTest {

    private val secrets = listOf(
        "Zed Secretname", "Zed", "Secretname", "zed.secret", "Glow Serum", "1k sold", "Sponsored", "Shop now",
        "caption", "page-0", "page-1", FakeScreenDriver.DEFAULT_NAME, FakeScreenDriver.DEFAULT_HANDLE,
    )

    private fun assertClean(log: FakeDecisionLog) {
        assertTrue("something was logged", log.entries.isNotEmpty())
        log.entries.map(DecisionLogCodec::encode).forEach { line ->
            secrets.forEach { secret -> assertFalse("'$secret' leaked into $line", line.contains(secret, ignoreCase = true)) }
        }
    }

    @Test
    fun curatorEntriesCarryNoScreenText() = runTest {
        val clock = { testScheduler.currentTime }
        val feed = FakeFeedDriver(clock).apply {
            displayName = "Zed Secretname"
            feedContext = FeedContext.FOR_YOU
            signals = Signals(disclosureLabel = "Sponsored", ctaText = "Shop now") // label text not yet classified
        }
        val log = FakeDecisionLog()
        val guard = LoopGuard()
        var settings = FeedCleanSettings(curateFeedEnabled = true, autoSkipPaidAds = true)
        val accounts = FakeAccountRepository()
        val sightings = FakeSightingRepository()
        val curator = FeedCurator(
            pipeline = FeedPagePipeline(accounts, sightings, clock),
            autoSkip = AutoSkipController(feed, FakeSkipCover(clock), guard, { settings }, NotInterestedRunner { _, _, _ ->
                NotInterestedResult.Done(verified = true, reasonChosen = "Glow Serum 1k sold")
            }),
            loopGuard = guard,
            sightings = sightings,
            session = SessionTracker(FakeSessionRepository(), clock),
            settings = { settings },
            context = { AutoSkipContext(tiktokForeground = true, screenInteractive = true) },
            decisionLog = log,
            clock = clock,
        )

        curator.onSnapshot(feed.snapshot()) // first read: no classified signal yet
        testScheduler.advanceTimeBy(600)
        feed.signals = Signals(
            isPaidAd = true, hasShopAnchor = true, disclosureLabel = "Sponsored", anchorText = "Glow Serum 1k sold",
            ctaText = "Shop now",
        )
        curator.onSnapshot(feed.snapshot()) // late label → skipped (paid ads target on)
        settings = settings.copy(notInterestedAutomated = true, notInterestedRiskAcknowledged = true, autoSkipPaidAds = false)
        feed.signals = Signals(hasShopAnchor = true, anchorText = "Glow Serum 1k sold")
        curator.onSnapshot(feed.snapshot()) // page-1: organic Shop with automated Not interested

        assertEquals(setOf(DecisionEvent.PAGE, DecisionEvent.WATCH), log.entries.map { it.event }.toSet())
        assertClean(log)
    }

    @Test
    fun cardActionEntriesCarryOnlyEnumNames() = runTest {
        val clock = { testScheduler.currentTime }
        val tiktok = FakeScreenDriver(clock)
        val log = FakeDecisionLog()
        val settings = FeedCleanSettings()
        val accounts = FakeAccountRepository()
        val actions = AssistedActions(
            driver = tiktok,
            rateLimiter = RateLimiter(FakeActionLogRepository(), { settings }, clock),
            settings = { settings },
            persistence = CardActionPersistence(accounts, FakeSightingRepository(), FakeBlockEventRepository(accounts), clock),
            cover = FakeSkipCover(clock),
            clock = clock,
            decisionLog = log,
        )
        val id = accounts.upsertFromFeed(FakeScreenDriver.DEFAULT_NAME, null, 0)
        val target = CardTarget(
            accountId = id, sightingId = null, pageSignature = "page-0", displayName = FakeScreenDriver.DEFAULT_NAME,
            knownHandle = null, signals = Signals(hasShopAnchor = true, anchorText = "Glow Serum 1k sold"),
            feedContext = FeedContext.FOR_YOU,
        )

        actions.block(UserIntentToken.fromUserClick("overlay_card.block", clock()), target)
        testScheduler.advanceTimeBy(10_000) // past the rate limiter's minimum interval
        actions.notInterested(UserIntentToken.fromUserClick("overlay_card.not_interested", clock()), target)
        actions.skip(UserIntentToken.fromUserClick("overlay_card.skip", clock()), target)

        val cards = log.of(DecisionEvent.CARD)
        assertEquals(listOf(DecisionCardAction.BLOCK, DecisionCardAction.NOT_INTERESTED, DecisionCardAction.SKIP), cards.map { it.action })
        assertEquals(listOf("Blocked", "VERIFIED"), listOf(cards[0].result, cards[0].reason))
        assertTrue(cards.all { it.context == FeedContext.FOR_YOU && it.kinds == listOf(DetectionRules.Kind.SHOP) })
        assertClean(log)
    }
}
