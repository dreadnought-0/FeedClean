package io.github.dreadnought0.feedclean.ui.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.dreadnought0.feedclean.ui.common.CardHeader
import io.github.dreadnought0.feedclean.ui.common.GlassButton
import io.github.dreadnought0.feedclean.ui.common.GlassDialog
import io.github.dreadnought0.feedclean.ui.common.OptionRow
import io.github.dreadnought0.feedclean.ui.common.PrimaryButton
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import androidx.compose.foundation.layout.PaddingValues
import io.github.dreadnought0.feedclean.ui.common.GlassCard
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.io.DataTransferViewModel
import io.github.dreadnought0.feedclean.io.FailureReason
import io.github.dreadnought0.feedclean.io.FullExportCodec
import io.github.dreadnought0.feedclean.io.ImportReport
import io.github.dreadnought0.feedclean.io.TransferStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Settings → Data & privacy transfer actions: full export, public export (with the
 * "include subjective tags" setting), import with its report, and Clear all data behind a confirmation.
 * Every file goes through the system file picker.
 *
 * Scrolls itself: give it the screen's content area (e.g. `Modifier.fillMaxSize().padding(innerPadding)`) and
 * do not wrap it in another vertical scroll container.
 */
@Composable
fun DataTransferContent(modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel: DataTransferViewModel = viewModel(factory = DataTransferViewModel.factory(container))
    val status by viewModel.status.collectAsStateWithLifecycle()
    val includeSubjective by viewModel.includeSubjectiveTags.collectAsStateWithLifecycle()

    val exportFull = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_ZIP)) { uri ->
        uri?.let(viewModel::exportFull)
    }
    val exportPublic = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
        uri?.let(viewModel::exportPublic)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    DataTransferBody(
        status = status,
        includeSubjectiveTags = includeSubjective,
        onIncludeSubjectiveTags = viewModel::setIncludeSubjectiveTags,
        onExportFull = { exportFull.launch("feedclean-export-${LocalDate.now()}.zip") },
        onExportPublic = {
            exportPublic.launch("feedclean-public-${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))}.json")
        },
        onImport = { import.launch(IMPORT_MIME_TYPES) },
        onClearConfirmed = viewModel::clearAllData,
        onDismissStatus = viewModel::dismiss,
        modifier = modifier,
    )
}

/** Stateless body of [DataTransferContent]. */
@Composable
internal fun DataTransferBody(
    status: TransferStatus,
    includeSubjectiveTags: Boolean,
    onIncludeSubjectiveTags: (Boolean) -> Unit,
    onExportFull: () -> Unit,
    onExportPublic: () -> Unit,
    onImport: () -> Unit,
    onClearConfirmed: () -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val busy = status is TransferStatus.Working

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(status, onDismissStatus)

        Section(FeedCleanIcons.Archive, R.string.io_full_title, R.string.io_full_body) {
            PrimaryButton(stringResource(R.string.io_export_full), onExportFull, icon = FeedCleanIcons.Download, enabled = !busy)
        }

        Section(Icons.Filled.Person, R.string.io_public_title, R.string.io_public_body) {
            OptionRow(
                title = stringResource(R.string.io_public_subjective),
                description = stringResource(R.string.io_public_subjective_body),
                checked = includeSubjectiveTags,
                onCheckedChange = onIncludeSubjectiveTags,
            )
            PrimaryButton(stringResource(R.string.io_export_public), onExportPublic, icon = FeedCleanIcons.Upload, enabled = !busy)
        }

        Section(FeedCleanIcons.Download, R.string.io_import_title, R.string.io_import_body) {
            GlassButton(stringResource(R.string.io_import), onImport, icon = FeedCleanIcons.Upload, enabled = !busy)
        }

        Section(Icons.Filled.Delete, R.string.io_clear_title, R.string.io_clear_body) {
            GlassButton(stringResource(R.string.io_clear), { confirmClear = true }, icon = Icons.Filled.Delete, destructive = true, enabled = !busy)
        }
    }

    if (confirmClear) {
        GlassDialog(
            title = stringResource(R.string.io_clear_confirm_title),
            text = stringResource(R.string.io_clear_confirm_body),
            confirm = stringResource(R.string.io_clear_confirm),
            onConfirm = {
                confirmClear = false
                onClearConfirmed()
            },
            dismiss = stringResource(R.string.io_cancel),
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun Section(icon: ImageVector, titleRes: Int, bodyRes: Int, actions: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(stringResource(titleRes), icon = icon)
            Text(
                stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            actions()
        }
    }
}

@Composable
private fun StatusCard(status: TransferStatus, onDismiss: () -> Unit) {
    if (status == TransferStatus.Idle) return
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (status) {
                TransferStatus.Idle -> Unit
                is TransferStatus.Working -> {
                    Text(stringResource(R.string.io_working), style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is TransferStatus.FullExported -> Text(
                    stringResource(
                        R.string.io_full_exported,
                        status.counts[FullExportCodec.ACCOUNTS] ?: 0,
                        status.counts[FullExportCodec.SIGHTINGS] ?: 0,
                    ),
                )
                is TransferStatus.PublicExported -> Text(
                    pluralStringResource(R.plurals.io_public_exported, status.accountCount, status.accountCount),
                )
                is TransferStatus.Imported -> ImportReportLines(status.report)
                TransferStatus.Cleared -> Text(stringResource(R.string.io_cleared))
                is TransferStatus.Failed -> Text(
                    stringResource(failureMessage(status.reason)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (status !is TransferStatus.Working) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.io_ok))
                }
            }
        }
    }
}

