package io.github.dreadnought0.feedclean.share

import io.github.dreadnought0.feedclean.data.model.Handles
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/** Pure rules for what a share may take from the screen. */
object ShareContext {
    /**
     * A feed snapshot older than this is not attached to a share. Generous because the candidate is usually
     * `ScreenStateHolder.lastFeedVideo`, frozen when TikTok's share sheet opened; finding FeedClean in that
     * sheet can take a while. A newer feed page (the user scrolled on) always wins over an older one.
     */
    const val MAX_AGE_MILLIS: Long = 30_000

    /**
     * The newest [ScreenSnapshot.FeedVideo] among [candidates] captured less than [MAX_AGE_MILLIS] before
     * [nowMillis], or null. Snapshots from the future (clock changes) are ignored.
     */
    fun pick(candidates: List<ScreenSnapshot?>, nowMillis: Long): ScreenSnapshot.FeedVideo? =
        candidates.asSequence()
            .filterIsInstance<ScreenSnapshot.FeedVideo>()
            .filter { (nowMillis - it.capturedAtMillis) in 0 until MAX_AGE_MILLIS }
            .maxByOrNull { it.capturedAtMillis }

    /**
     * True when the screen context cannot belong to the shared post: its probable handle (a heuristic) differs
     * from the [confirmedHandle] read from the link. Without either, nothing contradicts it.
     */
    fun contradicts(context: ScreenSnapshot.FeedVideo?, confirmedHandle: String?): Boolean {
        val probable = Handles.normalize(context?.probableHandle) ?: return false
        val confirmed = Handles.normalize(confirmedHandle) ?: return false
        return probable != confirmed
    }

    /**
     * Stable page signature for a shared link when there is no screen context: `share:` + the first 32 hex
     * chars of SHA-256 over the link without its query or fragment (share links carry per-share tracking
     * parameters) and with a lowercased scheme and host.
     */
    fun urlSignature(url: String): String {
        val canonical = try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase().orEmpty()
            val path = (uri.rawPath ?: "").trimEnd('/')
            "$scheme://$host$path"
        } catch (_: URISyntaxException) {
            url.trim()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "share:" + digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
