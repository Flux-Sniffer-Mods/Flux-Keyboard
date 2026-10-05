package it.palsoftware.pastiera.inputmethod.statusbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import it.palsoftware.pastiera.inputmethod.StatusBarController

/**
 * Any corner button's own background, cut to the display contour a gap inside the contoured
 * LEDs, with a smooth edge: the symbol pages' corner keys, the close button and the emoji
 * picker's search toggle take the same shape as the bar's corner buttons.
 */
internal class ContourClipDrawable(
    private val view: View,
    val inner: Drawable
) : Drawable(), Drawable.Callback {
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val location = IntArray(2)
    private val chromeLocation = IntArray(2)

    // The view clears the old background's callback when this replaces it, after this was built:
    // claim it again whenever drawing, so the button's own drawable can still redraw itself
    private fun claimInner() {
        if (inner.callback !== this) inner.callback = this
    }

    private fun chrome(): StatusBarController.ImeChromeLayout? {
        var ancestor = view.parent
        while (ancestor != null && ancestor !is StatusBarController.ImeChromeLayout) ancestor = ancestor.parent
        return ancestor as? StatusBarController.ImeChromeLayout
    }

    /** The contour in this drawable's coordinates, or null where there's none to follow. */
    private fun contour(): Path? {
        val chrome = chrome() ?: return null
        val radii = chrome.bottomCornerRadiiPx ?: return null
        if (!chrome.contourIntegratedIndicators || chrome.width <= 0) return null
        view.getLocationInWindow(location)
        chrome.getLocationInWindow(chromeLocation)
        val calibration = it.palsoftware.pastiera.T2eCornerCalibration.read(view.context)
        val inset = it.palsoftware.pastiera.inputmethod.ui.LedStatusView.contourButtonInsetPx(view.context)
        return it.palsoftware.pastiera.T2eCornerGeometry.path(
            chrome.width.toFloat(), chrome.height.toFloat(),
            radii.first.toFloat(), radii.second.toFloat(), calibration, inset
        ).apply {
            offset((chromeLocation[0] - location[0]).toFloat(), (chromeLocation[1] - location[1]).toFloat())
        }
    }

    override fun draw(canvas: Canvas) {
        claimInner()
        val path = contour()
        if (path == null) {
            inner.draw(canvas)
            return
        }
        val save = canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), null)
        inner.draw(canvas)
        canvas.drawPath(path, maskPaint)
        canvas.restoreToCount(save)
    }

    override fun onBoundsChange(bounds: Rect) { inner.bounds = bounds }
    override fun isStateful() = inner.isStateful
    override fun onStateChange(state: IntArray): Boolean = inner.setState(state).also { invalidateSelf() }
    override fun getPadding(padding: Rect) = inner.getPadding(padding)
    override fun getOutline(outline: Outline) = inner.getOutline(outline)
    override fun getIntrinsicWidth() = inner.intrinsicWidth
    override fun getIntrinsicHeight() = inner.intrinsicHeight
    override fun setAlpha(alpha: Int) { inner.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { inner.colorFilter = colorFilter }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
