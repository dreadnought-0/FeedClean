package io.github.dreadnought0.feedclean.audit

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.data.AuditRepository
import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.io.openForRead
import io.github.dreadnought0.feedclean.io.openForWrite
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the audit screen is doing or last did. */
sealed interface AuditStatus {
    data object Idle : AuditStatus

    data object Running : AuditStatus

    /** The last run finished; [exportedCount] handles were in the export's block list. */
    data class Ran(val exportedCount: Int, val foundBlockList: Boolean) : AuditStatus

    data class Imported(val count: Int) : AuditStatus

    data object ReportSaved : AuditStatus

    data class Failed(val reason: AuditFailure) : AuditStatus
}

/** Why an audit action failed. */
enum class AuditFailure { CANNOT_OPEN_FILE, MALFORMED, NO_JSON, TOO_LARGE, UNEXPECTED }

/** The selected audit with its findings. */
data class AuditReport(val audit: Audit, val findings: List<AuditFinding>) {
    fun of(kind: AuditFindingKind): List<AuditFinding> = findings.filter { it.kind == kind }
}

/**
 * Unblock audit: pick TikTok's data export, run the diff, show/export the report, and import
 * blocks that FeedClean doesn't know about — only after the user confirms. Nothing here acts on TikTok.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuditViewModel(
    private val runner: AuditRunner,
    private val audits: AuditRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val _status = MutableStateFlow<AuditStatus>(AuditStatus.Idle)
    private val selectedId = MutableStateFlow<Long?>(null)

    /** Current status. */
    val status: StateFlow<AuditStatus> = _status.asStateFlow()

    /** Past audits, newest first. */
    val history: StateFlow<List<Audit>> = audits.observeAudits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The selected audit (default: the newest) with its findings. */
    val report: StateFlow<AuditReport?> = combine(selectedId, history) { id, list ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.flatMapLatest { audit ->
        if (audit == null) {
            flowOf(null)
        } else {
            audits.observeFindings(audit.id).let { findings ->
                combine(flowOf(audit), findings) { a, f -> AuditReport(a, f) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Shows a past audit. */
    fun select(auditId: Long) {
        selectedId.value = auditId
    }

    /** Runs the audit on the file at [uri] (from `OpenDocument`). */
    fun runAudit(uri: Uri) = launchAction {
        val name = withContext(Dispatchers.IO) { displayName(uri) }
        val scan = withContext(Dispatchers.IO) { resolver.openForRead(uri).use { DataExportReader.read(it) } }
        val result = runner.run(scan, name)
        selectedId.value = result.auditId
        AuditStatus.Ran(result.exportedCount, scan.found)
    }

    /** Imports the selected audit's IN_EXPORT_NOT_IN_DB findings, after the user confirmed. Database only. */
    fun importMissing() = launchAction {
        val current = report.value ?: return@launchAction AuditStatus.Idle
        AuditStatus.Imported(runner.importMissing(current.of(AuditFindingKind.IN_EXPORT_NOT_IN_DB)))
    }

    /** Saves the selected report as JSON to [uri] (from `CreateDocument("application/json")`). */
    fun exportJson(uri: Uri) = exportReport(uri) { AuditReportExport.json(it.audit, it.findings) }

    /** Saves the selected report as CSV to [uri] (from `CreateDocument("text/csv")`). */
    fun exportCsv(uri: Uri) = exportReport(uri) { AuditReportExport.csv(it.findings) }

    fun dismiss() {
        if (_status.value != AuditStatus.Running) _status.value = AuditStatus.Idle
    }

    private fun exportReport(uri: Uri, render: (AuditReport) -> String) = launchAction {
        val current = report.value ?: return@launchAction AuditStatus.Idle
        val text = render(current)
        withContext(Dispatchers.IO) { resolver.openForWrite(uri).use { it.write(text.toByteArray(Charsets.UTF_8)) } }
        AuditStatus.ReportSaved
    }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun launchAction(block: suspend () -> AuditStatus) {
        if (_status.value == AuditStatus.Running) return
        _status.value = AuditStatus.Running
        viewModelScope.launch {
            _status.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataExportException) {
                AuditStatus.Failed(
                    when (e.reason) {
                        DataExportException.Reason.MALFORMED -> AuditFailure.MALFORMED
                        DataExportException.Reason.NO_JSON -> AuditFailure.NO_JSON
                        DataExportException.Reason.TOO_LARGE -> AuditFailure.TOO_LARGE
                    },
                )
            } catch (_: IOException) {
                AuditStatus.Failed(AuditFailure.CANNOT_OPEN_FILE)
            } catch (_: SecurityException) {
                AuditStatus.Failed(AuditFailure.CANNOT_OPEN_FILE)
            } catch (_: Exception) {
                AuditStatus.Failed(AuditFailure.UNEXPECTED)
            }
        }
    }

    companion object {
        /** Factory wired to the app container. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuditViewModel(
                    runner = AuditRunner(
                        container.accountRepository,
                        container.auditRepository,
                        container.blockEventRepository,
                    ),
                    audits = container.auditRepository,
                    resolver = container.appContext.contentResolver,
                )
            }
        }
    }
}
