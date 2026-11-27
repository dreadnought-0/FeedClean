package io.github.dreadnought0.feedclean.ui.debug

import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.service.ActionStatus
import io.github.dreadnought0.feedclean.service.AutoSkipStatus
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class DebugRuntimeFormatterTest {
    @Test
    fun showsSelectorsAutomationAndEvents() {
        val text = DebugRuntimeFormatter.format(
            DebugRuntimeInfo(
                selectorSource = "content://override",
                selectorLoadError = "Selector override not used: bad json",
                selectorWarnings = listOf("en/feed.x[0]: invalid regex"),
                verifiedAppVersions = listOf("47.7.3"),
                selectorsLoaded = true,
                curateFeedEnabled = true,
                autoSkip = AutoSkipStatus(pausedByLoopGuard = true, consecutiveSkips = 8, lastOutcome = "skipped", lastOutcomeAtMillis = 3_723_000),
                action = ActionStatus("block", running = false, result = "BLOCKED", atMillis = 0),
                recentEvents = listOf("01:02:03 auto-skip: skipped"),
            ),
            ZoneOffset.UTC,
        )
        listOf(
            "source: content://override",
            "loadError: Selector override not used: bad json",
            "- en/feed.x[0]: invalid regex",
            "verifiedAppVersions: [47.7.3]",
            "armed: true",
            "pausedByLoopGuard: true",
            "consecutiveSkips: 8",
            "lastOutcomeAt: 01:02:03",
            "kind: block",
            "result: BLOCKED",
            "01:02:03 auto-skip: skipped",
        ).forEach { assertTrue("missing '$it' in:\n$text", text.contains(it)) }
    }

    @Test
    fun showsWhereAutoSkipIsAllowedAndTheDecisionLogState() {
        val text = DebugRuntimeFormatter.format(
            DebugRuntimeInfo(
                autoSkipContexts = listOf(FeedContext.FOR_YOU, FeedContext.SEARCH),
                decisionLogEnabled = false,
            ),
        )
        assertTrue(text, text.contains("allowedIn: [FOR_YOU, SEARCH]"))
        assertTrue(text, text.contains("decisionLog: off"))
        assertTrue("omitted when unknown", !DebugRuntimeFormatter.format(DebugRuntimeInfo()).contains("allowedIn"))
    }

    @Test
    fun killSwitchDisarms() {
        val text = DebugRuntimeFormatter.format(DebugRuntimeInfo(curateFeedEnabled = true, killSwitchEngaged = true))
        assertTrue(text.contains("armed: false"))
        assertTrue(text.contains("action:\n  (none)"))
    }
}
