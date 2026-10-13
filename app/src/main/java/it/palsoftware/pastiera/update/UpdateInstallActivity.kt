package it.palsoftware.pastiera.update

import android.app.Activity
import android.os.Bundle

/**
 * Opens Android's "Install unknown apps" page for a downloaded update and installs it on the
 * way back. Allowing installs restarts the app; Android then recreates this screen from the
 * back stack, so the install still follows.
 */
class UpdateInstallActivity : Activity() {
    private var leftForSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        leftForSettings = savedInstanceState?.getBoolean(KEY_LEFT) ?: false
        // From the update notification: downloaded and installed here, not opened on GitHub
        val apkUrl = intent.getStringExtra(EXTRA_APK_URL)
        if (savedInstanceState == null && apkUrl != null) {
            ForkUpdateInstaller.downloadAndInstall(this, apkUrl, intent.getStringExtra(EXTRA_PAGE_URL))
            finish()
            return
        }
        if (savedInstanceState == null) {
            runCatching { startActivity(ForkUpdateInstaller.allowInstallsIntent(this)) }
                .onFailure { finish() }
        }
    }

    override fun onStop() {
        super.onStop()
        leftForSettings = true
    }

    override fun onResume() {
        super.onResume()
        if (!leftForSettings) return
        // Back from the settings page: install if allowed, otherwise the download waits
        ForkUpdateInstaller.installPending(this)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LEFT, true)
    }

    companion object {
        private const val KEY_LEFT = "left_for_settings"
        private const val EXTRA_APK_URL = "apk_url"
        private const val EXTRA_PAGE_URL = "page_url"

        /** Downloads and installs the update at [apkUrl] (its release page the fallback). */
        fun downloadIntent(context: android.content.Context, apkUrl: String, pageUrl: String?): android.content.Intent =
            android.content.Intent(context, UpdateInstallActivity::class.java)
                .putExtra(EXTRA_APK_URL, apkUrl).putExtra(EXTRA_PAGE_URL, pageUrl)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
