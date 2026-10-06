package it.palsoftware.pastiera.device

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.getClicksNumberRowInputMode
import it.palsoftware.pastiera.getClicksOverlappingKeysMode
import it.palsoftware.pastiera.getOverlappingKeysEnabled
import it.palsoftware.pastiera.getPhysicalKeyboardCurrencySymbol
import it.palsoftware.pastiera.getPhysicalKeyboardProfileOverride
import it.palsoftware.pastiera.isClicksNumberRowRepeatEnabled
import it.palsoftware.pastiera.isTitan2LayoutEnabled
import it.palsoftware.pastiera.setClicksNumberRowInputMode
import it.palsoftware.pastiera.setClicksNumberRowRepeatEnabled
import it.palsoftware.pastiera.setClicksOverlappingKeysMode
import it.palsoftware.pastiera.setOverlappingKeysEnabled
import it.palsoftware.pastiera.setPhysicalKeyboardCurrencySymbol
import it.palsoftware.pastiera.setPhysicalKeyboardProfileOverride
import it.palsoftware.pastiera.setTitan2LayoutEnabled

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HardwareKeyboardSettingsTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun hardwareSettingsPersistIndependentlyFromLanguageLayouts() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "titan2")
        SettingsManager.setPhysicalKeyboardCurrencySymbol(context, "£")
        SettingsManager.setTitan2LayoutEnabled(context, true)

        assertEquals("titan2", SettingsManager.getPhysicalKeyboardProfileOverride(context))
        assertEquals("£", SettingsManager.getPhysicalKeyboardCurrencySymbol(context))
        assertEquals(true, SettingsManager.isTitan2LayoutEnabled(context))
    }

    @Test
    fun clicksPowerKeyboardCanBeSelected() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_power")

        assertEquals("clicks_power", SettingsManager.getPhysicalKeyboardProfileOverride(context))
    }

    @Test
    fun clicksBuiltInProfilesCanBeSelected() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_razr")
        assertEquals("clicks_razr", SettingsManager.getPhysicalKeyboardProfileOverride(context))

        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_pixel")
        assertEquals("clicks_pixel", SettingsManager.getPhysicalKeyboardProfileOverride(context))
    }

    @Test
    fun overlappingKeySettingsAreIndependentForGlobalAndClicksScopes() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(false, SettingsManager.getOverlappingKeysEnabled(context))
        assertEquals(
            SettingsManager.ClicksOverlappingKeysMode.OFF,
            SettingsManager.getClicksOverlappingKeysMode(context)
        )

        SettingsManager.setClicksOverlappingKeysMode(
            context,
            SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY
        )

        assertEquals(false, SettingsManager.getOverlappingKeysEnabled(context))
        assertEquals(
            SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY,
            SettingsManager.getClicksOverlappingKeysMode(context)
        )

        SettingsManager.setOverlappingKeysEnabled(context, true)

        assertEquals(true, SettingsManager.getOverlappingKeysEnabled(context))
        assertEquals(
            SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY,
            SettingsManager.getClicksOverlappingKeysMode(context)
        )
    }

    @Test
    fun legacyClicksOverlapBooleanMigratesToStrictMode() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit()
            .putBoolean("clicks_overlapping_keys_enabled", true)
            .commit()

        assertEquals(
            SettingsManager.ClicksOverlappingKeysMode.ALL_NON_MODIFIERS,
            SettingsManager.getClicksOverlappingKeysMode(context)
        )
    }

    @Test
    fun clicksNumberRowModePersistsAndUnknownValueFallsBackToNormal() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setClicksNumberRowInputMode(
            context,
            SettingsManager.ClicksNumberRowInputMode.LONG_PRESS
        )

        assertEquals(
            SettingsManager.ClicksNumberRowInputMode.LONG_PRESS,
            SettingsManager.getClicksNumberRowInputMode(context)
        )

        SettingsManager.getPreferences(context).edit()
            .putString("clicks_number_row_input_mode", "future_mode")
            .commit()

        assertEquals(
            SettingsManager.ClicksNumberRowInputMode.NORMAL,
            SettingsManager.getClicksNumberRowInputMode(context)
        )

        SettingsManager.getPreferences(context).edit()
            .putString("clicks_number_row_input_mode", "ignore_while_other_key_held")
            .commit()

        assertEquals(
            SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ANY_KEY_HELD,
            SettingsManager.getClicksNumberRowInputMode(context)
        )
    }

    @Test
    fun clicksNumberRowRepeatDefaultsToEnabledAndPersists() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(true, SettingsManager.isClicksNumberRowRepeatEnabled(context))

        SettingsManager.setClicksNumberRowRepeatEnabled(context, false)

        assertEquals(false, SettingsManager.isClicksNumberRowRepeatEnabled(context))
    }
}
