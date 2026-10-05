package it.palsoftware.pastiera

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiPickerKeySettingsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun modifierAndFunctionKeysAreAllowed() {
        listOf(
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_F5
        ).forEach { assertTrue(KeyEvent.keyCodeToString(it), SettingsManager.isAllowedEmojiPickerKey(it)) }
    }

    @Test
    fun typingAndReservedKeysAreRejected() {
        listOf(
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_SYM,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_UNKNOWN
        ).forEach { assertFalse(KeyEvent.keyCodeToString(it), SettingsManager.isAllowedEmojiPickerKey(it)) }
        assertFalse(SettingsManager.isAllowedEmojiPickerKey(KeyEvent.KEYCODE_COMMA, isPrintingKey = true))
    }

    @Test
    fun storesAnyAllowedKeyAndIgnoresRejectedOnes() {
        assertTrue(SettingsManager.setEmojiPickerKey(context, KeyEvent.KEYCODE_FUNCTION))
        assertEquals(KeyEvent.KEYCODE_FUNCTION, SettingsManager.getEmojiPickerKey(context))

        assertFalse(SettingsManager.setEmojiPickerKey(context, KeyEvent.KEYCODE_SPACE))
        assertEquals(KeyEvent.KEYCODE_FUNCTION, SettingsManager.getEmojiPickerKey(context))

        assertTrue(SettingsManager.setEmojiPickerKey(context, KeyEvent.KEYCODE_UNKNOWN))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, SettingsManager.getEmojiPickerKey(context))
    }

    @Test
    fun labelsNameKnownKeys() {
        assertEquals("Right Shift", emojiPickerKeyLabel(context, KeyEvent.KEYCODE_SHIFT_RIGHT))
        assertEquals("Left Alt", emojiPickerKeyLabel(context, KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals("Fn", emojiPickerKeyLabel(context, KeyEvent.KEYCODE_FUNCTION))
        assertEquals("Off", emojiPickerKeyLabel(context, KeyEvent.KEYCODE_UNKNOWN))
    }
}
