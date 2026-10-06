package it.palsoftware.pastiera.inputmethod.statusbar

import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.setStatusBarSlotsLeft

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TerminalAppButtonsTest {
    @After
    fun reset() = StatusBarButtonRegistry.setTerminalApp(false)

    @Test
    fun terminalAppsHaveNoMicrophoneButton() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setStatusBarSlotsLeft(context, listOf(SettingsManager.STATUS_BAR_BUTTON_MICROPHONE))
        val registry = StatusBarButtonRegistry()
        assertTrue(registry.getEnabledButtons(context).any { it.id == StatusBarButtonId.Microphone })
        StatusBarButtonRegistry.setTerminalApp(true)
        assertFalse(registry.getEnabledButtons(context).any { it.id == StatusBarButtonId.Microphone })
        StatusBarButtonRegistry.setTerminalApp(false)
        assertTrue(registry.getEnabledButtons(context).any { it.id == StatusBarButtonId.Microphone })
    }
}
