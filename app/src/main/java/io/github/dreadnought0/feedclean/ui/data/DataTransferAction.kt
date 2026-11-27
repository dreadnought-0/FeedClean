package io.github.dreadnought0.feedclean.ui.data

import kotlinx.serialization.Serializable

/**
 * Which Data & privacy action the user picked in Settings before landing on the DataTransfer screen.
 * Part of the `DataTransfer` navigation key, so it is saved with the back stack.
 */
@Serializable
enum class DataTransferAction {
    /** Full backup: every table, JSON + CSV zip. */
    EXPORT_FULL,

    /** Public export: handles + objective tags only. */
    EXPORT_PUBLIC,

    /** Import a previous full export (idempotent merge by handle). */
    IMPORT,

    /** Delete all local data. Must ask for explicit confirmation before doing anything. */
    CLEAR_ALL,
}
