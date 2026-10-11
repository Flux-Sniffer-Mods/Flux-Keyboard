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
        root.addView(LinearLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            addView(button(R.string.game_mode_place_keys) { runCatching { GameMode.placeKeys(this@ExtraScreenActivity).send() } })
            addView(button(R.string.stream_screen_to_mirror) { StreamHost.toggleScreen() })
            addView(button(R.string.stream_end) { StreamHost.endStream() })
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

    /** Fingers on the picture, at the same spots on the screen. */
    private fun touch(view: View, event: MotionEvent) {
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
