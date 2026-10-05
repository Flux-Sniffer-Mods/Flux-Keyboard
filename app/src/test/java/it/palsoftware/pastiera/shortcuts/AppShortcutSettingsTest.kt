package it.palsoftware.pastiera.shortcuts

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.backup.BackupPreferenceContract
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
class AppShortcutSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetSettings() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun onByDefaultWithNoChanges() {
        val config = AppShortcutSettings.config(context)
        assertTrue(config.enabled)
        assertTrue(config.apps.isEmpty())
    }

    @Test
    fun appSettingsRoundTrip() {
        val settings = AppShortcutAppSettings(
            enabled = false,
            appName = "Example",
            overrides = mapOf(
                StandardShortcut.Search to KeyCombo.key(KeyEvent.KEYCODE_SLASH),
                StandardShortcut.Delete to null
            )
        )
        AppShortcutSettings.setApp(context, "com.example", settings)
        AppShortcutSettings.setEnabled(context, false)

        val config = AppShortcutSettings.config(context)
        assertFalse(config.enabled)
        assertEquals(settings, config.apps["com.example"])

        AppShortcutSettings.setApp(context, "com.example", null)
        assertTrue(AppShortcutSettings.config(context).apps.isEmpty())
    }

    @Test
    fun unreadableSettingsAreIgnored() {
        assertTrue(AppShortcutSettings.parseApps("not json").isEmpty())
        val parsed = AppShortcutSettings.parseApps("""{"com.a":{"map":{"Nope":"ctrl:29","Search":"bad"}}}""")
        assertEquals(AppShortcutAppSettings(), parsed["com.a"])
    }

    @Test
    fun settingsAreInBackups() {
        listOf(AppShortcutSettings.KEY_ENABLED, AppShortcutSettings.KEY_SUGGESTIONS, AppShortcutSettings.KEY_APPS).forEach {
            assertTrue(it, BackupPreferenceContract.isExportable("pastiera_prefs", it))
        }
    }
}
