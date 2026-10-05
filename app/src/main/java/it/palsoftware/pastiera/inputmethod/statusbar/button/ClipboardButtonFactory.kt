package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for creating the clipboard button with badge and flash animation.
 */
class ClipboardButtonFactory : StatusBarButtonFactory {
    
    companion object {
        private const val FLASH_DURATION_MS = 350L
        
        // Tag keys for storing auxiliary views
        private const val TAG_BADGE = "clipboard_badge"
        private const val TAG_FLASH_OVERLAY = "clipboard_flash"
        private const val TAG_ANIMATOR = "clipboard_animator"
    }
    
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        val badge = createBadge(context)
        val flashOverlay = createFlashOverlay(context, size)
        
        // Set up click listener using the clipboard-specific callback
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            callbacks.onClipboardRequested?.invoke()
        }
        
        // Store references in tags for later updates
        button.setTag(R.id.tag_badge_view, badge)
        button.setTag(R.id.tag_flash_overlay, flashOverlay)
        setStateDescriptionIfChanged(
            button,
            context.getString(R.string.status_bar_button_clipboard_state_empty)
        )
        
        return ButtonCreationResult(
            view = button,
            badgeView = badge,
            flashOverlayView = flashOverlay
        )
    }
    
    override fun update(view: View, state: ButtonState) {
        if (state !is ButtonState.ClipboardState) return
        
        val badge = view.getTag(R.id.tag_badge_view) as? TextView ?: return
        val flashOverlay = view.getTag(R.id.tag_flash_overlay) as? View
        val previousCount = view.getTag(R.id.tag_previous_count) as? Int
        
        // The count floats on its own layer in the button's corner: the button itself never
        // moves or resizes, so the bar's spacing stays even
        updateBadge(badge, state.itemCount)
        view.translationX = 0f
        setStateDescriptionIfChanged(
            view,
            if (state.itemCount <= 0) {
                view.context.getString(R.string.status_bar_button_clipboard_state_empty)
            } else {
                view.context.getString(R.string.status_bar_button_clipboard_state_items, state.itemCount)
            }
        )
        
        // Flash when count increases
        if (previousCount != null && state.itemCount > 0 && state.itemCount != previousCount && flashOverlay != null) {
            flashButton(view, flashOverlay)
        }
        
        view.setTag(R.id.tag_previous_count, state.itemCount)
    }
    
    override fun cleanup(view: View) {
        // Cancel any running animation
        val animator = view.getTag(R.id.tag_animator) as? ValueAnimator
        animator?.cancel()
        view.setTag(R.id.tag_animator, null)
    }
    
    private fun createButton(context: Context, size: Int): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_content_paste_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_clipboard_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            // layoutParams will be set by VariationBarView for consistency
        }
    }
    
    // A small pill in the button's top corner, clear of the icon (the theme recolours it)
    private fun createBadge(context: Context): TextView {
        val padH = dpToPx(context, 3.5f)
        val size = dpToPx(context, 13f)
        return TextView(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = size / 2f
                setColor(Color.parseColor("#1E88E5"))
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            minWidth = size
            minHeight = size
            visibility = View.GONE
        }
    }
    
    private fun createFlashOverlay(context: Context, size: Int): View {
        return View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.RED)
            alpha = 0f
            isClickable = false
            isFocusable = false
        }
    }
    
    private fun updateBadge(badge: TextView, count: Int) {
        if (count <= 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = count.toString()
    }
    
    private fun flashButton(button: View, overlay: View) {
        // Cancel any existing animation
        val existingAnimator = button.getTag(R.id.tag_animator) as? ValueAnimator
        existingAnimator?.cancel()
        
        overlay.visibility = View.VISIBLE
        val animator = ValueAnimator.ofFloat(0f, 0.4f, 0f).apply {
            duration = FLASH_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                overlay.alpha = valueAnimator.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.alpha = 0f
                    overlay.visibility = View.GONE
                }
            })
        }
        button.setTag(R.id.tag_animator, animator)
        animator.start()
    }
    
    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun setStateDescriptionIfChanged(view: View, stateDescription: String) {
        if (ViewCompat.getStateDescription(view)?.toString() == stateDescription) {
            return
        }
        ViewCompat.setStateDescription(view, stateDescription)
    }
}
