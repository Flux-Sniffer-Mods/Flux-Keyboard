package it.palsoftware.pastiera

import android.content.Context
import android.content.SharedPreferences

/**
 * Offline mode: nothing in Pastiera goes online. GIF search (KLIPY) disappears, and dictionary
 * and layout downloads, update checks, release notes and the downloadable emoji font are all
 * skipped. Loaded when the app starts and updated by its setting, so code without a Context
 * (the download managers) can check it too.
 */
object OfflineMode {
    @Volatile
    var enabled: Boolean = false
        private set

    // Held here: SharedPreferences keeps its listeners only weakly
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Reads the setting and follows it from then on, however it changes (a restored backup too) */
    fun load(context: Context) {
        val appContext = context.applicationContext
        enabled = SettingsManager.isOfflineMode(appContext)
        if (listener != null) return
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = SettingsManager.isOfflineMode(appContext)
        }.also { SettingsManager.getPreferences(appContext).registerOnSharedPreferenceChangeListener(it) }
    }

    internal fun update(value: Boolean) {
        enabled = value
    }
}
