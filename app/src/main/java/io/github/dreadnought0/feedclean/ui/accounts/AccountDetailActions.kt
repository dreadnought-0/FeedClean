package io.github.dreadnought0.feedclean.ui.accounts

import io.github.dreadnought0.feedclean.data.model.AccountStatus

/**
 * Edits available on the Account detail screen. All of them change FeedClean's own records only; none can
 * start anything in TikTok. Implemented by [AccountDetailViewModel]; previews pass [NoOpAccountDetailActions].
 */
interface AccountDetailActions {
    /** Sets the account's status. */
    fun setStatus(status: AccountStatus)

    /** Saves the notes; blank clears them. */
    fun saveNotes(text: String)

    /** Adds a manual tag; false when [raw] is blank or too long. */
    fun addManualTag(raw: String): Boolean

    /** Removes a manual tag (detected tags stay). */
    fun removeManualTag(name: String)

    /** Opens the "suspected impersonator of…" picker. */
    fun openPicker()

    /** Clears the suspected-original link. */
    fun clearOriginal()
}

/** Does nothing; for previews. */
object NoOpAccountDetailActions : AccountDetailActions {
    override fun setStatus(status: AccountStatus) = Unit
    override fun saveNotes(text: String) = Unit
    override fun addManualTag(raw: String): Boolean = normalizeManualTag(raw) != null
    override fun removeManualTag(name: String) = Unit
    override fun openPicker() = Unit
    override fun clearOriginal() = Unit
}
