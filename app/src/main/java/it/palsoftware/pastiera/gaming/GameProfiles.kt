package it.palsoftware.pastiera.gaming

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONArray
import org.json.JSONObject

/** What a key does in a game: a controller button, or a keyboard key sent in its place. */
enum class GameAction(val label: String, val keyCode: Int, val gamepad: Boolean) {
    DPAD_UP("D-pad up", KeyEvent.KEYCODE_DPAD_UP, true),
    DPAD_DOWN("D-pad down", KeyEvent.KEYCODE_DPAD_DOWN, true),
    DPAD_LEFT("D-pad left", KeyEvent.KEYCODE_DPAD_LEFT, true),
    DPAD_RIGHT("D-pad right", KeyEvent.KEYCODE_DPAD_RIGHT, true),
    BUTTON_A("A", KeyEvent.KEYCODE_BUTTON_A, true),
    BUTTON_B("B", KeyEvent.KEYCODE_BUTTON_B, true),
    BUTTON_X("X", KeyEvent.KEYCODE_BUTTON_X, true),
    BUTTON_Y("Y", KeyEvent.KEYCODE_BUTTON_Y, true),
    L1("L", KeyEvent.KEYCODE_BUTTON_L1, true),
    R1("R", KeyEvent.KEYCODE_BUTTON_R1, true),
    L2("ZL", KeyEvent.KEYCODE_BUTTON_L2, true),
    R2("ZR", KeyEvent.KEYCODE_BUTTON_R2, true),
    L3("Left stick press", KeyEvent.KEYCODE_BUTTON_THUMBL, true),
    R3("Right stick press", KeyEvent.KEYCODE_BUTTON_THUMBR, true),
    START("Start (+)", KeyEvent.KEYCODE_BUTTON_START, true),
    SELECT("Select (-)", KeyEvent.KEYCODE_BUTTON_SELECT, true),
    HOME("Home", KeyEvent.KEYCODE_BUTTON_MODE, true),
    KEY_W("W", KeyEvent.KEYCODE_W, false),
    KEY_A("A key", KeyEvent.KEYCODE_A, false),
    KEY_S("S", KeyEvent.KEYCODE_S, false),
    KEY_D("D", KeyEvent.KEYCODE_D, false),
    KEY_1("1", KeyEvent.KEYCODE_1, false),
    KEY_2("2", KeyEvent.KEYCODE_2, false),
    KEY_3("3", KeyEvent.KEYCODE_3, false),
    KEY_4("4", KeyEvent.KEYCODE_4, false),
    KEY_5("5", KeyEvent.KEYCODE_5, false),
    KEY_6("6", KeyEvent.KEYCODE_6, false),
    KEY_7("7", KeyEvent.KEYCODE_7, false),
    KEY_8("8", KeyEvent.KEYCODE_8, false),
    KEY_9("9", KeyEvent.KEYCODE_9, false),
    KEY_0("0", KeyEvent.KEYCODE_0, false),
    KEY_ESC("Esc", KeyEvent.KEYCODE_ESCAPE, false),
    KEY_TAB("Tab", KeyEvent.KEYCODE_TAB, false),
    KEY_SPACE("Space", KeyEvent.KEYCODE_SPACE, false),
    KEY_ENTER("Enter", KeyEvent.KEYCODE_ENTER, false),
    KEY_SHIFT("Shift", KeyEvent.KEYCODE_SHIFT_LEFT, false),
    KEY_CTRL("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, false),
    KEY_ALT("Alt", KeyEvent.KEYCODE_ALT_LEFT, false),
    KEY_F1("F1", KeyEvent.KEYCODE_F1, false),
    KEY_F2("F2", KeyEvent.KEYCODE_F2, false),
    KEY_F3("F3", KeyEvent.KEYCODE_F3, false),
    KEY_F4("F4", KeyEvent.KEYCODE_F4, false),
    MOUSE_LEFT("Mouse left click", -1, false),
    MOUSE_RIGHT("Mouse right click", -2, false);
}

/** What a half of the trackpad does in a game. */
enum class TrackpadRole(val label: String) {
    NONE("Nothing"),
    LEFT_STICK("Left stick"),
    RIGHT_STICK("Right stick"),
    MOUSE("Mouse")
}

enum class GameStyle(val label: String) {
    GAMEPAD("Gamepad"),
    PC("PC (MMO)"),
    /** Movement on D Z X C (W A S D's shape, lower down), so the whole top row is the number row. */
    PC_DZXC("PC (MMO), moving on D Z X C")
}

/**
 * One game's (or app's) setup: the apps it's for, each key's action (keys not listed reach the
 * game as they are), and what each trackpad half does.
 */
data class GameProfile(
    val id: String,
    val name: String,
    val style: GameStyle,
    val packages: Set<String>,
    val keys: Map<Int, GameAction>,
    val leftHalf: TrackpadRole,
    val rightHalf: TrackpadRole,
    /** Controller buttons and sticks as touches on Flux Keyboard's GameNative controls profile. */
    val touchControls: Boolean = false,
    /** Keys placed on the screen: each taps its spot (a share of the screen's width and height). */
    val taps: Map<Int, Pair<Float, Float>> = emptyMap(),
    /** Sticks placed on the screen (0 left, 1 right): centre and reach (a share of the width). */
    val stickZones: Map<Int, Triple<Float, Float, Float>> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("style", style.name)
        put("packages", JSONArray(packages.toList()))
        put("keys", JSONObject().apply { keys.forEach { (key, action) -> put(key.toString(), action.name) } })
        put("left", leftHalf.name)
        put("right", rightHalf.name)
        put("touchControls", touchControls)
        put("taps", JSONObject().apply { taps.forEach { (key, at) -> put(key.toString(), JSONArray(listOf(at.first.toDouble(), at.second.toDouble()))) } })
        put("stickZones", JSONObject().apply {
            stickZones.forEach { (stick, zone) ->
                put(stick.toString(), JSONArray(listOf(zone.first.toDouble(), zone.second.toDouble(), zone.third.toDouble())))
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): GameProfile? = runCatching {
            val keys = json.optJSONObject("keys")
            GameProfile(
                id = json.getString("id"),
                name = json.getString("name"),
                style = GameStyle.valueOf(json.optString("style", GameStyle.GAMEPAD.name)),
                packages = json.optJSONArray("packages")?.let { array -> List(array.length()) { array.getString(it) }.toSet() }.orEmpty(),
                keys = keys?.keys()?.asSequence()?.mapNotNull { key ->
                    val action = runCatching { GameAction.valueOf(keys.getString(key)) }.getOrNull()
                    key.toIntOrNull()?.let { code -> action?.let { code to it } }
                }?.toMap().orEmpty(),
                leftHalf = TrackpadRole.valueOf(json.optString("left", TrackpadRole.NONE.name)),
                rightHalf = TrackpadRole.valueOf(json.optString("right", TrackpadRole.NONE.name)),
                touchControls = json.optBoolean("touchControls", false),
                taps = json.optJSONObject("taps")?.let { taps ->
                    taps.keys().asSequence().mapNotNull { key ->
                        val at = taps.optJSONArray(key) ?: return@mapNotNull null
                        key.toIntOrNull()?.let { it to Pair(at.getDouble(0).toFloat(), at.getDouble(1).toFloat()) }
                    }.toMap()
                }.orEmpty(),
                stickZones = json.optJSONObject("stickZones")?.let { zones ->
                    zones.keys().asSequence().mapNotNull { key ->
                        val z = zones.optJSONArray(key) ?: return@mapNotNull null
                        key.toIntOrNull()?.let { it to Triple(z.getDouble(0).toFloat(), z.getDouble(1).toFloat(), z.getDouble(2).toFloat()) }
                    }.toMap()
                }.orEmpty()
            )
        }.getOrNull()
    }
}

object GameProfiles {
    private const val KEY_PROFILES = "game_profiles"
    private const val KEY_ENABLED = "game_mode_enabled"
    private const val KEY_PORTRAIT = "game_mode_portrait"
    private const val KEY_ACTIVE_PREFIX = "game_mode_active_"

    /** Every key a profile can remap: the letters, Space, Enter, Backspace, Shift and Alt. */
    val REMAPPABLE_KEYS: List<Int> = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() + listOf(
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SYM,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN
    )

    /**
     * WASD the d-pad, O K P L the Y X B A buttons in an Xbox controller's places (O on top, K
     * left, P right, L below), shoulders on Q E Z C and
     * the left ones on the volume keys too (up the shoulder, down the trigger).
     */
    fun gamepadDefaults(): Map<Int, GameAction> = mapOf(
        KeyEvent.KEYCODE_W to GameAction.DPAD_UP,
        KeyEvent.KEYCODE_A to GameAction.DPAD_LEFT,
        KeyEvent.KEYCODE_S to GameAction.DPAD_DOWN,
        KeyEvent.KEYCODE_D to GameAction.DPAD_RIGHT,
        KeyEvent.KEYCODE_O to GameAction.BUTTON_Y,
        KeyEvent.KEYCODE_K to GameAction.BUTTON_X,
        KeyEvent.KEYCODE_P to GameAction.BUTTON_B,
        KeyEvent.KEYCODE_L to GameAction.BUTTON_A,
        KeyEvent.KEYCODE_Q to GameAction.L1,
        KeyEvent.KEYCODE_E to GameAction.R1,
        KeyEvent.KEYCODE_Z to GameAction.L2,
        KeyEvent.KEYCODE_C to GameAction.R2,
        KeyEvent.KEYCODE_VOLUME_UP to GameAction.L1,
        KeyEvent.KEYCODE_VOLUME_DOWN to GameAction.L2,
        KeyEvent.KEYCODE_ENTER to GameAction.START,
        KeyEvent.KEYCODE_DEL to GameAction.SELECT,
        KeyEvent.KEYCODE_F to GameAction.L3,
        KeyEvent.KEYCODE_J to GameAction.R3
    )

    /** WASD as they are, the top row the number row (W stays movement: 2 moves to X). */
    fun pcDefaults(): Map<Int, GameAction> = mapOf(
        KeyEvent.KEYCODE_Q to GameAction.KEY_1,
        KeyEvent.KEYCODE_X to GameAction.KEY_2,
        KeyEvent.KEYCODE_E to GameAction.KEY_3,
        KeyEvent.KEYCODE_R to GameAction.KEY_4,
        KeyEvent.KEYCODE_T to GameAction.KEY_5,
        KeyEvent.KEYCODE_Y to GameAction.KEY_6,
        KeyEvent.KEYCODE_U to GameAction.KEY_7,
        KeyEvent.KEYCODE_I to GameAction.KEY_8,
        KeyEvent.KEYCODE_O to GameAction.KEY_9,
        KeyEvent.KEYCODE_P to GameAction.KEY_0
    )

    /** D Z X C move (sent as W A S D), and Q to P the whole number row. */
    fun pcDzxcDefaults(): Map<Int, GameAction> = mapOf(
        KeyEvent.KEYCODE_D to GameAction.KEY_W,
        KeyEvent.KEYCODE_Z to GameAction.KEY_A,
        KeyEvent.KEYCODE_X to GameAction.KEY_S,
        KeyEvent.KEYCODE_C to GameAction.KEY_D,
        KeyEvent.KEYCODE_Q to GameAction.KEY_1,
        KeyEvent.KEYCODE_W to GameAction.KEY_2,
        KeyEvent.KEYCODE_E to GameAction.KEY_3,
        KeyEvent.KEYCODE_R to GameAction.KEY_4,
        KeyEvent.KEYCODE_T to GameAction.KEY_5,
        KeyEvent.KEYCODE_Y to GameAction.KEY_6,
        KeyEvent.KEYCODE_U to GameAction.KEY_7,
        KeyEvent.KEYCODE_I to GameAction.KEY_8,
        KeyEvent.KEYCODE_O to GameAction.KEY_9,
        KeyEvent.KEYCODE_P to GameAction.KEY_0
    )

    fun newProfile(name: String, style: GameStyle, packages: Set<String>): GameProfile = GameProfile(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        style = style,
        packages = packages,
        keys = when (style) {
            GameStyle.GAMEPAD -> gamepadDefaults()
            GameStyle.PC -> pcDefaults()
            GameStyle.PC_DZXC -> pcDzxcDefaults()
        },
        leftHalf = if (style == GameStyle.GAMEPAD) TrackpadRole.LEFT_STICK else TrackpadRole.NONE,
        rightHalf = if (style == GameStyle.GAMEPAD) TrackpadRole.RIGHT_STICK else TrackpadRole.MOUSE,
        // GameNative only takes real controllers: its on-screen controls stand in
        touchControls = style == GameStyle.GAMEPAD && GameNativeBridge.PACKAGE in packages
    )

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()

    /** Keep the screen upright while a game runs (game apps turn it sideways). */
    fun keepPortrait(context: Context): Boolean = prefs(context).getBoolean(KEY_PORTRAIT, true)
    fun setKeepPortrait(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_PORTRAIT, on).apply()

    fun all(context: Context): List<GameProfile> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_PROFILES, "[]"))
        List(array.length()) { GameProfile.fromJson(array.getJSONObject(it)) }.filterNotNull()
    }.getOrDefault(emptyList())

    private fun saveAll(context: Context, profiles: List<GameProfile>) {
        prefs(context).edit().putString(KEY_PROFILES, JSONArray(profiles.map { it.toJson() }).toString()).apply()
    }

    fun save(context: Context, profile: GameProfile) {
        val profiles = all(context).toMutableList()
        val at = profiles.indexOfFirst { it.id == profile.id }
        if (at >= 0) profiles[at] = profile else profiles += profile
        saveAll(context, profiles)
    }

    fun delete(context: Context, id: String) = saveAll(context, all(context).filterNot { it.id == id })

    /** The profiles for an app; several when it launches several games. */
    fun forPackage(context: Context, packageName: String): List<GameProfile> =
        all(context).filter { packageName in it.packages }

    /** The profile in use for an app: the one picked last there, else its first. */
    fun active(context: Context, packageName: String): GameProfile? {
        val profiles = forPackage(context, packageName)
        val chosen = prefs(context).getString(KEY_ACTIVE_PREFIX + packageName, null)
        return profiles.firstOrNull { it.id == chosen } ?: profiles.firstOrNull()
    }

    fun setActive(context: Context, packageName: String, id: String) =
        prefs(context).edit().putString(KEY_ACTIVE_PREFIX + packageName, id).apply()
}
