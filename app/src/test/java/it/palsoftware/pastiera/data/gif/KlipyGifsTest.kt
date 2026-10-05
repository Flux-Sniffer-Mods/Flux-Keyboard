package it.palsoftware.pastiera.data.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import it.palsoftware.pastiera.SettingsManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KlipyGifsTest {

    private val sample = """
        {
          "results": [
            {
              "id": "abc123",
              "content_description": "Cat waving",
              "media_formats": {
                "gif": { "url": "https://static.klipy.com/full.gif", "dims": [480, 360] },
                "tinygif": { "url": "https://static.klipy.com/tiny.gif", "dims": [220, 165] }
              }
            },
            {
              "id": "no-https",
              "media_formats": { "gif": { "url": "http://insecure.example/x.gif" } }
            },
            {
              "id": "only-gif",
              "content_description": "",
              "media_formats": { "gif": { "url": "https://static.klipy.com/only.gif", "dims": [300, 200] } }
            }
          ],
          "next": "30"
        }
    """.trimIndent()

    @Test
    fun parsesTenorV2ResultsAndSkipsInsecureOnes() {
        val gifs = KlipyGifs.parse(sample)

        assertEquals(listOf("abc123", "only-gif"), gifs.map { it.id })
        val cat = gifs.first()
        assertEquals("Cat waving", cat.description)
        assertEquals("https://static.klipy.com/tiny.gif", cat.previewUrl)
        assertEquals("https://static.klipy.com/full.gif", cat.gifUrl)
        assertEquals(220, cat.previewWidth)
        assertEquals(165, cat.previewHeight)
        // No preview format: the full GIF doubles as the preview
        assertEquals("https://static.klipy.com/only.gif", gifs[1].previewUrl)
    }

    @Test
    fun emptyOrUnexpectedResponsesGiveNoResults() {
        assertTrue(KlipyGifs.parse("""{"results": []}""").isEmpty())
        assertTrue(KlipyGifs.parse("""{"error": "bad key"}""").isEmpty())
    }

    @Test
    fun blankQueryAsksForFeaturedAndTextSearches() {
        val featured = KlipyGifs.requestUrl("KEY", "  ", Locale.UK)
        assertEquals("/v2/featured", featured.encodedPath)
        assertNull(featured.queryParameter("q"))
        assertEquals("KEY", featured.queryParameter("key"))
        assertEquals("tinygif,gif", featured.queryParameter("media_filter"))
        assertEquals("en_GB", featured.queryParameter("locale"))
        assertEquals("GB", featured.queryParameter("country"))

        val search = KlipyGifs.requestUrl(" KEY ", " happy cat ", Locale.UK)
        assertEquals("api.klipy.com", search.host)
        assertEquals("/v2/search", search.encodedPath)
        assertEquals("happy cat", search.queryParameter("q"))
        assertEquals("KEY", search.queryParameter("key"))
    }

    @Test
    fun onlyFieldsTakingGifContentGetTheFile() {
        assertTrue(KlipyGifs.editorAcceptsGif(arrayOf("image/gif")))
        assertTrue(KlipyGifs.editorAcceptsGif(arrayOf("image/png", "image/*")))
        assertFalse(KlipyGifs.editorAcceptsGif(arrayOf("image/png")))
        assertFalse(KlipyGifs.editorAcceptsGif(emptyArray()))
    }

    @Test
    fun ownKeyReplacesTheBuiltInOneAndIsAllTheFieldShows() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("", SettingsManager.getUserKlipyApiKey(context))

        SettingsManager.setKlipyApiKey(context, "  mine  ")

        assertEquals("mine", SettingsManager.getUserKlipyApiKey(context))
        assertEquals("mine", SettingsManager.getKlipyApiKey(context))
    }

    @Test
    fun parsesKlipysOwnResponsesAndSkipsAds() {
        val json = """
            {"result": true, "data": {"data": [
              {"id": 101, "slug": "happy-cat", "title": "Happy cat", "type": "gif",
               "file": {
                 "hd": {"gif": {"url": "https://static.klipy.com/hd.gif", "width": 498, "height": 373}},
                 "md": {"gif": {"url": "https://static.klipy.com/md.gif", "width": 320, "height": 240}},
                 "xs": {"gif": {"url": "https://static.klipy.com/xs.gif", "width": 90, "height": 68}}
               }},
              {"type": "ad", "id": 5},
              {"id": 102, "title": "Small only",
               "files": {"sm": {"gif": {"url": "https://static.klipy.com/sm.gif", "width": 200, "height": 150}}}}
            ], "current_page": 1, "per_page": 24, "has_next": true}}
        """.trimIndent()

        val gifs = KlipyGifs.parseNative(json)

        assertEquals(listOf("101", "102"), gifs.map { it.id })
        assertEquals("https://static.klipy.com/md.gif", gifs[0].gifUrl)
        assertEquals("https://static.klipy.com/xs.gif", gifs[0].previewUrl)
        assertEquals(90, gifs[0].previewWidth)
        assertEquals("https://static.klipy.com/sm.gif", gifs[1].gifUrl)
        assertEquals("https://static.klipy.com/sm.gif", gifs[1].previewUrl)
    }

    @Test
    fun klipysOwnApiPutsTheKeyInThePath() {
        val search = KlipyGifs.nativeUrl("KEY", "happy cat", "install-1", Locale.UK)
        assertEquals("/api/v1/KEY/gifs/search", search.encodedPath)
        assertEquals("happy cat", search.queryParameter("q"))
        assertEquals("install-1", search.queryParameter("customer_id"))

        val trending = KlipyGifs.nativeUrl("KEY", " ", "install-1", Locale.UK)
        assertEquals("/api/v1/KEY/gifs/trending", trending.encodedPath)
        assertNull(trending.queryParameter("q"))
    }

    @Test
    fun klipysOwnApiPrefersSmallWebpPreviews() {
        val json = """
            {"result": true, "data": {"data": [
              {"id": 7, "title": "Wave",
               "file": {
                 "md": {"gif": {"url": "https://static.klipy.com/md.gif", "width": 320, "height": 240}},
                 "sm": {"gif": {"url": "https://static.klipy.com/sm.gif"},
                        "webp": {"url": "https://static.klipy.com/sm.webp", "width": 200, "height": 150}}
               }}
            ]}}
        """.trimIndent()

        val gif = KlipyGifs.parseNative(json).single()

        assertEquals("https://static.klipy.com/sm.webp", gif.previewUrl)
        assertEquals("https://static.klipy.com/md.gif", gif.gifUrl)
    }

    @Test
    fun cachedResultsKeepTitlesAndExpire() {
        val gifs = listOf(GifResult("1", "Happy cat", "https://p/1.webp", "https://g/1.gif", 200, 150))
        val file = File.createTempFile("gifs", ".json")
        file.writeText(KlipyGifs.resultsToJson(gifs))
        val now = file.lastModified()

        assertEquals(gifs, KlipyGifs.readResults(file, maxAgeMs = 60_000, now = now + 1_000))
        assertNull(KlipyGifs.readResults(file, maxAgeMs = 60_000, now = now + 120_000))
    }

    @Test
    fun cacheKeysIgnoreCaseAndOuterSpaces() {
        assertEquals(
            KlipyGifs.resultsCacheKey("Happy Cat", Locale.UK),
            KlipyGifs.resultsCacheKey("  happy cat ", Locale.UK)
        )
        assertTrue(KlipyGifs.resultsCacheKey("cat", Locale.UK) != KlipyGifs.resultsCacheKey("dog", Locale.UK))
    }

    @Test
    fun previewCacheDropsTheLeastRecentlyUsedFirst() {
        val folder = kotlin.io.path.createTempDirectory("previews").toFile()
        val old = File(folder, "old").apply { writeBytes(ByteArray(600)); setLastModified(1_000) }
        val recent = File(folder, "recent").apply { writeBytes(ByteArray(600)); setLastModified(2_000) }

        KlipyGifs.trimPreviews(folder, maxBytes = 1_000)

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun oldAndSurplusSearchResultsAreDropped() {
        val folder = kotlin.io.path.createTempDirectory("results").toFile()
        val now = 100L * 24 * 60 * 60 * 1000
        val day = 24L * 60 * 60 * 1000
        val stale = File(folder, "stale.json").apply { writeText("[]"); setLastModified(now - 8 * day) }
        val recent = (1..3).map { i -> File(folder, "recent$i.json").apply { writeText("[]"); setLastModified(now - i * 1000L) } }

        KlipyGifs.trimResults(folder, maxAgeMs = 7 * day, maxFiles = 2, now = now)

        assertFalse(stale.exists())
        assertTrue(recent[0].exists())
        assertTrue(recent[1].exists())
        assertFalse(recent[2].exists())
    }

    @Test
    fun cachedResultsShowAtOnceAndSayWhetherTheyAreFresh() {
        val context = RuntimeEnvironment.getApplication()
        val gifs = listOf(GifResult("1", "Happy cat", "https://p/1.webp", "https://g/1.gif", 200, 150))
        assertNull(KlipyGifs.cachedResults(context, "no such search"))

        KlipyGifs.cacheResults(context, "cat", gifs)

        assertEquals(gifs to true, KlipyGifs.cachedResults(context, "cat"))
        // Two days later: still shown at once, but asked for again behind them
        KlipyGifs.resultsFile(context, "cat").setLastModified(System.currentTimeMillis() - 2 * 24 * 3600 * 1000L)
        assertEquals(gifs to false, KlipyGifs.cachedResults(context, "cat"))
    }
}
