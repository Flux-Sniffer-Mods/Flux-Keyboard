package it.palsoftware.pastiera.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactDetailsTest {
    @Test
    fun emailsAndPhoneNumbersAreRecognised() {
        listOf("sam@example.com", "first.last+tag@mail.co.uk").forEach { assertTrue(it, ContactDetails.isEmail(it)) }
        listOf("sam@", "@example.com", "sam@example", "sam example@x.com").forEach { assertFalse(it, ContactDetails.isEmail(it)) }
        listOf("07700 900123", "+44 7700 900123", "(020) 7946-0018", "5551234").forEach { assertTrue(it, ContactDetails.isPhone(it)) }
        listOf("12:30", "2026", "3.14", "123 456", "+44").forEach { assertFalse(it, ContactDetails.isPhone(it)) }
    }

    @Test
    fun onlyWhatWasTypedByHandIsLearned() {
        assertEquals("sam@example.com", ContactDetails.toLearn("sam@example.com", ContactDetails.Kind.EMAIL, 15))
        // Pasted or filled in: fewer characters typed than the address has
        assertNull(ContactDetails.toLearn("sam@example.com", ContactDetails.Kind.EMAIL, 3))
    }

    @Test
    fun inOrdinaryTextOnlyTheWordJustTypedCounts() {
        assertEquals("sam@example.com", ContactDetails.toLearn("Write to sam@example.com.", null, 40))
        assertEquals("+447700900123", ContactDetails.toLearn("Call +447700900123", null, 40))
        // A number with spaces in running text could be anything
        assertNull(ContactDetails.toLearn("Room 07700 900123", null, 40))
        assertNull(ContactDetails.toLearn("See you at 12:30", null, 40))
    }

    @Test
    fun anEmailFieldOnlyLearnsEmails() {
        assertNull(ContactDetails.toLearn("07700 900123", ContactDetails.Kind.EMAIL, 20))
        assertEquals("07700 900123", ContactDetails.toLearn("07700 900123", ContactDetails.Kind.PHONE, 20))
    }

    @Test
    fun savedDetailsAreMatchedAndNotSavedTwice() {
        val saved = listOf("Sam@Example.com", "07700 900123", "sally@example.org", "hello")
        assertEquals(listOf("Sam@Example.com", "sally@example.org"), ContactDetails.matching(saved, "sa", ContactDetails.Kind.EMAIL))
        assertEquals(listOf("sally@example.org"), ContactDetails.matching(saved, "sal", ContactDetails.Kind.EMAIL))
        assertEquals(listOf("07700 900123"), ContactDetails.matching(saved, "0770-09", ContactDetails.Kind.PHONE))
        assertTrue(ContactDetails.matching(saved, "07700900123", ContactDetails.Kind.PHONE).isEmpty())
        assertTrue(ContactDetails.alreadySaved(saved, "sam@example.com"))
        assertTrue(ContactDetails.alreadySaved(saved, "07700-900-123"))
        assertFalse(ContactDetails.alreadySaved(saved, "new@example.com"))
    }
}
