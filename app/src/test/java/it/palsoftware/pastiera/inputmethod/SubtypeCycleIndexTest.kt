package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ctrl+Space goes forwards, Ctrl+Shift+Space backwards, both wrapping round (palsoftware/pastiera#267). */
class SubtypeCycleIndexTest {
    @Test
    fun forwardsAndBackwardsWrap() {
        assertEquals(1, SubtypeCycler.cycleIndex(0, 3, backwards = false))
        assertEquals(0, SubtypeCycler.cycleIndex(2, 3, backwards = false))
        assertEquals(2, SubtypeCycler.cycleIndex(0, 3, backwards = true))
        assertEquals(1, SubtypeCycler.cycleIndex(2, 3, backwards = true))
    }

    @Test
    fun unknownCurrentStartsAtAnEnd() {
        assertEquals(0, SubtypeCycler.cycleIndex(-1, 3, backwards = false))
        assertEquals(2, SubtypeCycler.cycleIndex(-1, 3, backwards = true))
    }
}
