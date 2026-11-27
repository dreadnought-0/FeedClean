package io.github.dreadnought0.feedclean.io

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dreadnought0.feedclean.AppContainer
import io.github.dreadnought0.feedclean.settings.SettingsRepository
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Data & privacy transfer section is doing or last did. */
sealed interface TransferStatus {
    data object Idle : TransferStatus

    data class Working(val operation: TransferOperation) : TransferStatus

    data class FullExported(val counts: Map<String, Int>) : TransferStatus

    data class PublicExported(val accountCount: Int) : TransferStatus

    data class Imported(val report: ImportReport) : TransferStatus

    data object Cleared : TransferStatus

    data class Failed(val operation: TransferOperation, val reason: FailureReason) : TransferStatus
}

/** The four actions of the section. */
enum class TransferOperation { EXPORT_FULL, EXPORT_PUBLIC, IMPORT, CLEAR }

/** Why an action failed (shown as a friendly message). */
enum class FailureReason { CANNOT_OPEN_FILE, NOT_A_FEEDCLEAN_EXPORT, NEWER_SCHEMA, DAMAGED, TOO_LARGE, UNEXPECTED }

/**
 * Full export, public export, import, and Clear all data (Settings → Data & privacy). Every file goes
 * through a URI the user picked in the system file picker. Nothing here acts on TikTok.
 */
class DataTransferViewModel(
    private val backup: FullBackup,
    private val publicExporter: PublicExporter,
    private val settings: SettingsRepository,
    private val clearAll: suspend () -> Unit,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val _status = MutableStateFlow<TransferStatus>(TransferStatus.Idle)

    /** Current status; one action runs at a time. */
    val status: StateFlow<TransferStatus> = _status.asStateFlow()

    /** The "Public export: include subjective tags" setting. */
    val includeSubjectiveTags: StateFlow<Boolean> = settings.settings
        .map { it.publicExportIncludeSubjectiveTags }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.settings.value.publicExportIncludeSubjectiveTags)

    fun setIncludeSubjectiveTags(include: Boolean) {
        viewModelScope.launch { settings.update { it.copy(publicExportIncludeSubjectiveTags = include) } }
    }

    /** Writes the full export to [uri] (from `CreateDocument("application/zip")`). */
    fun exportFull(uri: Uri) = launchOperation(TransferOperation.EXPORT_FULL) {
        val counts = withOutput(uri) { backup.export(it) }
        TransferStatus.FullExported(counts)
    }

    /** Writes the public export to [uri] (from `CreateDocument("application/json")`). */
    fun exportPublic(uri: Uri) = launchOperation(TransferOperation.EXPORT_PUBLIC) {
        val include = settings.settings.value.publicExportIncludeSubjectiveTags
        TransferStatus.PublicExported(withOutput(uri) { publicExporter.export(it, include) })
    }

    /** Imports the full export at [uri] (from `OpenDocument`). Database writes only. */
    fun import(uri: Uri) = launchOperation(TransferOperation.IMPORT) {
        val report = withContext(Dispatchers.IO) { resolver.openForRead(uri).use { backup.import(it) } }
        TransferStatus.Imported(report)
    }

    /** Settings → Clear all data, after the user confirmed. Settings and the rate-limit budget are kept. */
    fun clearAllData() = launchOperation(TransferOperation.CLEAR) {
        clearAll()
        TransferStatus.Cleared
    }

    /** Hides the last result. */
    fun dismiss() {
        if (_status.value !is TransferStatus.Working) _status.value = TransferStatus.Idle
    }

    /** Opens, writes and closes the picked document off the main thread. */
    private suspend fun <T> withOutput(uri: Uri, write: suspend (OutputStream) -> T): T =
        withContext(Dispatchers.IO) { resolver.openForWrite(uri).use { write(it) } }

    private fun launchOperation(operation: TransferOperation, block: suspend () -> TransferStatus) {
        if (_status.value is TransferStatus.Working) return
        _status.value = TransferStatus.Working(operation)
        viewModelScope.launch {
            _status.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportFormatException) {
                TransferStatus.Failed(operation, e.reason.toFailure())
            } catch (_: IOException) {
                TransferStatus.Failed(operation, FailureReason.CANNOT_OPEN_FILE)
            } catch (_: SecurityException) {
                TransferStatus.Failed(operation, FailureReason.CANNOT_OPEN_FILE)
            } catch (_: Exception) {
                TransferStatus.Failed(operation, FailureReason.UNEXPECTED)
            }
        }
    }

    private fun ImportFormatException.Reason.toFailure(): FailureReason = when (this) {
        ImportFormatException.Reason.NOT_A_FEEDCLEAN_EXPORT -> FailureReason.NOT_A_FEEDCLEAN_EXPORT
        ImportFormatException.Reason.NEWER_SCHEMA -> FailureReason.NEWER_SCHEMA
        ImportFormatException.Reason.DAMAGED -> FailureReason.DAMAGED
        ImportFormatException.Reason.TOO_LARGE -> FailureReason.TOO_LARGE
    }

    companion object {
        /** Factory wired to the app container. */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val version = appVersionName(container.appContext)
                DataTransferViewModel(
                    backup = FullBackup(container.dataLayer.database, version),
                    publicExporter = PublicExporter(container.accountRepository, version),
                    settings = container.settingsRepository,
                    clearAll = { container.dataLayer.clearAllData() },
                    resolver = container.appContext.contentResolver,
                )
            }
        }
    }
}
