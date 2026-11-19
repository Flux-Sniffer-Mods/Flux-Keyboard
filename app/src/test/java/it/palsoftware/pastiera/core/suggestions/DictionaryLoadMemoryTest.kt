package it.palsoftware.pastiera.core.suggestions

import android.app.Application
import android.content.res.AssetManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DictionaryLoadMemoryTest {

    @Test
    fun outOfMemoryLeavesDictionaryUnavailableWithoutRetrying() {
        val path = "common/dictionaries_serialized/en_base.dict"
        val assets = mock(AssetManager::class.java)
        whenever(assets.open(path)).thenThrow(OutOfMemoryError("Simulated dictionary load failure"))
        val repository = AndroidDictionaryRepository(
            context = RuntimeEnvironment.getApplication(),
            assets = assets,
            userDictionaryStore = mock(UserDictionaryStore::class.java),
            baseLocale = Locale.ENGLISH
        )

        runBlocking(Dispatchers.IO) {
            repository.loadIfNeeded()
            repository.loadIfNeeded()
        }

        assertFalse(repository.isReady)
        verify(assets).open(path)
    }
}
