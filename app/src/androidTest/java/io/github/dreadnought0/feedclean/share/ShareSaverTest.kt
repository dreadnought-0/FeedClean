package io.github.dreadnought0.feedclean.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.dreadnought0.feedclean.data.DbTest
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagSource
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.ScreenSnapshot
import io.github.dreadnought0.feedclean.detect.Signals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareSaverTest : DbTest() {

    private val now = 1_795_341_600_000L
    private val url = "https://vm.tiktok.com/ZMexample/"

    private fun context(probable: String? = null) = ScreenSnapshot.FeedVideo(
        capturedAtMillis = now - 1_000,
        displayName = "Example Shop",
        probableHandle = probable,
        caption = null,
        soundDescription = null,
        signals = Signals(hasShopAnchor = true),
        pageSignature = "page-sig",
        isForYouTab = true,
    )

    private fun saver() = ShareSaver(accounts, sightings)

    @Test
    fun resolvedHandleMergesTheProvisionalFeedAccount() = runTest {
        val provisional = accounts.upsertFromFeed("Example Shop", null, now - 5_000)

        val id = saver().save(
            ShareSaveRequest(url, context(), "example_shop", Signals(hasShopAnchor = true), setOf("ai_generated"), "note"),
            nowMillis = now,
        )

        val account = checkNotNull(accounts.getAccount(id))
        assertEquals("example_shop", account.handle)
        assertEquals(HandleSource.RESOLVE, account.handleSource)
        assertEquals(provisional, id)
        assertEquals("note", account.notes)
        val sighting = checkNotNull(sightings.latestForAccount(id))
        assertEquals(SightingSource.SHARE, sighting.source)
        assertEquals(UserAction.LOG, sighting.userAction)
        assertEquals(url, sighting.videoUrl)
        assertEquals("page-sig", sighting.pageSignature)
        val tags = accounts.observeTags(id).first().map { it.tag.name to it.source }.toSet()
        assertTrue(("ai_generated" to TagSource.MANUAL) in tags)
        assertTrue(("shop_anchor" to TagSource.AUTO_DETECTED) in tags)
    }

    @Test
    fun withoutContextOrHandleTheUrlSignatureIsUsed() = runTest {
        val id = saver().save(ShareSaveRequest(url, null, null, Signals(), emptySet(), ""), nowMillis = now)

        val sighting = checkNotNull(sightings.latestForAccount(id))
        assertEquals(ShareContext.urlSignature(url), sighting.pageSignature)
        assertNull(accounts.getAccount(id)?.handle)
    }

    @Test
    fun handleOnlyCreatesTheHandleAccount() = runTest {
        val id = saver().save(ShareSaveRequest(url, null, "someone", Signals(), emptySet(), ""), nowMillis = now)
        assertEquals("someone", accounts.getAccount(id)?.handle)
    }
}
