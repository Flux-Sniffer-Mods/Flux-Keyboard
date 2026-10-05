package it.palsoftware.pastiera.data.emoji

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiSearchFallbackTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private val doubleExclamation = "\u203C\uFE0F"
    private val flagItaly = "\uD83C\uDDEE\uD83C\uDDF9"
    private val flagEngland =
        "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
    private val womanRunning = "\uD83C\uDFC3\u200D\u2640\uFE0F"

    // Search results are limited to emoji the device can draw; widen so the test
    // doesn't depend on Robolectric's glyph support.
    private fun searchAll(query: String): List<String> = runBlocking {
        val index = EmojiSearchRepository.getSearchIndex(context)
        EmojiSearchRepository.search(index, query) { true }.map { it.entry.base }
    }

    @Test
    fun textStyleSymbolIsFoundByItsUnicodeName() {
        assertTrue(doubleExclamation in searchAll("double exclamation"))
    }

    @Test
    fun countryFlagIsFoundByCountryName() {
        assertTrue(flagItaly in searchAll("italy"))
    }

    @Test
    fun subdivisionFlagIsFoundByName() {
        assertTrue(flagEngland in searchAll("england"))
    }

    @Test
    fun zwjSequenceIsFoundByItsPartsNames() {
        assertTrue(womanRunning in searchAll("running female"))
    }

    @Test
    fun fallbackTerms_useMetadataOfPartsWhenAvailable() {
        val metadata = mapOf(
            EmojiSearchRepository.metadataKey("\uD83C\uDFC3") to listOf(
                EmojiSearchRepository.MetadataRecord("person running", listOf("marathon")) to true
            )
        )
        val terms = EmojiSearchRepository.fallbackTerms(womanRunning, metadata, listOf("en"))
            .map { it.normalizedText }
        assertTrue("person running" in terms)
        assertTrue("marathon" in terms)
        assertTrue("female sign" in terms)
        assertTrue("person running female sign" in terms)
    }

    @Test
    fun metadataKey_ignoresPresentationSelector() {
        assertTrue(EmojiSearchRepository.metadataKey(doubleExclamation) == "\u203C")
    }
}
