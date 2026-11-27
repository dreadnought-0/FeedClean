package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.AccountRepository
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes the public export from the repositories. See [PublicExport] for what is included.
 *
 * @param clock time source for `generated`.
 */
class PublicExporter(
    private val accounts: AccountRepository,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Builds the export (handle-keyed accounts only; provisional ones are never read). */
    suspend fun build(includeSubjectiveTags: Boolean): PublicExportFile {
        val withHandles = accounts.observeAccounts().first().map { it.account }.filter { it.handle != null }
        val input = withHandles.map { it to accounts.observeTags(it.id).first() }
        return PublicExport.build(input, includeSubjectiveTags, clock(), appVersion)
    }

    /** Writes the export to [out] (not closed) and returns the number of accounts in it. */
    suspend fun export(out: OutputStream, includeSubjectiveTags: Boolean): Int {
        val file = build(includeSubjectiveTags)
        withContext(Dispatchers.IO) {
            out.write(PublicExport.encode(file).toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return file.accounts.size
    }
}
