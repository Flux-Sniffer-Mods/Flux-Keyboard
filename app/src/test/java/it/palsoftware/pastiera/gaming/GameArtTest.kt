package it.palsoftware.pastiera.gaming

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameArtTest {
    @Test
    fun aSteamGamesIdComesFromItsLaunch() {
        assertEquals("1332010", GameArt.steamId(Intent("app.gamenative.LAUNCH_GAME").putExtra("app_id", 1332010).putExtra("game_source", "STEAM")))
        assertEquals("367520", GameArt.steamId(Intent("gamehub.lite.LAUNCH_GAME").putExtra("steamAppId", "367520")))
        assertNull(GameArt.steamId(Intent("app.gamenative.LAUNCH_GAME").putExtra("app_id", 5).putExtra("game_source", "GOG")))
    }

    @Test
    fun libretrosBoxArtIsFoundByTheGamesName() {
        assertEquals(
            "https://raw.githubusercontent.com/libretro-thumbnails/Sony_-_PlayStation_Portable/master/Named_Boxarts/Lumines%20-%20Puzzle%20Fusion%20%28USA%29.png",
            GameArt.libretro("Sony - PlayStation Portable", "Lumines - Puzzle Fusion (USA)")
        )
        assertEquals(true, GameArt.libretro("Nintendo - Wii", "Game: Name").endsWith("Game_%20Name.png"))
    }
}
