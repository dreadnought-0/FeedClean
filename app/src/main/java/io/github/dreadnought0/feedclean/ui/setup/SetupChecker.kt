package io.github.dreadnought0.feedclean.ui.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.service.FeedCleanAccessibilityService

/**
 * Reads the system state behind the Setup checklist and opens the matching system screens. All checks are
 * local reads of this phone's settings; nothing is sent anywhere.
 */
class SetupChecker(context: Context) {
    private val appContext = context.applicationContext

    /** Runs every check. Cheap; call it on each resume. */
    fun check(): SetupStatus = SetupStatus(
        targetAppInstalled = isTargetAppInstalled(),
        serviceEnabled = isServiceEnabled(),
        batteryUnrestricted = isBatteryUnrestricted(),
    )

    /** True if the target app's package is installed. */
    fun isTargetAppInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(targetPackage(), PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** True if FeedClean's accessibility service is switched on in system settings. */
    fun isServiceEnabled(): Boolean = isAccessibilityServiceEnabled(
        enabledServices = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ),
        packageName = appContext.packageName,
        className = FeedCleanAccessibilityService::class.java.name,
    )

    /** True if FeedClean is exempt from battery optimizations. */
    fun isBatteryUnrestricted(): Boolean =
        appContext.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(appContext.packageName) == true

    private fun targetPackage(): String = appContext.getString(R.string.ui_target_app_package)
}

/** Intents for the system screens the checklist links to. Launch them with [launchSystemScreen]. */
object SetupIntents {
    /** Accessibility settings, where the user switches FeedClean on. */
    fun accessibilitySettings(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** System dialog asking to exempt FeedClean from battery optimizations. */
    fun requestIgnoreBatteryOptimizations(packageName: String): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))

    /** Fallback: the list of battery-optimization exemptions. */
    fun batteryOptimizationSettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** FeedClean's own App info page (⋮ → Allow restricted settings lives here). */
    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
}

/**
 * Starts [intent]; if no activity handles it, tries each of [fallbacks] in order. Returns false if none could
 * be started.
 */
fun Context.launchSystemScreen(intent: Intent, vararg fallbacks: Intent): Boolean {
    for (candidate in listOf(intent, *fallbacks)) {
        try {
            startActivity(candidate)
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        } catch (_: SecurityException) {
            // Some OEM builds guard these screens; try the next one.
        }
    }
    return false
}
