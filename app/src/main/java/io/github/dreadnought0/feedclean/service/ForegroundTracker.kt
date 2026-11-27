package io.github.dreadnought0.feedclean.service

/**
 * Decides whether TikTok is the app in front, from the interactive window list. Pure state machine.
 *
 * TikTok is foreground when one of its application windows is active or focused. Accessibility overlays
 * (FeedClean's own windows) never count either way.
 *
 * The service only receives TikTok's events, so while TikTok is believed foreground the runtime re-checks the
 * window list about once a second. To avoid flicker during transitions:
 *  - entering the foreground is immediate;
 *  - leaving is immediate when another app's window is clearly in front (active and focused);
 *  - otherwise (empty or unreadable window list) it takes [missesToLeave] consecutive misses.
 *
 * @param targetPackage TikTok's package (from the selector config).
 */
class ForegroundTracker(
    private val targetPackage: () -> String?,
    private val missesToLeave: Int = DEFAULT_MISSES_TO_LEAVE,
) {
    /** Current belief. Starts false. */
    var isForeground: Boolean = false
        private set

    private var misses = 0

    /**
     * Feeds one window-list reading. Returns the new value of [isForeground] if it changed, else null.
     */
    fun update(windows: List<WindowFacts>): Boolean? {
        val before = isForeground
        when (classify(windows, targetPackage())) {
            Reading.TARGET_IN_FRONT -> {
                misses = 0
                isForeground = true
            }
            Reading.OTHER_IN_FRONT -> {
                misses = 0
                isForeground = false
            }
            Reading.UNCLEAR -> {
                misses++
                if (misses >= missesToLeave) isForeground = false
            }
        }
        return isForeground.takeIf { it != before }
    }

    /** Forgets everything (service restart). */
    fun reset() {
        isForeground = false
        misses = 0
    }

    /** What one window-list reading says. */
    enum class Reading { TARGET_IN_FRONT, OTHER_IN_FRONT, UNCLEAR }

    companion object {
        const val DEFAULT_MISSES_TO_LEAVE = 2

        /** Classifies one reading of the window list. */
        fun classify(windows: List<WindowFacts>, targetPackage: String?): Reading {
            if (targetPackage.isNullOrBlank()) return Reading.UNCLEAR
            val apps = windows.filter { it.kind == WindowKind.APPLICATION && (it.isActive || it.isFocused) }
            if (apps.any { it.packageName == targetPackage }) return Reading.TARGET_IN_FRONT
            val other = apps.any { it.isActive && it.isFocused && it.packageName != null }
            val systemInFront = windows.any { it.kind == WindowKind.SYSTEM && it.isActive && it.isFocused }
            return if (other || systemInFront) Reading.OTHER_IN_FRONT else Reading.UNCLEAR
        }
    }
}
