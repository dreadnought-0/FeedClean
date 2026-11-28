package io.github.dreadnought0.feedclean.armed

import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * The single source of truth for "will this behaviour actually act?". Home's summary, every Armed / Off /
 * Blocked / Paused pill, the reason sheet, the bubble's skip glyph and the Debug screen all read [ArmedEvaluator].
 *
 * "Armed" means: the behaviour's own switch is on AND every condition that decides whether it may act is met,
 * using the same rules the runtime uses ([io.github.dreadnought0.feedclean.detect.CurationPolicy],
 * [io.github.dreadnought0.feedclean.curate.AutoSkipGate], `NotInterestedFlow`, the overlay card). Conditions
 * that change from moment to moment (TikTok in front, screen on, another action running, the rate limit, the
 * post on screen being a LIVE preview) are deliberately NOT part of the badge: they are shown as one live status
 * line instead, so a badge doesn't flicker every time the user switches apps.
 */
enum class ArmedLevel { ARMED, OFF, BLOCKED, PAUSED }

/** The seven behaviours Home counts ("5 of 7 armed"), in display order. */
enum class Behaviour {
    SKIP_SHOP,
    SKIP_PAID_AD,
    SKIP_PROMOTED,
    SKIP_LIVE_SHOPPING,
    AUTO_NOT_INTERESTED,
    NOT_INTERESTED_BUTTON,
    BLOCK_BUTTON,
    ;

    val isSkip: Boolean get() = this in SKIPS

    companion object {
        val SKIPS: Set<Behaviour> = setOf(SKIP_SHOP, SKIP_PAID_AD, SKIP_PROMOTED, SKIP_LIVE_SHOPPING)
    }
}

/**
 * One condition a behaviour needs. [pause] conditions make a behaviour PAUSED rather than BLOCKED when they are the
 * only ones unmet.
 */
enum class Requirement(val pause: Boolean = false) {
    /** The accessibility service is bound and running (FeedClean can see TikTok). */
    SERVICE_RUNNING,

    /** Detection master switch. */
    DETECTION_ON,

    /** At least one kind is selected under Detection rules. */
    ANY_DETECTION_KIND_ON,
    DETECT_PAID_AD_ON,
    DETECT_PROMOTED_ON,
    DETECT_SHOP_ON,
    DETECT_LIVE_SHOPPING_ON,

    /** Auto-skip master switch ("Curate feed" in settings). */
    AUTO_SKIP_ON,

    /** At least one place under "Where it skips". */
    PLACE_SELECTED,

    /** At least one skip type is on and its detection is on (for the Auto-skip master badge). */
    SKIP_TYPE_READY,

    /** "Skip Shop posts" is on: automated Not interested only ever runs on a skipped organic Shop post. */
    SKIP_SHOP_ON,

    /** The account-risk note for automated Not interested was accepted. */
    RISK_ACCEPTED,

    /** The bubble is shown: the card (with Block / Not interested) only opens from the bubble. */
    BUBBLE_SHOWN,

    /** "Pause everything" is off. */
    NOT_PAUSED_BY_USER(pause = true),

    /** The safety stop (too many skips in a row) hasn't paused auto-skip. */
    NOT_PAUSED_BY_SAFETY_STOP(pause = true),
}

/** One checked condition. */
data class Check(val requirement: Requirement, val met: Boolean)

/**
 * The evaluated state of one behaviour or master switch.
 *
 * @property reason the first unmet condition (the one the UI explains and offers to fix); null when ARMED or OFF.
 * @property checks every condition, in the order the UI lists them (the reason sheet).
 */
data class ArmedStatus(
    val level: ArmedLevel,
    val reason: Requirement? = null,
    val checks: List<Check> = emptyList(),
) {
    val isArmed: Boolean get() = level == ArmedLevel.ARMED
}

/** Things that aren't settings but decide whether anything can act. */
data class LiveFacts(
    val serviceConnected: Boolean,
    val loopGuardPaused: Boolean = false,
    /** How many skips in a row triggered the safety stop (0 when not paused). */
    val loopGuardPausedAfter: Int = 0,
)

