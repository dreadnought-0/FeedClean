package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.detect.Signals
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Reads and writes the full-export zip (docs/EXPORT_SCHEMA.md). Pure (streams in, streams out).
 *
 * ```
 * manifest.json
 * json/<table>.json   one JSON array per table (authoritative; the importer reads these)
 * csv/<table>.csv     the same rows as CSV, for spreadsheets
 * ```
 */
object FullExportCodec {
    /** Current full-export schema version. */
    const val SCHEMA_VERSION: Int = 1

    /** Value of [ExportManifest.format]. */
    const val FORMAT_FULL: String = "feedclean-full"

    /** Largest zip entry the importer will read (guards against zip bombs). */
    const val MAX_ENTRY_BYTES: Long = 64L * 1024 * 1024

    const val ACCOUNTS = "accounts"
    const val TAGS = "tags"
    const val ACCOUNT_TAGS = "account_tags"
    const val SIGHTINGS = "sightings"
    const val BLOCK_EVENTS = "block_events"
    const val ACTION_LOG = "action_log"
    const val SESSIONS = "sessions"
    const val AUDITS = "audits"
    const val AUDIT_FINDINGS = "audit_findings"

    private const val MANIFEST = "manifest.json"

    internal val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** `exported_at` text for [nowMillis]: ISO-8601 UTC with seconds precision. */
    fun exportedAt(nowMillis: Long): String =
        Instant.ofEpochMilli(nowMillis).truncatedTo(ChronoUnit.SECONDS).toString()

    /** Builds the manifest for [bundle]. */
    fun manifest(bundle: ExportBundle, appVersion: String, nowMillis: Long): ExportManifest = ExportManifest(
        schemaVersion = SCHEMA_VERSION,
        appVersion = appVersion,
        exportedAt = exportedAt(nowMillis),
        counts = bundle.counts,
    )

    /** Writes [bundle] with [manifest] as a zip to [out]. Does not close [out]. */
    fun write(manifest: ExportManifest, bundle: ExportBundle, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.entry(MANIFEST, json.encodeToString(ExportManifest.serializer(), manifest))
        tables(bundle).forEach { table -> zip.entry("json/${table.name}.json", table.json()) }
        tables(bundle).forEach { table -> zip.entry("csv/${table.name}.csv", table.csv()) }
        zip.finish()
    }

    /**
     * Reads a full export from [input]. Only `manifest.json` and `json/<table>.json` are read; CSV and unknown
     * entries are ignored. Missing table files count as empty tables.
     *
     * @throws ImportFormatException when the file is not a FeedClean full export, is from a newer schema, or
     *   is damaged.
     */
    fun read(input: InputStream): ExportFile {
        val entries = HashMap<String, String>()
        try {
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val key = entryKey(entry.name)
                    if (!entry.isDirectory && key != null && key !in entries) entries[key] = zip.readCapped()
                    entry = zip.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw ImportFormatException(ImportFormatException.Reason.DAMAGED, e)
        } catch (e: IOException) {
            throw ImportFormatException(ImportFormatException.Reason.DAMAGED, e)
        }

        val manifestText = entries[MANIFEST]
            ?: throw ImportFormatException(ImportFormatException.Reason.NOT_A_FEEDCLEAN_EXPORT)
        val manifest = decode(ExportManifest.serializer(), manifestText)
        if (manifest.format != FORMAT_FULL) {
            throw ImportFormatException(ImportFormatException.Reason.NOT_A_FEEDCLEAN_EXPORT)
        }
        if (manifest.schemaVersion > SCHEMA_VERSION) {
            throw ImportFormatException(ImportFormatException.Reason.NEWER_SCHEMA, foundVersion = manifest.schemaVersion)
        }
        if (manifest.schemaVersion < 1) {
            throw ImportFormatException(ImportFormatException.Reason.DAMAGED, foundVersion = manifest.schemaVersion)
        }

        fun <T> table(name: String, serializer: KSerializer<T>): List<T> =
            entries["json/$name.json"]?.let { decode(ListSerializer(serializer), it) }.orEmpty()

        val bundle = ExportBundle(
            accounts = table(ACCOUNTS, AccountRow.serializer()),
            tags = table(TAGS, TagRow.serializer()),
            accountTags = table(ACCOUNT_TAGS, AccountTagRow.serializer()),
            sightings = table(SIGHTINGS, SightingRow.serializer()),
            blockEvents = table(BLOCK_EVENTS, BlockEventRow.serializer()),
            actionLog = table(ACTION_LOG, ActionLogRow.serializer()),
            sessions = table(SESSIONS, SessionRow.serializer()),
            audits = table(AUDITS, AuditRow.serializer()),
            auditFindings = table(AUDIT_FINDINGS, AuditFindingRow.serializer()),
        )
        return ExportFile(manifest, bundle)
    }

    /** CSV of one table of [bundle], by table name; null for unknown names. */
    fun csv(bundle: ExportBundle, tableName: String): String? = tables(bundle).firstOrNull { it.name == tableName }?.csv()

    // ── internals ────────────────────────────────────────────────────────────

    /** `manifest.json` / `json/<table>.json`, also when the zip was re-packed inside one top-level folder. */
    private fun entryKey(name: String): String? {
        val parts = name.replace('\\', '/').trim('/').split('/').filter { it.isNotEmpty() && it != "." }
        return when {
            parts.lastOrNull() == MANIFEST && parts.size <= 2 -> MANIFEST
            parts.size >= 2 && parts.size <= 3 && parts[parts.size - 2] == "json" ->
                "json/" + parts.last()
            else -> null
        }
    }

