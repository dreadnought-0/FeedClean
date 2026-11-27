package io.github.dreadnought0.feedclean.ui.debug

import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.service.DecisionCardAction
import io.github.dreadnought0.feedclean.service.DecisionEvent
import io.github.dreadnought0.feedclean.service.DecisionLogEntry
import io.github.dreadnought0.feedclean.service.DetectionRules
import io.github.dreadnought0.feedclean.service.WatchTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DecisionLogSummaryTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 25)
    private fun at(daysAgo: Long, plusMillis: Long = 0) =
        today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000 + plusMillis

    private fun page(daysAgo: Long, ctx: FeedContext, gate: String, out: String? = null, plus: Long = 0) =
        DecisionLogEntry(atMillis = at(daysAgo, plus), event = DecisionEvent.PAGE, context = ctx, gate = gate, outcome = out)

    @Test
    fun countsByContextAndGateForTodayAndTheWeek() {
        val entries = buildList {
            repeat(12) { add(page(0, FeedContext.SEARCH, "hold:CONTEXT_OFF", plus = it.toLong())) }
            repeat(3) { add(page(0, FeedContext.FOR_YOU, "go", "skipped")) }
            add(page(0, FeedContext.FOR_YOU, "go", "cancelled:USER_SCROLLED"))
            repeat(5) { add(page(3, FeedContext.SEARCH, "hold:CONTEXT_OFF")) }
            add(page(6, FeedContext.OTHER, "hold:KEEP"))
            add(page(7, FeedContext.OTHER, "hold:KEEP")) // 8 days ago counting today: outside the week
            add(
                DecisionLogEntry(
                    atMillis = at(0), event = DecisionEvent.WATCH, context = FeedContext.FOR_YOU, gate = "go",
                    outcome = "skipped", trigger = WatchTrigger.SIGNALS_GREW,
                ),
            )
            add(DecisionLogEntry(atMillis = at(1), event = DecisionEvent.CARD, action = DecisionCardAction.BLOCK, result = "Blocked", reason = "VERIFIED"))
        }

        val s = DecisionLogSummary.of(entries.shuffled(java.util.Random(1)), today, zone)

        assertEquals(17, s.todayTotal)
        assertEquals(24, s.weekTotal)
        assertEquals(
            listOf(
                DecisionLogSummary.Row(DecisionEvent.PAGE, "SEARCH", "hold:CONTEXT_OFF", 12),
                DecisionLogSummary.Row(DecisionEvent.PAGE, "FOR_YOU", "go → skipped", 3),
                DecisionLogSummary.Row(DecisionEvent.PAGE, "FOR_YOU", "go → cancelled:USER_SCROLLED", 1),
                DecisionLogSummary.Row(DecisionEvent.WATCH, "FOR_YOU", "go → skipped", 1),
            ),
            s.today,
        )
        assertEquals(DecisionLogSummary.Row(DecisionEvent.PAGE, "SEARCH", "hold:CONTEXT_OFF", 17), s.week.first())
        assertTrue(DecisionLogSummary.Row(DecisionEvent.PAGE, "OTHER", "hold:KEEP", 1) in s.week)
        assertEquals(DecisionLogSummary.Row(DecisionEvent.CARD, "BLOCK", "Blocked:VERIFIED", 1), s.week.last())
    }

    @Test
    fun recentIsTheNewestFiftyNewestFirst() {
        val entries = (0 until 80).map { page(0, FeedContext.FOR_YOU, "hold:KEEP", plus = it * 1_000L) }
        val recent = DecisionLogSummary.of(entries, today, zone).recent
        assertEquals(DecisionLogSummary.RECENT, recent.size)
        assertEquals(entries.last(), recent.first())
        assertEquals(entries[30], recent.last())
    }

    @Test
    fun formattedCountsShowContextCountAndGate() {
        val s = DecisionLogSummary.of(List(12) { page(0, FeedContext.SEARCH, "hold:CONTEXT_OFF") }, today, zone)
        val text = DecisionLogFormatter.formatCounts(s)
        assertTrue(text, text.contains("today 2026-11-25 (12):"))
        assertTrue(text, text.contains("page  SEARCH 12  hold:CONTEXT_OFF"))
        assertTrue(text, text.contains("last 7 days (12):"))
        assertTrue(DecisionLogFormatter.formatCounts(DecisionLogSummary.of(emptyList(), today, zone)).contains("(none)"))
    }

    @Test
    fun formattedEntryLine() {
        val e = DecisionLogEntry(
            atMillis = at(0, 62_000), event = DecisionEvent.WATCH, context = FeedContext.FOR_YOU,
            kinds = listOf(DetectionRules.Kind.PAID_AD, DetectionRules.Kind.SHOP), newKinds = listOf(DetectionRules.Kind.SHOP),
            decision = "skip:PAID_AD", gate = "go", outcome = "skipped", sinceFirstReadMillis = 800,
            trigger = WatchTrigger.SIGNALS_GREW, logged = true,
        )
        assertEquals(
            "11-25 01:01:02 watch FOR_YOU [PAID_AD,SHOP] new=[SHOP] +800ms SIGNALS_GREW skip:PAID_AD go → skipped logged",
            DecisionLogFormatter.formatEntry(e, zone),
        )
    }
}
