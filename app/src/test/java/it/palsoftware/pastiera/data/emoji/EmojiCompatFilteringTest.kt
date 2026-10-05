package it.palsoftware.pastiera.data.emoji

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.emoji2.text.EmojiCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiCompatFilteringTest {

    private val context get() = RuntimeEnvironment.getApplication()

    // Emoji 17.0, not drawable by any shipping Android 16 system font
    private val distortedFace = "\uD83E\uDEEA"

    @Test
    fun editorMetadataVersion_readFromEmojiCompatExtras() {
        assertNull(EmojiCompatSupport.editorMetadataVersion(null))
        assertNull(EmojiCompatSupport.editorMetadataVersion(EditorInfo()))
        val info = EditorInfo().apply {
            extras = Bundle().apply { putInt(EmojiCompat.EDITOR_INFO_METAVERSION_KEY, 7) }
        }
        assertEquals(7, EmojiCompatSupport.editorMetadataVersion(info))
    }

    @Test
    fun onStartInput_bumpsGenerationOnlyWhenFieldSupportChanges() {
        EmojiCompatSupport.onStartInput(EditorInfo())
        val plain = EmojiCompatSupport.generation
        EmojiCompatSupport.onStartInput(EditorInfo())
        assertEquals(plain, EmojiCompatSupport.generation)

        EmojiCompatSupport.onStartInput(EditorInfo().apply {
            extras = Bundle().apply { putInt(EmojiCompat.EDITOR_INFO_METAVERSION_KEY, 7) }
        })
        assertNotEquals(plain, EmojiCompatSupport.generation)
        EmojiCompatSupport.onStartInput(EditorInfo())
    }

    @Test
    fun noExtraPredicate_keepsSystemOnlyBehaviour() = runBlocking {
        val system = EmojiRepository.getEmojiCategories(context)
        val withNull = EmojiRepository.getEmojiCategories(context, null)
        val withNothingExtra = EmojiRepository.getEmojiCategories(context) { false }
        assertEquals(system, withNull)
        assertEquals(system, withNothingExtra)
    }

    @Test
    fun extraPredicate_addsEmojiTheSystemFontLacks() = runBlocking {
        val all = EmojiRepository.getAllEmojiCategories(context)
        assertTrue(all.any { category -> category.emojis.any { it.base == distortedFace } })

        val widened = EmojiRepository.getEmojiCategories(context) { it == distortedFace }
        assertTrue(widened.any { category -> category.emojis.any { it.base == distortedFace } })

        val everything = EmojiRepository.getEmojiCategories(context) { true }
        assertEquals(all, everything)
    }

    @Test
    fun filterEntry_dropsDisallowedBaseAndVariants() {
        val entry = EmojiRepository.EmojiEntry(base = "A", variants = listOf("B", "C"))
        assertNull(EmojiRepository.filterEntry(entry) { it != "A" })
        assertEquals(listOf("C"), EmojiRepository.filterEntry(entry) { it != "B" }?.variants)
    }

    @Test
    fun search_findsCompatOnlyEmojiOnlyWhenTheFieldSupportsIt() = runBlocking {
        val index = EmojiSearchRepository.getSearchIndex(context)
        val widened = EmojiSearchRepository.search(index, "distorted face") { it == distortedFace }
        assertTrue(widened.any { it.entry.base == distortedFace })

        if (!EmojiRepository.isSystemAvailable(distortedFace)) {
            val systemOnly = EmojiSearchRepository.search(index, "distorted face")
            assertFalse(systemOnly.any { it.entry.base == distortedFace })
        }
    }
}
