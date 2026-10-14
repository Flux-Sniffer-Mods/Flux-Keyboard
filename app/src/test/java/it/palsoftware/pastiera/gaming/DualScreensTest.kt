package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualScreensTest {
    private val portrait = 1080 to 1150
    private val landscape = 1240 to 700

    @Test
    fun stackedScreensFitThePortraitWindowInEitherOrder() {
        for (layout in listOf(DualScreens.Layout.STACKED, DualScreens.Layout.STACKED_BOTTOM_FIRST)) {
            val v = DualScreens.citraValues(layout, portrait).mapValues { it.value.toInt() }
            assertEquals(1, v["portrait_layout_option"])
            for (screen in listOf("top", "bottom")) {
                assertTrue(v.getValue("custom_portrait_${screen}_x") + v.getValue("custom_portrait_${screen}_width") <= portrait.first)
                assertTrue(v.getValue("custom_portrait_${screen}_y") + v.getValue("custom_portrait_${screen}_height") <= portrait.second)
            }
            val topFirst = v.getValue("custom_portrait_top_y") < v.getValue("custom_portrait_bottom_y")
            assertEquals(layout == DualScreens.Layout.STACKED, topFirst)
        }
    }

    @Test
    fun theBottomScreenAloneFillsTheLandscapeWindow() {
        val v = DualScreens.citraValues(DualScreens.Layout.BOTTOM_ONLY, landscape).mapValues { it.value.toInt() }
        assertEquals(5, v["layout_option"])
        assertEquals(1, v["custom_top_width"])
        assertTrue(v.getValue("custom_bottom_height") > landscape.second * 0.9)
    }

    @Test
    fun theTouchAreaFollowsTheLayout() {
        val game = GameProfiles.newProfile("Pokémon X", setOf("org.azahar_emu.azahar")).copy(screens = DualScreens.Layout.STACKED_BOTTOM_FIRST.name)
        val area = DualScreens.bottomArea(game, portrait.first.toFloat(), portrait.second.toFloat())!!
        assertEquals(0f, area[1], 0.01f)
        assertNull(DualScreens.bottomArea(game.copy(screens = DualScreens.Layout.TOP_ONLY.name), 1080f, 1150f))
        assertNull(DualScreens.bottomArea(game.copy(screens = null), 1080f, 1150f))
    }

    @Test
    fun noodsLinesAreReplacedInPlace() {
        val ini = "screenPosition=0\nscreenArrangement=0\nscreenSizing=0\n"
        assertEquals("screenPosition=0\nscreenArrangement=3\nscreenSizing=0\n", DualScreens.withNoodsKey(ini, "screenArrangement", "3"))
        assertEquals("a=1\nscreenGap=2\n", DualScreens.withNoodsKey("a=1\n", "screenGap", "2"))
    }

    @Test
    fun onlyEmulatorsWithReachableSettingsOfferLayouts() {
        assertEquals(DualScreens.Layout.entries, DualScreens.layouts("org.azahar_emu.azahar"))
        assertTrue(DualScreens.layouts("me.magnum.melonds").isEmpty())
        assertTrue(DualScreens.Layout.BOTTOM_ONLY !in DualScreens.layouts("com.hydra.noods"))
    }
}
