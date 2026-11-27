package io.github.dreadnought0.feedclean.data.model

/** One unblock-audit run. */
data class Audit(
    val id: Long,
    val runAt: Long,
    val source: AuditSource,
    val exportFileName: String?,
    val missingFromBlocklistCount: Int,
    val seenAfterBlockCount: Int,
)

/** Persisted by name; never rename entries. */
enum class AuditSource { DATA_EXPORT, MANUAL }

/**
 * One finding of an audit. When passed to `AuditRepository.addFindings`, [id] and [auditId] are ignored
 * (the row gets a new id and the audit id given to that call).
 */
data class AuditFinding(
    val id: Long = 0,
    val auditId: Long = 0,
    /** Matching account, or null when the handle is not in the database (e.g. [AuditFindingKind.IN_EXPORT_NOT_IN_DB]). */
    val accountId: Long?,
    /** Lowercase, without "@". */
    val handle: String,
    val kind: AuditFindingKind,
    val detail: String? = null,
)

/** Persisted by name; never rename entries. */
enum class AuditFindingKind {
    /** Blocked by us (verified) but absent from TikTok's exported block list. */
    MISSING_FROM_BLOCKLIST,

    /** A sighting with a matching handle after the verified block. */
    SEEN_AFTER_BLOCK,

    /** In TikTok's block list but unknown to FeedClean (can be imported as method=IMPORTED). */
    IN_EXPORT_NOT_IN_DB,
}
