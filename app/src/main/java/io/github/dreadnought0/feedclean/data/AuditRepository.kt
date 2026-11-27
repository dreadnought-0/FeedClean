package io.github.dreadnought0.feedclean.data

import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.Sighting
import kotlinx.coroutines.flow.Flow

/** Unblock audit storage and the queries the audit diff needs. */
interface AuditRepository {
    /** Creates an audit run (counts 0) and returns its id. */
    suspend fun createAudit(runAtMillis: Long, source: AuditSource, exportFileName: String?): Long

    /** Adds findings to [auditId]; each finding's id/auditId are ignored and its handle normalized. */
    suspend fun addFindings(auditId: Long, findings: List<AuditFinding>)

    /** Stores the final counts of the run. */
    suspend fun finishAudit(auditId: Long, missingCount: Int, seenAfterCount: Int)

    /** Newest first. */
    fun observeAudits(): Flow<List<Audit>>

    fun observeAudit(auditId: Long): Flow<Audit?>

    /** Sorted by kind, then handle. */
    fun observeFindings(auditId: Long): Flow<List<AuditFinding>>

    /**
     * Accounts we blocked: those with a handle, status BLOCKED or UNBLOCKED_SUSPECTED, and at least one
     * verified block event; each paired with its most recent verified block event. Sorted by handle.
     */
    suspend fun blockedAccountsWithHandles(): List<Pair<Account, BlockEvent>>

    /** Sightings of [accountId] strictly after [afterMillis], oldest first. */
    suspend fun sightingsAfter(accountId: Long, afterMillis: Long): List<Sighting>
}
