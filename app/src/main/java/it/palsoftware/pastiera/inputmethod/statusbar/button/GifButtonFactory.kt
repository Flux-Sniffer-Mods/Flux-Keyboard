package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Factory for the GIF button: opens the emoji picker straight into GIF search.
 */
class GifButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = ImageView(context).apply {
            setImageResource(R.drawable.ic_gif_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.status_bar_button_gif_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            callbacks.onGifSearchRequested?.invoke()
        }
        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) {
        // No state to update for the GIF button
    }
}
