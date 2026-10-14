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
import it.palsoftware.pastiera.display.ExtraScreen
import it.palsoftware.pastiera.display.ScreenPreview

/**
 * Placing keys and the trackpad's sticks on a game's own on-screen controls (games that only take
 * touch, and GameHub's and GameNative's sticks): drawn over the game, a box for each key and a
 * circle for each stick, to drag where the game draws them. Gaming mode then taps there for the
 * keys and drags there as the trackpad moves.
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
        val density = metrics.density
        // The game on a screen of its own: placed on a live picture of it, fitted on the phone's
        val screen = GameMode.extraScreen()?.takeIf { own -> own.id == ExtraScreen.current?.id }
        val scale = screen?.let { minOf(metrics.widthPixels.toFloat() / it.width, metrics.heightPixels.toFloat() / it.height) }
        val width = if (screen != null && scale != null) screen.width * scale else metrics.widthPixels.toFloat()
        val height = if (screen != null && scale != null) screen.height * scale else metrics.heightPixels.toFloat()
        val left = (metrics.widthPixels - width) / 2
        val top = (metrics.heightPixels - height) / 2
        val chips = mutableListOf<View>()
        val zones = profile.stickZones.toMutableMap()
        val taps = profile.taps.toMutableMap()
        val frame = FrameLayout(context).apply { setBackgroundColor(if (screen != null) Color.BLACK else 0x55000000) }
        if (screen != null) frame.addView(ScreenPreview(context).apply { alpha = 0.85f },
            FrameLayout.LayoutParams(width.toInt(), height.toInt()).apply { leftMargin = left.toInt(); topMargin = top.toInt() })
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
                this.x = left + x * width - sizePx / 2f
                this.y = top + y * height - sizePx / 2f
                setOnTouchListener(dragger(sizePx) { cx, cy -> onMoved((cx - left) / width, (cy - top) / height) })
            }

        // Nothing placed yet: the sticks where the launcher draws them
        if (zones.isEmpty() && taps.isEmpty()) zones.putAll(withDefaults(profile, always = true).stickZones)
        // A stick circle's size is a share of the screen's shorter side, as gaming mode reaches it
        val side = minOf(width, height)
        val tuning = profile.stickTuning.toMutableMap()
        val stickChips = mutableMapOf<Int, View>()
        var refreshStickRow: () -> Unit = {}
        fun addStick(stick: Int) {
            val zone = zones[stick] ?: Triple(if (stick == 0) 0.25f else 0.75f, 0.7f, 0.18f)
            zones[stick] = zone
            val size = (zone.third * 2 * side).toInt()
            stickChips[stick]?.let { frame.removeView(it); chips -= it }
            val chip = chip(context.getString(if (stick == 0) R.string.game_mode_left_stick else R.string.game_mode_right_stick),
                zone.first, zone.second, true, size) { x, y -> zones[stick] = Triple(x, y, zones[stick]?.third ?: zone.third) }
            frame.addView(chip)
            chips += chip
            stickChips[stick] = chip
            refreshStickRow()
        }
        zones.keys.toList().forEach { addStick(it) }
        val keySize = (52 * density).toInt()
        fun addKey(code: Int, at: Pair<Float, Float>) {
            frame.addView(chip(keyName(code), at.first, at.second, false, keySize) { x, y -> taps[code] = x to y }.also { chips += it })
        }
        taps.forEach { (code, at) -> addKey(code, at) }

        // A DS or 3DS's bottom screen, where the trackpad touches: a box to drag, and size below
        val usesArea = profile.leftHalf == TrackpadRole.BOTTOM_SCREEN || profile.rightHalf == TrackpadRole.BOTTOM_SCREEN
        val screenW = if (screen != null) screen.width.toFloat() else metrics.widthPixels.toFloat()
        val screenH = if (screen != null) screen.height.toFloat() else metrics.heightPixels.toFloat()
        var area: List<Float>? = if (usesArea) GameMode.touchArea(profile, screenW, screenH) else null
        var areaBox: View? = null
        fun drawArea() {
            val a = area ?: return
            areaBox?.let { frame.removeView(it) }
            val boxW = ((a[2] - a[0]) * width).toInt().coerceAtLeast(1)
            val boxH = ((a[3] - a[1]) * height).toInt().coerceAtLeast(1)
            val box = TextView(context).apply {
                text = context.getString(R.string.game_mode_bottom_screen)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                textSize = 13f
                background = GradientDrawable().apply {
                    setColor(0x553D5AFE)
                    setStroke((2 * density).toInt(), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(boxW, boxH)
                x = left + a[0] * width
                y = top + a[1] * height
                var grabX = 0f; var grabY = 0f
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { grabX = event.rawX - view.x; grabY = event.rawY - view.y }
                        MotionEvent.ACTION_MOVE -> {
                            view.x = event.rawX - grabX; view.y = event.rawY - grabY
                            val l = (view.x - left) / width; val t = (view.y - top) / height
                            val cur = area ?: return@setOnTouchListener true
                            area = listOf(l, t, l + (cur[2] - cur[0]), t + (cur[3] - cur[1]))
                        }
                    }
                    true
                }
            }
            // Under the keys and sticks (over the live picture, where there is one)
            frame.addView(box, if (screen != null) 1 else 0)
            areaBox = box
        }
        fun resizeArea(by: Float) {
            val a = area ?: return
            val cx = (a[0] + a[2]) / 2; val cy = (a[1] + a[3]) / 2
            val f = (1 + by).coerceAtLeast(0.1f)
            val hw = (a[2] - a[0]) / 2 * f; val hh = (a[3] - a[1]) / 2 * f
            if (hw * 2 > 1.2f || hw * 2 < 0.1f) return
            area = listOf(cx - hw, cy - hh, cx + hw, cy + hh)
            drawArea()
        }
        drawArea()

        // The bar: a key, a stick, clear, save or cancel
        val hint = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            text = context.getString(R.string.game_mode_place_hint)
        }
        var waitingForKey = false
        var picker: View? = null
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
                    picker?.let { frame.removeView(it); picker = null } ?: run {
                        // Every key at a tap: the game or the keyboard may take a key pressed here
                        picker = keyPicker(context, density) { code ->
                            picker?.let { frame.removeView(it) }; picker = null
                            waitingForKey = false
                            hint.text = context.getString(R.string.game_mode_place_hint)
                            if (code !in taps) { taps[code] = 0.5f to 0.5f; addKey(code, 0.5f to 0.5f) }
                        }.also { frame.addView(it) }
                    }
                })
                addView(button(R.string.game_mode_left_stick) { if (0 !in zones) addStick(0) })
                addView(button(R.string.game_mode_right_stick) { if (1 !in zones) addStick(1) })
                addView(button(R.string.game_mode_place_clear) {
                    zones.clear(); taps.clear()
                    chips.forEach { frame.removeView(it) }
                    chips.clear(); stickChips.clear()
                    refreshStickRow()
                })
                addView(button(R.string.game_mode_save) {
                    GameMode.placed(context, profile.copy(stickZones = zones.toMap(), taps = taps.toMap(), stickTuning = tuning.toMap(),
                        touchArea = area ?: profile.touchArea))
                    close(windows)
                })
                addView(button(R.string.cancel) { close(windows) })
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
            )
        }
        // Each placed stick: its circle's size, its dead zone and its range
        val stickRow = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        bar.addView(stickRow)
        refreshStickRow = {
            stickRow.removeAllViews()
            for (stick in zones.keys.sorted()) {
                val (dead, range) = tuning[stick] ?: GameProfile.DEFAULT_TUNING
                val name = context.getString(if (stick == 0) R.string.game_mode_left_stick else R.string.game_mode_right_stick)
                fun resize(by: Float) {
                    val zone = zones[stick] ?: return
                    zones[stick] = Triple(zone.first, zone.second, (zone.third + by).coerceIn(0.05f, 0.45f))
                    addStick(stick)
                }
                stickRow.addView(LinearLayout(context).apply {
                    addView(TextView(context).apply { text = name; setTextColor(Color.WHITE); textSize = 13f
                        setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0) })
                    addView(button(R.string.game_mode_stick_smaller) { resize(-0.02f) })
                    addView(button(R.string.game_mode_stick_bigger) { resize(0.02f) })
                    addView(TextView(context).apply {
                        text = context.getString(R.string.game_mode_stick_dead_zone, (dead * 100).toInt())
                        setTextColor(Color.WHITE); textSize = 14f
                        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                        setOnClickListener {
                            val next = DEAD_ZONES[(DEAD_ZONES.indexOfFirst { kotlin.math.abs(it - dead) < 0.001f } + 1) % DEAD_ZONES.size]
                            tuning[stick] = next to range; refreshStickRow()
                        }
                    })
                    addView(TextView(context).apply {
                        text = context.getString(R.string.game_mode_stick_range, (range * 100).toInt())
                        setTextColor(Color.WHITE); textSize = 14f
                        setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
                        setOnClickListener {
                            val next = RANGES[(RANGES.indexOfFirst { kotlin.math.abs(it - range) < 0.001f } + 1) % RANGES.size]
                            tuning[stick] = dead to next; refreshStickRow()
                        }
                    })
                })
            }
        }
        refreshStickRow()
        if (area != null) bar.addView(LinearLayout(context).apply {
            addView(TextView(context).apply { text = context.getString(R.string.game_mode_bottom_screen); setTextColor(Color.WHITE); textSize = 13f
                setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0) })
            addView(button(R.string.game_mode_stick_smaller) { resizeArea(-0.05f) })
            addView(button(R.string.game_mode_stick_bigger) { resizeArea(0.05f) })
        })
        frame.addView(bar)

        // Back closes it, as Cancel does; the key pressed after Add a key gets a box in the
        // middle, to drag into place
        frame.isFocusableInTouchMode = true
        frame.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            if (keyCode == KeyEvent.KEYCODE_BACK) { close(windows); return@setOnKeyListener true }
            if (waitingForKey) {
                waitingForKey = false
                picker?.let { frame.removeView(it) }; picker = null
                hint.text = context.getString(R.string.game_mode_place_hint)
                if (keyCode !in taps) {
                    taps[keyCode] = 0.5f to 0.5f
                    addKey(keyCode, 0.5f to 0.5f)
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

    /** Dead zones and ranges to step through, tapped. */
    private val DEAD_ZONES = listOf(0f, 0.04f, 0.08f, 0.12f, 0.16f, 0.2f, 0.3f)
    private val RANGES = listOf(0.6f, 0.8f, 1f, 1.2f, 1.4f)

    /** The keys a game can be given, as on the keyboard: letters, then the rest. */
    private val PICKABLE = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toList() + listOf(
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN
    )

    /** A grid of the keys, at the bottom of the screen: tapping one places it. */
    private fun keyPicker(context: Context, density: Float, onPick: (Int) -> Unit): View {
        val grid = android.widget.GridLayout(context).apply {
            columnCount = 9
            setBackgroundColor(0xEE111111.toInt())
            setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
        }
        for (code in PICKABLE) {
            grid.addView(TextView(context).apply {
                text = keyName(code)
                setTextColor(Color.WHITE)
                textSize = 13f
                gravity = Gravity.CENTER
                minWidth = (40 * density).toInt()
                minHeight = (40 * density).toInt()
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(0xAA3D5AFE.toInt())
                }
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    setMargins((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
                }
                setOnClickListener { onPick(code) }
            })
        }
        return android.widget.HorizontalScrollView(context).apply {
            addView(grid)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        }
    }

    /** Where GameHub draws its sticks (sideways, its own layout): centre and reach, shares of the screen. */
    private val GAMEHUB = Triple(0.146f, 0.692f, 0.156f) to Triple(0.683f, 0.692f, 0.156f)

    /** Where GameNative's built-in on-screen controller (its default controls profile) has its sticks. */
    private val GAMENATIVE = Triple(0.216f, 0.733f, 0.096f) to Triple(0.784f, 0.733f, 0.096f)

    /**
     * Where most touch games draw their sticks: a joystick low on the left, the camera dragged on
     * the right (sizes a share of the screen's shorter side).
     */
    private val TOUCH_GAME = Triple(0.17f, 0.72f, 0.17f) to Triple(0.78f, 0.55f, 0.2f)

    /**
     * A program played by touch: keys placed on its screen, or a game that is an app of its own,
     * set up as a game or as an app (not an emulator's or launcher's game, nor a cloud game,
     * which take a controller).
     */
    private fun playedByTouch(profile: GameProfile): Boolean {
        if (profile.taps.isNotEmpty()) return true
        val pkg = profile.packages.firstOrNull() ?: return false
        // A game added as one, or an app set up as a game (from the notification): both played by touch
        return GameApps.appFor(profile) == null && GameApps.emulator(pkg) == null &&
            pkg != it.palsoftware.pastiera.BuildConfig.APPLICATION_ID
    }

    /**
     * [profile] with the trackpad's sticks on its launcher's on-screen sticks, when none have
     * been placed by hand: for GameHub and GameNative, or [always] (the placer's start).
     */
    fun withDefaults(profile: GameProfile, always: Boolean = false): GameProfile {
        if (profile.stickZones.isNotEmpty()) return profile
        val (left, right) = when (GameApps.appFor(profile)) {
            "GameHub" -> GAMEHUB
            "GameNative" -> GAMENATIVE
            // Programs played by touch: the trackpad moves their own on-screen sticks
            else -> if (always || playedByTouch(profile)) TOUCH_GAME else return profile
        }
        // Both sticks: the keys move them (W A Z D, Alt with I J M L)
        return profile.copy(stickZones = mapOf(0 to left, 1 to right))
    }

    private fun keyName(code: Int): String =
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace("_LEFT", "").replace("DEL", "BKSP")
            .replace("VOLUME", "VOL").take(6)

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
