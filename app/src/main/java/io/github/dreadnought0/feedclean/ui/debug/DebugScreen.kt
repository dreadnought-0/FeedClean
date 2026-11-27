package io.github.dreadnought0.feedclean.ui.debug

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.appContainer
import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.FeedContext
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.service.CaptureFile
import io.github.dreadnought0.feedclean.service.CaptureStore
import io.github.dreadnought0.feedclean.service.DecisionLogStore
import io.github.dreadnought0.feedclean.service.ScreenStateHolder
import io.github.dreadnought0.feedclean.ui.common.FeedCleanTopAppBar
import io.github.dreadnought0.feedclean.ui.common.StatusPill
import io.github.dreadnought0.feedclean.ui.theme.FeedCleanTheme
import io.github.dreadnought0.feedclean.ui.theme.MonospaceBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Debug destination: live view of [ScreenStateHolder], selector status, automation state, the decision log, and captures. */
@Composable
fun DebugRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val serviceConnected by ScreenStateHolder.serviceConnected.collectAsStateWithLifecycle()
    val tiktokForeground by ScreenStateHolder.tiktokForeground.collectAsStateWithLifecycle()
    val snapshot by ScreenStateHolder.snapshot.collectAsStateWithLifecycle()
    val autoSkip by ScreenStateHolder.autoSkip.collectAsStateWithLifecycle()
    val action by ScreenStateHolder.action.collectAsStateWithLifecycle()
    val events by ScreenStateHolder.recentEvents.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val config by container.selectorProvider.config.collectAsStateWithLifecycle()
    val source by container.selectorProvider.source.collectAsStateWithLifecycle()
    val loadError by container.selectorProvider.loadError.collectAsStateWithLifecycle()

    val store = remember(context) { CaptureStore.inFilesDir(context.filesDir) }
    var captures by remember { mutableStateOf(emptyList<CaptureFile>()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exportedText = stringResource(R.string.service_debug_export_done, pendingExport.orEmpty())
    val exportFailedText = stringResource(R.string.service_debug_export_failed)

    LaunchedEffect(refreshKey, action) { captures = withContext(Dispatchers.IO) { store.list() } }

    // ── Decision log (files written by the service; read, exported and cleared here) ──
    val logStore = remember(context) { DecisionLogStore.inFilesDir(context.filesDir) }
    var logData by remember { mutableStateOf<DecisionLogData?>(null) }
    var logLoaded by remember { mutableStateOf(false) }
    var logKey by remember { mutableIntStateOf(0) }
    var logStatus by remember { mutableStateOf<String?>(null) }
    val logExportedText = stringResource(R.string.debug_log_exported)
    val logExportFailedText = stringResource(R.string.debug_log_export_failed)
    val logClearedText = stringResource(R.string.debug_log_cleared)

    LaunchedEffect(logKey, events.firstOrNull()) {
        logData = withContext(Dispatchers.IO) { DecisionLogData.load(logStore) }
        logLoaded = true
    }

    val logExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DECISION_LOG_MIME)) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output -> logStore.exportTo(output) } != null
                }.getOrDefault(false)
            }
            logStatus = if (ok) logExportedText else logExportFailedText
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri: Uri? ->
        val name = pendingExport
        if (uri == null || name == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    store.open(name)?.use { input ->
                        context.contentResolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)
            }
            exportStatus = if (ok) exportedText else exportFailedText
        }
    }

    val runtime = DebugRuntimeInfo(
        selectorSource = source,
        selectorLoadError = loadError,
        selectorWarnings = config?.warnings.orEmpty(),
        verifiedAppVersions = config?.verifiedAppVersions.orEmpty(),
        selectorsLoaded = config != null,
        curateFeedEnabled = settings.curateFeedEnabled,
        killSwitchEngaged = settings.killSwitchEngaged,
        autoSkipContexts = FeedContext.entries.filter { settings.autoSkipAllowedIn(it) },
        decisionLogEnabled = settings.decisionLogEnabled,
        autoSkip = autoSkip,
        action = action,
        recentEvents = events,
    )

    val logUi = if (logLoaded) DecisionLogUiState(settings.decisionLogEnabled, logData, logStatus) else null

    DebugScreen(
        serviceConnected = serviceConnected,
        tiktokForeground = tiktokForeground,
        snapshot = snapshot,
        onBack = onBack,
        modifier = modifier,
        runtime = runtime,
        captures = captures,
        exportStatus = exportStatus,
        onRefreshCaptures = { refreshKey++ },
        onExportCapture = { name ->
            pendingExport = name
            exporter.launch(name)
        },
        onDeleteCapture = { name ->
            scope.launch {
                withContext(Dispatchers.IO) { store.delete(name) }
                refreshKey++
            }
        },
        decisionLog = logUi,
        onRefreshDecisionLog = {
            logStatus = null
            logKey++
        },
        onExportDecisionLog = { logExporter.launch("feedclean-decision-log-${LocalDate.now()}${DecisionLogStore.SUFFIX}") },
        onClearDecisionLog = {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { logStore.clear() } }
                logStatus = logClearedText
                logKey++
            }
        },
    )
}

