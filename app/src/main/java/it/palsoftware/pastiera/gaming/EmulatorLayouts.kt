package it.palsoftware.pastiera.gaming

import android.content.Context
import it.palsoftware.pastiera.adb.AdbShell

/**
 * Where open-source emulators put their on-screen gamepad, read from their own code (and,
 * for PPSSPP, its saved settings), so a profile taps the right spots without placing them by
 * hand. Positions are shares of the upright screen; they can still be fine-tuned with the placer.
 */
object EmulatorLayouts {
    /** Buttons by what they are (Xbox places), and sticks (0 left, 1 right): centre and reach. */
    data class Layout(
        val buttons: Map<GameAction, Pair<Float, Float>>,
        val sticks: Map<Int, Triple<Float, Float, Float>>
    )

    private val DOLPHIN = setOf("org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr", "org.mm.jr")
    private val PPSSPP = setOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")

    /** An emulator whose on-screen gamepad Flux Keyboard knows. */
    fun name(packageName: String): String? = when (packageName) {
        in DOLPHIN -> "Dolphin"
        in PPSSPP -> "PPSSPP"
        else -> null
    }

    /** The layout for [packageName]; null when unknown or not readable yet (blocking: may read the shell). */
    fun layout(context: Context, packageName: String): Layout? {
        val (w, h) = uprightScreen(context)
        return when (packageName) {
            in DOLPHIN -> dolphin(w, h)
            in PPSSPP -> ppsspp(packageName, w, h, context.resources.displayMetrics.density)
            else -> null
        }
    }

    /** [profile] tapping [layout]: each key set to a button taps it, each stick drags there. */
    fun apply(profile: GameProfile, layout: Layout): GameProfile {
        val taps = profile.keys.mapNotNull { (key, action) -> layout.buttons[action]?.let { key to it } }.toMap()
        return profile.copy(
            taps = taps,
            stickZones = layout.sticks,
            leftHalf = if (0 in layout.sticks) TrackpadRole.LEFT_STICK else profile.leftHalf,
            rightHalf = if (1 in layout.sticks) TrackpadRole.RIGHT_STICK else profile.rightHalf
        )
    }

    private fun uprightScreen(context: Context): Pair<Float, Float> {
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.getRealSize(size)
        val w = minOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
        val h = maxOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1920f
        return w to h
    }

    /**
     * Dolphin's GameCube portrait layout (res/values/integers.xml, InputOverlay.kt): each
     * control's top-left corner in thousandths of the short and long sides, its size a share of
     * the short side (at the default control scale).
     */
    private fun dolphin(w: Float, h: Float): Layout {
        fun centre(x: Int, y: Int, scale: Float) = Pair(x / 1000f + scale / 2, y / 1000f + scale / 2 * w / h)
        val dpad = centre(44, 448, 0.2375f)
        val arm = 0.2375f / 3
        val main = centre(134, 687, 0.275f)
        val c = centre(622, 715, 0.275f)
        return Layout(
            buttons = mapOf(
                GameAction.BUTTON_A to centre(638, 534, 0.2f),
                GameAction.BUTTON_B to centre(560, 648, 0.125f),
                GameAction.BUTTON_X to centre(795, 519, 0.175f),
                GameAction.BUTTON_Y to centre(594, 463, 0.175f),
                GameAction.R1 to centre(357, 560, 0.225f),
                GameAction.L2 to centre(76, 582, 0.225f),
                GameAction.R2 to centre(739, 629, 0.225f),
                GameAction.START to centre(472, 789, 0.075f),
                GameAction.DPAD_UP to Pair(dpad.first, dpad.second - arm * w / h),
                GameAction.DPAD_DOWN to Pair(dpad.first, dpad.second + arm * w / h),
                GameAction.DPAD_LEFT to Pair(dpad.first - arm, dpad.second),
                GameAction.DPAD_RIGHT to Pair(dpad.first + arm, dpad.second)
            ),
            sticks = mapOf(
                0 to Triple(main.first, main.second, 0.275f / 2 * 0.8f),
                1 to Triple(c.first, c.second, 0.275f / 2 * 0.8f)
            )
        )
    }

    /**
     * PPSSPP's portrait touch controls, from ppsspp.ini's [TouchControls.Portrait] (it saves
     * where each control went the first time they showed). Face buttons sit 60 units around
     * their centre and the d-pad's arms 50 (UI/GamepadEmu), the units about a dp.
     */
    private fun ppsspp(pkg: String, w: Float, h: Float, density: Float): Layout? {
        val ini = AdbShell.run(
            "for f in /storage/emulated/0/PSP/SYSTEM/ppsspp.ini /storage/emulated/0/Android/data/$pkg/files/PSP/SYSTEM/ppsspp.ini " +
                "\$(find /storage/emulated/0 -maxdepth 4 -name ppsspp.ini 2>/dev/null); do [ -f \"\$f\" ] && { cat \"\$f\"; break; }; done",
            8_000
        ) ?: return null
        val section = ini.substringAfter("[TouchControls.Portrait]", "").substringBefore("\n[")
        if (section.isEmpty()) return null
        val values = section.lines().mapNotNull { line ->
            val (key, value) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            key.trim() to value.trim()
        }.toMap()
        fun at(prefix: String): Pair<Float, Float>? {
            val x = values["${prefix}X"]?.toFloatOrNull() ?: return null
            val y = values["${prefix}Y"]?.toFloatOrNull() ?: return null
            return if (x < 0f || y < 0f) null else x to y
        }
        val action = at("ActionButtonCenter") ?: return null
        val spacing = (values["ActionButtonSpacing2"]?.toFloatOrNull() ?: 1f) * 60f * density
        val sx = spacing / w
        val sy = spacing / h
        val buttons = mutableMapOf(
            GameAction.BUTTON_A to Pair(action.first, action.second + sy),
            GameAction.BUTTON_B to Pair(action.first + sx, action.second),
            GameAction.BUTTON_X to Pair(action.first - sx, action.second),
            GameAction.BUTTON_Y to Pair(action.first, action.second - sy)
        )
        at("DPad")?.let { d ->
            val arm = 40f * density
            buttons[GameAction.DPAD_UP] = Pair(d.first, d.second - arm / h)
            buttons[GameAction.DPAD_DOWN] = Pair(d.first, d.second + arm / h)
            buttons[GameAction.DPAD_LEFT] = Pair(d.first - arm / w, d.second)
            buttons[GameAction.DPAD_RIGHT] = Pair(d.first + arm / w, d.second)
        }
        at("StartKey")?.let { buttons[GameAction.START] = it }
        at("SelectKey")?.let { buttons[GameAction.SELECT] = it }
        at("LKey")?.let { buttons[GameAction.L1] = it }
        at("RKey")?.let { buttons[GameAction.R1] = it }
        val reach = 40f * density / w
        val sticks = mutableMapOf<Int, Triple<Float, Float, Float>>()
        if (values["ShowAnalogStick"] != "False") at("AnalogStick")?.let { sticks[0] = Triple(it.first, it.second, reach) }
        if (values["ShowRightAnalogStick"] == "True") at("RightAnalogStick")?.let { sticks[1] = Triple(it.first, it.second, reach) }
        return Layout(buttons, sticks)
    }
}
