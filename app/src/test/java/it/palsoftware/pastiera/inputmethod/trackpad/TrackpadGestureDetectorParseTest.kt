package it.palsoftware.pastiera.inputmethod.trackpad

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackpadGestureDetectorParseTest {

    @Test
    fun titan2EliteTouch_reportsDownMovesAndUp() {
        val touches = mutableListOf<Triple<TrackpadGestureDetector.TouchPhase, Float, Float>>()
        val detector = TrackpadGestureDetector(
            isEnabled = { true },
            onTouch = { phase, x, y, _ -> touches += Triple(phase, x, y) },
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
        // As the Titan 2 Elite's touchPad reports a swipe (getevent -l)
        listOf(
            "EV_KEY       BTN_TOUCH            DOWN",
            "EV_ABS       ABS_MT_TOUCH_MAJOR   00000003",
            "EV_ABS       ABS_MT_POSITION_X    000002ee",
            "EV_ABS       ABS_MT_POSITION_Y    000000c1",
            "EV_SYN       SYN_MT_REPORT        00000000",
            "EV_SYN       SYN_REPORT           00000000",
            "EV_ABS       ABS_MT_POSITION_X    000002ed",
            "EV_ABS       ABS_MT_POSITION_Y    000000c3",
            "EV_SYN       SYN_MT_REPORT        00000000",
            "EV_SYN       SYN_REPORT           00000000",
            "EV_KEY       BTN_TOUCH            UP",
            "EV_SYN       SYN_REPORT           00000000"
        ).forEach(detector::parseTrackpadEvent)

        assertEquals(
            listOf(
                Triple(TrackpadGestureDetector.TouchPhase.DOWN, 750f, 193f),
                Triple(TrackpadGestureDetector.TouchPhase.MOVE, 749f, 195f),
                Triple(TrackpadGestureDetector.TouchPhase.UP, 749f, 195f)
            ),
            touches
        )
    }
}
