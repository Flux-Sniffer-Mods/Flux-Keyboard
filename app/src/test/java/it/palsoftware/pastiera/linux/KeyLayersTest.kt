package it.palsoftware.pastiera.linux

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayersTest {
    private val layers = KeyLayers(
        alt = mapOf(KeyEvent.KEYCODE_Q to "0", KeyEvent.KEYCODE_W to "1"),
        sym = mapOf(KeyEvent.KEYCODE_Q to "~", KeyEvent.KEYCODE_M to "£"),
        symShifted = mapOf(KeyEvent.KEYCODE_M to "$")
    )

    @Test
    fun aTapIsForTheNextKeyOnly() {
        layers.altKey.down(); layers.altKey.up(100)
        assertTrue(layers.altKey.latched)
        assertEquals("0", layers.characters(KeyEvent.KEYCODE_Q, false))
        assertNull("the tap is spent", layers.characters(KeyEvent.KEYCODE_W, false))
    }

    @Test
    fun twoTapsLockTillTheNextTap() {
        val alt = layers.altKey
        alt.down(); alt.up(100)
        alt.down(); alt.up(300)
        assertTrue(alt.locked)
        assertEquals("0", layers.characters(KeyEvent.KEYCODE_Q, false))
        assertEquals("1", layers.characters(KeyEvent.KEYCODE_W, false))
        alt.down(); alt.up(2_000)
        assertFalse(alt.on)
    }

    @Test
    fun aSecondTapLaterTurnsItOffInstead() {
        val alt = layers.altKey
        alt.down(); alt.up(100)
        alt.down(); alt.up(900)
        assertFalse(alt.on)
    }

    @Test
    fun heldItIsOnOnlyWhileHeld() {
        val sym = layers.symKey
        sym.down()
        assertEquals("~", layers.characters(KeyEvent.KEYCODE_Q, false))
        assertEquals("~", layers.characters(KeyEvent.KEYCODE_Q, false))
        sym.up(400)
        assertFalse("held for other keys, not a tap", sym.on)
    }

    @Test
    fun symComesBeforeAltAndShiftHasItsOwnSymbols() {
        layers.symKey.down()
        layers.altKey.down()
        assertEquals("~", layers.characters(KeyEvent.KEYCODE_Q, false))
        assertEquals("$", layers.characters(KeyEvent.KEYCODE_M, true))
        assertEquals("£", layers.characters(KeyEvent.KEYCODE_M, false))
    }

    @Test
    fun aKeyWithNothingOnTheLayerIsItself() {
        layers.altKey.down()
        assertNull(layers.characters(KeyEvent.KEYCODE_TAB, false))
        assertNull(KeyLayers(emptyMap(), emptyMap()).characters(KeyEvent.KEYCODE_Q, false))
    }
}
