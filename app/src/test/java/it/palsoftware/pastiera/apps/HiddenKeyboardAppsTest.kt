package it.palsoftware.pastiera.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.getHiddenKeyboardApps
import it.palsoftware.pastiera.hiddenAppAllowsPanels
import it.palsoftware.pastiera.hiddenAppShowsLeds
import it.palsoftware.pastiera.isKeyboardHiddenForApp
import it.palsoftware.pastiera.parsePackageList
import it.palsoftware.pastiera.setHiddenAppAllowsPanels
import it.palsoftware.pastiera.setHiddenAppShowsLeds
import it.palsoftware.pastiera.setHiddenKeyboardApps

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HiddenKeyboardAppsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun parsesPackageNamesFromFreeTextAndDropsJunk() {
        assertEquals(
            listOf("com.termux.x11", "org.example.app"),
            SettingsManager.parsePackageList("com.termux.x11, org.example.app\nnotapackage 1bad.pkg com.termux.x11")
        )
    }

    @Test
    fun niagaraAndTermuxX11ByDefault() {
        // Niagara Launcher's search and Termux:X11 read keys themselves, so the keyboard stays hidden
        assertEquals(listOf("bitpit.launcher", "com.termux.x11"), SettingsManager.getHiddenKeyboardApps(context))
        assertTrue(SettingsManager.isKeyboardHiddenForApp(context, "bitpit.launcher"))
        assertTrue(SettingsManager.isKeyboardHiddenForApp(context, "com.termux.x11"))
        assertFalse(SettingsManager.isKeyboardHiddenForApp(context, "com.termux"))
    }

    @Test
    fun storedAppsAreHidden() {
        SettingsManager.setHiddenKeyboardApps(context, listOf("com.termux.x11"))

        assertTrue(SettingsManager.isKeyboardHiddenForApp(context, "com.termux.x11"))
        assertFalse(SettingsManager.isKeyboardHiddenForApp(context, "com.termux"))
        assertFalse(SettingsManager.isKeyboardHiddenForApp(context, null))
    }

    @Test
    fun perAppOptionsAreOffByDefaultAndIndependent() {
        SettingsManager.setHiddenKeyboardApps(context, listOf("com.termux.x11", "org.example.app"))
        assertFalse(SettingsManager.hiddenAppShowsLeds(context, "com.termux.x11"))
        assertFalse(SettingsManager.hiddenAppAllowsPanels(context, "com.termux.x11"))

        SettingsManager.setHiddenAppShowsLeds(context, "com.termux.x11", true)
        SettingsManager.setHiddenAppAllowsPanels(context, "org.example.app", true)

        assertTrue(SettingsManager.hiddenAppShowsLeds(context, "com.termux.x11"))
        assertFalse(SettingsManager.hiddenAppAllowsPanels(context, "com.termux.x11"))
        assertFalse(SettingsManager.hiddenAppShowsLeds(context, "org.example.app"))
        assertTrue(SettingsManager.hiddenAppAllowsPanels(context, "org.example.app"))

        SettingsManager.setHiddenAppShowsLeds(context, "com.termux.x11", false)
        assertFalse(SettingsManager.hiddenAppShowsLeds(context, "com.termux.x11"))
        assertFalse(SettingsManager.hiddenAppShowsLeds(context, null))
    }

    @Test
    fun earlierGlobalSwitchCarriesOverToAppsHiddenAtTheTime() {
        SettingsManager.setHiddenKeyboardApps(context, listOf("com.termux.x11", "org.example.app"))
        SettingsManager.getPreferences(context).edit()
            .putBoolean("hidden_keyboard_apps_show_leds", true)
            .commit()

        assertTrue(SettingsManager.hiddenAppShowsLeds(context, "com.termux.x11"))
        assertTrue(SettingsManager.hiddenAppShowsLeds(context, "org.example.app"))
        assertFalse(SettingsManager.hiddenAppAllowsPanels(context, "com.termux.x11"))

        // The first per-app change keeps the others as they were and retires the global switch
        SettingsManager.setHiddenAppShowsLeds(context, "org.example.app", false)
        assertTrue(SettingsManager.hiddenAppShowsLeds(context, "com.termux.x11"))
        assertFalse(SettingsManager.hiddenAppShowsLeds(context, "org.example.app"))
        assertFalse(SettingsManager.getPreferences(context).contains("hidden_keyboard_apps_show_leds"))
    }
}
