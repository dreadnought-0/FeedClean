package io.github.dreadnought0.feedclean.audit

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** The chosen file is not a usable TikTok data export. */
class DataExportException(val reason: Reason) : Exception(reason.name) {
    enum class Reason {
        /** Not valid JSON (and not a zip). */
        MALFORMED,

        /** A zip without any `.json` file (the TXT export format was probably chosen). */
        NO_JSON,

        /** More than [DataExportReader.MAX_TOTAL_BYTES] once uncompressed. */
        TOO_LARGE,
    }
}

/**
 * Reads TikTok's "Download your data" file — the `.zip` or the `.json` inside it — and finds the block list
 * (see [BlockListFinder]). Streams the content; nothing is stored. Pure (works on an [InputStream]).
 */
object DataExportReader {
    /** Upper bound on the (uncompressed) bytes read, against zip bombs. */
    const val MAX_TOTAL_BYTES: Long = 1L shl 30

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * Scans [input]. A zip is recognized by its magic bytes; every `.json` entry in it is scanned and the
     * results merged (malformed entries are skipped as long as one entry parses).
     *
     * @param maxBytes uncompressed read budget (tests use a small one).
     * @throws DataExportException when the file can't be used.
     */
    fun read(input: InputStream, maxBytes: Long = MAX_TOTAL_BYTES): BlockListScan {
        val budget = Budget(maxBytes)
        val buffered = BufferedInputStream(input)
        return try {
            if (isZip(buffered)) readZip(buffered, budget) else scanJson(Capped(buffered, budget))
        } catch (_: ZipException) {
            throw DataExportException(DataExportException.Reason.MALFORMED)
        } catch (_: BudgetExceeded) {
            throw DataExportException(DataExportException.Reason.TOO_LARGE)
        }
    }

    private fun readZip(input: InputStream, budget: Budget): BlockListScan {
        val scans = ArrayList<BlockListScan>()
        var jsonEntries = 0
        var malformed = 0
        val zip = ZipInputStream(input)
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                jsonEntries++
                try {
                    scans += BlockListFinder.find(InputStreamReader(Capped(zip, budget), Charsets.UTF_8))
                } catch (_: ExportParseException) {
                    malformed++
                }
            }
            entry = zip.nextEntry
        }
        return when {
            jsonEntries == 0 -> throw DataExportException(DataExportException.Reason.NO_JSON)
            malformed == jsonEntries -> throw DataExportException(DataExportException.Reason.MALFORMED)
            else -> BlockListFinder.merge(scans)
        }
    }

    private fun scanJson(input: InputStream): BlockListScan = try {
        BlockListFinder.find(InputStreamReader(input, Charsets.UTF_8))
    } catch (_: ExportParseException) {
        throw DataExportException(DataExportException.Reason.MALFORMED)
    }

    private fun isZip(input: BufferedInputStream): Boolean {
        input.mark(ZIP_MAGIC.size)
        val head = ByteArray(ZIP_MAGIC.size)
        var read = 0
        while (read < head.size) {
            val n = input.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        input.reset()
        return read == head.size && head.contentEquals(ZIP_MAGIC)
    }

    private class Budget(var remaining: Long)

    private class BudgetExceeded : IOException("too large")

    /** Counts bytes against [budget]; never closes the underlying stream (so one zip entry can be read). */
    private class Capped(input: InputStream, private val budget: Budget) : FilterInputStream(input) {
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) take(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) take(n.toLong())
            return n
        }

        override fun close() = Unit

        private fun take(n: Long) {
            budget.remaining -= n
            if (budget.remaining < 0) throw BudgetExceeded()
        }
    }
}
