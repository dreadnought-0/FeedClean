package io.github.dreadnought0.feedclean.share

import io.github.dreadnought0.feedclean.data.model.Handles
import java.net.URI
import java.net.URISyntaxException

/**
 * Pure helpers for links shared to FeedClean. No network access here.
 *
 * These are URL shapes (hosts, the `/@handle` path segment), not TikTok UI selectors, so they live in code
 * rather than in `assets/selectors/tiktok.json`.
 */
object TikTokLinks {
    /** Hosts FeedClean accepts in shared links. Exact match, lowercase; subdomains of these are NOT accepted. */
    val ALLOWED_HOSTS: Set<String> = setOf(
        "vm.tiktok.com",
        "vt.tiktok.com",
        "www.tiktok.com",
        "tiktok.com",
        "m.tiktok.com",
    )

    /** Hosts that only serve short links (each link is a redirect to the real page). */
    private val SHORT_LINK_HOSTS: Set<String> = setOf("vm.tiktok.com", "vt.tiktok.com")

    private val urlCandidate = Regex("""https?://[^\s<>"'`{}|\\^]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = ".,;:!?)]}>'\"…"
    private val handleInPath = Regex("""/@([A-Za-z0-9._]{2,24})(?=[/?#]|$)""")

    /**
     * Every distinct TikTok link in [text], in order of appearance. Text may contain other words and links to
     * other sites; those are ignored. Trailing sentence punctuation is stripped from each link.
     */
    fun extract(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return urlCandidate.findAll(text)
            .map { it.value.trimEnd { c -> c in trailingPunctuation } }
            .filter { isTikTokUrl(it) }
            .distinct()
            .toList()
    }

    /** True when [url] parses, uses http or https, has no user-info, and its host is in [ALLOWED_HOSTS]. */
    fun isTikTokUrl(url: String): Boolean {
        val uri = parse(url) ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "https" || scheme == "http") && uri.rawUserInfo == null && hostOf(uri) in ALLOWED_HOSTS
    }

    /**
     * True when [url] may be sent to the network by Resolve: https only, allow-listed host, default port, no
     * user-info.
     */
    fun isResolvable(url: String): Boolean {
        val uri = parse(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            hostOf(uri) in ALLOWED_HOSTS
    }

    /** True for short links (`vm.`/`vt.tiktok.com/…` or `…tiktok.com/t/…`) that only redirect elsewhere. */
    fun isShortLink(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = hostOf(uri) ?: return false
        if (host in SHORT_LINK_HOSTS) return true
        return host in ALLOWED_HOSTS && (uri.rawPath ?: "").startsWith("/t/")
    }

    /**
     * The handle in a TikTok page URL or path (`…/@some.handle/video/123`), normalized (lowercase, no "@"), or
     * null when there is none. Works on absolute URLs and on bare paths (a relative `Location` header).
     */
    fun handleFromUrl(urlOrPath: String?): String? {
        if (urlOrPath.isNullOrBlank()) return null
        val path = parse(urlOrPath)?.rawPath ?: urlOrPath.substringBefore('?').substringBefore('#')
        val match = handleInPath.find(path) ?: return null
        return Handles.normalize(match.groupValues[1])
    }

    /** Lowercased host of [url], or null if it does not parse. */
    fun hostOf(url: String): String? = parse(url)?.let(::hostOf)

    private fun hostOf(uri: URI): String? = uri.host?.lowercase()?.removeSuffix(".")

    private fun parse(url: String): URI? = try {
        URI(url.trim())
    } catch (_: URISyntaxException) {
        null
    }
}
