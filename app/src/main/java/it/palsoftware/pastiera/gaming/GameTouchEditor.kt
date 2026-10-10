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
 * Placing the trackpad's sticks on a game's own on-screen sticks (GameHub's and GameNative's
 * games, which take a stick only by touch): drawn over the game, a circle for each stick to drag
 * where the game draws it. Gaming mode then drags there as the trackpad moves.
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

        // Nothing placed yet: the sticks where the launcher draws them
        if (zones.isEmpty()) zones.putAll(withDefaults(profile, always = true).stickZones)
        fun addStick(stick: Int) {
            val zone = zones[stick] ?: Triple(if (stick == 0) 0.25f else 0.75f, 0.7f, 0.12f)
            zones[stick] = zone
            val size = (zone.third * 2 * width).toInt()
            frame.addView(chip(context.getString(if (stick == 0) R.string.game_mode_left_stick else R.string.game_mode_right_stick),
                zone.first, zone.second, true, size) { x, y -> zones[stick] = Triple(x, y, zone.third) }.also { chips += it })
        }
        zones.keys.toList().forEach { addStick(it) }

        // The bar: a stick, clear, save or cancel
        val hint = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            text = context.getString(R.string.game_mode_place_hint)
        }
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
                addView(button(R.string.game_mode_left_stick) { if (0 !in zones) addStick(0) })
                addView(button(R.string.game_mode_right_stick) { if (1 !in zones) addStick(1) })
                addView(button(R.string.game_mode_place_clear) {
                    zones.clear()
                    chips.forEach { frame.removeView(it) }
                    chips.clear()
                })
                addView(button(R.string.game_mode_save) {
                    GameMode.placed(context, profile.copy(stickZones = zones.toMap()))
                    close(windows)
                })
                addView(button(R.string.cancel) { close(windows) })
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
            )
        }
        frame.addView(bar)

        // Back closes it, as Cancel does
        frame.isFocusableInTouchMode = true
        frame.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) close(windows)
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

    /** Where GameHub draws its sticks (sideways, its own layout): centre and reach, shares of the screen. */
    private val GAMEHUB = Triple(0.146f, 0.692f, 0.0875f) to Triple(0.683f, 0.692f, 0.0875f)

    /** Where GameNative's built-in on-screen controller (its default controls profile) has its sticks. */
    private val GAMENATIVE = Triple(0.216f, 0.733f, 0.054f) to Triple(0.784f, 0.733f, 0.054f)

    /**
     * [profile] with the trackpad's sticks on its launcher's on-screen sticks, when none have
     * been placed by hand: for GameHub and GameNative, or [always] (the placer's start).
     */
    fun withDefaults(profile: GameProfile, always: Boolean = false): GameProfile {
        if (profile.stickZones.isNotEmpty()) return profile
        val (left, right) = when (GameApps.appFor(profile)) {
            "GameHub" -> GAMEHUB
            "GameNative" -> GAMENATIVE
            else -> if (always) GAMEHUB else return profile
        }
        val halves = listOf(profile.leftHalf, profile.rightHalf)
        val zones = buildMap {
            if (TrackpadRole.LEFT_STICK in halves) put(0, left)
            if (TrackpadRole.RIGHT_STICK in halves) put(1, right)
        }
        return profile.copy(stickZones = zones)
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
}
