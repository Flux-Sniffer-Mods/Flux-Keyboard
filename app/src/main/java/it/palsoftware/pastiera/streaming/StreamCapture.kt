package it.palsoftware.pastiera.streaming

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Asks Android to capture the screen for a Moonlight session (straight through when the shell
 * has allowed it ahead), hands the capture to the streaming service, then starts the game.
 */
class StreamCapture : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (StreamHost.session == null) { finish(); return }
        if (savedInstanceState != null) return
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (manager == null) { finish(); return }
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST)
    }

    @Deprecated("Activity result API not used in this plain activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                StreamHostService.capture(this, resultCode, data)
                StreamHost.takeLaunch()?.let { game -> runCatching { startActivity(game.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            } else {
                StreamHost.session?.end()
            }
        }
        finish()
    }

    private companion object {
        const val REQUEST = 7306
    }
}
