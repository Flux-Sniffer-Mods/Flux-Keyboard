package it.palsoftware.pastiera.legacy

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build

/**
 * Shortcuts an app's shortcut screen returns the way it was done before Android 8: the intent
 * and icon as extras of the result.
 */
internal object LegacyShortcutExtras {
    @Suppress("DEPRECATION")
    fun intent(data: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
        }

    @Suppress("DEPRECATION")
    fun icon(data: Intent): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        } else {
            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
        }
}
