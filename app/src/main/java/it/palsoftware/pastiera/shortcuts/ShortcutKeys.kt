package it.palsoftware.pastiera.shortcuts

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent

/**
 * How a shortcut is pressed on the keyboard in use. Keyboards without a number row or a "/" key
 * (the Titan 2 Elite) type those with Alt and a letter, so Ctrl+Alt+that letter counts as the
 * shortcut; shortcuts on keys the keyboard can't make at all (arrows without arrow keys) aren't
 * offered.
 */
object ShortcutKeys {
    /** Characters standard shortcuts use, and their key codes. */
    private val CHAR_KEYS: Map<Char, Int> = ('0'..'9').associateWith { KeyEvent.KEYCODE_0 + (it - '0') } + mapOf(
        '/' to KeyEvent.KEYCODE_SLASH,
        ',' to KeyEvent.KEYCODE_COMMA,
        '.' to KeyEvent.KEYCODE_PERIOD,
        '-' to KeyEvent.KEYCODE_MINUS,
        '=' to KeyEvent.KEYCODE_EQUALS,
        '[' to KeyEvent.KEYCODE_LEFT_BRACKET,
        ']' to KeyEvent.KEYCODE_RIGHT_BRACKET,
        ';' to KeyEvent.KEYCODE_SEMICOLON
    )
    private val KEY_CHARS = CHAR_KEYS.entries.associate { (c, k) -> k to c }

    fun charOf(keyCode: Int): Char? = KEY_CHARS[keyCode]
    fun keyCodeOf(c: Char): Int? = CHAR_KEYS[c]

    /** Hardware keyboards connected now (not the on-screen one). */
    private fun physicalKeyboards(): List<InputDevice> = runCatching {
        InputDevice.getDeviceIds().asSequence()
            .mapNotNull(InputDevice::getDevice)
            .filter { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
            .toList()
    }.getOrDefault(emptyList())

    /** The keyboard in use has this key (with no hardware keyboard to ask, assume it does). */
    fun keyboardHas(keyCode: Int): Boolean {
        val keyboards = physicalKeyboards()
        if (keyboards.isEmpty()) return true
        return keyboards.any { device -> runCatching { device.hasKeys(keyCode)[0] }.getOrDefault(false) }
    }

    /** The letter key that types [c] with Alt, from Pastiera's Alt layer. */
    fun altKeyFor(altLayer: Map<Int, String>, c: Char): Int? =
        altLayer.entries.firstOrNull { (_, value) -> value == c.toString() }?.key

    /**
     * What to press for [combo] here, e.g. "Ctrl+Alt+W (1)", or null when this keyboard can't
     * make it. [label] names a combo the usual way ("Ctrl+/").
     */
    fun howToPress(
        combo: KeyCombo,
        altLayer: Map<Int, String>,
        has: (Int) -> Boolean,
        label: (KeyCombo) -> String,
        keyName: (Int) -> String
    ): String? {
        if (has(combo.keyCode)) return label(combo)
        val c = charOf(combo.keyCode) ?: return null
        val key = altKeyFor(altLayer, c) ?: return null
        return buildString {
            if (combo.ctrl) append("Ctrl+")
            append("Alt+")
            if (combo.shift) append("Shift+")
            append(keyName(key))
            append(" ($c)")
        }
    }

    /**
     * A key pressed with Ctrl and Alt whose Alt character is [altChar]: the shortcut it stands for
     * (key code, and whether Alt is still part of it). Digits keep Alt (Ctrl+Alt+1); "/" and the
     * like drop it (Ctrl+Alt+letter for "/" is Ctrl+/). Null for anything else.
     */
    fun translate(altChar: Char?): Pair<Int, Boolean>? {
        val c = altChar ?: return null
        val keyCode = CHAR_KEYS[c] ?: return null
        return keyCode to (c in '0'..'9')
    }
}
