package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonId
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The GIF button: a status bar slot choice and a built-in button (the menu shows it too). */
class GifStatusBarButtonTest {
    @Test
    fun gifIsABuiltInButtonYouCanPutInASlot() {
        assertTrue(SettingsManager.STATUS_BAR_BUTTON_GIF in SettingsManager.getAvailableStatusBarButtons())
        val registry = StatusBarButtonRegistry()
        assertNotNull(registry.getFactory(StatusBarButtonId.Gif))
        assertTrue(!registry.unregister(StatusBarButtonId.Gif))
    }
}
