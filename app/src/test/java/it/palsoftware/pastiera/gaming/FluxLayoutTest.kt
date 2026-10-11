package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxLayoutTest {
    @Test
    fun theFaceButtonsSitWhereAControllersDo() {
        assertEquals(0, FluxLayout.PAD_BUTTONS[KeyEvent.KEYCODE_L]) // A, bottom
        assertEquals(4, FluxLayout.PAD_BUTTONS[KeyEvent.KEYCODE_O]) // Y, top
        assertEquals(11, FluxLayout.PAD_BUTTONS[KeyEvent.KEYCODE_SPACE]) // Start
    }

    @Test
    fun dolphinGetsTheKeysByName() {
        val lines = FluxLayout.dolphinGameCube("TitanKey")
        assertEquals("Device = Android/0/TitanKey", lines.first())
        assertTrue("Buttons/Start = `Space`" in lines)
        assertTrue("Main Stick/Up = `Up`" in lines)
        assertTrue("C-Stick/Left = `B`" in lines)
        assertTrue("Triggers/L = `Q` | `Z` | `Volume Up` | `Volume Down`" in lines)
    }

    @Test
    fun aSectionIsReplacedAndTheRestKept() {
        val ini = "[GCPad1]\nDevice = old\nButtons/A = `X`\n\n[GCPad2]\nDevice = other\n"
        val out = FluxLayout.withSection(ini, "GCPad1", listOf("Device = new"))
        assertEquals("[GCPad1]\nDevice = new\n\n[GCPad2]\nDevice = other\n", out)
        assertEquals("[ControlMapping]\nStart = 1-62\n", FluxLayout.withSection("", "ControlMapping", listOf("Start = 1-62")))
    }

    @Test
    fun ppssppReadsTheKeyboardsCodes() {
        assertTrue("Start = 1-${KeyEvent.KEYCODE_SPACE}" in FluxLayout.pspMapping())
        assertTrue("Cross = 1-${KeyEvent.KEYCODE_L}" in FluxLayout.pspMapping())
    }
}
