package it.palsoftware.pastiera.inputmethod.expansion

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetPlaceholdersTest {
    private val now = Calendar.getInstance(TimeZone.getDefault()).apply {
        clear(); set(2026, Calendar.SEPTEMBER, 26, 9, 5)
    }.time

    @Test
    fun datesAndClipboardFillIn() {
        assertEquals(
            "Sent 2026-09-26 (Saturday): link",
            SnippetPlaceholders.fill("Sent {isodate} ({day}): {clipboard}", now, Locale.UK) { "link" }
        )
        // The phone's own date format (month names differ between Android versions)
        val date = SnippetPlaceholders.fill("{date}", now, Locale.UK)
        assertTrue(date, date.startsWith("26 ") && date.endsWith(" 2026"))
    }

    @Test
    fun otherBracesAndPlainTextStay() {
        assertEquals("{name} and {}", SnippetPlaceholders.fill("{name} and {}", now, Locale.UK))
        assertEquals("", SnippetPlaceholders.fill("{clipboard}", now, Locale.UK) { null })
    }
}
