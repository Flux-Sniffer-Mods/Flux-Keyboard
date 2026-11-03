package it.palsoftware.pastiera.flux

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ShortcutManager
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager

/**
 * What gaming mode, streaming and the Linux desktop left in the keyboard's app when they became
 * apps of their own, tidied once: their notification categories removed, and the games and
 * Linux programs pinned to the home screen greyed out, saying where to pin them again (their
 * screens aren't in this app any more).
 */
internal object MovedModules {
    private const val KEY_DONE = "flux_moved_modules_tidied"
    private val CHANNELS = listOf("game_mode", "game_detect", "stream_host", "stream_pairing", "airplay", "linux", "linux_notifications")

    fun tidy(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getBoolean(KEY_DONE, false)) return
        context.getSystemService(NotificationManager::class.java)?.let { manager ->
            CHANNELS.forEach { runCatching { manager.deleteNotificationChannel(it) } }
        }
        runCatching {
            val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return@runCatching
            val pinned = shortcuts.pinnedShortcuts.map { it.id }
            val games = pinned.filter { it.startsWith("game_") || it.startsWith("games_") }
            val linux = pinned.filter { it.startsWith("linux_") }
            if (games.isNotEmpty()) shortcuts.disableShortcuts(games, context.getString(R.string.flux_shortcut_moved_gaming))
            if (linux.isNotEmpty()) shortcuts.disableShortcuts(linux, context.getString(R.string.flux_shortcut_moved_desktop))
            shortcuts.dynamicShortcuts.map { it.id }
                .filter { it.startsWith("game_") || it.startsWith("games_") || it.startsWith("linux_") }
                .takeIf { it.isNotEmpty() }?.let { shortcuts.removeDynamicShortcuts(it) }
        }
        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }
}
