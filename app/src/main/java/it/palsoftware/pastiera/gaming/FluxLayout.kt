package it.palsoftware.pastiera.gaming

import android.view.InputDevice
import android.view.KeyEvent
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Flux Keyboard's controller layout, the same everywhere. The left hand: W A Z D the left stick
 * around S, its click; with SYM held they're the d-pad, with right Shift the right stick. The
 * right hand: I J M L the face buttons around K, the right stick's click (I top, J left, M
 * bottom, L right, as a controller has them); with Alt held they're the right stick. Q and E
 * the shoulders, Volume Up and Down the triggers (with Alt, pressed fully without their click, for
 * a GameCube controller), Space Start (Shift+Space Select, Alt+Space the Xbox/Home button). Any
 * key can be changed, alone or with a modifier held ([overrides]). Gaming mode reads the keys and modifiers ([controlFor]) and sends each
 * control as one key of its own ([KEYS]), the one written into an emulator's controller settings.
 */
object FluxLayout {
    enum class Control {
        UP, DOWN, LEFT, RIGHT, A, B, X, Y, L1, R1, L2, R2, SELECT, START, HOME, L3, R3,
        LS_UP, LS_DOWN, LS_LEFT, LS_RIGHT, RS_UP, RS_DOWN, RS_LEFT, RS_RIGHT,
        /** The triggers pressed all the way without their click (a GameCube controller's). */
        L2_ANALOG, R2_ANALOG
    }

    /**
     * The key each control is sent as (and so written into emulators as): the keys you press for
     * it where they're its own, keys of their own for the rest.
     */
    val KEYS: List<Pair<Int, Control>> = listOf(
        KeyEvent.KEYCODE_W to Control.LS_UP, KeyEvent.KEYCODE_Z to Control.LS_DOWN,
        KeyEvent.KEYCODE_A to Control.LS_LEFT, KeyEvent.KEYCODE_D to Control.LS_RIGHT,
        KeyEvent.KEYCODE_S to Control.L3,
        KeyEvent.KEYCODE_I to Control.Y, KeyEvent.KEYCODE_J to Control.X,
        KeyEvent.KEYCODE_M to Control.A, KeyEvent.KEYCODE_L to Control.B,
        KeyEvent.KEYCODE_K to Control.R3,
        KeyEvent.KEYCODE_Q to Control.L1, KeyEvent.KEYCODE_E to Control.R1,
        KeyEvent.KEYCODE_U to Control.L2, KeyEvent.KEYCODE_O to Control.R2,
        KeyEvent.KEYCODE_SPACE to Control.START, KeyEvent.KEYCODE_ENTER to Control.SELECT,
        KeyEvent.KEYCODE_DEL to Control.HOME,
        KeyEvent.KEYCODE_DPAD_UP to Control.UP, KeyEvent.KEYCODE_DPAD_DOWN to Control.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to Control.LEFT, KeyEvent.KEYCODE_DPAD_RIGHT to Control.RIGHT,
        KeyEvent.KEYCODE_T to Control.RS_UP, KeyEvent.KEYCODE_V to Control.RS_DOWN,
        KeyEvent.KEYCODE_F to Control.RS_LEFT, KeyEvent.KEYCODE_G to Control.RS_RIGHT,
        KeyEvent.KEYCODE_C to Control.L2_ANALOG, KeyEvent.KEYCODE_N to Control.R2_ANALOG
    )

    /** The key a control is sent as. */
    fun keyOf(control: Control): Int = KEYS.first { it.second == control }.first

    /** What the modifiers held make of the keys. */
    data class Modifiers(val sym: Boolean = false, val rightShift: Boolean = false, val shift: Boolean = false, val alt: Boolean = false)

    /** The modifier keys: held, not sent to the game. */
    val MODIFIER_KEYS = setOf(
        KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT
    )

    /** The modifier held that a key is read with: the first of SYM, right Shift, Alt, Shift. */
    enum class Layer { NONE, SYM, RIGHT_SHIFT, ALT, SHIFT }

    fun layerOf(mods: Modifiers): Layer = when {
        mods.sym -> Layer.SYM; mods.rightShift -> Layer.RIGHT_SHIFT; mods.alt -> Layer.ALT; mods.shift -> Layer.SHIFT
        else -> Layer.NONE
    }

    fun modifiersOf(layer: Layer): Modifiers = when (layer) {
        Layer.NONE -> Modifiers(); Layer.SYM -> Modifiers(sym = true)
        Layer.RIGHT_SHIFT -> Modifiers(rightShift = true, shift = true)
        Layer.ALT -> Modifiers(alt = true); Layer.SHIFT -> Modifiers(shift = true)
    }

