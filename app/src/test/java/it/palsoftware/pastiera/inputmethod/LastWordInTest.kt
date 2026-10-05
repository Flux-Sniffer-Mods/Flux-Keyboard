package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastWordInTest {
    @Test
    fun takesTheWordBeforeTheCursorSkippingSpacesAndPunctuation() {
        assertEquals("Titan", lastWordIn("I love my Titan"))
        assertEquals("Titan", lastWordIn("I love my Titan, "))
        assertEquals("don't", lastWordIn("I don't"))
        assertEquals("e-mail", lastWordIn("send an e-mail."))
        assertEquals("Flux2", lastWordIn("try Flux2 "))
    }

    @Test
    fun noWordMeansNull() {
        assertNull(lastWordIn(""))
        assertNull(lastWordIn("   "))
        assertNull(lastWordIn("42 "))
        assertNull(lastWordIn("?!"))
    }
}
