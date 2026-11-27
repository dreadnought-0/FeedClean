package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.detect.Signals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FullExportCodecTest {

    private val bundle = ExportBundle(
        accounts = listOf(
            AccountRow(id = 1, handle = "example_shop", handleSource = "PROFILE", displayName = "Example, \"Shop\"",
                displayNameSkeleton = "example shop", firstSeenAt = 10, lastSeenAt = 20, status = "BLOCKED", notes = "line1\nline2"),
            AccountRow(id = 2, handle = null, displayName = "=HYPERLINK(1)", firstSeenAt = 5, lastSeenAt = 6),
        ),
        tags = listOf(TagRow(1, "paid_ad", "SIGNAL")),
        accountTags = listOf(AccountTagRow(1, 1, "AUTO_DETECTED", 11)),
        sightings = listOf(
            SightingRow(id = 3, accountId = 1, seenAt = 12, pageSignature = "sig", signals = Signals(isPaidAd = true, disclosureLabel = "Ad"),
                source = "OVERLAY", userAction = "BLOCK", actionVerified = true, sessionId = 4),
        ),
        blockEvents = listOf(BlockEventRow(5, 1, 13, "ASSISTED", true)),
        actionLog = listOf(ActionLogRow(6, "BLOCK", 13, 1, true)),
        sessions = listOf(SessionRow(4, startedAt = 9, endedAt = 30, videosSeen = 3, ads = 1)),
        audits = listOf(AuditRow(7, 40, "DATA_EXPORT", "export.zip", 1, 0)),
        auditFindings = listOf(AuditFindingRow(8, 7, 1, "example_shop", "MISSING_FROM_BLOCKLIST", "blocked_at=1970-01-01T00:00:00.013Z")),
    )

    private fun zipBytes(manifest: ExportManifest = FullExportCodec.manifest(bundle, "0.1.0", 1_763_805_600_000)): ByteArray {
        val out = ByteArrayOutputStream()
        FullExportCodec.write(manifest, bundle, out)
        return out.toByteArray()
    }

    @Test
    fun roundTripsEveryTable() {
        val file = FullExportCodec.read(ByteArrayInputStream(zipBytes()))

        assertEquals(bundle, file.bundle)
        assertEquals(1, file.manifest.schemaVersion)
        assertEquals("0.1.0", file.manifest.appVersion)
        assertEquals("2025-11-22T10:00:00Z", file.manifest.exportedAt)
        assertEquals(2, file.manifest.counts[FullExportCodec.ACCOUNTS])
    }

    @Test
    fun zipHasManifestJsonAndCsvForEveryTable() {
        val names = ZipInputStream(ByteArrayInputStream(zipBytes())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
        val tables = listOf("accounts", "tags", "account_tags", "sightings", "block_events", "action_log", "sessions",
            "audits", "audit_findings")
        assertEquals(
            listOf("manifest.json") + tables.map { "json/$it.json" } + tables.map { "csv/$it.csv" },
            names,
        )
    }

    @Test
    fun csvEscapesAndNeutralizesFormulas() {
        val csv = checkNotNull(FullExportCodec.csv(bundle, FullExportCodec.ACCOUNTS))
        val lines = csv.split("\r\n")
        assertTrue(lines[0].startsWith("id,handle,handle_source,display_name,"))
        assertTrue(csv.contains("\"Example, \"\"Shop\"\"\""))
        assertTrue(csv.contains("\"line1\nline2\""))
        assertTrue(csv.contains(",'=HYPERLINK(1),"))
    }

    @Test
    fun rejectsNewerSchema() {
        val bytes = zipBytes(FullExportCodec.manifest(bundle, "9.0", 0).copy(schemaVersion = 2))
        expectFailure(ImportFormatException.Reason.NEWER_SCHEMA) { FullExportCodec.read(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun rejectsFilesThatAreNotFullExports() {
        expectFailure(ImportFormatException.Reason.NOT_A_FEEDCLEAN_EXPORT) {
            FullExportCodec.read(ByteArrayInputStream(zipOf("other.json" to "{}")))
        }
        expectFailure(ImportFormatException.Reason.NOT_A_FEEDCLEAN_EXPORT) {
            FullExportCodec.read(ByteArrayInputStream("not a zip".toByteArray()))
        }
        expectFailure(ImportFormatException.Reason.NOT_A_FEEDCLEAN_EXPORT) {
            FullExportCodec.read(ByteArrayInputStream(zipOf("manifest.json" to """{"schema_version":1,"format":"other"}""")))
        }
        expectFailure(ImportFormatException.Reason.DAMAGED) {
            FullExportCodec.read(ByteArrayInputStream(zipOf("manifest.json" to "{ nope")))
        }
        expectFailure(ImportFormatException.Reason.DAMAGED) {
            FullExportCodec.read(
                ByteArrayInputStream(
                    zipOf(
                        "manifest.json" to """{"schema_version":1,"format":"feedclean-full"}""",
                        "json/accounts.json" to """[{"id": "x"}]""",
                    ),
                ),
            )
        }
    }

    @Test
    fun readsRepackedZipAndTreatsMissingTablesAsEmpty() {
        val bytes = zipOf(
            "backup/manifest.json" to """{"schema_version":1,"format":"feedclean-full","future_field":true}""",
            "backup/json/tags.json" to """[{"id":1,"name":"paid_ad","kind":"SIGNAL","future":1}]""",
        )
        val file = FullExportCodec.read(ByteArrayInputStream(bytes))
        assertEquals(listOf(TagRow(1, "paid_ad", "SIGNAL")), file.bundle.tags)
        assertEquals(emptyList<AccountRow>(), file.bundle.accounts)
    }

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

    private fun expectFailure(reason: ImportFormatException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: ImportFormatException) {
            assertEquals(reason, e.reason)
        }
    }
}
