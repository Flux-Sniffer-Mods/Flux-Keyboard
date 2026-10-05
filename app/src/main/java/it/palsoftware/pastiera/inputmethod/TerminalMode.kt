package it.palsoftware.pastiera.inputmethod

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

/**
 * Terminal mode (Termux and other terminals you add): a terminal tells input methods it has no
 * text field (TYPE_NULL), so Pastiera would leave Alt and SYM to the app. Treated instead as a
 * visible-password text field, the same thing Termux's own "enforce-char-based-input" option
 * asks for, Pastiera types its Alt and SYM symbols into it, with no suggestions, corrections or
 * automatic capitals; Ctrl goes to the terminal as a real Ctrl (see the service).
 */
internal object TerminalMode {
    const val INPUT_TYPE = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

    /** Whether [inputType] is a terminal's "no text field". */
    fun isTerminalView(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

    /** Rewrites a terminal's field in place, as Pastiera sees it; true when it did. */
    fun apply(info: EditorInfo?): Boolean {
        if (info == null || !isTerminalView(info.inputType)) return info?.inputType == INPUT_TYPE
        info.inputType = INPUT_TYPE
        return true
    }

    /** Keys a terminal reads itself: sent as pressed unless Alt or SYM is typing a symbol. */
    fun isTerminalKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_INSERT -> true
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> true
        else -> false
    }

    /**
     * What the emoji key does in a terminal (Terminal mode > Emoji key): the emoji picker, or a
     * key terminals use all the time, sent as pressed. Holding ↑ repeats it.
     */
    enum class EmojiKeyAction(val id: String, val keyCode: Int, val ctrl: Boolean = false) {
        EmojiPicker("emoji_picker", KeyEvent.KEYCODE_UNKNOWN),
        Escape("esc", KeyEvent.KEYCODE_ESCAPE),
        Tab("tab", KeyEvent.KEYCODE_TAB),
        PreviousCommand("up", KeyEvent.KEYCODE_DPAD_UP),
        Interrupt("ctrl_c", KeyEvent.KEYCODE_C, ctrl = true),
        EndOfInput("ctrl_d", KeyEvent.KEYCODE_D, ctrl = true),
        Suspend("ctrl_z", KeyEvent.KEYCODE_Z, ctrl = true),
        ClearScreen("ctrl_l", KeyEvent.KEYCODE_L, ctrl = true),
        SearchHistory("ctrl_r", KeyEvent.KEYCODE_R, ctrl = true),
        // Held, it is Alt itself: the keyboard's Alt layer and terminal Alt combos
        Alt("alt", KeyEvent.KEYCODE_ALT_LEFT);

        /** Only the arrow repeats while held; a held Ctrl+C sends one interrupt. */
        val repeats: Boolean get() = this == PreviousCommand

        val metaState: Int
            get() = if (ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0

        companion object {
            fun byId(id: String?): EmojiKeyAction = entries.firstOrNull { it.id == id } ?: EmojiPicker
        }
    }
}
