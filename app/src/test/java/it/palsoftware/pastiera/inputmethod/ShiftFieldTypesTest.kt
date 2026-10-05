package it.palsoftware.pastiera.inputmethod

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShiftFieldTypesTest {
    private fun text(variation: Int) = InputType.TYPE_CLASS_TEXT or variation

    @Test
    fun kindsOfFieldAreToldApart() {
        assertEquals(ShiftFieldTypes.Type.TEXT, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE), 0))
        assertEquals(ShiftFieldTypes.Type.SEARCH, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_NORMAL), EditorInfo.IME_ACTION_SEARCH))
        assertEquals(ShiftFieldTypes.Type.SEARCH, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_FILTER), 0))
        assertEquals(ShiftFieldTypes.Type.LINKS, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_URI), EditorInfo.IME_ACTION_GO))
        assertEquals(ShiftFieldTypes.Type.EMAIL, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), 0))
        assertEquals(ShiftFieldTypes.Type.NAMES, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_PERSON_NAME), 0))
        assertEquals(ShiftFieldTypes.Type.ADDRESSES, ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS), 0))
    }

    @Test
    fun passwordsAndNumbersAreNeverShifted() {
        assertNull(ShiftFieldTypes.of(text(InputType.TYPE_TEXT_VARIATION_PASSWORD), 0))
        assertNull(ShiftFieldTypes.of(InputType.TYPE_CLASS_NUMBER, 0))
    }
}
