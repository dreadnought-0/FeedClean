package io.github.dreadnought0.feedclean.data.room

/**
 * SQL LIKE helpers. Queries using these patterns must declare `ESCAPE '\'`. Handles commonly contain "_" and
 * ".", so "_" must be escaped to match literally.
 */
internal object LikePatterns {
    /** `%text%` with `\`, `%` and `_` escaped. */
    fun contains(text: String): String = buildString(text.length + 2) {
        append('%')
        for (c in text) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
        append('%')
    }
}
