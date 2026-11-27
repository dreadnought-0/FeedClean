package io.github.dreadnought0.feedclean.io

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Opens a document the user picked in the system file picker for writing, truncating it where the provider
 * supports that (`"wt"`), otherwise with plain `"w"` (documents from `CreateDocument` start empty anyway).
 *
 * @throws IOException when the provider gives no stream.
 */
internal fun ContentResolver.openForWrite(uri: Uri): OutputStream {
    val truncating = try {
        openOutputStream(uri, "wt")
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    }
    return truncating ?: openOutputStream(uri, "w") ?: throw IOException("no output stream")
}

/** Opens a document the user picked for reading. @throws IOException when the provider gives no stream. */
internal fun ContentResolver.openForRead(uri: Uri): InputStream =
    openInputStream(uri) ?: throw IOException("no input stream")

/** The app's `versionName` (written into exports), or "unknown". */
fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        ?: "unknown"
} catch (_: PackageManager.NameNotFoundException) {
    "unknown"
}
