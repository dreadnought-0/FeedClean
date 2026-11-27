package io.github.dreadnought0.feedclean.service

/** Kind of an on-screen window, mirrored from `AccessibilityWindowInfo.TYPE_*` so decisions stay JVM-testable. */
enum class WindowKind { APPLICATION, INPUT_METHOD, SYSTEM, ACCESSIBILITY_OVERLAY, OTHER }

/**
 * What the runtime needs to know about one interactive window.
 *
 * @property layer z-order; higher is on top.
 * @property packageName owning package. Only read for active or focused application windows (foreground
 *   tracking) to avoid touching other apps' content more than necessary; null otherwise or if unknown.
 */
data class WindowFacts(
    val id: Int,
    val kind: WindowKind,
    val layer: Int,
    val isActive: Boolean,
    val isFocused: Boolean,
    val packageName: String? = null,
)

/** Window ordering rules for snapshots. Pure. */
object WindowOrdering {
    /**
     * The windows a snapshot should read: every interactive window except accessibility overlays (FeedClean's
     * own bubble, card, and cover are of that type and must never be parsed), topmost (highest layer) first.
     * Ties keep the system's order.
     */
    fun <T> forParsing(windows: List<T>, facts: (T) -> WindowFacts): List<T> =
        windows
            .map { it to facts(it) }
            .filter { (_, f) -> f.kind != WindowKind.ACCESSIBILITY_OVERLAY }
            .sortedByDescending { (_, f) -> f.layer }
            .map { it.first }
}
