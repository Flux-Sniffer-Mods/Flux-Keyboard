package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameFilesTest {
    private fun header(size: Int, vararg at: Pair<Int, Long>): List<Int> {
        val bytes = MutableList(size) { 0 }
        at.forEach { (offset, word) -> for (i in 0..3) bytes[offset + i] = ((word shr (24 - 8 * i)) and 0xFF).toInt() }
        return bytes
    }

    @Test
    fun aDiscImageSaysWhetherItsWiiOrGameCube() {
        assertEquals(true, GameFiles.byHeader(header(96, 0x18 to 0x5D1C9EA3L)))
        assertEquals(false, GameFiles.byHeader(header(96, 0x1C to 0xC2339F3DL)))
        assertNull(GameFiles.byHeader(header(96)))
    }

    @Test
    fun anRvzFileSaysItsDiscType() {
        val rvz = header(96, 0x48 to 2L).toMutableList().apply { set(0, 'R'.code); set(1, 'V'.code); set(2, 'Z'.code); set(3, 1) }
        assertEquals(true, GameFiles.byHeader(rvz))
        rvz[0x4B] = 1
        assertEquals(false, GameFiles.byHeader(rvz))
    }

    @Test
    fun theFileKindOrFolderCanTell() {
        assertEquals(true, GameFiles.byName("/storage/emulated/0/Games/Dolphin/Mario Galaxy.wbfs"))
        assertEquals(true, GameFiles.byName("/storage/emulated/0/ROMs/wii/Zelda.rvz"))
        assertEquals(false, GameFiles.byName("/storage/emulated/0/ROMs/gc/Wind Waker.rvz"))
        assertNull(GameFiles.byName("/storage/emulated/0/Games/Dolphin/Game.iso"))
    }

    @Test
    fun aShortcutsGameFileIsFoundOnThePhone() {
        assertEquals(
            "/storage/emulated/0/Download/Flux Keyboard/Games/Dolphin/Wind Waker.rvz",
            GameFiles.pathOf("content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2FFlux%20Keyboard%2FGames%2FDolphin%2FWind%20Waker.rvz")
        )
    }
}
