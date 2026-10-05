package it.palsoftware.pastiera

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The menu bar is customisable: which buttons, in what order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MenuBarButtonsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun reset() = SettingsManager.resetMenuBarButtons(context)

    @Test
    fun theEverydayButtonsByDefault() {
        // Symbols, emoji, GIF, Solderina and keyboard mode are opt-in; the language button stays
        // off while only one input language is on (the test phone can't tell, so it shows)
        assertEquals(
            listOf(
                SettingsManager.STATUS_BAR_BUTTON_MICROPHONE,
                SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD,
                SettingsManager.STATUS_BAR_BUTTON_UNDO,
                SettingsManager.STATUS_BAR_BUTTON_REDO,
                SettingsManager.STATUS_BAR_BUTTON_LANGUAGE,
                SettingsManager.STATUS_BAR_BUTTON_SETTINGS
            ),
            SettingsManager.getMenuBarButtons(context)
        )
    }

    @Test
    fun yourChoiceAndOrderAreKept() {
        val mine = listOf(
            SettingsManager.STATUS_BAR_BUTTON_GIF,
            SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD,
            SettingsManager.STATUS_BAR_BUTTON_SETTINGS
        )
        SettingsManager.setMenuBarButtons(context, mine + "not_a_button" + SettingsManager.STATUS_BAR_BUTTON_GIF)
        assertEquals(mine, SettingsManager.getMenuBarButtons(context))
    }

    @Test
    fun anEmptyMenuStaysEmpty() {
        SettingsManager.setMenuBarButtons(context, emptyList())
        assertEquals(emptyList<String>(), SettingsManager.getMenuBarButtons(context))
    }
}
