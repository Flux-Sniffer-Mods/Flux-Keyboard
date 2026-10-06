package it.palsoftware.pastiera.spellcheck

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellCheckRulesTest {
    @Test
    fun ordinaryWordsAreChecked() {
        listOf("hello", "Helo", "don't", "caffè", "end.").forEach { assertTrue(it, SpellCheckRules.shouldCheck(it)) }
    }

    @Test
    fun codesLinksAndNamesAreLeftAlone() {
        listOf("a", "B2B", "NASA", "@pastiera", "#tbt", "example.com", "http://x", "snake_case", "42", "--")
            .forEach { assertFalse(it, SpellCheckRules.shouldCheck(it)) }
    }

    @Test
    fun apostropheWordsAreCheckedAsWhatTheyreMadeFrom() {
        fun forms(word: String) = SpellCheckRules.apostropheForms(word)
        assertTrue("will" in forms("won't"))
        assertTrue("will" in forms("Won’t"))
        assertTrue("can" in forms("can't"))
        assertTrue("could" in forms("couldn't"))
        assertTrue("homme" in forms("l'homme"))
        assertTrue("anno" in forms("dell'anno"))
        assertFalse("re" in forms("thye're"))
    }

    @Test
    fun everyBundledDictionaryIsALanguage() {
        val bundled = java.io.File("src/main/assets/common/dictionaries_serialized").list().orEmpty()
            .map { it.substringBefore('_') }.toSet()
        // English is built in; the other languages' dictionaries download on first use
        org.junit.Assert.assertEquals(setOf("en"), bundled)
        assertTrue(SpellCheckRules.LANGUAGES.containsAll(bundled))
    }
}
