package io.github.dreadnought0.feedclean.share

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Checks how the production requester configures its one connection, using a fake connection (no network). */
class HttpsHeadRequesterTest {

    private class FakeHttpsConnection(url: URL, private val location: String?) : HttpsURLConnection(url) {
        var opened = 0
        var disconnected = false
        var bodyRead = false
        val headersAtSend = mutableMapOf<String, String?>()
        var followRedirectsAtSend: Boolean? = null
        var methodAtSend: String? = null

        override fun getResponseCode(): Int {
            opened++
            followRedirectsAtSend = instanceFollowRedirects
            methodAtSend = requestMethod
            headersAtSend["User-Agent"] = getRequestProperty("User-Agent")
            headersAtSend["Cookie"] = getRequestProperty("Cookie")
            return 301
        }

        override fun getHeaderField(name: String?): String? = if (name.equals("Location", true)) location else null

        override fun getInputStream(): InputStream {
            bodyRead = true
            throw IOException("body must not be read")
        }

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy(): Boolean = false
        override fun getCipherSuite(): String = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test
    fun sendsOneHeadWithoutRedirectsCookiesOrBody() = runTest {
        var connection: FakeHttpsConnection? = null
        var opens = 0
        val requester = HttpsHeadRequester { url ->
            opens++
            FakeHttpsConnection(url, "https://vm.tiktok.com/ZMsecond/").also { connection = it }
        }

        val response = requester.head(URI.create("https://vm.tiktok.com/ZMfirst/").toURL())

        val c = checkNotNull(connection)
        assertEquals(HeadResponse(301, "https://vm.tiktok.com/ZMsecond/"), response)
        assertEquals(1, opens)
        assertEquals(1, c.opened)
        assertEquals("HEAD", c.methodAtSend)
        assertEquals(false, c.followRedirectsAtSend)
        assertEquals(ShortLinkResolver.TIMEOUT_MILLIS, c.connectTimeout)
        assertEquals(ShortLinkResolver.TIMEOUT_MILLIS, c.readTimeout)
        assertFalse(c.useCaches)
        assertEquals("FeedClean", c.headersAtSend["User-Agent"])
        assertEquals(null, c.headersAtSend["Cookie"])
        assertFalse(c.bodyRead)
        assertTrue(c.disconnected)
    }

    @Test
    fun refusesPlainHttpBeforeOpeningAConnection() = runTest {
        val requester = HttpsHeadRequester { fail("must not open a connection for http"); throw AssertionError() }
        try {
            requester.head(URI.create("http://vm.tiktok.com/ZMabc/").toURL())
            fail("expected IOException")
        } catch (_: IOException) {
            // expected
        }
    }
}
