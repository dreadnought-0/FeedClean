package io.github.dreadnought0.feedclean.data.model

/** Handle normalization rules shared by every repository. */
object Handles {
    private val defaultHandle = Regex("^user\\d{6,}$")

    /** Lowercase, without a leading "@", trimmed. Returns null when nothing is left. */
    fun normalize(raw: String?): String? =
        raw?.trim()?.removePrefix("@")?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** True for TikTok default/throwaway handles (`user` followed by 6 or more digits). */
    fun isDefault(handle: String): Boolean = defaultHandle.matches(handle)
}