@Composable
private fun ImportReportLines(report: ImportReport) {
    Text(stringResource(R.string.io_import_report_title), style = MaterialTheme.typography.titleMedium)
    if (report.exportAppVersion.isNotBlank() || report.exportedAt.isNotBlank()) {
        Text(
            stringResource(R.string.io_import_source, report.exportAppVersion, report.exportedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (report.nothingNew) Text(stringResource(R.string.io_import_nothing_new))
    val skipped = report.skipped
    val lines = buildList {
        if (report.accountsAdded > 0) {
            add(stringResource(R.string.io_import_accounts_added, report.accountsAdded, report.provisionalAccountsAdded))
        }
        if (report.accountsMatched > 0) add(stringResource(R.string.io_import_accounts_matched, report.accountsMatched))
        if (report.sightingsAdded > 0) add(stringResource(R.string.io_import_sightings_added, report.sightingsAdded))
        if (report.blockEventsAdded > 0) add(stringResource(R.string.io_import_blocks_added, report.blockEventsAdded))
        if (report.tagsAdded > 0 || report.tagAssignmentsAdded > 0) {
            add(stringResource(R.string.io_import_tags_added, report.tagsAdded, report.tagAssignmentsAdded))
        }
        if (report.sessionsAdded > 0) add(stringResource(R.string.io_import_sessions_added, report.sessionsAdded))
        if (report.auditsAdded > 0) {
            add(stringResource(R.string.io_import_audits_added, report.auditsAdded, report.auditFindingsAdded))
        }
        if (report.actionLogAdded > 0) add(stringResource(R.string.io_import_actions_added, report.actionLogAdded))
        if (skipped.duplicateSightings > 0 || skipped.duplicateBlockEvents > 0 || skipped.duplicateAudits > 0) {
            add(
                stringResource(
                    R.string.io_import_duplicates,
                    skipped.duplicateSightings,
                    skipped.duplicateBlockEvents,
                    skipped.duplicateAudits,
                ),
            )
        }
        if (skipped.actionsInRateLimitWindow > 0) {
            add(stringResource(R.string.io_import_actions_window, skipped.actionsInRateLimitWindow))
        }
        if (skipped.invalidRows > 0) add(stringResource(R.string.io_import_invalid, skipped.invalidRows))
    }
    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

private fun failureMessage(reason: FailureReason): Int = when (reason) {
    FailureReason.CANNOT_OPEN_FILE -> R.string.io_fail_open
    FailureReason.NOT_A_FEEDCLEAN_EXPORT -> R.string.io_fail_not_export
    FailureReason.NEWER_SCHEMA -> R.string.io_fail_newer
    FailureReason.DAMAGED -> R.string.io_fail_damaged
    FailureReason.TOO_LARGE -> R.string.io_fail_too_large
    FailureReason.UNEXPECTED -> R.string.io_fail_unexpected
}

private const val MIME_ZIP = "application/zip"
private const val MIME_JSON = "application/json"

/** Zip types file providers report for a full export. */
private val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
