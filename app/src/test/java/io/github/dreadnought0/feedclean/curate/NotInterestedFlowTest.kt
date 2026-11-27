package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.block.DenyReason
import io.github.dreadnought0.feedclean.block.RateLimiter
import io.github.dreadnought0.feedclean.block.RejectReason
import io.github.dreadnought0.feedclean.block.UserIntentToken
import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.settings.NotInterestedReason
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository
import io.github.dreadnought0.feedclean.testutil.FakeActionLogRepository.Entry
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver
import io.github.dreadnought0.feedclean.testutil.FakeScreenDriver.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotInterestedFlowTest {

    private class Harness(scope: TestScope) {
        val clock = { scope.testScheduler.currentTime }
        val driver = FakeScreenDriver(clock)
        val log = FakeActionLogRepository()
        var settings = FeedCleanSettings()
        val limiter = RateLimiter(log, { settings }, clock)
        val steps = mutableListOf<NotInterestedStep>()

        fun flow(isShoppingReason: (String) -> Boolean = NotInterestedFlow.DEFAULT_IS_SHOPPING_REASON) =
            NotInterestedFlow(driver, limiter, { settings }, clock, isShoppingReason)

        fun manual() = NotInterestedTrigger.Manual(UserIntentToken.fromUserClick("test.not_interested", clock()))

        suspend fun run(
            trigger: NotInterestedTrigger = manual(),
            expectedPageSignature: String? = "page-0",
            flow: NotInterestedFlow = flow(),
        ): NotInterestedResult = flow.run(trigger, ACCOUNT_ID, expectedPageSignature) { steps += it }
    }

    private fun harness(scope: TestScope, configure: FakeScreenDriver.() -> Unit = {}) =
        Harness(scope).apply { driver.configure() }

    // ── Manual ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun manualWithoutReasonPicker() = runTest {
        val h = harness(this)

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = null), result)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED), h.driver.actions)
        assertEquals(
            listOf(
                NotInterestedStep.CHECK_RATE_LIMIT, NotInterestedStep.FIND_VIDEO, NotInterestedStep.LONG_PRESS,
                NotInterestedStep.TAP_NOT_INTERESTED, NotInterestedStep.VERIFY,
            ),
            h.steps,
        )
        assertEquals("TikTok moved on to the next video", 1, h.driver.pageIndex)
        assertEquals(listOf(Entry(ActionKind.NOT_INTERESTED, h.clock(), ACCOUNT_ID, true)), h.log.entries)
    }

    @Test
    fun reasonPickerFirstOffered() = runTest {
        val h = harness(this) { reasons = REASONS }

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = REASONS[0]), result)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED, "click:reason:${REASONS[0]}"), h.driver.actions)
        assertEquals(REASONS[0], h.driver.chosenReason)
        assertTrue(NotInterestedStep.CHOOSE_REASON in h.steps)
    }

    @Test
    fun reasonPickerPreferShopping() = runTest {
        val h = harness(this) { reasons = REASONS }
        h.settings = FeedCleanSettings(notInterestedReason = NotInterestedReason.PREFER_SHOPPING)

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = REASONS[1]), result)
        assertEquals(REASONS[1], h.driver.chosenReason)
    }

    @Test
    fun reasonPickerPreferShoppingFallsBackToFirst() = runTest {
        val h = harness(this) { reasons = listOf("Seen too often", "Other") }
        h.settings = FeedCleanSettings(notInterestedReason = NotInterestedReason.PREFER_SHOPPING)

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = "Seen too often"), result)
    }

    @Test
    fun reasonPickerPreferShoppingUsesInjectedMatcher() = runTest {
        val h = harness(this) { reasons = listOf("Seen too often", "Ventas") }
        h.settings = FeedCleanSettings(notInterestedReason = NotInterestedReason.PREFER_SHOPPING)

        val result = h.run(flow = h.flow(isShoppingReason = { it == "Ventas" }))

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = "Ventas"), result)
    }

    @Test
    fun reasonPickerSkipReasonPressesBackOnce() = runTest {
        val h = harness(this) { reasons = REASONS }
        h.settings = FeedCleanSettings(notInterestedReason = NotInterestedReason.SKIP_REASON)

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = null), result)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED, BACK), h.driver.actions)
        assertEquals(null, h.driver.chosenReason)
        assertEquals(Screen.FEED, h.driver.screen)
    }

    @Test
    fun manualStaleTokenIsRejected() = runTest {
        val h = harness(this)
        val trigger = h.manual()
        testScheduler.advanceTimeBy(10_001)

        val result = h.run(trigger = trigger)

        assertEquals(NotInterestedResult.Rejected(RejectReason.STALE_TOKEN), result)
        assertUntouched(h)
    }

    @Test
    fun manualReusedTokenIsRejected() = runTest {
        val h = harness(this)
        val trigger = h.manual()
        h.run(trigger = trigger)
        val actionsAfterFirstRun = h.driver.actions.toList()

        val result = h.run(trigger = trigger, expectedPageSignature = null)

        assertEquals(NotInterestedResult.Rejected(RejectReason.TOKEN_ALREADY_USED), result)
        assertEquals(actionsAfterFirstRun, h.driver.actions)
    }

    @Test
    fun manualDisabledIsRejected() = runTest {
        val h = harness(this)
        h.settings = FeedCleanSettings(notInterestedManualEnabled = false)

        val result = h.run()

        assertEquals(NotInterestedResult.Rejected(RejectReason.MANUAL_DISABLED), result)
        assertUntouched(h)
    }

    // ── Automated ──────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun automatedNotArmedIsRejected() = runTest {
        val h = harness(this)
        val notArmed = listOf(
            FeedCleanSettings(),
            ARMED.copy(notInterestedRiskAcknowledged = false),
            ARMED.copy(curateFeedEnabled = false),
            ARMED.copy(killSwitchEngaged = true),
        )

        for (settings in notArmed) {
            h.settings = settings
            assertEquals(
                NotInterestedResult.Rejected(RejectReason.AUTOMATION_NOT_ARMED),
                h.run(trigger = NotInterestedTrigger.Automated(settings)),
            )
        }
        assertUntouched(h)
    }

    @Test
    fun automatedIsRejectedWhenTheKillSwitchFlipsAfterTheDecision() = runTest {
        val h = harness(this)
        h.settings = ARMED.copy(killSwitchEngaged = true)

        val result = h.run(trigger = NotInterestedTrigger.Automated(ARMED))

        assertEquals(NotInterestedResult.Rejected(RejectReason.AUTOMATION_NOT_ARMED), result)
        assertUntouched(h)
    }

    @Test
    fun automatedArmedRuns() = runTest {
        val h = harness(this)
        h.settings = ARMED

        val result = h.run(trigger = NotInterestedTrigger.Automated(ARMED))

        assertEquals(NotInterestedResult.Done(verified = true, reasonChosen = null), result)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED), h.driver.actions)
        assertEquals(listOf(ActionKind.NOT_INTERESTED), h.log.entries.map { it.kind })
    }

    @Test
    fun automatedRateLimitedTouchesNothing() = runTest {
        val h = harness(this)
        h.settings = ARMED
        h.log.seed(h.clock() - 2_000, kind = ActionKind.BLOCK)

        val result = h.run(trigger = NotInterestedTrigger.Automated(ARMED))

        assertEquals(NotInterestedResult.RateLimited(3_000, DenyReason.MIN_INTERVAL), result)
        assertEquals("not even a snapshot", 0, h.driver.snapshotCount)
        assertEquals(emptyList<String>(), h.driver.actions)
        assertEquals("nothing new logged", 1, h.log.entries.size)
    }

    @Test
    fun automatedRunsBackToBackAreRateLimited() = runTest {
        val h = harness(this)
        h.settings = ARMED
        h.run(trigger = NotInterestedTrigger.Automated(ARMED))
        h.driver.actions.clear()

        val result = h.run(trigger = NotInterestedTrigger.Automated(ARMED), expectedPageSignature = "page-1")

        assertTrue(result is NotInterestedResult.RateLimited)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    // ── Aborts ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun abortsWhenNotOnTheFeed() = runTest {
        val h = harness(this) { screen = Screen.PROFILE }

        val result = h.run()

        assertAborted(result, NotInterestedStep.FIND_VIDEO, NotInterestedAbortReason.NOT_ON_FEED_VIDEO)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsWhenThePageChanged() = runTest {
        val h = harness(this) { pageIndex = 3 }

        val result = h.run(expectedPageSignature = "page-0")

        assertAborted(result, NotInterestedStep.FIND_VIDEO, NotInterestedAbortReason.PAGE_CHANGED)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsOnALivePreviewWithoutTouchingTheScreen() = runTest {
        val h = harness(this) { feedIsLivePreview = true }

        val result = h.run()

        assertAborted(result, NotInterestedStep.FIND_VIDEO, NotInterestedAbortReason.LIVE_PREVIEW)
        assertEquals("no long-press: it could enter the live room", emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsWithoutAVideoNode() = runTest {
        val h = harness(this) { feedHasVideoNode = false }

        val result = h.run()

        assertAborted(result, NotInterestedStep.FIND_VIDEO, NotInterestedAbortReason.NO_VIDEO_NODE)
        assertEquals(emptyList<String>(), h.driver.actions)
    }

    @Test
    fun abortsWhenTheLongPressFails() = runTest {
        val h = harness(this) { failingTags = setOf(FakeScreenDriver.FEED_VIDEO) }

        val result = h.run()

        assertAborted(result, NotInterestedStep.LONG_PRESS, NotInterestedAbortReason.LONG_PRESS_FAILED)
        assertEquals(listOf(LONG_PRESS), h.driver.actions)
    }

    @Test
    fun abortsWhenNoMenuOpens() = runTest {
        val h = harness(this) { neverAppears = setOf(Screen.LONG_PRESS_MENU) }

        val result = h.run()

        assertAborted(result, NotInterestedStep.LONG_PRESS, NotInterestedAbortReason.MENU_DID_NOT_OPEN)
        assertEquals("still on the feed: no Back", listOf(LONG_PRESS), h.driver.actions)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun abortsAndClosesAMenuWithoutNotInterested() = runTest {
        val h = harness(this) { longPressOpens = Screen.OTHER_MENU }

        val result = h.run()

        assertAborted(result, NotInterestedStep.LONG_PRESS, NotInterestedAbortReason.NOT_INTERESTED_MISSING)
        assertFalse((result as NotInterestedResult.Aborted).mayHaveReachedTikTok)
        assertEquals(listOf(LONG_PRESS, BACK), h.driver.actions)
        testScheduler.advanceTimeBy(1_000)
        assertTrue("menu closed", h.driver.snapshot() is ScreenSnapshot.FeedVideo)
    }

    @Test
    fun abortsWhenTheNotInterestedTapFails() = runTest {
        val h = harness(this) { failingTags = setOf(FakeScreenDriver.MENU_NOT_INTERESTED) }

        val result = h.run()

        assertAborted(result, NotInterestedStep.TAP_NOT_INTERESTED, NotInterestedAbortReason.TAP_FAILED)
        assertTrue((result as NotInterestedResult.Aborted).mayHaveReachedTikTok)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED, BACK), h.driver.actions)
    }

    @Test
    fun unverifiedWhenTheMenuStaysOpenAfterTheTap() = runTest {
        val h = harness(this) { notInterestedClosesMenu = false }

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = false, reasonChosen = null), result)
        assertEquals("one Back, no retry", listOf(LONG_PRESS, NOT_INTERESTED, BACK), h.driver.actions)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    @Test
    fun skipReasonNeverPressesBackTwice() = runTest {
        val h = harness(this) {
            reasons = REASONS
            neverAppears = setOf(Screen.FEED) // the picker refuses to close
        }
        h.settings = FeedCleanSettings(notInterestedReason = NotInterestedReason.SKIP_REASON)

        val result = h.run()

        assertEquals(NotInterestedResult.Done(verified = false, reasonChosen = null), result)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED, BACK), h.driver.actions)
    }

    @Test
    fun driverErrorAbortsWithoutFurtherTaps() = runTest {
        val h = harness(this) { throwingTag = FakeScreenDriver.MENU_NOT_INTERESTED }

        val result = h.run()

        assertAborted(result, NotInterestedStep.TAP_NOT_INTERESTED, NotInterestedAbortReason.ERROR)
        assertEquals(listOf(LONG_PRESS, NOT_INTERESTED), h.driver.actions)
        assertEquals(listOf(false), h.log.entries.map { it.verified })
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────────────────

    private fun assertAborted(result: NotInterestedResult, step: NotInterestedStep, reason: NotInterestedAbortReason) {
        assertTrue("expected Aborted but was $result", result is NotInterestedResult.Aborted)
        result as NotInterestedResult.Aborted
        assertEquals(step, result.step)
        assertEquals(reason, result.reason)
    }

    private fun assertUntouched(h: Harness) {
        assertEquals(0, h.driver.snapshotCount)
        assertEquals(emptyList<String>(), h.driver.actions)
        assertEquals(emptyList<Entry>(), h.log.entries)
        assertEquals(emptyList<NotInterestedStep>(), h.steps)
    }

    private companion object {
        const val ACCOUNT_ID = 7L
        val ARMED = FeedCleanSettings(
            curateFeedEnabled = true,
            notInterestedAutomated = true,
            notInterestedRiskAcknowledged = true,
        )
        val REASONS = listOf("I don't like this video", "Too many shopping posts", "Other")

        const val LONG_PRESS = "longPress:${FakeScreenDriver.FEED_VIDEO}"
        const val NOT_INTERESTED = "click:${FakeScreenDriver.MENU_NOT_INTERESTED}"
        const val BACK = "back"
    }
}
