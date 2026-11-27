package io.github.dreadnought0.feedclean.curate

import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSkipGateTest {
    private val skip = CurationDecision.Skip(SkipReason.ORGANIC_SHOP)
    private val armed = FeedCleanSettings(curateFeedEnabled = true)
    private val ok = AutoSkipContext(tiktokForeground = true, screenInteractive = true)

    private fun page(
        forYou: Boolean = true,
        pager: Boolean = true,
        signature: String = "sig",
        context: FeedContext = if (forYou) FeedContext.FOR_YOU else FeedContext.OTHER,
    ) = FeedVideo(
        capturedAtMillis = 0,
        displayName = "Creator",
        probableHandle = null,
        caption = null,
        soundDescription = null,
        signals = Signals(hasShopAnchor = true),
        pageSignature = signature,
        isForYouTab = forYou,
        pagerNode = if (pager) FakeUiNode("pager", isScrollable = true) else null,
        feedContext = context,
    )

    private fun eval(
        decision: CurationDecision = skip,
        page: FeedVideo = page(),
        settings: FeedCleanSettings = armed,
        context: AutoSkipContext = ok,
        guard: LoopGuard = LoopGuard(),
    ) = AutoSkipGate.evaluate(decision, page, settings, context, guard)

    private fun hold(why: SkipHold) = SkipGateResult.Hold(why)

    @Test
    fun allConditionsMet() = assertEquals(SkipGateResult.Go(SkipReason.ORGANIC_SHOP), eval())

    @Test
    fun keepDecisionNeverSkips() = assertEquals(hold(SkipHold.KEEP), eval(decision = CurationDecision.Keep))

    @Test
    fun notArmed() = assertEquals(hold(SkipHold.NOT_ARMED), eval(settings = FeedCleanSettings()))

    @Test
    fun killSwitch() = assertEquals(
        hold(SkipHold.KILL_SWITCH),
        eval(settings = armed.copy(killSwitchEngaged = true)),
    )

    @Test
    fun notForeground() = assertEquals(hold(SkipHold.NOT_FOREGROUND), eval(context = ok.copy(tiktokForeground = false)))

    @Test
    fun defaultsAllowOnlyTheForYouFeed() {
        assertEquals(SkipGateResult.Go(SkipReason.ORGANIC_SHOP), eval(page = page(context = FeedContext.FOR_YOU)))
        for (ctx in FeedContext.entries - FeedContext.FOR_YOU) {
            assertEquals("$ctx is off by default", hold(SkipHold.CONTEXT_OFF), eval(page = page(context = ctx)))
        }
    }

    /** Every context × its own toggle: the toggle for a context decides it, and only it. */
    @Test
    fun eachContextFollowsItsOwnToggle() {
        for (ctx in FeedContext.entries) {
            for (on in listOf(true, false)) {
                // Every OTHER context gets the opposite value, so a gate reading the wrong toggle fails.
                val s = FeedContext.entries.fold(armed) { acc, c -> acc.withContext(c, if (c == ctx) on else !on) }
                val expected = if (on) SkipGateResult.Go(SkipReason.ORGANIC_SHOP) else hold(SkipHold.CONTEXT_OFF)
                assertEquals("$ctx toggle=$on", expected, eval(page = page(context = ctx), settings = s))
            }
        }
    }

    @Test
    fun searchAndProfilePlayersSkipOnceTheirToggleIsOn() {
        val s = armed.copy(autoSkipInSearch = true, autoSkipInOtherFeeds = true)
        assertEquals(SkipGateResult.Go(SkipReason.ORGANIC_SHOP), eval(page = page(forYou = false, context = FeedContext.SEARCH), settings = s))
        assertEquals(SkipGateResult.Go(SkipReason.ORGANIC_SHOP), eval(page = page(forYou = false, context = FeedContext.OTHER), settings = s))
        assertEquals(hold(SkipHold.CONTEXT_OFF), eval(page = page(forYou = false, context = FeedContext.HOME_OTHER_TAB), settings = s))
    }

    @Test
    fun contextOffComesAfterArmingAndForegroundButBeforeTheOtherGates() {
        val off = page(context = FeedContext.SEARCH, pager = false)
        assertEquals(hold(SkipHold.NOT_ARMED), eval(page = off, settings = FeedCleanSettings()))
        assertEquals(hold(SkipHold.NOT_FOREGROUND), eval(page = off, context = ok.copy(tiktokForeground = false)))
        assertEquals(hold(SkipHold.CONTEXT_OFF), eval(page = off, context = ok.copy(cardOpen = true)))
    }

    @Test
    fun otherGatesStillApplyInAnAllowedContext() {
        val s = armed.copy(autoSkipInSearch = true)
        val search = page(context = FeedContext.SEARCH)
        assertEquals(hold(SkipHold.CARD_OPEN), eval(page = search, settings = s, context = ok.copy(cardOpen = true)))
        assertEquals(hold(SkipHold.NO_PAGER), eval(page = page(context = FeedContext.SEARCH, pager = false), settings = s))
        assertEquals(hold(SkipHold.KILL_SWITCH), eval(page = search, settings = s.copy(killSwitchEngaged = true)))
        assertEquals(hold(SkipHold.ALREADY_ATTEMPTED), eval(page = search, settings = s, guard = LoopGuard().apply { markAttempt("sig") }))
    }

    private fun FeedCleanSettings.withContext(ctx: FeedContext, on: Boolean) = when (ctx) {
        FeedContext.FOR_YOU -> copy(autoSkipInForYou = on)
        FeedContext.HOME_OTHER_TAB -> copy(autoSkipInOtherHomeTabs = on)
        FeedContext.SEARCH -> copy(autoSkipInSearch = on)
        FeedContext.OTHER -> copy(autoSkipInOtherFeeds = on)
    }

    @Test
    fun screenOffHoldsOnlyWhenTheSettingSaysSo() {
        val off = ok.copy(screenInteractive = false)
        assertEquals(hold(SkipHold.SCREEN_OFF), eval(context = off))
        assertEquals(
            SkipGateResult.Go(SkipReason.ORGANIC_SHOP),
            eval(context = off, settings = armed.copy(autoSkipOnlyWhenScreenOn = false)),
        )
    }

    @Test
    fun incompleteRead() = assertEquals(hold(SkipHold.INCOMPLETE_READ), eval(context = ok.copy(incompleteRead = true)))

    @Test
    fun actionInProgress() = assertEquals(hold(SkipHold.ACTION_IN_PROGRESS), eval(context = ok.copy(actionInProgress = true)))

    @Test
    fun cardOpen() = assertEquals(hold(SkipHold.CARD_OPEN), eval(context = ok.copy(cardOpen = true)))

    @Test
    fun noPager() = assertEquals(hold(SkipHold.NO_PAGER), eval(page = page(pager = false)))

    @Test
    fun pausedByLoopGuard() {
        val guard = LoopGuard().apply { onAutoSkipped(cap = 1) }
        assertEquals(hold(SkipHold.PAUSED), eval(guard = guard))
    }

    @Test
    fun alreadyAttempted() {
        val guard = LoopGuard().apply { markAttempt("sig") }
        assertEquals(hold(SkipHold.ALREADY_ATTEMPTED), eval(guard = guard))
    }

    @Test
    fun killSwitchWinsOverEverythingElse() {
        assertEquals(
            hold(SkipHold.KILL_SWITCH),
            eval(settings = armed.copy(killSwitchEngaged = true), context = AutoSkipContext(false, false)),
        )
    }
}
