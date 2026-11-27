package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.block.BlockAbortReason
import io.github.dreadnought0.feedclean.block.BlockFlowExecutor
import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.block.BlockTarget
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.curate.AutoSkipContext
import io.github.dreadnought0.feedclean.curate.AutoSkipController
import io.github.dreadnought0.feedclean.curate.LoopGuard
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.curate.NotInterestedRunner
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.service.FeedCurator
import io.github.dreadnought0.feedclean.service.FeedPagePipeline
import io.github.dreadnought0.feedclean.service.ScreenDriver
import io.github.dreadnought0.feedclean.service.SessionTracker
import io.github.dreadnought0.feedclean.service.pollForSnapshot
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeAccountRepository
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeSessionRepository
import io.github.dreadnought0.feedclean.testutil.FakeSightingRepository
import io.github.dreadnought0.feedclean.testutil.FakeSkipCover
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the (unchanged) runtime does with a parsed LIVE preview: auto-skip is one scroll on the pager and nothing
 * else, and the block flow stops before touching the screen because the preview exposes no profile button.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePreviewDownstreamTest {

    /** Serves a parsed fixture; one scroll replaces it with [next]. Records every action. */
    private class FixtureDriver(var current: ScreenSnapshot, val next: ScreenSnapshot, val clock: () -> Long) :
        ScreenDriver {
        val actions = mutableListOf<String>()
        override suspend fun snapshot() = current
        override suspend fun click(node: UiNode) = true.also { actions += "click:${node.viewIdName}" }
        override suspend fun tap(x: Int, y: Int) = true.also { actions += "tap" }
        override suspend fun longPress(node: UiNode) = true.also { actions += "longPress:${node.viewIdName}" }
        override suspend fun scrollForward(node: UiNode): Boolean {
            actions += "scrollForward:${node.viewIdName}"
            current = next
            return true
        }
        override suspend fun back() = true.also { actions += "back" }
        override suspend fun awaitSnapshot(timeoutMillis: Long, pollMillis: Long, predicate: (ScreenSnapshot) -> Boolean) =
            pollForSnapshot(::snapshot, timeoutMillis, pollMillis, clock, predicate)
    }

    private class Harness(scope: TestScope, screen: ScreenSnapshot, val settings: FeedCleanSettings) {
        val clock = { scope.testScheduler.currentTime }
        val driver = FixtureDriver(screen, Fixtures.parse("a_normal"), clock)
        val accounts = FakeAccountRepository()
        val sightings = FakeSightingRepository()
        var notInterestedRuns = 0
        private val guard = LoopGuard()
        private val controller = AutoSkipController(
            driver, FakeSkipCover(clock), guard, { settings },
            NotInterestedRunner { _, _, _ ->
                notInterestedRuns++
                NotInterestedResult.Done(verified = true, reasonChosen = null)
            },
        )
        val curator = FeedCurator(
            pipeline = FeedPagePipeline(accounts, sightings, clock),
            autoSkip = controller,
            loopGuard = guard,
            sightings = sightings,
            session = SessionTracker(FakeSessionRepository(), clock),
            settings = { settings },
            context = { AutoSkipContext(tiktokForeground = true, screenInteractive = true) },
            clock = clock,
        )
    }

    private val shopping: ScreenSnapshot by lazy {
        Fixtures.parser.parse(Fixtures.syntheticRoots("live_shopping_preview"), Fixtures.NOW)
    }
    private val plain: ScreenSnapshot by lazy { Fixtures.parse("k_feed_live_preview") }

    /** Curation on, every automation armed, including automated Not-interested. */
    private val everythingArmed = FeedCleanSettings(
        curateFeedEnabled = true, autoSkipLiveShopping = true, autoSkipPaidAds = true, autoSkipPromoted = true,
        notInterestedAutomated = true, notInterestedRiskAcknowledged = true,
    )

    @Test
    fun liveShoppingPreview_withItsTarget_isSkippedByOneScrollOnThePagerOnly() = runTest {
        val h = Harness(this, shopping, everythingArmed)
        val processed = checkNotNull(h.curator.onSnapshot(shopping))
        assertEquals(CurationDecision.Skip(SkipReason.LIVE_SHOPPING), processed.decision)
        assertEquals(listOf("scrollForward:viewpager"), h.driver.actions)
        assertEquals("Not interested is only ever paired with organic Shop", 0, h.notInterestedRuns)
        val sighting = h.sightings.sightings.values.single()
        assertEquals(UserAction.AUTO_SKIP, sighting.userAction)
        assertEquals(SkipReason.LIVE_SHOPPING, sighting.skipReason)
        assertTrue(sighting.signals.isLiveShopping)
    }

    @Test
    fun liveShoppingPreview_withDefaultTargets_isLoggedAndKept() = runTest {
        val h = Harness(this, shopping, FeedCleanSettings(curateFeedEnabled = true))
        val processed = checkNotNull(h.curator.onSnapshot(shopping))
        assertEquals(CurationDecision.Keep, processed.decision)
        assertTrue(h.driver.actions.isEmpty())
        val sighting = h.sightings.sightings.values.single()
        assertEquals(UserAction.NONE, sighting.userAction)
        assertEquals("LIVE auction", sighting.signals.anchorText)
    }

    @Test
    fun plainLivePreview_isProcessedKeptAndNotLogged() = runTest {
        val h = Harness(this, plain, everythingArmed)
        val processed = checkNotNull(h.curator.onSnapshot(plain))
        assertEquals("Streamer A", processed.page.displayName)
        assertEquals(CurationDecision.Keep, processed.decision)
        assertTrue(h.driver.actions.isEmpty())
        assertTrue(h.sightings.sightings.isEmpty())
        assertNull("same stream, same page", h.curator.onSnapshot(Fixtures.parse("k_feed_live_preview")))
    }

    @Test
    fun knownShopStreamer_isNeverSkippedAsOrganicShop_soNotInterestedNeverRuns() = runTest {
        val h = Harness(this, shopping, everythingArmed.copy(autoSkipLiveShopping = false))
        h.accounts.seed(displayName = "Seller Streamer B", category = FeedPagePipeline.SHOP_CATEGORY)
        val processed = checkNotNull(h.curator.onSnapshot(shopping))
        assertTrue("the pipeline enriched the streamer as a Shop creator", processed.signals.isShopCreator)
        assertEquals(CurationDecision.Keep, processed.decision)
        assertTrue(h.driver.actions.isEmpty())
        assertEquals(0, h.notInterestedRuns)
    }

    @Test
    fun blockFromALivePreview_stopsBeforeTouchingTheScreen() = runTest {
        for (screen in listOf(plain, shopping)) {
            val clock = { testScheduler.currentTime }
            val driver = FixtureDriver(screen, screen, clock)
            val limiter = RateLimiter(FakeActionLogRepository(), { FeedCleanSettings() }, clock)
            val executor = BlockFlowExecutor(driver, limiter, clock)
            val name = (screen as ScreenSnapshot.FeedVideo).displayName
            val result = executor.run(
                UserIntentToken.fromUserClick("test.block", clock()),
                BlockTarget(accountId = 1, expectedDisplayName = name, expectedHandle = null),
                FeedCleanSettings().blockStyle,
            )
            assertEquals(
                name,
                BlockResult.Aborted(BlockStep.OPEN_PROFILE, BlockAbortReason.NO_PROFILE_BUTTON, null, returnedToFeed = true, detail = "screen=FeedVideo"),
                result,
            )
            assertTrue("$name: nothing tapped", driver.actions.isEmpty())
        }
    }
}
