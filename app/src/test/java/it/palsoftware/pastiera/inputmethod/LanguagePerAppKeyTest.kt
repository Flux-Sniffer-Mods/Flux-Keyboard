package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A language remembered per app is its locale plus input style. */
class LanguagePerAppKeyTest {
    @Test
    fun keyCombinesLocaleAndInputStyle() {
        assertEquals("it_IT|", SubtypeCycler.subtypeKey("it_IT", null))
        assertEquals("de_DE|layout=qwertz", SubtypeCycler.subtypeKey("de_DE", "layout=qwertz"))
        assertNotEquals(SubtypeCycler.subtypeKey("en_US", "layout=qwerty"), SubtypeCycler.subtypeKey("en_US", "layout=dvorak"))
    }
}
