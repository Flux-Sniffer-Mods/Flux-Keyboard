package it.palsoftware.pastiera.data.emoji

import it.palsoftware.pastiera.inputmethod.suggestions.EmojiSuggestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class EmojiSuggestionTest {

    private val context get() = RuntimeEnvironment.getApplication()
    // Robolectric has no emoji font, so count every emoji as drawable
    private val all: (String) -> Boolean = { true }
    private fun index() = runBlocking { EmojiSearchRepository.getSearchIndex(context) }

    @Test
    fun nameMatchComesFirst() {
        val index = index()
        assertEquals("🍕", EmojiSearchRepository.suggestionFor(index, "pizza", all))
        assertEquals("🚀", EmojiSearchRepository.suggestionFor(index, "Rocket", all))
        assertNotNull(EmojiSearchRepository.cachedSearchIndex(context))
    }

    @Test
    fun keywordsGiveTheFirstEmojiInPickerOrder() {
        val happy = EmojiSearchRepository.suggestionFor(index(), "happy", all)
        assertNotNull(happy)
        assertTrue(EmojiSuggestion.isEmoji(happy!!))
    }

    @Test
    fun genericAndShortWordsGetNothing() {
        val index = index()
        listOf("time", "up", "button", "the", "a", "zzzqx").forEach {
            assertNull(it, EmojiSearchRepository.suggestionFor(index, it, all))
        }
    }

    @Test
    fun emojiAreToldApartFromWords() {
        assertTrue(EmojiSuggestion.isEmoji("😊"))
        assertTrue(EmojiSuggestion.isEmoji("❤️"))
        assertFalse(EmojiSuggestion.isEmoji("pizza"))
        assertFalse(EmojiSuggestion.isEmoji("1️⃣"))
        assertFalse(EmojiSuggestion.isEmoji("—"))
    }
}
