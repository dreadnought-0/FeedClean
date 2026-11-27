package io.github.dreadnought0.feedclean.overlay

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.curate.SkipCover
import io.github.dreadnought0.feedclean.detect.Bounds
import io.github.dreadnought0.feedclean.detect.SkipReason
import io.github.dreadnought0.feedclean.service.RuntimeLog
import io.github.dreadnought0.feedclean.settings.CoverStyle

/**
 * The skip-cover window: a `TYPE_ACCESSIBILITY_OVERLAY` sized to the feed page, with
 * FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE so it never blocks the user's touches or takes focus.
 *
 * Styles: BLANK (black, like TikTok's own video background), BLUR (blur-behind where the system supports
 * cross-window blur, else a heavy dim), LABELLED (black + "Skipped a Shop post"). Plain Views, not Compose, so
 * it appears on the next frame. Main thread only.
 *
 * To read as a normal quick scroll rather than "the app refreshed": the cover is
 * black (not the theme's grey surface), fades in/out instead of hard-cutting, and starts below TikTok's top tab
 * bar so the For You header never disappears.
 */
class SkipCoverOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val log: RuntimeLog = RuntimeLog.NONE,
) : SkipCover {
    private var view: View? = null

    /** True while the cover is on screen. */
    val isShowing: Boolean get() = view != null

    override fun show(bounds: Bounds, style: CoverStyle, reason: SkipReason?) {
        if (bounds.isEmpty) return
        hide()
        // Keep TikTok's status bar + top tab bar visible: start the cover below them.
        val coverBounds = bounds.copy(top = maxOf(bounds.top, px(TOP_INSET_DP)).coerceAtMost(bounds.bottom))
        if (coverBounds.isEmpty) return
        val params = OverlayParams.passive(coverBounds)
        val cover = FrameLayout(context)
        when (style) {
            CoverStyle.BLANK -> cover.setBackgroundColor(Color.BLACK)
            CoverStyle.LABELLED -> {
                cover.setBackgroundColor(Color.BLACK)
                cover.addView(label(reason), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }
            CoverStyle.BLUR -> if (OverlayParams.blurSupported(windowManager)) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = px(BLUR_RADIUS_DP)
                cover.setBackgroundColor(Color.argb(BLUR_SCRIM_ALPHA, 0, 0, 0))
            } else {
                cover.setBackgroundColor(Color.argb(HEAVY_DIM_ALPHA, 0, 0, 0))
            }
        }
        try {
            cover.alpha = 0f
            windowManager.addView(cover, params)
            view = cover
            cover.animate().alpha(1f).setDuration(FADE_IN_MILLIS).start()
        } catch (e: RuntimeException) {
            log.log("cover add failed ${e.javaClass.simpleName}")
        }
    }

    override fun hide() {
        val v = view ?: return
        view = null
        // Fade out, then remove. The view is detached from [view] immediately, so a new show() never waits on it.
        v.animate().cancel()
        v.animate().alpha(0f).setDuration(FADE_OUT_MILLIS).withEndAction { remove(v) }.start()
        // Failsafe: if the animation never ends (view not attached), remove anyway.
        v.postDelayed({ remove(v) }, FADE_OUT_MILLIS + 250)
    }

    private fun remove(v: View) {
        if (!v.isAttachedToWindow) return
        try {
            windowManager.removeViewImmediate(v)
        } catch (e: RuntimeException) {
            log.log("cover remove failed ${e.javaClass.simpleName}")
        }
    }

    private fun label(reason: SkipReason?): TextView = TextView(context).apply {
        text = context.getString(
            when (reason) {
                SkipReason.ORGANIC_SHOP -> R.string.overlay_cover_skipped_shop
                SkipReason.PAID_AD -> R.string.overlay_cover_skipped_ad
                SkipReason.PROMOTED -> R.string.overlay_cover_skipped_promoted
                SkipReason.LIVE_SHOPPING -> R.string.overlay_cover_skipped_live
                null -> R.string.overlay_cover_skipped
            },
        )
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SP)
        gravity = Gravity.CENTER
    }

    private fun px(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
        const val BLUR_RADIUS_DP = 40f
        const val BLUR_SCRIM_ALPHA = 90
        const val HEAVY_DIM_ALPHA = 235
        const val LABEL_SP = 18f

        /** Status bar + TikTok's top tab bar on the Fold 7 cover screen (~220 px at 2.625x). */
        const val TOP_INSET_DP = 88f
        const val FADE_IN_MILLIS = 90L
        const val FADE_OUT_MILLIS = 160L
    }
}
