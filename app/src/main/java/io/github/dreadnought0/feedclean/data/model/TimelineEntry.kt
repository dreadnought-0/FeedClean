package io.github.dreadnought0.feedclean.data.model

/** One row of an account's timeline: a sighting (incl. skips / Not-interested) or a block event. */
sealed interface TimelineEntry {
    /** When it happened (epoch millis). Timelines are ordered by this, newest first. */
    val atMillis: Long

    data class SightingEntry(val sighting: Sighting) : TimelineEntry {
        override val atMillis: Long
            get() = sighting.seenAt
    }

    data class BlockEntry(val event: BlockEvent) : TimelineEntry {
        override val atMillis: Long
            get() = event.attemptedAt
    }
}
