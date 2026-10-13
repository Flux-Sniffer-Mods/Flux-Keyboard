package it.palsoftware.pastiera.gaming

import android.view.InputDevice
import android.view.KeyEvent
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Flux Keyboard's controller layout, the same everywhere: W A S D the d-pad, O K P L the face
 * buttons where a controller has them (O top, K left, P right, L bottom), Q E the shoulders and
 * Z C the triggers (Volume Up the left shoulder, Volume Down the left trigger; with root, the
 * red side key the right trigger), Space Start, Enter Select, Backspace
 * Home, F and H the stick presses, J B N M the right stick and the arrow keys (the trackpad's
 * left half, pushed) the left stick. The real controller plays it, and it can be written into
 * an emulator's own controller settings, where keys reach the emulator as they are.
 */
object FluxLayout {
    enum class Control {
        UP, DOWN, LEFT, RIGHT, A, B, X, Y, L1, R1, L2, R2, SELECT, START, HOME, L3, R3,
        LS_UP, LS_DOWN, LS_LEFT, LS_RIGHT, RS_UP, RS_DOWN, RS_LEFT, RS_RIGHT
    }

    val KEYS: List<Pair<Int, Control>> = listOf(
        KeyEvent.KEYCODE_W to Control.UP, KeyEvent.KEYCODE_S to Control.DOWN,
        KeyEvent.KEYCODE_A to Control.LEFT, KeyEvent.KEYCODE_D to Control.RIGHT,
        KeyEvent.KEYCODE_L to Control.A, KeyEvent.KEYCODE_P to Control.B,
        KeyEvent.KEYCODE_K to Control.X, KeyEvent.KEYCODE_O to Control.Y,
        KeyEvent.KEYCODE_Q to Control.L1, KeyEvent.KEYCODE_E to Control.R1,
        KeyEvent.KEYCODE_Z to Control.L2, KeyEvent.KEYCODE_C to Control.R2,
        KeyEvent.KEYCODE_VOLUME_UP to Control.L1, KeyEvent.KEYCODE_VOLUME_DOWN to Control.L2,
        KeyEvent.KEYCODE_ENTER to Control.SELECT, KeyEvent.KEYCODE_SPACE to Control.START,
        KeyEvent.KEYCODE_DEL to Control.HOME,
        KeyEvent.KEYCODE_F to Control.L3, KeyEvent.KEYCODE_H to Control.R3,
        // The left stick on R T Y and G (keys the keyboard really has: an emulator only takes a
        // device's own keys, and the Titan's keyboard has no arrows), pressed by the trackpad
        KeyEvent.KEYCODE_T to Control.LS_UP, KeyEvent.KEYCODE_G to Control.LS_DOWN,
        KeyEvent.KEYCODE_R to Control.LS_LEFT, KeyEvent.KEYCODE_Y to Control.LS_RIGHT,
        KeyEvent.KEYCODE_J to Control.RS_UP, KeyEvent.KEYCODE_N to Control.RS_DOWN,
        KeyEvent.KEYCODE_B to Control.RS_LEFT, KeyEvent.KEYCODE_M to Control.RS_RIGHT
    )

    /** The left stick's keys in the trackpad's order: up, left, down, right. */
    val LEFT_STICK = intArrayOf(KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_Y)

    /** The right stick's keys: up, left, right, down. */
    val RIGHT_STICK = intArrayOf(KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_N)

    /** The real controller's button for each control that is one (see [VirtualGamepad.button]). */
    val PAD_BUTTONS: Map<Int, Int> = KEYS.mapNotNull { (key, control) ->
        when (control) {
            Control.A -> 0; Control.B -> 1; Control.X -> 3; Control.Y -> 4
            Control.L1 -> 6; Control.R1 -> 7; Control.L2 -> 8; Control.R2 -> 9
            Control.SELECT -> 10; Control.START -> 11; Control.HOME -> 12; Control.L3 -> 13; Control.R3 -> 14
            else -> null
        }?.let { key to it }
    }.toMap()

