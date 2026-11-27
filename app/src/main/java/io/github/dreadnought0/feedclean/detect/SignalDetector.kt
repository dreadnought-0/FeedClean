package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.selectors.SelectorKeys
import io.github.dreadnought0.feedclean.selectors.SelectorMatch
import io.github.dreadnought0.feedclean.selectors.SelectorMatcher
import java.security.MessageDigest

/**
 * How sure the detector is that a page is a paid ad. The disclosure label is authoritative: corroborating
 * evidence alone ([LOW]) never sets [Signals.isPaidAd].
 */
enum class AdConfidence {
    /** No label, no corroborating evidence. */
    NONE,

    /** Corroborating evidence (promoted-music sound, CTA) without a paid-ad label. Not flagged. */
    LOW,

    /** Paid-ad label only. */
    MEDIUM,

    /** Paid-ad label plus at least one corroborating signal. */
    HIGH,
}

/**
 * Result of [SignalDetector.detect].
 *
 * @property corroborating selector keys of the corroborating evidence found (e.g. `feed.cta`).
 * @property matchedStrategies selector key → matched strategy description, for the Debug screen.
 */
data class SignalReport(
    val signals: Signals,
    val adConfidence: AdConfidence,
    val corroborating: List<String>,
    val matchedStrategies: Map<String, String>,
)

/**
 * Derives [Signals] from the nodes of ONE visible feed page:
 *  - paid ad / promoted from the disclosure label (`feed.disclosureLabel` ∈ `feed.paidAdLabels` /
 *    `feed.promotedLabels`);
 *  - Shop anchor (`feed.shopAnchor`), LIVE badge, LIVE Event anchor, LIVE shopping (best effort);
 *  - CTA text and the promoted-music sound as corroborating paid-ad evidence.
 *
 * [Signals.isShopCreator] is never set here: on the feed it comes from the database (account category).
 * Detection toggles are NOT applied here; `CurationPolicy` applies them, so logged signals stay raw.
 *
 * In-feed LIVE previews go through [detectLivePreview] instead.
 */
class SignalDetector(private val matcher: SelectorMatcher) {

    /**
     * Signals of ONE visible in-feed LIVE preview page ([pageNodes] restricted as for [detect]):
     *  - [Signals.isLive] and [Signals.isLivePreview] always;
     *  - LIVE shopping when the page shows commerce text (`feed.liveShoppingText`: auction, price, shipping, …),
     *    a "LIVE shopping" label (`feed.liveShopping`), or a product anchor (`feed.shopAnchor`);
     *    [Signals.anchorText] is the first such text;
     *  - paid ad / promoted from the disclosure label, exactly as for videos.
     *
     * Never [Signals.hasShopAnchor] (so never organic Shop), never a CTA or LIVE Event: commerce on a LIVE preview
     * is LIVE shopping, which has its own skip target. Only the page is searched, never the top tabs (the "Shop"
     * tab is on every screen).
     */
    fun detectLivePreview(pageNodes: List<UiNode>): SignalReport {
        val matched = linkedMapOf<String, String>()
        fun find(key: String): SelectorMatch? = matcher.first(key, pageNodes)?.also { matched[key] = it.description }

        val label = find(SelectorKeys.FEED_DISCLOSURE_LABEL)?.value
        val isPaidAd = matcher.isLabel(SelectorKeys.FEED_PAID_AD_LABELS, label)
        val isPromoted = !isPaidAd && matcher.isLabel(SelectorKeys.FEED_PROMOTED_LABELS, label)

        val commerce = find(SelectorKeys.FEED_LIVE_SHOPPING_TEXT)
        val liveShoppingLabel = find(SelectorKeys.FEED_LIVE_SHOPPING)
        val productAnchor = find(SelectorKeys.FEED_SHOP_ANCHOR)

        val signals = Signals(
            isPaidAd = isPaidAd,
            isPromoted = isPromoted,
            isLive = true,
            isLiveShopping = commerce != null || liveShoppingLabel != null || productAnchor != null,
            disclosureLabel = label,
            anchorText = commerce?.value
                ?: liveShoppingLabel?.value
                ?: productAnchor?.takeIf { NameNormalizer.clean(it.node.text) != null }?.value,
            isLivePreview = true,
        )
        val adConfidence = if (isPaidAd) AdConfidence.MEDIUM else AdConfidence.NONE
        return SignalReport(signals, adConfidence, corroborating = emptyList(), matchedStrategies = matched)
    }

