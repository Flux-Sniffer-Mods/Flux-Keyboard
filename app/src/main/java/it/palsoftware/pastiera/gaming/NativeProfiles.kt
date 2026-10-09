package it.palsoftware.pastiera.gaming

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.adb.AdbShell

/**
 * A game's profile in its launcher's or emulator's own form, for games started from inside
 * it: GameNative's controls profile, a Dolphin controller profile, the game's own PPSSPP
 * settings, or for Azahar and Eden (no file to load) the keys to bind. Each comes with how to
 * load it there; the emulators then take the keys themselves (gaming mode leaves them alone).
 */
object NativeProfiles {
    /** What saving did: [steps] to show, and whether the app now reads the keys itself. */
    data class Result(val steps: String, val nativeKeys: Boolean)

    /** The app this profile's game is played in, if it has a form of its own. */
    fun appFor(profile: GameProfile): String? = profile.packages.firstNotNullOfOrNull { pkg ->
        when {
            pkg == GameNativeBridge.PACKAGE -> "GameNative"
            pkg.contains("gamehub", true) || pkg == "com.xiaoji.egggame" -> "GameHub"
            else -> EmulatorLayouts.name(pkg)
        }
    }

    /** Saves [profile] in its app's form (blocking: writes through the shell). */
    fun save(context: Context, profile: GameProfile): Result {
        val pkg = profile.packages.firstOrNull() ?: return Result(context.getString(R.string.native_failed), false)
        val game = profile.name.ifBlank { "Game" }.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        return when (appFor(profile)) {
            "GameNative" -> if (GameNativeBridge.save(context, game)) {
                Result(context.getString(R.string.native_gamenative_steps, "$game (Flux Keyboard).icp", profile.name), false)
            } else Result(context.getString(R.string.native_failed), false)
            "GameHub" -> Result(context.getString(R.string.native_gamehub_steps), false)
            "Dolphin" -> dolphin(context, pkg, game, profile)
            "PPSSPP" -> ppsspp(context, pkg, profile)
            "Azahar", "Eden" -> Result(
                context.getString(R.string.native_bind_steps, appFor(profile), bindings(profile)), true
            )
            else -> Result(context.getString(R.string.native_none), false)
        }
    }

    /** "A (bottom): L" lines, one per button set. */
    private fun bindings(profile: GameProfile): String =
        profile.keys.entries.filter { e -> e.value.gamepad }.sortedBy { e -> e.value.ordinal }
            .joinToString("\n") { (key, action) -> "${action.label}: ${keyName(key)}" }

    /** The physical keyboard's name, as Android (and so Dolphin) knows it. */
    private fun keyboardName(@Suppress("UNUSED_PARAMETER") context: Context): String? {
        for (id in InputDevice.getDeviceIds()) {
            val device: InputDevice = InputDevice.getDevice(id) ?: continue
            val keyboard = (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
            if (!device.isVirtual && keyboard && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) return device.name
        }
        return null
    }

    /** A key's name as Dolphin writes it (its KEYCODE_NAMES), which also reads well. */
    internal fun keyName(code: Int): String = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (code - KeyEvent.KEYCODE_0)).toString()
        KeyEvent.KEYCODE_DPAD_UP -> "Up"; KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"; KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume Down"
        KeyEvent.KEYCODE_SHIFT_LEFT -> "Left Shift"; KeyEvent.KEYCODE_SHIFT_RIGHT -> "Right Shift"
        KeyEvent.KEYCODE_ALT_LEFT -> "Left Alt"; KeyEvent.KEYCODE_ALT_RIGHT -> "Right Alt"
        KeyEvent.KEYCODE_TAB -> "Tab"; KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_ENTER -> "Enter"; KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_ESCAPE -> "Escape"; KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
        KeyEvent.KEYCODE_CTRL_LEFT -> "Left Ctrl"; KeyEvent.KEYCODE_CTRL_RIGHT -> "Right Ctrl"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase()
            .split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    /** Writes [content] to [path] through the shell (its folder made first). */
    private fun write(path: String, content: String): Boolean {
        val data = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val dir = path.substringBeforeLast('/')
        return AdbShell.run("mkdir -p \"$dir\" && echo '$data' | base64 -d > \"$path\"", 5_000) != null
    }

    // ---- Dolphin: a GameCube controller profile (Config/Profiles/GCPad/<game>.ini) ----

