package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InlineAutofillTest {
    @Test
    fun requestAsksForStyledChipsThatFitTheBar() {
        val request = InlineAutofill.request(RuntimeEnvironment.getApplication())
        assertEquals(6, request.maxSuggestionCount)
        val spec = request.inlinePresentationSpecs.single()
        assertTrue(spec.minSize.height <= spec.maxSize.height)
        assertTrue(spec.minSize.width < spec.maxSize.width)
        // Styled for the androidx inline UI, which autofill services render
        assertTrue(!spec.style.isEmpty)
    }
}
