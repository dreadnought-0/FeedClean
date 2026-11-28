package io.github.dreadnought0.feedclean.armed

import io.github.dreadnought0.feedclean.armed.ArmedLevel.ARMED
import io.github.dreadnought0.feedclean.armed.ArmedLevel.BLOCKED
import io.github.dreadnought0.feedclean.armed.ArmedLevel.OFF
import io.github.dreadnought0.feedclean.armed.ArmedLevel.PAUSED
import io.github.dreadnought0.feedclean.armed.Behaviour.AUTO_NOT_INTERESTED
import io.github.dreadnought0.feedclean.armed.Behaviour.BLOCK_BUTTON
import io.github.dreadnought0.feedclean.armed.Behaviour.NOT_INTERESTED_BUTTON
import io.github.dreadnought0.feedclean.armed.Behaviour.SKIP_LIVE_SHOPPING
import io.github.dreadnought0.feedclean.armed.Behaviour.SKIP_PAID_AD
import io.github.dreadnought0.feedclean.armed.Behaviour.SKIP_PROMOTED
import io.github.dreadnought0.feedclean.armed.Behaviour.SKIP_SHOP
import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.CurationPolicy
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmedEvaluatorTest {

    /** Everything on and ready: the "all armed" Home. */
    private val allOn = FeedCleanSettings(
        curateFeedEnabled = true,
        autoSkipOrganicShop = true,
        autoSkipPaidAds = true,
        autoSkipPromoted = true,
        autoSkipLiveShopping = true,
        notInterestedAutomated = true,
        notInterestedRiskAcknowledged = true,
    )
    private val running = LiveFacts(serviceConnected = true)

    private fun eval(settings: FeedCleanSettings = allOn, facts: LiveFacts = running) = ArmedEvaluator.evaluate(settings, facts)

    @Test
    fun `everything on and the service running arms all seven`() {
        val r = eval()
        assertEquals(7, r.total)
        assertEquals(7, r.armedCount)
        assertEquals(ARMED, r.detection.level)
        assertEquals(ARMED, r.autoSkip.level)
        assertTrue(r.anySkipArmed)
    }

    @Test
    fun `defaults - auto-skip master is off by default so only the manual buttons are armed`() {
        val r = eval(FeedCleanSettings())
        assertEquals(OFF, r.autoSkip.level)
        assertEquals(BLOCKED, r.of(SKIP_SHOP).level)
        assertEquals(Requirement.AUTO_SKIP_ON, r.of(SKIP_SHOP).reason)
        assertEquals(OFF, r.of(SKIP_PAID_AD).level)
        assertEquals(OFF, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(ARMED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(ARMED, r.of(BLOCK_BUTTON).level)
    }

    @Test
    fun `service not running blocks everything that is switched on, with the service as the reason`() {
        val r = eval(facts = LiveFacts(serviceConnected = false))
        assertEquals(0, r.armedCount)
        r.behaviours.values.forEach {
            assertEquals(BLOCKED, it.level)
            assertEquals(Requirement.SERVICE_RUNNING, it.reason)
        }
        assertEquals(BLOCKED, r.detection.level)
        assertEquals(BLOCKED, r.autoSkip.level)
    }

    @Test
    fun `a skip type whose detection kind is off is blocked by that kind - matches CurationPolicy`() {
        val s = allOn.copy(detectPaidAd = false)
        val r = eval(s)
        assertEquals(BLOCKED, r.of(SKIP_PAID_AD).level)
        assertEquals(Requirement.DETECT_PAID_AD_ON, r.of(SKIP_PAID_AD).reason)
        // The policy agrees: a labelled paid ad is not skipped.
        assertEquals(CurationDecision.Keep, CurationPolicy.decide(Signals(isPaidAd = true), s))
        assertEquals(ARMED, r.of(SKIP_PROMOTED).level)
        assertEquals(6, r.armedCount)
    }

    @Test
    fun `each skip type maps to its own detection kind`() {
        assertEquals(Requirement.DETECT_SHOP_ON, eval(allOn.copy(detectShopAnchor = false)).of(SKIP_SHOP).reason)
        assertEquals(Requirement.DETECT_PROMOTED_ON, eval(allOn.copy(detectPromoted = false)).of(SKIP_PROMOTED).reason)
        assertEquals(
            Requirement.DETECT_LIVE_SHOPPING_ON,
            eval(allOn.copy(detectLiveShopping = false)).of(SKIP_LIVE_SHOPPING).reason,
        )
    }

    @Test
    fun `detection master off blocks every skip and auto Not interested but not the manual buttons`() {
        val r = eval(allOn.copy(detectionEnabled = false))
        assertEquals(OFF, r.detection.level)
        Behaviour.SKIPS.forEach { assertEquals(Requirement.DETECTION_ON, r.of(it).reason) }
        assertEquals(Requirement.DETECTION_ON, r.of(AUTO_NOT_INTERESTED).reason)
        assertEquals(ARMED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(ARMED, r.of(BLOCK_BUTTON).level)
        assertEquals(BLOCKED, r.autoSkip.level)
    }

    @Test
    fun `no place under where it skips blocks every skip - the old autoSkipArmed said armed here`() {
        val s = allOn.copy(autoSkipInForYou = false, autoSkipInOtherHomeTabs = false, autoSkipInSearch = false, autoSkipInOtherFeeds = false)
        assertTrue("the runtime gate alone is satisfied", s.autoSkipAllowedBySettings)
        val r = eval(s)
        Behaviour.SKIPS.forEach {
            assertEquals(BLOCKED, r.of(it).level)
            assertEquals(Requirement.PLACE_SELECTED, r.of(it).reason)
        }
        assertEquals(Requirement.PLACE_SELECTED, r.autoSkip.reason)
        assertFalse(r.anySkipArmed)
    }

    @Test
    fun `pause everything pauses automatic behaviours but not the manual buttons`() {
        val r = eval(allOn.copy(killSwitchEngaged = true))
        Behaviour.SKIPS.forEach {
            assertEquals(PAUSED, r.of(it).level)
            assertEquals(Requirement.NOT_PAUSED_BY_USER, r.of(it).reason)
        }
        assertEquals(PAUSED, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(PAUSED, r.autoSkip.level)
        assertEquals(ARMED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(ARMED, r.of(BLOCK_BUTTON).level)
        assertEquals(2, r.armedCount)
        assertEquals(5, r.count(PAUSED))
    }

    @Test
    fun `the safety stop pauses skips with its own reason`() {
        val r = eval(facts = LiveFacts(serviceConnected = true, loopGuardPaused = true, loopGuardPausedAfter = 8))
        assertEquals(PAUSED, r.of(SKIP_SHOP).level)
        assertEquals(Requirement.NOT_PAUSED_BY_SAFETY_STOP, r.of(SKIP_SHOP).reason)
        assertEquals(PAUSED, r.autoSkip.level)
    }

    @Test
    fun `a blocker wins over a pause`() {
        val r = eval(allOn.copy(killSwitchEngaged = true, detectPaidAd = false))
        assertEquals(BLOCKED, r.of(SKIP_PAID_AD).level)
        assertEquals(PAUSED, r.of(SKIP_SHOP).level)
    }

    @Test
    fun `auto Not interested needs the risk note`() {
        val r = eval(allOn.copy(notInterestedRiskAcknowledged = false))
        assertEquals(BLOCKED, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(Requirement.RISK_ACCEPTED, r.of(AUTO_NOT_INTERESTED).reason)
    }

    @Test
    fun `auto Not interested only runs on skipped Shop posts so it needs Skip Shop posts`() {
        val r = eval(allOn.copy(autoSkipOrganicShop = false))
        assertEquals(BLOCKED, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(Requirement.SKIP_SHOP_ON, r.of(AUTO_NOT_INTERESTED).reason)
        // The runtime gate alone would have said yes.
        assertTrue(allOn.copy(autoSkipOrganicShop = false).automatedNotInterestedAllowedBySettings)
    }

    @Test
    fun `auto Not interested needs Shop detection and the auto-skip master`() {
        assertEquals(Requirement.DETECT_SHOP_ON, eval(allOn.copy(detectShopAnchor = false)).of(AUTO_NOT_INTERESTED).reason)
        assertEquals(Requirement.AUTO_SKIP_ON, eval(allOn.copy(curateFeedEnabled = false)).of(AUTO_NOT_INTERESTED).reason)
    }

    @Test
    fun `hiding the bubble blocks Block and the Not interested button`() {
        val r = eval(allOn.copy(showBubble = false))
        assertEquals(BLOCKED, r.of(BLOCK_BUTTON).level)
        assertEquals(Requirement.BUBBLE_SHOWN, r.of(BLOCK_BUTTON).reason)
        assertEquals(Requirement.BUBBLE_SHOWN, r.of(NOT_INTERESTED_BUTTON).reason)
        assertEquals(ARMED, r.of(SKIP_SHOP).level) // skips don't need the bubble
    }

    @Test
    fun `Not interested button switched off is off, Block has no switch`() {
        val r = eval(allOn.copy(notInterestedManualEnabled = false))
        assertEquals(OFF, r.of(NOT_INTERESTED_BUTTON).level)
        assertNull(r.of(NOT_INTERESTED_BUTTON).reason)
        assertEquals(ARMED, r.of(BLOCK_BUTTON).level)
    }

    @Test
    fun `detection master is blocked when no kind is selected`() {
        val r = eval(allOn.copy(detectPaidAd = false, detectPromoted = false, detectShopAnchor = false, detectLiveShopping = false))
        assertEquals(BLOCKED, r.detection.level)
        assertEquals(Requirement.ANY_DETECTION_KIND_ON, r.detection.reason)
        assertEquals(Requirement.SKIP_TYPE_READY, r.autoSkip.reason)
    }

    @Test
    fun `auto-skip master is blocked when every skip type is off`() {
        val r = eval(allOn.copy(autoSkipOrganicShop = false, autoSkipPaidAds = false, autoSkipPromoted = false, autoSkipLiveShopping = false))
        assertEquals(BLOCKED, r.autoSkip.level)
        assertEquals(Requirement.SKIP_TYPE_READY, r.autoSkip.reason)
    }

    @Test
    fun `checks list every condition in order with the reason as the first unmet one`() {
        val s = allOn.copy(detectPaidAd = false, killSwitchEngaged = true)
        val status = eval(s).of(SKIP_PAID_AD)
        assertEquals(
            listOf(
                Requirement.SERVICE_RUNNING, Requirement.DETECTION_ON, Requirement.DETECT_PAID_AD_ON,
                Requirement.AUTO_SKIP_ON, Requirement.PLACE_SELECTED,
                Requirement.NOT_PAUSED_BY_USER, Requirement.NOT_PAUSED_BY_SAFETY_STOP,
            ),
            status.checks.map { it.requirement },
        )
        assertEquals(status.checks.first { !it.met }.requirement, status.reason)
    }

    @Test
    fun `auto-skip master is armed exactly when at least one skip type is armed`() {
        // Exhaustive over the switches that feed both, so the master can never contradict the rows under it.
        val bools = listOf(false, true)
        for (service in bools) for (detection in bools) for (curate in bools) for (kill in bools) for (loop in bools)
            for (shop in bools) for (detectShop in bools) for (paid in bools) for (detectPaid in bools) for (place in bools) {
                val s = FeedCleanSettings(
                    detectionEnabled = detection, curateFeedEnabled = curate, killSwitchEngaged = kill,
                    autoSkipOrganicShop = shop, detectShopAnchor = detectShop,
                    autoSkipPaidAds = paid, detectPaidAd = detectPaid,
                    autoSkipPromoted = false, autoSkipLiveShopping = false,
                    autoSkipInForYou = place,
                )
                val r = ArmedEvaluator.evaluate(s, LiveFacts(serviceConnected = service, loopGuardPaused = loop))
                assertEquals(s.toString(), r.anySkipArmed, r.autoSkip.isArmed)
            }
    }


    // ── Card-level summaries (Home header pills) ───────────────────────────────────────────────

    @Test
    fun `Not interested card is off only when both the button and auto are off`() {
        val r = eval(allOn.copy(notInterestedManualEnabled = false, notInterestedAutomated = false))
        assertEquals(OFF, r.notInterestedCard.level)
        assertNull(r.notInterestedCard.reason)
    }

    @Test
    fun `Not interested card is armed when the button is armed and auto is off`() {
        val r = eval(allOn.copy(notInterestedAutomated = false))
        assertEquals(ARMED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(OFF, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(ARMED, r.notInterestedCard.level)
    }

    @Test
    fun `Not interested card is blocked with auto's reason when the button is armed and auto is blocked`() {
        val r = eval(allOn.copy(notInterestedRiskAcknowledged = false))
        assertEquals(ARMED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(BLOCKED, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(BLOCKED, r.notInterestedCard.level)
        assertEquals(Requirement.RISK_ACCEPTED, r.notInterestedCard.reason)
    }

    @Test
    fun `Not interested card is paused when auto is paused even if the button is armed`() {
        val r = eval(allOn.copy(killSwitchEngaged = true))
        assertEquals(ARMED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(PAUSED, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(PAUSED, r.notInterestedCard.level)
        assertEquals(Requirement.NOT_PAUSED_BY_USER, r.notInterestedCard.reason)
    }

    @Test
    fun `Not interested card prefers the button's reason when both enabled rows are blocked`() {
        val r = eval(allOn.copy(showBubble = false), LiveFacts(serviceConnected = false))
        assertEquals(BLOCKED, r.of(NOT_INTERESTED_BUTTON).level)
        assertEquals(Requirement.SERVICE_RUNNING, r.of(NOT_INTERESTED_BUTTON).reason)
        assertEquals(BLOCKED, r.of(AUTO_NOT_INTERESTED).level)
        assertEquals(BLOCKED, r.notInterestedCard.level)
        assertEquals(Requirement.SERVICE_RUNNING, r.notInterestedCard.reason)
    }

    @Test
    fun `Not interested card is blocked by the hidden bubble when auto is off`() {
        val r = eval(allOn.copy(showBubble = false, notInterestedAutomated = false))
        assertEquals(BLOCKED, r.notInterestedCard.level)
        assertEquals(Requirement.BUBBLE_SHOWN, r.notInterestedCard.reason)
    }

    @Test
    fun `Blocking card follows the Block button`() {
        assertEquals(ARMED, eval().blockingCard.level)
        val hidden = eval(allOn.copy(showBubble = false))
        assertEquals(BLOCKED, hidden.blockingCard.level)
        assertEquals(Requirement.BUBBLE_SHOWN, hidden.blockingCard.reason)
        val noService = eval(facts = LiveFacts(serviceConnected = false))
        assertEquals(BLOCKED, noService.blockingCard.level)
        assertEquals(Requirement.SERVICE_RUNNING, noService.blockingCard.reason)
    }

    @Test
    fun `helpers card is armed when the bubble is on and the service is running`() {
        val r = eval() // showBubble defaults to true, cover off
        assertEquals(ARMED, r.helpersCard.level)
        assertNull(r.helpersCard.reason)
    }

    @Test
    fun `helpers card is off when the bubble and the cover are both off`() {
        val r = eval(allOn.copy(showBubble = false, skipCoverEnabled = false))
        assertEquals(OFF, r.helpersCard.level)
    }

    @Test
    fun `helpers card is blocked when a helper is on but the service is not running`() {
        val r = eval(facts = LiveFacts(serviceConnected = false))
        assertEquals(BLOCKED, r.helpersCard.level)
        assertEquals(Requirement.SERVICE_RUNNING, r.helpersCard.reason)
    }

    @Test
    fun `helpers card is armed when only the cover is on and the service is running`() {
        val r = eval(allOn.copy(showBubble = false, skipCoverEnabled = true))
        assertEquals(ARMED, r.helpersCard.level)
    }

    @Test
    fun `helpers card is not paused by Pause everything`() {
        val r = eval(allOn.copy(killSwitchEngaged = true, skipCoverEnabled = true))
        assertEquals(ARMED, r.helpersCard.level)
    }

    @Test
    fun `summarizeCard is off only when every status is off, otherwise worst enabled wins`() {
        val off = ArmedStatus(OFF)
        val armed = ArmedStatus(ARMED)
        val paused = ArmedStatus(PAUSED, Requirement.NOT_PAUSED_BY_USER)
        val blocked = ArmedStatus(BLOCKED, Requirement.BUBBLE_SHOWN)
        val blockedService = ArmedStatus(BLOCKED, Requirement.SERVICE_RUNNING)
        assertEquals(OFF, summarizeCard(off, off).level)
        assertEquals(ARMED, summarizeCard(armed, off).level)
        assertEquals(PAUSED, summarizeCard(armed, paused).level)
        assertEquals(Requirement.NOT_PAUSED_BY_USER, summarizeCard(armed, paused).reason)
        assertEquals(BLOCKED, summarizeCard(armed, paused, blocked).level)
        assertEquals(Requirement.BUBBLE_SHOWN, summarizeCard(armed, paused, blocked).reason)
        // First of the worst level wins.
        assertEquals(Requirement.BUBBLE_SHOWN, summarizeCard(blocked, blockedService).reason)
        assertEquals(Requirement.SERVICE_RUNNING, summarizeCard(blockedService, blocked).reason)
        assertEquals(OFF, summarizeCard().level)
    }


    // ── Detection kinds (Home Detection sub-panel) ─────────────────────────────────────────────

    @Test
    fun `each detection kind is armed when Detection is on and the service is running`() {
        val r = eval()
        DetectionKind.entries.forEach { kind ->
            assertEquals(kind.name, ARMED, r.of(kind).level)
        }
        assertEquals(ARMED, r.detection.level)
    }

    @Test
    fun `a detection kind switched off is Off, and the matching skip is blocked by that kind`() {
        val r = eval(allOn.copy(detectPaidAd = false))
        assertEquals(OFF, r.of(DetectionKind.PAID_AD).level)
        assertNull(r.of(DetectionKind.PAID_AD).reason)
        assertEquals(ARMED, r.of(DetectionKind.SHOP).level)
        assertEquals(BLOCKED, r.of(SKIP_PAID_AD).level)
        assertEquals(Requirement.DETECT_PAID_AD_ON, r.of(SKIP_PAID_AD).reason)
    }

    @Test
    fun `Detection master stays armed when only one kind is off if another kind is still on`() {
        val r = eval(allOn.copy(detectPaidAd = false))
        assertEquals(ARMED, r.detection.level)
        assertEquals(ARMED, r.autoSkip.level)
        assertEquals(ARMED, r.of(SKIP_SHOP).level)
    }

    @Test
    fun `a detection kind that is on is blocked when Detection or the service is off`() {
        assertEquals(Requirement.DETECTION_ON, eval(allOn.copy(detectionEnabled = false)).of(DetectionKind.SHOP).reason)
        assertEquals(Requirement.SERVICE_RUNNING, eval(facts = LiveFacts(serviceConnected = false)).of(DetectionKind.SHOP).reason)
        assertEquals(OFF, eval(allOn.copy(detectShopAnchor = false, detectionEnabled = false)).of(DetectionKind.SHOP).level)
    }

    @Test
    fun `turning every detection kind off blocks the Detection master and Auto-skip`() {
        val r = eval(allOn.copy(detectPaidAd = false, detectPromoted = false, detectShopAnchor = false, detectLiveShopping = false))
        DetectionKind.entries.forEach { assertEquals(it.name, OFF, r.of(it).level) }
        assertEquals(BLOCKED, r.detection.level)
        assertEquals(Requirement.ANY_DETECTION_KIND_ON, r.detection.reason)
        assertEquals(Requirement.SKIP_TYPE_READY, r.autoSkip.reason)
        Behaviour.SKIPS.forEach {
            assertEquals(it.name, BLOCKED, r.of(it).level)
        }
    }
}
