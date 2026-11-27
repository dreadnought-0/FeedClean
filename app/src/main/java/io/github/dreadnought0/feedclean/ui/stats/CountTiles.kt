package io.github.dreadnought0.feedclean.ui.stats

import androidx.annotation.StringRes
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.DailyCounts
import io.github.dreadnought0.feedclean.data.model.DayStats

/** One counter of [DailyCounts], with its tile label. Order = display order. */
enum class CountKind(@param:StringRes val labelRes: Int) {
    /** Pages seen; only meaningful with session tracking on. */
    SEEN(R.string.screens_count_seen),
    DETECTED(R.string.screens_count_detected),
    ORGANIC_SHOP(R.string.screens_count_organic_shop),
    PAID_ADS(R.string.screens_count_paid_ads),
    PROMOTED(R.string.screens_count_promoted),
    AUTO_SKIPPED(R.string.screens_count_auto_skipped),
    MANUALLY_SKIPPED(R.string.screens_count_manually_skipped),
    NOT_INTERESTED(R.string.screens_count_not_interested),
    BLOCKED(R.string.screens_count_blocked),
    ;

    /** The value of this counter in [counts]. */
    fun valueIn(counts: DailyCounts): Int = when (this) {
        SEEN -> counts.seen
        DETECTED -> counts.detected
        ORGANIC_SHOP -> counts.organicShop
        PAID_ADS -> counts.paidAds
        PROMOTED -> counts.promoted
        AUTO_SKIPPED -> counts.autoSkipped
        MANUALLY_SKIPPED -> counts.manuallySkipped
        NOT_INTERESTED -> counts.notInterested
        BLOCKED -> counts.blocked
    }
}

/** One tile of a count grid. */
data class CountTile(val kind: CountKind, val value: Int)

/**
 * The tiles for [counts], in [CountKind] order. "Seen" comes from opt-in session tracking and is left out
 * when [sessionTrackingEnabled] is false (it would always read 0, which looks like a bug).
 */
fun countTiles(counts: DailyCounts, sessionTrackingEnabled: Boolean): List<CountTile> =
    CountKind.entries
        .filter { it != CountKind.SEEN || sessionTrackingEnabled }
        .map { CountTile(it, it.valueIn(counts)) }

/** Field-by-field sum of every day in [series]. */
fun sumCounts(series: List<DayStats>): DailyCounts = series.fold(DailyCounts()) { sum, day ->
    val c = day.counts
    DailyCounts(
        seen = sum.seen + c.seen,
        detected = sum.detected + c.detected,
        organicShop = sum.organicShop + c.organicShop,
        paidAds = sum.paidAds + c.paidAds,
        promoted = sum.promoted + c.promoted,
        autoSkipped = sum.autoSkipped + c.autoSkipped,
        manuallySkipped = sum.manuallySkipped + c.manuallySkipped,
        notInterested = sum.notInterested + c.notInterested,
        blocked = sum.blocked + c.blocked,
    )
}
