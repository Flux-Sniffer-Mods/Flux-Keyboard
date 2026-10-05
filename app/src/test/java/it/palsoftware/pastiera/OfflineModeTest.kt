package it.palsoftware.pastiera

import android.view.KeyEvent
import it.palsoftware.pastiera.data.gif.KlipyGifs
import it.palsoftware.pastiera.data.layout.LayoutRepositoryManager
import it.palsoftware.pastiera.dictionaries.DictionaryRepositoryManager
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineModeTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun backOnline() {
        SettingsManager.setOfflineMode(context, false)
    }

    @Test
    fun offlineModeHidesGifsAndStopsEveryRequest() {
        SettingsManager.setGifsEnabled(context, true)
        assertTrue(SettingsManager.gifsAvailable(context))

        SettingsManager.setOfflineMode(context, true)

        assertTrue(OfflineMode.enabled)
        assertFalse(SettingsManager.gifsAvailable(context))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, SettingsManager.activeEmojiLayerGifKey(context))
        assertFalse(shouldUseGithubUpdateChecks(context))
        runBlocking {
            assertTrue(DictionaryRepositoryManager.fetchManifest().isFailure)
            assertTrue(LayoutRepositoryManager.fetchManifest().isFailure)
            assertTrue(runCatching { KlipyGifs.find(context, "key", "cat") }.isFailure)
        }
        // The switch itself is untouched: GIFs come back with the network
        assertTrue(SettingsManager.getGifsEnabled(context))
    }

    @Test
    fun turningItOffRestoresGifs() {
        SettingsManager.setGifsEnabled(context, true)
        SettingsManager.setOfflineMode(context, true)

        SettingsManager.setOfflineMode(context, false)

        assertFalse(OfflineMode.enabled)
        assertTrue(SettingsManager.gifsAvailable(context))
    }
}
