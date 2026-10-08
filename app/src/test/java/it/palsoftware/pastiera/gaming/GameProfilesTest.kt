package it.palsoftware.pastiera.gaming

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameProfilesTest {
    @Test
    fun aProfileSurvivesSavingAndLoading() {
        val profile = GameProfiles.newProfile("Hollow Knight", GameStyle.GAMEPAD, setOf("app.gamenative"))
        assertEquals(profile, GameProfile.fromJson(profile.toJson()))
    }

    @Test
    fun gamepadDefaultsPutXboxButtonsInTheirPlacesOnOKPL() {
        val keys = GameProfiles.gamepadDefaults()
        assertEquals(GameAction.BUTTON_Y, keys[KeyEvent.KEYCODE_O])
        assertEquals(GameAction.BUTTON_X, keys[KeyEvent.KEYCODE_K])
        assertEquals(GameAction.BUTTON_B, keys[KeyEvent.KEYCODE_P])
        assertEquals(GameAction.BUTTON_A, keys[KeyEvent.KEYCODE_L])
        assertEquals(GameAction.DPAD_UP, keys[KeyEvent.KEYCODE_W])
    }

    @Test
    fun theDzxcLayoutMovesOnDzxcWithTheWholeNumberRow() {
        val keys = GameProfiles.newProfile("MMO", GameStyle.PC_DZXC, emptySet()).keys
        assertEquals(GameAction.KEY_W, keys[KeyEvent.KEYCODE_D])
        assertEquals(GameAction.KEY_A, keys[KeyEvent.KEYCODE_Z])
        assertEquals(GameAction.KEY_S, keys[KeyEvent.KEYCODE_X])
        assertEquals(GameAction.KEY_D, keys[KeyEvent.KEYCODE_C])
        assertEquals(GameAction.KEY_2, keys[KeyEvent.KEYCODE_W])
    }

    @Test
    fun gamesAreReadFromTheShortcutList() {
        val dump = """
            ShortcutInfo {id=1, flags=0x2, packageName=app.gamenative, activity=x, shortLabel=Celeste, resId=0}
            ShortcutInfo {id=2, flags=0x2, packageName=other.app, activity=x, shortLabel=Nope, resId=0}
            ShortcutInfo {id=3, flags=0x2, packageName=app.gamenative, activity=x, shortLabel=Hades, resId=0}
        """.trimIndent()
        assertEquals(listOf("Celeste", "Hades"), GameLibrary.parseGames(dump, "app.gamenative"))
    }
}
