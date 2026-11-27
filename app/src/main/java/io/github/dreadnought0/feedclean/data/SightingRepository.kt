package io.github.dreadnought0.feedclean.data

import io.github.dreadnought0.feedclean.data.model.Sighting
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.Flow

/** Sightings log. */
interface SightingRepository {
    /**
     * Logs a sighting of [accountId]'s page and marks the account seen at [nowMillis].
     *
     * Dedupe: if a sighting with the same [pageSignature] exists within [DEDUPE_WINDOW_MILLIS] before
     * [nowMillis], no row is added and its id is returned with [RecordResult.deduplicated] = true. If that row
     * has no action yet and [userAction] is not NONE, its action (and [skipReason]) are filled in.
     *
     * A new sighting also puts the signal tags implied by [signals] on the account
     * ([io.github.dreadnought0.feedclean.data.model.TagNames.forSignals], source AUTO_DETECTED).
     */
    suspend fun recordSighting(
        accountId: Long,
        pageSignature: String,
        signals: Signals,
        source: SightingSource,
        userAction: UserAction = UserAction.NONE,
        skipReason: SkipReason? = null,
        videoUrl: String? = null,
        nowMillis: Long,
        sessionId: Long? = null,
        screenshotPath: String? = null,
    ): RecordResult

    /** Sets the action taken on a sighting and whether it was verified on screen (null = not applicable). */
    suspend fun setUserAction(sightingId: Long, action: UserAction, verified: Boolean?)

    /**
     * Replaces a sighting's [signals] (a label TikTok slid in after the first read of the page, so the detection
     * grew), and puts any newly implied signal tags on its account
     * ([io.github.dreadnought0.feedclean.data.model.TagNames.forSignals], source AUTO_DETECTED). The time, action, and
     * everything else stay as they were. No-op for an unknown id.
     */
    suspend fun updateSignals(sightingId: Long, signals: Signals, nowMillis: Long)

    /** Most recent sightings across all accounts, newest first. */
    fun observeRecent(limit: Int): Flow<List<Sighting>>

    suspend fun latestForAccount(accountId: Long): Sighting?

    suspend fun getSighting(id: Long): Sighting?

    companion object {
        /** The same page signature within this window is the same sighting. */
        const val DEDUPE_WINDOW_MILLIS: Long = 10 * 60 * 1000L
    }
}

/** Result of [SightingRepository.recordSighting]. */
data class RecordResult(
    val id: Long,
    /** True when an existing sighting within the dedupe window was reused. */
    val deduplicated: Boolean,
)
