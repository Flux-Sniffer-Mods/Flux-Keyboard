package it.palsoftware.pastiera.otp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OneTimeCodesTest {
    @Test
    fun findsCommonCodeMessages() {
        assertEquals("482913", OneTimeCodes.extract("Your verification code is 482913. It expires in 10 minutes."))
        assertEquals("123456", OneTimeCodes.extract("G-123456 is your Google verification code."))
        assertEquals("735104", OneTimeCodes.extract("Your login code: 735 104"))
        assertEquals("9921", OneTimeCodes.extract("Il tuo codice è 9921"))
        assertEquals("583012", OneTimeCodes.extract("Code 583012 – use it to sign in. © 2026 Example"))
    }

    @Test
    fun ignoresMessagesWithoutACode() {
        assertNull(OneTimeCodes.extract("Your order 55812 has shipped"))
        assertNull(OneTimeCodes.extract("Lunch at 12:30? Code review after"))
        assertNull(OneTimeCodes.extract("Your code review has 3 comments"))
    }

    @Test
    fun aCopyButtonNamesTheCode() {
        assertEquals("482913", OneTimeCodes.fromCopyAction("Copy 482913"))
        assertEquals("735104", OneTimeCodes.fromCopyAction("Copy code 735 104"))
        assertEquals(null, OneTimeCodes.fromCopyAction("Reply"))
        assertEquals(null, OneTimeCodes.fromCopyAction("Mark as read"))
    }

    @Test
    fun aThreadsNewestCodeWins() {
        assertEquals("662190", OneTimeCodes.extract("Your code is 104837. Your code is 662190."))
    }

    @Test
    fun theNumberNearestTheKeywordIsTheCode() {
        assertEquals("4417", OneTimeCodes.extract("Order 845921: your verification code is 4417"))
        assertEquals("4417", OneTimeCodes.extract("Your code is 4417. Reference 845921"))
        assertEquals("123456", OneTimeCodes.extract("G-123456 is your Google verification code. Account 99887766"))
    }

    @Test
    fun keywordsInsideOtherWordsDontCount() {
        assertNull(OneTimeCodes.extract("Your order 845921 is shipping today"))
        assertNull(OneTimeCodes.extract("New Pinterest pins: 25 ideas for 2026"))
        assertNull(OneTimeCodes.extract("We value your opinion: survey 482913"))
        assertNull(OneTimeCodes.extract("Barcode 501234 scanned"))
        assertNull(OneTimeCodes.extract("Meeting at 14:30 in room 2204. Security briefing."))
        assertEquals("7294", OneTimeCodes.extract("Sign in to Microsoft: security code 7294."))
        assertEquals("4417", OneTimeCodes.extract("Your PIN is 4417"))
        assertEquals("735104", OneTimeCodes.extract("OTP: 735104"))
        assertEquals("190288", OneTimeCodes.extract("Twój kod weryfikacyjny: 190288"))
        assertEquals("583012", OneTimeCodes.extract("Use 583012 for 2FA"))
    }

    @Test
    fun skipsAmountsAndPrefersSixDigits() {
        assertEquals("402817", OneTimeCodes.extract("Payment of $1500 needs code 402817"))
    }

    @Test
    fun aTypedCodeIsntOfferedAgainWhenItsNotificationIsReposted() {
        OneTimeCodes.consume()
        OneTimeCodes.offer("482913", now = 1_000L)
        assertEquals("482913", OneTimeCodes.current(now = 2_000L))
        OneTimeCodes.consume(now = 3_000L)
        OneTimeCodes.offer("482913", now = 4_000L)
        assertNull(OneTimeCodes.current(now = 5_000L))
        // A new code still comes through
        OneTimeCodes.offer("735104", now = 6_000L)
        assertEquals("735104", OneTimeCodes.current(now = 7_000L))
        OneTimeCodes.consume()
    }

    @Test
    fun anUpdatedNotificationKeepsTheCodesFirstArrival() {
        OneTimeCodes.consume()
        OneTimeCodes.offer("583012", now = 10_000L)
        OneTimeCodes.offer("583012", now = 10_000L + OneTimeCodes.LIFETIME_MS - 1)
        assertNull(OneTimeCodes.current(now = 10_000L + OneTimeCodes.LIFETIME_MS + 1))
        OneTimeCodes.consume()
    }
}
