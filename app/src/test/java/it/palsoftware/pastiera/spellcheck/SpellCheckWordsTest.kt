package it.palsoftware.pastiera.spellcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpellCheckWordsTest {
    private fun wordsOf(text: String) = SpellCheckRules.words(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun apostrophesStayInsideWords() {
        assertEquals(listOf("just", "couldn't", "be", "bothered"), wordsOf("just couldn't be bothered"))
        assertEquals(listOf("It’s", "Sam's", "rock", "n", "roll"), wordsOf("It’s Sam's rock 'n' roll!"))
        assertEquals(listOf("well", "known", "café"), wordsOf("well-known café."))
    }

    @Test
    fun contractionsAndPossessivesComeFromTheirWord() {
        assertEquals("could", SpellCheckRules.contractionBase("couldn't"))
        assertEquals("it", SpellCheckRules.contractionBase("it’s"))
        assertEquals("Sam", SpellCheckRules.contractionBase("Sam's"))
        assertEquals("they", SpellCheckRules.contractionBase("they're"))
        assertNull(SpellCheckRules.contractionBase("o'clock"))
    }
}
