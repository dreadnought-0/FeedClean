package io.github.dreadnought0.feedclean.ui.debug

import io.github.dreadnought0.feedclean.service.DecisionEvent
import io.github.dreadnought0.feedclean.service.DecisionLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Counts of decision-log entries for the Debug screen's "Decision log" section, grouped so that e.g.
 * `page  SEARCH  12  hold:CONTEXT_OFF` answers "why wasn't this skipped?" at a glance. Pure.
 *
 * @property today rows for entries of [day], most frequent first.
 * @property week rows for the 7 days ending on [day] (today included), most frequent first.
 * @property recent the newest entries, newest first.
 */
data class DecisionLogSummary(
    val day: LocalDate,
    val todayTotal: Int,
    val weekTotal: Int,
    val today: List<Row>,
    val week: List<Row>,
    val recent: List<DecisionLogEntry>,
) {
    /**
     * One group. For PAGE / WATCH entries: [where] = the feed context, [what] = the gate result, plus `→ <outcome>`
     * when auto-skip ran. For CARD entries: [where] = the button, [what] = the result (and reason).
     */
    data class Row(val event: DecisionEvent, val where: String, val what: String, val count: Int)

    companion object {
        const val DAYS = 7
        const val RECENT = 50

        fun of(entries: List<DecisionLogEntry>, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DecisionLogSummary {
            val weekStart = day.minusDays(DAYS - 1L)
            val week = entries.filter { dateOf(it, zone) in weekStart..day }
            val today = week.filter { dateOf(it, zone) == day }
            return DecisionLogSummary(
                day = day,
                todayTotal = today.size,
                weekTotal = week.size,
                today = rows(today),
                week = rows(week),
                recent = entries.sortedBy { it.atMillis }.takeLast(RECENT).reversed(),
            )
        }

        /** The group key of one entry: (event, where, what). */
        fun keyOf(e: DecisionLogEntry): Triple<DecisionEvent, String, String> = when (e.event) {
            DecisionEvent.CARD -> Triple(
                e.event,
                e.action?.name ?: "?",
                listOfNotNull(e.result, e.reason).joinToString(":").ifEmpty { "?" },
            )
            DecisionEvent.PAGE, DecisionEvent.WATCH -> Triple(
                e.event,
                e.context?.name ?: "?",
                (e.gate ?: "?") + (e.outcome?.let { " → $it" } ?: ""),
            )
        }

        private fun rows(entries: List<DecisionLogEntry>): List<Row> = entries
            .groupingBy(::keyOf)
            .eachCount()
            .map { (key, count) -> Row(key.first, key.second, key.third, count) }
            .sortedWith(compareBy<Row>({ it.event.ordinal }, { -it.count }, { it.where }, { it.what }))

        private fun dateOf(e: DecisionLogEntry, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(e.atMillis).atZone(zone).toLocalDate()
    }
}

/** Monospace text for the Debug screen's "Decision log" section (diagnostic keys, not translated UI text). */
object DecisionLogFormatter {
    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    /** The count tables: today, then the last 7 days. */
    fun formatCounts(summary: DecisionLogSummary): String = buildString {
        appendLine("today ${summary.day} (${summary.todayTotal}):")
        rows(summary.today)
        appendLine()
        appendLine("last ${DecisionLogSummary.DAYS} days (${summary.weekTotal}):")
        rows(summary.week)
    }.trimEnd()

    /** The newest entries, one per line, newest first. */
    fun formatRecent(entries: List<DecisionLogEntry>, zone: ZoneId = ZoneId.systemDefault()): String =
        if (entries.isEmpty()) "(none)" else entries.joinToString("\n") { formatEntry(it, zone) }

    /** One entry as a short line, e.g. `11-25 14:03:12 page FOR_YOU [SHOP] skip:ORGANIC_SHOP go → skipped`. */
    fun formatEntry(e: DecisionLogEntry, zone: ZoneId = ZoneId.systemDefault()): String = buildList {
        add(time.format(Instant.ofEpochMilli(e.atMillis).atZone(zone)))
        add(e.event.name.lowercase())
        e.action?.let { add(it.name) }
        e.context?.let { add(it.name) }
        e.kinds?.let { add(it.joinToString(",", "[", "]")) }
        e.newKinds?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(",", "new=[", "]")) }
        e.sinceFirstReadMillis?.let { add("+${it}ms") }
        e.trigger?.let { add(it.name) }
        e.decision?.let { add(it) }
        e.gate?.let { add(it) }
        e.outcome?.let { add("→ $it") }
        e.notInterested?.let { add("ni=$it") }
        e.result?.let { add(it) }
        e.step?.let { add("step=$it") }
        e.reason?.let { add("reason=$it") }
        if (e.logged == true) add("logged")
    }.joinToString(" ")

    private fun StringBuilder.rows(rows: List<DecisionLogSummary.Row>) {
        if (rows.isEmpty()) {
            appendLine("  (none)")
            return
        }
        val whereWidth = rows.maxOf { it.where.length }
        val countWidth = rows.maxOf { it.count.toString().length }
        rows.forEach { r ->
            append("  ").append(r.event.name.lowercase().padEnd(5)).append(' ')
            append(r.where.padEnd(whereWidth)).append(' ')
            append(r.count.toString().padStart(countWidth)).append("  ")
            appendLine(r.what)
        }
    }
}
