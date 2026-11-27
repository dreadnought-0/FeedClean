package io.github.dreadnought0.feedclean.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * A rounded ring drawn around TikTok's own Block button while the stop-at-dialog block style waits for the
 * user's tap. Lives in a non-touchable window, so the user's tap reaches TikTok's button.
 */
@SuppressLint("ViewConstructor")
class HighlightRingView(context: Context, private val strokePx: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        color = Color.rgb(255, 196, 0)
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = strokePx / 2
        rect.set(inset, inset, width - inset, height - inset)
        val radius = minOf(width, height) / 4f
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}
