package it.palsoftware.pastiera.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoticonSentencesTest {
    @After
    fun tearDown() {
        EmoticonSentences.enabled = true
    }

    @Test
    fun emoticonsEndSentences() {
        listOf("Great :)", "ok ;-)", "haha :D", ":P", "sad :'(", "love you <3", "yay ^_^", "lol XD").forEach {
            assertTrue(it, EmoticonSentences.endsWithEmoticon(it))
        }
        listOf("(see above)", "chapter 8)", "http://x", "Note:D", "hello", "a :)b").forEach {
            assertFalse(it, EmoticonSentences.endsWithEmoticon(it))
        }
        EmoticonSentences.enabled = false
        assertFalse(EmoticonSentences.endsWithEmoticon("Great :)"))
    }
}