/** SAF MIME type for the exported .jsonl (octet-stream keeps the file name exactly as given). */
private const val DECISION_LOG_MIME = "application/octet-stream"

/** What the Debug screen read from the decision-log files. Loaded off the main thread. */
data class DecisionLogData(val summary: DecisionLogSummary, val entryCount: Int, val fileCount: Int, val totalBytes: Long) {
    companion object {
        /** Prunes (retention + cap), then reads everything. Null when the files can't be read. */
        fun load(store: DecisionLogStore, today: LocalDate = LocalDate.now()): DecisionLogData? = runCatching {
            store.prune(today)
            val entries = store.readAll()
            val files = store.files()
            DecisionLogData(DecisionLogSummary.of(entries, today), entries.size, files.size, files.sumOf { it.sizeBytes })
        }.getOrNull()
    }
}

/** The Debug screen's "Decision log" section state. [data] null = the files couldn't be read. */
data class DecisionLogUiState(val enabled: Boolean, val data: DecisionLogData?, val status: String? = null)

/**
 * Debug screen: status pills, runtime state (selectors, auto-skip, last actions), the captures list with
 * export (system file picker) and delete, and a monospace, selectable dump of the latest snapshot. "Copy" puts
 * the runtime + snapshot dump on the clipboard, flagged as sensitive so the system clipboard preview hides it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(
    serviceConnected: Boolean,
    tiktokForeground: Boolean,
    snapshot: ScreenSnapshot?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    runtime: DebugRuntimeInfo? = null,
    captures: List<CaptureFile> = emptyList(),
    exportStatus: String? = null,
    onRefreshCaptures: () -> Unit = {},
    onExportCapture: (String) -> Unit = {},
    onDeleteCapture: (String) -> Unit = {},
    decisionLog: DecisionLogUiState? = null,
    onRefreshDecisionLog: () -> Unit = {},
    onExportDecisionLog: () -> Unit = {},
    onClearDecisionLog: () -> Unit = {},
) {
    val summary = remember(serviceConnected, tiktokForeground, snapshot) {
        DebugFormatter.format(serviceConnected, tiktokForeground, snapshot)
    }
    val runtimeText = remember(runtime) { runtime?.let { DebugRuntimeFormatter.format(it) } }
    val logCounts = remember(decisionLog?.data) { decisionLog?.data?.summary?.let(DecisionLogFormatter::formatCounts) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.ui_debug_clip_label)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FeedCleanTopAppBar(
                title = stringResource(R.string.ui_debug_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = {
                            val text = listOfNotNull(runtimeText, logCounts?.let { "decisionLog:\n$it\n" }, summary).joinToString("\n")
                            val clip = ClipData.newPlainText(clipLabel, text).apply {
                                description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            scope.launch { clipboard.setClipEntry(ClipEntry(clip)) }
                        },
                    ) { Text(stringResource(R.string.ui_debug_copy)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = stringResource(
                        if (serviceConnected) R.string.ui_status_service_connected else R.string.ui_status_service_disconnected,
                    ),
                    ok = serviceConnected,
                )
                StatusPill(
                    text = stringResource(
                        if (tiktokForeground) R.string.ui_status_target_foreground else R.string.ui_status_target_background,
                    ),
                    ok = tiktokForeground,
                )
                StatusPill(
                    text = stringResource(R.string.ui_debug_snapshot_type, DebugFormatter.typeName(snapshot)),
                    ok = snapshot != null,
                )
                if (runtime != null) {
                    StatusPill(
                        text = stringResource(
                            when {
                                runtime.autoSkip.pausedByLoopGuard -> R.string.service_debug_autoskip_paused
                                runtime.autoSkipArmed -> R.string.service_debug_autoskip_armed
                                else -> R.string.service_debug_autoskip_off
                            },
                        ),
                        ok = runtime.autoSkipArmed && !runtime.autoSkip.pausedByLoopGuard,
                    )
                    val selectorsOk = runtime.selectorsLoaded && runtime.selectorLoadError == null
                    StatusPill(
                        text = stringResource(
                            if (selectorsOk) R.string.service_debug_selectors_ok else R.string.service_debug_selectors_problem,
                        ),
                        ok = selectorsOk,
                    )
                }
            }
            Text(
                text = stringResource(R.string.ui_debug_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (runtimeText != null) {
                SectionTitle(stringResource(R.string.service_debug_section_runtime))
                MonospaceBlock(runtimeText)
            }
            if (decisionLog != null) {
                DecisionLogSection(decisionLog, logCounts, onRefreshDecisionLog, onExportDecisionLog, onClearDecisionLog)
            }
            if (runtime != null) {
                CapturesSection(captures, exportStatus, onRefreshCaptures, onExportCapture, onDeleteCapture)
            }
            SectionTitle(stringResource(R.string.service_debug_section_snapshot))
            MonospaceBlock(summary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun MonospaceBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MonospaceBody,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun CapturesSection(
    captures: List<CaptureFile>,
    exportStatus: String?,
    onRefresh: () -> Unit,
    onExport: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.service_debug_section_captures),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRefresh) { Text(stringResource(R.string.service_debug_captures_refresh)) }
    }
    Text(
        text = stringResource(R.string.service_debug_captures_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (exportStatus != null) Text(exportStatus, style = MaterialTheme.typography.bodySmall)
    if (captures.isEmpty()) {
        Text(stringResource(R.string.service_debug_captures_empty), style = MaterialTheme.typography.bodyMedium)
    }
    captures.forEach { capture ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.service_debug_capture_size, capture.name, ((capture.sizeBytes + 1023) / 1024).toInt()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onExport(capture.name) }) { Text(stringResource(R.string.service_debug_capture_export)) }
            TextButton(onClick = { onDelete(capture.name) }) { Text(stringResource(R.string.service_debug_capture_delete)) }
        }
    }
}

/** Counts by context × gate (today, 7 days), the newest entries, and Export / Clear. */
@Composable
private fun DecisionLogSection(
    state: DecisionLogUiState,
    counts: String?,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val data = state.data
    val recent = remember(data) { data?.summary?.recent?.let(DecisionLogFormatter::formatRecent) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.debug_log_section_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRefresh) { Text(stringResource(R.string.debug_log_refresh)) }
    }
    StatusPill(
        text = stringResource(if (state.enabled) R.string.debug_log_on else R.string.debug_log_off),
        ok = state.enabled,
    )
    Text(
        text = stringResource(R.string.debug_log_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.status != null) Text(state.status, style = MaterialTheme.typography.bodySmall)
    when {
        data == null -> Text(stringResource(R.string.debug_log_unreadable), style = MaterialTheme.typography.bodyMedium)
        data.entryCount == 0 -> Text(stringResource(R.string.debug_log_empty), style = MaterialTheme.typography.bodyMedium)
        else -> {
            Text(
                text = stringResource(R.string.debug_log_size, data.entryCount, data.fileCount, ((data.totalBytes + 1023) / 1024).toInt()),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.debug_log_counts), style = MaterialTheme.typography.labelLarge)
            if (counts != null) MonospaceBlock(counts)
            Text(
                stringResource(R.string.debug_log_recent, data.summary.recent.size),
                style = MaterialTheme.typography.labelLarge,
            )
            if (recent != null) MonospaceBlock(recent)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onExport, enabled = (data?.entryCount ?: 0) > 0) { Text(stringResource(R.string.debug_log_export)) }
        TextButton(onClick = { confirmClear = true }, enabled = (data?.fileCount ?: 0) > 0) {
            Text(stringResource(R.string.debug_log_clear))
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.debug_log_clear_title)) },
            text = { Text(stringResource(R.string.debug_log_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text(stringResource(R.string.debug_log_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.debug_log_clear_cancel)) }
            },
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun DebugScreenPreview() {
    FeedCleanTheme(dynamicColor = false) {
        DebugScreen(
            serviceConnected = true,
            tiktokForeground = true,
            snapshot = ScreenSnapshot.FeedVideo(
                capturedAtMillis = 0L,
                matchedStrategies = mapOf(
                    "feed.displayName" to "viewId=title",
                    "feed.shopAnchor" to "descEquals=Product description",
                ),
                displayName = "Creator Name",
                probableHandle = "creator.handle",
                caption = "Caption text",
                soundDescription = "original sound",
                signals = Signals(hasShopAnchor = true, anchorText = "Product · 1k sold"),
                pageSignature = "a1b2c3",
                isForYouTab = true,
                pageBounds = Bounds(0, 0, 1080, 2520),
            ),
            onBack = {},
            runtime = DebugRuntimeInfo(selectorsLoaded = true, curateFeedEnabled = true),
            captures = listOf(CaptureFile("20261122-120000-000.xml", 40_000, 0L)),
        )
    }
}
