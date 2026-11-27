package io.github.dreadnought0.feedclean.ui.setup

/**
 * Result of one pass over the system checks on the Setup checklist. Service *connection* is
 * not here: it is live state from `ScreenStateHolder.serviceConnected`.
 */
data class SetupStatus(
    /** The target app's package is installed and visible (manifest `<queries>`). */
    val targetAppInstalled: Boolean,
    /** FeedClean's accessibility service is listed in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. */
    val serviceEnabled: Boolean,
    /** FeedClean is exempt from battery optimizations ("Unrestricted"). */
    val batteryUnrestricted: Boolean,
) {
    companion object {
        /** Used before the first check has run, and in previews. */
        val Unknown = SetupStatus(targetAppInstalled = false, serviceEnabled = false, batteryUnrestricted = false)
    }
}

/**
 * True if the colon-separated [enabledServices] list (the value of
 * `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`) contains the component [packageName]/[className].
 * Accepts both the full (`pkg/pkg.Cls`) and the short (`pkg/.Cls`) flattened forms, case-insensitively
 * like the platform does.
 */
fun isAccessibilityServiceEnabled(enabledServices: String?, packageName: String, className: String): Boolean {
    if (enabledServices.isNullOrBlank()) return false
    return enabledServices.split(':').any { entry ->
        val slash = entry.indexOf('/')
        if (slash <= 0) return@any false
        val pkg = entry.substring(0, slash).trim()
        var cls = entry.substring(slash + 1).trim()
        if (cls.startsWith('.')) cls = pkg + cls
        pkg.equals(packageName, ignoreCase = true) && cls.equals(className, ignoreCase = true)
    }
}
