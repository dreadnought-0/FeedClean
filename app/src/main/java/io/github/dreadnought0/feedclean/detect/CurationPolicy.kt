package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.settings.FeedCleanSettings

/**
 * Keep-or-skip decision for one feed page. Pure and table-driven.
 *
 * Rules, in order:
 *  1. Detection off ([FeedCleanSettings.detectionEnabled] false) → [CurationDecision.Keep].
 *  2. Paid ad (label detected and [FeedCleanSettings.detectPaidAd]) → `Skip(PAID_AD)` if
 *     [FeedCleanSettings.autoSkipPaidAds], else Keep. Nothing else is considered.
 *  3. Promoted (label detected and [FeedCleanSettings.detectPromoted]) → `Skip(PROMOTED)` if
 *     [FeedCleanSettings.autoSkipPromoted], else Keep.
 *  4. LIVE shopping (and [FeedCleanSettings.detectLiveShopping]) with [FeedCleanSettings.autoSkipLiveShopping]
 *     → `Skip(LIVE_SHOPPING)`.
 *  5. Organic Shop with [FeedCleanSettings.autoSkipOrganicShop] → `Skip(ORGANIC_SHOP)`. Shop signals (anchor and
 *     DB-known Shop creator) count only if [FeedCleanSettings.detectShopAnchor]. Never for a LIVE preview.
 *  6. Otherwise Keep.
 *
 * The disclosure label is authoritative about what a post IS: a post labelled as a paid ad or promoted is never
 * organic Shop, even with a Shop anchor, and even when paid-ad/promoted detection is switched off (the toggles
 * only control whether those labels can trigger their own skip target).
 *
 * An in-feed LIVE preview ([Signals.isLivePreview]) is never organic Shop either, even when the pipeline knows the
 * streamer as a Shop creator: selling on a LIVE preview is LIVE shopping, which only the LIVE-shopping target
 * (default off) skips. A plain LIVE preview is kept.
 *
 * This answers "would this page be skipped?". It deliberately does NOT check whether auto-skip may run right
 * now: callers must also require [FeedCleanSettings.autoSkipArmed] (master "Curate feed" on, kill-switch off),
 * TikTok in the foreground, auto-skip allowed in the page's [FeedContext][io.github.dreadnought0.feedclean.detect.FeedContext]
 * (`FeedCleanSettings.autoSkipAllowedIn`), and the loop guard. A Skip decision alone never moves the feed.
 */
object CurationPolicy {

    fun decide(signals: Signals, settings: FeedCleanSettings): CurationDecision {
        if (!settings.detectionEnabled) return CurationDecision.Keep

        val paidAd = settings.detectPaidAd && signals.isPaidAd
        val promoted = settings.detectPromoted && signals.isPromoted
        val liveShopping = settings.detectLiveShopping && signals.isLiveShopping
        val shop = settings.detectShopAnchor && (signals.hasShopAnchor || signals.isShopCreator)
        val disclosed = signals.isPaidAd || signals.isPromoted

        return when {
            paidAd -> skipIf(settings.autoSkipPaidAds, SkipReason.PAID_AD)
            promoted -> skipIf(settings.autoSkipPromoted, SkipReason.PROMOTED)
            liveShopping && settings.autoSkipLiveShopping -> CurationDecision.Skip(SkipReason.LIVE_SHOPPING)
            shop && !disclosed && !signals.isLivePreview && settings.autoSkipOrganicShop ->
                CurationDecision.Skip(SkipReason.ORGANIC_SHOP)
            else -> CurationDecision.Keep
        }
    }

    private fun skipIf(enabled: Boolean, reason: SkipReason): CurationDecision =
        if (enabled) CurationDecision.Skip(reason) else CurationDecision.Keep
}
