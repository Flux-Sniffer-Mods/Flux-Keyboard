package it.palsoftware.pastiera.linux

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * A Linux program's home-screen shortcut, tapped: the program opened in the desktop (started
 * first if it isn't running), the desktop shown. Nothing of its own on screen.
 */
class LinuxLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = intent.getStringExtra(EXTRA_APP)?.let { LinuxApps.find(this, it) }
        when {
            app == null -> Toast.makeText(this, R.string.linux_program_gone, Toast.LENGTH_LONG).show()
            LinuxDesktop.installedMode(this) == null -> Toast.makeText(this, R.string.tile_desktop_not_installed, Toast.LENGTH_LONG).show()
            else -> {
                @Suppress("DEPRECATION")
                val metrics = android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
                if (LinuxDesktop.state.value != LinuxDesktop.State.Running) {
                    Toast.makeText(this, getString(R.string.linux_opening, app.name), Toast.LENGTH_SHORT).show()
                }
                LinuxApps.open(this, app, metrics.widthPixels, metrics.heightPixels)
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_APP = "app"
    }
}
