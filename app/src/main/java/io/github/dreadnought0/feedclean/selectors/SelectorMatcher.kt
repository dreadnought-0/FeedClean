package io.github.dreadnought0.feedclean.selectors

import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.NameNormalizer
import io.github.dreadnought0.feedclean.detect.UiNode

/**
 * A node found for a selector key.
 *
 * @property value the captured value: the requested regex group, else the attribute the strategy tested, else
 *   the node's text or content-desc (cleaned of invisible characters, trimmed).
 * @property strategyIndex 0 = the primary strategy; anything higher means a fallback fired, which usually
 *   means a TikTok update changed something.
 */
data class SelectorMatch(
    val key: String,
    val node: UiNode,
    val value: String?,
    val strategyIndex: Int,
    val strategyCount: Int,
    val strategy: SelectorStrategy,
) {
    val isFallback: Boolean get() = strategyIndex > 0

    /** Executors should gesture-tap the bounds center if ACTION_CLICK has no effect. */
    val tapFallback: Boolean get() = strategy.tapFallback

    /** For `ScreenSnapshot.matchedStrategies`, e.g. `2/3 descRegex=^(.+) profile$ group=1`. */
    val description: String get() = "${strategyIndex + 1}/$strategyCount ${strategy.describe()}"
}

/**
 * Evaluates selector keys against a set of nodes. Stateless and thread-safe.
 *
 * Strategies are tried in order and the first strategy that matches anything wins; within it, nodes are taken in
 * the order given (document order), except for [SelectorStrategy.above] / [SelectorStrategy.below], where the
 * nearest node wins. Missing keys, invalid regexes, and unresolvable references all mean "no match"; nothing
 * here throws.
 *
 * `frame` is the reference rectangle for [SelectorStrategy.topFraction] (the parser passes the TikTok window's
 * bounds). Strategies with `topFraction` never match when no frame is given.
 */
class SelectorMatcher(val selectors: LocaleSelectors) {

    /** First match for [key] among [nodes], or null. [resolved] supplies already-captured values of other keys. */
    fun first(
        key: String,
        nodes: List<UiNode>,
        resolved: Map<String, String?> = emptyMap(),
        frame: Bounds? = null,
    ): SelectorMatch? = evaluate(key, nodes, resolved, frame, depth = 0, firstOnly = true).firstOrNull()

    /** All matches of the first strategy of [key] that matches anything. */
    fun all(
        key: String,
        nodes: List<UiNode>,
        resolved: Map<String, String?> = emptyMap(),
        frame: Bounds? = null,
    ): List<SelectorMatch> = evaluate(key, nodes, resolved, frame, depth = 0, firstOnly = false)

    /** Label list stored under [key] (e.g. `feed.paidAdLabels`); empty if missing. */
    fun labels(key: String): List<String> = selectors.labels[key].orEmpty()

    /** True if [value] equals (ignoring case and invisible characters) an entry of the label list [key]. */
    fun isLabel(key: String, value: String?): Boolean {
        val cleaned = NameNormalizer.clean(value) ?: return false
        return labels(key).any { it.equals(cleaned, ignoreCase = true) }
    }

    /**
     * Evaluates the text-only strategies of [key] (no view ID, class, flag, or geometry predicates) against a
     * bare string, testing it as both text and content-desc. For toasts and option labels.
     */
    fun matchesText(key: String, value: String?): Boolean {
        val cleaned = NameNormalizer.clean(value) ?: return false
        val list = selectors.compiled[key] ?: return false
        return list.any { cs ->
            cs.isValid && cs.strategy.isTextOnly && textPredicates(cs, text = cleaned, desc = cleaned) != null
        }
    }

