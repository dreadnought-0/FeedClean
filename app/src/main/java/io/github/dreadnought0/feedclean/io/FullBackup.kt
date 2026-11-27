package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.db.FeedCleanDatabase
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full export and import: every table, as a zip of JSON + CSV. Streams come from the system
 * file picker (SAF); this class never chooses a location itself.
 *
 * @param appVersion written into the manifest.
 * @param clock time source for `exported_at` and for the importer's rate-limit window.
 */
class FullBackup(
    private val db: FeedCleanDatabase,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Writes a full export to [out] (not closed). Returns the row count per table. */
    suspend fun export(out: OutputStream): Map<String, Int> {
        val bundle = RoomSnapshotReader(db).read()
        val manifest = FullExportCodec.manifest(bundle, appVersion, clock())
        withContext(Dispatchers.IO) { FullExportCodec.write(manifest, bundle, out) }
        return bundle.counts
    }

    /**
     * Imports a full export from [input] (merging, idempotent; see [ImportPlanner]). Only database writes:
     * nothing happens on screen.
     *
     * @throws ImportFormatException when the file can't be imported; the database is then unchanged.
     */
    suspend fun import(input: InputStream): ImportReport {
        val file = withContext(Dispatchers.IO) { FullExportCodec.read(input) }
        val existing = RoomSnapshotReader(db).read()
        val plan = ImportPlanner.plan(existing, file.bundle, clock())
        return ImportApplier(db).apply(plan, file.manifest)
    }
}
