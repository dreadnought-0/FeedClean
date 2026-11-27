package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.detect.NameNormalizer
import io.github.dreadnought0.feedclean.detect.TreeXmlWriter
import io.github.dreadnought0.feedclean.detect.UiNode
import io.github.dreadnought0.feedclean.selectors.LocaleSelectors

/**
 * Default redactor for in-app screen captures. Keeps only text/content-desc that FeedClean's own
 * selector config already knows about, and replaces everything else with [REDACTED]. Pure.
 *
 * Kept verbatim: values equal to a label (any label list, case-insensitive) or to a `textEquals` /
 * `descEquals` of any strategy, e.g. "Ad", "Product description", "Not interested", "Block".
 *
 * Kept with masking: values matched by the regex of a *text-only* strategy (one that only looks at text or
 * content-desc). Only the matched part is kept; text before/after it becomes [REDACTED], and when the strategy
 * extracts a value (`group` / `group2Equals`, i.e. a name or handle) every capture group is masked too:
 * `"Block [redacted] ([redacted])?"`, `"[redacted] profile"`, `"Sound: [redacted]"`. When several regexes match,
 * the result that keeps the least text wins. Regexes of strategies with structural predicates (view ID, class,
 * position) are ignored, since those (like `\S` on `id/title`) would match anything.
 *
 * So names, handles, captions, and the share sheet's contact rows never survive a capture.
 */
class SelectorRedactor(selectors: LocaleSelectors) : TreeXmlWriter.Redactor {

    private class Pattern(val regex: Regex, val maskGroups: Boolean)

    private val labels: Set<String> = selectors.labels.values.flatten().map { it.lowercase() }.toSet()
    private val textEquals: Set<String>
    private val descEquals: Set<String>
    private val textPatterns: List<Pattern>
    private val descPatterns: List<Pattern>

    init {
        val strategies = selectors.strategies.values.flatten()
        textEquals = strategies.mapNotNull { it.textEquals }.toSet()
        descEquals = strategies.mapNotNull { it.descEquals }.toSet()
        val textOnly = strategies.filter { it.isTextOnly }
        fun compile(pattern: String?, mask: Boolean): Pattern? =
            pattern?.let { p -> runCatching { Regex(p) }.getOrNull()?.let { Pattern(it, mask) } }
        textPatterns = textOnly.mapNotNull { compile(it.textRegex, it.group != null || it.group2Equals != null) }
        descPatterns = textOnly.mapNotNull { compile(it.descRegex, it.group != null || it.group2Equals != null) }
    }

    override fun redact(field: TreeXmlWriter.Field, value: String, node: UiNode): String = redactValue(field, value)

    /** [redact] without a node (tests, other callers). */
    fun redactValue(field: TreeXmlWriter.Field, value: String): String {
        val cleaned = NameNormalizer.clean(value) ?: return value
        if (cleaned.lowercase() in labels) return cleaned
        val (equals, patterns) = when (field) {
            TreeXmlWriter.Field.TEXT -> textEquals to textPatterns
            TreeXmlWriter.Field.CONTENT_DESCRIPTION -> descEquals to descPatterns
        }
        if (cleaned in equals) return cleaned
        return patterns
            .mapNotNull { p -> p.regex.find(cleaned)?.takeIf { it.value.isNotEmpty() }?.let { mask(cleaned, it, p.maskGroups) } }
            .minByOrNull { it.replace(REDACTED, "").length }
            ?: REDACTED
    }

    private fun mask(value: String, m: MatchResult, maskGroups: Boolean): String {
        val sb = StringBuilder()
        if (m.range.first > 0) sb.append(REDACTED)
        var pos = m.range.first
        if (maskGroups) {
            val ranges = (1 until m.groups.size)
                .mapNotNull { m.groups[it]?.range }
                .filter { !it.isEmpty() }
                .sortedBy { it.first }
            var lastEnd = -1
            for (r in ranges) {
                if (r.first <= lastEnd) continue // nested or overlapping: the outer group already masks it
                sb.append(value, pos, r.first).append(REDACTED)
                pos = r.last + 1
                lastEnd = r.last
            }
        }
        sb.append(value, pos, m.range.last + 1)
        if (m.range.last + 1 < value.length) sb.append(REDACTED)
        return sb.toString()
    }

    companion object {
        const val REDACTED = "[redacted]"
    }
}
