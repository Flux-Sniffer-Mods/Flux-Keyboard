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
        assertTrue("Main Stick/Up = `T`" in lines)
        assertTrue("C-Stick/Left = `B`" in lines)
        assertTrue("Triggers/L = `Volume Up` | `Q`" in lines)
        assertTrue("Buttons/Z = `Z` | `C`" in lines)
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

    @Test
    fun eachWiiControllerHasItsOwnSetUp() {
        val classic = FluxLayout.dolphinWii("TitanKey", FluxLayout.WiiPad.CLASSIC)
        assertTrue("Extension = Classic" in classic)
        assertTrue("Classic/Right Stick/Up = `J`" in classic)
        val sideways = FluxLayout.dolphinWii("TitanKey", FluxLayout.WiiPad.SIDEWAYS)
        assertTrue("Options/Sideways Wiimote = True" in sideways)
        assertTrue("Buttons/2 = `L`" in sideways)
    }

    @Test
    fun aGamesControllerIsOneKeyInItsSettings() {
        val ini = "[Core]\nCPUThread = True\n"
        val set = FluxLayout.withKey(ini, "Controls", "WiimoteProfile1", "Flux Keyboard (sideways)")
        assertEquals("[Core]\nCPUThread = True\n\n[Controls]\nWiimoteProfile1 = Flux Keyboard (sideways)\n", set)
        assertEquals("[Core]\nCPUThread = True\n", FluxLayout.withKey(set, "Controls", "WiimoteProfile1", null))
    }

    @Test
    fun aWiiGameCanTakeAGameCubeController() {
        val gc = FluxLayout.wiiPadSettings("", FluxLayout.WiiPad.GAMECUBE)
        assertTrue("PadType0 = 6" in gc)
        assertTrue("WiimoteSource0 = 0" in gc)
        assertTrue("PadProfile1 = Flux Keyboard" in gc)
        // Back to the Nunchuk: nothing of it kept
        assertEquals("", FluxLayout.wiiPadSettings(gc, FluxLayout.WiiPad.NUNCHUK))
    }

    @Test
    fun theVolumeKeysAreTheLeftShoulderAndTrigger() {
        assertEquals(6, FluxLayout.PAD_BUTTONS[KeyEvent.KEYCODE_VOLUME_UP])
        assertEquals(8, FluxLayout.PAD_BUTTONS[KeyEvent.KEYCODE_VOLUME_DOWN])
    }

    @Test
    fun theRedSideKeysDeviceIsFound() {
        val dump = """add device 7: /dev/input/event0
  name:     "gpio-keys"
  events:
    KEY (0001): 0072  0073
add device 8: /dev/input/event1
  name:     "mtk-pmic-keys"
  events:
    KEY (0001): 002a  0074  00f9
"""
        assertEquals("/dev/input/event1", GameMode.sideKeyDevice(dump))
    }
}
