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
    fun theKeysAreTheSticksAndTheTrackpadIsLeftAlone() {
        val launcher = GameProfiles.newProfile("Stray", setOf("app.gamenative"))
        assertEquals(TrackpadRole.NONE, launcher.leftHalf)
        assertEquals(TrackpadRole.NONE, launcher.rightHalf)
        val emulator = GameProfiles.newProfile("Wind Waker", setOf("org.dolphinemu.dolphinemu"))
        assertEquals(TrackpadRole.NONE, emulator.leftHalf)
        assertEquals(ScreenMode.SIDEWAYS, emulator.screen)
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

    @Test
    fun gamesFoundJoinTheListOnceAndWaitToBeSetUp() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val game = GameLibrary.Game("Wind Waker", "org.dolphinemu.dolphinemu", launch = "intent:#Intent;S.AutoStartFile=/x/zelda.rvz;end")
        GameProfiles.addDetected(context, listOf(game))
        GameProfiles.addDetected(context, listOf(game))
        val found = GameProfiles.all(context).filter { it.launch == game.launch }
        assertEquals(1, found.size)
        assertEquals(true, found.single().pending)
        // Not played with until set up
        assertEquals(true, GameProfiles.forPackage(context, "org.dolphinemu.dolphinemu").none { it.launch == game.launch })
        assertEquals(true, GameProfile.fromJson(found.single().toJson())?.pending)
        assertEquals(false, GameProfiles.confirm(context, found.single()).pending)
    }

    @Test
    fun aGameCanDrawSmallerItsShapeKept() {
        // Kept upright on a 1240 x 1080-ish landscape shape, drawn 720 rows high
        assertEquals(1240 to 720, GameMode.scaled(1860 to 1080, 720))
        assertEquals(1860 to 1080, GameMode.scaled(1860 to 1080, null))
        // Never larger than the screen
        assertEquals(1860 to 1080, GameMode.scaled(1860 to 1080, 1440))
    }

    @Test
    fun aGameOutOfTheFoldersOrUninstalledIsGone() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val game = GameProfiles.newProfile("Celeste", setOf(context.packageName)).copy(launch = "intent:#Intent;S.x=1;end")
        // Still in the folders, or never found there (added by hand): here
        assertEquals(false, GameProfiles.gone(context, game, setOf(game.launch!!), setOf(game.launch!!)))
        assertEquals(false, GameProfiles.gone(context, game, emptySet(), emptySet()))
        // Found there before, not now: gone
        assertEquals(true, GameProfiles.gone(context, game, emptySet(), setOf(game.launch!!)))
        // Its app uninstalled: gone
        assertEquals(true, GameProfiles.gone(context, game.copy(packages = setOf("com.example.uninstalled")), emptySet(), emptySet()))
    }
}
