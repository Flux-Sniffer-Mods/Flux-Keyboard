package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.apps.appLabel
import android.content.Intent
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Which of an app's games is running, for a game started from inside its launcher or emulator
 * (not from its home screen shortcut): the game file the app's screen was opened with (other
 * frontends such as ES-DE pass it), or for PPSSPP the game it last started. Read through the
 * ADB shell; null when it can't tell, and the notification's Next profile still switches.
 */
object GameDetect {
    /** The running game among [profiles] (the app's games) for [packageName], if it can be told. */
    fun detect(packageName: String, profiles: List<GameProfile>): GameProfile? {
        if (profiles.isEmpty()) return null
        val running = listOfNotNull(resumedGameFile(), if (GameApps.emulator(packageName) == "PPSSPP") ppssppRecent(packageName) else null)
            .map { key(it) }.filter { it.length >= 3 }
        if (running.isEmpty()) return null
        return running.firstNotNullOfOrNull { game -> profiles.firstOrNull { matches(it, game) } }
    }

    internal fun matches(profile: GameProfile, game: String): Boolean {
        val file = profile.launch?.let { gameFileOf(it) }?.let { key(it) }
        if (file != null && file == game) return true
        val name = key(profile.name)
        return name.length >= 4 && (game == name || game.contains(name) || name.contains(game))
    }

    /** The game file a profile's shortcut starts, if any. */
    private fun gameFileOf(launch: String): String? = runCatching {
        val intent = Intent.parseUri(launch, Intent.URI_INTENT_SCHEME)
        intent.dataString ?: intent.getStringExtra("AutoStartFile")
    }.getOrNull()

    /** A file's name, lower case letters and digits only ("Wind Waker (USA).rvz" -> "windwakerusa"). */
    internal fun key(path: String): String {
        val decoded = runCatching { java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)
        val file = decoded.substringAfterLast('/').substringAfterLast(':')
        val name = if (file.contains('.')) file.substringBeforeLast('.') else file
        return name.lowercase().filter { it.isLetterOrDigit() }
    }

    /** The data the app in front was opened with, from Android's list of activities. */
    private fun resumedGameFile(): String? {
        val dump = AdbShell.run("dumpsys activity activities", 4_000) ?: return null
        return resumedData(dump)
    }

    internal fun resumedData(dump: String): String? {
        val hash = Regex("(?:topResumedActivity|mResumedActivity)[=:] ?ActivityRecord\\{(\\w+)").find(dump)?.groupValues?.get(1)
            ?: return null
        val start = Regex("\\* (?:Hist +#\\d+: )?ActivityRecord\\{$hash ").find(dump)?.range?.last ?: return null
        val block = dump.substring(start).let { rest -> rest.substring(0, Regex("\n\\s*\\* ").find(rest)?.range?.first ?: rest.length) }
        val intent = Regex("intent=\\{([^}]*)\\}").find(block)?.groupValues?.get(1) ?: return null
        return Regex("dat=(\\S+)").find(intent)?.groupValues?.get(1)
    }

    /** PPSSPP's last started game ([Recent] FileName0 in ppsspp.ini). */
    private fun ppssppRecent(pkg: String): String? {
        val ini = AdbShell.run(
            "for f in /storage/emulated/0/PSP/SYSTEM/ppsspp.ini /storage/emulated/0/Android/data/$pkg/files/PSP/SYSTEM/ppsspp.ini; do " +
                "[ -f \"\$f\" ] && { grep -m 1 '^FileName0' \"\$f\"; break; }; done; true", 3_000
        ) ?: return null
        return ini.substringAfter('=', "").trim().ifEmpty { null }
    }

    // ---- Apps that look like games: offered once for gaming mode ----

    internal const val ACTION_NOT_GAME = "it.palsoftware.pastiera.GAME_MODE_NOT_GAME"
    internal const val EXTRA_PACKAGE = "package"
    private const val KEY_ASKED = "game_detect_asked"
    private const val CHANNEL = "game_detect"
    private const val NOTIFICATION_ID = 7303

    /** An app Android files as a game (by its own category, or the older game flag), or a known game app. */
    fun looksLikeGame(context: android.content.Context, packageName: String): Boolean = runCatching {
        if (GameApps.appFor(packageName) != null) return true
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        @Suppress("DEPRECATION")
        info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
            (info.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0
    }.getOrDefault(false)

    private fun asked(context: android.content.Context): Set<String> =
        it.palsoftware.pastiera.SettingsManager.getPreferences(context).getStringSet(KEY_ASKED, emptySet()).orEmpty()

    /** Not asked about again (set up, or turned down). */
    fun dismiss(context: android.content.Context, packageName: String) {
        val prefs = it.palsoftware.pastiera.SettingsManager.getPreferences(context)
        prefs.edit().putStringSet(KEY_ASKED, asked(context) + packageName).apply()
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /**
     * A game in front with no gaming mode settings: a notification, once per app, offering to
     * set it up (its settings made from Flux Keyboard's layout) or to leave it.
     */
    fun offer(context: android.content.Context, packageName: String) {
        if (packageName in asked(context) || !looksLikeGame(context, packageName)) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val label = context.appLabel(packageName)
        manager.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL, context.getString(it.palsoftware.pastiera.R.string.game_detect_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT)
        )
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        val setUp = android.app.PendingIntent.getActivity(
            context, packageName.hashCode(), GameEditActivity.forApp(context, packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), flags
        )
        val notGame = android.app.PendingIntent.getBroadcast(
            context, packageName.hashCode() + 1,
            android.content.Intent(context, GameMode.Receiver::class.java).setAction(ACTION_NOT_GAME).putExtra(EXTRA_PACKAGE, packageName), flags
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(it.palsoftware.pastiera.R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(it.palsoftware.pastiera.R.string.game_detect_title, label))
            .setContentText(context.getString(it.palsoftware.pastiera.R.string.game_detect_text))
            .setContentIntent(setUp)
            .setAutoCancel(true)
            .addAction(android.app.Notification.Action.Builder(null, context.getString(it.palsoftware.pastiera.R.string.game_first_start_set_up), setUp).build())
            .addAction(android.app.Notification.Action.Builder(null, context.getString(it.palsoftware.pastiera.R.string.game_detect_not_game), notGame).build())
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        // Asked once: turning it down or leaving it alike
        it.palsoftware.pastiera.SettingsManager.getPreferences(context).edit().putStringSet(KEY_ASKED, asked(context) + packageName).apply()
    }
}
