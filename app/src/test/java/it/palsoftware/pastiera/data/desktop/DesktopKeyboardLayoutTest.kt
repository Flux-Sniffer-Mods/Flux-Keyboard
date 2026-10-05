package it.palsoftware.pastiera.data.desktop

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DesktopKeyboardLayoutTest {

    private fun keyLine(layout: String, name: String): String =
        layout.lineSequence().first { it.trimStart().startsWith("key <$name>") }

    @Test
    fun altAndSymLayersComeFromPastierasMappings() {
        val layout = DesktopKeyboardLayout.build(
            alt = mapOf(KeyEvent.KEYCODE_Q to "0", KeyEvent.KEYCODE_M to "."),
            symCustom = mapOf(KeyEvent.KEYCODE_M to "£"),
            symDefault = mapOf(KeyEvent.KEYCODE_Q to "~", KeyEvent.KEYCODE_M to "$")
        )

        assertTrue(keyLine(layout, "AD01").contains("[ q, Q, 0, 0, U007E, U007E, 0, 0 ]"))
        // A changed Sym key keeps the default on Sym+Shift
        assertTrue(keyLine(layout, "AB07").contains("[ m, M, U002E, U002E, U00A3, U0024, U002E, U002E ]"))
        // No mapping: the key only types its letter
        assertTrue(keyLine(layout, "AD02").contains("[ w, W, NoSymbol, NoSymbol, NoSymbol, NoSymbol, NoSymbol, NoSymbol ]"))
        assertEquals(26, layout.lineSequence().count { it.trimStart().startsWith("key <A") })
        // The modifiers latch as in Pastiera
        assertTrue(layout.contains("LatchMods(modifiers = Control, clearLocks, latchToLock)"))
        assertTrue(layout.contains("ISO_Level5_Latch"))
    }

    @Test
    fun keysymsNameLettersAndDigitsAndUseUnicodeForTheRest() {
        assertEquals("a", DesktopKeyboardLayout.keysym("a"))
        assertEquals("7", DesktopKeyboardLayout.keysym("7"))
        assertEquals("U00E9", DesktopKeyboardLayout.keysym("é"))
        assertEquals("U1F600", DesktopKeyboardLayout.keysym(String(Character.toChars(0x1F600))))
        // An X key carries one character: sequences (emoji with a variation selector) can't
        assertEquals("NoSymbol", DesktopKeyboardLayout.keysym("\u2764\uFE0F"))
        assertEquals("NoSymbol", DesktopKeyboardLayout.keysym(""))
    }

    @Test
    fun exportWritesTheFileOnceAndLeavesItWhenUnchanged() {
        val context = RuntimeEnvironment.getApplication()
        val file = DesktopKeyboardLayout.export(context)
        assertNotNull(file)
        assertTrue(file!!.isFile)
        assertEquals(DesktopKeyboardLayout.FILE_NAME, file.name)
        val written = file.lastModified()
        file.setLastModified(written - 60_000)

        DesktopKeyboardLayout.export(context)

        assertEquals(written - 60_000, file.lastModified())
    }
}
