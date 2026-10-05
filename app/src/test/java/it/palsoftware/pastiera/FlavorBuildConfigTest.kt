package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlavorBuildConfigTest {

    @Test
    fun releaseChannelBuildConfigMatchesFlavor() {
        assertTrue(BuildConfig.SUCCESSOR_GITHUB_REPOSITORY.matches(Regex("[^/]+/[^/]+")))
        when (BuildConfig.RELEASE_CHANNEL) {
            "stable" -> {
                assertEquals(BuildConfig.IS_FDROID_BUILD, !BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
                assertFalse(BuildConfig.VERSION_NAME.contains("nightly"))
                assertTrue(BuildInfo.getBuildInfoString().let { it.startsWith("Version ") || it.startsWith("Dev build ") })
            }
            "nightly" -> {
                assertEquals(BuildConfig.IS_FDROID_BUILD, !BuildConfig.ENABLE_GITHUB_UPDATE_CHECKS)
                assertTrue(BuildConfig.VERSION_NAME.contains("nightly"))
                assertEquals("Nightly ${BuildConfig.VERSION_NAME}", BuildInfo.getBuildInfoString())
            }
            else -> error("Unexpected release channel: ${BuildConfig.RELEASE_CHANNEL}")
        }
    }

    @Test
    fun buildInfoNamesReleasesAndDevBuilds() {
        assertEquals("Version 1.0.0", BuildInfo.describe("1.0.0", "stable"))
        assertEquals("Dev build 1.0.1 · 2026-10-05 15:49 UTC", BuildInfo.describe("1.0.1-flux.202610051549", "stable"))
    }
}
