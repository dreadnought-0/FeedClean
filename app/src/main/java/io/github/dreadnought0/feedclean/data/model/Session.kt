package io.github.dreadnought0.feedclean.data.model

/** One opt-in tracking session: counts of every page seen, not only detections. */
data class Session(
    val id: Long,
    val startedAt: Long,
    /** Null while the session is still open. */
    val endedAt: Long?,
    val videosSeen: Int,
    /** Pages with the paid-ad label. */
    val ads: Int,
    /** Pages with a promoted/paid-partnership label. */
    val promoted: Int,
    /** Organic Shop pages (Shop anchor or Shop creator, no paid/promoted label). */
    val shop: Int,
    /** LIVE pages (creator live or live shopping). */
    val live: Int,
    /** Pages auto-skipped or skipped by hand. */
    val skipped: Int,
)

/** Shares of a session's pages (0.0..1.0; all 0.0 when nothing was seen). */
data class SessionRatio(
    val session: Session,
    val adRatio: Double,
    val promotedRatio: Double,
    val shopRatio: Double,
    val liveRatio: Double,
    val skippedRatio: Double,
) {
    companion object {
        /** Computes the ratios for [session]. */
        fun of(session: Session): SessionRatio {
            val seen = session.videosSeen
            fun share(count: Int): Double = if (seen <= 0) 0.0 else count.toDouble() / seen
            return SessionRatio(
                session = session,
                adRatio = share(session.ads),
                promotedRatio = share(session.promoted),
                shopRatio = share(session.shop),
                liveRatio = share(session.live),
                skippedRatio = share(session.skipped),
            )
        }
    }
}
