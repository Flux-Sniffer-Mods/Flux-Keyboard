package it.palsoftware.pastiera.inputmethod.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadPickDistanceTest {
    private val titan = TrackpadAxisRange(0f, 1079f)

    @Test
    fun oneKeyAcrossIsEnough() {
        val distance = TrackpadCoordinateMapper.pickDistance(40f, titan)
        // Just under a key's width (108): one key to the next, not a key press's drift
        assertTrue(distance in 100f..108f)
        assertEquals(300f, TrackpadCoordinateMapper.pickDistance(300f, titan))
        assertEquals(40f, TrackpadCoordinateMapper.pickDistance(40f, null))
    }

    @Test
    fun slowSlidesStillCount() {
        assertTrue(TrackpadCoordinateMapper.pickQuickEnough(110f, 103f, 400L, 2f))
        assertTrue(TrackpadCoordinateMapper.pickQuickEnough(200f, 103f, 700L, 2f))
        assertFalse(TrackpadCoordinateMapper.pickQuickEnough(110f, 103f, 3000L, 2f))
    }
}
