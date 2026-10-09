package it.palsoftware.pastiera.gaming

import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.apps.AppListHelper
import it.palsoftware.pastiera.apps.InstalledApp

/**
 * The games of game launchers (GameNative, GameHub, GameHub Lite), so a profile can be made for
 * each: the shortcuts they put on the home screen or in their app shortcuts (Android's shortcut
 * list), and the Steam games installed in their folders, both read through the ADB shell.
 */
object GameLibrary {
    private val LAUNCHER_NAMES = listOf("gamenative", "gamehub")

    /** A game: its name, its launcher and, for a GameNative shortcut, its game ID and store. */
    data class Game(val name: String, val packageName: String, val gameId: Int? = null, val source: String? = null)

    /** The game launchers installed, found by name. */
    fun launchers(context: Context): List<InstalledApp> =
        AppListHelper.getInstalledApps(context).filter { app ->
            val name = app.appName.lowercase().replace(" ", "")
            LAUNCHER_NAMES.any { it in name }
        }

    /** The games a launcher lists (blocking: reads the shell). */
    fun games(packageName: String): List<Game> {
        val shortcuts = AdbShell.run("dumpsys shortcut", 8_000)?.let { parseGames(it, packageName) }.orEmpty()
        // Where games are installed on shared storage: GameNative's own folder on each storage
        // (Steam, GOG, Epic, Amazon), the launcher's folder in Android/data, and any Steam
        // library on the phone's storage. Games kept in a launcher's private storage can't be seen
        val folders = AdbShell.run(
            "for r in /storage/emulated/0 /storage/*-*; do " +
                "for b in \"\$r/GameNative\" \"\$r/Android/data/$packageName\" \"\$r/Android/data/$packageName/files\"; do " +
                "for s in Steam/steamapps/common GOG/games/common Epic/games Amazon/games; do " +
                "[ -d \"\$b/\$s\" ] && ls -1 \"\$b/\$s\"; done; done; " +
                "find \"\$r/Android/data/$packageName\" -maxdepth 7 -type d -path '*steamapps/common' -exec ls -1 {} \\; 2>/dev/null; done; " +
                "find /storage/emulated/0 -maxdepth 5 -path /storage/emulated/0/Android -prune -o -type d -path '*steamapps/common' -exec ls -1 {} \\; 2>/dev/null; true",
            10_000
        )?.lines().orEmpty().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(".") && it != "Steamworks Shared" }
        val known = shortcuts.map { it.name.lowercase() }.toSet()
        return shortcuts + folders.distinct().filter { it.lowercase() !in known }.map { Game(it, packageName) }
    }

    internal fun parseGames(dump: String, packageName: String): List<Game> {
        val ownPackage = Regex("packageName=${Regex.escape(packageName)}(?![\\w.])")
        return dump.split("ShortcutInfo {").drop(1).mapNotNull { block ->
            if (!ownPackage.containsMatchIn(block)) return@mapNotNull null
            val label = Regex("shortLabel=([^,\\n]+)").find(block)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" } ?: return@mapNotNull null
            val id = Regex("(?<![\\w])id=([^,\\s}]+)").find(block)?.groupValues?.get(1)
            // GameNative's shortcuts are game_<its game ID>
            val gameId = id?.removePrefix("game_")?.takeIf { id.startsWith("game_") }?.toIntOrNull()
            val source = Regex("game_source=(\\w+)").find(block)?.groupValues?.get(1)
            Game(label, packageName, gameId, source)
        }.distinctBy { it.name }
    }

    /** What starts [game] straight away, where its launcher allows it; null to open the launcher. */
    fun launchIntent(game: Game): Intent? {
        val id = game.gameId ?: return null
        if (game.packageName != GameNativeBridge.PACKAGE) return null
        return Intent("app.gamenative.LAUNCH_GAME").setPackage(game.packageName)
            .putExtra("app_id", id)
            .putExtra("game_source", game.source ?: "STEAM")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
