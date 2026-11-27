package io.github.dreadnought0.feedclean.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.settings.BlockStyle
import io.github.dreadnought0.feedclean.settings.CoverStyle
import io.github.dreadnought0.feedclean.settings.NotInterestedReason

// Radio options for the enum-valued settings. `entries` order is the display order, and the `when`s are
// exhaustive, so adding an enum entry without a label is a compile error.

/** Cover style choices (Blank / Blur / Labelled). */
@Composable
fun coverStyleOptions(): List<RadioOption<CoverStyle>> = CoverStyle.entries.map { style ->
    when (style) {
        CoverStyle.BLANK -> RadioOption(
            style,
            stringResource(R.string.ui_settings_cover_blank),
            stringResource(R.string.ui_settings_cover_blank_desc),
        )

        CoverStyle.BLUR -> RadioOption(
            style,
            stringResource(R.string.ui_settings_cover_blur),
            stringResource(R.string.ui_settings_cover_blur_desc),
        )

        CoverStyle.LABELLED -> RadioOption(
            style,
            stringResource(R.string.ui_settings_cover_labelled),
            stringResource(R.string.ui_settings_cover_labelled_desc),
        )
    }
}

/** Block style choices (one-tap full flow / stop at the confirmation dialog). */
@Composable
fun blockStyleOptions(): List<RadioOption<BlockStyle>> = BlockStyle.entries.map { style ->
    when (style) {
        BlockStyle.ONE_TAP_FULL_FLOW -> RadioOption(
            style,
            stringResource(R.string.ui_settings_block_one_tap),
            stringResource(R.string.ui_settings_block_one_tap_desc),
        )

        BlockStyle.STOP_AT_TIKTOK_DIALOG -> RadioOption(
            style,
            stringResource(R.string.ui_settings_block_stop_at_dialog),
            stringResource(R.string.ui_settings_block_stop_at_dialog_desc),
        )
    }
}

/** Preferred "Not interested" reason choices. */
@Composable
fun notInterestedReasonOptions(): List<RadioOption<NotInterestedReason>> =
    NotInterestedReason.entries.map { reason ->
        when (reason) {
            NotInterestedReason.FIRST_OFFERED -> RadioOption(
                reason,
                stringResource(R.string.ui_settings_reason_first),
                stringResource(R.string.ui_settings_reason_first_desc),
            )

            NotInterestedReason.PREFER_SHOPPING -> RadioOption(
                reason,
                stringResource(R.string.ui_settings_reason_shopping),
                stringResource(R.string.ui_settings_reason_shopping_desc),
            )

            NotInterestedReason.SKIP_REASON -> RadioOption(
                reason,
                stringResource(R.string.ui_settings_reason_skip),
                stringResource(R.string.ui_settings_reason_skip_desc),
            )
        }
    }
