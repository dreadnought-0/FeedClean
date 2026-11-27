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

/** Home dashboard: service status, quick toggles, today's counts. Root of the back stack. */
@Serializable
data object HomeKey : TopLevelKey

/** Accounts list. */
@Serializable
data object AccountsKey : TopLevelKey

/** Stats (blocks/day, skips/day, Shop-vs-ad ratio). */
@Serializable
data object StatsKey : TopLevelKey

/** Full grouped Settings list. */
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

/**
 * Export / import / clear data.
 *
 * @property action the action picked in Settings, or null when opened without a specific action.
 */
@Serializable
data class DataTransferKey(val action: DataTransferAction? = null) : NavKey
