package it.palsoftware.pastiera.gaming

import android.content.Context
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.apps.AppListHelper
import it.palsoftware.pastiera.apps.InstalledApp

/**
 * The games of game launchers (GameNative, GameHub, GameHub Lite): each game they put on the
 * home screen or in their app shortcuts, read from Android's shortcut list through the ADB
 * shell, so a profile can be made for each.
 */
object GameLibrary {
    private val LAUNCHER_NAMES = listOf("gamenative", "gamehub")

    /** The game launchers installed, found by name. */
    fun launchers(context: Context): List<InstalledApp> =
        AppListHelper.getInstalledApps(context).filter { app ->
            val name = app.appName.lowercase().replace(" ", "")
            LAUNCHER_NAMES.any { it in name }
        }

    /** The games a launcher lists (blocking: reads the shell). */
    fun games(packageName: String): List<String> {
        val dump = AdbShell.run("dumpsys shortcut", 8_000) ?: return emptyList()
        return parseGames(dump, packageName)
    }

    internal fun parseGames(dump: String, packageName: String): List<String> {
        val pattern = Regex("ShortcutInfo \\{[^}]*?packageName=${Regex.escape(packageName)}[^}]*?shortLabel=([^,}]+)")
        return pattern.findAll(dump).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()
    }
}