    private fun ZipInputStream.readCapped(): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_ENTRY_BYTES) throw ImportFormatException(ImportFormatException.Reason.TOO_LARGE)
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun <T> decode(serializer: KSerializer<T>, text: String): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw ImportFormatException(ImportFormatException.Reason.DAMAGED, e)
    } catch (e: IllegalArgumentException) {
        throw ImportFormatException(ImportFormatException.Reason.DAMAGED, e)
    }

    private fun ZipOutputStream.entry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** One table: its name, JSON encoder, and CSV columns. */
    private class Table<T>(
        val name: String,
        val rows: List<T>,
        val serializer: KSerializer<T>,
        val header: List<String>,
        val values: (T) -> List<Any?>,
    ) {
        fun json(): String = FullExportCodec.json.encodeToString(ListSerializer(serializer), rows)
        fun csv(): String = Csv.write(header, rows.map(values))
    }

    private fun tables(bundle: ExportBundle): List<Table<*>> = listOf(
        Table(
            ACCOUNTS, bundle.accounts, AccountRow.serializer(),
            listOf(
                "id", "handle", "handle_source", "display_name", "display_name_skeleton", "first_seen_at",
                "last_seen_at", "status", "account_category", "shop_tier", "is_default_handle", "notes",
                "suspected_original_of", "profile_photo_hash",
            ),
        ) {
            listOf(
                it.id, it.handle, it.handleSource, it.displayName, it.displayNameSkeleton, it.firstSeenAt,
                it.lastSeenAt, it.status, it.accountCategory, it.shopTier, it.isDefaultHandle, it.notes,
                it.suspectedOriginalOf, it.profilePhotoHash,
            )
        },
        Table(TAGS, bundle.tags, TagRow.serializer(), listOf("id", "name", "kind")) {
            listOf(it.id, it.name, it.kind)
        },
        Table(
            ACCOUNT_TAGS, bundle.accountTags, AccountTagRow.serializer(),
            listOf("account_id", "tag_id", "source", "created_at"),
        ) { listOf(it.accountId, it.tagId, it.source, it.createdAt) },
        Table(
            SIGHTINGS, bundle.sightings, SightingRow.serializer(),
            listOf(
                "id", "account_id", "seen_at", "video_url", "page_signature", "signals_json", "source",
                "user_action", "action_verified", "skip_reason", "screenshot_path", "session_id",
            ),
        ) {
            listOf(
                it.id, it.accountId, it.seenAt, it.videoUrl, it.pageSignature, signalsJson(it), it.source,
                it.userAction, it.actionVerified, it.skipReason, it.screenshotPath, it.sessionId,
            )
        },
        Table(
            BLOCK_EVENTS, bundle.blockEvents, BlockEventRow.serializer(),
            listOf("id", "account_id", "attempted_at", "method", "verified", "failure_reason", "step_reached"),
        ) { listOf(it.id, it.accountId, it.attemptedAt, it.method, it.verified, it.failureReason, it.stepReached) },
        Table(
            ACTION_LOG, bundle.actionLog, ActionLogRow.serializer(),
            listOf("id", "kind", "at", "account_id", "verified"),
        ) { listOf(it.id, it.kind, it.at, it.accountId, it.verified) },
        Table(
            SESSIONS, bundle.sessions, SessionRow.serializer(),
            listOf("id", "started_at", "ended_at", "videos_seen", "ads", "promoted", "shop", "live", "skipped"),
        ) { listOf(it.id, it.startedAt, it.endedAt, it.videosSeen, it.ads, it.promoted, it.shop, it.live, it.skipped) },
        Table(
            AUDITS, bundle.audits, AuditRow.serializer(),
            listOf("id", "run_at", "source", "export_file_name", "missing_from_blocklist_count", "seen_after_block_count"),
        ) {
            listOf(it.id, it.runAt, it.source, it.exportFileName, it.missingFromBlocklistCount, it.seenAfterBlockCount)
        },
        Table(
            AUDIT_FINDINGS, bundle.auditFindings, AuditFindingRow.serializer(),
            listOf("id", "audit_id", "account_id", "handle", "kind", "detail"),
        ) { listOf(it.id, it.auditId, it.accountId, it.handle, it.kind, it.detail) },
    )

    /** Compact signals JSON for the CSV cell (only non-default fields). */
    private fun signalsJson(row: SightingRow): String = Json.encodeToString(Signals.serializer(), row.signals)
}

/** The chosen file cannot be imported. Nothing was written to the database. */
class ImportFormatException(
    val reason: Reason,
    cause: Throwable? = null,
    /** `schema_version` found in the file, when known. */
    val foundVersion: Int? = null,
) : Exception(reason.name, cause) {
    enum class Reason {
        /** No `manifest.json`, or not a full export. */
        NOT_A_FEEDCLEAN_EXPORT,

        /** Written by a newer FeedClean with a schema this version does not understand. */
        NEWER_SCHEMA,

        /** Not a valid zip, or malformed JSON. */
        DAMAGED,

        /** An entry is larger than [FullExportCodec.MAX_ENTRY_BYTES]. */
        TOO_LARGE,
    }
}
