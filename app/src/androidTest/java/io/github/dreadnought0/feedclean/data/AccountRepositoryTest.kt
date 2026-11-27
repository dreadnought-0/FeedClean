package io.github.dreadnought0.feedclean.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.AccountSummary
import io.github.dreadnought0.feedclean.data.model.AuditFinding
import io.github.dreadnought0.feedclean.data.model.AuditFindingKind
import io.github.dreadnought0.feedclean.data.model.AuditSource
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagKind
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.TimelineEntry
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest : DbTest() {

    @Test
    fun seedTagsExistWithKinds() = runTest {
        assertEquals(TagNames.DEFAULTS.map { it.first }.sorted(), accounts.observeAllTagNames().first())
        val kinds = accounts.observeAllTags().first().associate { it.name to it.kind }
        TagNames.DEFAULTS.forEach { (name, kind) -> assertEquals(name, kind, kinds[name]) }
    }

    @Test
    fun feedUpsertReusesProvisionalAccountBySkeleton() = runTest {
        val a = accounts.upsertFromFeed("Shop Seller", null, 1_000)
        val b = accounts.upsertFromFeed("  shop seller ", null, 2_000)
        assertEquals(a, b)
        val account = checkNotNull(accounts.getAccount(a))
        assertTrue(account.isProvisional)
        assertEquals("Shop Seller", account.displayName)
        assertEquals(1_000L, account.firstSeenAt)
        assertEquals(2_000L, account.lastSeenAt)

        assertNotEquals(a, accounts.upsertFromFeed("Other Seller", null, 3_000))
    }

    @Test
    fun probableHandleIsNeverStoredButMatchesAnExistingHandle() = runTest {
        val provisional = accounts.upsertFromFeed("Seller", "seller.real", 1_000)
        assertNull(checkNotNull(accounts.getAccount(provisional)).handle)
        assertNull(accounts.findByHandle("seller.real"))

        val real = accounts.ensureHandleAccount("@Seller.Real", HandleSource.PROFILE, null, 500)
        assertEquals(real, accounts.upsertFromFeed("Seller", "seller.real", 2_000))
        val account = checkNotNull(accounts.getAccount(real))
        assertEquals(2_000L, account.lastSeenAt)
        assertEquals("Seller", account.displayName)
        assertEquals(real, accounts.findByHandle("@SELLER.REAL")?.id)
    }

    @Test
    fun blankDisplayNameUsesOneUnknownBucketThatIsNeverMerged() = runTest {
        val a = accounts.upsertFromFeed(null, null, 1)
        assertEquals(a, accounts.upsertFromFeed("   ", null, 2))

        val learned = accounts.learnHandle(a, "someone", HandleSource.PROFILE)
        assertNotEquals(a, learned)
        assertNotNull(accounts.getAccount(a))
        assertEquals("someone", accounts.getAccount(learned)?.handle)
    }

    @Test
    fun learnHandleSetsTheHandleOnAProvisionalAccount() = runTest {
        val provisional = accounts.upsertFromFeed("Default Guy", null, 1)
        val id = accounts.learnHandle(provisional, "@User1234567890", HandleSource.PROFILE)
        assertEquals(provisional, id)
        val account = checkNotNull(accounts.getAccount(id))
        assertEquals("user1234567890", account.handle)
        assertEquals(HandleSource.PROFILE, account.handleSource)
        assertTrue(account.isDefaultHandle)

        // Handle-keyed accounts are never matched by display name alone (impersonators copy names).
        assertNotEquals(id, accounts.upsertFromFeed("Default Guy", null, 2))
    }

    @Test
    fun learnHandleMergesProvisionalIntoExistingHandleAccount() = runTest {
        val real = accounts.ensureHandleAccount("seller.real", HandleSource.PROFILE, "Seller", 100)
        accounts.addTag(real, TagNames.REPOSTED, TagSource.MANUAL, 100)
        sightings.recordSighting(real, "sig-old", Signals(isPaidAd = true), SightingSource.OVERLAY, nowMillis = 150)
        blockEvents.record(real, 160, BlockMethod.ASSISTED, verified = true)

        val provisional = accounts.upsertFromFeed("Seller", null, 1_000)
        assertNotEquals(real, provisional)
        sightings.recordSighting(provisional, "sig-1", Signals(hasShopAnchor = true), SightingSource.OVERLAY, nowMillis = 1_000)
        sightings.recordSighting(provisional, "sig-2", Signals(hasShopAnchor = true), SightingSource.OVERLAY, nowMillis = 2_000)
        accounts.addTag(provisional, "AI generated", TagSource.MANUAL, 1_500)
        accounts.addTag(provisional, TagNames.REPOSTED, TagSource.MANUAL, 1_600)
        actionLog.record(ActionKind.NOT_INTERESTED, 1_800, provisional, verified = true)
        blockEvents.record(provisional, 1_900, BlockMethod.ASSISTED, verified = false, failureReason = "mismatch", stepReached = "CHECK_DIALOG")
        val auditId = audits.createAudit(2_100, AuditSource.MANUAL, null)
        audits.addFindings(
            auditId,
            listOf(AuditFinding(accountId = provisional, handle = "seller.real", kind = AuditFindingKind.SEEN_AFTER_BLOCK)),
        )
        accounts.setNotes(provisional, "copycat?")

        val survivor = accounts.learnHandle(provisional, "Seller.Real", HandleSource.DIALOG)

        assertEquals(real, survivor)
        assertNull(accounts.getAccount(provisional))
        val timeline = accounts.observeTimeline(real).first()
        assertEquals(3, timeline.count { it is TimelineEntry.SightingEntry })
        assertEquals(2, timeline.count { it is TimelineEntry.BlockEntry })
        assertEquals(timeline.sortedByDescending { it.atMillis }, timeline)
        val tags = accounts.observeTags(real).first()
        assertEquals(
            setOf(TagNames.AI_GENERATED, TagNames.REPOSTED, TagNames.SHOP_ANCHOR, TagNames.ORGANIC_SHOP, TagNames.PAID_AD),
            tags.map { it.tag.name }.toSet(),
        )
        assertEquals(1, tags.count { it.tag.name == TagNames.REPOSTED })
        val merged = checkNotNull(accounts.getAccount(real))
        assertEquals("seller.real", merged.handle)
        assertEquals(AccountStatus.BLOCKED, merged.status)
        assertEquals(100L, merged.firstSeenAt)
        assertEquals(2_000L, merged.lastSeenAt)
        assertEquals("copycat?", merged.notes)
        assertEquals(real, audits.observeFindings(auditId).first().single().accountId)
        assertEquals(1, actionLog.countSince(0))
        assertEquals(3, accounts.observeAccounts().first().single().sightingCount)
    }

    @Test
    fun learnHandleNeverOverwritesAConfirmedHandle() = runTest {
        val first = accounts.ensureHandleAccount("first", HandleSource.PROFILE, "Name", 1)
        val second = accounts.learnHandle(first, "second", HandleSource.PROFILE)
        assertNotEquals(first, second)
        assertEquals("first", accounts.getAccount(first)?.handle)
        assertEquals("second", accounts.getAccount(second)?.handle)
        assertEquals(first, accounts.learnHandle(first, "@FIRST", HandleSource.DIALOG))
    }

    @Test
    fun observeAccountsSearchesAndFilters() = runTest {
        val shop = accounts.ensureHandleAccount("best_deals.shop", HandleSource.PROFILE, "Best Deals", 1)
        val ad = accounts.ensureHandleAccount("brandx", HandleSource.PROFILE, "Brand X Official", 2)
        val provisional = accounts.upsertFromFeed("Ünïcode Créator", null, 3)
        sightings.recordSighting(
            shop, "p1", Signals(hasShopAnchor = true), SightingSource.OVERLAY,
            userAction = UserAction.AUTO_SKIP, skipReason = SkipReason.ORGANIC_SHOP, nowMillis = 1,
        )
        sightings.recordSighting(shop, "p2", Signals(hasShopAnchor = true), SightingSource.OVERLAY, nowMillis = 1)
        accounts.addTag(shop, TagNames.BOT_LIKE, TagSource.MANUAL, 1)
        accounts.addTag(ad, TagNames.PAID_AD, TagSource.AUTO_DETECTED, 2)
        accounts.setStatus(ad, AccountStatus.BLOCKED)

        suspend fun ids(
            query: String = "",
            statuses: Set<AccountStatus> = emptySet(),
            tags: Set<String> = emptySet(),
        ) = accounts.observeAccounts(query, statuses, tags).first().map(AccountSummary::account).map { it.id }

        assertEquals(listOf(provisional, ad, shop), ids())
        assertEquals(listOf(shop), ids("@BEST_"))
        assertEquals(listOf(ad), ids("x offic"))
        assertEquals(emptyList<Long>(), ids("best%deals"))
        assertEquals(listOf(provisional), ids("ÜNÏCODE"))
        assertEquals(listOf(ad), ids(statuses = setOf(AccountStatus.BLOCKED)))
        assertEquals(listOf(provisional, shop), ids(statuses = setOf(AccountStatus.SEEN)))
        assertEquals(listOf(shop), ids(tags = setOf(TagNames.ORGANIC_SHOP, TagNames.BOT_LIKE)))
        assertEquals(emptyList<Long>(), ids(tags = setOf(TagNames.ORGANIC_SHOP, TagNames.PAID_AD)))
        assertEquals(listOf(ad), ids("brand", setOf(AccountStatus.BLOCKED), setOf("Paid Ad")))

        val summary = accounts.observeAccounts("best").first().single()
        assertEquals(listOf(TagNames.BOT_LIKE, TagNames.ORGANIC_SHOP, TagNames.SHOP_ANCHOR), summary.tagNames)
        assertEquals(2, summary.sightingCount)
        assertEquals(UserAction.AUTO_SKIP, summary.lastAction)
    }

    @Test
    fun findByDisplayNameReturnsEverySkeletonMatch() = runTest {
        val blocked = accounts.ensureHandleAccount("orig", HandleSource.PROFILE, "Famous Person", 1)
        accounts.setStatus(blocked, AccountStatus.BLOCKED)
        val provisional = accounts.upsertFromFeed("famous person", null, 2)
        accounts.upsertFromFeed("Someone Else", null, 3)
        assertEquals(setOf(blocked, provisional), accounts.findByDisplayName("FAMOUS PERSON").map { it.id }.toSet())
        assertTrue(accounts.findByDisplayName(" ").isEmpty())
    }

    @Test
    fun tagsAreNormalizedIdempotentAndRemovable() = runTest {
        val a = accounts.upsertFromFeed("X", null, 1)
        accounts.addTag(a, "My Custom", TagSource.MANUAL, 1)
        accounts.addTag(a, "my custom", TagSource.AUTO_DETECTED, 2)
        accounts.addTag(a, "my_custom", TagSource.MANUAL, 3)
        assertEquals(2, accounts.observeTags(a).first().size)
        assertEquals(TagKind.MANUAL, accounts.observeAllTags().first().single { it.name == "my_custom" }.kind)

        accounts.removeTag(a, "My Custom", TagSource.MANUAL)
        assertEquals(listOf(TagSource.AUTO_DETECTED), accounts.observeTags(a).first().map { it.source })
        accounts.removeTag(a, "my_custom")
        assertTrue(accounts.observeTags(a).first().isEmpty())
        accounts.removeTag(a, "never_existed")
    }

    @Test
    fun accountFieldSetters() = runTest {
        val original = accounts.ensureHandleAccount("original", HandleSource.PROFILE, "Original", 1)
        val copy = accounts.upsertFromFeed("Original", null, 2)
        accounts.setSuspectedOriginal(copy, original)
        accounts.setSuspectedOriginal(original, original)
        accounts.setAccountCategory(copy, " Shop ")
        accounts.setNotes(copy, "   ")
        accounts.setStatus(copy, AccountStatus.FALSE_POSITIVE)

        val updated = checkNotNull(accounts.observeAccount(copy).first())
        assertEquals(original, updated.suspectedOriginalOf)
        assertEquals("Shop", updated.accountCategory)
        assertNull(updated.notes)
        assertEquals(AccountStatus.FALSE_POSITIVE, updated.status)
        assertNull(accounts.getAccount(original)?.suspectedOriginalOf)
    }
}
