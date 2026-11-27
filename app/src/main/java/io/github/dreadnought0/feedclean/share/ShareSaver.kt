package io.github.dreadnought0.feedclean.share

import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals

/** What the user confirmed in the share sheet. */
data class ShareSaveRequest(
    val url: String,
    /** Screen context still attached (fresh and not contradicted by the link), or null. */
    val context: ScreenSnapshot.FeedVideo?,
    /** Handle read from the link or from Resolve, or null. */
    val confirmedHandle: String?,
    /** Signals to record with the sighting (the context's, minus any the user deselected). */
    val signals: Signals,
    /** Tags the user picked; saved with source MANUAL. */
    val manualTags: Set<String>,
    val notes: String,
)

/**
 * Saves a share: account, one sighting (source SHARE, action LOG), manual tags and notes.
 * Logging only: nothing here touches the screen or TikTok.
 */
class ShareSaver(
    private val accounts: AccountRepository,
    private val sightings: SightingRepository,
) {
    /** Saves [request] at [nowMillis] and returns the account id. */
    suspend fun save(request: ShareSaveRequest, nowMillis: Long): Long {
        val accountId = resolveAccount(request, nowMillis)
        sightings.recordSighting(
            accountId = accountId,
            pageSignature = request.context?.pageSignature?.takeIf { it.isNotBlank() }
                ?: ShareContext.urlSignature(request.url),
            signals = request.signals,
            source = SightingSource.SHARE,
            userAction = UserAction.LOG,
            videoUrl = request.url,
            nowMillis = nowMillis,
        )
        request.manualTags.forEach { accounts.addTag(accountId, it, TagSource.MANUAL, nowMillis) }
        val notes = request.notes.trim()
        if (notes.isNotEmpty()) {
            accounts.setNotes(accountId, appendNote(accounts.getAccount(accountId)?.notes, notes))
        }
        return accountId
    }

    private suspend fun resolveAccount(request: ShareSaveRequest, nowMillis: Long): Long {
        val displayName = request.context?.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val probable = request.context?.probableHandle
        val handle = request.confirmedHandle
        return when {
            handle != null && displayName != null -> {
                // Find (or create) the feed account for the screen, then attach the confirmed handle; a
                // provisional row is merged into the handle-keyed account by learnHandle.
                val feedAccount = accounts.upsertFromFeed(displayName, probable, nowMillis)
                accounts.learnHandle(feedAccount, handle, HandleSource.RESOLVE)
            }
            handle != null -> accounts.ensureHandleAccount(handle, HandleSource.RESOLVE, null, nowMillis)
            else -> accounts.upsertFromFeed(displayName, probable, nowMillis)
        }
    }

    companion object {
        /** Appends [note] to [existing] on a new line, unless it is already there. */
        fun appendNote(existing: String?, note: String): String {
            val current = existing?.trim().orEmpty()
            return when {
                current.isEmpty() -> note
                current.lines().any { it.trim() == note } -> current
                else -> "$current\n$note"
            }
        }
    }
}
