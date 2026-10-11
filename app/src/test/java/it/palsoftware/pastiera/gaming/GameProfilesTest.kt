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
    fun keysPlacedOnScreenAreKept() {
        val profile = GameProfiles.newProfile("Touch game", setOf("com.example.game")).copy(
            taps = mapOf(android.view.KeyEvent.KEYCODE_SPACE to (0.8f to 0.7f))
        )
        assertEquals(profile.taps, GameProfile.fromJson(profile.toJson())?.taps)
    }

    @Test
    fun theRightStickOnKeysIsKept() {
        val profile = GameProfiles.newProfile("Game", setOf("com.example.game")).copy(rightStickKeys = true)
        assertEquals(true, GameProfile.fromJson(profile.toJson())?.rightStickKeys)
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
    fun aControllerProfilePlaysTheSticksAndIsKept() {
        val profile = GameProfiles.newProfile("Wind Waker", setOf("org.dolphinemu.dolphinemu"), controller = true)
        assertEquals(TrackpadRole.LEFT_STICK, profile.leftHalf)
        assertEquals(true, GameProfile.fromJson(profile.toJson())?.controller)
    }

    @Test
    fun aGamePlaysWithItsAppsSettingsAndDolphinsByController() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val apps = GameProfiles.ensureApp(context, "org.dolphinemu.dolphinemu", "Dolphin")
        assertEquals(listOf(GameProfiles.VARIANT_GAMECUBE, GameProfiles.VARIANT_WII), apps.map { it.variant })
        // Made once: asked again, the same two
        assertEquals(apps.map { it.id }, GameProfiles.ensureApp(context, "org.dolphinemu.dolphinemu", "Dolphin").map { it.id })
        fun game(path: String) = GameProfiles.newProfile("Game", setOf("org.dolphinemu.dolphinemu")).copy(
            launch = android.content.Intent().putExtra("AutoStartFile", path).toUri(android.content.Intent.URI_INTENT_SCHEME), own = false
        )
        assertEquals(GameProfiles.VARIANT_WII, GameProfiles.effective(context, game("/storage/emulated/0/ROMs/wii/Galaxy.rvz")).variant)
        assertEquals(GameProfiles.VARIANT_GAMECUBE, GameProfiles.effective(context, game("/storage/emulated/0/ROMs/gc/Zelda.gcm")).variant)
        // A game with settings of its own keeps them
        val own = game("/storage/emulated/0/ROMs/wii/Galaxy.rvz").copy(own = true)
        assertEquals(own, GameProfiles.effective(context, own))
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
