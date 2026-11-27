package io.github.dreadnought0.feedclean.audit

import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.io.Csv
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** JSON form of an audit report (docs/EXPORT_SCHEMA.md, "Audit report"). */
@Serializable
data class AuditReportFile(
    @SerialName("schema_version") val schemaVersion: Int = AuditReportExport.SCHEMA_VERSION,
    val format: String = AuditReportExport.FORMAT,
    /** ISO-8601 UTC. */
    @SerialName("run_at") val runAt: String,
    val source: String,
    @SerialName("export_file_name") val exportFileName: String?,
    val counts: Map<String, Int>,
    val findings: List<AuditReportFinding>,
)

/** One finding in the report file. */
@Serializable
data class AuditReportFinding(val handle: String, val kind: String, val detail: String?)

/**
 * Report export of one audit (JSON or CSV), saved through the system file picker. Pure. Contains handles,
 * finding kinds and the locale-neutral details only (no internal ids, notes or display names).
 */
object AuditReportExport {
    const val SCHEMA_VERSION: Int = 1
    const val FORMAT: String = "feedclean-audit"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** Lowercase kind names used in the files, e.g. `missing_from_blocklist`. */
    fun kindName(kind: AuditFindingKind): String = kind.name.lowercase()

    fun json(audit: Audit, findings: List<AuditFinding>): String = json.encodeToString(
        AuditReportFile.serializer(),
        AuditReportFile(
            runAt = Instant.ofEpochMilli(audit.runAt).toString(),
            source = audit.source.name.lowercase(),
            exportFileName = audit.exportFileName,
            counts = AuditFindingKind.entries.associate { kindName(it) to findings.count { f -> f.kind == it } },
            findings = findings.map { AuditReportFinding(it.handle, kindName(it.kind), it.detail) },
        ),
    )

    fun csv(findings: List<AuditFinding>): String = Csv.write(
        header = listOf("handle", "kind", "detail"),
        rows = findings.map { listOf(it.handle, kindName(it.kind), it.detail) },
    )
}
