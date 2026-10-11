package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StickShapeTest {
    @Test
    fun insideDeadZoneIsStill() {
        assertEquals(0f to 0f, GameMode.stickShape(0.05f, 0f, 0.08f))
    }

    @Test
    fun edgeIsFullPush() {
        val (x, y) = GameMode.stickShape(0f, 1f, 0.08f)
        assertEquals(0f, x, 0.001f)
        assertEquals(1f, y, 0.001f)
    }

    @Test
    fun diagonalStopsAtTheCircle() {
        val (x, y) = GameMode.stickShape(1f, 1f, 0f)
        assertEquals(1f, kotlin.math.sqrt(x * x + y * y), 0.001f)
    }

    @Test
    fun downwardPushWorks() {
        val (_, y) = GameMode.stickShape(0f, 0.5f, 0.1f)
        assertEquals((0.5f - 0.1f) / 0.9f, y, 0.001f)
    }
}

class SidewaysDensityTest {
    @Test
    fun shorterScreenGetsLowerDensity() {
        // A 1080-wide phone at 440 dpi drawn sideways 700 rows high: the same dp across
        org.junit.Assert.assertEquals(285, GameMode.sidewaysDensity(440, 1080, 1240 to 700))
    }

    @Test
    fun sameShortSideKeepsDensity() {
        org.junit.Assert.assertEquals(440, GameMode.sidewaysDensity(440, 1080, 1920 to 1080))
    }
}