    /** [pageNodes] must already be restricted to shown nodes inside the visible page. */
    fun detect(pageNodes: List<UiNode>): SignalReport {
        val matched = linkedMapOf<String, String>()
        fun find(key: String): SelectorMatch? = matcher.first(key, pageNodes)?.also { matched[key] = it.description }

        val label = find(SelectorKeys.FEED_DISCLOSURE_LABEL)?.value
        val isPaidAd = matcher.isLabel(SelectorKeys.FEED_PAID_AD_LABELS, label)
        val isPromoted = !isPaidAd && matcher.isLabel(SelectorKeys.FEED_PROMOTED_LABELS, label)

        val promotedMusic = find(SelectorKeys.FEED_PROMOTED_MUSIC)
        val cta = find(SelectorKeys.FEED_CTA)
        val corroborating = listOfNotNull(
            promotedMusic?.let { SelectorKeys.FEED_PROMOTED_MUSIC },
            cta?.let { SelectorKeys.FEED_CTA },
        )
        val adConfidence = when {
            isPaidAd && corroborating.isNotEmpty() -> AdConfidence.HIGH
            isPaidAd -> AdConfidence.MEDIUM
            corroborating.isNotEmpty() -> AdConfidence.LOW
            else -> AdConfidence.NONE
        }

        val anchor = find(SelectorKeys.FEED_ANCHOR)
        val shopAnchor = find(SelectorKeys.FEED_SHOP_ANCHOR)
        val liveEvent = find(SelectorKeys.FEED_LIVE_EVENT_ANCHOR)
        val liveBadge = find(SelectorKeys.FEED_LIVE_BADGE)
        val liveShopping = find(SelectorKeys.FEED_LIVE_SHOPPING)

        val anchorText = anchor?.let { anchorTextInside(it.node, pageNodes) }
            ?: liveEvent?.value
            ?: shopAnchor?.takeIf { NameNormalizer.clean(it.node.text) != null }?.value

        val signals = Signals(
            isPaidAd = isPaidAd,
            isPromoted = isPromoted,
            hasShopAnchor = shopAnchor != null,
            isShopCreator = false,
            isLive = liveBadge != null,
            isLiveShopping = liveShopping != null,
            isLiveEvent = liveEvent != null,
            disclosureLabel = label,
            anchorText = anchorText,
            ctaText = cta?.value,
        )
        return SignalReport(signals, adConfidence, corroborating, matched)
    }

    /** Non-blank texts inside the anchor container, joined with " · ". Pure-punctuation fragments are skipped. */
    private fun anchorTextInside(anchor: UiNode, pageNodes: List<UiNode>): String? =
        pageNodes.asSequence()
            .filter { it !== anchor && anchor.bounds.contains(it.bounds) }
            .mapNotNull { NameNormalizer.clean(it.text) }
            .filter { text -> text.any { it.isLetterOrDigit() } }
            .distinct()
            .joinToString(" · ")
            .ifEmpty { null }

    companion object {
        /** Caption characters that feed the page signature; long enough to tell posts apart. */
        private const val CAPTION_KEY_LENGTH = 40

        /**
         * Stable per-video signature: SHA-1 hex of `displayName|caption|sound`, each [NameNormalizer.normalize]d.
         *
         * The caption contributes only its first [CAPTION_KEY_LENGTH] characters before any "…", so expanding a
         * truncated caption ("…more") doesn't look like a new page.
         */
        fun pageSignature(displayName: String?, caption: String?, sound: String?): String {
            val captionKey = caption?.let { NameNormalizer.normalize(it).substringBefore('…') }
                ?.let { it.take(it.offsetByCodePointsSafe(CAPTION_KEY_LENGTH)) }
                .orEmpty()
            val input = listOf(
                displayName?.let(NameNormalizer::normalize).orEmpty(),
                captionKey.trim(),
                sound?.let(NameNormalizer::normalize).orEmpty(),
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Stable per-stream signature of an in-feed LIVE preview: [pageSignature] of the streamer's [nickname], the
         * stream [title] if one is ever read (none is on screen in 47.7.3), and a fixed LIVE marker in the sound
         * slot, so a preview never collides with a video by the same creator (real sound credits start with
         * "Sound: "). Chat, gift, and auction text change every frame and must never be passed in here.
         */
        fun livePageSignature(nickname: String?, title: String? = null): String =
            pageSignature(nickname, title, LIVE_PREVIEW_MARKER)

        /** FeedClean's own marker (not a TikTok string) for [livePageSignature]. */
        private const val LIVE_PREVIEW_MARKER = "feedclean:live-preview"

        private fun String.offsetByCodePointsSafe(codePoints: Int): Int =
            if (codePointCount(0, length) <= codePoints) length else offsetByCodePoints(0, codePoints)
    }
}
