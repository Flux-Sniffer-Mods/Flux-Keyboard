package it.palsoftware.pastiera.linux

import android.view.KeyEvent

/** Android's keys as X keysyms, which VNC sends: the special keys by name, the rest by character. */
internal object Keysyms {
    private val special = mapOf(
        KeyEvent.KEYCODE_ENTER to 0xFF0D, KeyEvent.KEYCODE_NUMPAD_ENTER to 0xFF8D,
        KeyEvent.KEYCODE_DEL to 0xFF08, KeyEvent.KEYCODE_FORWARD_DEL to 0xFFFF,
        KeyEvent.KEYCODE_TAB to 0xFF09, KeyEvent.KEYCODE_ESCAPE to 0xFF1B,
        KeyEvent.KEYCODE_DPAD_LEFT to 0xFF51, KeyEvent.KEYCODE_DPAD_UP to 0xFF52,
        KeyEvent.KEYCODE_DPAD_RIGHT to 0xFF53, KeyEvent.KEYCODE_DPAD_DOWN to 0xFF54,
        KeyEvent.KEYCODE_MOVE_HOME to 0xFF50, KeyEvent.KEYCODE_MOVE_END to 0xFF57,
        KeyEvent.KEYCODE_PAGE_UP to 0xFF55, KeyEvent.KEYCODE_PAGE_DOWN to 0xFF56,
        KeyEvent.KEYCODE_INSERT to 0xFF63,
        KeyEvent.KEYCODE_SHIFT_LEFT to 0xFFE1, KeyEvent.KEYCODE_SHIFT_RIGHT to 0xFFE2,
        KeyEvent.KEYCODE_CTRL_LEFT to 0xFFE3, KeyEvent.KEYCODE_CTRL_RIGHT to 0xFFE4,
        KeyEvent.KEYCODE_ALT_LEFT to 0xFFE9, KeyEvent.KEYCODE_ALT_RIGHT to 0xFFEA,
        KeyEvent.KEYCODE_META_LEFT to 0xFFEB, KeyEvent.KEYCODE_META_RIGHT to 0xFFEC,
        KeyEvent.KEYCODE_CAPS_LOCK to 0xFFE5, KeyEvent.KEYCODE_SPACE to 0x20,
        KeyEvent.KEYCODE_F1 to 0xFFBE, KeyEvent.KEYCODE_F2 to 0xFFBF, KeyEvent.KEYCODE_F3 to 0xFFC0,
        KeyEvent.KEYCODE_F4 to 0xFFC1, KeyEvent.KEYCODE_F5 to 0xFFC2, KeyEvent.KEYCODE_F6 to 0xFFC3,
        KeyEvent.KEYCODE_F7 to 0xFFC4, KeyEvent.KEYCODE_F8 to 0xFFC5, KeyEvent.KEYCODE_F9 to 0xFFC6,
        KeyEvent.KEYCODE_F10 to 0xFFC7, KeyEvent.KEYCODE_F11 to 0xFFC8, KeyEvent.KEYCODE_F12 to 0xFFC9
    )

    /** Keys that stand alone: held with others, they still say themselves rather than a character. */
    private val modifiers = setOf(0xFFE1, 0xFFE2, 0xFFE3, 0xFFE4, 0xFFE9, 0xFFEA, 0xFFEB, 0xFFEC, 0xFFE5)

    fun isModifier(keysym: Int) = keysym in modifiers

    /** [keyCode]'s keysym, [char] the character it types (0 when none); 0 when it has neither. */
    fun of(keyCode: Int, char: Int): Int {
        special[keyCode]?.let { return it }
        return of(char)
    }

    /** A character's keysym: Latin-1 as itself, the rest of Unicode at 0x1000000 on. */
    fun of(char: Int): Int = when {
        char <= 0 -> 0
        char == '\n'.code -> 0xFF0D
        char in 0x20..0x7E || char in 0xA0..0xFF -> char
        else -> 0x1000000 or char
    }

    /** A character as Ctrl or Alt would have it: the key's own letter, lower case. */
    fun base(event: KeyEvent): Int = event.getUnicodeChar(0).let { if (it > 0) of(it) else of(event.keyCode, 0) }
}
