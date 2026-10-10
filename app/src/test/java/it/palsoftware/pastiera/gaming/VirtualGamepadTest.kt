package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualGamepadTest {
    @Test
    fun theDpadIsAHat() {
        assertEquals(8, VirtualGamepad.hatOf(false, false, false, false))
        assertEquals(0, VirtualGamepad.hatOf(true, false, false, false))
        assertEquals(1, VirtualGamepad.hatOf(true, false, false, true))
        assertEquals(4, VirtualGamepad.hatOf(false, true, false, false))
        assertEquals(7, VirtualGamepad.hatOf(true, false, true, false))
        // Opposite directions cancel
        assertEquals(8, VirtualGamepad.hatOf(true, true, false, false))
    }

    @Test
    fun aReportHoldsButtonsHatAndSticks() {
        val report = VirtualGamepad.report((1 shl 0) or (1 shl 11), 2, intArrayOf(127, -127, 0, 64))
        assertArrayEquals(byteArrayOf(0x01, 0x08, 0x02, 127, -127, 0, 64), report)
    }

    @Test
    fun theDescriptorIsOneWholeCollection() {
        val d = VirtualGamepad.DESCRIPTOR
        assertEquals(0xA1.toByte(), d[4])
        assertEquals(0xC0.toByte(), d.last())
    }
}
