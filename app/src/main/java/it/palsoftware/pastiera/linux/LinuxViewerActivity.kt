package it.palsoftware.pastiera.linux

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.getSymMappingsPage2
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.personaliseSymbolsDefaults

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
    /** The built-in keyboard's Alt and Sym layers and sticky Shift, from Flux Keyboard's own maps (your edits too). */
    private val layers by lazy {
        val custom = runCatching { SettingsManager.getSymMappingsPage2(this) }.getOrDefault(emptyMap())
        KeyLayers(
            alt = runCatching { AltModifierMappingResolver.resolve(assets, this) }.getOrDefault(emptyMap()),
            sym = custom.ifEmpty {
                runCatching { SettingsManager.personaliseSymbolsDefaults(this, KeyMappingLoader.loadSymKeyMappingsPage2(assets)) }.getOrDefault(emptyMap())
            },
            symShifted = if (custom.isEmpty()) runCatching { KeyMappingLoader.loadSymKeyMappingsPage2Uppercase(assets) }.getOrDefault(emptyMap()) else emptyMap()
        )
    }
    /** Keys that typed a layer's character, by key: typed again as they repeat, their release spent. */
    private val typed = HashMap<Int, String>()
    /** Modifiers held around a key for a layer or Shift tapped before it, let go with it. */
    private val wrapped = HashMap<Int, List<Int>>()

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
        // KDE lays itself out around the camera cutout and the screen's rounded corners (its panel
        // and title bars clear them), so it has the whole screen; Xfce doesn't, so it's kept below
        // the cutout
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (LinuxDesktop.desktop(this) != LinuxDesktop.Desktop.KDE) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val band = insets.displayCutout?.safeInsetTop ?: 0
                val params = v.layoutParams as FrameLayout.LayoutParams
                if (params.topMargin != band) { params.topMargin = band; v.layoutParams = params }
                insets
            }
        }
        // The whole screen the desktop's (on Android 10 too, which has no insetsController)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).let {
            it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            // Shown once the desktop is drawing: till then its screen is only black
            if (state == LinuxDesktop.State.Starting) { Thread.sleep(1_000); continue }
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
        // Left while it was connecting: nothing kept open or playing behind it
        if (!open) { vnc?.close(); return }
        if (vnc == null) {
            // Why, from Linux itself
            val logs = LinuxDesktop.guestLogs(this).orEmpty()
            FluxLog.w("FluxLinux", "viewer gave up: $problem\n$logs")
            explain(getString(R.string.linux_viewer_unreachable), problem, logs)
            return
        }
        vnc.live()
        client = vnc
        view.attach(vnc)
        sound.start()
        // Closed just now (between the check above and here): what was just opened goes too
        if (!open) { vnc.close(); sound.stop(); return }
        // The server says whether it can resize with its first picture: asked then
        view.postDelayed({ fit(view.width, view.height) }, 1_500)
        runOnUiThread { message.visibility = android.view.View.GONE }
        runCatching { vnc.run() }.onFailure { if (open) FluxLog.w("FluxLinux", "viewer: $it") }
        if (open) {
            // The session's last word comes once it has wound down
            val until = System.currentTimeMillis() + 8_000
            while (LinuxDesktop.state.value == LinuxDesktop.State.Running && System.currentTimeMillis() < until) Thread.sleep(250)
            val logs = LinuxDesktop.guestLogs(this).orEmpty()
            // Ended from inside it (its Close desktop, or signing out): the picture goes with it
            if ("flux: the desktop was stopped" in logs) { runOnUiThread { finish() }; return }
            // Ended without being asked: why, from Linux itself
            if (logs.isNotBlank()) FluxLog.w("FluxLinux", "desktop ended:\n$logs")
            explain(getString(R.string.linux_viewer_ended), "", logs)
        }
    }

    /** [text], then why: the problem seen and the ends of Linux's own logs, small and left-aligned. */
    private fun explain(text: String, problem: String, logs: String) = runOnUiThread {
        message.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        message.textSize = 13f
        message.text = listOf(text, problem, logs).filter { it.isNotBlank() }.joinToString("\n\n")
        message.visibility = android.view.View.VISIBLE
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
        // Too big for Android's clipboard, or refused: Linux keeps it
        runCatching { getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Linux", text)) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val vnc = client ?: return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        // Volume stays the phone's
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return super.dispatchKeyEvent(event)
        if (builtIn(event) && layered(vnc, event)) return true
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
                wrapped.remove(event.keyCode)?.asReversed()?.forEach { vnc.key(it, false) }
            }
            KeyEvent.ACTION_MULTIPLE -> event.characters?.codePoints()?.forEach { char ->
                val keysym = Keysyms.of(char)
                if (keysym != 0) { vnc.key(keysym, true); vnc.key(keysym, false) }
            }
        }
        return true
    }

    /** The phone's own keyboard (not one plugged in or paired, nor keys an app sends). */
    private fun builtIn(event: KeyEvent): Boolean = event.device?.let { !it.isVirtual && !it.isExternal } == true

    /**
     * The built-in keyboard as Flux Keyboard has it: Alt and Sym are layers, not keys Linux sees
     * (held, tapped for the next key, or locked with two taps), Shift tapped capitalises the next
     * key. True when the key was dealt with here; the rest goes the usual way, a tapped Alt or
     * Shift held around it where it has nothing on a layer (Alt+Tab, Shift+arrows).
     */
    private fun layered(vnc: VncClient, event: KeyEvent): Boolean {
        val down = when (event.action) { KeyEvent.ACTION_DOWN -> true; KeyEvent.ACTION_UP -> false; else -> return false }
        val code = runCatching { DeviceSpecific.remapHardwareKeyEvent(event.keyCode, event).keyCode }.getOrDefault(event.keyCode)
        val sticky = when (code) {
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> layers.altKey
            KeyEvent.KEYCODE_SYM -> layers.symKey
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> layers.shift
            else -> null
        }
        if (sticky != null) {
            if (down) { if (event.repeatCount == 0) sticky.down() } else sticky.up(event.eventTime)
            // Shift is Shift in Linux too while held (a selection, Shift+Tab): sent the usual way
            return sticky !== layers.shift
        }
        if (!down) return typed.remove(event.keyCode) != null
        // A layer's character, typed again as the key repeats
        val chars = typed[event.keyCode]?.takeIf { event.repeatCount > 0 }
            ?: layers.characters(code, event.isShiftPressed || layers.shift.on)?.also {
                typed[event.keyCode] = it
                if (!event.isShiftPressed) layers.shift.used()
            }
        if (chars != null) {
            chars.codePoints().forEach { c -> Keysyms.of(c).takeIf { it != 0 }?.let { vnc.key(it, true); vnc.key(it, false) } }
            return true
        }
        if (event.repeatCount > 0) return false
        val wrap = mutableListOf<Int>()
        if (layers.altKey.on) { wrap += ALT; layers.altKey.used() }
        if (layers.symKey.on) layers.symKey.used()
        if (layers.shift.on && !event.isShiftPressed) {
            val upper = event.getUnicodeChar(event.metaState or KeyEvent.META_SHIFT_ON)
            // Capitals locked: letters only; a tap: the next key's shifted character
            val letter = upper > 0 && Character.isLetter(upper)
            if (upper > 0 && wrap.isEmpty() && !event.isCtrlPressed && (letter || layers.shift.latched)) {
                layers.shift.used()
                val keysym = Keysyms.of(event.keyCode, upper)
                if (keysym != 0) { held[event.keyCode] = keysym; vnc.key(keysym, true); return true }
            } else if (!layers.shift.locked) {
                wrap += SHIFT; layers.shift.used()
            }
        }
        if (wrap.isNotEmpty()) { wrap.forEach { vnc.key(it, true) }; wrapped[event.keyCode] = wrap }
        return false
    }

    private fun keysymOf(event: KeyEvent): Int {
        val unicode = event.getUnicodeChar(event.metaState)
        // Held with Ctrl or Alt there's no character: the key's own
        return if (unicode > 0 && !event.isCtrlPressed) Keysyms.of(event.keyCode, unicode)
        else Keysyms.of(event.keyCode, 0).takeIf { it != 0 } ?: Keysyms.base(event)
    }

    private fun paste(vnc: VncClient) {
        val text = runCatching {
            getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull()
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

    private companion object {
        const val ALT = 0xFFE9
        const val SHIFT = 0xFFE1
    }

    override fun onDestroy() {
        open = false
        client?.close()
        sound.stop()
        super.onDestroy()
    }
}
