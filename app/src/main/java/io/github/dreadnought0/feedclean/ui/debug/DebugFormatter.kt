package io.github.dreadnought0.feedclean.ui.debug

import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Plain-text dump of the live service state for the Debug screen and its copy-to-clipboard button.
 *
 * Keys are the Kotlin property names on purpose: this is a diagnostic dump (pasted into bug reports and
 * compared with selector keys), not translated UI text.
 */
object DebugFormatter {
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Short type name of a snapshot, stable across obfuscation. */
    fun typeName(snapshot: ScreenSnapshot?): String = when (snapshot) {
        null -> "none"
        is ScreenSnapshot.FeedVideo -> "FeedVideo"
        is ScreenSnapshot.Profile -> "Profile"
        is ScreenSnapshot.ShareSheet -> "ShareSheet"
        is ScreenSnapshot.BlockDialog -> "BlockDialog"
        is ScreenSnapshot.LongPressMenu -> "LongPressMenu"
        is ScreenSnapshot.DismissibleSheet -> "DismissibleSheet"
        is ScreenSnapshot.Unknown -> "Unknown"
    }

    /**
     * Formats the whole Debug view as text.
     *
     * @param zone time zone for `capturedAt` (injectable for tests).
     */
    fun format(
        serviceConnected: Boolean,
        tiktokForeground: Boolean,
        snapshot: ScreenSnapshot?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        line("serviceConnected", serviceConnected)
        line("tiktokForeground", tiktokForeground)
        line("snapshot", typeName(snapshot))
        if (snapshot == null) return@buildString
        line("capturedAt", timeFormat.format(Instant.ofEpochMilli(snapshot.capturedAtMillis).atZone(zone)))
        appendLine()
        when (snapshot) {
            is ScreenSnapshot.FeedVideo -> feedVideo(snapshot)
            is ScreenSnapshot.Profile -> profile(snapshot)
            is ScreenSnapshot.ShareSheet -> {
                line("blockNode", present(snapshot.blockNode))
                line("closeNode", present(snapshot.closeNode))
            }

            is ScreenSnapshot.BlockDialog -> {
                line("titleText", snapshot.titleText)
                line("displayName", snapshot.displayName)
                line("handle", snapshot.handle)
                line("confirmNode", present(snapshot.confirmNode))
                line("cancelNode", present(snapshot.cancelNode))
            }

            is ScreenSnapshot.LongPressMenu -> {
                line("notInterestedNode", present(snapshot.notInterestedNode))
                line("optionLabels", snapshot.optionLabels.joinToString(prefix = "[", postfix = "]"))
                line("reasonLabels", snapshot.reasonNodes.keys.joinToString(prefix = "[", postfix = "]"))
            }

            is ScreenSnapshot.DismissibleSheet -> {
                line("isCommerce", snapshot.isCommerce)
                line("dismissNode", present(snapshot.dismissNode))
                line("underlyingProfile", snapshot.underlyingProfile != null)
            }

            is ScreenSnapshot.Unknown -> line("note", snapshot.note)
        }
        appendLine()
        appendLine("matchedStrategies (${snapshot.matchedStrategies.size}):")
        if (snapshot.matchedStrategies.isEmpty()) {
            appendLine("  (none)")
        } else {
            snapshot.matchedStrategies.toSortedMap().forEach { (key, strategy) ->
                appendLine("  $key → $strategy")
            }
        }
    }

    private fun StringBuilder.feedVideo(s: ScreenSnapshot.FeedVideo) {
        line("displayName", s.displayName)
        line("probableHandle", s.probableHandle)
        line("caption", s.caption)
        line("soundDescription", s.soundDescription)
        line("isForYouTab", s.isForYouTab)
        line("pageSignature", s.pageSignature)
        line("pageBounds", bounds(s.pageBounds))
        line("avatarNode", present(s.avatarNode))
        line("displayNameNode", present(s.displayNameNode))
        line("videoNode", present(s.videoNode))
        line("pagerNode", present(s.pagerNode))
        appendLine()
        signals(s.signals)
    }

    private fun StringBuilder.profile(s: ScreenSnapshot.Profile) {
        line("displayName", s.displayName)
        line("handle", s.handle)
        line("accountCategory", s.accountCategory)
        line("isShopCreator", s.isShopCreator)
        line("isBlockedByMe", s.isBlockedByMe)
        line("blockedMessageHandle", s.blockedMessageHandle)
        line("shareNode", present(s.shareNode))
        line("backNode", present(s.backNode))
    }

    private fun StringBuilder.signals(signals: Signals) {
        appendLine("signals:")
        line("  isPaidAd", signals.isPaidAd)
        line("  isPromoted", signals.isPromoted)
        line("  hasShopAnchor", signals.hasShopAnchor)
        line("  isShopCreator", signals.isShopCreator)
        line("  isOrganicShop", signals.isOrganicShop)
        line("  isLive", signals.isLive)
        line("  isLiveShopping", signals.isLiveShopping)
        line("  isLiveEvent", signals.isLiveEvent)
        line("  hasAnySignal", signals.hasAnySignal)
        line("  disclosureLabel", signals.disclosureLabel)
        line("  anchorText", signals.anchorText)
        line("  ctaText", signals.ctaText)
    }

    private fun StringBuilder.line(key: String, value: Any?) {
        append(key).append(": ").appendLine(value?.toString() ?: "null")
    }

    private fun present(node: Any?): String = if (node != null) "present" else "absent"

    private fun bounds(b: Bounds): String = "[${b.left},${b.top}][${b.right},${b.bottom}]"
}
