package io.github.dreadnought0.feedclean.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameNormalizerTest {

    @Test
    fun normalize_stripsBidiAndZeroWidth_collapsesSpace_lowercases() {
        assertEquals("hello world", NameNormalizer.normalize("‎Hello‏   World‪"))
        assertEquals("contact 5", NameNormalizer.normalize("‎Contact 5"))
        assertEquals("ad", NameNormalizer.normalize("Ad​"))
        assertEquals("ab", NameNormalizer.normalize("a‍b﻿⁦⁩"))
        assertEquals("a b", NameNormalizer.normalize("  a\t\n b  "))
    }

    @Test
    fun normalize_stripsVariationSelectors() {
        assertEquals("❤", NameNormalizer.normalize("❤️"))
        assertEquals("x", NameNormalizer.normalize("x󠄀")) // U+E0100
    }

    @Test
    fun normalize_foldsMathematicalAlphanumericsWithNfkc() {
        // Sans-serif bold "KAIS" with Greek Lambda and Latin tone-two (as in captured sound credits).
        val styled = "𝗞Λ𝗜Ƨ"
        assertEquals("kλiƨ", NameNormalizer.normalize(styled))
        assertEquals("creator", NameNormalizer.normalize("𝐂𝐫𝐞𝐚𝐭𝐨𝐫"))
        assertEquals("abc", NameNormalizer.normalize("ＡＢＣ")) // full-width
    }

    @Test
    fun skeleton_foldsStyledNamesToLatin() {
        assertEquals(NameNormalizer.skeleton("KAIS"), NameNormalizer.skeleton("𝗞Λ𝗜Ƨ"))
        assertTrue(NameNormalizer.namesMatch("ARTIST.E", "𝗔𝗥𝗧𝗜Ƨ𝗧.𝗘"))
    }

    @Test
    fun skeleton_catchesCyrillicHomoglyphs() {
        val cyrillic = "Сrеаtоr A" // С, е, а, о are Cyrillic
        assertNotEquals(NameNormalizer.normalize("Creator A"), NameNormalizer.normalize(cyrillic))
        assertEquals(NameNormalizer.skeleton("Creator A"), NameNormalizer.skeleton(cyrillic))
        assertTrue(NameNormalizer.namesMatch("Creator A", cyrillic))
        assertTrue(NameNormalizer.namesMatch("SELLER B", "ЅЕLLЕR В")) // Ѕ Е Е В
    }

    @Test
    fun skeleton_foldsDigitsDiacriticsSeparatorsAndCase() {
        assertEquals("creatoral", NameNormalizer.skeleton("creator.a_1"))
        assertTrue(NameNormalizer.namesMatch("Creat0r A", "creator a"))
        assertTrue(NameNormalizer.namesMatch("Créator", "Creator"))
        assertTrue(NameNormalizer.namesMatch("Iarry", "larry"))
        assertTrue(NameNormalizer.namesMatch("BRIAN", "Brian"))
        assertTrue(NameNormalizer.namesMatch("rnike", "mike"))
        assertTrue(NameNormalizer.namesMatch("seller-b", "SELLER B"))
        assertFalse(NameNormalizer.namesMatch("Creator A", "Creator C"))
        assertFalse(NameNormalizer.namesMatch("", ""))
        assertFalse(NameNormalizer.namesMatch(null, "x"))
    }

    @Test
    fun similarity_isJaroWinklerOnSkeletons() {
        assertEquals(1.0, NameNormalizer.similarity("Creator A", "Сrеаtоr A"), 1e-9)
        assertTrue(NameNormalizer.similarity("Creator A", "Creator AA") >= 0.92)
        assertTrue(NameNormalizer.similarity("Creator A", "Brand D") < 0.7)
        assertEquals(0.0, NameNormalizer.similarity("", "Creator A"), 0.0)
        assertEquals(
            NameNormalizer.similarity("martha", "marhta"),
            NameNormalizer.similarity("marhta", "martha"),
            1e-12,
        )
        assertEquals(0.961, NameNormalizer.similarity("martha", "marhta"), 0.001) // textbook Jaro-Winkler value
    }

    @Test
    fun normalizeHandle_stripsAtLowercasesAndValidates() {
        assertEquals("creator_a123", NameNormalizer.normalizeHandle("@Creator_A123"))
        assertEquals("abc.def", NameNormalizer.normalizeHandle("‎@abc.def "))
        assertEquals("user0000000000001", NameNormalizer.normalizeHandle("user0000000000001"))
        assertNull(NameNormalizer.normalizeHandle("@a"))
        assertNull(NameNormalizer.normalizeHandle("has space"))
        assertNull(NameNormalizer.normalizeHandle("a".repeat(25)))
        assertNull(NameNormalizer.normalizeHandle("emoji💎"))
        assertNull(NameNormalizer.normalizeHandle(null))
        assertNull(NameNormalizer.normalizeHandle(""))
    }

    @Test
    fun isDefaultHandle_matchesUserPlusSixOrMoreDigits() {
        assertTrue(NameNormalizer.isDefaultHandle("user0000000000001"))
        assertTrue(NameNormalizer.isDefaultHandle("@User123456"))
        assertFalse(NameNormalizer.isDefaultHandle("user12345"))
        assertFalse(NameNormalizer.isDefaultHandle("username"))
        assertFalse(NameNormalizer.isDefaultHandle("creator_a123"))
        assertFalse(NameNormalizer.isDefaultHandle(null))
    }

    @Test
    fun clean_keepsCaseAndEmoji_dropsInvisibles_returnsNullForBlank() {
        assertEquals("Contact 5 💎", NameNormalizer.clean("‎Contact 5 💎‎"))
        assertNull(NameNormalizer.clean(" "))
        assertNull(NameNormalizer.clean("‎​"))
        assertNull(NameNormalizer.clean(null))
    }
}
