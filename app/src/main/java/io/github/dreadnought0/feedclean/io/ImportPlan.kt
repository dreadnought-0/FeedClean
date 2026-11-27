package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.ActionKind
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.SkipReason

/** Where an incoming row lands: an existing row, or a row this import inserts. */
sealed interface Ref {
    /** The existing database row with this [id]. */
    data class Existing(val id: Long) : Ref

    /** A row inserted by this import; [incomingId] is its id in the file (remapped on insert). */
    data class New(val incomingId: Long) : Ref
}

/**
 * What an import will write, computed by [ImportPlanner] without touching the database. Foreign keys are
 * [Ref]s; the applier turns them into real ids.
 */
data class ImportPlan(
    /** Incoming account id → target. Accounts missing here were invalid; their children are skipped. */
    val accountTargets: Map<Long, Ref>,
    /** Incoming accounts that match an existing account: (existing id, incoming row) for a field merge. */
    val accountMerges: List<Pair<Long, AccountRow>>,
    /** Incoming accounts to insert. Handles are normalized; provisional rows keep `handle = null`. */
    val newAccounts: List<AccountRow>,
    /** (account, suspected original) links to set where the account has none yet. */
    val suspectedOriginals: List<Pair<Ref, Ref>>,
    /** Tags the database does not have yet (normalized names). */
    val newTags: List<Pair<String, TagKind>>,
    val tagAssignments: List<PlannedTagAssignment>,
    /** Incoming session id → target. */
    val sessionTargets: Map<Long, Ref>,
    val newSessions: List<SessionRow>,
    val sightings: List<PlannedSighting>,
    val blockEvents: List<PlannedBlockEvent>,
    val actionLog: List<PlannedAction>,
    val audits: List<PlannedAudit>,
    val skipped: ImportSkips,
)

/** A tag assignment to write. */
data class PlannedTagAssignment(val account: Ref, val tagName: String, val source: TagSource, val createdAt: Long)

/** A sighting to insert, with enums already validated. */
data class PlannedSighting(
    val row: SightingRow,
    val account: Ref,
    val session: Ref?,
    val source: SightingSource,
    val userAction: UserAction,
    val skipReason: SkipReason?,
)

/** A block event to insert. */
data class PlannedBlockEvent(val row: BlockEventRow, val account: Ref, val method: BlockMethod)

/** An action-log row to insert (always older than the rate-limit window). */
data class PlannedAction(val row: ActionLogRow, val account: Ref?, val kind: ActionKind)

/** An audit run to insert with its findings. */
data class PlannedAudit(val row: AuditRow, val source: AuditSource, val findings: List<PlannedFinding>)

/** An audit finding to insert. */
data class PlannedFinding(val row: AuditFindingRow, val account: Ref?, val kind: AuditFindingKind, val handle: String)

/** Rows the import leaves out, and why. */
data class ImportSkips(
    /** Same page signature and seen-at time as a sighting already in the database (or earlier in the file). */
    val duplicateSightings: Int = 0,
    /** Same account, time, method and outcome as an existing block event. */
    val duplicateBlockEvents: Int = 0,
    /** Same kind, time and outcome as an existing action-log row. */
    val duplicateActions: Int = 0,
    /** Action-log rows inside the rate limiter's window: never imported, so the live budget is untouched. */
    val actionsInRateLimitWindow: Int = 0,
    /** Audit runs already present (same time, source and file name), with their findings. */
    val duplicateAudits: Int = 0,
    /** Rows with unknown references or values. */
    val invalidRows: Int = 0,
)

/** Result of an import, for the UI. */
data class ImportReport(
    val exportAppVersion: String,
    val exportedAt: String,
    val accountsAdded: Int,
    /** Of [accountsAdded], how many are provisional (no handle). */
    val provisionalAccountsAdded: Int,
    /** Incoming accounts merged into an account that already existed (by handle, or a provisional match). */
    val accountsMatched: Int,
    val tagsAdded: Int,
    val tagAssignmentsAdded: Int,
    val sightingsAdded: Int,
    val blockEventsAdded: Int,
    val actionLogAdded: Int,
    val sessionsAdded: Int,
    val auditsAdded: Int,
    val auditFindingsAdded: Int,
    val skipped: ImportSkips,
) {
    /** True when nothing new was written (e.g. the same file imported twice). */
    val nothingNew: Boolean
        get() = accountsAdded == 0 && tagsAdded == 0 && tagAssignmentsAdded == 0 && sightingsAdded == 0 &&
            blockEventsAdded == 0 && actionLogAdded == 0 && sessionsAdded == 0 && auditsAdded == 0
}