/** Everything Home needs about arming. */
data class ArmedReport(
    val behaviours: Map<Behaviour, ArmedStatus>,
    val detection: ArmedStatus,
    val autoSkip: ArmedStatus,
    /** Bubble and skip-cover, summarized for the On-screen helpers card header. */
    val helpersCard: ArmedStatus,
    val facts: LiveFacts,
) {
    fun of(behaviour: Behaviour): ArmedStatus = behaviours.getValue(behaviour)

    /**
     * Not interested card header: the button and automated Not interested, in display order.
     * See [summarizeCard].
     */
    val notInterestedCard: ArmedStatus
        get() = summarizeCard(of(Behaviour.NOT_INTERESTED_BUTTON), of(Behaviour.AUTO_NOT_INTERESTED))

    /** Blocking card header: the Block button (always present; it has no off switch). */
    val blockingCard: ArmedStatus
        get() = of(Behaviour.BLOCK_BUTTON)

    fun count(level: ArmedLevel): Int = behaviours.values.count { it.level == level }

    val armedCount: Int get() = count(ArmedLevel.ARMED)
    val total: Int get() = behaviours.size

    /** True when at least one skip type would act on the next matching post. */
    val anySkipArmed: Boolean get() = Behaviour.SKIPS.any { of(it).isArmed }

    /** Behaviours in [level], display order. */
    fun inLevel(level: ArmedLevel): List<Behaviour> = Behaviour.entries.filter { of(it).level == level }
}

/** Pure evaluation of [ArmedReport] from settings + live facts. */
object ArmedEvaluator {

    fun evaluate(settings: FeedCleanSettings, facts: LiveFacts): ArmedReport {
        val skips = mapOf(
            Behaviour.SKIP_SHOP to skip(settings, facts, settings.autoSkipOrganicShop, Requirement.DETECT_SHOP_ON, settings.detectShopAnchor),
            Behaviour.SKIP_PAID_AD to skip(settings, facts, settings.autoSkipPaidAds, Requirement.DETECT_PAID_AD_ON, settings.detectPaidAd),
            Behaviour.SKIP_PROMOTED to skip(settings, facts, settings.autoSkipPromoted, Requirement.DETECT_PROMOTED_ON, settings.detectPromoted),
            Behaviour.SKIP_LIVE_SHOPPING to
                skip(settings, facts, settings.autoSkipLiveShopping, Requirement.DETECT_LIVE_SHOPPING_ON, settings.detectLiveShopping),
        )
        val behaviours = LinkedHashMap<Behaviour, ArmedStatus>()
        Behaviour.entries.forEach { b ->
            behaviours[b] = when (b) {
                Behaviour.AUTO_NOT_INTERESTED -> automatedNotInterested(settings, facts)
                Behaviour.NOT_INTERESTED_BUTTON -> manual(settings.notInterestedManualEnabled, settings, facts)
                Behaviour.BLOCK_BUTTON -> manual(true, settings, facts)
                else -> skips.getValue(b)
            }
        }
        return ArmedReport(
            behaviours = behaviours,
            detection = detection(settings, facts),
            autoSkip = autoSkipMaster(settings, facts),
            helpersCard = summarizeCard(helper(settings.showBubble, facts), helper(settings.skipCoverEnabled, facts)),
            facts = facts,
        )
    }

    /** Conditions shared by every automatic behaviour, in the order the reason sheet lists them. */
    private fun automationChecks(settings: FeedCleanSettings, facts: LiveFacts): List<Check> = listOf(
        Check(Requirement.SERVICE_RUNNING, facts.serviceConnected),
        Check(Requirement.DETECTION_ON, settings.detectionEnabled),
    )

    private fun pauseChecks(settings: FeedCleanSettings, facts: LiveFacts): List<Check> = listOf(
        Check(Requirement.NOT_PAUSED_BY_USER, !settings.killSwitchEngaged),
        Check(Requirement.NOT_PAUSED_BY_SAFETY_STOP, !facts.loopGuardPaused),
    )

    private fun skip(
        settings: FeedCleanSettings,
        facts: LiveFacts,
        switchOn: Boolean,
        detectRequirement: Requirement,
        detectOn: Boolean,
    ): ArmedStatus {
        if (!switchOn) return ArmedStatus(ArmedLevel.OFF)
        val checks = automationChecks(settings, facts) + listOf(
            Check(detectRequirement, detectOn),
            Check(Requirement.AUTO_SKIP_ON, settings.curateFeedEnabled),
            Check(Requirement.PLACE_SELECTED, settings.anyAutoSkipPlace),
        ) + pauseChecks(settings, facts)
        return resolve(checks)
    }

    private fun automatedNotInterested(settings: FeedCleanSettings, facts: LiveFacts): ArmedStatus {
        if (!settings.notInterestedAutomated) return ArmedStatus(ArmedLevel.OFF)
        val checks = listOf(
            Check(Requirement.SERVICE_RUNNING, facts.serviceConnected),
            Check(Requirement.RISK_ACCEPTED, settings.notInterestedRiskAcknowledged),
            Check(Requirement.SKIP_SHOP_ON, settings.autoSkipOrganicShop),
            Check(Requirement.DETECTION_ON, settings.detectionEnabled),
            Check(Requirement.DETECT_SHOP_ON, settings.detectShopAnchor),
            Check(Requirement.AUTO_SKIP_ON, settings.curateFeedEnabled),
            Check(Requirement.PLACE_SELECTED, settings.anyAutoSkipPlace),
        ) + pauseChecks(settings, facts)
        return resolve(checks)
    }

