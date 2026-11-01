package it.palsoftware.pastiera.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiracastTest {
    @Test
    fun screensFoundComeFirstThenRemembered() {
        val status = Miracast.parse(
            "state\t3\t0\t0\t\n" +
                "screen\t02:aa:bb:cc:dd:ee\tLiving room TV\t0\t0\t1\n" +
                "screen\t02:11:22:33:44:55\t[LG] webOS TV\t1\t1\t0\n"
        )!!
        assertEquals(Miracast.FEATURE_ON, status.feature)
        assertFalse(status.connected)
        assertEquals(listOf("[LG] webOS TV", "Living room TV"), status.offered.map { it.name })
    }

    @Test
    fun connectedScreenIsReported() {
        val status = Miracast.parse("state\t3\t0\t2\t02:11:22:33:44:55\nscreen\t02:11:22:33:44:55\tTV\t1\t1\t1")!!
        assertTrue(status.connected)
        assertEquals("02:11:22:33:44:55", status.activeAddress)
        assertEquals(1, status.screens.size)
    }

    @Test
    fun noStateLineMeansNoAnswer() {
        assertNull(Miracast.parse("miracast: java.lang.NoSuchMethodException"))
        assertEquals(Miracast.FEATURE_UNAVAILABLE, Miracast.parse("state\t0\t0\t0\t")!!.feature)
    }

    @Test
    fun aScreenWithoutANameShowsItsAddress() {
        val status = Miracast.parse("state\t3\t0\t0\t\nscreen\t02:00:00:00:00:01\t\t1\t1\t0\nscreen\t02:00:00:00:00:01\tdup\t1\t1\t0")!!
        assertEquals(listOf("02:00:00:00:00:01"), status.offered.map { it.name })
    }
}
