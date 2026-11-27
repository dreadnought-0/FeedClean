package io.github.dreadnought0.feedclean.share

import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.detect.Signals

/**
 * The tag chips of the share sheet. Pure.
 *
 * Two kinds of selection:
 * - **detected**: tags implied by the screen's [Signals] ([TagNames.forSignals]). They are recorded with the
 *   sighting (source AUTO_DETECTED). Deselecting one clears the matching signal, so the sighting never claims
 *   a label the user rejected.
 * - **manual**: everything else the user picks. Saved with source MANUAL.
 */
data class ShareTagSelection(
    val signals: Signals = Signals(),
    val manualTags: Set<String> = emptySet(),
) {
    /** Tags implied by [signals]. */
    val detectedTags: Set<String>
        get() = TagNames.forSignals(signals).toSet()

    /** Every selected tag (detected or manual). */
    val selectedTags: Set<String>
        get() = detectedTags + manualTags

    /** Toggles [tag]: a detected tag is cleared from [signals]; any other tag toggles in [manualTags]. */
    fun toggle(tag: String): ShareTagSelection = when (tag) {
        in detectedTags -> copy(signals = withoutSignal(signals, tag))
        in manualTags -> copy(manualTags = manualTags - tag)
        else -> copy(manualTags = manualTags + tag)
    }

    companion object {
        /** Chips for on-screen-label tags, in display order. */
        val SIGNAL_TAGS: List<String> = listOf(
            TagNames.PAID_AD,
            TagNames.PROMOTED,
            TagNames.SHOP_ANCHOR,
            TagNames.ORGANIC_SHOP,
            TagNames.LIVE_SHOPPING,
        )

        /** Chips for the user's own judgement, in display order. */
        val MANUAL_TAGS: List<String> = listOf(
            TagNames.AI_GENERATED,
            TagNames.SEXUAL_CONTENT,
            TagNames.IMPERSONATOR,
            TagNames.REPOSTED,
            TagNames.BOT_LIKE,
        )

        /**
         * [signals] with the signal behind [tag] cleared. Organic Shop is derived from the Shop signals, so
         * clearing it clears the Shop anchor and Shop-creator signals too.
         */
        fun withoutSignal(signals: Signals, tag: String): Signals = when (tag) {
            TagNames.PAID_AD -> signals.copy(isPaidAd = false)
            TagNames.PROMOTED -> signals.copy(isPromoted = false)
            TagNames.SHOP_ANCHOR -> signals.copy(hasShopAnchor = false)
            TagNames.ORGANIC_SHOP -> signals.copy(hasShopAnchor = false, isShopCreator = false)
            TagNames.LIVE_SHOPPING -> signals.copy(isLiveShopping = false)
            else -> signals
        }
    }
}
