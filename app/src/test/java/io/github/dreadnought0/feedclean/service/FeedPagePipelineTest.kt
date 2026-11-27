package io.github.dreadnought0.feedclean.service

import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.CurationDecision
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.settings.FeedCleanSettings
import io.github.dreadnought0.feedclean.testutil.FakeAccountRepository
import io.github.dreadnought0.feedclean.testutil.FakeSightingRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPagePipelineTest {
    private val accounts = FakeAccountRepository()
    private val sightings = FakeSightingRepository()
    private val pipeline = FeedPagePipeline(accounts, sightings) { NOW }
    private val settings = FeedCleanSettings()

    private fun page(
        signals: Signals = Signals(),
        name: String? = "Creator",
        probableHandle: String? = null,
        signature: String = "sig-1",
    ) = ScreenSnapshot.FeedVideo(
        capturedAtMillis = NOW,
        displayName = name,
        probableHandle = probableHandle,
        caption = "c",
        soundDescription = null,
        signals = signals,
        pageSignature = signature,
        isForYouTab = true,
    )

    private fun profile(handle: String?, shop: Boolean, name: String? = "Seller") = ScreenSnapshot.Profile(
        capturedAtMillis = NOW,
        displayName = name,
        handle = handle,
        accountCategory = if (shop) "Shop" else null,
        isShopCreator = shop,
        isBlockedByMe = false,
    )

    @Test
    fun normalPageIsAttributedButNotLogged() = runTest {
        val p = pipeline.process(page(), settings, sessionId = null)
        assertEquals(listOf("upsertFromFeed"), accounts.calls)
        assertNull(p.sightingId)
        assertTrue(sightings.sightings.isEmpty())
        assertEquals(CurationDecision.Keep, p.decision)
        assertEquals(BlockedMatch.NONE, p.blockedMatch)
        assertNull(p.knownHandle)
    }

    @Test
    fun shopPageIsLoggedAndWouldBeSkipped() = runTest {
        val p = pipeline.process(page(Signals(hasShopAnchor = true)), settings, sessionId = 5L)
        val s = sightings.sightings.getValue(checkNotNull(p.sightingId))
        assertEquals(SightingSource.OVERLAY, s.source)
        assertEquals(UserAction.NONE, s.userAction)
        assertEquals(5L, s.sessionId)
        assertEquals(p.accountId, s.accountId)
        assertEquals(CurationDecision.Skip(SkipReason.ORGANIC_SHOP), p.decision)
    }

    @Test
    fun detectionTogglesDecideWhatIsLogged() = runTest {
        val shop = page(Signals(hasShopAnchor = true))
        assertNull(pipeline.process(shop, settings.copy(detectShopAnchor = false), null).sightingId)
        assertNull(pipeline.process(shop, settings.copy(detectionEnabled = false), null).sightingId)
        val ad = page(Signals(isPaidAd = true, disclosureLabel = "Ad"), signature = "sig-2")
        assertNull(pipeline.process(ad, settings.copy(detectPaidAd = false), null).sightingId)
        assertNotNull(pipeline.process(ad, settings, null).sightingId)
    }

    @Test
    fun paidAdIsLoggedAndKept() = runTest {
        val p = pipeline.process(page(Signals(isPaidAd = true, hasShopAnchor = true)), settings, null)
        assertNotNull(p.sightingId)
        assertEquals(CurationDecision.Keep, p.decision)
    }

    @Test
    fun knownShopCreatorIsEnrichedFromTheDatabase() = runTest {
        val seller = accounts.seed(handle = "seller.one", displayName = "Creator", category = "Shop")
        val p = pipeline.process(page(probableHandle = "seller.one"), settings, null)
        assertTrue(p.signals.isShopCreator)
        assertEquals(seller.id, p.accountId)
        assertEquals("seller.one", p.knownHandle)
        assertNotNull("Shop creator counts as a detection", p.sightingId)
        assertEquals(CurationDecision.Skip(SkipReason.ORGANIC_SHOP), p.decision)
    }

    @Test
    fun unknownProbableHandleIsNotEnrichedAndNotStoredAsHandle() = runTest {
        val p = pipeline.process(page(probableHandle = "nobody.here"), settings, null)
        assertFalse(p.signals.isShopCreator)
        assertNull(p.knownHandle)
        assertNull(accounts.accounts.getValue(p.accountId).handle)
    }

    @Test
    fun blockedHandleIsAStrongMatch() = runTest {
        accounts.seed(handle = "blocked.one", displayName = "Creator", status = AccountStatus.BLOCKED)
        val p = pipeline.process(page(probableHandle = "blocked.one"), settings, null)
        assertEquals(BlockedMatch.STRONG, p.blockedMatch)
    }

    @Test
    fun blockedDisplayNameIsOnlyAPossibleMatch() = runTest {
        accounts.seed(handle = "blocked.one", displayName = "Creator", status = AccountStatus.BLOCKED)
        val p = pipeline.process(page(), settings, null)
        assertEquals(BlockedMatch.POSSIBLE, p.blockedMatch)
        assertEquals(BlockedMatch.NONE, pipeline.process(page(name = "Someone Else", signature = "s2"), settings, null).blockedMatch)
    }

    @Test
    fun shopProfileRecordsTheCategoryOnce() = runTest {
        val id = pipeline.recordProfile(profile("seller.two", shop = true))
        assertNotNull(id)
        assertEquals("Shop", accounts.accounts.getValue(checkNotNull(id)).accountCategory)
        pipeline.recordProfile(profile("seller.two", shop = true))
        assertEquals(1, accounts.calls.count { it.startsWith("setAccountCategory") })
    }

    @Test
    fun nonShopOrUnreadableProfilesAreNotStored() = runTest {
        assertNull(pipeline.recordProfile(profile("person", shop = false)))
        assertNull(pipeline.recordProfile(profile(null, shop = true)))
        assertTrue(accounts.accounts.isEmpty())
    }

    // ── refine (watch window) ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun refineWithoutNewSignalsDoesNothing() = runTest {
        val p = pipeline.process(page(Signals(isPaidAd = true, hasShopAnchor = true)), settings, null)
        sightings.calls.clear()
        assertNull(pipeline.refine(p, page(Signals(isPaidAd = true)), settings, null)) // shrank
        assertNull(pipeline.refine(p, page(Signals(isPaidAd = true, hasShopAnchor = true)), settings, null)) // same
        assertTrue(sightings.calls.isEmpty())
    }

    @Test
    fun refineLogsAPageThatHadNoSightingYet() = runTest {
        val p = pipeline.process(page(), settings, sessionId = 3L)
        assertNull(p.sightingId)
        val r = checkNotNull(pipeline.refine(p, page(Signals(hasShopAnchor = true)), settings, 3L, recordSignature = "first-sig"))
        val s = sightings.sightings.getValue(checkNotNull(r.page.sightingId))
        assertEquals("first-sig", s.pageSignature)
        assertEquals(3L, s.sessionId)
        assertTrue(s.signals.hasShopAnchor)
        assertEquals(CurationDecision.Skip(SkipReason.ORGANIC_SHOP), r.page.decision)
        assertEquals(listOf(DetectionRules.Kind.SHOP), r.newKinds)
        assertEquals("the account is not touched again", 1, accounts.calls.count { it == "upsertFromFeed" })
    }

    @Test
    fun refineUpdatesTheExistingSightingAndKeepsDbEnrichment() = runTest {
        accounts.seed(handle = "seller.one", displayName = "Creator", category = "Shop")
        val p = pipeline.process(page(probableHandle = "seller.one"), settings, null)
        val id = checkNotNull(p.sightingId)
        val r = checkNotNull(pipeline.refine(p, page(Signals(isPaidAd = true, disclosureLabel = "Ad")), settings, null))
        assertEquals(id, r.page.sightingId)
        assertEquals(listOf("updateSignals:$id"), sightings.calls.filter { it.startsWith("updateSignals") })
        assertEquals(1, sightings.sightings.size)
        val merged = sightings.sightings.getValue(id).signals
        assertTrue("DB enrichment survives the raw re-read", merged.isShopCreator && r.page.signals.isShopCreator)
        assertTrue(merged.isPaidAd)
        assertEquals("now a paid ad: kept by default", CurationDecision.Keep, r.page.decision)
        assertEquals(listOf(DetectionRules.Kind.PAID_AD), r.newKinds)
    }

    @Test
    fun refineRespectsDetectionToggles() = runTest {
        val off = settings.copy(detectShopAnchor = false)
        val p = pipeline.process(page(), off, null)
        val r = checkNotNull(pipeline.refine(p, page(Signals(hasShopAnchor = true)), off, null))
        assertNull("not an enabled detection: not logged", r.page.sightingId)
        assertEquals(CurationDecision.Keep, r.page.decision)
        assertTrue(r.newKinds.isEmpty())
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
