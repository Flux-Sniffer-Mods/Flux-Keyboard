package it.palsoftware.pastiera.gaming

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.display.ExtraScreen
import it.palsoftware.pastiera.display.ScreenPreview
import it.palsoftware.pastiera.streaming.StreamHost
import java.lang.ref.WeakReference

/**
 * The Games app's page for a game on a screen of its own: that screen live on the phone, and
 * while this is in front the phone's keys and touch go there (with gaming mode's placed keys
 * and trackpad sticks). Buttons to place keys, bring the game back to the phone, or end the stream.
 */
class ExtraScreenActivity : Activity() {
    private lateinit var preview: ScreenPreview
    private val closed: () -> Unit = { if (ExtraScreen.current == null) runOnUiThread { finish() } }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ExtraScreen.current == null) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val density = resources.displayMetrics.density
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = ScreenPreview(this)
        root.addView(preview, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        // The screen's shape, as large as the phone allows
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val screen = ExtraScreen.current ?: return@addOnLayoutChangeListener
            val scale = minOf((r - l).toFloat() / screen.width, (b - t).toFloat() / screen.height)
            val w = (screen.width * scale).toInt(); val h = (screen.height * scale).toInt()
            val params = preview.layoutParams
            if (params.width != w || params.height != h) {
                params.width = w; params.height = h
                preview.post { preview.layoutParams = params }
            }
        }
        preview.setOnTouchListener { view, event -> touch(view, event); true }

        fun button(label: Int, onClick: () -> Unit) = TextView(this).apply {
            text = getString(label)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            setOnClickListener { onClick() }
        }
        // Where each kind of input goes: the phone's touch as the screen's touch or its
        // trackpad, the phone's keys to the screen or kept by the phone
        val desktop = StreamHost.session?.isDesktop == true
        val prefs = it.palsoftware.pastiera.SettingsManager.getPreferences(this)
        val kind = if (desktop) "desktop" else "game"
        trackpad = prefs.getBoolean("extra_screen_trackpad_$kind", desktop)
        ExtraScreen.keysToScreen = prefs.getBoolean("extra_screen_keys_$kind", true)
        lateinit var touchButton: TextView
        lateinit var keysButton: TextView
        fun labels() {
            touchButton.text = getString(if (trackpad) R.string.extra_screen_touch_trackpad else R.string.extra_screen_touch_direct)
            keysButton.text = getString(if (ExtraScreen.keysToScreen) R.string.extra_screen_keys_screen else R.string.extra_screen_keys_phone)
        }
        touchButton = button(R.string.extra_screen_touch_direct) {
            trackpad = !trackpad
            prefs.edit().putBoolean("extra_screen_trackpad_$kind", trackpad).apply()
            labels()
        }
        keysButton = button(R.string.extra_screen_keys_screen) {
            ExtraScreen.keysToScreen = !ExtraScreen.keysToScreen
            prefs.edit().putBoolean("extra_screen_keys_$kind", ExtraScreen.keysToScreen).apply()
            labels()
        }
        labels()
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x99000000.toInt())
            addView(LinearLayout(this@ExtraScreenActivity).apply {
                addView(touchButton)
                addView(keysButton)
            })
            addView(LinearLayout(this@ExtraScreenActivity).apply {
                addView(button(R.string.game_mode_place_keys) { runCatching { GameMode.placeKeys(this@ExtraScreenActivity).send() } })
                // A game moves back to the phone; the desktop has none to move
                if (!desktop) addView(button(R.string.stream_screen_to_mirror) { StreamHost.toggleScreen() })
                addView(button(R.string.stream_end) { StreamHost.endStream() })
            })
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        setContentView(root)
        open = WeakReference(this)
        ExtraScreen.watch(closed)
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        ExtraScreen.focused = true
        // Gaming mode follows the game on the screen, not this page
        StreamHost.session?.gamePackage?.let { GameMode.onAppInFront(this, it) }
        // This page took focus as it opened: given back to the screen once it has
        window.decorView.postDelayed({ ExtraScreen.refocus() }, 600)
    }

    override fun onPause() {
        inFront = false
        ExtraScreen.focused = false
        super.onPause()
    }

    override fun onDestroy() {
        ExtraScreen.unwatch(closed)
        if (open?.get() === this) open = null
        super.onDestroy()
    }

    /** Keys reach here only without Flux Keyboard's accessibility helper: passed on all the same. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (GameMode.onKeyEvent(event)) return true
        if (ExtraScreen.key(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** The phone's screen as the extra screen's trackpad (else its touch, at the same spots). */
    private var trackpad = false
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var moved = 0f
    private var fingers = 0
    private var scrollRest = 0f

    /**
     * Trackpad: one finger moves the pointer, a tap clicks, a two-finger tap right-clicks, two
     * fingers moving scroll.
     */
    private fun trackpadTouch(view: View, event: MotionEvent) {
        val screen = ExtraScreen.current ?: return
        // Across the phone, a little more than across the screen: the pointer covers it in one swipe
        val speed = 1.6f * screen.width / view.width.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y; downTime = event.eventTime; moved = 0f; fingers = 1; scrollRest = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                fingers = maxOf(fingers, event.pointerCount)
                lastX = event.getX(0); lastY = event.getY(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.getX(0) - lastX; val dy = event.getY(0) - lastY
                lastX = event.getX(0); lastY = event.getY(0)
                moved += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                if (event.pointerCount >= 2) {
                    // Two fingers: the wheel, a notch per 40 pixels
                    scrollRest += dy / 40f
                    val notches = scrollRest.toInt()
                    if (notches != 0) { ExtraScreen.scroll(notches.toFloat(), 0f); scrollRest -= notches }
                } else ExtraScreen.mouseMove(this, dx * speed, dy * speed)
            }
            MotionEvent.ACTION_UP -> {
                val tap = event.eventTime - downTime < 250 && moved < 24 * resources.displayMetrics.density
                if (tap) {
                    val right = fingers >= 2
                    ExtraScreen.mouseButton(true, right)
                    ExtraScreen.mouseButton(false, right)
                }
                fingers = 0
            }
        }
    }

    /** Fingers on the picture, at the same spots on the screen. */
    private fun touch(view: View, event: MotionEvent) {
        if (trackpad) { trackpadTouch(view, event); return }
        val screen = ExtraScreen.current ?: return
        val sx = screen.width.toFloat() / view.width.coerceAtLeast(1)
        val sy = screen.height.toFloat() / view.height.coerceAtLeast(1)
        fun at(i: Int) = (event.getX(i) * sx).coerceIn(0f, screen.width - 1f) to (event.getY(i) * sy).coerceIn(0f, screen.height - 1f)
        fun finger(i: Int) = FINGERS + event.getPointerId(i)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val (x, y) = at(i)
                ExtraScreen.touch('d', finger(i), x, y)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val (x, y) = at(i)
                ExtraScreen.touch('m', finger(i), x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                ExtraScreen.touch('u', finger(event.actionIndex), 0f, 0f)
                // Touching the phone gave it focus: back to the screen, for controllers plugged in
                if (event.actionMasked == MotionEvent.ACTION_UP) ExtraScreen.refocus()
            }
            MotionEvent.ACTION_CANCEL -> for (i in 0 until event.pointerCount) ExtraScreen.touch('u', finger(i), 0f, 0f)
        }
    }

    companion object {
        private const val FINGERS = 200

        @Volatile var inFront = false
            private set
        @Volatile private var open: WeakReference<ExtraScreenActivity>? = null

        /** The page closed (the game's back on the phone, or the stream ended). */
        fun finishOpen() {
            val page = open?.get() ?: return
            page.runOnUiThread { page.finish() }
        }
    }
}
