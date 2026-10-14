package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import it.palsoftware.pastiera.gaming.DolphinPads.Binding
import it.palsoftware.pastiera.gaming.FluxLayout.Layer
import it.palsoftware.pastiera.gaming.FluxLayout.WiiPad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DolphinPadsTest {
    @Test
    fun aGamesKeysRoundTrip() {
        val map = mapOf(
            "D-Pad/Up" to listOf(Binding(Layer.ALT, KeyEvent.KEYCODE_R)),
            "Shake" to emptyList()
        )
        assertEquals(map, DolphinPads.parse(DolphinPads.format(map)))
        assertEquals(emptyMap<String, List<Binding>>(), DolphinPads.parse(null))
    }

    @Test
    fun aGamesOwnKeyPressesOnlyItsButton() {
        // A wheelie on Alt + M (the layout's right stick down)
        val gc = FluxLayout.dolphinGameCube("Keys", remap = mapOf("D-Pad/Up" to listOf(Binding(Layer.ALT, KeyEvent.KEYCODE_M))))
        val up = gc.first { it.startsWith("D-Pad/Up = ") }
        assertTrue(up, "`M`" in up && "`W`" !in up)
        val cDown = gc.first { it.startsWith("C-Stick/Down = ") }
        assertTrue(cDown, "`M`" !in cDown)
        // The rest keep the layout's keys
        val a = gc.first { it.startsWith("Buttons/A = ") }
        assertTrue(a, "`M`" in a)
    }

    @Test
    fun movementsTheLayoutLeavesAloneCanBeGivenKeys() {
        val wii = FluxLayout.dolphinWii("Keys", WiiPad.NUNCHUK, remap = mapOf("Tilt/Forward" to listOf(Binding(Layer.NONE, KeyEvent.KEYCODE_R))))
        val tilt = wii.first { it.startsWith("Tilt/Forward = ") }
        assertTrue(tilt, "`R`" in tilt)
        val shake = FluxLayout.dolphinWii("Keys", WiiPad.SIDEWAYS, remap = mapOf("Shake" to listOf(Binding(Layer.NONE, KeyEvent.KEYCODE_R))))
        assertEquals(3, shake.count { it.startsWith("Shake/") && "`R`" in it })
    }

    @Test
    fun aRemoteWithItsOwnKeysIsLoadedForTheGame() {
        val own = FluxLayout.wiiPadSettings("", WiiPad.NUNCHUK, "Flux Keyboard RMCE01")
        assertTrue(own, "WiimoteProfile1 = Flux Keyboard RMCE01" in own)
        // Always named, so Dolphin loads it over whatever it had loaded
        assertEquals("[Controls]\nWiimoteProfile1 = Flux Keyboard\n", FluxLayout.wiiPadSettings("", WiiPad.NUNCHUK))
    }

    @Test
    fun buttonsDontOverlapOnAPhone() {
        val width = 336f
        WiiPad.entries.forEach { pad ->
            val height = width / DolphinPads.aspect(pad)
            val inputs = DolphinPads.inputs(pad)
            assertEquals(pad.name, inputs.size, inputs.map { it.id }.toSet().size)
            for (i in inputs.indices) for (j in i + 1 until inputs.size) {
                val a = inputs[i]; val b = inputs[j]
                val apart = kotlin.math.abs(a.x - b.x) * width >= 40f || kotlin.math.abs(a.y - b.y) * height >= 30f
                assertTrue("${pad.name}: ${a.label} and ${b.label} overlap", apart)
            }
        }
    }

    @Test
    fun aGameNamesOnlyItsOwnControllersProfile() {
        val wii = FluxLayout.gameProfileName(WiiPad.NUNCHUK, "RMCE01")
        val gc = FluxLayout.gameProfileName(WiiPad.GAMECUBE, "RMCE01")
        assertTrue(wii != gc)
        // A GameCube profile left from before, and one set in Dolphin itself, taken away
        val before = "[Controls]\nPadProfile1 = Flux Keyboard RMCE01\nPadProfile2 = Mine\n"
        val out = FluxLayout.withoutOtherFluxProfiles(FluxLayout.wiiPadSettings(before, WiiPad.NUNCHUK, wii), "WiimoteProfile1")
        assertTrue(out, "WiimoteProfile1 = $wii" in out)
        assertTrue(out, "Flux Keyboard RMCE01\n" !in out && "PadProfile1" !in out)
        // The person's own profiles are left alone
        assertTrue(out, "PadProfile2 = Mine" in out)
    }
}
