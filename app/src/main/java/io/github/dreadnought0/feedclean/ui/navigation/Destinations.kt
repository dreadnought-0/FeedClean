package io.github.dreadnought0.feedclean.ui.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dreadnought0.feedclean.ui.data.DataTransferAction
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destinations. Keys are @Serializable so the back stack survives process death.
 *
 * Top-level destinations (bottom bar) implement [TopLevelKey]; everything else is pushed on top of one.
 */
sealed interface TopLevelKey : NavKey

/** Home control center: armed summary, Pause everything, Detection, Auto-skip, Not interested, Blocking. */
@Serializable
data object HomeKey : TopLevelKey

/** Accounts list. */
@Serializable
data object AccountsKey : TopLevelKey

/** Stats (blocks/day, skips/day, Shop-vs-ad ratio). */
@Serializable
data object StatsKey : TopLevelKey

/** Slimmed Settings root. */
@Serializable
data object SettingsKey : TopLevelKey

/** One account's timeline. */
@Serializable
data class AccountDetailKey(val accountId: Long) : NavKey

/** Unblock-audit report. */
@Serializable
data object AuditKey : NavKey

/** Community-list review queue (not built yet). */
@Serializable
data object SuggestedKey : NavKey

/** Live setup checklist. */
@Serializable
data object SetupKey : NavKey

/** Live ScreenSnapshot / matched-strategy view. */
@Serializable
data object DebugKey : NavKey

/** "What Armed means" guide. */
@Serializable
data object ArmedGuideKey : NavKey

/** Where auto-skip may act (the four places). */
@Serializable
data object WhereItSkipsKey : NavKey

/** Detection master + the four kinds. */
@Serializable
data object DetectionRulesKey : NavKey

/** Not interested button, automation, reason. */
@Serializable
data object NotInterestedKey : NavKey

/** Block button, how a block works, block mode. */
@Serializable
data object BlockingKey : NavKey

/** Bubble and skip cover. */
@Serializable
data object BubbleCoverKey : NavKey

/** Settle delay, safety stop, screen-on. */
@Serializable
data object TimingKey : NavKey

/** Shared pace limits for blocks and Not interested. */
@Serializable
data object PaceKey : NavKey

/** Session tracking, decision log, screenshots. */
@Serializable
data object DataPrivacyKey : NavKey

/** Matching rules, OCR, Debug / Setup / Audit / Suggested. */
@Serializable
data object AdvancedKey : NavKey

/**
 * Export / import / clear data.
 *
 * @property action the action picked in Settings, or null when opened without a specific action.
 */
@Serializable
data class DataTransferKey(val action: DataTransferAction? = null) : NavKey
