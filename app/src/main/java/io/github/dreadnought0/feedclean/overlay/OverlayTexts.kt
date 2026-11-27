package io.github.dreadnought0.feedclean.overlay

import android.content.Context
import androidx.annotation.StringRes
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.block.BlockStep
import io.github.dreadnought0.feedclean.curate.NotInterestedStep
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.service.DetectionRules

/** String lookups for overlay models. Plain Context-based so the bubble hint (not Compose) can use them too. */
internal object OverlayTexts {

    fun message(context: Context, m: ActionMessage): String {
        val arg = m.arg.orEmpty()
        return when (m.type) {
            ActionMessage.Type.BLOCKED -> context.getString(R.string.overlay_result_blocked, arg)
            ActionMessage.Type.ALREADY_BLOCKED -> context.getString(R.string.overlay_result_already_blocked, arg)
            ActionMessage.Type.BLOCK_STOPPED -> context.getString(R.string.overlay_result_block_stopped, arg)
            ActionMessage.Type.BLOCK_OVERLAY_NOT_DISMISSED -> context.getString(R.string.overlay_result_block_overlay_not_dismissed)
            ActionMessage.Type.BLOCK_USER_CANCELLED -> context.getString(R.string.overlay_result_block_user_cancelled)
            ActionMessage.Type.BLOCK_TIMED_OUT -> context.getString(R.string.overlay_result_block_timed_out)
            ActionMessage.Type.BLOCK_CANCELLED -> context.getString(R.string.overlay_result_block_cancelled)
            ActionMessage.Type.NOT_INTERESTED_DONE -> context.getString(R.string.overlay_result_ni_done)
            ActionMessage.Type.NOT_INTERESTED_UNVERIFIED -> context.getString(R.string.overlay_result_ni_unverified)
            ActionMessage.Type.NOT_INTERESTED_STOPPED -> context.getString(R.string.overlay_result_ni_stopped, arg)
            ActionMessage.Type.RATE_LIMITED -> context.getString(R.string.overlay_result_rate_limited, arg)
            ActionMessage.Type.REJECTED -> context.getString(R.string.overlay_result_rejected, arg)
            ActionMessage.Type.SKIPPED -> context.getString(R.string.overlay_result_skipped)
            ActionMessage.Type.SKIP_NOT_ADVANCED -> context.getString(R.string.overlay_result_skip_not_advanced)
            ActionMessage.Type.SKIP_NOT_DONE -> context.getString(R.string.overlay_result_skip_not_done, arg)
            ActionMessage.Type.LOGGED -> context.getString(R.string.overlay_result_logged)
            ActionMessage.Type.FALSE_POSITIVE -> context.getString(R.string.overlay_result_false_positive)
            ActionMessage.Type.CAPTURE_SAVED -> context.getString(R.string.overlay_result_capture_saved, arg)
            ActionMessage.Type.CAPTURE_FAILED -> context.getString(R.string.overlay_result_capture_failed)
            ActionMessage.Type.ACTION_BUSY -> context.getString(R.string.overlay_result_busy)
            ActionMessage.Type.STOPPED_BY_USER -> context.getString(R.string.overlay_result_stopped_by_user)
            ActionMessage.Type.KILL_SWITCH_ON -> context.getString(R.string.overlay_hint_kill_on)
            ActionMessage.Type.KILL_SWITCH_OFF -> context.getString(R.string.overlay_hint_kill_off)
            ActionMessage.Type.AUTO_SKIP_RESUMED -> context.getString(R.string.overlay_hint_resumed)
            ActionMessage.Type.NO_POST -> context.getString(R.string.overlay_result_no_post)
            ActionMessage.Type.LIVE_PREVIEW_UNSUPPORTED -> context.getString(R.string.overlay_result_live_preview)
        }
    }

    /** Progress text for an executor step name (BlockStep or NotInterestedStep). */
    @StringRes
    fun step(kind: CardActionKind, stepName: String?): Int {
        if (stepName == null) return R.string.overlay_step_working
        return when (kind) {
            CardActionKind.BLOCK -> when (enumValueOrNull<BlockStep>(stepName)) {
                BlockStep.CHECK_RATE_LIMIT -> R.string.overlay_step_rate_limit
                BlockStep.OPEN_PROFILE -> R.string.overlay_step_open_profile
                BlockStep.READ_HANDLE -> R.string.overlay_step_read_handle
                BlockStep.OPEN_SHARE_SHEET -> R.string.overlay_step_open_share
                BlockStep.TAP_BLOCK -> R.string.overlay_step_tap_block
                BlockStep.CHECK_DIALOG -> R.string.overlay_step_check_dialog
                BlockStep.AWAITING_USER_CONFIRMATION -> R.string.overlay_step_await_user
                BlockStep.CONFIRM -> R.string.overlay_step_confirm
                BlockStep.VERIFY -> R.string.overlay_step_verify
                BlockStep.RETURN -> R.string.overlay_step_return
                BlockStep.DISMISS_OVERLAY -> R.string.overlay_step_dismiss_overlay
                null -> R.string.overlay_step_working
            }
            CardActionKind.NOT_INTERESTED -> when (enumValueOrNull<NotInterestedStep>(stepName)) {
                NotInterestedStep.CHECK_RATE_LIMIT -> R.string.overlay_step_rate_limit
                NotInterestedStep.FIND_VIDEO -> R.string.overlay_step_find_video
                NotInterestedStep.LONG_PRESS -> R.string.overlay_step_long_press
                NotInterestedStep.TAP_NOT_INTERESTED -> R.string.overlay_step_tap_not_interested
                NotInterestedStep.CHOOSE_REASON -> R.string.overlay_step_choose_reason
                NotInterestedStep.VERIFY -> R.string.overlay_step_verify
                null -> R.string.overlay_step_working
            }
            else -> R.string.overlay_step_working
        }
    }

    @StringRes
    fun kind(kind: DetectionRules.Kind): Int = when (kind) {
        DetectionRules.Kind.PAID_AD -> R.string.overlay_kind_paid_ad
        DetectionRules.Kind.PROMOTED -> R.string.overlay_kind_promoted
        DetectionRules.Kind.SHOP -> R.string.overlay_kind_shop
        DetectionRules.Kind.LIVE_SHOPPING -> R.string.overlay_kind_live_shopping
    }

    @StringRes
    fun tag(tag: String): Int? = when (tag) {
        TagNames.AI_GENERATED -> R.string.overlay_tag_ai_generated
        TagNames.SEXUAL_CONTENT -> R.string.overlay_tag_sexual_content
        TagNames.IMPERSONATOR -> R.string.overlay_tag_impersonator
        TagNames.REPOSTED -> R.string.overlay_tag_reposted
        TagNames.BOT_LIKE -> R.string.overlay_tag_bot_like
        else -> null
    }

    private inline fun <reified E : Enum<E>> enumValueOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
