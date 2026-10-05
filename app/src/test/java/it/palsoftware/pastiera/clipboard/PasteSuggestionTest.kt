package it.palsoftware.pastiera.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PasteSuggestionTest {
    @Test
    fun shortTextIsShownWhole() {
        assertEquals("⎘ hello world", PasteSuggestion.label("  hello\n world "))
    }

    @Test
    fun longTextIsCutOnOneLine() {
        val label = PasteSuggestion.label("https://example.com/a/very/long/path/that/goes/on")
        assertEquals("⎘ https://example.com/a/v…", label)
    }

    @Test
    fun aLongLabelIsNeverCutInsideAnEmoji() {
        assertEquals("\u2398 " + "a".repeat(22) + "…", PasteSuggestion.label("a".repeat(22) + "😀😀"))
        assertEquals("\u2398 " + "a".repeat(19) + "🇬🇧…", PasteSuggestion.label("a".repeat(19) + "🇬🇧🇬🇧"))
        assertEquals("\u2398 " + "a".repeat(21) + "…", PasteSuggestion.label("a".repeat(21) + "🇬🇧🇬🇧"))
    }

    @Test
    fun thePasswordFieldLabelGivesNothingAway() {
        assertEquals("\u2398 \u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022", PasteSuggestion.MASKED_LABEL)
    }
}
