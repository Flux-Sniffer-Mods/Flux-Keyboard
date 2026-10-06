package it.palsoftware.pastiera.core

import android.content.Context
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.isOfflineMode
import it.palsoftware.pastiera.setOfflineMode

/**
 * Private mode, from a key shortcut or the quick launcher: Incognito typing in every field and
 * Offline mode together, at once. Turning it off restores Offline mode as it was before.
 */
object PrivateMode {
    private const val KEY_ON = "private_mode"
    private const val KEY_OFFLINE_BEFORE = "private_mode_offline_before"

    fun isOn(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ON, false)

    /** Switches it over and says so; returns whether it's now on. */
    fun toggle(context: Context): Boolean {
        val prefs = SettingsManager.getPreferences(context)
        val on = !isOn(context)
        if (on) {
            prefs.edit()
                .putBoolean(KEY_ON, true)
                .putBoolean(KEY_OFFLINE_BEFORE, SettingsManager.isOfflineMode(context))
                .apply()
            SettingsManager.setOfflineMode(context, true)
            IncognitoTyping.active = true
        } else {
            val offlineBefore = prefs.getBoolean(KEY_OFFLINE_BEFORE, false)
            prefs.edit().putBoolean(KEY_ON, false).remove(KEY_OFFLINE_BEFORE).apply()
            SettingsManager.setOfflineMode(context, offlineBefore)
            IncognitoTyping.active = false
        }
        Toast.makeText(
            context,
            if (on) R.string.private_mode_on else R.string.private_mode_off,
            Toast.LENGTH_LONG
        ).show()
        return on
    }
}
