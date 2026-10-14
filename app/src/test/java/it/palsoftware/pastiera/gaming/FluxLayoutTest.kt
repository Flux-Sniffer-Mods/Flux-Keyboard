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
        fun line(name: String) = lines.first { it.startsWith("$name = ") }
        assertTrue(line("Buttons/Start"), "`Space`" in line("Buttons/Start"))
        assertTrue(line("Main Stick/Up"), "`W`" in line("Main Stick/Up"))
        // The right stick: Alt with J, or right Shift with A
        assertTrue(line("C-Stick/Left"), "`J`" in line("C-Stick/Left") && "`Right Shift`" in line("C-Stick/Left"))
        assertTrue(line("Buttons/Z"), "`E`" in line("Buttons/Z"))
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
        val rightUp = classic.first { it.startsWith("Classic/Right Stick/Up = ") }
        assertTrue(rightUp, "`I`" in rightUp && "`W`" in rightUp)
        val sideways = FluxLayout.dolphinWii("TitanKey", FluxLayout.WiiPad.SIDEWAYS)
        assertTrue("Options/Sideways Wiimote = True" in sideways)
        val two = sideways.first { it.startsWith("Buttons/2 = ") }
        assertTrue(two, "`M`" in two)
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
        // Back to the Nunchuk: nothing of the GameCube controller kept, the Wii Remote's profile named
        assertEquals("[Controls]\nWiimoteProfile1 = Flux Keyboard\n", FluxLayout.wiiPadSettings(gc, FluxLayout.WiiPad.NUNCHUK))
    }

    @Test
    fun altVolumeIsTheTriggerWithoutItsClick() {
        val alt = FluxLayout.Modifiers(alt = true)
        assertEquals(FluxLayout.Control.L2_ANALOG, FluxLayout.defaultControlFor(KeyEvent.KEYCODE_VOLUME_DOWN, alt))
        // Held sideways, Volume Up is on the right
        assertEquals(FluxLayout.Control.R2, FluxLayout.defaultControlFor(KeyEvent.KEYCODE_VOLUME_UP, FluxLayout.Modifiers()))
        val gc = FluxLayout.dolphinGameCube("Keys")
        val l = gc.first { it.startsWith("Triggers/L = ") }
        val analog = gc.first { it.startsWith("Triggers/L-Analog = ") }
        // The click only without Alt; all the way with or without
        assertTrue(l, "`Volume Down`" in l && "(`Left Alt` | `Right Alt`)" !in l)
        assertTrue(analog, "(`Left Alt` | `Right Alt`)" in analog)
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

    @Test
    fun swapIsWrittenIntoTheEmulator() {
        val gc = FluxLayout.dolphinGameCube("Keys", swap = true)
        val a = gc.first { it.startsWith("Buttons/A = ") }
        assertTrue(a, "`L`" in a && "`M`" !in a)
        val plain = FluxLayout.dolphinGameCube("Keys").first { it.startsWith("Buttons/A = ") }
        assertTrue(plain, "`M`" in plain)
    }

    @Test
    fun combinationsAreWrittenForDolphin() {
        val gc = FluxLayout.dolphinGameCube("Keys")
        val up = gc.first { it.startsWith("D-Pad/Up = ") }
        assertTrue(up, "`Sym`" in up && "`W`" in up)
        val start = gc.first { it.startsWith("Buttons/Start = ") }
        assertTrue(start, "`Space`" in start && "!`Left Shift`" in start)
        val l = gc.first { it.startsWith("Triggers/L = ") }
        assertTrue(l, "`Volume Down`" in l)
    }

    @Test
    fun theLayoutsKeysAreKnownWhateverTheModifier() {
        assertTrue(FluxLayout.isLayoutKey(KeyEvent.KEYCODE_W))
        assertTrue(FluxLayout.isLayoutKey(KeyEvent.KEYCODE_VOLUME_UP))
        // Keys the layout doesn't use go to the game as they are
        assertTrue(!FluxLayout.isLayoutKey(KeyEvent.KEYCODE_P))
        assertTrue(!FluxLayout.isLayoutKey(KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun dolphinReadsTheVolumeKeysFromTheirOwnDevice() {
        val gc = FluxLayout.dolphinGameCube("TitanKey", volume = listOf("Android/0/mtk-kpd"))
        val l = gc.first { it.startsWith("Triggers/L = ") }
        assertTrue(l, "`Android/0/mtk-kpd:Volume Down`" in l)
        // The keyboard's own keys stay unnamed (its device is the profile's)
        val a = gc.first { it.startsWith("Buttons/A = ") }
        assertTrue(a, ":" !in a)
    }

    @Test
    fun aGamesOwnKeysApplyOnlyWhileItPlays() {
        val none = FluxLayout.Modifiers()
        try {
            FluxLayout.gameOverrides = mapOf((FluxLayout.Layer.NONE to KeyEvent.KEYCODE_R) to FluxLayout.Control.R3)
            assertEquals(FluxLayout.Control.R3, FluxLayout.controlFor(KeyEvent.KEYCODE_R, none))
            assertTrue(FluxLayout.isLayoutKey(KeyEvent.KEYCODE_R))
            // Dolphin's controls are written with the layout as everyone has it
            assertEquals(null, FluxLayout.layoutControlFor(KeyEvent.KEYCODE_R, none))
        } finally {
            FluxLayout.gameOverrides = emptyMap()
        }
        assertEquals(null, FluxLayout.controlFor(KeyEvent.KEYCODE_R, none))
    }
}
