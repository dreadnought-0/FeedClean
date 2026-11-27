package io.github.dreadnought0.feedclean.ui.debug

import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.service.ActionStatus
import io.github.dreadnought0.feedclean.service.AutoSkipStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Runtime facts for the Debug screen beyond the live snapshot: selectors, automation state, recent events. */
data class DebugRuntimeInfo(
    val selectorSource: String = "bundled",
    val selectorLoadError: String? = null,
    val selectorWarnings: List<String> = emptyList(),
    val verifiedAppVersions: List<String> = emptyList(),
    val selectorsLoaded: Boolean = false,
    val curateFeedEnabled: Boolean = false,
    val killSwitchEngaged: Boolean = false,
    /** Players auto-skip is switched on in (Settings → Where to auto-skip); null = unknown. */
    val autoSkipContexts: List<FeedContext>? = null,
    val decisionLogEnabled: Boolean? = null,
    val autoSkip: AutoSkipStatus = AutoSkipStatus(),
    val action: ActionStatus? = null,
    val recentEvents: List<String> = emptyList(),
) {
    /** Mirrors FeedCleanSettings.autoSkipArmed. */
    val autoSkipArmed: Boolean get() = curateFeedEnabled && !killSwitchEngaged
}

/** Plain-text dump of [DebugRuntimeInfo] (diagnostic keys, not translated UI text). */
object DebugRuntimeFormatter {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun format(info: DebugRuntimeInfo, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("selectors:")
        line("  loaded", info.selectorsLoaded)
        line("  source", info.selectorSource)
        line("  loadError", info.selectorLoadError)
        line("  verifiedAppVersions", info.verifiedAppVersions.joinToString(prefix = "[", postfix = "]"))
        appendLine("  warnings (${info.selectorWarnings.size}):")
        info.selectorWarnings.forEach { appendLine("    - $it") }
        appendLine()
        appendLine("autoSkip:")
        line("  armed", info.autoSkipArmed)
        line("  curateFeedEnabled", info.curateFeedEnabled)
        line("  killSwitchEngaged", info.killSwitchEngaged)
        info.autoSkipContexts?.let { line("  allowedIn", it.joinToString(prefix = "[", postfix = "]")) }
        info.decisionLogEnabled?.let { line("  decisionLog", if (it) "on" else "off") }
        line("  pausedByLoopGuard", info.autoSkip.pausedByLoopGuard)
        line("  consecutiveSkips", info.autoSkip.consecutiveSkips)
        line("  skipping", info.autoSkip.skipping)
        line("  lastOutcome", info.autoSkip.lastOutcome)
        line("  lastOutcomeAt", info.autoSkip.lastOutcomeAtMillis?.let { time(it, zone) })
        appendLine()
        appendLine("action:")
        val a = info.action
        if (a == null) {
            appendLine("  (none)")
        } else {
            line("  kind", a.kind)
            line("  running", a.running)
            line("  step", a.step)
            line("  result", a.result)
            line("  at", time(a.atMillis, zone))
        }
        appendLine()
        appendLine("recentEvents (${info.recentEvents.size}):")
        if (info.recentEvents.isEmpty()) appendLine("  (none)")
        info.recentEvents.forEach { appendLine("  $it") }
    }

    private fun time(millis: Long, zone: ZoneId): String = timeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    private fun StringBuilder.line(key: String, value: Any?) {
        append(key).append(": ").appendLine(value?.toString() ?: "null")
    }
}
