package io.github.dreadnought0.feedclean.ui.home

import androidx.annotation.StringRes
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.armed.Behaviour
import io.github.dreadnought0.feedclean.armed.DetectionKind
import io.github.dreadnought0.feedclean.armed.Requirement
import io.github.dreadnought0.feedclean.ui.settings.SettingsActions

/** User-facing name of a behaviour. */
@StringRes
fun Behaviour.label(): Int = when (this) {
    Behaviour.SKIP_SHOP -> R.string.behaviour_skip_shop
    Behaviour.SKIP_PAID_AD -> R.string.behaviour_skip_paid_ad
    Behaviour.SKIP_PROMOTED -> R.string.behaviour_skip_promoted
    Behaviour.SKIP_LIVE_SHOPPING -> R.string.behaviour_skip_live_shopping
    Behaviour.AUTO_NOT_INTERESTED -> R.string.behaviour_auto_ni
    Behaviour.NOT_INTERESTED_BUTTON -> R.string.behaviour_ni_button
    Behaviour.BLOCK_BUTTON -> R.string.behaviour_block_button
}

/** User-facing name of a detection kind (same titles as Detection rules). */
@StringRes
fun DetectionKind.label(): Int = when (this) {
    DetectionKind.SHOP -> R.string.rules_shop
    DetectionKind.PAID_AD -> R.string.rules_paid
    DetectionKind.PROMOTED -> R.string.rules_promoted
    DetectionKind.LIVE_SHOPPING -> R.string.rules_live
}

/** The reason line when this requirement is the one holding a behaviour back. */
@StringRes
fun Requirement.unmetText(): Int = when (this) {
    Requirement.SERVICE_RUNNING -> R.string.req_unmet_service
    Requirement.DETECTION_ON -> R.string.req_unmet_detection
    Requirement.ANY_DETECTION_KIND_ON -> R.string.req_unmet_any_kind
    Requirement.DETECT_PAID_AD_ON -> R.string.req_unmet_detect_paid
    Requirement.DETECT_PROMOTED_ON -> R.string.req_unmet_detect_promoted
    Requirement.DETECT_SHOP_ON -> R.string.req_unmet_detect_shop
    Requirement.DETECT_LIVE_SHOPPING_ON -> R.string.req_unmet_detect_live
    Requirement.AUTO_SKIP_ON -> R.string.req_unmet_auto_skip
    Requirement.PLACE_SELECTED -> R.string.req_unmet_place
    Requirement.SKIP_TYPE_READY -> R.string.req_unmet_skip_type
    Requirement.SKIP_SHOP_ON -> R.string.req_unmet_skip_shop
    Requirement.RISK_ACCEPTED -> R.string.req_unmet_risk
    Requirement.BUBBLE_SHOWN -> R.string.req_unmet_bubble
    Requirement.NOT_PAUSED_BY_USER -> R.string.req_unmet_paused
    Requirement.NOT_PAUSED_BY_SAFETY_STOP -> R.string.req_unmet_safety_stop
}

/** The checklist line when the requirement is met. [R.string.req_met_place] takes the place list. */
@StringRes
fun Requirement.metText(): Int = when (this) {
    Requirement.SERVICE_RUNNING -> R.string.req_met_service
    Requirement.DETECTION_ON -> R.string.req_met_detection
    Requirement.ANY_DETECTION_KIND_ON -> R.string.req_met_any_kind
    Requirement.DETECT_PAID_AD_ON -> R.string.req_met_detect_paid
    Requirement.DETECT_PROMOTED_ON -> R.string.req_met_detect_promoted
    Requirement.DETECT_SHOP_ON -> R.string.req_met_detect_shop
    Requirement.DETECT_LIVE_SHOPPING_ON -> R.string.req_met_detect_live
    Requirement.AUTO_SKIP_ON -> R.string.req_met_auto_skip
    Requirement.PLACE_SELECTED -> R.string.req_met_place
    Requirement.SKIP_TYPE_READY -> R.string.req_met_skip_type
    Requirement.SKIP_SHOP_ON -> R.string.req_met_skip_shop
    Requirement.RISK_ACCEPTED -> R.string.req_met_risk
    Requirement.BUBBLE_SHOWN -> R.string.req_met_bubble
    Requirement.NOT_PAUSED_BY_USER -> R.string.req_met_not_paused
    Requirement.NOT_PAUSED_BY_SAFETY_STOP -> R.string.req_met_no_safety_stop
}

