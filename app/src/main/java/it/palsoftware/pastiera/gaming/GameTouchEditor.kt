package it.palsoftware.pastiera.gaming

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * Placing keys on a game's own on-screen controls (any game or emulator with them, GameHub
 * included): drawn over the game, a chip for each key placed and a circle for each stick, to drag
 * where the game draws its buttons and sticks. Gaming mode then taps those spots for the keys and
 * drags the circles with the trackpad.
 */
object GameTouchEditor {
    @Volatile var open = false
        private set

    private var root: FrameLayout? = null

    fun show(context: Context, profile: GameProfile) {
        if (open) return
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, R.string.game_mode_place_overlay_needed, Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        val windows = context.getSystemService(WindowManager::class.java) ?: return
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val density = metrics.density
        val taps = profile.taps.toMutableMap()
        val chips = mutableListOf<View>()
        val zones = profile.stickZones.toMutableMap()
        val frame = FrameLayout(context).apply { setBackgroundColor(0x55000000) }
        root = frame
        open = true

        fun chip(label: String, x: Float, y: Float, round: Boolean, sizePx: Int, onMoved: (Float, Float) -> Unit): TextView =
            TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                textSize = 13f
                background = GradientDrawable().apply {
                    shape = if (round) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                    cornerRadius = 12 * density
                    setColor(0xAA3D5AFE.toInt())
                    setStroke((2 * density).toInt(), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
                this.x = x * width - sizePx / 2f
                this.y = y * height - sizePx / 2f
                setOnTouchListener(dragger(sizePx) { cx, cy -> onMoved(cx / width, cy / height) })
            }

        val keySize = (52 * density).toInt()
        fun addKeyChip(code: Int, at: Pair<Float, Float>) {
            val action = profile.keys[code]?.takeIf { it.gamepad }
            val label = keyName(code) + (action?.let { "\n" + it.label.take(6) } ?: "")
            frame.addView(chip(label, at.first, at.second, false, keySize) { x, y -> taps[code] = x to y }.also {
                it.textSize = 11f
                chips += it
            })
        }
        // Nothing placed yet: each controller button's key where GameHub draws that button
        if (taps.isEmpty() && zones.isEmpty()) {
            val laid = withDefaults(profile, always = true)
            taps.putAll(laid.taps); zones.putAll(laid.stickZones)
        }
        taps.forEach { (code, at) -> addKeyChip(code, at) }
        fun addStick(stick: Int) {
            val zone = zones[stick] ?: Triple(if (stick == 0) 0.25f else 0.75f, 0.7f, 0.12f)
            zones[stick] = zone
            val size = (zone.third * 2 * width).toInt()
            frame.addView(chip(context.getString(if (stick == 0) R.string.game_mode_left_stick else R.string.game_mode_right_stick),
                zone.first, zone.second, true, size) { x, y -> zones[stick] = Triple(x, y, zone.third) }.also { chips += it })
        }
        zones.keys.toList().forEach { addStick(it) }

        // The bar: add a key (press it), a stick, done or cancel
        val hint = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            text = context.getString(R.string.game_mode_place_hint)
        }
        var waitingForKey = false
        fun button(label: Int, onClick: () -> Unit) = TextView(context).apply {
            text = context.getString(label)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setOnClickListener { onClick() }
        }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            addView(hint)
            addView(LinearLayout(context).apply {
                addView(button(R.string.game_mode_add_key) {
                    waitingForKey = true
                    hint.text = context.getString(R.string.game_mode_press_key)
                })
                addView(button(R.string.game_mode_left_stick) { if (0 !in zones) addStick(0) })
                addView(button(R.string.game_mode_right_stick) { if (1 !in zones) addStick(1) })
                addView(button(R.string.game_mode_place_clear) {
                    taps.clear(); zones.clear()
                    chips.forEach { frame.removeView(it) }
                    chips.clear()
                })
                addView(button(R.string.game_mode_save) {
                    GameMode.placed(context, profile.copy(taps = taps.toMap(), stickZones = zones.toMap()))
                    close(windows)
                })
                addView(button(R.string.cancel) { close(windows) })
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
            )
        }
        frame.addView(bar)

        // The key pressed after Add a key gets a chip in the middle, to drag into place
        frame.isFocusableInTouchMode = true
        frame.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_BACK) { close(windows); return@setOnKeyListener true }
            if (waitingForKey) {
                waitingForKey = false
                hint.text = context.getString(R.string.game_mode_place_hint)
                if (keyCode !in taps) {
                    taps[keyCode] = 0.5f to 0.5f
                    addKeyChip(keyCode, 0.5f to 0.5f)
                }
            }
            true
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        runCatching { windows.addView(frame, params); frame.requestFocus() }
            .onFailure { open = false; root = null }
    }

    /** An app's on-screen controls: each button's centre and the sticks (x, y, reach), shares of the screen. */
    private class Layout(val buttons: Map<GameAction, Pair<Float, Float>>, val left: Triple<Float, Float, Float>, val right: Triple<Float, Float, Float>)

    /**
     * GameHub's own layout, sideways: face buttons, d-pad, bumpers, triggers and stick clicks.
     * Its Start and Select sit in its menu, so they're left to the keys.
     */
    private val GAMEHUB = Layout(
        mapOf(
            GameAction.BUTTON_A to (0.873f to 0.831f), GameAction.BUTTON_B to (0.935f to 0.693f),
            GameAction.BUTTON_X to (0.810f to 0.693f), GameAction.BUTTON_Y to (0.873f to 0.555f),
            GameAction.DPAD_UP to (0.315f to 0.629f), GameAction.DPAD_DOWN to (0.315f to 0.748f),
            GameAction.DPAD_LEFT to (0.265f to 0.688f), GameAction.DPAD_RIGHT to (0.367f to 0.688f),
            GameAction.L1 to (0.079f to 0.281f), GameAction.R1 to (0.918f to 0.281f),
            GameAction.L2 to (0.081f to 0.116f), GameAction.R2 to (0.917f to 0.116f),
            GameAction.L3 to (0.058f to 0.419f), GameAction.R3 to (0.962f to 0.419f)
        ),
        Triple(0.146f, 0.692f, 0.0875f), Triple(0.683f, 0.692f, 0.0875f)
    )

    /**
     * GameNative's built-in on-screen controller (its default controls profile), placed as
     * shares of the screen. Its d-pad is one control: each direction is pressed on its arm.
     */
    private val GAMENATIVE = Layout(
        mapOf(
            GameAction.BUTTON_A to (0.872f to 0.533f), GameAction.BUTTON_B to (0.931f to 0.4f),
            GameAction.BUTTON_X to (0.813f to 0.4f), GameAction.BUTTON_Y to (0.872f to 0.267f),
            GameAction.DPAD_UP to (0.108f to 0.359f), GameAction.DPAD_DOWN to (0.108f to 0.441f),
            GameAction.DPAD_LEFT to (0.072f to 0.4f), GameAction.DPAD_RIGHT to (0.144f to 0.4f),
            GameAction.L1 to (0.03f to 0.222f), GameAction.R1 to (0.97f to 0.222f),
            GameAction.L2 to (0.07f to 0.07f), GameAction.R2 to (0.93f to 0.07f),
            GameAction.START to (0.539f to 0.911f), GameAction.SELECT to (0.461f to 0.911f),
            GameAction.L3 to (0.05f to 0.733f), GameAction.R3 to (0.95f to 0.733f)
        ),
        Triple(0.216f, 0.733f, 0.054f), Triple(0.784f, 0.733f, 0.054f)
    )

    private fun layoutFor(profile: GameProfile): Layout? = when (NativeProfiles.appFor(profile)) {
        "GameHub" -> GAMEHUB
        "GameNative" -> GAMENATIVE
        else -> null
    }

    /**
     * [profile] with its launcher's on-screen buttons pressed for its keys and its sticks moved by
     * the trackpad, when nothing has been placed by hand: for GameHub and GameNative, or [always]
     * (the placer's start, GameHub's layout for other apps).
     */
    fun withDefaults(profile: GameProfile, always: Boolean = false): GameProfile {
        if (profile.taps.isNotEmpty() || profile.stickZones.isNotEmpty()) return profile
        val layout = layoutFor(profile) ?: if (always) GAMEHUB else return profile
        val taps = profile.keys.mapNotNull { (code, action) -> layout.buttons[action]?.let { code to it } }.toMap()
        val halves = listOf(profile.leftHalf, profile.rightHalf)
        val zones = buildMap {
            if (TrackpadRole.LEFT_STICK in halves) put(0, layout.left)
            if (TrackpadRole.RIGHT_STICK in halves) put(1, layout.right)
        }
        return profile.copy(taps = taps, stickZones = zones)
    }

    private fun close(windows: WindowManager) {
        root?.let { runCatching { windows.removeView(it) } }
        root = null
        open = false
    }

    /** Dragging a chip: its centre follows the finger. */
    @SuppressLint("ClickableViewAccessibility")
    private fun dragger(sizePx: Int, onMoved: (Float, Float) -> Unit) = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                view.x = event.rawX - sizePx / 2f
                view.y = event.rawY - sizePx / 2f
                onMoved(event.rawX, event.rawY)
            }
        }
        true
    }

    private fun keyName(code: Int): String =
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace("_LEFT", "").replace("DEL", "BKSP")
            .replace("VOLUME", "VOL").take(6)
}
