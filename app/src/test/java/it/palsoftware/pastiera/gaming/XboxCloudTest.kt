package it.palsoftware.pastiera.gaming

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XboxCloudTest {
    @Test
    fun theCloudListsIdsAreRead() {
        val json = """[{"siglId":"29a8","title":"All games"},{"id":"9NPDN9R45JX4"},{"id":"9P8LR42PTRGJ"}]"""
        assertEquals(listOf("9NPDN9R45JX4", "9P8LR42PTRGJ"), XboxCloud.parseIds(json))
    }

    @Test
    fun eachProductsTitleAndPosterAreRead() {
        val json = """{"Products":[{"ProductId":"9NPDN9R45JX4","LocalizedProperties":[{"ProductTitle":"1000xRESIST",
            "Images":[{"ImagePurpose":"Logo","Uri":"//x/logo"},{"ImagePurpose":"Poster","Uri":"//x/poster"}]}]}]}"""
        assertEquals(listOf(XboxCloud.CloudGame("9NPDN9R45JX4", "1000xRESIST", "https://x/poster")), XboxCloud.parseProducts(json))
    }

    @Test
    fun aGamesPlayLinkStartsItInTheXboxApp() {
        val launch = XboxCloud.launchFor(XboxCloud.CloudGame("9NPDN9R45JX4", "1000xRESIST", null), "com.gamepass")
        assertEquals("9NPDN9R45JX4", XboxCloud.idOf(launch))
        assert(launch.contains("1000xresist")) { launch }
    }
}
