package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxLayoutTest {
    @Test
    fun theFaceButtonsSitWhereAControllersDo() {
        val none = FluxLayout.Modifiers()
        assertEquals(FluxLayout.Control.A, FluxLayout.controlFor(KeyEvent.KEYCODE_M, none)) // bottom
        assertEquals(FluxLayout.Control.Y, FluxLayout.controlFor(KeyEvent.KEYCODE_I, none)) // top
        assertEquals(FluxLayout.Control.X, FluxLayout.controlFor(KeyEvent.KEYCODE_J, none)) // left
        assertEquals(FluxLayout.Control.B, FluxLayout.controlFor(KeyEvent.KEYCODE_L, none)) // right
        assertEquals(FluxLayout.Control.R3, FluxLayout.controlFor(KeyEvent.KEYCODE_K, none))
    }

    @Test
    fun theModifiersChangeWhatTheKeysAre() {
        assertEquals(FluxLayout.Control.LS_DOWN, FluxLayout.controlFor(KeyEvent.KEYCODE_Z, FluxLayout.Modifiers()))
        assertEquals(FluxLayout.Control.DOWN, FluxLayout.controlFor(KeyEvent.KEYCODE_Z, FluxLayout.Modifiers(sym = true)))
        assertEquals(FluxLayout.Control.RS_DOWN, FluxLayout.controlFor(KeyEvent.KEYCODE_Z, FluxLayout.Modifiers(rightShift = true, shift = true)))
        assertEquals(FluxLayout.Control.RS_UP, FluxLayout.controlFor(KeyEvent.KEYCODE_I, FluxLayout.Modifiers(alt = true)))
        assertEquals(FluxLayout.Control.L3, FluxLayout.controlFor(KeyEvent.KEYCODE_S, FluxLayout.Modifiers()))
        assertEquals(FluxLayout.Control.START, FluxLayout.controlFor(KeyEvent.KEYCODE_SPACE, FluxLayout.Modifiers()))
        assertEquals(FluxLayout.Control.SELECT, FluxLayout.controlFor(KeyEvent.KEYCODE_SPACE, FluxLayout.Modifiers(shift = true)))
        assertEquals(FluxLayout.Control.HOME, FluxLayout.controlFor(KeyEvent.KEYCODE_SPACE, FluxLayout.Modifiers(alt = true)))
        assertEquals(null, FluxLayout.controlFor(KeyEvent.KEYCODE_X, FluxLayout.Modifiers()))
    }

    @Test
    fun everyControlIsSentAsAKeyOfItsOwn() {
        val keys = FluxLayout.Control.entries.map { FluxLayout.keyOf(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun dolphinGetsTheKeysByName() {
        val lines = FluxLayout.dolphinGameCube("TitanKey")
        assertEquals("Device = Android/0/TitanKey", lines.first())
        assertTrue("Buttons/Start = `Space`" in lines)
        assertTrue("Main Stick/Up = `W`" in lines)
        assertTrue("C-Stick/Left = `F`" in lines)
        assertTrue("Triggers/L = `U`" in lines)
        assertTrue("Buttons/Z = `E`" in lines)
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
        assertTrue("Cross = 1-${KeyEvent.KEYCODE_M}" in FluxLayout.pspMapping())
    }

    @Test
    fun eachWiiControllerHasItsOwnSetUp() {
        val classic = FluxLayout.dolphinWii("TitanKey", FluxLayout.WiiPad.CLASSIC)
        assertTrue("Extension = Classic" in classic)
        assertTrue("Classic/Right Stick/Up = `T`" in classic)
        val sideways = FluxLayout.dolphinWii("TitanKey", FluxLayout.WiiPad.SIDEWAYS)
        assertTrue("Options/Sideways Wiimote = True" in sideways)
        assertTrue("Buttons/2 = `M`" in sideways)
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
        assertTrue("SIDevice0 = 6" in gc)
        // Back to the Nunchuk: nothing of it kept
        assertEquals("", FluxLayout.wiiPadSettings(gc, FluxLayout.WiiPad.NUNCHUK))
    }

    @Test
    fun altVolumeIsTheTriggerWithoutItsClick() {
        val alt = FluxLayout.Modifiers(alt = true)
        assertEquals(FluxLayout.Control.L2_ANALOG, FluxLayout.defaultControlFor(KeyEvent.KEYCODE_VOLUME_UP, alt))
        assertEquals(FluxLayout.Control.R2, FluxLayout.defaultControlFor(KeyEvent.KEYCODE_VOLUME_DOWN, FluxLayout.Modifiers()))
        val gc = FluxLayout.dolphinGameCube("Keys")
        assertTrue("Triggers/L = `U`" in gc)
        assertTrue("Triggers/L-Analog = `U` | `C`" in gc)
    }

    @Test
    fun changedKeysRoundTrip() {
        val map = mapOf(
            (FluxLayout.Layer.ALT to KeyEvent.KEYCODE_Q) to FluxLayout.Control.A,
            (FluxLayout.Layer.NONE to KeyEvent.KEYCODE_E) to null
        )
        assertEquals(map, FluxLayout.parseOverrides(FluxLayout.formatOverrides(map)))
        assertEquals(emptyMap<Any, Any>(), FluxLayout.parseOverrides(""))
    }
}
