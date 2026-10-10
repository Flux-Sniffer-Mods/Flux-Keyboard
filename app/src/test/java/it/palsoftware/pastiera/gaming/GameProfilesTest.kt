package it.palsoftware.pastiera.gaming

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
        val profile = GameProfiles.newProfile("Hollow Knight", setOf("app.gamenative"))
        assertEquals(profile, GameProfile.fromJson(profile.toJson()))
    }

    @Test
    fun theTrackpadStartsAsTheGamesSticks() {
        // Launchers' games: their on-screen sticks; emulators: direction keys they map themselves
        val launcher = GameProfiles.newProfile("Stray", setOf("app.gamenative"))
        assertEquals(TrackpadRole.LEFT_STICK, launcher.leftHalf)
        assertEquals(TrackpadRole.RIGHT_STICK, launcher.rightHalf)
        val emulator = GameProfiles.newProfile("Wind Waker", setOf("org.dolphinemu.dolphinemu"))
        assertEquals(TrackpadRole.WASD_KEYS, emulator.leftHalf)
        assertEquals(TrackpadRole.ARROW_KEYS, emulator.rightHalf)
    }

    @Test
    fun anOldProfileWithKeysStillLoads() {
        val old = org.json.JSONObject("""{"id":"x","name":"Old","style":"GAMEPAD","packages":["a.b"],"keys":{"29":"BUTTON_A"},"left":"LEFT_STICK","right":"MOUSE","nativeKeys":true}""")
        val profile = GameProfile.fromJson(old)
        assertEquals("Old", profile?.name)
        assertEquals(TrackpadRole.MOUSE, profile?.rightHalf)
    }

    @Test
    fun gamesAreReadFromTheShortcutList() {
        val dump = """
            ShortcutInfo {id=game_570, flags=0x2, packageName=app.gamenative, activity=ComponentInfo{app.gamenative/app.gamenative.MainActivity}, shortLabel=Celeste, resId=0}
            ShortcutInfo {id=2, flags=0x2, packageName=other.app, activity=x, shortLabel=Nope, resId=0}
            ShortcutInfo {
                packageName=app.gamenative.extra
                shortLabel=Nope either
            }
            ShortcutInfo {
                id=game_1145360
                flags=0x2
                packageName=app.gamenative
                activity=ComponentInfo{app.gamenative/app.gamenative.MainActivity}
                shortLabel=Hades
            }
        """.trimIndent()
        val games = GameLibrary.parseGames(dump, "app.gamenative")
        assertEquals(listOf("Celeste", "Hades"), games.map { it.name })
        assertEquals(listOf(570, 1145360), games.map { it.gameId })
    }
}
