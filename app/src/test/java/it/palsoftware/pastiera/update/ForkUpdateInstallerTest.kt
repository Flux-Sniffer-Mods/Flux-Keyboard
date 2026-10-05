package it.palsoftware.pastiera.update

import it.palsoftware.pastiera.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkUpdateInstallerTest {
    private val repo = BuildConfig.FORK_GITHUB_REPOSITORY

    @Test
    fun onlyThisRepositorysReleaseApksAreDownloaded() {
        assertTrue(ForkUpdateInstaller.isTrustedApkUrl("https://github.com/$repo/releases/download/flux/v0.90-flux.1/flux-keyboard-0.90-flux.1.apk"))
        assertFalse(ForkUpdateInstaller.isTrustedApkUrl("https://github.com/someone/else/releases/download/v1/app.apk"))
        assertFalse(ForkUpdateInstaller.isTrustedApkUrl("http://github.com/$repo/releases/download/flux/v1/app.apk"))
        assertFalse(ForkUpdateInstaller.isTrustedApkUrl("https://github.com/$repo/releases/download/flux/v1/notes.zip"))
    }

    @Test
    fun onlyANewerBuildOfThisAppIsInstalled() {
        val own = "io.github.fluxsniffermods.fluxkeyboard"
        assertTrue(ForkUpdateInstaller.isNewerBuildOfThisApp(own, "0.90-flux.202609261300", own, "0.86-flux.202609261133"))
        assertFalse(ForkUpdateInstaller.isNewerBuildOfThisApp(own, "0.86-flux.202609261133", own, "0.86-flux.202609261133"))
        assertFalse(ForkUpdateInstaller.isNewerBuildOfThisApp("com.example.other", "9.0", own, "0.86-flux.1"))
        assertFalse(ForkUpdateInstaller.isNewerBuildOfThisApp(own, null, own, "0.86-flux.1"))
    }
}
