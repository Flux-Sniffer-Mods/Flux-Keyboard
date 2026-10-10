package it.palsoftware.pastiera.gaming

import android.content.Intent
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Which controller a Dolphin game gets: a GameCube controller, or a Wii Remote (with a Nunchuk,
 * a Classic Controller, or held sideways). Automatic tells a Wii game from a GameCube one by its
 * file; the profile can always be set by hand, for the layout that suits the game best.
 */
enum class DolphinPad {
    AUTO, GAMECUBE, NUNCHUK, CLASSIC, SIDEWAYS;

    val wii: Boolean get() = this == NUNCHUK || this == CLASSIC || this == SIDEWAYS
}

object DolphinPads {
    /** The controller [profile] gets: its own choice, or for Automatic what its game is. */
    fun resolve(profile: GameProfile): DolphinPad {
        if (profile.dolphinPad != DolphinPad.AUTO) return profile.dolphinPad
        val path = profile.launch?.let { gameFile(it) } ?: return DolphinPad.GAMECUBE
        // Most Wii games take a Wii Remote and Nunchuk; the profile can pick another
        return if (isWii(path)) DolphinPad.NUNCHUK else DolphinPad.GAMECUBE
    }

    /** The game file a profile's shortcut starts, as a path on the phone. */
    internal fun gameFile(launch: String): String? {
        val uri = runCatching { Intent.parseUri(launch, Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?.let { it.getStringExtra("AutoStartFile") ?: it.dataString } ?: return null
        return pathOf(uri)
    }

    /** A storage document's path (".../document/primary%3AGames%2Fx.rvz" -> /storage/emulated/0/Games/x.rvz). */
    internal fun pathOf(uri: String): String? {
        if (uri.startsWith("/")) return uri
        if (uri.startsWith("file://")) return java.net.URLDecoder.decode(uri.removePrefix("file://"), "UTF-8")
        val id = java.net.URLDecoder.decode(uri.substringAfterLast("/document/", "").ifEmpty { return null }, "UTF-8")
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        return (if (volume == "primary") "/storage/emulated/0" else "/storage/$volume") + "/" + relative
    }

    /** A Wii game: by its kind of file, the folder it's kept in, or its disc's own mark. */
    fun isWii(path: String): Boolean {
        byName(path)?.let { return it }
        val head = AdbShell.run("od -A n -t x1 -N 96 \"${path.replace("\"", "")}\"; true", 3_000) ?: return false
        return byHeader(head.split(Regex("\\s+")).filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }) ?: false
    }

    /** What the file's kind or folder says, if it says. */
    internal fun byName(path: String): Boolean? {
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension == "wbfs" || extension == "wad") return true
        if (extension == "gcm" || extension == "tgc") return false
        val folders = path.lowercase().split('/').dropLast(1)
        if ("wii" in folders) return true
        if ("gc" in folders || "gamecube" in folders || "ngc" in folders) return false
        return null
    }

    /**
     * What the start of the file says: a disc image's own Wii or GameCube mark, or the disc type
     * an RVZ or WIA file records. Null when it's neither (an executable, an unknown kind).
     */
    internal fun byHeader(bytes: List<Int>): Boolean? {
        fun word(at: Int) = if (bytes.size < at + 4) -1L else
            (bytes[at].toLong() shl 24) or (bytes[at + 1].toLong() shl 16) or (bytes[at + 2].toLong() shl 8) or bytes[at + 3].toLong()
        if (word(0x18) == 0x5D1C9EA3L) return true
        if (word(0x1C) == 0xC2339F3DL) return false
        // RVZ and WIA: "RVZ\u0001" or "WIA\u0001", then their second header's disc type (1 GameCube, 2 Wii)
        val magic = bytes.take(3).map { it.toChar() }.joinToString("")
        if ((magic == "RVZ" || magic == "WIA") && bytes.getOrNull(3) == 1) {
            return when (word(0x48)) { 2L -> true; 1L -> false; else -> null }
        }
        return null
    }

    /** Each action's control on a Wii Remote profile, for [pad]. */
    internal fun wiiControls(pad: DolphinPad): Map<GameAction, String> = when (pad) {
        DolphinPad.NUNCHUK -> mapOf(
            GameAction.BUTTON_A to "Buttons/A", GameAction.BUTTON_B to "Buttons/B", GameAction.R2 to "Buttons/B",
            GameAction.BUTTON_X to "Buttons/1", GameAction.BUTTON_Y to "Buttons/2",
            GameAction.L1 to "Nunchuk/Buttons/C", GameAction.L2 to "Nunchuk/Buttons/Z",
            GameAction.START to "Buttons/+", GameAction.SELECT to "Buttons/-", GameAction.HOME to "Buttons/Home",
            GameAction.DPAD_UP to "D-Pad/Up", GameAction.DPAD_DOWN to "D-Pad/Down",
            GameAction.DPAD_LEFT to "D-Pad/Left", GameAction.DPAD_RIGHT to "D-Pad/Right",
            GameAction.KEY_W to "Nunchuk/Stick/Up", GameAction.KEY_S to "Nunchuk/Stick/Down",
            GameAction.KEY_A to "Nunchuk/Stick/Left", GameAction.KEY_D to "Nunchuk/Stick/Right"
        )
        DolphinPad.CLASSIC -> mapOf(
            GameAction.BUTTON_A to "Classic/Buttons/a", GameAction.BUTTON_B to "Classic/Buttons/b",
            GameAction.BUTTON_X to "Classic/Buttons/x", GameAction.BUTTON_Y to "Classic/Buttons/y",
            GameAction.L1 to "Classic/Buttons/ZL", GameAction.R1 to "Classic/Buttons/ZR",
            GameAction.L2 to "Classic/Triggers/L", GameAction.R2 to "Classic/Triggers/R",
            GameAction.START to "Classic/Buttons/+", GameAction.SELECT to "Classic/Buttons/-", GameAction.HOME to "Classic/Buttons/Home",
            GameAction.DPAD_UP to "Classic/D-Pad/Up", GameAction.DPAD_DOWN to "Classic/D-Pad/Down",
            GameAction.DPAD_LEFT to "Classic/D-Pad/Left", GameAction.DPAD_RIGHT to "Classic/D-Pad/Right",
            GameAction.KEY_W to "Classic/Left Stick/Up", GameAction.KEY_S to "Classic/Left Stick/Down",
            GameAction.KEY_A to "Classic/Left Stick/Left", GameAction.KEY_D to "Classic/Left Stick/Right"
        )
        // Held sideways, as for NES-style games: 2 is the right-hand button, 1 the left
        DolphinPad.SIDEWAYS -> mapOf(
            GameAction.BUTTON_A to "Buttons/2", GameAction.BUTTON_B to "Buttons/1",
            GameAction.BUTTON_X to "Buttons/B", GameAction.BUTTON_Y to "Buttons/A",
            GameAction.START to "Buttons/+", GameAction.SELECT to "Buttons/-", GameAction.HOME to "Buttons/Home",
            GameAction.DPAD_UP to "D-Pad/Up", GameAction.DPAD_DOWN to "D-Pad/Down",
            GameAction.DPAD_LEFT to "D-Pad/Left", GameAction.DPAD_RIGHT to "D-Pad/Right"
        )
        else -> emptyMap()
    }

    /** The lines that set up the Wii Remote itself: its extension, or holding it sideways. */
    internal fun wiiOptions(pad: DolphinPad): List<String> = when (pad) {
        DolphinPad.NUNCHUK -> listOf("Extension = Nunchuk")
        DolphinPad.CLASSIC -> listOf("Extension = Classic")
        DolphinPad.SIDEWAYS -> listOf("Extension = None", "Options/Sideways Wiimote = True")
        else -> emptyList()
    }
}
