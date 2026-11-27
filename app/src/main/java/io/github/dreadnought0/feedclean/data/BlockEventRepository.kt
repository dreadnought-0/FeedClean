package io.github.dreadnought0.feedclean.data

import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import kotlinx.coroutines.flow.Flow

/**
 * Block attempts. This is the account-facing record; the rate-limit budget lives separately in
 * [ActionLogRepository], which the executor writes itself.
 */
interface BlockEventRepository {
    /**
     * Records a block attempt and returns its id. When [verified] is true the account's status is set to
     * BLOCKED in the same transaction.
     */
    suspend fun record(
        accountId: Long,
        atMillis: Long,
        method: BlockMethod,
        verified: Boolean,
        failureReason: String? = null,
        stepReached: String? = null,
    ): Long

    /** Newest first. */
    fun observeForAccount(accountId: Long): Flow<List<BlockEvent>>

    suspend fun latestVerifiedBlock(accountId: Long): BlockEvent?
}
