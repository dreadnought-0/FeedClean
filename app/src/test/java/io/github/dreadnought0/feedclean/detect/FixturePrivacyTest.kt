package io.github.dreadnought0.feedclean.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Committed fixtures (and any docs written from the same captures) must be scrubbed. Every committed fixture, the
 * selector file, and any notes doc written from the captures are scanned for words and two-word phrases that were
 * on screen in the raw captures (creator, store and search terms, the location tab), whole-word and
 * case-insensitive.
 *
 * The denylist is stored as SHA-256 digests of the lowercase term: a plaintext list in an open-source repo would
 * itself publish the names (and the user's location) the scrubbing removed. To add a term, append
 * `sha256(lowercase term)`; phrases of two words are matched with a single space.
 */
class FixturePrivacyTest {

    /** Text files that ship in the repo and were written from raw device captures. */
    private val scannedFiles: List<File> by lazy {
        val fixtures = File("src/test/resources/fixtures").walkTopDown().filter { it.isFile }.toList()
        val extra = listOf(File("src/main/assets/selectors/tiktok.json"))
        fixtures + extra.filter { it.isFile }
    }

    @Test
    fun scanner_findsTheControlPlaceholder() {
        // Positive control: the 47.7.3 placeholder "Creator A" is in a_normal, so a broken tokenizer can't pass.
        val a = File("src/test/resources/fixtures/tiktok-47.7.3/cover/a_normal.xml")
        assertTrue("control phrase not found: the scanner is broken", CONTROL in termDigests(a.readText()))
    }

    @Test
    fun committedFixturesAndDocs_containNoDenylistedTerm() {
        assertTrue("fixtures not found (working dir should be app/)", scannedFiles.size >= 15)
        val leaks = scannedFiles.flatMap { file ->
            val found = termDigests(file.readText()) intersect DENYLIST
            found.map { "${file.path}: denylisted term (sha256 ${it.take(12)}…)" }
        }
        assertEquals("scrub these fixtures/docs", emptyList<String>(), leaks)
    }

    @Test
    fun fixturesFromCaptures_sayHowTheyWereScrubbed() {
        val newFixtures = File("src/test/resources/fixtures/tiktok-47.9.3").walkTopDown().filter { it.isFile }.toList() +
            File("src/test/resources/fixtures/synthetic/profile_with_product_sheet.xml")
        assertTrue(newFixtures.size >= 3)
        for (f in newFixtures) {
            val head = f.readText().take(HEADER_CHARS)
            assertTrue("${f.name} needs a header saying it is scrubbed or synthetic", "Scrubbed" in head || "SYNTHETIC" in head)
        }
    }

    private companion object {
        const val HEADER_CHARS = 800

        /** sha256("creator a"): the 47.7.3 placeholder, used as the scanner's positive control. */
        const val CONTROL = "7eb318a97cdcadd18c5c0430527d82e3330ef715b86902bc23f75f872c09ea22"

        /** sha256 of lowercase terms seen in raw captures (location tab, creators, store, search queries). */
        val DENYLIST = setOf(
            "fb4c23b8ac683b1f62fd7c3df42db5ab804861a87f173a87b53bb0f99acb5075",
            "f9b7f3042c2eed80ab7368f7d4338a8cbdde29850ee8730910226b34ae143263",
            "2943a247106bb282e158a7c4991e01bbafdfa60002e957c3b8ae6a32db4b44fb",
            "83d425af120c69e561cc73756c95223bab55108bee4e31090daa8c30b1b62fb5",
            "ec3620470529f11448cb4ec5baeec3dc4395f58d69d5b99ce1891409a7c02ef7",
            "77dbb823506583d15373c7534a2118a7f8c3b86c607a6b6bdab2a76aecad7cde",
            "83ffd663b1caa557ac25782874d3405bda5ad397c4940b6202c22884624a9461",
            "13aa6127ef7bdca296bfeb6e93246194c4424219fdffabbe5e26c0b0f25ed7e8",
            "f44a052a0ececd8044386fac3d42cde8bcb4484d13579e7c51c47053fe25fc78",
        )

        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

        /** Digests of every whole word and every adjacent two-word phrase of [text], lowercased. */
        fun termDigests(text: String): Set<String> {
            val words = NameNormalizer.stripInvisible(text).lowercase(Locale.ROOT).split(NON_WORD).filter { it.isNotEmpty() }
            val terms = HashSet<String>(words.size * 2)
            for (i in words.indices) {
                terms += words[i]
                if (i + 1 < words.size) terms += "${words[i]} ${words[i + 1]}"
            }
            return terms.mapTo(HashSet()) { sha256(it) }
        }

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
