package it.palsoftware.pastiera.shortcuts

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Shortcuts as pressed on a keyboard without a number row, "/" or arrow keys (Titan 2 Elite). */
class ShortcutKeysTest {
    // Alt layer: W types 1, X types /, K types ,
    private val altLayer = mapOf(KeyEvent.KEYCODE_W to "1", KeyEvent.KEYCODE_X to "/", KeyEvent.KEYCODE_K to ",")
    private val letters = { code: Int -> code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || code == KeyEvent.KEYCODE_ENTER }
    private fun label(c: KeyCombo) = (if (c.ctrl) "Ctrl+" else "") + (if (c.alt) "Alt+" else "") + name(c.keyCode)
    private fun name(code: Int) = ('A' + (code - KeyEvent.KEYCODE_A)).toString()
    private fun press(c: KeyCombo) = ShortcutKeys.howToPress(c, altLayer, letters, ::label, ::name)

    @Test
    fun keysTheKeyboardHasAreShownAsTheyAre() {
        assertEquals("Ctrl+M", press(KeyCombo(KeyEvent.KEYCODE_M, ctrl = true)))
    }

    @Test
    fun missingKeysUseTheLetterThatTypesThemWithAlt() {
        assertEquals("Ctrl+Alt+W (1)", press(KeyCombo(KeyEvent.KEYCODE_1, ctrl = true, alt = true)))
        assertEquals("Ctrl+Alt+X (/)", press(KeyCombo(KeyEvent.KEYCODE_SLASH, ctrl = true)))
        assertEquals("Ctrl+Alt+K (,)", press(KeyCombo(KeyEvent.KEYCODE_COMMA, ctrl = true)))
    }

    @Test
    fun shortcutsTheKeyboardCantMakeAreNotOffered() {
        assertNull(press(KeyCombo(KeyEvent.KEYCODE_DPAD_DOWN, alt = true)))
        assertNull(press(KeyCombo(KeyEvent.KEYCODE_2, ctrl = true, alt = true)))
    }

    @Test
    fun ctrlAltLetterStandsForTheShortcut() {
        assertEquals(KeyEvent.KEYCODE_1 to true, ShortcutKeys.translate('1'))
        assertEquals(KeyEvent.KEYCODE_SLASH to false, ShortcutKeys.translate('/'))
        assertNull(ShortcutKeys.translate('a'))
        assertNull(ShortcutKeys.translate(null))
    }
}
