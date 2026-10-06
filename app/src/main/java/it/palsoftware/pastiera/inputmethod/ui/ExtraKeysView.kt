package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKey
import it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeySets
import it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysRow
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * The extra keys row: takes the bar's place like the menu bar, a close button then the row's
 * keys. Each key shows the top-row letter that presses it while the row is open.
 */
class ExtraKeysView(private val context: Context) {

    private var root: FrameLayout? = null
    private var row: LinearLayout? = null
    private var shown: ExtraKeysRow? = null
    private val keyViews = mutableMapOf<ExtraKey, TextView>()

    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) return
            field = value
            root?.setBackgroundColor(value?.background ?: Color.BLACK)
            shown?.let { rebuild(it) }
        }

    fun attachTo(parent: FrameLayout) {
        val view = ensureView()
        if (view.parent !== parent) {
            (view.parent as? ViewGroup)?.removeView(view)
            parent.addView(view)
        }
        view.bringToFront()
    }

    fun show(state: ExtraKeysRow) {
        val view = ensureView()
        val previous = shown
        shown = state
        view.visibility = View.VISIBLE
        view.bringToFront()
        if (previous != null && previous.keys == state.keys && row?.childCount == state.keys.size + 1) {
            applyLatched(state.latched)
        } else {
            view.post { shown?.let { rebuild(it) } }
        }
    }

    fun hide() {
        shown = null
        root?.visibility = View.GONE
    }

    fun isVisible(): Boolean = root?.visibility == View.VISIBLE

    private fun ensureView(): FrameLayout {
        root?.let { return it }
        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        row = rowView
        return FrameLayout(context).apply {
            setBackgroundColor(themeOverride?.background ?: Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = false
            visibility = View.GONE
            addView(rowView)
        }.also { root = it }
    }

    private fun rebuild(state: ExtraKeysRow) {
        val rowView = row ?: return
        rowView.removeAllViews()
        keyViews.clear()
        val padding = dpToPx(if (rowView.height in 1 until dpToPx(36f)) 1f else 4f)
        rowView.setPadding(0, padding, 0, padding)
        val spacing = dpToPx(3f)
        val count = state.keys.size + 1
        rowView.addView(closeButton(state), keyParams(isLast = false, spacing))
        state.keys.forEachIndexed { index, key ->
            val view = keyView(key, index, state)
            keyViews[key] = view.getChildAt(0) as TextView
            rowView.addView(view, keyParams(isLast = index == count - 2, spacing))
        }
        applyLatched(state.latched)
    }

    private fun keyParams(isLast: Boolean, spacing: Int) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = if (isLast) 0 else spacing
        }

    private fun closeButton(state: ExtraKeysRow): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.ic_close_24)
        contentDescription = context.getString(R.string.extra_keys_close)
        scaleType = ImageView.ScaleType.CENTER
        setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
        background = buttonDrawable(latched = false)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            state.onClose()
        }
    }

    private fun keyView(key: ExtraKey, index: Int, state: ExtraKeysRow): FrameLayout {
        val textColor = themeOverride?.textAndIcons ?: Color.WHITE
        val label = TextView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (key.label.length > 3) 12f else 15f)
            maxLines = 1
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val hint = ExtraKeySets.physicalKeyFor(index)?.let { physical ->
            TextView(context).apply {
                text = KeyEvent.keyCodeToString(physical).removePrefix("KEYCODE_").lowercase()
                setTextColor(textColor)
                alpha = 0.5f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply { setMargins(0, dpToPx(1f), dpToPx(3f), 0) }
            }
        }
        return FrameLayout(context).apply {
            addView(label)
            hint?.let { addView(it) }
            contentDescription = key.label
            isClickable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                state.onKey(key)
            }
        }
    }

    private fun applyLatched(latched: Set<ExtraKey>) {
        keyViews.forEach { (key, label) ->
            (label.parent as? View)?.background = buttonDrawable(latched = key in latched)
        }
    }

    private fun buttonDrawable(latched: Boolean) = StatusBarButtonStyles.createButtonDrawable(
        heightPx = dpToPx(39f),
        normalColor = if (latched) {
            themeOverride?.accent ?: StatusBarButtonStyles.PRESSED_BLUE
        } else {
            themeOverride?.statusBarButton ?: StatusBarButtonStyles.NORMAL_COLOR
        },
        pressedColor = themeOverride?.accent ?: StatusBarButtonStyles.PRESSED_BLUE,
        cornerRadiusRatio = themeOverride?.chromeCornerRadiusRatio ?: StatusBarButtonStyles.BUTTON_CORNER_RADIUS_RATIO,
        borderColor = themeOverride?.divider,
        borderWidthPx = if (themeOverride != null) dpToPx(1f) else 0
    )

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}
