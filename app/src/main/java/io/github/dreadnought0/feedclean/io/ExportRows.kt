package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.detect.Signals
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Full-export file format, schema_version 1 (docs/EXPORT_SCHEMA.md). One row class per table; field names are
 * the database column names. Enums are written by name and read leniently (unknown names skip the row), so
 * the importer survives files from newer app versions that add enum values.
 */

/** `manifest.json` of a full export. */
@Serializable
data class ExportManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("app_version") val appVersion: String = "",
    /** ISO-8601 UTC, seconds precision, e.g. `2026-11-22T10:00:00Z`. */
    @SerialName("exported_at") val exportedAt: String = "",
    /** Always [FullExportCodec.FORMAT_FULL]; tells a full export apart from other FeedClean JSON files. */
    val format: String = FullExportCodec.FORMAT_FULL,
    /** Row count per table, for a quick look; not used by the importer. */
    val counts: Map<String, Int> = emptyMap(),
)

/** `accounts` row. */
@Serializable
data class AccountRow(
    val id: Long,
    val handle: String? = null,
    @SerialName("handle_source") val handleSource: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("display_name_skeleton") val displayNameSkeleton: String? = null,
    @SerialName("first_seen_at") val firstSeenAt: Long,
    @SerialName("last_seen_at") val lastSeenAt: Long,
    val status: String = "SEEN",
    @SerialName("account_category") val accountCategory: String? = null,
    @SerialName("shop_tier") val shopTier: String? = null,
    @SerialName("is_default_handle") val isDefaultHandle: Boolean = false,
    val notes: String? = null,
    @SerialName("suspected_original_of") val suspectedOriginalOf: Long? = null,
    @SerialName("profile_photo_hash") val profilePhotoHash: Long? = null,
)

/** `tags` row. */
@Serializable
data class TagRow(
    val id: Long,
    val name: String,
    val kind: String,
)

/** `account_tags` row. */
@Serializable
data class AccountTagRow(
    @SerialName("account_id") val accountId: Long,
    @SerialName("tag_id") val tagId: Long,
    val source: String,
    @SerialName("created_at") val createdAt: Long,
)

/** `sightings` row. `signals` is the detector's [Signals] object (the `signals_json` column, as JSON). */
@Serializable
data class SightingRow(
    val id: Long,
    @SerialName("account_id") val accountId: Long,
    @SerialName("seen_at") val seenAt: Long,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("page_signature") val pageSignature: String,
    val signals: Signals = Signals(),
    val source: String,
    @SerialName("user_action") val userAction: String = "NONE",
    @SerialName("action_verified") val actionVerified: Boolean? = null,
    @SerialName("skip_reason") val skipReason: String? = null,
    @SerialName("screenshot_path") val screenshotPath: String? = null,
    @SerialName("session_id") val sessionId: Long? = null,
)

/** `block_events` row. */
@Serializable
data class BlockEventRow(
    val id: Long,
    @SerialName("account_id") val accountId: Long,
    @SerialName("attempted_at") val attemptedAt: Long,
    val method: String,
    val verified: Boolean,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("step_reached") val stepReached: String? = null,
)

/** `action_log` row (the rate limiter's history). */
@Serializable
data class ActionLogRow(
    val id: Long,
    val kind: String,
    val at: Long,
    @SerialName("account_id") val accountId: Long? = null,
    val verified: Boolean,
)

/** `sessions` row. */
@Serializable
data class SessionRow(
    val id: Long,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("videos_seen") val videosSeen: Int = 0,
    val ads: Int = 0,
    val promoted: Int = 0,
    val shop: Int = 0,
    val live: Int = 0,
    val skipped: Int = 0,
)

/** `audits` row. */
@Serializable
data class AuditRow(
    val id: Long,
    @SerialName("run_at") val runAt: Long,
    val source: String,
    @SerialName("export_file_name") val exportFileName: String? = null,
    @SerialName("missing_from_blocklist_count") val missingFromBlocklistCount: Int = 0,
    @SerialName("seen_after_block_count") val seenAfterBlockCount: Int = 0,
)

/** `audit_findings` row. */
@Serializable
data class AuditFindingRow(
    val id: Long,
    @SerialName("audit_id") val auditId: Long,
    @SerialName("account_id") val accountId: Long? = null,
    val handle: String,
    val kind: String,
    val detail: String? = null,
)

/** Every table of one database (or of one export file), in memory. */
data class ExportBundle(
    val accounts: List<AccountRow> = emptyList(),
    val tags: List<TagRow> = emptyList(),
    val accountTags: List<AccountTagRow> = emptyList(),
    val sightings: List<SightingRow> = emptyList(),
    val blockEvents: List<BlockEventRow> = emptyList(),
    val actionLog: List<ActionLogRow> = emptyList(),
    val sessions: List<SessionRow> = emptyList(),
    val audits: List<AuditRow> = emptyList(),
    val auditFindings: List<AuditFindingRow> = emptyList(),
) {
    /** Row count per table name. */
    val counts: Map<String, Int>
        get() = linkedMapOf(
            FullExportCodec.ACCOUNTS to accounts.size,
            FullExportCodec.TAGS to tags.size,
            FullExportCodec.ACCOUNT_TAGS to accountTags.size,
            FullExportCodec.SIGHTINGS to sightings.size,
            FullExportCodec.BLOCK_EVENTS to blockEvents.size,
            FullExportCodec.ACTION_LOG to actionLog.size,
            FullExportCodec.SESSIONS to sessions.size,
            FullExportCodec.AUDITS to audits.size,
            FullExportCodec.AUDIT_FINDINGS to auditFindings.size,
        )
}

/** A full export as read from a file. */
data class ExportFile(val manifest: ExportManifest, val bundle: ExportBundle)

/** Parses an enum persisted by name; null for unknown names. */
internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
