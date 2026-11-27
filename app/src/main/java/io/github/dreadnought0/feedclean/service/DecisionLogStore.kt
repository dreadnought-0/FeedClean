package io.github.dreadnought0.feedclean.service

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The decision log's files in app-private storage: `<dir>/YYYY-MM-DD.jsonl`, one per local day (see [DecisionLog]).
 * Synchronous file I/O: call it off the main thread. All operations hold one process-wide lock, so the service's
 * writer and the Debug screen (read / export / clear) never interleave. Pure JVM (no Android types).
 *
 * Limits ([prune]): files of the last [retentionDays] days (today included) are kept; then, while the total is over
 * [maxTotalBytes], the oldest file is dropped; a single file that alone is over the cap keeps only its newest half.
 */
class DecisionLogStore(
    val dir: File,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    private val retentionDays: Int = RETENTION_DAYS,
) {
    init {
        require(maxTotalBytes > 0 && retentionDays > 0) { "limits must be positive" }
    }

    /** One day's file. */
    data class LogFile(val date: LocalDate, val file: File) {
        val sizeBytes: Long get() = file.length()
    }

    /** Appends [text] (complete lines, each ending in a newline) to [date]'s file. */
    fun append(date: LocalDate, text: String) {
        if (text.isEmpty()) return
        synchronized(LOCK) {
            dir.mkdirs()
            FileOutputStream(fileFor(date), true).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    /** The log files, oldest first. Other files in [dir] are ignored. */
    fun files(): List<LogFile> = synchronized(LOCK) { listFiles() }

    fun totalBytes(): Long = files().sumOf { it.sizeBytes }

    /** Applies retention and the size cap relative to [today]. Returns how many files were deleted or trimmed. */
    fun prune(today: LocalDate): Int = synchronized(LOCK) {
        var changed = 0
        val oldestKept = today.minusDays(retentionDays - 1L)
        val files = listFiles().toMutableList()
        files.removeAll { f ->
            (f.date < oldestKept && f.file.delete()).also { if (it) changed++ }
        }
        var total = files.sumOf { it.sizeBytes }
        while (total > maxTotalBytes && files.size > 1) {
            val oldest = files.removeAt(0)
            val size = oldest.sizeBytes
            if (oldest.file.delete()) changed++
            total -= size
        }
        val last = files.singleOrNull()
        if (last != null && last.sizeBytes > maxTotalBytes) {
            trimToNewest(last.file, maxTotalBytes / 2)
            changed++
        }
        changed
    }

    /** Every parseable entry of the kept files, oldest first; damaged lines are skipped. */
    fun readAll(): List<DecisionLogEntry> = synchronized(LOCK) {
        listFiles().flatMap { f ->
            f.file.useLines(Charsets.UTF_8) { lines -> lines.mapNotNull(DecisionLogCodec::decode).toList() }
        }
    }

    /** Writes every file, oldest first, into [out] as one JSON-lines stream (the Debug screen's "Export log"). */
    fun exportTo(out: OutputStream) = synchronized(LOCK) {
        listFiles().forEach { f -> f.file.inputStream().use { it.copyTo(out) } }
        out.flush()
    }

    /** Deletes every log file. Returns how many were deleted. */
    fun clear(): Int = synchronized(LOCK) { listFiles().count { it.file.delete() } }

    private fun fileFor(date: LocalDate) = File(dir, "$date$SUFFIX")

    private fun listFiles(): List<LogFile> = dir.listFiles().orEmpty()
        .mapNotNull { file ->
            if (!file.isFile || !file.name.endsWith(SUFFIX)) return@mapNotNull null
            val date = try {
                LocalDate.parse(file.name.removeSuffix(SUFFIX))
            } catch (e: DateTimeParseException) {
                return@mapNotNull null
            }
            LogFile(date, file)
        }
        .sortedBy { it.date }

    /** Keeps only the newest complete lines of [file] that fit in [keepBytes]. */
    private fun trimToNewest(file: File, keepBytes: Long) {
        val bytes = file.readBytes()
        var start = (bytes.size - keepBytes).coerceAtLeast(0).toInt()
        while (start in 1 until bytes.size && bytes[start - 1] != NEWLINE) start++
        file.writeBytes(bytes.copyOfRange(start.coerceAtMost(bytes.size), bytes.size))
    }

    companion object {
        const val DIR_NAME = "decision-log"
        const val SUFFIX = ".jsonl"
        const val RETENTION_DAYS = 7
        const val DEFAULT_MAX_TOTAL_BYTES = 5L * 1024 * 1024

        private const val NEWLINE = '\n'.code.toByte()

        /** One lock for every store instance: the service and the app UI may each hold one for the same directory. */
        private val LOCK = Any()

        /** The app's decision log directory, `filesDir/decision-log`. */
        fun inFilesDir(filesDir: File) = DecisionLogStore(File(filesDir, DIR_NAME))
    }
}
