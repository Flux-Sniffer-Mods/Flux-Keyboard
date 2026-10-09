package it.palsoftware.pastiera.gaming

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A shortcut's game survives being stored as text and read back, exactly as the app needs it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameLaunchIntentTest {
    private fun roundTrip(intent: Intent): Intent =
        Intent.parseUri(intent.toUri(Intent.URI_INTENT_SCHEME), Intent.URI_INTENT_SCHEME)

    @Test
    fun gameNativeGetsItsGameIdAsANumberAndItsStore() {
        val intent = roundTrip(GameFolders.intentFor(GameFolders.Player.GAMENATIVE, "app.gamenative", "app.gamenative.MainActivity", null, "1145360", "GOG")!!)
        assertEquals("app.gamenative.LAUNCH_GAME", intent.action)
        assertEquals(1145360, intent.getIntExtra("app_id", -1))
        assertEquals("GOG", intent.getStringExtra("game_source"))
        assertEquals("app.gamenative", intent.component?.packageName)
    }

    @Test
    fun dolphinGetsItsFileAndStartsAfresh() {
        val file = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGames/document/primary%3AGames%2FWW.rvz")
        val intent = roundTrip(GameFolders.intentFor(GameFolders.Player.DOLPHIN, "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.ui.main.TvMainActivity", file, null, null)!!)
        assertEquals(file.toString(), intent.getStringExtra("AutoStartFile"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertEquals("org.dolphinemu.dolphinemu.ui.main.TvMainActivity", intent.component?.className)
    }

    @Test
    fun gameHubGetsItsSteamIdAndAutoStart() {
        val intent = roundTrip(GameFolders.intentFor(GameFolders.Player.GAMEHUB, "gamehub.lite", "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity", null, "504230", "STEAM")!!)
        assertEquals("504230", intent.getStringExtra("steamAppId"))
        assertTrue(intent.getBooleanExtra("autoStartGame", false))
    }

    @Test
    fun edenAndPpssppGetTheFileAsData() {
        val file = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AGames/document/primary%3AGames%2Fg.nsp")
        val eden = roundTrip(GameFolders.intentFor(GameFolders.Player.EDEN, "dev.eden.eden_emulator", "org.yuzu.yuzu_emu.activities.EmulationActivity", file, null, null)!!)
        assertEquals(file, eden.data)
        assertEquals("android.nfc.action.TECH_DISCOVERED", eden.action)
        val psp = roundTrip(GameFolders.intentFor(GameFolders.Player.PPSSPP, "org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity", file, null, null)!!)
        assertEquals(file, psp.data)
    }
}
