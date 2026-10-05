package it.palsoftware.pastiera.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EditHistoryTest {
    @Test
    fun diffFindsOnlyTheChangedMiddle() {
        assertEquals(6 to 0, EditHistory.diff("hello world", "hello "))
        assertEquals(6 to 5, EditHistory.diff("hello brave world", "hello world"))
        assertEquals(3 to 0, EditHistory.diff("aaa", "aaaa"))
        assertEquals(0 to 0, EditHistory.diff("", "typed"))
    }
}
