package io.github.dreadnought0.feedclean.audit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DataExportReaderTest {

    private val blockJson = """{ "App Settings": { "Block List": { "BlockList": [ { "UserName": "zip_user" } ] } } }"""

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun readsPlainJson() {
        val scan = DataExportReader.read(ByteArrayInputStream(blockJson.toByteArray()))
        assertEquals(listOf(ExportedBlock("zip_user", null)), scan.entries)
    }

    @Test
    fun readsEveryJsonInZipAndSkipsOtherFiles() {
        val bytes = zipOf(
            "TikTok_Data/readme.txt" to "BlockList: not json",
            "TikTok_Data/user_data_tiktok.json" to blockJson,
            "TikTok_Data/extra.JSON" to """{ "Blocked": [ { "username": "second_user" } ] }""",
            "TikTok_Data/broken.json" to "{ nope",
        )
        val scan = DataExportReader.read(ByteArrayInputStream(bytes))
        assertTrue(scan.found)
        assertEquals(listOf("second_user", "zip_user"), scan.entries.map { it.handle })
    }

    @Test
    fun failures() {
        expect(DataExportException.Reason.NO_JSON) {
            DataExportReader.read(ByteArrayInputStream(zipOf("user_data.txt" to "Block List")))
        }
        expect(DataExportException.Reason.MALFORMED) {
            DataExportReader.read(ByteArrayInputStream(zipOf("a.json" to "{ nope")))
        }
        expect(DataExportException.Reason.MALFORMED) {
            DataExportReader.read(ByteArrayInputStream("not json at all".toByteArray()))
        }
        expect(DataExportException.Reason.TOO_LARGE) {
            DataExportReader.read(ByteArrayInputStream(zipOf("big.json" to blockJson.padEnd(5_000))), maxBytes = 1_000)
        }
    }

    private fun expect(reason: DataExportException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: DataExportException) {
            assertEquals(reason, e.reason)
        }
    }
}
