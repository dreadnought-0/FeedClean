package io.github.dreadnought0.feedclean.ui.audit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.audit.AuditDetail
import io.github.dreadnought0.feedclean.audit.AuditFailure
import io.github.dreadnought0.feedclean.audit.AuditReport
import io.github.dreadnought0.feedclean.audit.AuditStatus
import io.github.dreadnought0.feedclean.audit.AuditViewModel
import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Unblock audit screen content: pick TikTok's data export, run the audit, read the
 * screenshot-friendly report (counts and a list per finding kind), record blocks that are only in TikTok's
 * list (after a confirmation; database only), export the report, and browse past audits.
 *
 * A lazy list that scrolls itself: give it the screen's content area (e.g.
 * `Modifier.fillMaxSize().padding(innerPadding)`) and do not wrap it in another vertical scroll container.
 */
@Composable
fun AuditContent(modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel: AuditViewModel = viewModel(factory = AuditViewModel.factory(container))
    val status by viewModel.status.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::runAudit)
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportJson)
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }

    AuditBody(
        status = status,
        report = report,
        history = history,
        onPick = { pick.launch(EXPORT_MIME_TYPES) },
        onImportConfirmed = viewModel::importMissing,
        onExportJson = { report?.let { saveJson.launch("feedclean-audit-${fileDate(it.audit.runAt)}.json") } },
        onExportCsv = { report?.let { saveCsv.launch("feedclean-audit-${fileDate(it.audit.runAt)}.csv") } },
        onSelect = viewModel::select,
        onDismissStatus = viewModel::dismiss,
        modifier = modifier,
    )
}

/** Stateless body of [AuditContent]. */
@Composable
internal fun AuditBody(
    status: AuditStatus,
    report: AuditReport?,
    history: List<Audit>,
    onPick: () -> Unit,
    onImportConfirmed: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onSelect: (Long) -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    val busy = status == AuditStatus.Running

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.audit_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPick, enabled = !busy) { Text(stringResource(R.string.audit_pick)) }
            }
        }
        item { StatusCard(status, onDismissStatus) }
        if (report != null) {
            reportItems(report, busy, onImportRequested = { confirmImport = true }, onExportJson, onExportCsv)
        }
        if (history.size > 1) {
            item {
                Text(
                    stringResource(R.string.audit_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(history, key = { it.id }) { audit ->
                val selected = audit.id == report?.audit?.id
                Text(
                    stringResource(
                        R.string.audit_history_row,
                        dateTime(audit.runAt),
                        audit.missingFromBlocklistCount,
                        audit.seenAfterBlockCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(audit.id) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.audit_import_confirm_title)) },
            text = { Text(stringResource(R.string.audit_import_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmImport = false
                        onImportConfirmed()
                    },
                ) { Text(stringResource(R.string.audit_import_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(stringResource(R.string.io_cancel)) } },
        )
    }
}

private fun LazyListScope.reportItems(
    report: AuditReport,
    busy: Boolean,
    onImportRequested: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val missing = report.of(AuditFindingKind.MISSING_FROM_BLOCKLIST)
    val seenAfter = report.of(AuditFindingKind.SEEN_AFTER_BLOCK)
    val inExport = report.of(AuditFindingKind.IN_EXPORT_NOT_IN_DB)

    item(key = "header") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HorizontalDivider()
            Text(
                stringResource(R.string.audit_report_title, dateTime(report.audit.runAt)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            report.audit.exportFileName?.let {
                Text(
                    stringResource(R.string.audit_file, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CountBox(missing.size, R.string.audit_count_missing, Modifier.weight(1f))
                CountBox(seenAfter.size, R.string.audit_count_seen_after, Modifier.weight(1f))
                CountBox(inExport.size, R.string.audit_count_in_export, Modifier.weight(1f))
            }
        }
    }
    findingSection("missing", R.string.audit_kind_missing, R.string.audit_kind_missing_body, missing)
    findingSection("seen", R.string.audit_kind_seen_after, R.string.audit_kind_seen_after_body, seenAfter)
    findingSection("export", R.string.audit_kind_in_export, R.string.audit_kind_in_export_body, inExport)
    if (inExport.isNotEmpty()) {
        item(key = "import") {
            Button(onClick = onImportRequested, enabled = !busy) {
                Text(pluralStringResource(R.plurals.audit_import_missing, inExport.size, inExport.size))
            }
        }
    }
    item(key = "export") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportJson, enabled = !busy) { Text(stringResource(R.string.audit_export_json)) }
            OutlinedButton(onClick = onExportCsv, enabled = !busy) { Text(stringResource(R.string.audit_export_csv)) }
        }
    }
}

private fun LazyListScope.findingSection(
    key: String,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    findings: List<AuditFinding>,
) {
    item(key = "$key-title") {
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${stringResource(titleRes)} (${findings.size})", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (findings.isEmpty()) Text(stringResource(R.string.audit_none), style = MaterialTheme.typography.bodyMedium)
        }
    }
    items(findings, key = { "$key-${it.id}-${it.handle}" }) { finding -> FindingRow(finding) }
}

@Composable
private fun FindingRow(finding: AuditFinding) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.audit_handle, finding.handle), style = MaterialTheme.typography.bodyLarge)
        val detail = detailText(AuditDetail.parse(finding.detail))
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun detailText(detail: AuditDetail): String = buildList {
    detail.blockedAtMillis?.let { add(stringResource(R.string.audit_detail_blocked, date(it))) }
    if (detail.newerThanExport) add(stringResource(R.string.audit_detail_newer))
    detail.sightingsAfter?.let { count ->
        add(stringResource(R.string.audit_detail_seen, count, detail.lastSeenAtMillis?.let(::date).orEmpty()))
    }
    detail.exportDateMillis?.let { add(stringResource(R.string.audit_detail_export_date, date(it))) }
    if (detail.knownAccount) add(stringResource(R.string.audit_detail_known))
}.joinToString(" · ")

