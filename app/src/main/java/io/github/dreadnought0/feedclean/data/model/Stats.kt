package io.github.dreadnought0.feedclean.data.model

import java.time.LocalDate

/**
 * Counts for one local day (Home dashboard, Stats).
 *
 * Sighting-based counts use deduplicated sightings, so a video seen twice within 10 minutes counts once.
 */
data class DailyCounts(
    /** Pages seen, from opt-in session tracking (sessions started that day). 0 when tracking is off. */
    val seen: Int = 0,
    /** Sightings with any detection signal. */
    val detected: Int = 0,
    val organicShop: Int = 0,
    val paidAds: Int = 0,
    val promoted: Int = 0,
    /** Sightings with user_action = AUTO_SKIP. */
    val autoSkipped: Int = 0,
    /** Sightings with user_action = SKIP (one-shot manual skip from the card). */
    val manuallySkipped: Int = 0,
    /** Sightings with user_action = NOT_INTERESTED that were not marked as failed (verified != false). */
    val notInterested: Int = 0,
    /** Verified block events, excluding imported ones. */
    val blocked: Int = 0,
)

/** [counts] for one local [date]; series are ordered oldest first and include days with no activity. */
data class DayStats(
    val date: LocalDate,
    val counts: DailyCounts,
)
