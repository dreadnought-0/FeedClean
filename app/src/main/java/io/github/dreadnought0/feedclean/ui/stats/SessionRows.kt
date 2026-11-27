package io.github.dreadnought0.feedclean.ui.stats

import io.github.dreadnought0.feedclean.data.model.SessionRatio
import kotlin.math.roundToInt

/** How many recent sessions the Stats screen lists. */
const val SESSION_LIMIT: Int = 10

/** One share of a session, as a meter fraction and a rounded percentage. */
data class Share(val fraction: Float, val percent: Int)

/** One session in the Stats list (opt-in session tracking). */
data class SessionRowModel(
    val id: Long,
    val startedAt: Long,
    /** Null while the session is still open. */
    val endedAt: Long?,
    val videosSeen: Int,
    val shop: Share,
    val ads: Share,
    val promoted: Share,
    val skipped: Int,
    val skippedShare: Share,
) {
    val isOpen: Boolean get() = endedAt == null

    /** Whole minutes between start and end; null while open. Never negative. */
    val durationMinutes: Long?
        get() = endedAt?.let { ((it - startedAt).coerceAtLeast(0L)) / 60_000L }
}

/** A 0.0..1.0 ratio as a [Share]; out-of-range or NaN input is clamped. */
fun shareOf(ratio: Double): Share {
    val clamped = if (ratio.isNaN()) 0.0 else ratio.coerceIn(0.0, 1.0)
    return Share(fraction = clamped.toFloat(), percent = (clamped * 100).roundToInt())
}

/** Maps a repository ratio to its list row. */
fun SessionRatio.toRowModel(): SessionRowModel = SessionRowModel(
    id = session.id,
    startedAt = session.startedAt,
    endedAt = session.endedAt,
    videosSeen = session.videosSeen,
    shop = shareOf(shopRatio),
    ads = shareOf(adRatio),
    promoted = shareOf(promotedRatio),
    skipped = session.skipped,
    skippedShare = shareOf(skippedRatio),
)
