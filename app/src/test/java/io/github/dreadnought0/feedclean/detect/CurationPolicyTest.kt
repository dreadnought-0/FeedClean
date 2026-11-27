package io.github.dreadnought0.feedclean.detect

import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurationPolicyTest {

    private val defaults = FeedCleanSettings()
    private val keep = CurationDecision.Keep
    private fun skip(reason: SkipReason) = CurationDecision.Skip(reason)

    private val normal = Signals()
    private val paidAd = Signals(isPaidAd = true, disclosureLabel = "Ad", ctaText = "Shop now")
    private val paidAdWithShop = paidAd.copy(hasShopAnchor = true)
    private val promoted = Signals(isPromoted = true, disclosureLabel = "Promotional content")
    private val promotedWithShop = promoted.copy(hasShopAnchor = true)
    private val shopAnchor = Signals(hasShopAnchor = true)
    private val shopCreator = Signals(isShopCreator = true)
    private val liveShopping = Signals(isLiveShopping = true, isLive = true)
    private val liveShoppingWithShop = liveShopping.copy(hasShopAnchor = true)
    private val liveOnly = Signals(isLive = true, isLiveEvent = true)
    private val livePreview = Signals(isLive = true, isLivePreview = true)
    private val liveShoppingPreview = livePreview.copy(isLiveShopping = true, anchorText = "LIVE auction")
    private val allTargets = defaults.copy(
        autoSkipOrganicShop = true, autoSkipPaidAds = true, autoSkipPromoted = true, autoSkipLiveShopping = true,
    )

    private data class Row(val name: String, val signals: Signals, val settings: FeedCleanSettings, val expected: CurationDecision)

    private val table = listOf(
        Row("normal post is kept", normal, defaults, keep),
        Row("live / LIVE Event alone is kept", liveOnly, defaults, keep),
        Row("organic Shop anchor is skipped by default", shopAnchor, defaults, skip(SkipReason.ORGANIC_SHOP)),
        Row("known Shop creator is organic Shop", shopCreator, defaults, skip(SkipReason.ORGANIC_SHOP)),
        Row("organic Shop kept when its target is off", shopAnchor, defaults.copy(autoSkipOrganicShop = false), keep),
        Row("Shop ignored when Shop detection is off", shopAnchor, defaults.copy(detectShopAnchor = false), keep),
        Row("Shop creator ignored when Shop detection is off", shopCreator, defaults.copy(detectShopAnchor = false), keep),
        Row("paid ad is kept by default", paidAd, defaults, keep),
        Row("paid ad with shop anchor is kept by default", paidAdWithShop, defaults, keep),
        Row("paid ad skipped when its target is on", paidAd, defaults.copy(autoSkipPaidAds = true), skip(SkipReason.PAID_AD)),
        Row(
            "paid ad with shop anchor + paid target is PAID_AD, never ORGANIC_SHOP",
            paidAdWithShop, defaults.copy(autoSkipPaidAds = true), skip(SkipReason.PAID_AD),
        ),
        Row(
            "paid-ad detection off: ad not skipped as ad",
            paidAd, defaults.copy(detectPaidAd = false, autoSkipPaidAds = true), keep,
        ),
        Row(
            "paid-ad detection off: ad with shop anchor is still never organic Shop",
            paidAdWithShop, defaults.copy(detectPaidAd = false), keep,
        ),
        Row("promoted is kept by default", promoted, defaults, keep),
        Row("promoted with shop anchor is kept by default", promotedWithShop, defaults, keep),
        Row("promoted skipped when its target is on", promoted, defaults.copy(autoSkipPromoted = true), skip(SkipReason.PROMOTED)),
        Row(
            "promoted detection off: not skipped as promoted",
            promoted, defaults.copy(detectPromoted = false, autoSkipPromoted = true), keep,
        ),
        Row(
            "promoted detection off: promoted with shop anchor is never organic Shop",
            promotedWithShop, defaults.copy(detectPromoted = false), keep,
        ),
        Row("LIVE shopping kept by default", liveShopping, defaults, keep),
        Row(
            "LIVE shopping skipped when its target is on",
            liveShopping, defaults.copy(autoSkipLiveShopping = true), skip(SkipReason.LIVE_SHOPPING),
        ),
        Row(
            "LIVE shopping detection off: not skipped",
            liveShopping, defaults.copy(detectLiveShopping = false, autoSkipLiveShopping = true), keep,
        ),
        Row(
            "LIVE shopping takes precedence over organic Shop",
            liveShoppingWithShop, defaults.copy(autoSkipLiveShopping = true), skip(SkipReason.LIVE_SHOPPING),
        ),
        Row(
            "LIVE shopping with anchor falls back to organic Shop when its own target is off",
            liveShoppingWithShop, defaults, skip(SkipReason.ORGANIC_SHOP),
        ),
        Row(
            "paid ad that is also LIVE shopping follows the paid-ad target",
            paidAd.copy(isLiveShopping = true), defaults.copy(autoSkipLiveShopping = true), keep,
        ),
        Row("detection off keeps organic Shop", shopAnchor, defaults.copy(detectionEnabled = false), keep),
        Row(
            "detection off keeps everything even with every target on",
            paidAdWithShop.copy(isLiveShopping = true),
            defaults.copy(detectionEnabled = false, autoSkipPaidAds = true, autoSkipPromoted = true, autoSkipLiveShopping = true),
            keep,
        ),
        Row(
            "policy does not check curateFeedEnabled (callers check autoSkipArmed)",
            shopAnchor, defaults.copy(curateFeedEnabled = false), skip(SkipReason.ORGANIC_SHOP),
        ),
        Row(
            "policy does not check the kill-switch (callers check autoSkipArmed)",
            shopAnchor, defaults.copy(curateFeedEnabled = true, killSwitchEngaged = true), skip(SkipReason.ORGANIC_SHOP),
        ),
        Row("plain LIVE preview is kept by default", livePreview, defaults, keep),
        Row("plain LIVE preview is kept even with every target on", livePreview, allTargets, keep),
        Row("LIVE-shopping preview is kept by default", liveShoppingPreview, defaults, keep),
        Row(
            "LIVE-shopping preview is skipped by its own target",
            liveShoppingPreview, defaults.copy(autoSkipLiveShopping = true), skip(SkipReason.LIVE_SHOPPING),
        ),
        Row(
            "LIVE preview of a DB-known Shop creator is never organic Shop",
            livePreview.copy(isShopCreator = true), allTargets, keep,
        ),
        Row(
            "LIVE-shopping preview of a DB-known Shop creator is LIVE_SHOPPING only with its target",
            liveShoppingPreview.copy(isShopCreator = true), defaults, keep,
        ),
    )

    @Test
    fun decisionTable() {
        for (row in table) assertEquals(row.name, row.expected, CurationPolicy.decide(row.signals, row.settings))
    }

    /** Every combination of the 5 signal flags × 9 relevant settings flags (16 384 cases) obeys the invariants. */
    @Test
    fun exhaustiveInvariants() {
        var cases = 0
        for (sigBits in 0 until (1 shl 5)) {
            val s = Signals(
                isPaidAd = sigBits.bit(0),
                isPromoted = sigBits.bit(1),
                hasShopAnchor = sigBits.bit(2),
                isShopCreator = sigBits.bit(3),
                isLiveShopping = sigBits.bit(4),
            )
            for (setBits in 0 until (1 shl 9)) {
                val st = FeedCleanSettings(
                    detectionEnabled = setBits.bit(0),
                    detectPaidAd = setBits.bit(1),
                    detectPromoted = setBits.bit(2),
                    detectShopAnchor = setBits.bit(3),
                    detectLiveShopping = setBits.bit(4),
                    autoSkipOrganicShop = setBits.bit(5),
                    autoSkipPaidAds = setBits.bit(6),
                    autoSkipPromoted = setBits.bit(7),
                    autoSkipLiveShopping = setBits.bit(8),
                )
                val d = CurationPolicy.decide(s, st)
                val ctx = "signals=$s settings=$st -> $d"
                cases++
                if (!st.detectionEnabled) assertEquals(ctx, keep, d)
                if (!st.autoSkipOrganicShop && !st.autoSkipPaidAds && !st.autoSkipPromoted && !st.autoSkipLiveShopping) {
                    assertEquals(ctx, keep, d)
                }
                when ((d as? CurationDecision.Skip)?.reason) {
                    SkipReason.ORGANIC_SHOP -> {
                        assertTrue(ctx, !s.isPaidAd && !s.isPromoted)
                        assertTrue(ctx, s.hasShopAnchor || s.isShopCreator)
                        assertTrue(ctx, st.detectShopAnchor && st.autoSkipOrganicShop)
                    }
                    SkipReason.PAID_AD -> assertTrue(ctx, s.isPaidAd && st.detectPaidAd && st.autoSkipPaidAds)
                    SkipReason.PROMOTED -> {
                        assertTrue(ctx, s.isPromoted && st.detectPromoted && st.autoSkipPromoted)
                        assertTrue(ctx, !(s.isPaidAd && st.detectPaidAd))
                    }
                    SkipReason.LIVE_SHOPPING -> {
                        assertTrue(ctx, s.isLiveShopping && st.detectLiveShopping && st.autoSkipLiveShopping)
                        assertTrue(ctx, !(s.isPaidAd && st.detectPaidAd) && !(s.isPromoted && st.detectPromoted))
                    }
                    null -> Unit
                }
                // A detected paid ad / promotion is decided by its own target alone.
                if (st.detectionEnabled && s.isPaidAd && st.detectPaidAd) {
                    assertEquals(ctx, if (st.autoSkipPaidAds) skip(SkipReason.PAID_AD) else keep, d)
                }
            }
        }
        assertEquals(16_384, cases)
    }

    /** A LIVE preview is never organic Shop, whatever else its signals say, under every settings combination. */
    @Test
    fun livePreview_isNeverOrganicShop() {
        for (sigBits in 0 until (1 shl 5)) {
            val s = Signals(
                isPaidAd = sigBits.bit(0),
                isPromoted = sigBits.bit(1),
                hasShopAnchor = sigBits.bit(2),
                isShopCreator = sigBits.bit(3),
                isLiveShopping = sigBits.bit(4),
                isLive = true,
                isLivePreview = true,
            )
            assertTrue("$s", !s.isOrganicShop)
            for (setBits in 0 until (1 shl 9)) {
                val st = FeedCleanSettings(
                    detectionEnabled = setBits.bit(0),
                    detectPaidAd = setBits.bit(1),
                    detectPromoted = setBits.bit(2),
                    detectShopAnchor = setBits.bit(3),
                    detectLiveShopping = setBits.bit(4),
                    autoSkipOrganicShop = setBits.bit(5),
                    autoSkipPaidAds = setBits.bit(6),
                    autoSkipPromoted = setBits.bit(7),
                    autoSkipLiveShopping = setBits.bit(8),
                )
                val d = CurationPolicy.decide(s, st)
                assertTrue("signals=$s settings=$st -> $d", d != skip(SkipReason.ORGANIC_SHOP))
            }
        }
    }

    private fun Int.bit(i: Int) = (this shr i) and 1 == 1
}
