package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Test

class BarsTest {
    @Test
    fun sidewaysOnUprightHasBarsAboveAndBelow() {
        // 1920x1080 on a 1080x1240 screen: shown 1080 wide, 607.5 high, 562 game pixels of bar each side
        val (x, y) = GameMode.bars(1920 to 1080, 1080 to 1240)
        assertEquals(0f, x, 0.5f)
        assertEquals(562.2f, y, 0.5f)
    }

    @Test
    fun sameShapeHasNone() {
        val (x, y) = GameMode.bars(1080 to 1240, 1080 to 1240)
        assertEquals(0f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }
}
