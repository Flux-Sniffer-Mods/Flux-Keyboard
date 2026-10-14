package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchAreaTest {
    private fun game(pkg: String) = GameProfile(
        id = "g", name = "g", packages = setOf(pkg), leftHalf = TrackpadRole.BOTTOM_SCREEN, rightHalf = TrackpadRole.BOTTOM_SCREEN
    )

    @Test
    fun threeDsBottomScreenIsBelowTheTopOne() {
        // 1080 wide, 1296 tall: the 3DS's 400 x 480 at 2.7x, filling it
        val area = GameMode.touchArea(game("org.azahar_emu.azahar"), 1080f, 1296f)
        assertEquals(0.1f, area[0], 0.001f)
        assertEquals(0.5f, area[1], 0.001f)
        assertEquals(0.9f, area[2], 0.001f)
        assertEquals(1f, area[3], 0.001f)
    }

    @Test
    fun placedAreaWins() {
        val placed = game("me.magnum.melonds").copy(touchArea = listOf(0.1f, 0.2f, 0.3f, 0.4f))
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), GameMode.touchArea(placed, 1080f, 1240f))
    }

    @Test
    fun gameAppsPlayLandscapeAndTwoScreensPortrait() {
        assertEquals(ScreenMode.UPRIGHT, ScreenMode.defaultFor("org.azahar_emu.azahar"))
        assertEquals(ScreenMode.UPRIGHT, ScreenMode.defaultFor("me.magnum.melonds"))
        assertEquals(ScreenMode.SIDEWAYS, ScreenMode.defaultFor("com.retroarch"))
        assertEquals(ScreenMode.SIDEWAYS, ScreenMode.defaultFor("app.gamenative"))
        assertEquals(ScreenMode.SIDEWAYS, ScreenMode.defaultFor("gamehub.lite"))
        assertEquals(ScreenMode.SIDEWAYS, ScreenMode.defaultFor("info.cemu.cemu"))
        assertEquals(ScreenMode.SIDEWAYS, ScreenMode.defaultFor("com.flycast.emulator"))
        assertEquals(ScreenMode.APP, ScreenMode.defaultFor("com.example.app"))
    }
}
