package io.github.dreadnought0.feedclean.data.room

import androidx.room.withTransaction
import io.github.dreadnought0.feedclean.data.AuditRepository
import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import io.github.dreadnought0.feedclean.data.db.entity.AuditEntity
import io.github.dreadnought0.feedclean.data.db.entity.AuditFindingEntity
import io.github.dreadnought0.feedclean.data.db.toModel
import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockEvent
import io.github.dreadnought0.feedclean.data.model.Handles
import io.github.dreadnought0.feedclean.data.model.Sighting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [AuditRepository]. */
class RoomAuditRepository(private val db: FeedCleanDatabase) : AuditRepository {
    private val auditDao = db.auditDao()
    private val accountDao = db.accountDao()
    private val blockEventDao = db.blockEventDao()
    private val sightingDao = db.sightingDao()

    override suspend fun createAudit(runAtMillis: Long, source: AuditSource, exportFileName: String?): Long =
        auditDao.insertAudit(AuditEntity(runAt = runAtMillis, source = source, exportFileName = exportFileName))

    override suspend fun addFindings(auditId: Long, findings: List<AuditFinding>) {
        if (findings.isEmpty()) return
        auditDao.insertFindings(
            findings.map {
                AuditFindingEntity(
                    auditId = auditId,
                    accountId = it.accountId,
                    handle = Handles.normalize(it.handle) ?: it.handle,
                    kind = it.kind,
                    detail = it.detail,
                )
            },
        )
    }

    override suspend fun finishAudit(auditId: Long, missingCount: Int, seenAfterCount: Int) =
        auditDao.finish(auditId, missingCount, seenAfterCount)

    override fun observeAudits(): Flow<List<Audit>> =
        auditDao.observeAudits().map { rows -> rows.map { it.toModel() } }

    override fun observeAudit(auditId: Long): Flow<Audit?> = auditDao.observeAudit(auditId).map { it?.toModel() }

    override fun observeFindings(auditId: Long): Flow<List<AuditFinding>> =
        auditDao.observeFindings(auditId).map { rows -> rows.map { it.toModel() } }

    override suspend fun blockedAccountsWithHandles(): List<Pair<Account, BlockEvent>> = db.withTransaction {
        val accounts = accountDao.withHandleAndStatus(
            listOf(AccountStatus.BLOCKED.name, AccountStatus.UNBLOCKED_SUSPECTED.name),
        )
        // allVerified() is newest first, so the first event per account is its latest verified block.
        val latestByAccount = blockEventDao.allVerified().groupBy { it.accountId }.mapValues { it.value.first() }
        accounts.mapNotNull { account ->
            latestByAccount[account.id]?.let { account.toModel() to it.toModel() }
        }
    }

    override suspend fun sightingsAfter(accountId: Long, afterMillis: Long): List<Sighting> =
        sightingDao.forAccountAfter(accountId, afterMillis).map { it.toModel() }
}