    /**
     * Keys changed from the layout, with the modifier they're pressed with: the control each
     * makes, or null for none (the key goes to the game as it is).
     */
    @Volatile var overrides: Map<Pair<Layer, Int>, Control?> = emptyMap()
        private set

    private const val KEY_OVERRIDES = "game_layout_overrides"

    fun loadOverrides(context: android.content.Context) {
        val text = it.palsoftware.pastiera.SettingsManager.getPreferences(context).getString(KEY_OVERRIDES, "").orEmpty()
        overrides = parseOverrides(text)
    }

    fun setOverrides(context: android.content.Context, changed: Map<Pair<Layer, Int>, Control?>) {
        overrides = changed
        it.palsoftware.pastiera.SettingsManager.getPreferences(context).edit().putString(KEY_OVERRIDES, formatOverrides(changed)).apply()
    }

    /** "ALT:41=A,NONE:45=-": layer, key, control (or - for none). */
    internal fun formatOverrides(map: Map<Pair<Layer, Int>, Control?>): String =
        map.entries.joinToString(",") { (key, control) -> "${key.first}:${key.second}=${control?.name ?: "-"}" }

    internal fun parseOverrides(text: String): Map<Pair<Layer, Int>, Control?> = text.split(',').mapNotNull { entry ->
        val layer = runCatching { Layer.valueOf(entry.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
        val code = entry.substringAfter(':').substringBefore('=').toIntOrNull() ?: return@mapNotNull null
        val name = entry.substringAfter('=', "")
        if (name.isEmpty()) return@mapNotNull null
        val control = if (name == "-") null else runCatching { Control.valueOf(name) }.getOrNull() ?: return@mapNotNull null
        (layer to code) to control
    }.toMap()

    /** Keys changed for the game being played alone, on top of [overrides]. */
    @Volatile var gameOverrides: Map<Pair<Layer, Int>, Control?> = emptyMap()

    /** The control a key pressed makes with [mods] held, as changed (for the game being played too); null: not one of the layout's keys. */
    fun controlFor(code: Int, mods: Modifiers): Control? {
        val key = layerOf(mods) to code
        val game = gameOverrides
        return if (key in game) game[key] else layoutControlFor(code, mods)
    }

    /** The control a key makes with [mods] held, as Keyboard controls has it (no game's own keys). */
    fun layoutControlFor(code: Int, mods: Modifiers): Control? {
        val key = layerOf(mods) to code
        val changed = overrides
        return if (key in changed) changed[key] else defaultControlFor(code, mods)
    }

    /** One of the layout's keys with some modifier held (as changed in Keyboard controls): gaming mode reads it. */
    fun isLayoutKey(code: Int): Boolean = Layer.entries.any { controlFor(code, modifiersOf(it)) != null }

    /** The control a key makes with [mods] held, as the layout has it. */
    fun defaultControlFor(code: Int, mods: Modifiers): Control? = when (code) {
        KeyEvent.KEYCODE_W -> if (mods.sym) Control.UP else if (mods.rightShift) Control.RS_UP else Control.LS_UP
        KeyEvent.KEYCODE_Z -> if (mods.sym) Control.DOWN else if (mods.rightShift) Control.RS_DOWN else Control.LS_DOWN
        KeyEvent.KEYCODE_A -> if (mods.sym) Control.LEFT else if (mods.rightShift) Control.RS_LEFT else Control.LS_LEFT
        KeyEvent.KEYCODE_D -> if (mods.sym) Control.RIGHT else if (mods.rightShift) Control.RS_RIGHT else Control.LS_RIGHT
        KeyEvent.KEYCODE_S -> if (mods.rightShift) Control.R3 else Control.L3
        KeyEvent.KEYCODE_I -> if (mods.alt) Control.RS_UP else Control.Y
        KeyEvent.KEYCODE_J -> if (mods.alt) Control.RS_LEFT else Control.X
        KeyEvent.KEYCODE_M -> if (mods.alt) Control.RS_DOWN else Control.A
        KeyEvent.KEYCODE_L -> if (mods.alt) Control.RS_RIGHT else Control.B
        KeyEvent.KEYCODE_K -> Control.R3
        KeyEvent.KEYCODE_Q -> Control.L1
        KeyEvent.KEYCODE_E -> Control.R1
        // As the phone is held sideways: Volume Down on the left, Volume Up on the right
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (mods.alt) Control.L2_ANALOG else Control.L2
        KeyEvent.KEYCODE_VOLUME_UP -> if (mods.alt) Control.R2_ANALOG else Control.R2
        KeyEvent.KEYCODE_SPACE -> if (mods.alt) Control.HOME else if (mods.shift) Control.SELECT else Control.START
        else -> null
    }

    /** The stick directions: left stick (up, down, left, right), then the right's. */
    val STICK_CONTROLS = listOf(
        Control.LS_UP, Control.LS_DOWN, Control.LS_LEFT, Control.LS_RIGHT,
        Control.RS_UP, Control.RS_DOWN, Control.RS_LEFT, Control.RS_RIGHT
    )

    /** A controller's own key for each button, for games that take a controller. */
    val GAMEPAD_KEYS: Map<Control, Int> = mapOf(
        Control.A to KeyEvent.KEYCODE_BUTTON_A, Control.B to KeyEvent.KEYCODE_BUTTON_B,
        Control.X to KeyEvent.KEYCODE_BUTTON_X, Control.Y to KeyEvent.KEYCODE_BUTTON_Y,
        Control.L1 to KeyEvent.KEYCODE_BUTTON_L1, Control.R1 to KeyEvent.KEYCODE_BUTTON_R1,
        Control.L2 to KeyEvent.KEYCODE_BUTTON_L2, Control.R2 to KeyEvent.KEYCODE_BUTTON_R2,
        Control.L2_ANALOG to KeyEvent.KEYCODE_BUTTON_L2, Control.R2_ANALOG to KeyEvent.KEYCODE_BUTTON_R2,
        Control.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT, Control.START to KeyEvent.KEYCODE_BUTTON_START,
        Control.HOME to KeyEvent.KEYCODE_BUTTON_MODE,
        Control.L3 to KeyEvent.KEYCODE_BUTTON_THUMBL, Control.R3 to KeyEvent.KEYCODE_BUTTON_THUMBR,
        Control.UP to KeyEvent.KEYCODE_DPAD_UP, Control.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        Control.LEFT to KeyEvent.KEYCODE_DPAD_LEFT, Control.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT
    )

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

    /**
     * The GameCube's L and R on the triggers (the volume keys), clicked and pressed all the way;
     * with Alt, pressed all the way without the click. Its Z on the right shoulder (E).
     */
    private val GAMECUBE = listOf(
        "Buttons/A" to ctl(Control.A), "Buttons/B" to ctl(Control.B),
        "Buttons/X" to ctl(Control.X), "Buttons/Y" to ctl(Control.Y),
        "Buttons/Z" to ctl(Control.R1), "Buttons/Start" to ctl(Control.START),
        "Triggers/L" to ctl(Control.L2), "Triggers/R" to ctl(Control.R2),
        "Triggers/L-Analog" to ctl(Control.L2, Control.L2_ANALOG), "Triggers/R-Analog" to ctl(Control.R2, Control.R2_ANALOG),
        "D-Pad/Up" to ctl(Control.UP), "D-Pad/Down" to ctl(Control.DOWN),
        "D-Pad/Left" to ctl(Control.LEFT), "D-Pad/Right" to ctl(Control.RIGHT),
        "Main Stick/Up" to ctl(Control.LS_UP), "Main Stick/Down" to ctl(Control.LS_DOWN),
        "Main Stick/Left" to ctl(Control.LS_LEFT), "Main Stick/Right" to ctl(Control.LS_RIGHT),
        "C-Stick/Up" to ctl(Control.RS_UP), "C-Stick/Down" to ctl(Control.RS_DOWN),
        "C-Stick/Left" to ctl(Control.RS_LEFT), "C-Stick/Right" to ctl(Control.RS_RIGHT)
    )

    private val WII = listOf(
        "Buttons/A" to ctl(Control.A), "Buttons/B" to ctl(Control.B, Control.R2),
        "Buttons/1" to ctl(Control.X), "Buttons/2" to ctl(Control.Y),
        "Buttons/-" to ctl(Control.SELECT), "Buttons/+" to ctl(Control.START), "Buttons/Home" to ctl(Control.HOME),
        "D-Pad/Up" to ctl(Control.UP), "D-Pad/Down" to ctl(Control.DOWN),
        "D-Pad/Left" to ctl(Control.LEFT), "D-Pad/Right" to ctl(Control.RIGHT),
        "IR/Up" to ctl(Control.RS_UP), "IR/Down" to ctl(Control.RS_DOWN),
        "IR/Left" to ctl(Control.RS_LEFT), "IR/Right" to ctl(Control.RS_RIGHT),
        "Shake/X" to ctl(Control.R1), "Shake/Y" to ctl(Control.R1), "Shake/Z" to ctl(Control.R1),
        "Nunchuk/Buttons/C" to ctl(Control.L1), "Nunchuk/Buttons/Z" to ctl(Control.L2),
        "Nunchuk/Stick/Up" to ctl(Control.LS_UP), "Nunchuk/Stick/Down" to ctl(Control.LS_DOWN),
        "Nunchuk/Stick/Left" to ctl(Control.LS_LEFT), "Nunchuk/Stick/Right" to ctl(Control.LS_RIGHT)
    )

    private val CLASSIC = listOf(
        "Classic/Buttons/A" to ctl(Control.A), "Classic/Buttons/B" to ctl(Control.B),
        "Classic/Buttons/X" to ctl(Control.X), "Classic/Buttons/Y" to ctl(Control.Y),
        "Classic/Triggers/L" to ctl(Control.L1), "Classic/Triggers/R" to ctl(Control.R1),
        "Classic/Buttons/ZL" to ctl(Control.L2), "Classic/Buttons/ZR" to ctl(Control.R2),
        "Classic/Buttons/-" to ctl(Control.SELECT), "Classic/Buttons/+" to ctl(Control.START),
        "Classic/Buttons/Home" to ctl(Control.HOME),
        "Classic/D-Pad/Up" to ctl(Control.UP), "Classic/D-Pad/Down" to ctl(Control.DOWN),
        "Classic/D-Pad/Left" to ctl(Control.LEFT), "Classic/D-Pad/Right" to ctl(Control.RIGHT),
        "Classic/Left Stick/Up" to ctl(Control.LS_UP), "Classic/Left Stick/Down" to ctl(Control.LS_DOWN),
        "Classic/Left Stick/Left" to ctl(Control.LS_LEFT), "Classic/Left Stick/Right" to ctl(Control.LS_RIGHT),
        "Classic/Right Stick/Up" to ctl(Control.RS_UP), "Classic/Right Stick/Down" to ctl(Control.RS_DOWN),
        "Classic/Right Stick/Left" to ctl(Control.RS_LEFT), "Classic/Right Stick/Right" to ctl(Control.RS_RIGHT)
    )

    /** Held sideways, as for NES-style games: 2 (right-hand) on A, 1 on X and B, the remote's A and B on Y and R2. */
    private val SIDEWAYS = listOf(
        "Buttons/2" to ctl(Control.A), "Buttons/1" to ctl(Control.X, Control.B),
        "Buttons/A" to ctl(Control.Y), "Buttons/B" to ctl(Control.R2),
        "Buttons/-" to ctl(Control.SELECT), "Buttons/+" to ctl(Control.START), "Buttons/Home" to ctl(Control.HOME),
        "D-Pad/Up" to ctl(Control.UP), "D-Pad/Down" to ctl(Control.DOWN),
        "D-Pad/Left" to ctl(Control.LEFT), "D-Pad/Right" to ctl(Control.RIGHT),
        "Shake/X" to ctl(Control.R1), "Shake/Y" to ctl(Control.R1), "Shake/Z" to ctl(Control.R1)
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


    private fun ctl(vararg controls: Control) = controls.toList()

    /** The keys gaming mode reads as the layout's (with a modifier or not). */
    private val READ_KEYS = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() + listOf(
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN
    )

    /** Each modifier layer as Dolphin reads it: the modifier keys held, as [layerOf] picks the layer. */
    private val LAYER_CONDITION = mapOf(
        Layer.NONE to "!`Sym` & !`Right Shift` & !`Left Alt` & !`Right Alt` & !`Left Shift`",
        Layer.SYM to "`Sym`",
        Layer.RIGHT_SHIFT to "`Right Shift` & !`Sym`",
        Layer.ALT to "(`Left Alt` | `Right Alt`) & !`Sym` & !`Right Shift`",
        Layer.SHIFT to "`Left Shift` & !`Sym` & !`Right Shift` & !`Left Alt` & !`Right Alt`"
    )

    /** A control with A and B (X and Y) the other way round, Nintendo's. */
    internal fun swapped(control: Control?): Control? = when (control) {
        Control.A -> Control.B; Control.B -> Control.A; Control.X -> Control.Y; Control.Y -> Control.X
        else -> control
    }

    /**
     * What presses [controls] as an expression Dolphin reads from the keyboard itself: each key
     * with the modifier layer it makes the control in (the layout, keys changed in Keyboard
     * controls, A and B swapped when [swap]). Dolphin only takes keys from the keyboard, not
     * ones sent for it, so every combination is its own.
     */
    internal fun dolphinExpression(
        controls: List<Control>, swap: Boolean, volume: List<String> = emptyList(),
        exclude: Set<DolphinPads.Binding> = emptySet()
    ): String = bindingsExpression(defaultBindings(controls, swap, exclude), volume)

    /** The keys (with their modifier) that press [controls], as the layout has them, but for [exclude]. */
    internal fun defaultBindings(controls: List<Control>, swap: Boolean, exclude: Set<DolphinPads.Binding> = emptySet()) =
        Layer.entries.flatMap { layer ->
            val mods = modifiersOf(layer)
            READ_KEYS.filter { key -> layoutControlFor(key, mods).let { if (swap) swapped(it) else it } in controls }
                .map { DolphinPads.Binding(layer, it) }
        }.filter { it !in exclude }

    /** Keys pressed with their modifier, as an expression Dolphin reads from the keyboard. */
    internal fun bindingsExpression(bindings: List<DolphinPads.Binding>, volume: List<String> = emptyList()): String =
        bindings.groupBy { it.layer }.entries.sortedBy { it.key.ordinal }.joinToString(" | ") { (layer, keys) ->
            "((" + LAYER_CONDITION.getValue(layer) + ") & (" + keys.joinToString(" | ") { dolphinKey(it.key, volume) } + "))"
        }

    /**
     * A key as Dolphin names it. The volume keys come from a device of their own (the phone's
     * side buttons), named with it ([volume]: "Android/0/<device>"), so Dolphin both reads them
     * and keeps them from changing the volume while it plays.
     */
    private fun dolphinKey(code: Int, volume: List<String>): String {
        val name = keyName(code)
        if ((code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) || volume.isEmpty()) return "`$name`"
        return "(" + (listOf("`$name`") + volume.map { "`$it:$name`" }).joinToString(" | ") + ")"
    }

    /**
     * Each control's line for Dolphin: the layout's keys, or a game's own ([remap], by
     * [DolphinPads.Input] id; a key it gives one control no longer presses another).
     */
    private fun dolphinLines(
        pad: WiiPad, controls: List<Pair<String, List<Control>>>, swap: Boolean, volume: List<String>,
        remap: Map<String, List<DolphinPads.Binding>> = emptyMap()
    ): List<String> {
        val taken = remap.values.flatten().toSet()
        val own = remap.flatMap { (id, keys) -> DolphinPads.keysOf(pad, id).map { it to keys } }.toMap()
        val lines = controls.map { (name, wanted) ->
            "$name = " + (own[name]?.let { bindingsExpression(it, volume) } ?: dolphinExpression(wanted, swap, volume, taken))
        }
        // A game's keys for what the layout leaves alone (tilting the Wii Remote, say)
        val known = controls.map { it.first }.toSet()
        return lines + own.filterKeys { it !in known }.map { (name, keys) -> "$name = " + bindingsExpression(keys, volume) }
    }

    /** The layout's controls for each of Dolphin's names on [pad] (none for what it leaves alone). */
    internal fun dolphinControls(pad: WiiPad): Map<String, List<Control>> = when (pad) {
        WiiPad.GAMECUBE -> GAMECUBE
        WiiPad.NUNCHUK -> WII
        WiiPad.CLASSIC -> CLASSIC
        WiiPad.SIDEWAYS -> SIDEWAYS
    }.toMap()

    internal fun dolphinGameCube(
        keyboard: String, swap: Boolean = false, volume: List<String> = emptyList(),
        remap: Map<String, List<DolphinPads.Binding>> = emptyMap()
    ): List<String> = listOf("Device = Android/0/$keyboard") + dolphinLines(WiiPad.GAMECUBE, GAMECUBE, swap, volume, remap)

    internal fun dolphinWii(
        keyboard: String, pad: WiiPad = WiiPad.NUNCHUK, swap: Boolean = false, volume: List<String> = emptyList(),
        remap: Map<String, List<DolphinPads.Binding>> = emptyMap()
    ): List<String> =
        listOf("Device = Android/0/$keyboard", "Source = 1") + when (pad) {
            WiiPad.NUNCHUK, WiiPad.GAMECUBE -> listOf("Extension = Nunchuk") + dolphinLines(WiiPad.NUNCHUK, WII, swap, volume, remap.takeIf { pad == WiiPad.NUNCHUK }.orEmpty())
            WiiPad.CLASSIC -> listOf("Extension = Classic") + dolphinLines(pad, CLASSIC, swap, volume, remap)
            WiiPad.SIDEWAYS -> listOf("Extension = None", "Options/Sideways Wiimote = True") + dolphinLines(pad, SIDEWAYS, swap, volume, remap)
        }

    private const val KEY_VOLUME_DEVICE = "game_volume_device"

    /** The device the volume keys were last pressed on, kept for writing Dolphin's controls. */
    fun volumeSeenFrom(context: android.content.Context, name: String?) {
        if (name.isNullOrBlank()) return
        val prefs = it.palsoftware.pastiera.SettingsManager.getPreferences(context)
        if (prefs.getString(KEY_VOLUME_DEVICE, null) == name) return
        prefs.edit().putString(KEY_VOLUME_DEVICE, name).apply()
        it.palsoftware.pastiera.FluxLog.w("FluxLayout", "volume keys come from \"$name\"")
    }

    /**
     * The devices besides the keyboard that have the volume keys, as Dolphin names them
     * ("Android/<n>/<name>", n counting devices of the same name): the one they were last
     * pressed on, and any other.
     */
    private fun volumeDevices(context: android.content.Context, keyboard: String): List<String> {
        val names = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && it.name != keyboard && it.hasKeys(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN).any { has -> has } }
            .map { it.name }
        val counted = names.groupingBy { it }.eachCount()
        val seen = it.palsoftware.pastiera.SettingsManager.getPreferences(context).getString(KEY_VOLUME_DEVICE, null)
            ?.takeIf { it != keyboard && it !in counted }
        return (counted.flatMap { (name, count) -> (0 until count).map { "Android/$it/$name" } } + listOfNotNull(seen?.let { "Android/0/$it" }))
            .distinct().sorted()
    }

    /** Whether [pkg]'s A and B (X and Y) are swapped: as its app's settings have it, else as suits it. */
    fun swapFor(context: android.content.Context, pkg: String): Boolean =
        GameProfiles.all(context).firstOrNull { !it.isGame && pkg in it.packages }?.swapsFace ?: GameApps.nintendo(pkg)

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

    /** PPSSPP's controls: each control's own key (gaming mode sends it, A and B swapped and keys changed as set). */
    internal fun pspMapping(): List<String> = PSP.map { (button, keys) -> "$button = " + keys.joinToString(",") { "1-$it" } }

    /** The keys to bind by hand, for an emulator without a file to write: "A: L" lines. */
    fun bindList(): String = listOf(
        "A" to "M", "B" to "L", "X" to "J", "Y" to "I",
        "L" to "Q", "R" to "E", "ZL" to "Volume Up", "ZR" to "Volume Down",
        "Start (+)" to "Space", "Select (-)" to "Shift + Space", "Home" to "Alt + Space",
        "D-pad" to "SYM + W A Z D", "Left stick" to "W A Z D", "Right stick" to "Alt + I J M L (or right Shift + W A Z D)",
        "L3" to "S", "R3" to "K"
    ).joinToString("\n") { (label, keys) -> "$label: $keys" }

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

    /** The layout's version: written again into emulators given an older one. */
    private const val LAYOUT_VERSION = 6

    /**
     * An emulator given Flux Keyboard's layout before it changed: given the current one, once
     * (blocking: through the shell, the emulator stopped). One never given it is left alone.
     */
    fun refreshIfOutdated(context: android.content.Context, pkg: String) {
        val prefs = it.palsoftware.pastiera.SettingsManager.getPreferences(context)
        val key = "flux_layout_version_$pkg"
        val state = layoutState(context, pkg)
        // Written already, with A and B and any changed keys as they're wanted
        if ((prefs.getInt(key, 0) == LAYOUT_VERSION && prefs.getString(KEY_STATE + pkg, null) == state) || !AdbShell.available()) return
        val given = when (GameApps.emulator(pkg)) {
            "Dolphin" -> dolphinDir(pkg)?.let { dir ->
                AdbShell.run("[ -f \"$dir/Config/Profiles/GCPad/Flux Keyboard.ini\" ] && echo yes; true", 3_000)?.contains("yes") == true
            } == true
            "PPSSPP" -> ppssppDir(pkg)?.let { dir -> read("$dir/SYSTEM/controls.ini").contains("Start = 1-${KeyEvent.KEYCODE_SPACE}") } == true
            else -> false
        }
        if (!given) return
        if (apply(context, pkg) != Result.DONE) return
        // The games' own profiles are built on the layout too
        if (GameApps.emulator(pkg) == "Dolphin") refreshGames(context, pkg)
        prefs.edit().putInt(key, LAYOUT_VERSION).putString(KEY_STATE + pkg, state).apply()
    }

    private const val KEY_STATE = "flux_layout_state_"

    /**
     * What a written layout depends on besides its version. Dolphin reads the keys itself, so
     * its controls hold A and B's way round, the keys changed in Keyboard controls and the
     * volume keys' device; PPSSPP gets the keys gaming mode sends, the same whatever those are.
     */
    private fun layoutState(context: android.content.Context, pkg: String): String {
        if (GameApps.emulator(pkg) != "Dolphin") return ""
        loadOverrides(context)
        val volume = keyboardName()?.let { volumeDevices(context, it) }.orEmpty()
        return "${swapFor(context, pkg)}|${formatOverrides(overrides)}|${volume.joinToString(",")}"
    }

    /** Remembers [pkg] has the current layout (just written by hand, as [layoutState] has it). */
    fun markWritten(context: android.content.Context, pkg: String) =
        it.palsoftware.pastiera.SettingsManager.getPreferences(context).edit().putInt("flux_layout_version_$pkg", LAYOUT_VERSION)
            .putString(KEY_STATE + pkg, layoutState(context, pkg)).apply()

    /** Writes the layout into [pkg]'s controller settings (blocking: through the shell, the emulator stopped first). */
    fun apply(context: android.content.Context, pkg: String): Result {
        if (!AdbShell.available()) return Result.FAILED
        return when (GameApps.emulator(pkg)) {
            "Dolphin" -> dolphin(context, pkg)
            "PPSSPP" -> ppsspp(pkg)
            null -> Result.FAILED
            // The others: the keys bound in their own settings
            else -> Result.BY_HAND
        }
    }

    private fun dolphin(context: android.content.Context, pkg: String): Result {
        val keyboard = keyboardName() ?: return Result.FAILED
        loadOverrides(context)
        val swap = swapFor(context, pkg)
        val volume = volumeDevices(context, keyboard)
        val dir = AdbShell.run(
            "for d in \"/storage/emulated/0/Android/data/$pkg/files\" /storage/emulated/0/dolphin-emu; do " +
                "[ -d \"\$d/Config\" ] && { echo \"\$d\"; break; }; done; true", 3_000
        )?.trim()?.ifEmpty { null } ?: return Result.NOT_FOUND
        // It writes its settings back as it closes: stopped first
        AdbShell.run("am force-stop $pkg", 3_000)
        val gc = dolphinGameCube(keyboard, swap, volume)
        val wii = dolphinWii(keyboard, swap = swap, volume = volume)
        it.palsoftware.pastiera.FluxLog.w("FluxLayout", "Dolphin controls written: keyboard \"$keyboard\", volume keys from $volume; " +
            gc.firstOrNull { it.startsWith("Triggers/L = ") })
        val ok = write("$dir/Config/GCPadNew.ini", withSection(read("$dir/Config/GCPadNew.ini"), "GCPad1", gc)) &&
            write("$dir/Config/WiimoteNew.ini", withSection(read("$dir/Config/WiimoteNew.ini"), "Wiimote1", wii)) &&
            // Also as profiles, to load again after changing them
            write("$dir/Config/Profiles/GCPad/Flux Keyboard.ini", withSection("", "Profile", gc)) &&
            WiiPad.entries.all { pad ->
                write("$dir/Config/Profiles/Wiimote/${pad.profile}.ini", withSection("", "Profile", dolphinWii(keyboard, pad, swap, volume)))
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
     * One game's controller in Dolphin, set in its own Dolphin settings (GameSettings/<game ID>.ini)
     * to load as it starts: a Wii game's Wii Remote (with Nunchuk, Classic Controller or sideways)
     * or GameCube controller; with keys of the game's own ([remap]), a profile of its own. A Wii
     * Remote with Nunchuk, or a GameCube game, with no keys of its own is everyone's: nothing kept.
     */
    fun setGamePad(
        context: android.content.Context, pkg: String, gameId: String, wii: Boolean, pad: WiiPad,
        remap: Map<String, List<DolphinPads.Binding>> = emptyMap()
    ): Result {
        val keyboard = keyboardName() ?: return Result.FAILED
        val dir = dolphinDir(pkg) ?: return Result.NOT_FOUND
        loadOverrides(context)
        val gc = pad == WiiPad.GAMECUBE || !wii
        val own = remap.isNotEmpty()
        val profile = if (own) gameProfileName(pad, gameId) else pad.profile
        val folder = if (gc) "GCPad" else "Wiimote"
        // The game's own profile; a shared one comes with the layout, written here only if it's missing
        val path = "$dir/Config/Profiles/$folder/$profile.ini"
        if (own || read(path).isEmpty()) {
            val swap = swapFor(context, pkg)
            val volume = volumeDevices(context, keyboard)
            val lines = if (gc) dolphinGameCube(keyboard, swap, volume, remap) else dolphinWii(keyboard, pad, swap, volume, remap)
            if (!writeIfChanged(path, withSection("", "Profile", lines))) return Result.FAILED
        }
        // The game's other profiles (another controller chosen, or its keys reset) taken away
        removeGameProfiles(dir, gameId, keep = "$folder/$profile.ini".takeIf { own })
        val file = "$dir/GameSettings/$gameId.ini"
        val ini = read(file)
        val updated = if (wii) wiiPadSettings(ini, pad, profile) else withKey(ini, "Controls", "PadProfile1", profile.takeIf { own })
        return if (updated == ini || write(file, updated)) Result.DONE else Result.FAILED
    }

    /** A game's own profile in Dolphin's list: "Flux Keyboard RMCE01", "Flux Keyboard (sideways) RMCE01". */
    internal fun gameProfileName(pad: WiiPad, gameId: String) = "${pad.profile} $gameId"

    /** [gameId]'s own Flux Keyboard profiles but [keep] ("GCPad/<name>.ini"), with their backup marks. */
    private fun removeGameProfiles(dir: String, gameId: String, keep: String?) {
        AdbShell.run(
            "cd \"$dir/Config/Profiles\" 2>/dev/null || exit 0; for f in GCPad/'Flux Keyboard'*' $gameId.ini' Wiimote/'Flux Keyboard'*' $gameId.ini'; do " +
                "[ -e \"\$f\" ] || continue; [ \"\$f\" = \"${keep.orEmpty()}\" ] && continue; rm -f \"\$f\" \"\$f$BACKUP_NONE\"; " +
                "[ -e \"\$f$BACKUP\" ] && mv -f \"\$f$BACKUP\" \"\$f\"; done; true", 3_000
        )
    }

    /** [write], unless the file already holds [content] (nothing rewritten each time a game starts). */
    internal fun writeIfChanged(path: String, content: String): Boolean = read(path) == content || write(path, content)

    /**
     * The games of [pkg] with a controller or keys of their own given the current layout again
     * (it changed, or keys were changed in Keyboard controls). Blocking: through the shell.
     */
    fun refreshGames(context: android.content.Context, pkg: String) {
        GameProfiles.all(context).filter { it.isGame && pkg in it.packages && (it.wiiPad != null || it.padControls.isNotEmpty()) }.forEach { game ->
            runCatching {
                val path = game.launch?.let { GameFiles.gameFile(it) } ?: return@runCatching
                val id = GameIds.id(context, "Dolphin", path) ?: return@runCatching
                val wii = GameFiles.isWii(path)
                val pad = game.wiiPad?.let { name -> WiiPad.entries.firstOrNull { it.name == name } }
                    ?: if (wii) WiiPad.NUNCHUK else WiiPad.GAMECUBE
                setGamePad(context, pkg, id, wii, pad, game.padRemap(pad))
            }
        }
    }

    /**
     * A Wii game's [Controls]: its Wii Remote's profile; or for a GameCube controller, controller
     * port 1 plugged in with its profile and Wii Remote 1 taken away (0 is none, 6 a GameCube
     * controller). A Wii Remote with Nunchuk is everyone's unless it has its own [profile].
     */
    internal fun wiiPadSettings(ini: String, pad: WiiPad, profile: String = pad.profile): String {
        val gc = pad == WiiPad.GAMECUBE
        val remote = pad == WiiPad.CLASSIC || pad == WiiPad.SIDEWAYS || (pad == WiiPad.NUNCHUK && profile != pad.profile)
        var out = withKey(ini, "Controls", "WiimoteProfile1", profile.takeIf { remote })
        out = withKey(out, "Controls", "PadType0", if (gc) "6" else null)
        out = withKey(out, "Controls", "PadProfile1", if (gc) profile else null)
        out = withKey(out, "Controls", "WiimoteSource0", if (gc) "0" else null)
        // Also as Dolphin's newer settings name the GameCube port and the Wii Remote
        out = withKey(out, "Core", "SIDevice0", if (gc) "6" else null)
        return withKey(out, "Wiimote1", "Source", if (gc) "0" else null)
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
