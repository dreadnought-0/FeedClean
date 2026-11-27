package io.github.dreadnought0.feedclean.audit

import io.github.dreadnought0.feedclean.data.model.Handles
import java.io.Reader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One account in TikTok's exported block list. */
data class ExportedBlock(
    /** Normalized: lowercase, no "@". */
    val handle: String,
    /** When TikTok says it was blocked (epoch millis, UTC assumed when the file has no zone), if known. */
    val blockedAtMillis: Long?,
)

/** What [BlockListFinder] found in one file. */
data class BlockListScan(
    /** True when a block list was recognized (it may be empty). */
    val found: Boolean,
    /** Distinct handles, sorted. */
    val entries: List<ExportedBlock>,
    /** Entries inside a recognized block list that had no usable handle. */
    val skippedEntries: Int,
) {
    companion object {
        val NOT_FOUND = BlockListScan(found = false, entries = emptyList(), skippedEntries = 0)
    }
}

/**
 * Finds the block list in TikTok's "Download your data" JSON **by shape**, because the format changes.
 * Pure.
 *
 * A block list is an array under a key containing "block" (any case, at any depth, possibly nested inside
 * further objects) whose elements are objects with a username-like key (`UserName`, `username`, `user_name`,
 * `uniqueId`, `handle`, …) and optionally a date-like key (`Date`, `BlockedAt`, `time`, …). Handles are
 * normalized and validated; profile URLs (`…/@handle`) are accepted. Dates are read as
 * `yyyy-MM-dd HH:mm:ss` (UTC), ISO-8601, `yyyy-MM-dd`, or epoch seconds/millis.
 */
object BlockListFinder {
    private val validHandle = Regex("^[a-z0-9._]{2,24}$")
    private val handleInUrl = Regex("""/@([A-Za-z0-9._]{2,24})(?=[/?#]|$)""")
    private val usernameKeys = listOf(
        "username", "uniqueid", "userhandle", "handle", "blockedusername", "blockeduser", "tiktokusername",
        "accountname", "user", "account",
    )
    private val dateKeys = listOf(
        "date", "blockdate", "blockeddate", "blockedat", "blocktime", "blockedtime", "datetime", "time",
        "timestamp", "createtime", "createdat",
    )
    private val tiktokDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** True for keys that may hold a block list. */
    fun isBlockKey(key: String): Boolean = key.contains("block", ignoreCase = true)

    /** Scans a whole parsed document. */
    fun find(root: JsonElement): BlockListScan {
        val acc = Accumulator()
        walk(root, acc)
        return acc.result()
    }

    /**
     * Scans a document from [reader] without loading it whole (see [JsonStreamScanner]).
     *
     * @throws ExportParseException on malformed JSON.
     */
    fun find(reader: Reader): BlockListScan {
        val acc = Accumulator()
        JsonStreamScanner(reader, ::isBlockKey).scan { _, value -> scanBlockValue(value, direct = true, acc) }
        return acc.result()
    }

    /** Combines scans of several files (e.g. every JSON file in a zip). */
    fun merge(scans: List<BlockListScan>): BlockListScan {
        val acc = Accumulator()
        scans.forEach { scan ->
            if (scan.found) acc.found = true
            acc.skipped += scan.skippedEntries
            scan.entries.forEach(acc::add)
        }
        return acc.result()
    }