    private val DOLPHIN_BUTTONS = mapOf(
        GameAction.BUTTON_A to "Buttons/A", GameAction.BUTTON_B to "Buttons/B",
        GameAction.BUTTON_X to "Buttons/X", GameAction.BUTTON_Y to "Buttons/Y",
        GameAction.R1 to "Buttons/Z", GameAction.START to "Buttons/Start",
        GameAction.L1 to "Triggers/L", GameAction.L2 to "Triggers/L",
        GameAction.R2 to "Triggers/R",
        GameAction.DPAD_UP to "D-Pad/Up", GameAction.DPAD_DOWN to "D-Pad/Down",
        GameAction.DPAD_LEFT to "D-Pad/Left", GameAction.DPAD_RIGHT to "D-Pad/Right",
        GameAction.KEY_W to "Main Stick/Up", GameAction.KEY_S to "Main Stick/Down",
        GameAction.KEY_A to "Main Stick/Left", GameAction.KEY_D to "Main Stick/Right"
    )

    internal fun dolphinProfile(profile: GameProfile, keyboard: String): String {
        val lines = profile.keys.entries.groupBy({ e -> DOLPHIN_BUTTONS[e.value] }, { e -> e.key })
            .filterKeys { k -> k != null }
            .map { (control, keys) -> "$control = " + keys.joinToString(" | ") { k -> "`${keyName(k)}`" } }
            .sorted()
        return (listOf("[Profile]", "Device = Android/0/$keyboard") + lines).joinToString("\n") + "\n"
    }

    private fun dolphin(context: Context, pkg: String, game: String, profile: GameProfile): Result {
        val keyboard = keyboardName(context) ?: return Result(context.getString(R.string.native_failed), false)
        val dir = AdbShell.run(
            "for d in \"/storage/emulated/0/Android/data/$pkg/files\" /storage/emulated/0/dolphin-emu; do " +
                "[ -d \"\$d/Config\" ] && { echo \"\$d\"; break; }; done; true", 3_000
        )?.trim()?.ifEmpty { null } ?: "/storage/emulated/0/Android/data/$pkg/files"
        val ok = write("$dir/Config/Profiles/GCPad/$game.ini", dolphinProfile(profile, keyboard))
        return if (ok) Result(context.getString(R.string.native_dolphin_steps, game), true)
        else Result(context.getString(R.string.native_failed), false)
    }

    // ---- PPSSPP: the game's own settings (PSP/SYSTEM/<game ID>_ppsspp.ini), [ControlMapping] ----

    private val PSP_BUTTONS = mapOf(
        GameAction.BUTTON_A to "Cross", GameAction.BUTTON_B to "Circle",
        GameAction.BUTTON_X to "Square", GameAction.BUTTON_Y to "Triangle",
        GameAction.L1 to "L", GameAction.L2 to "L", GameAction.R1 to "R", GameAction.R2 to "R",
        GameAction.START to "Start", GameAction.SELECT to "Select",
        GameAction.DPAD_UP to "Up", GameAction.DPAD_DOWN to "Down",
        GameAction.DPAD_LEFT to "Left", GameAction.DPAD_RIGHT to "Right",
        GameAction.KEY_W to "An.Up", GameAction.KEY_S to "An.Down",
        GameAction.KEY_A to "An.Left", GameAction.KEY_D to "An.Right"
    )

    /** PPSSPP's keyboard device is 1; its key codes are Android's. */
    internal fun pspMapping(profile: GameProfile): String =
        profile.keys.entries.groupBy({ e -> PSP_BUTTONS[e.value] }, { e -> e.key }).filterKeys { k -> k != null }
            .map { (button, keys) -> "$button = " + keys.joinToString(",") { k -> "1-$k" } }
            .sorted().joinToString("\n", prefix = "[ControlMapping]\n", postfix = "\n")

    /** [ini] with its [ControlMapping] section replaced by [mapping]. */
    internal fun withMapping(ini: String, mapping: String): String {
        val kept = Regex("(?ms)^\\[ControlMapping\\].*?(?=^\\[|\\z)").replace(ini, "").trimEnd()
        return (if (kept.isEmpty()) "" else "$kept\n\n") + mapping
    }

    private fun ppsspp(context: Context, pkg: String, profile: GameProfile): Result {
        // The game config made last: PPSSPP's own, made for this game just before
        val file = AdbShell.run(
            "ls -t /storage/emulated/0/PSP/SYSTEM/*_ppsspp.ini /storage/emulated/0/Android/data/$pkg/files/PSP/SYSTEM/*_ppsspp.ini 2>/dev/null | " +
                "grep -v '/ppsspp.ini' | head -n 1; true", 3_000
        )?.trim()?.ifEmpty { null } ?: return Result(context.getString(R.string.native_ppsspp_none), false)
        val ini = AdbShell.run("cat \"$file\"", 3_000) ?: return Result(context.getString(R.string.native_failed), false)
        val ok = write(file, withMapping(ini, pspMapping(profile)))
        val id = file.substringAfterLast('/').removeSuffix("_ppsspp.ini")
        return if (ok) Result(context.getString(R.string.native_ppsspp_steps, id), true)
        else Result(context.getString(R.string.native_failed), false)
    }
}
