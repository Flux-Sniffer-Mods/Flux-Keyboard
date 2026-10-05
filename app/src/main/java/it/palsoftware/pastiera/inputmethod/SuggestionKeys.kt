package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/**
 * Picking a suggestion from the physical keyboard (palsoftware/pastiera#217). The keys follow the
 * bar: left, middle (the best suggestion) and right.
 */
object SuggestionKeys {
    const val OFF = "off"
    /** Ctrl+Shift+Q / W / E: works on every keyboard, including those without a number row. */
    const val CTRL_SHIFT_QWE = "ctrl_shift_qwe"
    /** Ctrl+1 / 2 / 3, for keyboards with a number row. */
    const val CTRL_DIGITS = "ctrl_digits"

    val OPTIONS = listOf(CTRL_SHIFT_QWE, CTRL_DIGITS, OFF)

    /**
     * The bar third (0 left, 1 middle, 2 right) [keyCode] picks with these modifiers, or null
     * when it isn't a suggestion shortcut.
     */
    fun slotFor(option: String, keyCode: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): Int? {
        if (!ctrl || alt) return null
        return when (option) {
            CTRL_SHIFT_QWE -> if (!shift) null else when (keyCode) {
                KeyEvent.KEYCODE_Q -> 0
                KeyEvent.KEYCODE_W -> 1
                KeyEvent.KEYCODE_E -> 2
                else -> null
            }
            CTRL_DIGITS -> if (shift) null else when (keyCode) {
                KeyEvent.KEYCODE_1 -> 0
                KeyEvent.KEYCODE_2 -> 1
                KeyEvent.KEYCODE_3 -> 2
                else -> null
            }
            else -> null
        }
    }
}
