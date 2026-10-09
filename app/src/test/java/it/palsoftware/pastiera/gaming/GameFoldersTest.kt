package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFoldersTest {
    private val root = GameFolders.ROOT

    @Test
    fun exportsAndGamesAreReadByTheirFolder() {
        val listing = listOf(
            "$root/GameNative/Hades.steam\t1145360",
            "$root/GameNative/Empty.steam\t",
            "$root/GameHub/Celeste.steam\t504230",
            "$root/Dolphin/Wind Waker.rvz\t",
            "$root/Dolphin/Mario/Sunshine.iso\t",
            "$root/Eden/notes.txt\tsomething",
            "/storage/emulated/0/ROMs/psp/Patapon.cso\t"
        ).joinToString("\n")
        val found = GameFolders.parse(listing)
        assertEquals(
            listOf("Hades" to GameFolders.Player.GAMENATIVE, "Celeste" to GameFolders.Player.GAMEHUB,
                "Wind Waker" to GameFolders.Player.DOLPHIN, "Sunshine" to GameFolders.Player.DOLPHIN,
                "Patapon" to GameFolders.Player.PPSSPP),
            found.map { it.name to it.player }
        )
        assertEquals("1145360", found.first().content)
    }

    @Test
    fun yourOwnFolderSetToAPlayerTakesItsGamesWhateverTheNames() {
        val custom = listOf(GameFolders.Custom("/storage/emulated/0/Games/Cube", "Dolphin"),
            GameFolders.Custom("/storage/emulated/0/Exports"))
        val listing = listOf(
            "/storage/emulated/0/Games/Cube/Metroid Prime.iso\t",
            "/storage/emulated/0/Games/Cube/cover.png\t",
            "/storage/emulated/0/Exports/GameNative/Hades.steam\t1145360"
        ).joinToString("\n")
        assertEquals(
            listOf("Metroid Prime" to GameFolders.Player.DOLPHIN, "Hades" to GameFolders.Player.GAMENATIVE),
            GameFolders.parse(listing, custom).map { it.name to it.player }
        )
    }
}
