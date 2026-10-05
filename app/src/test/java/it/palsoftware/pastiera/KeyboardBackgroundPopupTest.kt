package it.palsoftware.pastiera

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardBackgroundPopupTest {
    @Test
    fun popupsTakeThePicturesColourAndStayReadable() {
        val pictures = listOf(Color.rgb(200, 40, 40), Color.rgb(20, 60, 120), Color.rgb(240, 230, 200), Color.rgb(15, 15, 15))
        for (picture in pictures) for (text in listOf(Color.BLACK, Color.WHITE)) {
            val popup = KeyboardBackgroundImage.popupOver(picture, text)
            assertTrue(Color.alpha(popup) == 255)
            assertTrue(ColorUtils.calculateContrast(text, popup) >= 4.5)
        }
        // A red picture gives a reddish popup, not a flat grey
        val red = KeyboardBackgroundImage.popupOver(Color.rgb(200, 40, 40), Color.BLACK)
        assertTrue(Color.red(red) > Color.green(red) && Color.red(red) > Color.blue(red))
    }
}
