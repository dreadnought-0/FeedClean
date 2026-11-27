package io.github.dreadnought0.feedclean.audit

import io.github.dreadnought0.feedclean.data.model.Audit
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuditReportExportTest {

    private val audit = Audit(id = 5, runAt = 1_767_225_600_000L, source = AuditSource.DATA_EXPORT,
        exportFileName = "export.zip", missingFromBlocklistCount = 1, seenAfterBlockCount = 0)
    private val findings = listOf(
        AuditFinding(id = 1, auditId = 5, accountId = 77, handle = "gone_user", kind = AuditFindingKind.MISSING_FROM_BLOCKLIST,
            detail = "blocked_at=2025-12-01T00:00:00Z"),
        AuditFinding(id = 2, auditId = 5, accountId = null, handle = "tiktok_only", kind = AuditFindingKind.IN_EXPORT_NOT_IN_DB),
    )

    @Test
    fun jsonHasCountsAndFindingsWithoutInternalIds() {
        val text = AuditReportExport.json(audit, findings)
        val root = Json.parseToJsonElement(text).jsonObject

        assertEquals("feedclean-audit", root["format"]!!.jsonPrimitive.content)
        assertEquals("2026-01-01T00:00:00Z", root["run_at"]!!.jsonPrimitive.content)
        val counts = root["counts"]!!.jsonObject
        assertEquals(1, counts["missing_from_blocklist"]!!.jsonPrimitive.int)
        assertEquals(0, counts["seen_after_block"]!!.jsonPrimitive.int)
        assertEquals(1, counts["in_export_not_in_db"]!!.jsonPrimitive.int)
        assertEquals(setOf("handle", "kind", "detail"), root["findings"]!!.jsonArray[0].jsonObject.keys)
        assertFalse(text.contains("77"))
    }

    @Test
    fun csvHasOneRowPerFinding() {
        assertEquals(
            "handle,kind,detail\r\n" +
                "gone_user,missing_from_blocklist,blocked_at=2025-12-01T00:00:00Z\r\n" +
                "tiktok_only,in_export_not_in_db,\r\n",
            AuditReportExport.csv(findings),
        )
    }
}
