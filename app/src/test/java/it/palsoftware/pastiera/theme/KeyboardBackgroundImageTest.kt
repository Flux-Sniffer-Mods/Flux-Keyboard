package it.palsoftware.pastiera.theme

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.SettingsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardBackgroundImageTest {
    private val theme = SettingsManager.KeyboardThemeSettings(
        background = Color.DKGRAY, divider = Color.GRAY, normalKey = Color.RED, specialKey = Color.BLUE,
        textAndIcons = Color.GREEN, ledInactive = 0, ledActive = 0, ledLocked = 0, accent = Color.YELLOW
    )

    @Test
    fun brightPictureGetsDarkSeeThroughKeysAndDarkOrLightTextToMatch() {
        val t = KeyboardBackgroundImage.recolour(theme, luminance = 0.8, autoColours = true, keyOpacityPercent = 40)
        assertEquals(Color.TRANSPARENT, t.background)
        assertEquals(Color.argb(102, 0, 0, 0), t.normalKey)
        assertTrue(Color.alpha(t.specialKey) > Color.alpha(t.normalKey))
        assertEquals(Color.BLACK, t.textAndIcons)
        assertEquals(Color.YELLOW, t.accent)
    }

    @Test
    fun darkPictureGetsLightSeeThroughKeys() {
        val t = KeyboardBackgroundImage.recolour(theme, luminance = 0.02, autoColours = true, keyOpacityPercent = 25)
        assertEquals(Color.argb(63, 255, 255, 255), t.normalKey)
        assertEquals(Color.WHITE, t.textAndIcons)
    }

    @Test
    fun solidKeysFlipTheText() {
        // Fully opaque black keys over a bright picture need white text
        val t = KeyboardBackgroundImage.recolour(theme, luminance = 0.9, autoColours = true, keyOpacityPercent = 100)
        assertEquals(Color.WHITE, t.textAndIcons)
    }

    @Test
    fun withoutAutoColoursOnlyTheBackgroundClears() {
        val t = KeyboardBackgroundImage.recolour(theme, luminance = 0.9, autoColours = false, keyOpacityPercent = 40)
        assertEquals(theme.copy(background = Color.TRANSPARENT), t)
    }

    @Test
    fun framingMovesThePictureWithinWhatDoesNotFit() {
        val matrix = android.graphics.Matrix()
        val values = FloatArray(9)
        // A 200 x 100 picture over a 100 x 100 bar: covering needs scale 1, 100 px spare across
        KeyboardBackgroundImage.frame(matrix, 200, 100, 0f, 0f, 100f, 100f, KeyboardBackgroundImage.Framing(x = 0f, y = 1f))
        matrix.getValues(values)
        assertEquals(0f, values[android.graphics.Matrix.MTRANS_X], 0.01f)
        KeyboardBackgroundImage.frame(matrix, 200, 100, 0f, 0f, 100f, 100f, KeyboardBackgroundImage.Framing(x = 1f, y = 1f))
        matrix.getValues(values)
        assertEquals(-100f, values[android.graphics.Matrix.MTRANS_X], 0.01f)
        assertEquals(
            KeyboardBackgroundImage.Framing(0.25f, 0.5f, 2f),
            KeyboardBackgroundImage.Framing.decode(KeyboardBackgroundImage.Framing(0.25f, 0.5f, 2f).encode())
        )
        assertEquals(KeyboardBackgroundImage.Framing(), KeyboardBackgroundImage.Framing.decode("nonsense"))
    }

    @Test
    fun aTurnedPictureCoversTheBarTurned() {
        val matrix = android.graphics.Matrix()
        val framing = KeyboardBackgroundImage.Framing(x = 0f, y = 0f).rotated()
        assertEquals(1, framing.turns)
        assertEquals(framing, KeyboardBackgroundImage.Framing.decode(framing.encode()))
        // 200 x 100 turned is 100 x 200: over a 100 x 100 bar, scale 1
        KeyboardBackgroundImage.frame(matrix, 200, 100, 0f, 0f, 100f, 100f, framing)
        // The picture's bottom left corner comes to the top left, its top left to the top right
        val points = floatArrayOf(0f, 100f, 0f, 0f)
        matrix.mapPoints(points)
        assertEquals(0f, points[0], 0.01f)
        assertEquals(0f, points[1], 0.01f)
        assertEquals(100f, points[2], 0.01f)
        assertEquals(0f, points[3], 0.01f)
        assertEquals(0, framing.rotated().rotated().rotated().turns)
    }
}
