package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.junit.After
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
class Titan2EliteDefaultsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearChoices() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        DeviceSpecific.clearTestOverrides()
    }

    @Test
    fun aTitan2EliteStartsFittedToItsScreen() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan2Elite_QWERTY",
            device = "titan2elite_qwerty",
            product = "titan2elite_qwerty"
        )
        assertTrue(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
        // Straight buttons by default: filled corners and the fixed 5 dp lift
        assertFalse(SettingsManager.getTitan2EliteContourLeds(context))
        assertTrue(SettingsManager.getTitan2EliteFillCorners(context))
        assertTrue(SettingsManager.getTitan2EliteStraightOuterButtons(context))
        assertEquals(5, SettingsManager.getTitan2EliteStatusBarLiftDp(context))

        // Contoured LEDs: rounded buttons, no lift, corners not filled
        SettingsManager.setTitan2EliteContourLeds(context, true)
        assertFalse(SettingsManager.getTitan2EliteFillCorners(context))
        assertFalse(SettingsManager.getTitan2EliteStraightOuterButtons(context))
        assertEquals(0, SettingsManager.getTitan2EliteStatusBarLiftDp(context))
    }

    @Test
    fun otherPhonesDontFillCorners() {
        // The corner style only matters once rounded corners are on, which they aren't here
        assertFalse(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
        assertFalse(SettingsManager.getTitan2EliteFillCorners(context))
    }
}
