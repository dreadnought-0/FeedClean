package io.github.dreadnought0.feedclean.share

import java.io.IOException
import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolve sends at most one request, only for a fresh tap, only to allow-listed https links. No real network. */
class ShortLinkResolverTest {

    private val now = 1_800_000_000_000L

    /** Records every request; answers with [response] (or throws [error]). */
    private class FakeRequester(
        private val response: HeadResponse = HeadResponse(301, null),
        private val error: IOException? = null,
    ) : HeadRequester {
        val requests = mutableListOf<String>()

        override suspend fun head(url: URL): HeadResponse {
            requests += url.toString()
            error?.let { throw it }
            return response
        }
    }

    private fun resolver(requester: HeadRequester) = ShortLinkResolver(requester, clock = { now })

    private fun tap() = ResolveTap.fromResolveButton(now)

    @Test
    fun readsHandleFromLocationWithExactlyOneRequest() = runTest {
        val fake = FakeRequester(HeadResponse(301, "https://www.tiktok.com/@Example.Creator/video/7300000000000000001?_r=1"))

        val result = resolver(fake).resolve("https://vm.tiktok.com/ZMabc/", tap())

        assertEquals(ResolveResult.Resolved("example.creator", fromLinkItself = false), result)
        assertEquals(listOf("https://vm.tiktok.com/ZMabc/"), fake.requests)
    }

    @Test
    fun relativeLocationIsResolvedAgainstTheLink() = runTest {
        val fake = FakeRequester(HeadResponse(302, "/@someone/video/1"))

        assertEquals(
            ResolveResult.Resolved("someone", fromLinkItself = false),
            resolver(fake).resolve("https://vt.tiktok.com/ZSabc/", tap()),
        )
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun secondHopShortLinkIsNotFollowed() = runTest {
        val fake = FakeRequester(HeadResponse(301, "https://vm.tiktok.com/ZMsecond/"))

        val result = resolver(fake).resolve("https://vm.tiktok.com/ZMfirst/", tap())

        assertEquals(ResolveResult.Unresolved(UnresolvedReason.SECOND_HOP, 301), result)
        assertEquals("exactly one request, never the second hop", listOf("https://vm.tiktok.com/ZMfirst/"), fake.requests)
    }

    @Test
    fun missingLocationIsUnresolvedWithoutRetry() = runTest {
        val fake = FakeRequester(HeadResponse(200, null))

        assertEquals(
            ResolveResult.Unresolved(UnresolvedReason.NO_LOCATION, 200),
            resolver(fake).resolve("https://vm.tiktok.com/ZMabc/", tap()),
        )
        assertEquals(1, fake.requests.size)
    }

    @Test
    fun locationWithoutHandleOrOffSiteIsUnresolved() = runTest {
        assertEquals(
            ResolveResult.Unresolved(UnresolvedReason.NO_HANDLE_IN_LOCATION, 302),
            resolver(FakeRequester(HeadResponse(302, "https://www.tiktok.com/login"))).resolve("https://vm.tiktok.com/Z/", tap()),
        )
        assertEquals(
            ResolveResult.Unresolved(UnresolvedReason.UNEXPECTED_LOCATION, 302),
            resolver(FakeRequester(HeadResponse(302, "https://example.com/@someone/video/1")))
                .resolve("https://vm.tiktok.com/Z/", tap()),
        )
    }

    @Test
    fun onlyHttpsAndAllowListedHostsAreRequested() = runTest {
        val fake = FakeRequester(HeadResponse(301, "https://www.tiktok.com/@someone/video/1"))
        val r = resolver(fake)
        val refused = ResolveResult.Refused(RefusedReason.NOT_ALLOWED)

        assertEquals(refused, r.resolve("http://vm.tiktok.com/ZMabc/", tap()))
        assertEquals(refused, r.resolve("https://example.com/ZMabc/", tap()))
        assertEquals(refused, r.resolve("https://tiktok.com.evil.example/ZMabc/", tap()))
        assertEquals(refused, r.resolve("https://vm.tiktok.com:8443/ZMabc/", tap()))
        assertEquals(refused, r.resolve("https://user@vm.tiktok.com/ZMabc/", tap()))
        assertEquals(emptyList<String>(), fake.requests)
    }

    @Test
    fun linkWithHandleNeedsNoRequest() = runTest {
        val fake = FakeRequester()

        assertEquals(
            ResolveResult.Resolved("someone", fromLinkItself = true),
            resolver(fake).resolve("https://www.tiktok.com/@someone/video/1", tap()),
        )
        assertEquals(emptyList<String>(), fake.requests)
    }

    @Test
    fun staleOrReusedTapSendsNothing() = runTest {
        val fake = FakeRequester(HeadResponse(301, "https://www.tiktok.com/@someone/video/1"))
        val r = resolver(fake)
        val notFresh = ResolveResult.Refused(RefusedReason.NOT_A_FRESH_TAP)

        assertEquals(notFresh, r.resolve("https://vm.tiktok.com/ZMabc/", ResolveTap.fromResolveButton(now - 60_000)))
        assertEquals(notFresh, r.resolve("https://vm.tiktok.com/ZMabc/", ResolveTap.fromResolveButton(now + 60_000)))
        assertEquals(0, fake.requests.size)

        val once = tap()
        assertTrue(r.resolve("https://vm.tiktok.com/ZMabc/", once) is ResolveResult.Resolved)
        assertEquals(notFresh, r.resolve("https://vm.tiktok.com/ZMabc/", once))
        assertEquals("one tap, one request", 1, fake.requests.size)
    }

    @Test
    fun networkErrorIsReportedNotRetried() = runTest {
        val fake = FakeRequester(error = IOException("offline"))

        assertEquals(ResolveResult.Failed("IOException"), resolver(fake).resolve("https://vm.tiktok.com/ZMabc/", tap()))
        assertEquals(1, fake.requests.size)
    }
}
