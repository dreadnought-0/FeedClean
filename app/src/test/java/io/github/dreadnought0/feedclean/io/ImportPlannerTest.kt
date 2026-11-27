package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.db.entity.AccountEntity
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_795_341_600_000L

    private fun acc(id: Long, handle: String?, name: String? = "Name $id", firstSeen: Long = 100, status: String = "SEEN") =
        AccountRow(id = id, handle = handle, handleSource = handle?.let { "PROFILE" }, displayName = name,
            displayNameSkeleton = name?.lowercase(), firstSeenAt = firstSeen, lastSeenAt = firstSeen + 10, status = status)

    private fun sighting(id: Long, accountId: Long, sig: String, at: Long, sessionId: Long? = null) =
        SightingRow(id = id, accountId = accountId, seenAt = at, pageSignature = sig, source = "OVERLAY", sessionId = sessionId)

    private val existing = ExportBundle(
        accounts = listOf(
            acc(1, "known_shop", "Known Shop"),
            acc(2, null, "Feed Only", firstSeen = 500),
            acc(3, null, null),
            acc(4, "merged_since"),
        ),
        tags = listOf(TagRow(1, "paid_ad", "SIGNAL"), TagRow(2, "ai_generated", "MANUAL")),
        accountTags = listOf(AccountTagRow(1, 1, "AUTO_DETECTED", 1)),
        sightings = listOf(
            sighting(1, 1, "sig-a", 1_000),
            sighting(2, 4, "sig-m", 3_000),
        ),
        blockEvents = listOf(BlockEventRow(1, 1, 2_000, "ASSISTED", true)),
        actionLog = listOf(ActionLogRow(1, "BLOCK", 2_000, 1, true)),
        sessions = listOf(SessionRow(1, startedAt = 900)),
        audits = listOf(AuditRow(1, 5_000, "DATA_EXPORT", "old.zip")),
    )

    @Test
    fun mergesByHandleAndRemapsForeignKeys() {
        val incoming = ExportBundle(
            accounts = listOf(acc(10, "Known_Shop", "Known Shop"), acc(11, "brand_new")),
            tags = listOf(TagRow(7, "paid_ad", "SIGNAL"), TagRow(8, "Custom Tag", "MANUAL")),
            accountTags = listOf(AccountTagRow(10, 7, "AUTO_DETECTED", 1), AccountTagRow(11, 8, "MANUAL", 2)),
            sightings = listOf(sighting(20, 10, "sig-b", 1_500, sessionId = 30), sighting(21, 11, "sig-c", 1_600)),
            blockEvents = listOf(BlockEventRow(40, 11, 1_700, "ASSISTED", true)),
            sessions = listOf(SessionRow(30, startedAt = 1_400)),
        )

        val plan = ImportPlanner.plan(existing, incoming, now)

        assertEquals(Ref.Existing(1), plan.accountTargets[10])
        assertEquals(Ref.New(11), plan.accountTargets[11])
        assertEquals(listOf(1L to incoming.accounts[0].copy(handle = "known_shop")), plan.accountMerges)
        assertEquals(listOf("brand_new"), plan.newAccounts.map { it.handle })
        // paid_ad on known_shop already exists; the custom tag is new and normalized.
        assertEquals(listOf("custom_tag" to TagKind.MANUAL), plan.newTags)
        assertEquals(
            listOf(PlannedTagAssignment(Ref.New(11), "custom_tag", TagSource.MANUAL, 2)),
            plan.tagAssignments,
        )
        assertEquals(listOf(Ref.Existing(1), Ref.New(11)), plan.sightings.map { it.account })
        assertEquals(Ref.New(30), plan.sightings[0].session)
        assertEquals(listOf(Ref.New(11)), plan.blockEvents.map { it.account })
    }

    @Test
    fun provisionalAccountsStayProvisional() {
        val incoming = ExportBundle(
            accounts = listOf(
                acc(10, null, "Feed Only", firstSeen = 500), // same row exported earlier → existing 2
                acc(11, null, "Feed Only", firstSeen = 777), // same name, different row → new provisional
                acc(12, null, null), // unknown-creator bucket → existing 3
                acc(13, null, "Was Merged"), // all its sightings now belong to account 4
                acc(14, null, "Known Shop"), // a name match alone never merges into a handle account
            ),
            sightings = listOf(sighting(20, 13, "sig-m", 3_000)),
        )

        val plan = ImportPlanner.plan(existing, incoming, now)

        assertEquals(Ref.Existing(2), plan.accountTargets[10])
        assertEquals(Ref.New(11), plan.accountTargets[11])
        assertEquals(Ref.Existing(3), plan.accountTargets[12])
        assertEquals(Ref.Existing(4), plan.accountTargets[13])
        assertEquals(Ref.New(14), plan.accountTargets[14])
        assertTrue(plan.newAccounts.all { it.handle == null })
        assertEquals(listOf(11L, 14L), plan.newAccounts.map { it.id })
    }

    @Test
    fun skipsDuplicatesAndKeepsTheRateLimitWindowUntouched() {
        val incoming = ExportBundle(
            accounts = listOf(acc(10, "known_shop")),
            sightings = listOf(
                sighting(20, 10, "sig-a", 1_000), // already in the database
                sighting(21, 10, "sig-new", 1_100),
                sighting(22, 10, "sig-new", 1_100), // duplicate inside the file
            ),
            blockEvents = listOf(
                BlockEventRow(40, 10, 2_000, "ASSISTED", true), // already in the database
                BlockEventRow(41, 10, 2_000, "ASSISTED", false), // different outcome → new
            ),
            actionLog = listOf(
                ActionLogRow(50, "BLOCK", 2_000, 10, true), // duplicate
                ActionLogRow(51, "NOT_INTERESTED", 2_500, 10, true), // old history → added
                ActionLogRow(52, "BLOCK", now - hour / 2, 10, true), // inside the window → never imported
                ActionLogRow(53, "BLOCK", now + hour, 10, true), // future → never imported
            ),
            sessions = listOf(SessionRow(30, startedAt = 900)), // same start → existing
            audits = listOf(AuditRow(60, 5_000, "DATA_EXPORT", "old.zip"), AuditRow(61, 6_000, "DATA_EXPORT", "new.zip")),
            auditFindings = listOf(
                AuditFindingRow(70, 60, 10, "known_shop", "MISSING_FROM_BLOCKLIST"),
                AuditFindingRow(71, 61, 10, "@Known_Shop", "SEEN_AFTER_BLOCK"),
                AuditFindingRow(72, 61, 99, "stranger", "IN_EXPORT_NOT_IN_DB"),
            ),
        )

        val plan = ImportPlanner.plan(existing, incoming, now)

        assertEquals(listOf(21L), plan.sightings.map { it.row.id })
        assertEquals(2, plan.skipped.duplicateSightings)
        assertEquals(listOf(41L), plan.blockEvents.map { it.row.id })
        assertEquals(1, plan.skipped.duplicateBlockEvents)
        assertEquals(listOf(51L), plan.actionLog.map { it.row.id })
        assertEquals(Ref.Existing(1), plan.actionLog.single().account)
        assertEquals(1, plan.skipped.duplicateActions)
        assertEquals(2, plan.skipped.actionsInRateLimitWindow)
        assertEquals(emptyList<SessionRow>(), plan.newSessions)
        assertEquals(Ref.Existing(1), plan.sessionTargets[30])
        assertEquals(listOf(61L), plan.audits.map { it.row.id })
        assertEquals(1, plan.skipped.duplicateAudits)
        val findings = plan.audits.single().findings
        assertEquals(listOf("known_shop", "stranger"), findings.map { it.handle })
        assertEquals(Ref.Existing(1), findings[0].account)
        assertNull(findings[1].account)
    }

    @Test
    fun importingTheDatabaseIntoItselfAddsNothing() {
        val plan = ImportPlanner.plan(existing, existing, now)

        assertEquals(emptyList<AccountRow>(), plan.newAccounts)
        assertEquals(emptyList<Pair<String, TagKind>>(), plan.newTags)
        assertEquals(emptyList<PlannedTagAssignment>(), plan.tagAssignments)
        assertEquals(emptyList<PlannedSighting>(), plan.sightings)
        assertEquals(emptyList<PlannedBlockEvent>(), plan.blockEvents)
        assertEquals(emptyList<PlannedAction>(), plan.actionLog)
        assertEquals(emptyList<SessionRow>(), plan.newSessions)
        assertEquals(emptyList<PlannedAudit>(), plan.audits)
        assertEquals(0, plan.skipped.invalidRows)
    }

    @Test
    fun invalidReferencesAndValuesAreSkipped() {
        val incoming = ExportBundle(
            accounts = listOf(acc(10, "ok")),
            sightings = listOf(sighting(20, 999, "orphan", 1), sighting(21, 10, "bad", 2).copy(source = "FROM_THE_FUTURE")),
            blockEvents = listOf(BlockEventRow(40, 10, 1, "TELEPATHY", true)),
            actionLog = listOf(ActionLogRow(50, "LIKE", 1, null, true)),
            accountTags = listOf(AccountTagRow(10, 77, "MANUAL", 1)),
        )

        val plan = ImportPlanner.plan(existing, incoming, now)

        assertEquals(emptyList<PlannedSighting>(), plan.sightings)
        assertEquals(emptyList<PlannedBlockEvent>(), plan.blockEvents)
        assertEquals(emptyList<PlannedAction>(), plan.actionLog)
        assertEquals(5, plan.skipped.invalidRows)
    }

    @Test
    fun suspectedOriginalIsRemapped() {
        val incoming = ExportBundle(
            accounts = listOf(acc(10, "known_shop"), acc(11, "copycat_shop").copy(suspectedOriginalOf = 10)),
        )
        val plan = ImportPlanner.plan(existing, incoming, now)
        assertEquals(listOf(Ref.New(11) to Ref.Existing(1)), plan.suspectedOriginals)
    }

    @Test
    fun accountFieldMergeIsConservativeAndIdempotent() {
        val target = AccountEntity(
            id = 1, handle = "known_shop", handleSource = HandleSource.DIALOG, displayName = null,
            displayNameSkeleton = null, firstSeenAt = 100, lastSeenAt = 200, status = AccountStatus.SEEN,
            notes = "mine",
        )
        val incoming = acc(10, "known_shop", "Known Shop", firstSeen = 50, status = "BLOCKED").copy(
            lastSeenAt = 300, notes = "theirs", accountCategory = "Shop",
        )

        val once = ImportPlanner.mergeImportedAccount(target, incoming)
        val twice = ImportPlanner.mergeImportedAccount(once, incoming)

        assertEquals(once, twice)
        assertEquals(HandleSource.DIALOG, once.handleSource)
        assertEquals("Known Shop", once.displayName)
        assertEquals(50L, once.firstSeenAt)
        assertEquals(300L, once.lastSeenAt)
        assertEquals(AccountStatus.BLOCKED, once.status)
        assertEquals("mine\ntheirs", once.notes)
        assertEquals("Shop", once.accountCategory)
        assertEquals(
            AccountStatus.FALSE_POSITIVE,
            ImportPlanner.mergeImportedAccount(target.copy(status = AccountStatus.FALSE_POSITIVE), incoming).status,
        )
    }
}
