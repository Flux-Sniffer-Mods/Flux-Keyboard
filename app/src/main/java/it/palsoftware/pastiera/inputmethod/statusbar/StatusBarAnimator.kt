package it.palsoftware.pastiera.inputmethod.statusbar

import android.animation.ValueAnimator
import android.view.View

/**
 * Handles animations for status bar elements.
 * 
 * Provides standardized fade-in/fade-out animations for variations row
 * and other status bar components.
 */
class StatusBarAnimator {
    
    companion object {
        private const val FADE_IN_DURATION_MS = 75L
        private const val FADE_OUT_DURATION_MS = 50L
    }
    
    private var currentAnimator: ValueAnimator? = null

    /**
     * Immediately hides a view without animation.
     * 
     * @param view The view to hide
     */
    fun hideImmediate(view: View) {
        currentAnimator?.cancel()
        currentAnimator = null
        view.visibility = View.GONE
        view.alpha = 1f
    }
    
    /**
     * Cancels any running animation.
     */
    fun cancel() {
        currentAnimator?.cancel()
        currentAnimator = null
    }
    
}
