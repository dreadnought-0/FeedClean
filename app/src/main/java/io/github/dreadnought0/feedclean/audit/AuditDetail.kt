package io.github.dreadnought0.feedclean.audit

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * The `detail` of an audit finding: locale-neutral `key=value` pairs joined by `;`, e.g.
 * `blocked_at=2026-10-03T12:00:00Z;sightings_after=2`. The UI formats them; the report export keeps them.
 * Dates are ISO-8601 UTC.
 */
data class AuditDetail(
    /** Our verified block (MISSING_FROM_BLOCKLIST, SEEN_AFTER_BLOCK). */
    val blockedAtMillis: Long? = null,
    /** Block date in TikTok's export (IN_EXPORT_NOT_IN_DB), if the export had one. */
    val exportDateMillis: Long? = null,
    /** Number of sightings after the block (SEEN_AFTER_BLOCK). */
    val sightingsAfter: Int? = null,
    /** Latest sighting after the block (SEEN_AFTER_BLOCK). */
    val lastSeenAtMillis: Long? = null,
    /** Our block is newer than every dated entry of the export, so the export may simply predate it. */
    val newerThanExport: Boolean = false,
    /** The handle is already in FeedClean (IN_EXPORT_NOT_IN_DB), just without a recorded block. */
    val knownAccount: Boolean = false,
) {
    /** Encodes to the `detail` text (null when there is nothing to say). */
    fun encode(): String? = buildList {
        blockedAtMillis?.let { add("$BLOCKED_AT=${iso(it)}") }
        exportDateMillis?.let { add("$EXPORT_DATE=${iso(it)}") }
        sightingsAfter?.let { add("$SIGHTINGS_AFTER=$it") }
        lastSeenAtMillis?.let { add("$LAST_SEEN_AT=${iso(it)}") }
        if (newerThanExport) add("$NEWER_THAN_EXPORT=true")
        if (knownAccount) add("$KNOWN_ACCOUNT=true")
    }.takeIf { it.isNotEmpty() }?.joinToString(";")

    companion object {
        private const val BLOCKED_AT = "blocked_at"
        private const val EXPORT_DATE = "export_date"
        private const val SIGHTINGS_AFTER = "sightings_after"
        private const val LAST_SEEN_AT = "last_seen_at"
        private const val NEWER_THAN_EXPORT = "newer_than_export"
        private const val KNOWN_ACCOUNT = "known_account"

        /** Parses a `detail` text; unknown keys and bad values are ignored. */
        fun parse(detail: String?): AuditDetail {
            if (detail.isNullOrBlank()) return AuditDetail()
            val map = detail.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            return AuditDetail(
                blockedAtMillis = map[BLOCKED_AT]?.let(::parseIso),
                exportDateMillis = map[EXPORT_DATE]?.let(::parseIso),
                sightingsAfter = map[SIGHTINGS_AFTER]?.toIntOrNull(),
                lastSeenAtMillis = map[LAST_SEEN_AT]?.let(::parseIso),
                newerThanExport = map[NEWER_THAN_EXPORT] == "true",
                knownAccount = map[KNOWN_ACCOUNT] == "true",
            )
        }

        private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

        private fun parseIso(text: String): Long? = try {
            Instant.parse(text).toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
