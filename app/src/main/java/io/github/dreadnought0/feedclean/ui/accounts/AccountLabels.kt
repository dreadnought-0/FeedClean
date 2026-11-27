package io.github.dreadnought0.feedclean.ui.accounts

import androidx.annotation.StringRes
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.data.model.AccountStatus
import io.github.dreadnought0.feedclean.data.model.BlockMethod
import io.github.dreadnought0.feedclean.data.model.HandleSource
import io.github.dreadnought0.feedclean.data.model.SightingSource
import io.github.dreadnought0.feedclean.data.model.TagNames
import io.github.dreadnought0.feedclean.data.model.UserAction
import io.github.dreadnought0.feedclean.detect.Signals
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.ui.stats.UiText

// Enum → string-resource mappings for the account screens. Pure, so the choices are unit-tested.

/** Short status label, e.g. for the status pill and filter chips. */
@StringRes
fun AccountStatus.labelRes(): Int = when (this) {
    AccountStatus.SEEN -> R.string.screens_status_seen
    AccountStatus.BLOCKED -> R.string.screens_status_blocked
    AccountStatus.FALSE_POSITIVE -> R.string.screens_status_false_positive
    AccountStatus.UNBLOCKED_SUSPECTED -> R.string.screens_status_unblocked_suspected
}

/** Where a handle was learned. */
@StringRes
fun HandleSource.labelRes(): Int = when (this) {
    HandleSource.PROFILE -> R.string.screens_handle_source_profile
    HandleSource.DIALOG -> R.string.screens_handle_source_dialog
    HandleSource.SOUND_HEURISTIC -> R.string.screens_handle_source_sound
    HandleSource.RESOLVE -> R.string.screens_handle_source_resolve
    HandleSource.IMPORT -> R.string.screens_handle_source_import
}

/** Why a page was skipped. */
@StringRes
fun SkipReason.labelRes(): Int = when (this) {
    SkipReason.ORGANIC_SHOP -> R.string.screens_skip_reason_organic_shop
    SkipReason.PAID_AD -> R.string.screens_skip_reason_paid_ad
    SkipReason.PROMOTED -> R.string.screens_skip_reason_promoted
    SkipReason.LIVE_SHOPPING -> R.string.screens_skip_reason_live_shopping
}

/** How a block was made. */
@StringRes
fun BlockMethod.labelRes(): Int = when (this) {
    BlockMethod.ASSISTED -> R.string.screens_block_method_assisted
    BlockMethod.MANUAL -> R.string.screens_block_method_manual
    BlockMethod.IMPORTED -> R.string.screens_block_method_imported
}

/** Where a sighting came from (shown when it is not the overlay). */
@StringRes
fun SightingSource.labelRes(): Int = when (this) {
    SightingSource.OVERLAY -> R.string.screens_source_overlay
    SightingSource.SHARE -> R.string.screens_source_share
    SightingSource.RECON -> R.string.screens_source_recon
}

/** Timeline title of a sighting with this action. */
@StringRes
fun UserAction.timelineTitleRes(): Int = when (this) {
    UserAction.NONE -> R.string.screens_timeline_seen
    UserAction.BLOCK -> R.string.screens_timeline_block_tapped
    UserAction.SKIP -> R.string.screens_timeline_skipped
    UserAction.AUTO_SKIP -> R.string.screens_timeline_auto_skipped
    UserAction.NOT_INTERESTED -> R.string.screens_timeline_not_interested
    UserAction.LOG -> R.string.screens_timeline_logged
    UserAction.FALSE_POSITIVE -> R.string.screens_timeline_false_positive
}

/** Labels of the signals present, strongest first. */
fun signalLabelRes(signals: Signals): List<Int> = buildList {
    if (signals.isPaidAd) add(R.string.screens_signal_paid_ad)
    if (signals.isPromoted) add(R.string.screens_signal_promoted)
    if (signals.isOrganicShop) add(R.string.screens_signal_organic_shop)
    if (signals.hasShopAnchor) add(R.string.screens_signal_shop_anchor)
    if (signals.isShopCreator) add(R.string.screens_signal_shop_creator)
    if (signals.isLiveShopping) add(R.string.screens_signal_live_shopping)
    if (signals.isLiveEvent) add(R.string.screens_signal_live_event)
    if (signals.isLive) add(R.string.screens_signal_live)
}

private val knownTagLabels: Map<String, Int> = mapOf(
    TagNames.PAID_AD to R.string.screens_tag_paid_ad,
    TagNames.PROMOTED to R.string.screens_tag_promoted,
    TagNames.SHOP_ANCHOR to R.string.screens_tag_shop_anchor,
    TagNames.ORGANIC_SHOP to R.string.screens_tag_organic_shop,
    TagNames.LIVE_SHOPPING to R.string.screens_tag_live_shopping,
    TagNames.AI_GENERATED to R.string.screens_tag_ai_generated,
    TagNames.SEXUAL_CONTENT to R.string.screens_tag_sexual_content,
    TagNames.IMPERSONATOR to R.string.screens_tag_impersonator,
    TagNames.REPOSTED to R.string.screens_tag_reposted,
    TagNames.BOT_LIKE to R.string.screens_tag_bot_like,
)

/** Display label of a tag: a translated label for the default tags, otherwise the humanized name. */
fun tagLabel(name: String): UiText =
    knownTagLabels[name]?.let { UiText.Res(it) } ?: UiText.Plain(humanizeTag(name))

/** "made_up_tag" → "Made up tag". Blank stays blank. */
fun humanizeTag(name: String): String =
    name.replace('_', ' ').trim().replaceFirstChar { it.titlecase() }
