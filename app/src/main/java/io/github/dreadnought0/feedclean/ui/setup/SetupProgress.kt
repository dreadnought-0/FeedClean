package io.github.dreadnought0.feedclean.ui.setup

import io.github.dreadnought0.feedclean.armed.HeroOutline

/**
 * A required Setup-checklist item that must be done before FeedClean can run. The list is
 * the same one the Setup screen counts in "N steps to go" (info-only cards are not steps).
 */
enum class SetupStepId {
    /** The target app (TikTok) is installed and visible to FeedClean. */
    TARGET_APP,
    /** Accessibility access is switched on in system settings and the service is actually running. */
    ACCESSIBILITY,
    /** Battery is Unrestricted, so the system will not put FeedClean to sleep. */
    BATTERY,
}

/** One required step and whether it is currently met. */
data class SetupStep(val id: SetupStepId, val done: Boolean)

/**
 * The required Setup steps, in checklist order, with a completed/total count.
 *
 * Built from the same sources as the Setup screen: [SetupStatus] (target app, service enabled,
 * battery) and `serviceConnected` (the service is bound and running — the same fact
 * ArmedEvaluator uses for [io.github.dreadnought0.feedclean.armed.Requirement.SERVICE_RUNNING]).
 */
data class SetupProgress(val steps: List<SetupStep>) {
    val completed: Int get() = steps.count { it.done }
    val total: Int get() = steps.size
    val allComplete: Boolean get() = steps.isNotEmpty() && steps.all { it.done }

    /** Accessibility access (enabled and running) and Battery: Unrestricted are both done. */
    val accessibilityAndBatteryComplete: Boolean
        get() = done(SetupStepId.ACCESSIBILITY) && done(SetupStepId.BATTERY)

    fun done(id: SetupStepId): Boolean = steps.first { it.id == id }.done
}

/** The required Setup steps and whether each one is met right now. */
fun setupProgress(status: SetupStatus, serviceConnected: Boolean): SetupProgress = SetupProgress(
    listOf(
        SetupStep(SetupStepId.TARGET_APP, status.targetAppInstalled),
        SetupStep(SetupStepId.ACCESSIBILITY, status.serviceEnabled && serviceConnected),
        SetupStep(SetupStepId.BATTERY, status.batteryUnrestricted),
    ),
)

/**
 * The Home hero loads collapsed when every required step is complete — FeedClean has the
 * accessibility and battery settings it needs, and TikTok is installed. Otherwise it loads
 * expanded. Expansion itself is in-session UI state only.
 */
fun heroStartsCollapsed(progress: SetupProgress): Boolean = progress.allComplete

/**
 * Home hero outline, from setup completeness (the hero is a steps panel, not an Auto-skip badge):
 *  - [HeroOutline.READY] (accent teal) when every required step is complete.
 *  - [HeroOutline.SETUP_MISSING] (red) when one or more required steps are not done.
 */
fun heroOutline(progress: SetupProgress): HeroOutline =
    if (progress.allComplete) HeroOutline.READY else HeroOutline.SETUP_MISSING
