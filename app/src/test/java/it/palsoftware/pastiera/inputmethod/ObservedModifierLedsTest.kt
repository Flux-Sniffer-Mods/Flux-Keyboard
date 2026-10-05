package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.inputmethod.ObservedModifierLeds.Level
import it.palsoftware.pastiera.inputmethod.ObservedModifierLeds.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class ObservedModifierLedsTest {
    private val leds = ObservedModifierLeds()
    private var clock = 1_000L

    private fun down(keyCode: Int) = leds.onKey(keyCode, KeyEvent.ACTION_DOWN, 0, clock, clock).also { clock += 10 }
    private fun up(keyCode: Int) = leds.onKey(keyCode, KeyEvent.ACTION_UP, 0, clock - 10, clock).also { clock += 10 }
    private fun tap(keyCode: Int) {
        down(keyCode)
        up(keyCode)
    }

    @Test
    fun tapLatchesUntilTheNextKey() {
        tap(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(Level.ACTIVE, leds.level(Modifier.SHIFT))

        tap(KeyEvent.KEYCODE_A)
        assertEquals(Level.OFF, leds.level(Modifier.SHIFT))
    }

    @Test
    fun secondTapLocksAndThirdUnlocks() {
        tap(KeyEvent.KEYCODE_ALT_LEFT)
        tap(KeyEvent.KEYCODE_ALT_LEFT)
        assertEquals(Level.LOCKED, leds.level(Modifier.ALT))

        tap(KeyEvent.KEYCODE_A)
        assertEquals(Level.LOCKED, leds.level(Modifier.ALT))

        tap(KeyEvent.KEYCODE_ALT_LEFT)
        assertEquals(Level.OFF, leds.level(Modifier.ALT))
    }

    @Test
    fun heldWithAnotherKeyDoesNotLatch() {
        down(KeyEvent.KEYCODE_SYM)
        assertEquals(Level.ACTIVE, leds.level(Modifier.SYM))
        tap(KeyEvent.KEYCODE_Q)
        up(KeyEvent.KEYCODE_SYM)

        assertEquals(Level.OFF, leds.level(Modifier.SYM))
    }

    @Test
    fun ctrlLatchesAndLocksLikeTheOthers() {
        // As the Titan X layout's Ctrl: tap = next key only
        tap(KeyEvent.KEYCODE_CTRL_LEFT)
        assertEquals(Level.ACTIVE, leds.level(Modifier.CTRL))
        tap(KeyEvent.KEYCODE_C)
        assertEquals(Level.OFF, leds.level(Modifier.CTRL))

        // Tap twice = locked, once more = off
        tap(KeyEvent.KEYCODE_CTRL_RIGHT)
        tap(KeyEvent.KEYCODE_CTRL_RIGHT)
        assertEquals(Level.LOCKED, leds.level(Modifier.CTRL))
        tap(KeyEvent.KEYCODE_CTRL_RIGHT)
        assertEquals(Level.OFF, leds.level(Modifier.CTRL))

        // Held with another key: only while held
        down(KeyEvent.KEYCODE_CTRL_LEFT)
        tap(KeyEvent.KEYCODE_V)
        up(KeyEvent.KEYCODE_CTRL_LEFT)
        assertEquals(Level.OFF, leds.level(Modifier.CTRL))
    }

    @Test
    fun theSameEventSeenTwiceCountsOnce() {
        assertTrue(leds.onKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 0, 50L, 50L))
        assertFalse(leds.onKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_DOWN, 0, 50L, 50L))
        leds.onKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 0, 50L, 60L)
        leds.onKey(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.ACTION_UP, 0, 50L, 60L)

        // One tap (latched), not two (locked)
        assertEquals(Level.ACTIVE, leds.level(Modifier.SHIFT))
    }

    @Test
    fun snapshotCarriesTheObservedStates() {
        tap(KeyEvent.KEYCODE_SYM)
        tap(KeyEvent.KEYCODE_SYM)
        tap(KeyEvent.KEYCODE_SHIFT_RIGHT)
        val base = StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = false,
            shiftOneShot = false,
            ctrlLatchActive = true,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = false,
            altLatchActive = true,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = 0
        )

        val observed = leds.applyTo(base)

        assertEquals(2, observed.symPage)
        assertTrue(observed.shiftOneShot)
        assertFalse(observed.capsLockEnabled)
        assertFalse(observed.ctrlLatchActive)
        assertFalse(observed.altLatchActive)
    }

    @Test
    fun snapshotShowsALatchedCtrl() {
        tap(KeyEvent.KEYCODE_CTRL_LEFT)
        val base = StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = false,
            shiftOneShot = false,
            ctrlLatchActive = false,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = false,
            altLatchActive = false,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = 0
        )

        val observed = leds.applyTo(base)

        assertTrue(observed.ctrlOneShot)
        assertFalse(observed.ctrlLatchActive)
    }
}