    /**
     * Block and the Not interested button: user-started from the overlay card, so they need the service and the
     * bubble (the card only opens from it). Neither "Pause everything" nor detection gates them.
     */
    private fun manual(switchOn: Boolean, settings: FeedCleanSettings, facts: LiveFacts): ArmedStatus {
        if (!switchOn) return ArmedStatus(ArmedLevel.OFF)
        return resolve(
            listOf(
                Check(Requirement.SERVICE_RUNNING, facts.serviceConnected),
                Check(Requirement.BUBBLE_SHOWN, settings.showBubble),
            ),
        )
    }

    /** Bubble or skip-cover: each only needs the service (an overlay can't appear without it). */
    private fun helper(switchOn: Boolean, facts: LiveFacts): ArmedStatus {
        if (!switchOn) return ArmedStatus(ArmedLevel.OFF)
        return resolve(listOf(Check(Requirement.SERVICE_RUNNING, facts.serviceConnected)))
    }

    private fun detection(settings: FeedCleanSettings, facts: LiveFacts): ArmedStatus {
        if (!settings.detectionEnabled) return ArmedStatus(ArmedLevel.OFF)
        return resolve(
            listOf(
                Check(Requirement.SERVICE_RUNNING, facts.serviceConnected),
                Check(Requirement.ANY_DETECTION_KIND_ON, settings.anyDetectionKind),
            ),
        )
    }

    private fun autoSkipMaster(settings: FeedCleanSettings, facts: LiveFacts): ArmedStatus {
        if (!settings.curateFeedEnabled) return ArmedStatus(ArmedLevel.OFF)
        val anyTypeReady = listOf(
            settings.autoSkipOrganicShop && settings.detectShopAnchor,
            settings.autoSkipPaidAds && settings.detectPaidAd,
            settings.autoSkipPromoted && settings.detectPromoted,
            settings.autoSkipLiveShopping && settings.detectLiveShopping,
        ).any { it }
        val checks = automationChecks(settings, facts) + listOf(
            Check(Requirement.SKIP_TYPE_READY, anyTypeReady),
            Check(Requirement.PLACE_SELECTED, settings.anyAutoSkipPlace),
        ) + pauseChecks(settings, facts)
        // Armed exactly when at least one skip type is armed (covered by ArmedEvaluatorTest).
        return resolve(checks)
    }

    /** OFF is handled by callers; this turns checks into ARMED / BLOCKED / PAUSED. */
    private fun resolve(checks: List<Check>): ArmedStatus {
        val blocker = checks.firstOrNull { !it.met && !it.requirement.pause }
        if (blocker != null) return ArmedStatus(ArmedLevel.BLOCKED, blocker.requirement, checks)
        val pause = checks.firstOrNull { !it.met }
        if (pause != null) return ArmedStatus(ArmedLevel.PAUSED, pause.requirement, checks)
        return ArmedStatus(ArmedLevel.ARMED, null, checks)
    }

    private val FeedCleanSettings.anyAutoSkipPlace: Boolean
        get() = autoSkipInForYou || autoSkipInOtherHomeTabs || autoSkipInSearch || autoSkipInOtherFeeds

    private val FeedCleanSettings.anyDetectionKind: Boolean
        get() = detectPaidAd || detectPromoted || detectShopAnchor || detectLiveShopping
}

/**
 * Card-level Armed / Off / Blocked / Paused for a Home card that groups one or more statuses
 * (the header pill). Per-row pills stay on each behaviour; this only summarizes the card.
 *
 * Rule — worst state among the ones that are switched on:
 *  1. Ignore OFF (a switch that's off doesn't count).
 *  2. If everything is OFF, the card is OFF.
 *  3. Otherwise pick the worst level, in this order: BLOCKED, then PAUSED, then ARMED.
 *  4. When several share that worst level, the first in the given order wins, and that
 *     status's [ArmedStatus.reason] and [ArmedStatus.checks] are the ones the fix sheet shows.
 */
fun summarizeCard(vararg statuses: ArmedStatus): ArmedStatus {
    val enabled = statuses.filter { it.level != ArmedLevel.OFF }
    if (enabled.isEmpty()) return ArmedStatus(ArmedLevel.OFF)
    return enabled.firstOrNull { it.level == ArmedLevel.BLOCKED }
        ?: enabled.firstOrNull { it.level == ArmedLevel.PAUSED }
        ?: enabled.first()
}

/** Border of the Home hero (setup-steps) card. */
enum class HeroOutline {
    /** Destructive red: one or more required Setup steps are not done. */
    SETUP_MISSING,
    /** Accent teal (same as primary buttons): every required Setup step is complete. */
    READY,
    /** Normal glass. Unused for the steps hero; kept so existing card tones stay valid. */
    NORMAL,
}