/** Full-width fix button in the sheet. */
@StringRes
fun Requirement.fixText(): Int = when (this) {
    Requirement.SERVICE_RUNNING -> R.string.req_fix_service
    Requirement.DETECTION_ON -> R.string.req_fix_detection
    Requirement.ANY_DETECTION_KIND_ON -> R.string.req_fix_any_kind
    Requirement.DETECT_PAID_AD_ON -> R.string.req_fix_detect_paid
    Requirement.DETECT_PROMOTED_ON -> R.string.req_fix_detect_promoted
    Requirement.DETECT_SHOP_ON -> R.string.req_fix_detect_shop
    Requirement.DETECT_LIVE_SHOPPING_ON -> R.string.req_fix_detect_live
    Requirement.AUTO_SKIP_ON -> R.string.req_fix_auto_skip
    Requirement.PLACE_SELECTED -> R.string.req_fix_place
    Requirement.SKIP_TYPE_READY -> R.string.req_fix_skip_type
    Requirement.SKIP_SHOP_ON -> R.string.req_fix_skip_shop
    Requirement.RISK_ACCEPTED -> R.string.req_fix_risk
    Requirement.BUBBLE_SHOWN -> R.string.req_fix_bubble
    Requirement.NOT_PAUSED_BY_USER -> R.string.req_fix_paused
    Requirement.NOT_PAUSED_BY_SAFETY_STOP -> R.string.req_fix_safety_stop
}

/** Short link text on a Blocked / Paused bar ("Turn on ›"); null when the fix is just "look below". */
@StringRes
fun Requirement.shortFixText(): Int? = when (this) {
    Requirement.SERVICE_RUNNING -> R.string.req_fix_short_setup
    Requirement.DETECTION_ON,
    Requirement.DETECT_PAID_AD_ON,
    Requirement.DETECT_PROMOTED_ON,
    Requirement.DETECT_SHOP_ON,
    Requirement.DETECT_LIVE_SHOPPING_ON,
    Requirement.AUTO_SKIP_ON,
    Requirement.SKIP_SHOP_ON,
    -> R.string.req_fix_short_turn_on
    Requirement.ANY_DETECTION_KIND_ON -> R.string.req_fix_short_open
    Requirement.PLACE_SELECTED -> R.string.req_fix_short_pick
    Requirement.SKIP_TYPE_READY -> null
    Requirement.RISK_ACCEPTED -> R.string.req_fix_short_read
    Requirement.BUBBLE_SHOWN -> R.string.req_fix_short_show
    Requirement.NOT_PAUSED_BY_USER, Requirement.NOT_PAUSED_BY_SAFETY_STOP -> R.string.req_fix_short_resume
}

/** Where a fix leads when it isn't a plain settings edit. */
enum class FixNavigation { SETUP, DETECTION_RULES, WHERE_IT_SKIPS }

/** Callbacks a fix may need. */
class FixHandlers(
    val actions: SettingsActions,
    val navigate: (FixNavigation) -> Unit,
    val resumeAfterSafetyStop: () -> Unit,
)

/**
 * Applies the fix for this requirement: a settings edit (turning the missing switch on), a navigation, or the
 * resume command. Turning on automated Not interested goes through the risk dialog ([SettingsActions.requestNotInterestedAutomated]).
 */
fun Requirement.applyFix(h: FixHandlers) {
    val a = h.actions
    when (this) {
        Requirement.SERVICE_RUNNING -> h.navigate(FixNavigation.SETUP)
        Requirement.DETECTION_ON -> a.setDetectionEnabled(true)
        Requirement.ANY_DETECTION_KIND_ON -> h.navigate(FixNavigation.DETECTION_RULES)
        Requirement.DETECT_PAID_AD_ON -> a.setDetectPaidAd(true)
        Requirement.DETECT_PROMOTED_ON -> a.setDetectPromoted(true)
        Requirement.DETECT_SHOP_ON -> a.setDetectShopAnchor(true)
        Requirement.DETECT_LIVE_SHOPPING_ON -> a.setDetectLiveShopping(true)
        Requirement.AUTO_SKIP_ON -> a.setCurateFeedEnabled(true)
        Requirement.PLACE_SELECTED -> h.navigate(FixNavigation.WHERE_IT_SKIPS)
        Requirement.SKIP_TYPE_READY -> Unit
        Requirement.SKIP_SHOP_ON -> a.setAutoSkipOrganicShop(true)
        Requirement.RISK_ACCEPTED -> a.requestNotInterestedAutomated(true)
        Requirement.BUBBLE_SHOWN -> a.setShowBubble(true)
        Requirement.NOT_PAUSED_BY_USER -> a.setKillSwitchEngaged(false)
        Requirement.NOT_PAUSED_BY_SAFETY_STOP -> h.resumeAfterSafetyStop()
    }
}
