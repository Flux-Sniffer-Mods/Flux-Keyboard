package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperKeyboardColoursTest {
    // Stand-in palettes: palette number in the top byte's low bits, tone in the rest
    private fun tone(palette: Int) = { t: Int -> (0xFF shl 24) or (palette shl 16) or t }
    private val palette = WallpaperKeyboardColours.Palette(tone(1), tone(2), tone(3), tone(4), tone(5))
    private val theme = SettingsManager.KeyboardThemeSettings(
        background = 0x80123456.toInt(), divider = 0, normalKey = 0, specialKey = 0, textAndIcons = 0,
        ledInactive = 0, ledActive = 0, ledLocked = 0, accent = 0, keyCornerRadiusRatio = 0.3f, keyHeightScale = 1.4f
    )

    @Test
    fun darkUsesDeepNeutralsAndALightAccent() {
        val dark = WallpaperKeyboardColours.recolour(theme, palette, dark = true)
        assertEquals(tone(3)(200), dark.accent)
        assertEquals(tone(1)(800), dark.normalKey)
        assertEquals(tone(1)(50), dark.textAndIcons)
        assertEquals(tone(5)(200), dark.ledLocked)
    }

    @Test
    fun lightUsesPaleNeutralsAndADeepAccent() {
        val light = WallpaperKeyboardColours.recolour(theme, palette, dark = false)
        assertEquals(tone(3)(600), light.accent)
        assertEquals(tone(1)(900), light.textAndIcons)
    }

    @Test
    fun shapeAndSeeThroughBackgroundStay() {
        val dark = WallpaperKeyboardColours.recolour(theme, palette, dark = true)
        assertEquals(0.3f, dark.keyCornerRadiusRatio)
        assertEquals(1.4f, dark.keyHeightScale)
        assertEquals(0x80, dark.background ushr 24)
    }
}
