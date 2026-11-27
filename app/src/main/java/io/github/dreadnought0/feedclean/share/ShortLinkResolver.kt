package io.github.dreadnought0.feedclean.share

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Learns the @handle behind a shared TikTok short link with **one** HTTPS `HEAD` request.
 *
 * This is the only network request in the whole app. It is sent only:
 * - when [resolve] gets a fresh, unused [ResolveTap], which only the share sheet's Resolve button creates;
 * - for `https` links on [TikTokLinks.ALLOWED_HOSTS];
 * - at most once per tap: redirects are not followed, a `Location` that points at another short link is
 *   reported as [ResolveResult.Unresolved] with [UnresolvedReason.SECOND_HOP] (no second request), and
 *   nothing retries.
 *
 * Links that already contain `/@handle` are answered from the link itself, without any request.
 *
 * @param requester the single HTTP exchange; [HttpsHeadRequester] in production, a fake in tests.
 * @param clock time source used to check that the tap is fresh.
 */
class ShortLinkResolver(
    private val requester: HeadRequester = HttpsHeadRequester(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Resolves [url] for the user's [tap] on Resolve. Never throws for network problems. */
    suspend fun resolve(url: String, tap: ResolveTap): ResolveResult {
        if (!tap.isFresh(clock()) || !tap.consume()) return ResolveResult.Refused(RefusedReason.NOT_A_FRESH_TAP)

        TikTokLinks.handleFromUrl(url)?.let { return ResolveResult.Resolved(it, fromLinkItself = true) }
        if (!TikTokLinks.isResolvable(url)) return ResolveResult.Refused(RefusedReason.NOT_ALLOWED)

        val target = try {
            URI(url.trim()).toURL()
        } catch (_: Exception) {
            return ResolveResult.Refused(RefusedReason.NOT_ALLOWED)
        }
        val response = try {
            requester.head(target)
        } catch (e: IOException) {
            return ResolveResult.Failed(e.javaClass.simpleName)
        } catch (e: SecurityException) {
            return ResolveResult.Failed(e.javaClass.simpleName)
        }
        return interpret(url, response)
    }

    companion object {
        /** Connect and read timeout of the single request. */
        const val TIMEOUT_MILLIS: Int = 8_000

        /** Generic User-Agent: names the app only, never the user or the device. */
        const val USER_AGENT: String = "FeedClean"

        /** Reads the handle from the response to the single HEAD request. Pure. */
        internal fun interpret(requestUrl: String, response: HeadResponse): ResolveResult {
            val rawLocation = response.location?.trim()?.takeIf { it.isNotEmpty() }
                ?: return ResolveResult.Unresolved(UnresolvedReason.NO_LOCATION, response.statusCode)
            val location = absolute(requestUrl, rawLocation)
                ?: return ResolveResult.Unresolved(UnresolvedReason.UNEXPECTED_LOCATION, response.statusCode)
            if (!TikTokLinks.isTikTokUrl(location)) {
                return ResolveResult.Unresolved(UnresolvedReason.UNEXPECTED_LOCATION, response.statusCode)
            }
            TikTokLinks.handleFromUrl(location)?.let { return ResolveResult.Resolved(it, fromLinkItself = false) }
            val reason = if (TikTokLinks.isShortLink(location)) {
                UnresolvedReason.SECOND_HOP
            } else {
                UnresolvedReason.NO_HANDLE_IN_LOCATION
            }
            return ResolveResult.Unresolved(reason, response.statusCode)
        }

        private fun absolute(base: String, location: String): String? = try {
            URI(base.trim()).resolve(URI(location)).toString()
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** Outcome of [ShortLinkResolver.resolve]. */
sealed interface ResolveResult {
    /** [handle] is normalized (lowercase, no "@"). [fromLinkItself]: read from the shared URL, no request sent. */
    data class Resolved(val handle: String, val fromLinkItself: Boolean) : ResolveResult

    /** The one request was answered, but it did not reveal a handle. Nothing more is requested. */
    data class Unresolved(val reason: UnresolvedReason, val statusCode: Int) : ResolveResult

    /** The request could not be completed (offline, timeout, TLS error). [error] is the exception type only. */
    data class Failed(val error: String) : ResolveResult

    /** No request was sent. */
    data class Refused(val reason: RefusedReason) : ResolveResult
}

/** Why the single request did not reveal a handle. */
enum class UnresolvedReason {
    /** No `Location` header (not a redirect). */
    NO_LOCATION,

    /** `Location` points at yet another short link; following it would be a second request. */
    SECOND_HOP,

    /** `Location` is a TikTok page without `/@handle`. */
    NO_HANDLE_IN_LOCATION,

    /** `Location` is malformed or not a TikTok URL. */
    UNEXPECTED_LOCATION,
}

/** Why no request was sent. */
enum class RefusedReason {
    /** Missing, stale, or already used [ResolveTap]. */
    NOT_A_FRESH_TAP,

    /** Not `https`, not an allow-listed TikTok host, or a non-default port. */
    NOT_ALLOWED,
}

/** What [ShortLinkResolver] reads from the one response. */
data class HeadResponse(val statusCode: Int, val location: String?)

/**
 * The single HTTP exchange behind Resolve, abstracted so unit tests use a fake (they never touch the network).
 * Implementations send exactly one request for [url] and must not follow redirects.
 */
fun interface HeadRequester {
    /** Sends one `HEAD` for [url] and returns its status and `Location` header. */
    @Throws(IOException::class)
    suspend fun head(url: URL): HeadResponse
}

/**
 * Production [HeadRequester]: one `HEAD` over [HttpsURLConnection] with redirects disabled, no caches, no
 * cookies (the app installs no `CookieHandler` and sets no `Cookie` header), [ShortLinkResolver.USER_AGENT],
 * and [ShortLinkResolver.TIMEOUT_MILLIS] connect/read timeouts. The response body is never read.
 *
 * @param open opens the connection; replaceable so tests can check the configuration without a network.
 */
class HttpsHeadRequester(
    private val open: (URL) -> java.net.URLConnection = { it.openConnection() },
) : HeadRequester {
    override suspend fun head(url: URL): HeadResponse = withContext(Dispatchers.IO) {
        if (!url.protocol.equals("https", ignoreCase = true)) throw IOException("https only")
        val connection = open(url) as? HttpsURLConnection ?: throw IOException("https only")
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = ShortLinkResolver.TIMEOUT_MILLIS
            connection.readTimeout = ShortLinkResolver.TIMEOUT_MILLIS
            connection.useCaches = false
            connection.doInput = true
            connection.setRequestProperty("User-Agent", ShortLinkResolver.USER_AGENT)
            HeadResponse(statusCode = connection.responseCode, location = connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }
}
