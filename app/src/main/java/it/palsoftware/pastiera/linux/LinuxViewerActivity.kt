package it.palsoftware.pastiera.linux

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.R

/**
 * The Linux desktop, full screen: its picture from the VNC server on this phone, the keyboard,
 * touch and mouse sent back. Back leaves it (the desktop keeps running); opened on a TV screen,
 * it shows there.
 */
class LinuxViewerActivity : Activity() {
    private lateinit var view: DesktopView
    private val sound = LinuxSound()
    private lateinit var message: TextView
    @Volatile private var client: VncClient? = null
    @Volatile private var open = true
    /** Each key's keysym as it went down, so it's let go as the same. */
    private val held = HashMap<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = DesktopView(this)
        // The desktop resized to this screen whenever the view's size settles (or changes)
        view.onSized = { w, h -> fit(w, h) }
        message = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            text = getString(R.string.linux_viewer_connecting)
        }
        setContentView(FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        })
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view.requestFocus()
        Thread({ connect() }, "LinuxViewer").apply { isDaemon = true; start() }
    }

    private fun connect() {
        // The server may still be starting: tried for as long as it is, then half a minute more,
        // what's going on shown meanwhile (installing what's new, starting, why it isn't answering)
        var until = System.currentTimeMillis() + 30_000
        var vnc: VncClient? = null
        var problem = ""
        while (open && vnc == null && (System.currentTimeMillis() < until || LinuxDesktop.state.value == LinuxDesktop.State.Starting)) {
            val state = LinuxDesktop.state.value
            if (state == LinuxDesktop.State.Starting) until = System.currentTimeMillis() + 30_000
            if (state is LinuxDesktop.State.Failed) break
            progress(problem)
            // The password of this run, read each time (a start may have made a new one)
            val password = LinuxDesktop.password(this)
            vnc = runCatching {
                VncClient.connect("127.0.0.1", LinuxDesktop.PORT,
                    onFrame = { x, y, w, h -> view.changed(x, y, w, h) },
                    onResize = { w, h -> view.post { view.resize(w, h) } },
                    onClipboard = { text -> runOnUiThread { copied(text) } }
                ).also { if (!it.handshake(password)) { it.close(); throw IllegalStateException(getString(R.string.linux_viewer_refused)) } }
            }.onFailure { problem = it.message?.takeIf { m -> m.isNotBlank() && "ECONNREFUSED" !in m && "failed to connect" !in m }.orEmpty() }
                .getOrNull()
            if (vnc == null) Thread.sleep(1_000)
        }
        if (vnc == null) {
            // Why, from Linux itself
            val logs = LinuxDesktop.guestLogs(this).orEmpty()
            FluxLog.w("FluxLinux", "viewer gave up: $problem\n$logs")
            runOnUiThread {
                message.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                message.textSize = 13f
                message.text = listOf(getString(R.string.linux_viewer_unreachable), problem, logs)
                    .filter { it.isNotBlank() }.joinToString("\n\n")
                message.visibility = android.view.View.VISIBLE
            }
            return
        }
        vnc.live()
        client = vnc
        view.attach(vnc)
        sound.start()
        // The server says whether it can resize with its first picture: asked then
        view.postDelayed({ fit(view.width, view.height) }, 1_500)
        runOnUiThread { message.visibility = android.view.View.GONE }
        runCatching { vnc.run() }.onFailure { if (open) FluxLog.w("FluxLinux", "viewer: $it") }
        if (open) show(R.string.linux_viewer_ended)
    }

    /** While connecting: what the desktop is doing (its last line), or why it isn't answering yet. */
    private fun progress(problem: String) = runOnUiThread {
        val detail = when (val state = LinuxDesktop.state.value) {
            LinuxDesktop.State.Starting -> LinuxDesktop.log.value.lastOrNull()?.removePrefix("flux: ").orEmpty()
            is LinuxDesktop.State.Failed -> state.step.name
            else -> problem
        }
        message.text = getString(R.string.linux_viewer_connecting) + if (detail.isNotBlank()) "\n\n" + detail else ""
    }

    private fun show(text: Int) = runOnUiThread {
        message.text = getString(text)
        message.visibility = android.view.View.VISIBLE
    }

    /** Text copied in Linux: the phone's clipboard too. */
    private fun copied(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Linux", text))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vnc = client ?: return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        // Volume stays the phone's
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                // Ctrl+V pastes the phone's clipboard into Linux's first
                if (event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_V && event.repeatCount == 0) paste(vnc)
                val keysym = keysymOf(event)
                if (keysym == 0) return super.dispatchKeyEvent(event)
                held[event.keyCode] = keysym
                vnc.key(keysym, true)
            }
            KeyEvent.ACTION_UP -> {
                val keysym = held.remove(event.keyCode) ?: keysymOf(event)
                if (keysym == 0) return super.dispatchKeyEvent(event)
                vnc.key(keysym, false)
            }
            KeyEvent.ACTION_MULTIPLE -> event.characters?.codePoints()?.forEach { char ->
                val keysym = Keysyms.of(char)
                if (keysym != 0) { vnc.key(keysym, true); vnc.key(keysym, false) }
            }
        }
        return true
    }

    private fun keysymOf(event: KeyEvent): Int {
        val unicode = event.getUnicodeChar(event.metaState)
        // Held with Ctrl or Alt there's no character: the key's own
        return if (unicode > 0 && !event.isCtrlPressed) Keysyms.of(event.keyCode, unicode)
        else Keysyms.of(event.keyCode, 0).takeIf { it != 0 } ?: Keysyms.base(event)
    }

    private fun paste(vnc: VncClient) {
        val text = getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!text.isNullOrEmpty()) vnc.clipboard(text)
    }

    /**
     * The desktop made the view's size, in pixels, every one of them (the desktop draws itself
     * bigger instead, by its scale). Only where the server can resize, and only when it's off.
     */
    private fun fit(w: Int, h: Int) {
        val vnc = client ?: return
        if (!vnc.resizable || w <= 0 || h <= 0) return
        val width = (w and 1.inv()).coerceIn(640, 3840)
        val height = (h and 1.inv()).coerceIn(480, 2160)
        if (kotlin.math.abs(width - vnc.width) < 8 && kotlin.math.abs(height - vnc.height) < 8) return
        Thread { vnc.setSize(width, height) }.start()
    }

    override fun onDestroy() {
        open = false
        client?.close()
        sound.stop()
        super.onDestroy()
    }
}
