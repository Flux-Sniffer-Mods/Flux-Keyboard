package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DolphinOverlayTest {
    @Test
    fun addsWhatIsNotSet() {
        val ini = "[General]\nISOPath0 = /x\n\n[Android]\nShowInputOverlay = True\n"
        val out = FluxLayout.dolphinOverlayIni(ini)
        assertTrue(out, out.contains("JoystickRelCenter = True"))
        assertTrue(out, out.contains("ControlOpacity = 0"))
        assertTrue(out, out.contains("ShowInputOverlay = True"))
        assertTrue(out, out.contains("ISOPath0 = /x"))
    }

    @Test
    fun keepsYourOwnChoices() {
        val ini = "[Android]\nControlOpacity = 40\nJoystickRelCenter = False\n"
        assertEquals(ini, FluxLayout.dolphinOverlayIni(ini))
    }

    @Test
    fun addsTheSectionWhenMissing() {
        val out = FluxLayout.dolphinOverlayIni("")
        assertTrue(out, out.startsWith("[Android]"))
    }
}
