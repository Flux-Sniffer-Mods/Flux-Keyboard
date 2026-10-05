package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Picking suggestions from the keyboard (palsoftware/pastiera#217). */
class SuggestionKeysTest {
    private val qwe = SuggestionKeys.CTRL_SHIFT_QWE

    @Test
    fun ctrlShiftQweFollowsTheBar() {
        assertEquals(0, SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_Q, ctrl = true, shift = true, alt = false))
        assertEquals(1, SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_W, ctrl = true, shift = true, alt = false))
        assertEquals(2, SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_E, ctrl = true, shift = true, alt = false))
    }

    @Test
    fun otherChordsKeepTheirMeaning() {
        // Ctrl+W (close tab), Ctrl+Alt shortcuts and plain letters aren't taken
        assertNull(SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_W, ctrl = true, shift = false, alt = false))
        assertNull(SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_W, ctrl = true, shift = true, alt = true))
        assertNull(SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_W, ctrl = false, shift = true, alt = false))
        assertNull(SuggestionKeys.slotFor(qwe, KeyEvent.KEYCODE_R, ctrl = true, shift = true, alt = false))
        assertNull(SuggestionKeys.slotFor(SuggestionKeys.OFF, KeyEvent.KEYCODE_Q, ctrl = true, shift = true, alt = false))
    }

    @Test
    fun numberRowOption() {
        val digits = SuggestionKeys.CTRL_DIGITS
        assertEquals(1, SuggestionKeys.slotFor(digits, KeyEvent.KEYCODE_2, ctrl = true, shift = false, alt = false))
        assertNull(SuggestionKeys.slotFor(digits, KeyEvent.KEYCODE_2, ctrl = true, shift = true, alt = false))
    }
}
