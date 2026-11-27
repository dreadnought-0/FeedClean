package io.github.dreadnought0.feedclean.selectors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One way of finding a node for a selector key in `assets/selectors/tiktok.json`.
 *
 * Every non-null predicate must hold (logical AND). A strategy with no predicate at all never matches, so a
 * typo can't turn into "match everything". Unknown JSON fields are ignored by the loader, so newer config files
 * still load in older app versions.
 *
 * Text and content-desc are compared after stripping invisible formatting characters (bidi marks, zero-width
 * characters, variation selectors) and trimming. `*Equals` is exact and case-sensitive; `*Regex` uses
 * "find" semantics, so anchor with `^…$` for a full match (inline flags such as `(?i)` work).
 */
@Serializable
data class SelectorStrategy(
    /** Matches [io.github.dreadnought0.feedclean.detect.UiNode.viewIdName] (the part after ":id/"). */
    val viewId: String? = null,
    val descEquals: String? = null,
    val descRegex: String? = null,
    val textEquals: String? = null,
    val textRegex: String? = null,
    /** Capture group of [descRegex] / [textRegex] used as the matched value. Null = the whole attribute value. */
    val group: Int? = null,
    /** Fully qualified class name, exact. Only use framework classes; app classes are obfuscated. */
    @SerialName("class") val className: String? = null,
    val clickable: Boolean? = null,
    val longClickable: Boolean? = null,
    val scrollable: Boolean? = null,
    val selected: Boolean? = null,
    /** Text must equal (ignoring case) one entry of the label list stored under this key. */
    val textIn: String? = null,
    /** Content-desc must equal (ignoring case) one entry of the label list stored under this key. */
    val descIn: String? = null,
    /** Node must not have one of these view IDs (keeps label fallbacks off the display name, caption, …). */
    val excludeViewIds: List<String>? = null,
    /** Node bounds must lie inside the bounds of the node matched by this other key. */
    val inside: String? = null,
    /** Node must sit above the node matched by this other key (horizontally overlapping); nearest wins. */
    val above: String? = null,
    /** Node must sit below the node matched by this other key (horizontally overlapping); nearest wins. */
    val below: String? = null,
    /** Max vertical gap in px for [above] / [below]. Null = unlimited. */
    val maxGap: Int? = null,
    /**
     * Node must lie entirely within the top [topFraction] (0..1] of the frame the caller passes to the matcher
     * (the parser passes the TikTok window's bounds), e.g. `0.25` = top quarter. Keeps a selector on the screen's top
     * chrome (tabs, search bar) so look-alikes further down never count. Without a frame it never matches. A
     * position modifier, not a predicate: a strategy needs at least one real predicate besides it.
     */
    val topFraction: Double? = null,
    /**
     * Capture group 2 of the regex must equal (after [io.github.dreadnought0.feedclean.detect.NameNormalizer.normalize])
     * the value captured for this other key, e.g. the sound credit's name must equal `feed.displayName`.
     */
    val group2Equals: String? = null,
    /** Hint for executors: ACTION_CLICK may not work here, gesture-tap the bounds center and verify. */
    val tapFallback: Boolean = false,
    /** Not yet verified on a device capture (placeholder). Shown on the Debug screen. */
    val unverified: Boolean = false,
    /** Free-form maintainer note. Ignored by the matcher. */
    val note: String? = null,
) {
    /** True when at least one predicate is set; strategies without predicates never match. */
    val hasPredicate: Boolean
        get() = viewId != null || descEquals != null || descRegex != null || textEquals != null ||
            textRegex != null || className != null || clickable != null || longClickable != null ||
            scrollable != null || selected != null || textIn != null || descIn != null || inside != null ||
            above != null || below != null || group2Equals != null

    /** True when the strategy only looks at text / content-desc, so it can be evaluated against a bare string. */
    val isTextOnly: Boolean
        get() = hasPredicate && viewId == null && className == null && clickable == null &&
            longClickable == null && scrollable == null && selected == null && inside == null &&
            above == null && below == null && group2Equals == null && excludeViewIds == null && topFraction == null

    /** Compact human-readable form, e.g. `descRegex=^(.+) profile$ group=1`. */
    fun describe(): String = buildList {
        viewId?.let { add("viewId=$it") }
        className?.let { add("class=$it") }
        descEquals?.let { add("descEquals=$it") }
        descRegex?.let { add("descRegex=$it") }
        descIn?.let { add("descIn=$it") }
        textEquals?.let { add("textEquals=$it") }
        textRegex?.let { add("textRegex=$it") }
        textIn?.let { add("textIn=$it") }
        group?.let { add("group=$it") }
        group2Equals?.let { add("group2Equals=$it") }
        clickable?.let { add("clickable=$it") }
        longClickable?.let { add("longClickable=$it") }
        scrollable?.let { add("scrollable=$it") }
        selected?.let { add("selected=$it") }
        excludeViewIds?.let { add("excludeViewIds=${it.joinToString(",")}") }
        inside?.let { add("inside=$it") }
        above?.let { add("above=$it") }
        below?.let { add("below=$it") }
        maxGap?.let { add("maxGap=$it") }
        topFraction?.let { add("topFraction=$it") }
        if (tapFallback) add("tapFallback")
        if (unverified) add("UNVERIFIED")
    }.joinToString(" ").ifEmpty { "(no predicate)" }
}
