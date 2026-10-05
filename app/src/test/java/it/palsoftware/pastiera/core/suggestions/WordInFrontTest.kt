package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordInFrontTest {
    @Test
    fun aNewWordKeepsTheWordAfterAndTheJoinedOneReplacesIt() {
        WordInFront.trackedWord = "some"
        assertTrue(WordInFront.keepsWordAfter("some", "some", "thing"))
        assertFalse(WordInFront.keepsWordAfter("something", "some", "thing"))
        // Suggestions made for the whole word (the cursor inside it) replace all of it
        WordInFront.trackedWord = "hello"
        assertFalse(WordInFront.keepsWordAfter("help", "hel", "lo"))
        assertEquals("thing", WordInFront.followingWord("thing else"))
    }
}
