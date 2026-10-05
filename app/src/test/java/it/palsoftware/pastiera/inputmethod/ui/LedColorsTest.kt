package it.palsoftware.pastiera.inputmethod.ui

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedColorsTest {
    private fun hsv(color: Int) = FloatArray(3).also { Color.colorToHSV(color, it) }

    @Test
    fun offIsDimActiveIsTheColourLockedIsBrighterAndMoreSaturated() {
        val base = Color.rgb(90, 140, 200)
        val off = LedColors.shade(base, LedColors.Level.OFF)
        val active = LedColors.shade(base, LedColors.Level.ACTIVE)
        val locked = LedColors.shade(base, LedColors.Level.LOCKED)
        assertTrue(hsv(off)[2] < hsv(active)[2])
        assertTrue(Color.alpha(off) < 255)
        assertTrue(hsv(locked)[1] > hsv(active)[1])
        assertTrue(hsv(locked)[2] > hsv(active)[2])
        // Same hue throughout, so each LED stays recognisable
        assertEquals(hsv(active)[0], hsv(locked)[0], 1f)
    }

    @Test
    fun theShiftLedJumpsClearlyWhenLocked() {
        // The default Shift colour is bright already; locking must still be obvious
        val blue = LedColors.Led.SHIFT.defaultColor // green
        val active = hsv(LedColors.shade(blue, LedColors.Level.ACTIVE))
        val locked = hsv(LedColors.shade(blue, LedColors.Level.LOCKED))
        assertTrue("brightness ${active[2]} -> ${locked[2]}", locked[2] - active[2] >= 0.3f)
        assertTrue("saturation ${active[1]} -> ${locked[1]}", locked[1] - active[1] >= 0.3f)
        assertEquals(active[0], locked[0], 1f)
    }

    @Test
    fun theLockedSweepGoesToAMoreIntenseVersion() {
        val base = Color.rgb(120, 90, 160)
        val intense = LedColors.intensify(base)
        assertTrue(hsv(intense)[1] > hsv(base)[1])
        assertTrue(hsv(intense)[2] > hsv(base)[2])
        assertEquals(hsv(base)[0], hsv(intense)[0], 1f)
        assertEquals(Color.alpha(base), Color.alpha(intense))
        // A strong sweep: the peak is at full brightness, the low point clearly darker than the colour
        assertEquals(1f, hsv(intense)[2], 0.01f)
        assertTrue(hsv(LedColors.deepen(base))[2] < hsv(base)[2] * 0.6f)
    }

    @Test
    fun everyLedHasItsOwnDefault() {
        assertEquals(LedColors.Led.entries.size, LedColors.Led.entries.map { it.defaultColor }.toSet().size)
    }
}
