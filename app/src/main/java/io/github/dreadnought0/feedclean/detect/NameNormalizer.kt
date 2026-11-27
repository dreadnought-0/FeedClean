package io.github.dreadnought0.feedclean.detect

import java.text.Normalizer
import java.util.Locale

/**
 * Normalization and comparison of display names and handles.
 *
 * TikTok names arrive with styling: Unicode mathematical alphanumerics (𝗞Λ𝗜Ƨ), U+200E marks, zero-width
 * characters, emoji variation selectors, and look-alike letters from other scripts. Identity is keyed on the
 * handle; display-name comparisons are only ever "possible" matches.
 *
 * Pure Kotlin + `java.text`, safe on the JVM and on Android.
 */
object NameNormalizer {
    private val DEFAULT_HANDLE = Regex("^user\\d{6,}$")
    private val VALID_HANDLE = Regex("^[a-z0-9._]{2,24}$")
    private val SKELETON_DROPPED = setOf(' '.code, '.'.code, '_'.code, '-'.code)

    /**
     * True for invisible formatting code points: bidi controls (U+061C, U+200E, U+200F, U+202A–U+202E,
     * U+2066–U+2069), zero-width characters (U+200B–U+200D, U+2060, U+FEFF), soft hyphen, and variation
     * selectors (U+FE00–U+FE0F, U+E0100–U+E01EF).
     */
    fun isInvisible(codePoint: Int): Boolean = when (codePoint) {
        0x00AD, 0x061C, 0x200E, 0x200F, 0x2060, 0xFEFF -> true
        in 0x200B..0x200D, in 0x202A..0x202E, in 0x2066..0x2069 -> true
        in 0xFE00..0xFE0F, in 0xE0100..0xE01EF -> true
        else -> false
    }

    /** Removes [isInvisible] code points; everything else (case, spacing, emoji) is kept. */
    fun stripInvisible(s: String): String {
        if (s.none { isInvisible(it.code) || it.isSurrogate() }) return s
        val sb = StringBuilder(s.length)
        s.codePoints().forEach { if (!isInvisible(it)) sb.appendCodePoint(it) }
        return sb.toString()
    }

    /** [stripInvisible] + trim. Returns null for null or blank input. Used for raw on-screen text. */
    fun clean(s: String?): String? = s?.let { stripInvisible(it).trim() }?.ifEmpty { null }

    /**
     * Canonical comparable form: strip invisible characters, NFKC (folds mathematical alphanumerics, full-width
     * forms, ligatures), collapse all whitespace to single spaces, trim, lowercase (Locale.ROOT).
     */
    fun normalize(s: String): String {
        val nfkc = Normalizer.normalize(stripInvisible(s), Normalizer.Form.NFKC)
        return collapseWhitespace(stripInvisible(nfkc)).lowercase(Locale.ROOT)
    }

    /**
     * Confusables skeleton (a compact UTS #39 subset) for impersonator matching: [normalize]-style cleanup,
     * diacritics removed, common Cyrillic/Greek look-alikes and digit/symbol substitutions (0→o, 1→l, i→l, …)
     * folded to Latin, "rn"→"m", "vv"→"w", and spaces, `.`, `_`, `-` dropped. Case-insensitive.
     * Collisions are expected; a skeleton match only ever means "possible" impersonator.
     */
    fun skeleton(s: String): String {
        val nfkc = Normalizer.normalize(stripInvisible(s), Normalizer.Form.NFKC)
        val decomposed = Normalizer.normalize(nfkc, Normalizer.Form.NFD)
        val upperFolded = StringBuilder(decomposed.length)
        decomposed.codePoints().forEach { cp ->
            if (isInvisible(cp) || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()) return@forEach
            val mapped = UPPER_CONFUSABLES[cp]
            if (mapped != null) upperFolded.append(mapped) else upperFolded.appendCodePoint(cp)
        }
        val lower = upperFolded.toString().lowercase(Locale.ROOT)
        val out = StringBuilder(lower.length)
        lower.codePoints().forEach { cp ->
            if (cp in SKELETON_DROPPED || Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return@forEach
            val mapped = LOWER_CONFUSABLES[cp]
            if (mapped != null) out.append(mapped) else out.appendCodePoint(cp)
        }
        return out.toString().replace("rn", "m").replace("vv", "w")
    }

    /** Jaro-Winkler similarity of the two [skeleton]s, 0.0..1.0. Returns 0.0 if either skeleton is empty. */
    fun similarity(a: String, b: String): Double {
        val sa = skeleton(a)
        val sb = skeleton(b)
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        return jaroWinkler(sa, sb)
    }

    /** True when the two names have the same non-empty [skeleton] (a "possible" same-name match). */
    fun namesMatch(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val sa = skeleton(a)
        return sa.isNotEmpty() && sa == skeleton(b)
    }

    /**
     * Handle without "@", lowercase, or null if it isn't a plausible TikTok handle (2–24 of `a-z 0-9 . _`).
     */
    fun normalizeHandle(raw: String?): String? {
        val cleaned = clean(raw) ?: return null
        val nfkc = Normalizer.normalize(cleaned, Normalizer.Form.NFKC).trim()
        val handle = nfkc.removePrefix("@").lowercase(Locale.ROOT)
        return handle.takeIf { VALID_HANDLE.matches(it) }
    }

    /** True for TikTok's auto-assigned throwaway handles, `user` + 6 or more digits (e.g. `user0000000000001`). */
    fun isDefaultHandle(handle: String?): Boolean = normalizeHandle(handle)?.let { DEFAULT_HANDLE.matches(it) } == true

    private fun collapseWhitespace(s: String): String {
        val sb = StringBuilder(s.length)
        var pendingSpace = false
        s.codePoints().forEach { cp ->
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = sb.isNotEmpty()
            } else {
                if (pendingSpace) sb.append(' ')
                pendingSpace = false
                sb.appendCodePoint(cp)
            }
        }
        return sb.toString()
    }

