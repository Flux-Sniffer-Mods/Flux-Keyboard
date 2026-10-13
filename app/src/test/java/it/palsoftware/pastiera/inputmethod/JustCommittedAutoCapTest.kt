package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JustCommittedAutoCapTest {
    @Test
    fun spaceNotAppliedYetStillCounts() {
        val before = AutoCapitalizeHelper.withCommitted("Hello world.", " ")
        assertEquals("Hello world. ", before.toString())
        assertTrue(AutoCapitalizeHelper.hasSentenceEndingPunctuation(before))
    }

    @Test
    fun alreadyAppliedIsLeftAlone() {
        assertEquals("Hi. ", AutoCapitalizeHelper.withCommitted("Hi. ", " ").toString())
        assertEquals("Hi. ", AutoCapitalizeHelper.withCommitted("Hi. ", ". ").toString())
        // Half applied: the period there, the space not yet
        assertEquals("Hi. ", AutoCapitalizeHelper.withCommitted("Hi.", ". ").toString())
        assertEquals("Hi. ", AutoCapitalizeHelper.withCommitted("Hi", ". ").toString())
    }
}
