package it.palsoftware.pastiera.theme

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import it.palsoftware.pastiera.SettingsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KeyboardBackgroundImageSaveTest {
    @Test
    fun choosingAPictureSavesItAndThemesOverIt() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "photo.png")
        val photo = Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(240, 230, 200)) }
        source.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue(KeyboardBackgroundImage.save(context, Uri.fromFile(source)))
        assertTrue(KeyboardBackgroundImage.exists(context))
        val theme = SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE)
        assertEquals(Color.TRANSPARENT, theme.background)
        SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE)
        KeyboardBackgroundImage.remove(context)
    }
}
