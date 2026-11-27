package io.github.dreadnought0.feedclean.block

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture guard for "every account action starts from a user tap": detection code (detect/) must never be able
 * to start an account action.
 * It may not mention the click-only token, the executors, the screen driver, or anything in block/.
 *
 * Reads the sources directly. Unit tests run with the app/ module as the working directory; the repo root is
 * accepted too (IDE runs).
 */
class DetectionCannotStartActionsTest {

    @Test
    fun detectionSourcesNeverReferenceActionStarters() {
        val dir = listOf(DETECT_DIR, "app/$DETECT_DIR").map(::File).firstOrNull { it.isDirectory }
            ?: throw AssertionError("detect/ sources not found from ${File("").absolutePath}")
        val sources = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("no Kotlin files under $dir", sources.isNotEmpty())

        val violations = sources.flatMap { file ->
            val code = stripComments(file.readText())
            FORBIDDEN.filter { references(code, it) }.map { "${file.relativeTo(dir)} references $it" }
        }

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun guardCatchesAReferenceButIgnoresComments() {
        val code = stripComments("import io.github.dreadnought0.feedclean.block.UserIntentToken\n// BlockFlowExecutor")

        assertTrue(references(code, "UserIntentToken"))
        assertTrue(references(code, "io.github.dreadnought0.feedclean.block"))
        assertFalse(references(code, "BlockFlowExecutor"))
    }

    private fun references(code: String, name: String): Boolean =
        Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(code)

    /** Drops block and line comments, so KDoc that merely mentions a name doesn't count as a reference. */
    private fun stripComments(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("//[^\n]*"), " ")

    private companion object {
        const val DETECT_DIR = "src/main/java/io/github/dreadnought0/feedclean/detect"

        val FORBIDDEN = listOf(
            "UserIntentToken",
            "BlockFlowExecutor",
            "NotInterestedFlow",
            "ScreenDriver",
            "io.github.dreadnought0.feedclean.block",
        )
    }
}
