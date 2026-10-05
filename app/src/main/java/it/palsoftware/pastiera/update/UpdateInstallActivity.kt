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

    private companion object {
        const val KEY_LEFT = "left_for_settings"
    }
}
