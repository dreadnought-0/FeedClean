package io.github.dreadnought0.feedclean.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture guards for "every account action starts from a user tap" in the live runtime, by reading the
 * sources (like `DetectionCannotStartActionsTest`):
 *  - click tokens are created only in click handlers (the overlay card, and a future Suggested queue);
 *  - auto-skip can only read the screen and scroll: it never clicks, taps, long-presses, or presses Back itself
 *    (automated Not-interested goes through the rate-limited NotInterestedFlow, behind its own arming).
 */
class ActionOriginGuardTest {
    private val root: File = listOf(MAIN, "app/$MAIN").map(::File).firstOrNull { it.isDirectory }
        ?: throw AssertionError("main sources not found from ${File("").absolutePath}")

    private fun code(file: File): String =
        file.readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("//[^\n]*"), " ")

    @Test
    fun clickTokensAreOnlyCreatedInClickHandlers() {
        val creators = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && code(it).contains("fromUserClick(") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filterNot { it == "block/UserIntentToken.kt" } // the definition
            .toList()
        val allowed = creators.filter { it == "overlay/CardContent.kt" || it.startsWith("ui/suggested/") }
        assertEquals("tokens created outside click handlers", creators, allowed)
        assertTrue("the overlay card creates tokens", "overlay/CardContent.kt" in creators)
        listOf("service/", "curate/", "detect/", "selectors/", "data/").forEach { pkg ->
            assertTrue("$pkg must not create tokens", creators.none { it.startsWith(pkg) })
        }
    }

    @Test
    fun autoSkipOnlyReadsAndScrolls() {
        val src = code(File(root, "curate/AutoSkipController.kt"))
        val calls = Regex("""driver\.(\w+)\(""").findAll(src).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("snapshot", "scrollForward", "awaitSnapshot"), calls)
        assertEquals("exactly one scroll call site", 1, Regex("""driver\.scrollForward\(""").findAll(src).count())
    }

    @Test
    fun curatorNeverTouchesTheScreenDirectly() {
        val src = code(File(root, "service/FeedCurator.kt"))
        assertTrue(Regex("""\b(click|tap|longPress|scrollForward|back)\(""").findAll(src).none())
    }

    private companion object {
        const val MAIN = "src/main/java/io/github/dreadnought0/feedclean"
    }
}
