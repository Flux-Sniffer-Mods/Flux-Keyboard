package it.palsoftware.pastiera.sym

import org.junit.Assert.assertEquals
import org.junit.Test

class SwapKeysTest {
    @Test
    fun twoMappedKeysSwap() {
        assertEquals(mapOf(1 to "b", 2 to "a", 3 to "c"), swapKeys(mapOf(1 to "a", 2 to "b", 3 to "c"), 1, 2))
    }

    @Test
    fun anEmptyKeyTakesTheOthersAndLeavesItEmpty() {
        assertEquals(mapOf(2 to "a"), swapKeys(mapOf(1 to "a"), 1, 2))
        assertEquals(mapOf(1 to "a"), swapKeys(mapOf(2 to "a"), 1, 2))
    }
}
