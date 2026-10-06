package it.palsoftware.pastiera.inputmethod.extrakeys

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraKeySetsTest {
    @Test
    fun theTopRowPressesTheRowLeftToRight() {
        assertEquals(0, ExtraKeySets.indexForPhysicalKey(KeyEvent.KEYCODE_Q))
        assertEquals(9, ExtraKeySets.indexForPhysicalKey(KeyEvent.KEYCODE_P))
        assertEquals(-1, ExtraKeySets.indexForPhysicalKey(KeyEvent.KEYCODE_A))
        assertEquals(KeyEvent.KEYCODE_E, ExtraKeySets.physicalKeyFor(2))
    }

    @Test
    fun aStoredRowIsReadBackAndBadEntriesDropped() {
        val keys = listOf(ExtraKey.ESC, ExtraKey.CTRL, ExtraKey.UP)
        assertEquals(keys, ExtraKeySets.parse(ExtraKeySets.serialize(keys) + ",nope,esc", ExtraKeySets.TEXT))
        assertEquals(ExtraKeySets.TERMINAL, ExtraKeySets.parse(null, ExtraKeySets.TERMINAL))
        assertEquals(ExtraKeySets.TERMINAL, ExtraKeySets.parse("", ExtraKeySets.TERMINAL))
    }

    @Test
    fun eachDefaultSetFitsTheTopRow() {
        assertEquals(ExtraKeySets.MAX_KEYS, ExtraKeySets.TERMINAL.size)
        assertEquals(ExtraKeySets.MAX_KEYS, ExtraKeySets.TEXT.size)
    }
}