    private fun keys(vararg controls: Control) = KEYS.filter { it.second in controls }.map { it.first }

    /** A key's name as Dolphin writes it, which also reads well. */
    internal fun keyName(code: Int): String = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
        KeyEvent.KEYCODE_DPAD_UP -> "Up"; KeyEvent.KEYCODE_DPAD_DOWN -> "Down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "Left"; KeyEvent.KEYCODE_DPAD_RIGHT -> "Right"
        KeyEvent.KEYCODE_VOLUME_UP -> "Volume Up"; KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume Down"
        KeyEvent.KEYCODE_SPACE -> "Space"; KeyEvent.KEYCODE_ENTER -> "Enter"; KeyEvent.KEYCODE_DEL -> "Backspace"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }

    // ---- Dolphin: GameCube controller and Wii Remote with Nunchuk ----

    /** The GameCube's L and R on the volume keys (and Q E), its Z on Z (and C). */
    private val GAMECUBE = listOf(
        "Buttons/A" to keys(Control.A), "Buttons/B" to keys(Control.B),
        "Buttons/X" to keys(Control.X), "Buttons/Y" to keys(Control.Y),
        "Buttons/Z" to listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_C), "Buttons/Start" to keys(Control.START),
        "Triggers/L" to listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_Q),
        "Triggers/R" to listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_E),
        "D-Pad/Up" to keys(Control.UP), "D-Pad/Down" to keys(Control.DOWN),
        "D-Pad/Left" to keys(Control.LEFT), "D-Pad/Right" to keys(Control.RIGHT),
        "Main Stick/Up" to keys(Control.LS_UP), "Main Stick/Down" to keys(Control.LS_DOWN),
        "Main Stick/Left" to keys(Control.LS_LEFT), "Main Stick/Right" to keys(Control.LS_RIGHT),
        "C-Stick/Up" to keys(Control.RS_UP), "C-Stick/Down" to keys(Control.RS_DOWN),
        "C-Stick/Left" to keys(Control.RS_LEFT), "C-Stick/Right" to keys(Control.RS_RIGHT)
    )

    private val WII = listOf(
        "Buttons/A" to keys(Control.A), "Buttons/B" to keys(Control.B, Control.R2),
        "Buttons/1" to keys(Control.X), "Buttons/2" to keys(Control.Y),
        "Buttons/-" to keys(Control.SELECT), "Buttons/+" to keys(Control.START), "Buttons/Home" to keys(Control.HOME),
        "D-Pad/Up" to keys(Control.UP), "D-Pad/Down" to keys(Control.DOWN),
        "D-Pad/Left" to keys(Control.LEFT), "D-Pad/Right" to keys(Control.RIGHT),
        "IR/Up" to keys(Control.RS_UP), "IR/Down" to keys(Control.RS_DOWN),
        "IR/Left" to keys(Control.RS_LEFT), "IR/Right" to keys(Control.RS_RIGHT),
        "Shake/X" to keys(Control.R1), "Shake/Y" to keys(Control.R1), "Shake/Z" to keys(Control.R1),
        "Nunchuk/Buttons/C" to keys(Control.L1), "Nunchuk/Buttons/Z" to keys(Control.L2),
        "Nunchuk/Stick/Up" to keys(Control.LS_UP), "Nunchuk/Stick/Down" to keys(Control.LS_DOWN),
        "Nunchuk/Stick/Left" to keys(Control.LS_LEFT), "Nunchuk/Stick/Right" to keys(Control.LS_RIGHT)
    )

    private val CLASSIC = listOf(
        "Classic/Buttons/A" to keys(Control.A), "Classic/Buttons/B" to keys(Control.B),
        "Classic/Buttons/X" to keys(Control.X), "Classic/Buttons/Y" to keys(Control.Y),
        "Classic/Triggers/L" to keys(Control.L1), "Classic/Triggers/R" to keys(Control.R1),
        "Classic/Buttons/ZL" to keys(Control.L2), "Classic/Buttons/ZR" to keys(Control.R2),
        "Classic/Buttons/-" to keys(Control.SELECT), "Classic/Buttons/+" to keys(Control.START),
        "Classic/Buttons/Home" to keys(Control.HOME),
        "Classic/D-Pad/Up" to keys(Control.UP), "Classic/D-Pad/Down" to keys(Control.DOWN),
        "Classic/D-Pad/Left" to keys(Control.LEFT), "Classic/D-Pad/Right" to keys(Control.RIGHT),
        "Classic/Left Stick/Up" to keys(Control.LS_UP), "Classic/Left Stick/Down" to keys(Control.LS_DOWN),
        "Classic/Left Stick/Left" to keys(Control.LS_LEFT), "Classic/Left Stick/Right" to keys(Control.LS_RIGHT),
        "Classic/Right Stick/Up" to keys(Control.RS_UP), "Classic/Right Stick/Down" to keys(Control.RS_DOWN),
        "Classic/Right Stick/Left" to keys(Control.RS_LEFT), "Classic/Right Stick/Right" to keys(Control.RS_RIGHT)
    )

    /** Held sideways, as for NES-style games: 2 (right-hand) on A, 1 on X and B, the remote's A and B on Y and R2. */
    private val SIDEWAYS = listOf(
        "Buttons/2" to keys(Control.A), "Buttons/1" to keys(Control.X, Control.B),
        "Buttons/A" to keys(Control.Y), "Buttons/B" to keys(Control.R2),
        "Buttons/-" to keys(Control.SELECT), "Buttons/+" to keys(Control.START), "Buttons/Home" to keys(Control.HOME),
        "D-Pad/Up" to keys(Control.UP), "D-Pad/Down" to keys(Control.DOWN),
        "D-Pad/Left" to keys(Control.LEFT), "D-Pad/Right" to keys(Control.RIGHT),
        "Shake/X" to keys(Control.R1), "Shake/Y" to keys(Control.R1), "Shake/Z" to keys(Control.R1)
    )

    /**
     * The controllers a Wii game can be given, each a Dolphin profile of its own: a Wii Remote
     * (with Nunchuk, Classic Controller, or sideways), or a GameCube controller, for the Wii
     * games that take one (Mario Kart Wii).
     */
    enum class WiiPad(val profile: String) {
        NUNCHUK("Flux Keyboard"), CLASSIC("Flux Keyboard (Classic Controller)"), SIDEWAYS("Flux Keyboard (sideways)"),
        GAMECUBE("Flux Keyboard")
    }

    private fun dolphinLines(controls: List<Pair<String, List<Int>>>) =
        controls.map { (control, keys) -> "$control = " + keys.joinToString(" | ") { "`${keyName(it)}`" } }

    internal fun dolphinGameCube(keyboard: String): List<String> = listOf("Device = Android/0/$keyboard") + dolphinLines(GAMECUBE)

    internal fun dolphinWii(keyboard: String, pad: WiiPad = WiiPad.NUNCHUK): List<String> =
        listOf("Device = Android/0/$keyboard", "Source = 1") + when (pad) {
            WiiPad.NUNCHUK, WiiPad.GAMECUBE -> listOf("Extension = Nunchuk") + dolphinLines(WII)
            WiiPad.CLASSIC -> listOf("Extension = Classic") + dolphinLines(CLASSIC)
            WiiPad.SIDEWAYS -> listOf("Extension = None", "Options/Sideways Wiimote = True") + dolphinLines(SIDEWAYS)
        }

    /** [ini] with [key] in [section] set to [value] (null: taken out), the rest kept. */
    internal fun withKey(ini: String, section: String, key: String, value: String?): String {
        val pattern = Regex("(?ms)^\\[" + Regex.escape(section) + "\\]\\R(.*?)(?=^\\[|\\z)")
        val body = pattern.find(ini)?.groupValues?.get(1).orEmpty().lines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("$key ") && !it.trimStart().startsWith("$key=") }
        val lines = body + listOfNotNull(value?.let { "$key = $it" })
        if (lines.isEmpty()) return pattern.replace(ini, "").trimEnd().let { if (it.isEmpty()) "" else "$it\n" }
        return withSection(ini, section, lines)
    }

    /** [ini] with section [name] holding [lines] (added when it has none). */
    internal fun withSection(ini: String, name: String, lines: List<String>): String {
        val section = "[$name]\n" + lines.joinToString("\n") + "\n"
        val pattern = Regex("(?ms)^\\[" + Regex.escape(name) + "\\]\\R.*?(?=^\\[|\\z)")
        return if (pattern.containsMatchIn(ini)) pattern.replace(ini) { section + "\n" }.trimEnd() + "\n"
        else (ini.trimEnd().let { if (it.isEmpty()) "" else "$it\n\n" }) + section
    }

    // ---- PPSSPP: its controls (PSP/SYSTEM/controls.ini); device 1 is the keyboard ----

    private val PSP = listOf(
        "Cross" to keys(Control.A), "Circle" to keys(Control.B), "Square" to keys(Control.X), "Triangle" to keys(Control.Y),
        "L" to keys(Control.L1, Control.L2), "R" to keys(Control.R1, Control.R2),
        "Start" to keys(Control.START), "Select" to keys(Control.SELECT),
        "Up" to keys(Control.UP), "Down" to keys(Control.DOWN), "Left" to keys(Control.LEFT), "Right" to keys(Control.RIGHT),
        "An.Up" to keys(Control.LS_UP), "An.Down" to keys(Control.LS_DOWN),
        "An.Left" to keys(Control.LS_LEFT), "An.Right" to keys(Control.LS_RIGHT),
        "RightAn.Up" to keys(Control.RS_UP), "RightAn.Down" to keys(Control.RS_DOWN),
        "RightAn.Left" to keys(Control.RS_LEFT), "RightAn.Right" to keys(Control.RS_RIGHT)
    )

    internal fun pspMapping(): List<String> = PSP.map { (button, keys) -> "$button = " + keys.joinToString(",") { "1-$it" } }

    /** The keys to bind by hand, for an emulator without a file to write: "A: L" lines. */
    fun bindList(): String = listOf(
        "A" to Control.A, "B" to Control.B, "X" to Control.X, "Y" to Control.Y,
        "L" to Control.L1, "R" to Control.R1, "ZL" to Control.L2, "ZR" to Control.R2,
        "Start (+)" to Control.START, "Select (-)" to Control.SELECT, "Home" to Control.HOME,
        "D-pad" to null, "Left stick" to null, "Right stick" to null, "L3" to Control.L3, "R3" to Control.R3
    ).joinToString("\n") { (label, control) ->
        label + ": " + when (label) {
            "D-pad" -> "W A S D"
            "Left stick" -> "↑ ← ↓ → (trackpad, left half)"
            "Right stick" -> "J B N M"
            else -> keys(control!!).joinToString(" / ") { keyName(it) }
        }
    }

    // ---- Writing them ----

    /** What writing did: done, or nothing found to write into (the emulator not opened yet), or failed. */
    enum class Result { DONE, NOT_FOUND, FAILED, BY_HAND }

    /** The phone's keyboard, as Android (and so Dolphin) names it. */
    private fun keyboardName(): String? = InputDevice.getDeviceIds().toList().firstNotNullOfOrNull { id ->
        InputDevice.getDevice(id)?.takeIf {
            !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }?.name
    }

    internal fun write(path: String, content: String): Boolean {
        val data = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        // The file as it was before Flux Keyboard first changed it, kept to put back on a reset
        // (or a mark that there was none, so a reset takes it away)
        val keep = "p=\"$path\"; [ -e \"\$p$BACKUP\" ] || [ -e \"\$p$BACKUP_NONE\" ] || " +
            "{ if [ -e \"\$p\" ]; then cp \"\$p\" \"\$p$BACKUP\"; else touch \"\$p$BACKUP_NONE\"; fi; }"
        return AdbShell.run("mkdir -p \"${path.substringBeforeLast('/')}\" && $keep && echo '$data' | base64 -d > \"$path\"", 5_000) != null
    }

    private const val BACKUP = ".before-flux"
    private const val BACKUP_NONE = ".before-flux.none"

    /**
     * Puts [pkg]'s settings back as they were before Flux Keyboard changed them (controls,
     * graphics, per-game controllers): each file restored, the ones it added taken away. Blocking,
     * through the shell, the emulator stopped first.
     */
    fun reset(pkg: String): Result {
        if (!AdbShell.available()) return Result.FAILED
        val dir = when (GameApps.emulator(pkg)) {
            "Dolphin" -> dolphinDir(pkg)
            "PPSSPP" -> ppssppDir(pkg)
            else -> return Result.BY_HAND
        } ?: return Result.NOT_FOUND
        AdbShell.run("am force-stop $pkg", 3_000)
        val script = "find \"$dir\" -name '*$BACKUP' -o -name '*$BACKUP_NONE' | while read -r b; do " +
            "case \"\$b\" in *$BACKUP_NONE) rm -f \"\${b%$BACKUP_NONE}\" \"\$b\";; *) mv -f \"\$b\" \"\${b%$BACKUP}\";; esac; done; echo ok"
        return if (AdbShell.run(script, 10_000)?.contains("ok") == true) Result.DONE else Result.FAILED
    }

    internal fun read(path: String): String = AdbShell.run("cat \"$path\" 2>/dev/null; true", 3_000).orEmpty()

    /** Writes the layout into [pkg]'s controller settings (blocking: through the shell, the emulator stopped first). */
    fun apply(pkg: String): Result {
        if (!AdbShell.available()) return Result.FAILED
        return when (GameApps.emulator(pkg)) {
            "Dolphin" -> dolphin(pkg)
            "PPSSPP" -> ppsspp(pkg)
            "Azahar", "Eden" -> Result.BY_HAND
            else -> Result.FAILED
        }
    }

    private fun dolphin(pkg: String): Result {
        val keyboard = keyboardName() ?: return Result.FAILED
        val dir = AdbShell.run(
            "for d in \"/storage/emulated/0/Android/data/$pkg/files\" /storage/emulated/0/dolphin-emu; do " +
                "[ -d \"\$d/Config\" ] && { echo \"\$d\"; break; }; done; true", 3_000
        )?.trim()?.ifEmpty { null } ?: return Result.NOT_FOUND
        // It writes its settings back as it closes: stopped first
        AdbShell.run("am force-stop $pkg", 3_000)
        val gc = dolphinGameCube(keyboard)
        val wii = dolphinWii(keyboard)
        val ok = write("$dir/Config/GCPadNew.ini", withSection(read("$dir/Config/GCPadNew.ini"), "GCPad1", gc)) &&
            write("$dir/Config/WiimoteNew.ini", withSection(read("$dir/Config/WiimoteNew.ini"), "Wiimote1", wii)) &&
            // Also as profiles, to load again after changing them
            write("$dir/Config/Profiles/GCPad/Flux Keyboard.ini", withSection("", "Profile", gc)) &&
            WiiPad.entries.all { pad ->
                write("$dir/Config/Profiles/Wiimote/${pad.profile}.ini", withSection("", "Profile", dolphinWii(keyboard, pad)))
            }
        dolphinOverlay(dir)
        return if (ok) Result.DONE else Result.FAILED
    }

    /**
     * Dolphin's on-screen controls as gaming mode plays them: the stick centred where it's
     * touched (so a stick placed near it still pushes every way) and the controls drawn
     * invisible (they still take touches). Only what isn't set yet: your own choices are kept.
     */
    internal val DOLPHIN_OVERLAY = listOf("JoystickRelCenter" to "True", "ControlOpacity" to "0")

    /** Writes [DOLPHIN_OVERLAY] into Dolphin's settings at [dir] (blocking; Dolphin not running). */
    fun dolphinOverlay(dir: String) {
        val path = "$dir/Config/Dolphin.ini"
        val ini = read(path)
        val updated = dolphinOverlayIni(ini)
        if (updated != ini) write(path, updated)
    }

    internal fun dolphinOverlayIni(ini: String): String {
        val section = Regex("(?ms)^\\[Android\\]\\R(.*?)(?=^\\[|\\z)").find(ini)?.groupValues?.get(1).orEmpty()
        return DOLPHIN_OVERLAY.fold(ini) { text, (key, value) ->
            if (Regex("(?m)^\\s*" + Regex.escape(key) + "\\s*=").containsMatchIn(section)) text else withKey(text, "Android", key, value)
        }
    }

    internal fun dolphinDir(pkg: String): String? = AdbShell.run(
        "for d in \"/storage/emulated/0/Android/data/$pkg/files\" /storage/emulated/0/dolphin-emu; do " +
            "[ -d \"\$d/Config\" ] && { echo \"\$d\"; break; }; done; true", 3_000
    )?.trim()?.ifEmpty { null }

    /**
     * One Wii game's controller: its own Dolphin settings (GameSettings/<game ID>.ini) load the
     * matching profile as it starts; a Wii Remote with Nunchuk is everyone's, so nothing is kept.
     */
    fun setWiiPad(pkg: String, gameId: String, pad: WiiPad): Result {
        val keyboard = keyboardName() ?: return Result.FAILED
        val dir = dolphinDir(pkg) ?: return Result.NOT_FOUND
        val written = if (pad == WiiPad.GAMECUBE) {
            write("$dir/Config/Profiles/GCPad/${pad.profile}.ini", withSection("", "Profile", dolphinGameCube(keyboard)))
        } else {
            write("$dir/Config/Profiles/Wiimote/${pad.profile}.ini", withSection("", "Profile", dolphinWii(keyboard, pad)))
        }
        if (!written) return Result.FAILED
        val file = "$dir/GameSettings/$gameId.ini"
        return if (write(file, wiiPadSettings(read(file), pad))) Result.DONE else Result.FAILED
    }

    /**
     * A Wii game's [Controls]: its Wii Remote's profile; or for a GameCube controller, controller
     * port 1 plugged in with its profile and Wii Remote 1 taken away (0 is none, 6 a GameCube
     * controller). A Wii Remote with Nunchuk is everyone's: nothing kept.
     */
    internal fun wiiPadSettings(ini: String, pad: WiiPad): String {
        val gc = pad == WiiPad.GAMECUBE
        var out = withKey(ini, "Controls", "WiimoteProfile1", pad.profile.takeIf { pad == WiiPad.CLASSIC || pad == WiiPad.SIDEWAYS })
        out = withKey(out, "Controls", "PadType0", if (gc) "6" else null)
        out = withKey(out, "Controls", "PadProfile1", if (gc) pad.profile else null)
        return withKey(out, "Controls", "WiimoteSource0", if (gc) "0" else null)
    }

    internal fun ppssppDir(pkg: String): String? = AdbShell.run(
        "for d in /storage/emulated/0/PSP \"/storage/emulated/0/Android/data/$pkg/files/PSP\"; do " +
            "[ -d \"\$d/SYSTEM\" ] && { echo \"\$d\"; break; }; done; true", 3_000
    )?.trim()?.ifEmpty { null }

    private fun ppsspp(pkg: String): Result {
        val dir = ppssppDir(pkg) ?: return Result.NOT_FOUND
        AdbShell.run("am force-stop $pkg", 3_000)
        val file = "$dir/SYSTEM/controls.ini"
        return if (write(file, withSection(read(file), "ControlMapping", pspMapping()))) Result.DONE else Result.FAILED
    }
}
