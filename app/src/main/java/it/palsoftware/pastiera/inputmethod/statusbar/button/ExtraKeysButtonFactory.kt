package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks

/** Opens the extra keys row (Esc, Tab, arrows and the like) in the bar's place. */
class ExtraKeysButtonFactory : StatusBarButtonFactory {

    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = ImageView(context).apply {
            setImageResource(R.drawable.modifier_keys_24)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.extra_keys_button_description)
            background = StatusBarButtonStyles.createButtonDrawable(size)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
        }
        button.setOnClickListener {
            callbacks.onHapticFeedback?.invoke()
            callbacks.onExtraKeysRequested?.invoke()
        }
        return ButtonCreationResult(view = button)
    }

    override fun update(view: View, state: ButtonState) {}
}
