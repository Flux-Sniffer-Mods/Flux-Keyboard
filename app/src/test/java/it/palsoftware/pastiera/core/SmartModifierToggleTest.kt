package it.palsoftware.pastiera.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartModifierToggleTest {
    @Test
    fun bracketsAndCurlyQuotesAlwaysOpen() {
        listOf("(", "[", "{", "“", "«", "¿").forEach {
            assertTrue(it, SmartModifierToggle.opensQuoteOrBracket(it, "word"))
        }
    }

    @Test
    fun straightQuotesOpenOnlyAfterASpaceOrTheStart() {
        assertTrue(SmartModifierToggle.opensQuoteOrBracket("\"", "He said "))
        assertTrue(SmartModifierToggle.opensQuoteOrBracket("\"", ""))
        assertTrue(SmartModifierToggle.opensQuoteOrBracket("\"", null))
        assertTrue(SmartModifierToggle.opensQuoteOrBracket("'", "("))
        // After a word it closes the quote
        assertFalse(SmartModifierToggle.opensQuoteOrBracket("\"", "hello"))
    }

    @Test
    fun otherCharactersDontCount() {
        listOf("1", ")", "@", "!", "ab").forEach {
            assertFalse(it, SmartModifierToggle.opensQuoteOrBracket(it, " "))
        }
    }

    @Test
    fun cursorAndSelectionMovesAreNavigation() {
        assertTrue(SmartModifierToggle.isNavigation("keycode", "DPAD_LEFT"))
        assertTrue(SmartModifierToggle.isNavigation("keycode", "PAGE_DOWN"))
        assertTrue(SmartModifierToggle.isNavigation("action", "expand_selection_word_left"))
        assertTrue(SmartModifierToggle.isNavigation("action", "move_word_right"))
        assertFalse(SmartModifierToggle.isNavigation("action", "copy"))
        assertFalse(SmartModifierToggle.isNavigation("keycode", "ESCAPE"))
        assertFalse(SmartModifierToggle.isNavigation(null, null))
    }
}