    private fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        val window = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val start = (i - window).coerceAtLeast(0)
            val end = (i + window + 1).coerceAtMost(b.length)
            for (j in start until end) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }

    /** Non-Latin capitals whose look-alike differs from their lowercase form's (Greek Eta looks like H, not n). */
    private val UPPER_CONFUSABLES: Map<Int, String> = buildMap {
        put('Ƨ'.code, "s")
        // Greek capitals
        for ((from, to) in listOf(
            'Α' to "a", 'Β' to "b", 'Ε' to "e", 'Ζ' to "z", 'Η' to "h", 'Ι' to "l", 'Κ' to "k", 'Λ' to "a",
            'Μ' to "m", 'Ν' to "n", 'Ο' to "o", 'Ρ' to "p", 'Τ' to "t", 'Υ' to "y", 'Χ' to "x",
        )) put(from.code, to)
        // Cyrillic capitals
        for ((from, to) in listOf(
            'А' to "a", 'В' to "b", 'Е' to "e", 'К' to "k", 'М' to "m", 'Н' to "h", 'О' to "o", 'Р' to "p",
            'С' to "c", 'Т' to "t", 'Х' to "x", 'У' to "y", 'Ѕ' to "s", 'І' to "l", 'Ј' to "j", 'Ӏ' to "l",
            'Ү' to "y", 'Һ' to "h", 'Ԁ' to "d", 'Ԛ' to "q", 'Ԝ' to "w",
        )) put(from.code, to)
    }

    /**
     * Applied after lowercasing. `i` folds to `l` (with 1, |, ı, …) so capital I / lowercase l swaps are caught
     * while the skeleton stays case-insensitive.
     */
    private val LOWER_CONFUSABLES: Map<Int, String> = buildMap {
        put('i'.code, "l")
        // Cyrillic
        for ((from, to) in listOf(
            'а' to "a", 'в' to "b", 'г' to "r", 'е' to "e", 'к' to "k", 'м' to "m", 'н' to "h", 'о' to "o",
            'п' to "n", 'р' to "p", 'с' to "c", 'т' to "t", 'у' to "y", 'х' to "x", 'ѕ' to "s", 'і' to "l",
            'ј' to "j", 'һ' to "h", 'ԁ' to "d", 'ԛ' to "q", 'ԝ' to "w", 'ӏ' to "l", 'ь' to "b", 'ү' to "y",
        )) put(from.code, to)
        // Greek
        for ((from, to) in listOf(
            'α' to "a", 'β' to "b", 'γ' to "y", 'ε' to "e", 'η' to "n", 'ι' to "l", 'κ' to "k", 'λ' to "a",
            'μ' to "u", 'ν' to "v", 'ο' to "o", 'ρ' to "p", 'τ' to "t", 'υ' to "u", 'χ' to "x", 'ω' to "w",
            'ς' to "s",
        )) put(from.code, to)
        // Latin look-alikes, digits, symbols
        for ((from, to) in listOf(
            'ƨ' to "s", 'ı' to "l", 'ɡ' to "g", 'ℓ' to "l", '0' to "o", '1' to "l", '3' to "e", '4' to "a",
            '5' to "s", '7' to "t", '8' to "b", '@' to "a", '$' to "s", '|' to "l", '€' to "e",
        )) put(from.code, to)
    }
}
