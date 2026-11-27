package io.github.dreadnought0.feedclean.io

/**
 * Minimal RFC 4180 CSV writer (comma separator, CRLF line ends, fields quoted when needed). Pure.
 *
 * CSV files are for viewing in a spreadsheet; JSON is the authoritative format. To keep spreadsheets from
 * running text as a formula (CSV injection), a **text** cell that starts with `=`, `+`, `-`, `@`, tab or CR
 * gets a leading `'`. Numbers and booleans are written as-is.
 */
object Csv {
    private val formulaStarts = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** One CSV document: [header] then [rows]. Each value is a [String] (text), a number, a Boolean, or null. */
    fun write(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendRow(header.map { text(it) })
        rows.forEach { row -> appendRow(row.map(::cell)) }
    }

    /** Encodes one value as a CSV cell. */
    fun cell(value: Any?): String = when (value) {
        null -> ""
        is String -> text(value)
        is Boolean, is Number -> value.toString()
        else -> text(value.toString())
    }

    private fun text(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in formulaStarts) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            safe.startsWith(' ') || safe.endsWith(' ')
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, separator = ",")
        append("\r\n")
    }
}
