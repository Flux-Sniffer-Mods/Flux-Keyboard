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
    fun extraKeysUndoRedoClipboardAndSettingsByDefault() {
        assertEquals(
            listOf(
                SettingsManager.STATUS_BAR_BUTTON_EXTRA_KEYS,
                SettingsManager.STATUS_BAR_BUTTON_UNDO,
                SettingsManager.STATUS_BAR_BUTTON_REDO,
                SettingsManager.STATUS_BAR_BUTTON_CLIPBOARD,
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
