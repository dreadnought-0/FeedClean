package io.github.dreadnought0.feedclean.selectors

/**
 * Parsed `assets/selectors/tiktok.json` (or a user override file). The single source of truth for every TikTok
 * string, content-desc, and view ID. Build one with [SelectorConfigLoader].
 *
 * Immutable and thread-safe; regexes are compiled once at load time.
 *
 * @property schemaVersion `schema_version` of the file. Files newer than [SUPPORTED_SCHEMA_VERSION] still load
 *   (unknown fields are ignored) but add a warning.
 * @property packageName TikTok package the selectors apply to. The parser ignores windows of other packages.
 * @property verifiedAppVersions TikTok versions these selectors were checked against.
 * @property warnings Problems found while loading (bad regex, malformed key, …). Each affected strategy or key
 *   is dropped; nothing else is affected. Shown on the Debug screen.
 */
class SelectorConfig(
    val schemaVersion: Int,
    val packageName: String,
    val verifiedAppVersions: List<String>,
    val locales: Map<String, LocaleSelectors>,
    val warnings: List<String> = emptyList(),
) {
    /**
     * Selectors for a locale: exact tag ("en-US"), then its language ("en"), then [DEFAULT_LOCALE], then any
     * locale, then an empty set (which matches nothing).
     */
    fun forLocale(tag: String = DEFAULT_LOCALE): LocaleSelectors {
        val normalized = tag.replace('_', '-')
        return locales[normalized]
            ?: locales[normalized.substringBefore('-').lowercase()]
            ?: locales[DEFAULT_LOCALE]
            ?: locales.values.firstOrNull()
            ?: LocaleSelectors.EMPTY
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val DEFAULT_LOCALE = "en"

        /** A config with no selectors: every lookup degrades to "no match". */
        val EMPTY = SelectorConfig(0, "", emptyList(), emptyMap())
    }
}

/**
 * The selector keys of one locale.
 *
 * @property strategies key → ordered strategies (first match wins).
 * @property labels key → label list (e.g. `feed.paidAdLabels`).
 */
class LocaleSelectors(
    val locale: String,
    strategies: Map<String, List<SelectorStrategy>>,
    val labels: Map<String, List<String>>,
    warningSink: MutableList<String>? = null,
) {
    val strategies: Map<String, List<SelectorStrategy>> = strategies

    internal val compiled: Map<String, List<CompiledStrategy>> = strategies.mapValues { (key, list) ->
        list.mapIndexed { i, s -> CompiledStrategy.compile(key, i, s, warningSink) }
    }

    /** True if [key] exists as a strategy list or a label list. */
    fun hasKey(key: String): Boolean = key in strategies || key in labels

    companion object {
        val EMPTY = LocaleSelectors("", emptyMap(), emptyMap())
    }
}

/** A strategy with its regexes compiled. An invalid regex makes the strategy permanently non-matching. */
internal class CompiledStrategy(
    val strategy: SelectorStrategy,
    val descRegex: Regex?,
    val textRegex: Regex?,
    val isValid: Boolean,
) {
    companion object {
        fun compile(key: String, index: Int, s: SelectorStrategy, warnings: MutableList<String>?): CompiledStrategy {
            var valid = s.hasPredicate
            if (!s.hasPredicate) warnings?.add("$key[$index]: strategy has no predicate; ignored")
            fun compileOrNull(pattern: String?, field: String): Regex? {
                if (pattern == null) return null
                return try {
                    Regex(pattern)
                } catch (e: IllegalArgumentException) {
                    warnings?.add("$key[$index]: invalid $field '$pattern': ${e.message?.lineSequence()?.first()}")
                    valid = false
                    null
                }
            }
            val desc = compileOrNull(s.descRegex, "descRegex")
            val text = compileOrNull(s.textRegex, "textRegex")
            return CompiledStrategy(s, desc, text, valid)
        }
    }
}
