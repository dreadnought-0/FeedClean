package io.github.dreadnought0.feedclean.io

import io.github.dreadnought0.feedclean.data.model.Account
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.Tag
import io.github.dreadnought0.feedclean.data.model.TagAssignment
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublicExportTest {

    // 2026-10-03T23:30:00Z and 2026-11-22T10:00:00Z
    private val octoberLate = 1_791_070_200_000L
    private val now = 1_795_341_600_000L

    private fun account(
        id: Long,
        handle: String?,
        status: AccountStatus = AccountStatus.SEEN,
        firstSeen: Long = octoberLate,
    ) = Account(
        id = id,
        handle = handle,
        handleSource = handle?.let { HandleSource.PROFILE },
        displayName = "Private Display Name $id",
        displayNameSkeleton = "private display name $id",
        firstSeenAt = firstSeen,
        lastSeenAt = firstSeen + 1_000,
        status = status,
        accountCategory = "Shop",
        shopTier = "gold",
        isDefaultHandle = false,
        notes = "my private note about $id",
        suspectedOriginalOf = null,
        profilePhotoHash = 42L,
    )

    private fun tag(name: String, source: TagSource = TagSource.AUTO_DETECTED) = TagAssignment(
        tag = Tag(id = name.hashCode().toLong(), name = name, kind = if (source == TagSource.MANUAL) TagKind.MANUAL else TagKind.SIGNAL),
        source = source,
        createdAt = 123_456_789L,
    )

    @Test
    fun keepsOnlyHandleTagsMonthAndEvidence() {
        val file = PublicExport.build(
            listOf(account(1, "example_shop") to listOf(tag(TagNames.SHOP_ANCHOR), tag(TagNames.ORGANIC_SHOP), tag(TagNames.PAID_AD, TagSource.MANUAL))),
            includeSubjectiveTags = false,
            nowMillis = now,
            appVersion = "0.1.0",
        )

        assertEquals("2026-11", file.generated)
        assertEquals(1, file.schemaVersion)
        assertEquals(
            listOf(
                PublicAccount(
                    handle = "example_shop",
                    tags = listOf("organic_shop", "paid_ad", "shop_anchor"),
                    firstReported = "2026-10",
                    evidenceType = listOf("manual_report", "shop_anchor"),
                ),
            ),
            file.accounts,
        )

        val json = PublicExport.encode(file)
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals(setOf("schema_version", "generated", "app_version", "accounts"), root.keys)
        val entry = root["accounts"]!!.jsonArray.single() as JsonObject
        assertEquals(setOf("handle", "tags", "first_reported", "evidence_type"), entry.keys)
        listOf("Private Display Name", "private note", "Shop\"", "gold", "123456789", "1791070200000", "42").forEach {
            assertFalse("leaked $it", json.contains(it))
        }
    }

    @Test
    fun dropsProvisionalFalsePositiveAndUntaggedAccounts() {
        val file = PublicExport.build(
            listOf(
                account(1, null) to listOf(tag(TagNames.PAID_AD)),
                account(2, "fp_account", AccountStatus.FALSE_POSITIVE) to listOf(tag(TagNames.PAID_AD)),
                account(3, "no_tags") to emptyList(),
                account(4, "only_custom") to listOf(tag("my_private_label", TagSource.MANUAL)),
                account(5, "blocked_one", AccountStatus.BLOCKED) to listOf(tag(TagNames.PROMOTED)),
            ),
            includeSubjectiveTags = true,
            nowMillis = now,
            appVersion = "0.1.0",
        )
        assertEquals(listOf("blocked_one"), file.accounts.map { it.handle })
        assertEquals(listOf("promoted_label"), file.accounts.single().evidenceType)
    }

    @Test
    fun subjectiveTagsOnlyWhenOptedIn() {
        val input = listOf(
            account(1, "creator") to listOf(
                tag(TagNames.PAID_AD),
                tag(TagNames.AI_GENERATED, TagSource.MANUAL),
                tag(TagNames.SEXUAL_CONTENT, TagSource.MANUAL),
            ),
            account(2, "only_subjective") to listOf(tag(TagNames.BOT_LIKE, TagSource.MANUAL)),
        )

        val off = PublicExport.build(input, includeSubjectiveTags = false, nowMillis = now, appVersion = "v")
        assertEquals(listOf("creator"), off.accounts.map { it.handle })
        assertEquals(listOf("paid_ad"), off.accounts.single().tags)
        assertEquals(listOf("ad_label"), off.accounts.single().evidenceType)

        val on = PublicExport.build(input, includeSubjectiveTags = true, nowMillis = now, appVersion = "v")
        assertEquals(listOf("creator", "only_subjective"), on.accounts.map { it.handle })
        assertEquals(listOf("ai_generated", "paid_ad", "sexual_content"), on.accounts[0].tags)
        assertEquals(listOf("ad_label", "manual_report"), on.accounts[0].evidenceType)
    }

    @Test
    fun evidenceVocabularyForEveryObjectiveTag() {
        val file = PublicExport.build(
            listOf(
                account(1, "a") to listOf(tag(TagNames.PAID_AD)),
                account(2, "b") to listOf(tag(TagNames.PROMOTED)),
                account(3, "c") to listOf(tag(TagNames.SHOP_ANCHOR)),
                account(4, "d") to listOf(tag(TagNames.ORGANIC_SHOP)),
                account(5, "e") to listOf(tag(TagNames.LIVE_SHOPPING)),
            ),
            includeSubjectiveTags = false,
            nowMillis = now,
            appVersion = "v",
        )
        assertEquals(
            listOf("ad_label", "promoted_label", "shop_anchor", "shop_creator", "live_shopping_label"),
            file.accounts.map { it.evidenceType.single() },
        )
    }

    @Test
    fun monthIsUtcAndSortedByHandle() {
        // 2026-10-31T23:30:00Z stays October whatever the device time zone.
        assertEquals("2026-10", PublicExport.month(1_793_489_400_000L))
        val file = PublicExport.build(
            listOf(account(1, "zeta") to listOf(tag(TagNames.PAID_AD)), account(2, "alpha") to listOf(tag(TagNames.PAID_AD))),
            includeSubjectiveTags = false,
            nowMillis = now,
            appVersion = "v",
        )
        assertEquals(listOf("alpha", "zeta"), file.accounts.map { it.handle })
    }
}
