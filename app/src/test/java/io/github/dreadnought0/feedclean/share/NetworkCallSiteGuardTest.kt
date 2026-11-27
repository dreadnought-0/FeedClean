package io.github.dreadnought0.feedclean.share

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture guard for the no-network rule: the user-tapped Resolve is the app's only network request.
 *
 * - Only `share/ShortLinkResolver.kt` may open a network connection.
 * - Only `share/ShareSheet.kt` (the Resolve button's click handler) may create a [ResolveTap].
 *
 * Reads the sources directly (comments ignored). Unit tests run with app/ as the working directory; the repo
 * root is accepted too.
 */
class NetworkCallSiteGuardTest {

    private val mainDir: File by lazy {
        listOf(MAIN_DIR, "app/$MAIN_DIR").map(::File).firstOrNull { it.isDirectory }
            ?: throw AssertionError("main sources not found from ${File("").absolutePath}")
    }

    private fun sources(): Map<String, String> = mainDir.walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
        .associate { it.relativeTo(mainDir).invariantSeparatorsPath to stripComments(it.readText()) }

    @Test
    fun onlyTheResolverOpensNetworkConnections() {
        val files = sources()
        assertTrue(files.isNotEmpty())
        val violations = files.filterKeys { it != RESOLVER }.flatMap { (path, code) ->
            NETWORK_APIS.filter { it.containsMatchIn(code) }.map { "$path uses ${it.pattern}" }
        }
        assertEquals(emptyList<String>(), violations)
        assertTrue("resolver must exist", files.containsKey(RESOLVER))
    }

    @Test
    fun onlyTheResolveButtonCreatesResolveTaps() {
        val callers = sources().filter { (path, code) -> path != TAP && TAP_FACTORY.containsMatchIn(code) }.keys
        assertEquals(setOf(SHEET), callers)
    }

    @Test
    fun guardIgnoresComments() {
        val code = stripComments("// url.openConnection()\n/* HttpsURLConnection */ val x = 1")
        assertTrue(NETWORK_APIS.none { it.containsMatchIn(code) })
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("//[^\n]*"), " ")

    private companion object {
        const val MAIN_DIR = "src/main/java/io/github/dreadnought0/feedclean"
        const val RESOLVER = "share/ShortLinkResolver.kt"
        const val TAP = "share/ResolveTap.kt"
        const val SHEET = "share/ShareSheet.kt"

        val NETWORK_APIS = listOf(
            Regex("""\bopenConnection\b"""),
            Regex("""\bopenStream\b"""),
            Regex("""\bHttpURLConnection\b"""),
            Regex("""\bHttpsURLConnection\b"""),
            Regex("""\bjava\.net\.Socket\b"""),
            Regex("""\bSocketChannel\b"""),
            Regex("""\bokhttp3?\b"""),
            Regex("""\bio\.ktor\b"""),
            Regex("""\bretrofit2?\b"""),
            Regex("""\bandroid\.webkit\b"""),
        )
        val TAP_FACTORY = Regex("""\bfromResolveButton\s*\(""")
    }
}
