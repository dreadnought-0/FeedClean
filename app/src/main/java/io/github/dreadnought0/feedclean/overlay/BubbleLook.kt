package io.github.dreadnought0.feedclean.overlay

import io.github.dreadnought0.feedclean.armed.ArmedEvaluator
import io.github.dreadnought0.feedclean.armed.LiveFacts
import io.github.dreadnought0.feedclean.service.BlockedMatch
import io.github.dreadnought0.feedclean.service.DetectionRules
import io.github.dreadnought0.feedclean.service.ProcessedPage
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/** The bubble's main state: what the current post is. */
enum class BubbleKind {
    /** Grey dot: nothing detected (or detection off). */
    NEUTRAL,

    /** Red: paid ad ("Ad" / "Sponsored"). */
    AD,

    /** Amber: promoted / paid partnership. */
    PROMO,

    /** Orange: TikTok Shop (anchor or known Shop creator). */
    SHOP,

    /** Purple: LIVE shopping. */
    LIVE,
}

/**
 * Everything the bubble draws. Pure data.
 *
 * @property signalCount number of distinct detected kinds; a count badge shows when it is 2 or more.
 * @property skipArmed show the skip glyph: at least one skip type is armed ([ArmedEvaluator]), not just the master.
 * @property warning "seen after block" badge (strong vs possible).
 * @property killSwitch the kill-switch is engaged (paused look).
 * @property loopGuardPaused auto-skip paused by the consecutive-skip cap (paused look + "tap to resume").
 * @property busy an assisted action is running (tap stops it).
 */
data class BubbleLook(
    val kind: BubbleKind = BubbleKind.NEUTRAL,
    val signalCount: Int = 0,
    val skipArmed: Boolean = false,
    val warning: BlockedMatch = BlockedMatch.NONE,
    val killSwitch: Boolean = false,
    val loopGuardPaused: Boolean = false,
    val busy: Boolean = false,
) {
    val paused: Boolean get() = killSwitch || loopGuardPaused
}

/** Maps the current page + settings + runtime flags to a [BubbleLook]. Pure. */
object BubbleLookMapper {
    /**
     * @param page the last processed feed page, or null if none (e.g. TikTok just came to the front).
     */
    fun map(
        page: ProcessedPage?,
        settings: FeedCleanSettings,
        loopGuardPaused: Boolean,
        busy: Boolean,
    ): BubbleLook {
        // The bubble only exists while the service runs, so the service is connected by definition here.
        val armed = ArmedEvaluator.evaluate(settings, LiveFacts(serviceConnected = true, loopGuardPaused = loopGuardPaused))
        val kinds = page?.let { DetectionRules.detectedKinds(it.signals, settings) }.orEmpty()
        return BubbleLook(
            kind = kinds.firstOrNull()?.toBubbleKind() ?: BubbleKind.NEUTRAL,
            signalCount = kinds.size,
            skipArmed = armed.anySkipArmed,
            warning = page?.blockedMatch ?: BlockedMatch.NONE,
            killSwitch = settings.killSwitchEngaged,
            loopGuardPaused = loopGuardPaused,
            busy = busy,
        )
    }

    private fun DetectionRules.Kind.toBubbleKind(): BubbleKind = when (this) {
        DetectionRules.Kind.PAID_AD -> BubbleKind.AD
        DetectionRules.Kind.PROMOTED -> BubbleKind.PROMO
        DetectionRules.Kind.SHOP -> BubbleKind.SHOP
        DetectionRules.Kind.LIVE_SHOPPING -> BubbleKind.LIVE
    }
}