    private fun evaluate(
        key: String,
        nodes: List<UiNode>,
        resolved: Map<String, String?>,
        frame: Bounds?,
        depth: Int,
        firstOnly: Boolean,
    ): List<SelectorMatch> {
        if (depth > MAX_REFERENCE_DEPTH) return emptyList()
        val list = selectors.compiled[key] ?: return emptyList()
        for ((index, cs) in list.withIndex()) {
            if (!cs.isValid) continue
            val found = evaluateStrategy(key, cs, index, list.size, nodes, resolved, frame, depth, firstOnly)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private fun evaluateStrategy(
        key: String,
        cs: CompiledStrategy,
        index: Int,
        count: Int,
        nodes: List<UiNode>,
        resolved: Map<String, String?>,
        frame: Bounds?,
        depth: Int,
        firstOnly: Boolean,
    ): List<SelectorMatch> {
        val s = cs.strategy
        if (s.topFraction != null && (frame == null || frame.isEmpty)) return emptyList()
        fun refNode(ref: String?): UiNode? =
            if (ref == null || ref == key) null else evaluate(ref, nodes, resolved, frame, depth + 1, true).firstOrNull()?.node

        val insideNode = s.inside?.let { refNode(it) ?: return emptyList() }
        val aboveNode = s.above?.let { refNode(it) ?: return emptyList() }
        val belowNode = s.below?.let { refNode(it) ?: return emptyList() }
        val group2Target = s.group2Equals?.let { ref ->
            if (ref == key) return emptyList()
            val v = if (resolved.containsKey(ref)) {
                resolved[ref]
            } else {
                evaluate(ref, nodes, resolved, frame, depth + 1, true).firstOrNull()?.value
            }
            v ?: return emptyList()
        }
        val geometric = aboveNode != null || belowNode != null

        val out = mutableListOf<Pair<SelectorMatch, Int>>()
        for (node in nodes) {
            if (!flagPredicates(s, node)) continue
            if (s.topFraction != null && !inTopOf(node.bounds, frame!!, s.topFraction)) continue
            if (insideNode != null && (node === insideNode || !insideNode.bounds.contains(node.bounds))) continue
            var gap = 0
            if (aboveNode != null) gap = verticalGap(node, aboveNode, above = true, s.maxGap) ?: continue
            if (belowNode != null) gap = verticalGap(node, belowNode, above = false, s.maxGap) ?: continue
            val text = NameNormalizer.clean(node.text)
            val desc = NameNormalizer.clean(node.contentDescription)
            val result = textPredicates(cs, text, desc) ?: continue
            if (group2Target != null) {
                val g2 = result.group2 ?: continue
                if (NameNormalizer.normalize(g2) != NameNormalizer.normalize(group2Target)) continue
            }
            out += SelectorMatch(key, node, result.value, index, count, s) to gap
            if (firstOnly && !geometric) break
        }
        if (geometric) out.sortBy { it.second }
        val matches = out.map { it.first }
        return if (firstOnly) matches.take(1) else matches
    }

    private fun flagPredicates(s: SelectorStrategy, node: UiNode): Boolean {
        if (s.viewId != null && node.viewIdName != s.viewId) return false
        if (s.className != null && node.className != s.className) return false
        if (s.clickable != null && node.isClickable != s.clickable) return false
        if (s.longClickable != null && node.isLongClickable != s.longClickable) return false
        if (s.scrollable != null && node.isScrollable != s.scrollable) return false
        if (s.selected != null && node.isSelected != s.selected) return false
        if (s.excludeViewIds != null && node.viewIdName in s.excludeViewIds) return false
        return true
    }

    private class TextResult(val value: String?, val group2: String?)

    /** Checks text/desc predicates; returns the captured value (possibly null) or null-result if no match. */
    private fun textPredicates(cs: CompiledStrategy, text: String?, desc: String?): TextResult? {
        val s = cs.strategy
        if (s.textEquals != null && text != s.textEquals) return null
        if (s.descEquals != null && desc != s.descEquals) return null
        if (s.textIn != null && (text == null || labels(s.textIn).none { it.equals(text, ignoreCase = true) })) {
            return null
        }
        if (s.descIn != null && (desc == null || labels(s.descIn).none { it.equals(desc, ignoreCase = true) })) {
            return null
        }
        val descMatch = cs.descRegex?.let { regex -> regex.find(desc ?: return null) ?: return null }
        val textMatch = cs.textRegex?.let { regex -> regex.find(text ?: return null) ?: return null }

        fun groupOf(m: MatchResult, whole: String): String? {
            val g = s.group ?: return whole
            return m.groups.getOrNullSafe(g)?.value
        }

        val value = when {
            descMatch != null -> groupOf(descMatch, desc!!) ?: return null
            textMatch != null -> groupOf(textMatch, text!!) ?: return null
            s.descEquals != null || s.descIn != null -> desc
            s.textEquals != null || s.textIn != null -> text
            else -> text ?: desc
        }
        val group2 = (descMatch ?: textMatch)?.groups?.getOrNullSafe(2)?.value
        return TextResult(value, group2)
    }

    private fun MatchGroupCollection.getOrNullSafe(index: Int): MatchGroup? =
        if (index in 0 until size) get(index) else null

    /** True if [b] lies vertically within the top [fraction] of [frame] (horizontal position is not checked). */
    private fun inTopOf(b: Bounds, frame: Bounds, fraction: Double): Boolean {
        if (b.isEmpty || b.top < frame.top) return false
        return b.bottom <= frame.top + frame.height * fraction
    }

    /** Vertical gap between [node] and [ref] if [node] is above/below [ref] and overlaps it horizontally. */
    private fun verticalGap(node: UiNode, ref: UiNode, above: Boolean, maxGap: Int?): Int? {
        if (node === ref) return null
        val a = node.bounds
        val b = ref.bounds
        if (!(a.left < b.right && b.left < a.right)) return null
        val gap = if (above) b.top - a.bottom else a.top - b.bottom
        if (gap < 0) return null
        if (maxGap != null && gap > maxGap) return null
        return gap
    }

    private companion object {
        const val MAX_REFERENCE_DEPTH = 4
    }
}