    /** Parses a date value as found in exports; null when it is not a recognizable date. */
    fun parseDate(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        text.toLongOrNull()?.let { return epochToMillis(it) }
        text.toDoubleOrNull()?.let { return epochToMillis(it.toLong()) }
        return try {
            LocalDateTime.parse(text, tiktokDate).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
            tryParse { Instant.parse(text).toEpochMilli() }
                ?: tryParse { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
                ?: tryParse { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }
                ?: tryParse { LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        }
    }

    /** Normalizes a username value (`@Name`, `name`, or a profile URL) to a valid handle, or null. */
    fun normalizeHandle(raw: String?): String? {
        val text = raw?.trim() ?: return null
        val fromUrl = handleInUrl.find(text)?.groupValues?.get(1)
        val handle = Handles.normalize(fromUrl ?: text) ?: return null
        return handle.takeIf { validHandle.matches(it) }
    }

    // ── internals ────────────────────────────────────────────────────────────

    private class Accumulator {
        var found = false
        var skipped = 0
        private val byHandle = LinkedHashMap<String, ExportedBlock>()

        fun add(entry: ExportedBlock) {
            val previous = byHandle[entry.handle]
            // Same handle twice: keep the earliest known date.
            byHandle[entry.handle] = when {
                previous == null || previous.blockedAtMillis == null -> entry
                entry.blockedAtMillis == null -> previous
                else -> previous.copy(blockedAtMillis = minOf(previous.blockedAtMillis, entry.blockedAtMillis))
            }
        }

        fun result() = BlockListScan(found, byHandle.values.sortedBy { it.handle }, skipped)
    }

    /** Looks for block keys anywhere in [element]. */
    private fun walk(element: JsonElement, acc: Accumulator) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (isBlockKey(key)) scanBlockValue(value, direct = true, acc) else walk(value, acc)
            }
            is JsonArray -> element.forEach { walk(it, acc) }
            else -> Unit
        }
    }

    /**
     * Scans a value found under a block key. [direct] is true when this value's own key contains "block"
     * (an empty array then counts as an empty block list).
     */
    private fun scanBlockValue(value: JsonElement, direct: Boolean, acc: Accumulator) {
        when (value) {
            is JsonArray -> when {
                value.isEmpty() -> if (direct) acc.found = true
                value.any { it is JsonObject && usernameOf(it) != null } -> {
                    acc.found = true
                    value.forEach { item ->
                        val entry = (item as? JsonObject)?.let(::entryOf)
                        if (entry == null) acc.skipped++ else acc.add(entry)
                    }
                }
                else -> value.forEach { scanBlockValue(it, direct = false, acc) }
            }
            is JsonObject -> value.forEach { (key, child) -> scanBlockValue(child, isBlockKey(key), acc) }
            else -> Unit
        }
    }

    private fun entryOf(item: JsonObject): ExportedBlock? {
        val handle = normalizeHandle(usernameOf(item)) ?: return null
        return ExportedBlock(handle, dateOf(item))
    }

    /** The value of the first username-like key that holds a string, by priority. */
    private fun usernameOf(item: JsonObject): String? {
        val byKey = item.entries.associate { (k, v) -> normalizeKey(k) to v }
        for (key in usernameKeys) {
            val primitive = byKey[key] as? JsonPrimitive ?: continue
            if (primitive.isString && primitive.content.isNotBlank()) return primitive.content
        }
        return byKey.entries.firstOrNull { (k, v) -> k.endsWith("username") && v is JsonPrimitive && v.isString }
            ?.let { (it.value as JsonPrimitive).content }
    }

    private fun dateOf(item: JsonObject): Long? {
        val byKey = item.entries.associate { (k, v) -> normalizeKey(k) to v }
        val candidates = dateKeys.mapNotNull { byKey[it] } +
            byKey.filterKeys { it !in dateKeys && ("date" in it || "time" in it) }.values
        return candidates.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.let(::parseDate) }
    }

    private fun normalizeKey(key: String): String = key.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    /** Epoch seconds or millis → millis, if plausible (2000..2200). */
    private fun epochToMillis(value: Long): Long? {
        val millis = if (value > 100_000_000_000L) value else value * 1000
        return millis.takeIf { it in 946_684_800_000L..7_258_118_400_000L }
    }

    private inline fun tryParse(block: () -> Long): Long? = try {
        block()
    } catch (_: DateTimeParseException) {
        null
    }
}
