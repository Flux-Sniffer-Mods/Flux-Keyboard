package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import it.palsoftware.pastiera.core.IncognitoTyping
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FrequentWordLearnerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private fun prefs() = context.getSharedPreferences("frequent_words_test", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        IncognitoTyping.active = false
    }

    @Test
    fun aWordTypedThreeTimesIsLearned() {
        val learner = FrequentWordLearner(prefs())
        assertNull(learner.countUse("zorbly"))
        assertNull(learner.countUse("zorbly"))
        assertEquals("zorbly", learner.countUse("zorbly"))
        // Counting starts again afterwards
        assertNull(learner.countUse("zorbly"))
    }

    @Test
    fun countsAreKeptBetweenSessions() {
        FrequentWordLearner(prefs()).apply { countUse("Quibbet"); countUse("quibbet") }
        assertEquals("quibbet", FrequentWordLearner(prefs()).countUse("Quibbet"))
    }

    @Test
    fun aNameAlwaysCapitalisedStaysCapitalised() {
        val learner = FrequentWordLearner(prefs())
        repeat(2) { learner.countUse("Nikkita") }
        assertEquals("Nikkita", learner.countUse("Nikkita"))
    }

    @Test
    fun onlyRealWordsCount() {
        listOf("ok", "abc123", "hello!", "-dash", "a'").forEach { assertFalse(it, FrequentWordLearner.isLearnable(it)) }
        listOf("zorbly", "can't", "well-known", "caffè").forEach { assertTrue(it, FrequentWordLearner.isLearnable(it)) }
    }

    @Test
    fun incognitoRecordsNoRecents() {
        IncognitoTyping.active = true
        assertFalse(RecentEmojiManager.addRecentEmoji(context, "🦊"))
        IncognitoTyping.active = false
        assertTrue(RecentEmojiManager.addRecentEmoji(context, "🦊"))
    }
}
