package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TerminalModeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetSettings() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun termuxIsATerminalAppByDefault() {
        assertTrue(SettingsManager.isTerminalModeApp(context, SettingsManager.TERMUX_PACKAGE))
        assertFalse(SettingsManager.isTerminalModeApp(context, "com.example.notes"))
        SettingsManager.setTerminalModeEnabled(context, false)
        assertFalse(SettingsManager.isTerminalModeApp(context, SettingsManager.TERMUX_PACKAGE))
    }

    @Test
    fun hiddenAppsKeepTheirOwnHandling() {
        SettingsManager.setHiddenKeyboardApps(context, listOf(SettingsManager.TERMUX_PACKAGE))
        assertFalse(SettingsManager.isTerminalModeApp(context, SettingsManager.TERMUX_PACKAGE))
    }

    @Test
    fun addedAppsAndRemovingTermux() {
        SettingsManager.setTerminalModeApps(context, listOf("org.connectbot"))
        assertTrue(SettingsManager.isTerminalModeApp(context, "org.connectbot"))
        assertFalse(SettingsManager.isTerminalModeApp(context, SettingsManager.TERMUX_PACKAGE))
    }

    @Test
    fun aTerminalViewBecomesAPlainTextFieldWithoutSmartFeatures() {
        val info = EditorInfo().apply { inputType = InputType.TYPE_NULL }
        assertTrue(TerminalMode.apply(info))
        assertEquals(TerminalMode.INPUT_TYPE, info.inputType)
        // Termux with "enforce-char-based-input" already asks for this: still terminal mode
        assertTrue(TerminalMode.apply(info))
        // A real text field in the same app (e.g. a dialog) is left alone
        val field = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertFalse(TerminalMode.apply(field))
        assertEquals(InputType.TYPE_CLASS_TEXT, field.inputType)
    }

    @Test
    fun terminalKeys() {
        listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_F5).forEach { assertTrue(TerminalMode.isTerminalKey(it)) }
        listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_SPACE).forEach { assertFalse(TerminalMode.isTerminalKey(it)) }
    }
}
