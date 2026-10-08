package it.palsoftware.pastiera.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCleanerTest {
    @Test
    fun trackingParametersGo() {
        assertEquals(
            "https://example.com/article?id=7",
            LinkCleaner.clean("https://example.com/article?utm_source=x&id=7&utm_medium=social&fbclid=abc")
        )
        assertEquals("https://example.com/a", LinkCleaner.clean("https://example.com/a?gclid=1&_ga=2"))
    }

    @Test
    fun siteShareIdsGoOnlyOnTheirSites() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ", LinkCleaner.clean("https://youtu.be/dQw4w9WgXcQ?si=Ab12"))
        assertEquals("https://youtu.be/WDF9JV2Vye0", LinkCleaner.clean("https://youtu.be/WDF9JV2Vye0?is=UFoIIt4lC2bt9Uzj"))
        assertEquals("https://youtube.com/watch?v=WDF9JV2Vye0&t=42", LinkCleaner.clean("https://youtube.com/watch?v=WDF9JV2Vye0&is=UFoIIt4lC2bt9Uzj&t=42"))
        // YouTube: share ids and channel labels go, the video, playlist and start time stay
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1&t=42",
            LinkCleaner.clean("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1&t=42&ab_channel=Rick&pp=ygUE")
        )
        assertEquals("https://youtube.com/shorts/abc123", LinkCleaner.clean("https://youtube.com/shorts/abc123?si=xyz&feature=share"))
        assertEquals("https://music.youtube.com/watch?v=abc", LinkCleaner.clean("https://music.youtube.com/watch?v=abc&si=Q1"))
        // Cleaning twice changes nothing (the cleaned clipboard comes back to be checked again)
        val once = LinkCleaner.clean("https://m.youtube.com/watch?v=abc&si=1")
        assertEquals(once, LinkCleaner.clean(once))
        assertEquals(
            "https://open.spotify.com/track/1?go=1",
            LinkCleaner.clean("https://open.spotify.com/track/1?si=xyz&go=1")
        )
        // si means something else on other sites, so it stays
        assertEquals("https://example.com/?si=5", LinkCleaner.clean("https://example.com/?si=5"))
    }

    @Test
    fun mobileHostsBecomeTheFullSite() {
        assertEquals("https://youtube.com/watch?v=abc", LinkCleaner.clean("https://m.youtube.com/watch?v=abc&feature=share"))
        assertEquals(
            "https://en.wikipedia.org/wiki/Pasta#History",
            LinkCleaner.clean("https://en.m.wikipedia.org/wiki/Pasta#History")
        )
    }

    @Test
    fun textAroundLinksAndSentencePunctuationStay() {
        assertEquals(
            "Look: https://example.com/x?id=1. Nice!",
            LinkCleaner.clean("Look: https://example.com/x?id=1&utm_campaign=y. Nice!")
        )
        assertEquals("no links here", LinkCleaner.clean("no links here"))
    }

    @Test
    fun redirectsBecomeTheLinkTheyLeadTo() {
        assertEquals(
            "https://example.com/page?id=3",
            LinkCleaner.clean("https://www.google.com/url?sa=t&source=web&url=https%3A%2F%2Fexample.com%2Fpage%3Fid%3D3%26utm_source%3Dg&ved=2a")
        )
        assertEquals("https://example.com/a", LinkCleaner.clean("https://www.google.co.uk/url?q=https://example.com/a&sa=U"))
        assertEquals("https://news.site/story", LinkCleaner.clean("https://www.google.com/amp/s/news.site/story"))
        assertEquals("https://example.com/x", LinkCleaner.clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fx%3Ffbclid%3D1&h=AT0"))
        assertEquals("See https://youtu.be/abc.", LinkCleaner.clean("See https://youtu.be/abc?si=zz."))
    }

    @Test
    fun shopTrackingGoesWhileTheItemStays() {
        assertEquals(
            "https://www.etsy.com/uk/listing/123/mug",
            LinkCleaner.clean("https://www.etsy.com/uk/listing/123/mug?click_key=abc&click_sum=1&ref=hp_rv-3&pro=1&frs=1&sts=1")
        )
        assertEquals(
            "https://www.ebay.co.uk/itm/2345?var=7",
            LinkCleaner.clean("https://www.ebay.co.uk/itm/2345?_trkparms=x&_trksid=p1&hash=item1&var=7&mkcid=16")
        )
        assertEquals(
            "https://www.amazon.de/dp/B01?th=1",
            LinkCleaner.clean("https://www.amazon.de/dp/B01?th=1&tag=aff-21&psc=1&linkCode=ll1&ref_=x")
        )
    }
}
