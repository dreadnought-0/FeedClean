package io.github.dreadnought0.feedclean.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Root view of an overlay window. Lets the window owner see touches before Compose does:
 *  - [interceptAll] = true (bubble): every touch goes to [onTouch]; the Compose content only draws.
 *  - [interceptAll] = false (card): only ACTION_OUTSIDE goes to [onTouch] (outside touch → dismiss); everything
 *    else reaches the Compose buttons.
 */
@SuppressLint("ViewConstructor")
internal class OverlayRootLayout(
    context: Context,
    private val interceptAll: Boolean,
    private val onTouch: (MotionEvent) -> Unit,
) : FrameLayout(context) {

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (interceptAll) {
            onTouch(ev)
            return true
        }
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onTouch(ev)
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
