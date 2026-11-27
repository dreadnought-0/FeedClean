package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot.FeedVideo
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * Does the creator on screen match an account already marked BLOCKED? The real-time half of the unblock audit.
 * Display names aren't unique, so a name-only match is only ever "possible".
 */
enum class BlockedMatch {
    NONE,

    /** Same display-name skeleton as a blocked account. */
    POSSIBLE,

    /** Same @handle as a blocked account. */
    STRONG,
}

/**
 * One new feed page after the pipeline ran: what the bubble, the card, and auto-skip work from.
 *
 * @property page the snapshot with [Signals.isShopCreator] enriched from the database.
 * @property accountId the (possibly provisional) account the page was attributed to.
 * @property knownHandle the account's confirmed @handle, if the database has one (never the sound heuristic).
 * @property sightingId the logged sighting, if the page had a detection worth logging.
 */
data class ProcessedPage(
    val page: FeedVideo,
    val accountId: Long,
    val knownHandle: String?,
    val sightingId: Long?,
    val decision: CurationDecision,
    val blockedMatch: BlockedMatch,
    val processedAtMillis: Long,
) {
    val signals: Signals get() = page.signals
    val pageSignature: String get() = page.pageSignature
}

/**
 * A watched page whose later read detected more than its first read ([FeedPagePipeline.refine]).
 *
 * @property page the page with the merged signals, the latest read's nodes, the (new) sighting id, and the new decision.
 * @property newKinds kinds detected for the first time (detection toggles applied); may be empty when only a raw
 *   signal behind an already-detected kind appeared.
 */
data class Refinement(val page: ProcessedPage, val newKinds: List<DetectionRules.Kind>)

/** Detection toggles applied to raw signals. Pure. */
object DetectionRules {
    /** Detected kinds, strongest first, honoring the per-signal detection toggles. */
    enum class Kind { PAID_AD, PROMOTED, SHOP, LIVE_SHOPPING }

    /**
     * Which kinds count as detected for [signals] under [settings]. Empty when detection is off.
     * Raw signals are stored unchanged; this only decides what is flagged, logged, and shown.
     */
    fun detectedKinds(signals: Signals, settings: FeedCleanSettings): List<Kind> {
        if (!settings.detectionEnabled) return emptyList()
        return buildList {
            if (settings.detectPaidAd && signals.isPaidAd) add(Kind.PAID_AD)
            if (settings.detectPromoted && signals.isPromoted) add(Kind.PROMOTED)
            if (settings.detectShopAnchor && (signals.hasShopAnchor || signals.isShopCreator)) add(Kind.SHOP)
            if (settings.detectLiveShopping && signals.isLiveShopping) add(Kind.LIVE_SHOPPING)
        }
    }

    /** True when a page should be logged as a sighting: at least one enabled detection fired. */
    fun shouldRecord(signals: Signals, settings: FeedCleanSettings): Boolean =
        signals.hasAnySignal && detectedKinds(signals, settings).isNotEmpty()

    /**
     * A later read ([newer]) of the SAME page merged into what was already known ([older]): every detection flag only
     * ever grows (TikTok slides labels in late, and one can flicker out for a frame), raw texts prefer the newer read.
     * [Signals.isShopCreator] is DB enrichment, so it survives a newer raw read that can't know it.
     */
    fun merge(older: Signals, newer: Signals): Signals = newer.copy(
        isPaidAd = older.isPaidAd || newer.isPaidAd,
        isPromoted = older.isPromoted || newer.isPromoted,
        hasShopAnchor = older.hasShopAnchor || newer.hasShopAnchor,
        isShopCreator = older.isShopCreator || newer.isShopCreator,
        isLive = older.isLive || newer.isLive,
        isLiveShopping = older.isLiveShopping || newer.isLiveShopping,
        isLiveEvent = older.isLiveEvent || newer.isLiveEvent,
        isLivePreview = older.isLivePreview || newer.isLivePreview,
        disclosureLabel = newer.disclosureLabel ?: older.disclosureLabel,
        anchorText = newer.anchorText ?: older.anchorText,
        ctaText = newer.ctaText ?: older.ctaText,
    )

    /**
     * True when [merged] has a detection signal [older] didn't: paid ad, promoted, Shop anchor, Shop creator, or LIVE
     * shopping newly true. Raw signals (detection toggles are applied later, by the policy and [detectedKinds]).
     */
    fun grew(older: Signals, merged: Signals): Boolean =
        (merged.isPaidAd && !older.isPaidAd) ||
            (merged.isPromoted && !older.isPromoted) ||
            (merged.hasShopAnchor && !older.hasShopAnchor) ||
            (merged.isShopCreator && !older.isShopCreator) ||
            (merged.isLiveShopping && !older.isLiveShopping)
}
