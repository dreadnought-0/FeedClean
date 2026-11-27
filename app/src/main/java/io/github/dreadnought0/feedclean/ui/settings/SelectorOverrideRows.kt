package io.github.dreadnought0.feedclean.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** MIME types offered by the selector-file picker. */
private val SelectorFileMimeTypes = arrayOf("application/json", "*/*")

/**
 * Selector override controls (Settings → Detection): the current source, "Load selector file" (SAF
 * OpenDocument with a persisted read grant) and "Use built-in selectors".
 *
 * @param overrideUri current `selectorOverrideUri`, or null for the bundled selectors.
 * @param onOverrideSelected stores the picked document's URI string.
 * @param onUseBuiltIn clears the override.
 */
@Composable
fun SelectorOverrideRows(
    overrideUri: String?,
    onOverrideSelected: (String) -> Unit,
    onUseBuiltIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            overrideUri?.let { context.releaseReadGrant(it) }
            context.takeReadGrant(picked)
            onOverrideSelected(picked.toString())
        }
    }
    val displayName by produceState<String?>(initialValue = null, overrideUri) {
        value = overrideUri?.let { context.documentDisplayName(it) }
    }

    Column(modifier = modifier) {
        ValueSettingRow(
            title = stringResource(R.string.ui_settings_selectors_current),
            value = if (overrideUri == null) {
                stringResource(R.string.ui_settings_selectors_builtin)
            } else {
                stringResource(R.string.ui_settings_selectors_override, displayName ?: overrideUri)
            },
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_selectors_load),
            summary = stringResource(R.string.ui_settings_selectors_load_summary),
            onClick = { launcher.launch(SelectorFileMimeTypes) },
        )
        ActionSettingRow(
            title = stringResource(R.string.ui_settings_selectors_use_builtin),
            enabled = overrideUri != null,
            onClick = {
                overrideUri?.let { context.releaseReadGrant(it) }
                onUseBuiltIn()
            },
        )
    }
}

/** Keeps read access to [uri] across restarts. Some providers don't offer persistable grants; that's fine. */
private fun Context.takeReadGrant(uri: Uri) {
    try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Not persistable: the override still works until the grant lapses; the loader falls back to built-in.
    }
}

private fun Context.releaseReadGrant(uriString: String) {
    try {
        contentResolver.releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Grant was never persisted or is already gone.
    }
}

/** The document's display name, or its last path segment if the provider can't tell us. */
private suspend fun Context.documentDisplayName(uriString: String): String? = withContext(Dispatchers.IO) {
    val uri = Uri.parse(uriString)
    val queried = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    queried ?: uri.lastPathSegment
}
