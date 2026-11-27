package io.github.dreadnought0.feedclean.data.model

import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason

/** One logged appearance of an account's post. */
data class Sighting(
    val id: Long,
    val accountId: Long,
    val seenAt: Long,
    val videoUrl: String?,
    /** Stable-per-video hash from the detector; used for the 10-minute dedupe. */
    val pageSignature: String,
    val signals: Signals,
    val source: SightingSource,
    val userAction: UserAction,
    /** Whether [userAction] was verified on screen; null when not applicable or unknown. */
    val actionVerified: Boolean?,
    val skipReason: SkipReason?,
    val screenshotPath: String?,
    val sessionId: Long?,
)

/** Persisted by name; never rename entries. */
enum class SightingSource { OVERLAY, SHARE, RECON }

/** What happened to a sighting. Persisted by name; never rename entries. */
enum class UserAction { NONE, BLOCK, SKIP, AUTO_SKIP, NOT_INTERESTED, LOG, FALSE_POSITIVE }
