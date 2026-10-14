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

    @Test
    fun aGamesOnScreenControlsAreSetAsItStarts() {
        val off = FluxLayout.dolphinOverlayIni("", FluxLayout.DolphinOverlay.OFF)
        assertTrue(off, "ShowInputOverlay = False" in off)
        val invisible = FluxLayout.dolphinOverlayIni(off, FluxLayout.DolphinOverlay.INVISIBLE)
        assertTrue(invisible, "ShowInputOverlay = True" in invisible && "ControlOpacity = 0" in invisible)
        val shown = FluxLayout.dolphinOverlayIni(invisible, FluxLayout.DolphinOverlay.SHOWN)
        assertTrue(shown, "ShowInputOverlay = True" in shown && "ControlOpacity = 65" in shown)
        // A visible opacity of the person's own is kept
        val own = FluxLayout.dolphinOverlayIni("[Android]\nControlOpacity = 40\n", FluxLayout.DolphinOverlay.SHOWN)
        assertTrue(own, "ControlOpacity = 40" in own)
    }
}
