package it.palsoftware.pastiera.gaming

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * A controller GameNative believes: GameNative only takes real controllers as controllers, but
 * its on-screen controls drive the game's Xbox controller. Flux Keyboard writes a controls
 * profile for it with every button, the d-pad and both sticks in fixed places, invisible; once
 * it's imported and picked in GameNative, gaming mode touches those places as keys are pressed
 * and drags the sticks with the trackpad.
 */
object GameNativeBridge {
    const val PACKAGE = "app.gamenative"
    private const val FILE_NAME = "Flux Keyboard gamepad.icp"

    /** Each button's place, as a share of the screen's width and height (its centre). */
    val BUTTONS: Map<GameAction, Pair<Float, Float>> = run {
        val order = listOf(
            GameAction.BUTTON_A, GameAction.BUTTON_B, GameAction.BUTTON_X, GameAction.BUTTON_Y,
            GameAction.L1, GameAction.R1, GameAction.L2, GameAction.R2,
            GameAction.START, GameAction.SELECT, GameAction.L3, GameAction.R3,
            GameAction.DPAD_UP, GameAction.DPAD_DOWN, GameAction.DPAD_LEFT, GameAction.DPAD_RIGHT
        )
        order.mapIndexed { index, action ->
            action to Pair(0.2f + (index % 4) * 0.2f, 0.15f + (index / 4) * 0.12f)
        }.toMap()
    }

    /** The sticks' centres. */
    val LEFT_STICK = Pair(0.3f, 0.8f)
    val RIGHT_STICK = Pair(0.7f, 0.8f)

    /** A stick's scale in the profile, and how far a full push drags, as a share of the width. */
    private const val STICK_SCALE = 1.5f
    const val STICK_REACH = 0.06f * STICK_SCALE * 0.9f

    private val BINDINGS = mapOf(
        GameAction.BUTTON_A to "GAMEPAD_BUTTON_A", GameAction.BUTTON_B to "GAMEPAD_BUTTON_B",
        GameAction.BUTTON_X to "GAMEPAD_BUTTON_X", GameAction.BUTTON_Y to "GAMEPAD_BUTTON_Y",
        GameAction.L1 to "GAMEPAD_BUTTON_L1", GameAction.R1 to "GAMEPAD_BUTTON_R1",
        GameAction.L2 to "GAMEPAD_BUTTON_L2", GameAction.R2 to "GAMEPAD_BUTTON_R2",
        GameAction.START to "GAMEPAD_BUTTON_START", GameAction.SELECT to "GAMEPAD_BUTTON_SELECT",
        GameAction.L3 to "GAMEPAD_BUTTON_L3", GameAction.R3 to "GAMEPAD_BUTTON_R3",
        GameAction.DPAD_UP to "GAMEPAD_DPAD_UP", GameAction.DPAD_DOWN to "GAMEPAD_DPAD_DOWN",
        GameAction.DPAD_LEFT to "GAMEPAD_DPAD_LEFT", GameAction.DPAD_RIGHT to "GAMEPAD_DPAD_RIGHT"
    )

    /** The controls profile GameNative imports. */
    fun profileJson(name: String = "Flux Keyboard"): JSONObject {
        val elements = JSONArray()
        fun element(type: String, bindings: List<String>, place: Pair<Float, Float>, scale: Float) =
            JSONObject().apply {
                put("type", type)
                put("shape", "CIRCLE")
                put("bindings", JSONArray(bindings + List(4 - bindings.size) { "NONE" }))
                put("scale", scale.toDouble())
                put("x", place.first.toDouble())
                put("y", place.second.toDouble())
                put("toggleSwitch", false)
                put("text", "")
                put("iconId", 0)
                // Invisible: the keyboard presses them
                put("buttonOpacity", 0.0)
            }
        BUTTONS.forEach { (action, place) -> elements.put(element("BUTTON", listOf(BINDINGS.getValue(action)), place, 1f)) }
        elements.put(element("STICK", listOf("GAMEPAD_LEFT_THUMB_UP", "GAMEPAD_LEFT_THUMB_RIGHT", "GAMEPAD_LEFT_THUMB_DOWN", "GAMEPAD_LEFT_THUMB_LEFT"), LEFT_STICK, STICK_SCALE))
        elements.put(element("STICK", listOf("GAMEPAD_RIGHT_THUMB_UP", "GAMEPAD_RIGHT_THUMB_RIGHT", "GAMEPAD_RIGHT_THUMB_DOWN", "GAMEPAD_RIGHT_THUMB_LEFT"), RIGHT_STICK, STICK_SCALE))
        return JSONObject().apply {
            put("name", name)
            put("cursorSpeed", 1.0)
            put("elements", elements)
        }
    }

    /** Saves the profile to Downloads/Flux Keyboard; false when it couldn't. */
    fun save(context: Context, game: String? = null): Boolean = runCatching {
        val resolver = context.contentResolver
        // A game's own copy: named after it, so GameNative can be set to it for that game
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, game?.let { "$it (Flux Keyboard).icp" } ?: FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Flux Keyboard" + if (game != null) "/Profiles/GameNative" else "")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        val json = profileJson(game?.let { "$it (Flux Keyboard)" } ?: "Flux Keyboard")
        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) } ?: return false
        true
    }.getOrDefault(false)
}
