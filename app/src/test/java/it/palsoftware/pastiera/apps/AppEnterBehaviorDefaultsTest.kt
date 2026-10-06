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
import it.palsoftware.pastiera.getAppEnterBehaviorEnabled
import it.palsoftware.pastiera.getAppEnterBehaviorPreset
import it.palsoftware.pastiera.setAppEnterBehaviorEnabled
import it.palsoftware.pastiera.setAppEnterBehaviorPreset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppEnterBehaviorDefaultsTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultsToEnabledEnterSendAndShiftEnterNewline() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.getAppEnterBehaviorEnabled(context))
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE,
            SettingsManager.getAppEnterBehaviorPreset(context)
        )
    }

    @Test
    fun explicitUserChoicesStillOverrideDefaults() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setAppEnterBehaviorEnabled(context, false)
        SettingsManager.setAppEnterBehaviorPreset(
            context,
            SettingsManager.ENTER_BEHAVIOR_PRESET_APP_DEFAULT
        )

        assertFalse(SettingsManager.getAppEnterBehaviorEnabled(context))
        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_PRESET_APP_DEFAULT,
            SettingsManager.getAppEnterBehaviorPreset(context)
        )
    }
}
