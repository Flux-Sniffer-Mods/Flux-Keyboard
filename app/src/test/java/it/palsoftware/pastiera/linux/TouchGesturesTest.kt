package it.palsoftware.pastiera.linux

import it.palsoftware.pastiera.linux.TouchGestures.Companion.LEFT
import it.palsoftware.pastiera.linux.TouchGestures.Companion.RIGHT
import it.palsoftware.pastiera.linux.TouchGestures.Companion.WHEEL_DOWN
import it.palsoftware.pastiera.linux.TouchGestures.Companion.WHEEL_LEFT
import it.palsoftware.pastiera.linux.TouchGestures.Companion.WHEEL_UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchGesturesTest {
    /** A 1080x1200 desktop filling the view, at the Titan 2 Elite's density. */
    private class Desk : TouchGestures.Desk {
        override val left = 0f
        override val top = 0f
        override val right = 1080f
        override val bottom = 1200f
        val events = mutableListOf<Triple<Int, Int, Int>>()
        var longPresses = 0
        override fun pointer(x: Float, y: Float, buttons: Int) { events += Triple(x.toInt(), y.toInt(), buttons) }
        override fun longPressed() { longPresses++ }
        /** Each button press, in order (a button held over several moves counted once). */
        fun presses(): List<Int> {
            var before = 0
            val out = mutableListOf<Int>()
            for (e in events) {
                if (e.third != 0 && e.third != before) out += e.third
                before = e.third
            }
            return out
        }
    }

    private val desk = Desk()
    private val touch = TouchGestures(desk, 2.625f, longPressMs = 500)
    private fun at(vararg p: Pair<Int, Int>) = p.map { floatArrayOf(it.first.toFloat(), it.second.toFloat()) }

    @Test
    fun aTapClicksWhereTheFingerWas() {
        touch.frame(at(500 to 600), 0)
        touch.frame(emptyList(), 90)
        assertEquals(listOf(LEFT), desk.presses())
        assertTrue(desk.events.contains(Triple(500, 600, LEFT)))
        assertEquals(Triple(500, 600, 0), desk.events.last())
    }

    @Test
    fun aLongPressLiftedRightClicksAndIsFelt() {
        touch.frame(at(500 to 600), 0)
        assertTrue(touch.ticking(100))
        touch.tick(520)
        assertEquals(1, desk.longPresses)
        touch.frame(emptyList(), 700)
        assertEquals(listOf(RIGHT), desk.presses())
    }

    @Test
    fun aLongPressThenAMoveDragsWithTheLeftButton() {
        touch.frame(at(500 to 600), 0)
        touch.tick(520)
        touch.frame(at(500 to 700), 600)
        touch.frame(at(500 to 800), 650)
        touch.frame(emptyList(), 700)
        assertEquals(listOf(LEFT), desk.presses())
        assertTrue(desk.events.contains(Triple(500, 800, LEFT)))
        assertEquals(Triple(500, 800, 0), desk.events.last())
    }

    @Test
    fun aDragScrollsTheContentWithTheFingerAndNeverClicks() {
        touch.frame(at(500 to 600), 0)
        touch.frame(at(500 to 700), 50)          // down 100 px: the content follows, so up
        assertEquals(listOf(WHEEL_UP, WHEEL_UP), desk.presses())
        desk.events.clear()
        touch.frame(at(503 to 500), 100)         // up 200: down; sideways ignored (one way only)
        touch.frame(emptyList(), 300)
        assertEquals(List(4) { WHEEL_DOWN }, desk.presses())
    }

    @Test
    fun aFlickGlidesOnAndComesToRest() {
        touch.frame(at(500 to 600), 0)
        touch.frame(at(500 to 640), 10)
        touch.frame(at(500 to 700), 20)
        touch.frame(at(500 to 760), 30)
        touch.frame(emptyList(), 35)
        val whileTouching = desk.presses().size
        var now = 35L
        while (touch.ticking(now) && now < 10_000) { now += 16; touch.tick(now) }
        assertTrue("glided on", desk.presses().size > whileTouching + 5)
        assertTrue("came to rest", now < 10_000)
        assertTrue(desk.presses().all { it == WHEEL_UP })
    }

    @Test
    fun aSlowLiftDoesNotGlide() {
        touch.frame(at(500 to 600), 0)
        touch.frame(at(500 to 700), 20)
        touch.frame(at(500 to 700), 400)         // held still
        touch.frame(emptyList(), 600)
        assertFalse(touch.ticking(600))
    }

    @Test
    fun aTwoFingerTapRightClicksBetweenThem() {
        touch.frame(at(400 to 600, 600 to 600), 0)
        touch.frame(emptyList(), 120)
        assertEquals(listOf(RIGHT), desk.presses())
        assertTrue(desk.events.contains(Triple(500, 600, RIGHT)))
    }

    @Test
    fun twoFingersScrollEitherWay() {
        touch.frame(at(400 to 600, 600 to 600), 0)
        touch.frame(at(480 to 600, 680 to 600), 60)   // right 80: the content follows, so left
        touch.frame(emptyList(), 400)
        assertEquals(listOf(WHEEL_LEFT, WHEEL_LEFT), desk.presses())
    }

    @Test
    fun theBottomEdgeHoldsThePointerThereAndNeverClicks() {
        touch.frame(at(500 to 1190), 0)
        touch.tick(16)
        touch.frame(emptyList(), 60)
        assertTrue(touch.ticking(300))
        touch.tick(300)
        assertFalse(touch.ticking(800))
        assertEquals(emptyList<Int>(), desk.presses())
        assertTrue(desk.events.all { it.second == 1200 })
    }

    @Test
    fun aTouchNearASideLandsOnIt() {
        touch.frame(at(5 to 600), 0)
        touch.frame(emptyList(), 80)
        assertTrue(desk.events.contains(Triple(0, 600, LEFT)))
    }

    @Test
    fun aDragTakenAwayLetsTheButtonGo() {
        touch.frame(at(500 to 600), 0)
        touch.tick(520)
        touch.frame(at(500 to 700), 600)
        touch.cancel()
        assertEquals(0, desk.events.last().third)
    }
}