@Composable
private fun CountBox(count: Int, @StringRes labelRes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusCard(status: AuditStatus, onDismiss: () -> Unit) {
    if (status == AuditStatus.Idle) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (status) {
                AuditStatus.Idle -> Unit
                AuditStatus.Running -> {
                    Text(stringResource(R.string.audit_running))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is AuditStatus.Ran -> Text(
                    if (status.foundBlockList) {
                        pluralStringResource(R.plurals.audit_ran, status.exportedCount, status.exportedCount)
                    } else {
                        stringResource(R.string.audit_no_block_list)
                    },
                )
                is AuditStatus.Imported -> Text(pluralStringResource(R.plurals.audit_imported, status.count, status.count))
                AuditStatus.ReportSaved -> Text(stringResource(R.string.audit_saved))
                is AuditStatus.Failed -> Text(stringResource(failureMessage(status.reason)), color = MaterialTheme.colorScheme.error)
            }
            if (status != AuditStatus.Running) {
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.io_ok)) }
            }
        }
    }
}

@StringRes
private fun failureMessage(reason: AuditFailure): Int = when (reason) {
    AuditFailure.CANNOT_OPEN_FILE -> R.string.audit_fail_open
    AuditFailure.MALFORMED -> R.string.audit_fail_malformed
    AuditFailure.NO_JSON -> R.string.audit_fail_no_json
    AuditFailure.TOO_LARGE -> R.string.audit_fail_too_large
    AuditFailure.UNEXPECTED -> R.string.audit_fail_unexpected
}

private fun date(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

private fun dateTime(millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

private fun fileDate(millis: Long): String =
    DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))

/** Types file providers report for TikTok's export (.zip or .json). */
private val EXPORT_MIME_TYPES = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/json",
    "text/json",
    "text/plain",
    "application/octet-stream",
)
