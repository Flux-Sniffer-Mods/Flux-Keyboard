package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchSlotsTest {
    private val events = mutableListOf<String>()
    private val slots = TouchSlots(
        { s, x, y -> events += "down $s $x $y" },
        { s, x, y -> events += "move $s $x $y" },
        { s -> events += "up $s" }
    )

    private fun feed(vararg lines: String) = lines.forEach { slots.line("/dev/input/event5: $it") }

    @Test
    fun twoFingersMoveApart() {
        feed(
            "EV_ABS ABS_MT_SLOT 00000000", "EV_ABS ABS_MT_TRACKING_ID 00000001",
            "EV_ABS ABS_MT_POSITION_X 00000064", "EV_ABS ABS_MT_POSITION_Y 0000000a",
            "EV_KEY BTN_TOUCH DOWN", "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_SLOT 00000001", "EV_ABS ABS_MT_TRACKING_ID 00000002",
            "EV_ABS ABS_MT_POSITION_X 000004b0", "EV_ABS ABS_MT_POSITION_Y 00000014",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_SLOT 00000000", "EV_ABS ABS_MT_POSITION_X 00000065",
            "EV_ABS ABS_MT_SLOT 00000001", "EV_ABS ABS_MT_POSITION_Y 00000015",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_SLOT 00000000", "EV_ABS ABS_MT_TRACKING_ID ffffffff",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_SLOT 00000001", "EV_ABS ABS_MT_POSITION_X 000004b1",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_TRACKING_ID ffffffff", "EV_KEY BTN_TOUCH UP", "EV_SYN SYN_REPORT 00000000"
        )
        assertEquals(
            listOf("down 0 100 10", "down 1 1200 20", "move 0 101 10", "move 1 1200 21", "up 0", "move 1 1201 21", "up 1"),
            events
        )
    }

    @Test
    fun aSurfaceWithoutTrackingIdsIsOneFinger() {
        feed(
            "EV_KEY BTN_TOUCH DOWN", "EV_ABS ABS_MT_POSITION_X 00000064", "EV_ABS ABS_MT_POSITION_Y 0000000a",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_POSITION_X 00000070", "EV_SYN SYN_REPORT 00000000",
            "EV_KEY BTN_TOUCH UP", "EV_SYN SYN_REPORT 00000000"
        )
        assertEquals(listOf("down 0 100 10", "move 0 112 10", "up 0"), events)
    }

    @Test
    fun anOlderDriverListingFingersIsFollowedByNearness() {
        feed(
            "EV_ABS ABS_MT_POSITION_X 00000064", "EV_ABS ABS_MT_POSITION_Y 0000000a", "EV_SYN SYN_MT_REPORT 00000000",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_POSITION_X 000004b0", "EV_ABS ABS_MT_POSITION_Y 00000014", "EV_SYN SYN_MT_REPORT 00000000",
            "EV_ABS ABS_MT_POSITION_X 00000066", "EV_ABS ABS_MT_POSITION_Y 0000000a", "EV_SYN SYN_MT_REPORT 00000000",
            "EV_SYN SYN_REPORT 00000000",
            "EV_ABS ABS_MT_POSITION_X 000004b2", "EV_ABS ABS_MT_POSITION_Y 00000014", "EV_SYN SYN_MT_REPORT 00000000",
            "EV_SYN SYN_REPORT 00000000",
            "EV_SYN SYN_MT_REPORT 00000000", "EV_SYN SYN_REPORT 00000000"
        )
        assertEquals(
            listOf("down 0 100 10", "move 0 102 10", "down 1 1200 20", "up 0", "move 1 1202 20", "up 1"),
            events
        )
    }
}
