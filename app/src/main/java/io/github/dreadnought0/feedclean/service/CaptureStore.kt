package io.github.dreadnought0.feedclean.service

import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One saved capture. */
data class CaptureFile(val name: String, val sizeBytes: Long, val modifiedMillis: Long)

/**
 * In-app screen captures (redacted uiautomator-style XML) in app-private storage: `filesDir/captures/`.
 * Nothing here leaves the phone unless the user exports a file through the system picker. Pure JVM file I/O;
 * call off the main thread.
 *
 * @param dir the captures directory (created on first write).
 */
class CaptureStore(private val dir: File, private val zone: ZoneId = ZoneId.systemDefault()) {

    private val nameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(zone)

    /** Writes [xml] as `<timestamp>.xml` and returns the file. */
    fun write(xml: String, nowMillis: Long): File {
        dir.mkdirs()
        val base = nameFormat.format(Instant.ofEpochMilli(nowMillis))
        var file = File(dir, "$base$SUFFIX")
        var n = 1
        while (file.exists()) file = File(dir, "$base-${n++}$SUFFIX")
        file.writeText(xml, Charsets.UTF_8)
        return file
    }

    /** All captures, newest first. */
    fun list(): List<CaptureFile> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) } ?: emptyArray())
            .map { CaptureFile(it.name, it.length(), it.lastModified()) }
            .sortedWith(compareByDescending<CaptureFile> { it.modifiedMillis }.thenByDescending { it.name })

    /** Opens a capture by [name] for export, or null if it doesn't exist or the name isn't a plain capture name. */
    fun open(name: String): InputStream? = resolve(name)?.inputStream()

    /** Deletes a capture. Returns true if it was deleted. */
    fun delete(name: String): Boolean = resolve(name)?.delete() == true

    private fun resolve(name: String): File? {
        if (!SAFE_NAME.matches(name)) return null
        return File(dir, name).takeIf { it.isFile }
    }

    companion object {
        const val DIR_NAME = "captures"
        const val SUFFIX = ".xml"
        private val SAFE_NAME = Regex("^[A-Za-z0-9._-]+\\.xml$")

        /** The store under an app's `filesDir`. */
        fun inFilesDir(filesDir: File): CaptureStore = CaptureStore(File(filesDir, DIR_NAME))
    }
}
