package io.github.dreadnought0.feedclean.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.SightingRepository.Companion.DEDUPE_WINDOW_MILLIS
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SightingRepositoryTest : DbTest() {
    private val shop = Signals(hasShopAnchor = true, anchorText = "Free shipping")

    @Test
    fun samePageWithinTenMinutesIsDeduplicated() = runTest {
        val a = accounts.upsertFromFeed("Seller", null, 0)
        val first = sightings.recordSighting(a, "page-1", shop, SightingSource.OVERLAY, nowMillis = 1_000)
        assertFalse(first.deduplicated)

        val withinWindow = 1_000 + DEDUPE_WINDOW_MILLIS - 1
        val again = sightings.recordSighting(
            a, "page-1", shop, SightingSource.OVERLAY,
            userAction = UserAction.AUTO_SKIP, skipReason = SkipReason.ORGANIC_SHOP, nowMillis = withinWindow,
        )
        assertTrue(again.deduplicated)
        assertEquals(first.id, again.id)
        assertEquals(withinWindow, accounts.getAccount(a)?.lastSeenAt)
        val stored = checkNotNull(sightings.getSighting(first.id))
        assertEquals(UserAction.AUTO_SKIP, stored.userAction)
        assertEquals(SkipReason.ORGANIC_SHOP, stored.skipReason)
        assertEquals(1_000L, stored.seenAt)

        val afterWindow = sightings.recordSighting(a, "page-1", shop, SightingSource.OVERLAY, nowMillis = 1_000 + DEDUPE_WINDOW_MILLIS + 1)
        assertFalse(afterWindow.deduplicated)
        assertNotEquals(first.id, afterWindow.id)

        val otherPage = sightings.recordSighting(a, "page-2", shop, SightingSource.OVERLAY, nowMillis = 1_500)
        assertFalse(otherPage.deduplicated)
        assertEquals(3, sightings.observeRecent(10).first().size)
    }

    @Test
    fun dedupeDoesNotOverwriteAnExistingAction() = runTest {
        val a = accounts.upsertFromFeed("Seller", null, 0)
        val first = sightings.recordSighting(a, "p", shop, SightingSource.OVERLAY, userAction = UserAction.NOT_INTERESTED, nowMillis = 10)
        sightings.recordSighting(a, "p", shop, SightingSource.OVERLAY, userAction = UserAction.AUTO_SKIP, nowMillis = 20)
        assertEquals(UserAction.NOT_INTERESTED, sightings.getSighting(first.id)?.userAction)
    }

    @Test
    fun newSightingTagsTheAccountFromSignals() = runTest {
        val a = accounts.upsertFromFeed("Brand", null, 0)
        sightings.recordSighting(a, "ad", Signals(isPaidAd = true, hasShopAnchor = true), SightingSource.OVERLAY, nowMillis = 5)
        val tags = accounts.observeTags(a).first()
        assertEquals(setOf(TagNames.PAID_AD, TagNames.SHOP_ANCHOR), tags.map { it.tag.name }.toSet())
        assertTrue(tags.all { it.source == TagSource.AUTO_DETECTED })
    }

    @Test
    fun userActionLatestAndRecent() = runTest {
        val a = accounts.upsertFromFeed("Seller", null, 0)
        val older = sightings.recordSighting(a, "p1", shop, SightingSource.OVERLAY, nowMillis = 100)
        val newer = sightings.recordSighting(a, "p2", shop, SightingSource.SHARE, videoUrl = "https://example.invalid/v/1", nowMillis = 200)
        sightings.setUserAction(older.id, UserAction.BLOCK, verified = true)

        val o = checkNotNull(sightings.getSighting(older.id))
        assertEquals(UserAction.BLOCK, o.userAction)
        assertEquals(true, o.actionVerified)
        assertEquals(newer.id, sightings.latestForAccount(a)?.id)
        assertEquals("https://example.invalid/v/1", sightings.latestForAccount(a)?.videoUrl)
        assertEquals(listOf(newer.id, older.id), sightings.observeRecent(10).first().map { it.id })
        assertEquals(listOf(newer.id), sightings.observeRecent(1).first().map { it.id })
    }

    @Test
    fun signalsAreStoredAsJsonAndEnumsByName() = runTest {
        val a = accounts.upsertFromFeed("Seller", null, 0)
        val sessionId = sessions.start(0)
        val full = Signals(
            isPaidAd = true, isPromoted = true, hasShopAnchor = true, isShopCreator = true, isLive = true,
            isLiveShopping = true, isLiveEvent = true, disclosureLabel = "Ad", anchorText = "1.2K sold", ctaText = "Shop now",
        )
        val result = sightings.recordSighting(
            a, "p", full, SightingSource.RECON,
            userAction = UserAction.AUTO_SKIP, skipReason = SkipReason.PAID_AD, nowMillis = 1, sessionId = sessionId,
        )
        val stored = checkNotNull(sightings.getSighting(result.id))
        assertEquals(full, stored.signals)
        assertEquals(sessionId, stored.sessionId)
        assertTrue(checkNotNull(rawString("SELECT signals_json FROM sightings")).contains("\"isPaidAd\":true"))
        assertEquals("AUTO_SKIP", rawString("SELECT user_action FROM sightings"))
        assertEquals("PAID_AD", rawString("SELECT skip_reason FROM sightings"))
        assertEquals("RECON", rawString("SELECT source FROM sightings"))
        assertEquals("SEEN", rawString("SELECT status FROM accounts"))
    }
}
