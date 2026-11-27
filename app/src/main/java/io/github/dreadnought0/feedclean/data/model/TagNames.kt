package io.github.dreadnought0.feedclean.data.model

import io.github.dreadnought0.feedclean.detect.Signals

/** Names of the default tags seeded into a new database, and tag-name helpers. Never rename these. */
object TagNames {
    // Signal tags (objective, derived from on-screen labels).
    const val PAID_AD = "paid_ad"
    const val PROMOTED = "promoted"
    const val SHOP_ANCHOR = "shop_anchor"
    const val ORGANIC_SHOP = "organic_shop"
    const val LIVE_SHOPPING = "live_shopping"

    // Manual tags (subjective, set by the user).
    const val AI_GENERATED = "ai_generated"
    const val SEXUAL_CONTENT = "sexual_content"
    const val IMPERSONATOR = "impersonator"
    const val REPOSTED = "reposted"
    const val BOT_LIKE = "bot_like"

    /** Default tags seeded on database creation, with their kinds. */
    val DEFAULTS: List<Pair<String, TagKind>> = listOf(
        PAID_AD to TagKind.SIGNAL,
        PROMOTED to TagKind.SIGNAL,
        SHOP_ANCHOR to TagKind.SIGNAL,
        ORGANIC_SHOP to TagKind.SIGNAL,
        LIVE_SHOPPING to TagKind.SIGNAL,
        AI_GENERATED to TagKind.MANUAL,
        SEXUAL_CONTENT to TagKind.MANUAL,
        IMPERSONATOR to TagKind.MANUAL,
        REPOSTED to TagKind.MANUAL,
        BOT_LIKE to TagKind.MANUAL,
    )

    /** Tags allowed in the public export by default: on-screen-label tags only. */
    val PUBLIC_DEFAULT: Set<String> = setOf(PAID_AD, PROMOTED, SHOP_ANCHOR, ORGANIC_SHOP, LIVE_SHOPPING)

    private val whitespace = Regex("\\s+")

    /** Normalizes a user-typed tag name: trimmed, lowercase, inner whitespace collapsed to "_". */
    fun normalize(raw: String): String = raw.trim().lowercase().replace(whitespace, "_")

    /** The signal tags implied by [signals], applied automatically when a sighting is recorded. */
    fun forSignals(signals: Signals): List<String> = buildList {
        if (signals.isPaidAd) add(PAID_AD)
        if (signals.isPromoted) add(PROMOTED)
        if (signals.hasShopAnchor) add(SHOP_ANCHOR)
        if (signals.isOrganicShop) add(ORGANIC_SHOP)
        if (signals.isLiveShopping) add(LIVE_SHOPPING)
    }
}
