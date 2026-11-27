package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.block.BlockAbortReason
import io.github.dreadnought0.feedclean.block.BlockResult
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.curate.NotInterestedResult
import io.github.dreadnought0.feedclean.data.AccountRepository
import io.github.dreadnought0.feedclean.data.BlockEventRepository
import io.github.dreadnought0.feedclean.data.SightingRepository
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction

/**
 * Maps the results of the overlay card's actions to repository calls. Pure over the repository interfaces
 * (the executors themselves never touch the database).
 *
 * Block:
 *  - [BlockResult.Blocked] → the sighting is marked BLOCK (verified); the handle is learned
 *    (`learnHandle`, source DIALOG, or PROFILE when it was already blocked), which merges the provisional
 *    account into the handle-keyed one; then a verified ASSISTED block event (sets status BLOCKED).
 *  - [BlockResult.Aborted] after something was tapped → an unverified block event with `failure_reason` and
 *    `step_reached`; a handle confirmed before the stop is still learned. Aborts that tapped nothing are not
 *    recorded (the rate limiter's action log still counts the attempt).
 *  - [BlockResult.UserCancelled] / [BlockResult.TimedOut] (stop-at-dialog style) → unverified event; handle learned.
 *  - [BlockResult.RateLimited] / [BlockResult.Rejected] → nothing (nothing happened on screen).
 */
class CardActionPersistence(
    private val accounts: AccountRepository,
    private val sightings: SightingRepository,
    private val blockEvents: BlockEventRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Persists a block result. Returns the account id that now represents the creator. */
    suspend fun persistBlock(target: CardTarget, result: BlockResult): Long = when (result) {
        is BlockResult.Blocked -> {
            // Mark the sighting before a handle merge can move it to another account id.
            markSighting(target, UserAction.BLOCK, verified = true)
            val source = if (result.alreadyBlocked) HandleSource.PROFILE else HandleSource.DIALOG
            val id = accounts.learnHandle(target.accountId, result.handle, source)
            val step = if (result.alreadyBlocked) BlockStep.READ_HANDLE else BlockStep.VERIFY
            blockEvents.record(id, clock(), BlockMethod.ASSISTED, verified = true, stepReached = step.name)
            id
        }
        is BlockResult.Aborted -> {
            if (!touchedScreen(result)) {
                target.accountId
            } else {
                val id = result.handleLearned?.let { accounts.learnHandle(target.accountId, it, HandleSource.PROFILE) }
                    ?: target.accountId
                recordFailure(id, result.reason.name, result.step)
                id
            }
        }
        is BlockResult.UserCancelled -> {
            val id = accounts.learnHandle(target.accountId, result.handle, HandleSource.DIALOG)
            recordFailure(id, FAILURE_USER_CANCELLED, BlockStep.AWAITING_USER_CONFIRMATION)
            id
        }
        is BlockResult.TimedOut -> {
            val id = accounts.learnHandle(target.accountId, result.handle, HandleSource.DIALOG)
            recordFailure(id, FAILURE_TIMED_OUT, BlockStep.AWAITING_USER_CONFIRMATION)
            id
        }
        is BlockResult.RateLimited, is BlockResult.Rejected -> target.accountId
    }

    /** The block run was cancelled (kill-switch, TikTok left) after reaching [lastStep]. */
    suspend fun persistBlockInterrupted(target: CardTarget, lastStep: BlockStep) {
        if (lastStep >= BlockStep.OPEN_PROFILE) recordFailure(target.accountId, FAILURE_CANCELLED, lastStep)
    }

    /** Manual "Not interested": the sighting gets NOT_INTERESTED (verified as reported) once the tap may have landed. */
    suspend fun persistNotInterested(target: CardTarget, result: NotInterestedResult) {
        when (result) {
            is NotInterestedResult.Done -> markSighting(target, UserAction.NOT_INTERESTED, result.verified)
            is NotInterestedResult.Aborted ->
                if (result.mayHaveReachedTikTok) markSighting(target, UserAction.NOT_INTERESTED, verified = false)
            is NotInterestedResult.RateLimited, is NotInterestedResult.Rejected -> Unit
        }
    }

    /** Manual Skip from the card; [advanced] = the feed showed another page afterwards. */
    suspend fun persistSkip(target: CardTarget, advanced: Boolean) {
        markSighting(target, UserAction.SKIP, verified = advanced)
    }

    /** "Log only": a sighting with action LOG (an existing one keeps any action it already has). */
    suspend fun persistLogOnly(target: CardTarget): Long = sightings.recordSighting(
        accountId = target.accountId,
        pageSignature = target.pageSignature,
        signals = target.signals,
        source = SightingSource.OVERLAY,
        userAction = UserAction.LOG,
        nowMillis = clock(),
    ).id

    /** "Mark false positive": account status FALSE_POSITIVE and the sighting's action FALSE_POSITIVE. */
    suspend fun persistFalsePositive(target: CardTarget) {
        accounts.setStatus(target.accountId, AccountStatus.FALSE_POSITIVE)
        markSighting(target, UserAction.FALSE_POSITIVE, verified = null)
    }

    /** Quick manual tag chip on the card. */
    suspend fun setManualTag(accountId: Long, tag: String, enabled: Boolean) {
        if (enabled) {
            accounts.addTag(accountId, tag, TagSource.MANUAL, clock())
        } else {
            accounts.removeTag(accountId, tag, TagSource.MANUAL)
        }
    }

    /** Sets [action] on the card's sighting, logging one first if the page had none. Returns its id. */
    private suspend fun markSighting(target: CardTarget, action: UserAction, verified: Boolean?): Long {
        val id = target.sightingId ?: sightings.recordSighting(
            accountId = target.accountId,
            pageSignature = target.pageSignature,
            signals = target.signals,
            source = SightingSource.OVERLAY,
            userAction = action,
            nowMillis = clock(),
        ).id
        sightings.setUserAction(id, action, verified)
        return id
    }

    private suspend fun recordFailure(accountId: Long, reason: String, step: BlockStep) {
        blockEvents.record(
            accountId = accountId,
            atMillis = clock(),
            method = BlockMethod.ASSISTED,
            verified = false,
            failureReason = reason,
            stepReached = step.name,
        )
    }

    companion object {
        const val FAILURE_USER_CANCELLED = "USER_CANCELLED"
        const val FAILURE_TIMED_OUT = "TIMED_OUT"
        const val FAILURE_CANCELLED = "CANCELLED"

        /** Abort reasons that stop before anything was tapped. */
        private val UNTOUCHED = setOf(
            BlockAbortReason.NOT_ON_FEED_VIDEO,
            BlockAbortReason.FEED_SHOWS_DIFFERENT_ACCOUNT,
            BlockAbortReason.NO_PROFILE_BUTTON,
        )

        /** True if the aborted run had tapped something on screen. */
        fun touchedScreen(result: BlockResult.Aborted): Boolean = result.reason !in UNTOUCHED
    }
}
