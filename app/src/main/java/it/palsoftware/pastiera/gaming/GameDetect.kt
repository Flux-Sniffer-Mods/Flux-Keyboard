package it.palsoftware.pastiera.gaming

import android.content.Intent
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Which of an app's games is running, for a game started from inside its launcher or emulator
 * (not from its home screen shortcut): the game file the app's screen was opened with (other
 * frontends such as ES-DE pass it), or for PPSSPP the game it last started. Read through the
 * ADB shell; null when it can't tell, and the notification's Next profile still switches.
 */
object GameDetect {
    /** The running game's profile among [profiles] for [packageName], if it can be told. */
    fun detect(packageName: String, profiles: List<GameProfile>): GameProfile? {
        if (profiles.size < 2) return null
        val running = listOfNotNull(resumedGameFile(), if (EmulatorLayouts.name(packageName) == "PPSSPP") ppssppRecent(packageName) else null)
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
}
