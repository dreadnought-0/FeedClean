package io.github.dreadnought0.feedclean.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import io.github.dreadnought0.feedclean.detect.Bounds

/**
 * Window parameters for FeedClean's overlays. All are `TYPE_ACCESSIBILITY_OVERLAY` windows added from the
 * accessibility service's own WindowManager (no SYSTEM_ALERT_WINDOW), laid out in absolute screen coordinates
 * (so they line up with `getBoundsInScreen`), never focusable (TikTok keeps input focus).
 */
internal object OverlayParams {

    private const val BASE_FLAGS =
        LayoutParams.FLAG_NOT_FOCUSABLE or
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** Bubble: small, touchable (tap / long-press / drag), never focusable. */
    fun bubble(x: Int, y: Int, sizePx: Int): LayoutParams = base(BASE_FLAGS).apply {
        width = sizePx
        height = sizePx
        this.x = x
        this.y = y
    }

    /** Card: touchable, not modal (touches outside go to TikTok), notified of outside touches (dismiss). */
    fun card(x: Int, y: Int, widthPx: Int): LayoutParams =
        base(BASE_FLAGS or LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH).apply {
            width = widthPx
            height = LayoutParams.WRAP_CONTENT
            this.x = x
            this.y = y
        }

    /** Non-touchable window over [bounds] (skip-cover, highlight ring, hint pill): never blocks the user. */
    fun passive(bounds: Bounds): LayoutParams = base(BASE_FLAGS or LayoutParams.FLAG_NOT_TOUCHABLE).apply {
        width = bounds.width
        height = bounds.height
        x = bounds.left
        y = bounds.top
    }

    /** Non-touchable, wrap-content window at ([x], [y]). */
    fun passiveWrap(x: Int, y: Int): LayoutParams = base(BASE_FLAGS or LayoutParams.FLAG_NOT_TOUCHABLE).apply {
        width = LayoutParams.WRAP_CONTENT
        height = LayoutParams.WRAP_CONTENT
        this.x = x
        this.y = y
    }

    /** Adds or removes [LayoutParams.FLAG_NOT_TOUCHABLE] (gesture pass-through). */
    fun setTouchable(params: LayoutParams, touchable: Boolean) {
        params.flags = if (touchable) {
            params.flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    private fun base(flags: Int): LayoutParams = LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        title = "FeedClean"
    }

    /** True when [wm] supports cross-window blur right now (it can be off in battery saver). */
    fun blurSupported(wm: WindowManager): Boolean = runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
}
