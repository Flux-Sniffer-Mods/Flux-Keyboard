package it.palsoftware.pastiera.inputmethod

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * Modifier state rebuilt from raw key events, for apps where Pastiera leaves every key to the app
 * but still shows its status LEDs ("Hide keyboard in these apps" with "Show status LEDs only").
 *
 * Shift, Ctrl, Alt and Sym behave like Pastiera and the Titan X layout: tap = next key only, tap
 * again = locked, tap once more = off, hold = only while held. The same event can arrive twice
 * (accessibility service and input method); duplicates are ignored.
 */
internal class ObservedModifierLeds {
    enum class Modifier { SHIFT, CTRL, ALT, SYM }
    enum class Level { OFF, ACTIVE, LOCKED }

    private class State {
        var held = false
        var usedWhileHeld = false
        var latched = false
        var locked = false
    }

    private val states = Modifier.values().associateWith { State() }
    private val recentEvents = ArrayDeque<Long>()

    fun level(modifier: Modifier): Level {
        val state = states.getValue(modifier)
        return when {
            state.locked -> Level.LOCKED
            state.held || state.latched -> Level.ACTIVE
            else -> Level.OFF
        }
    }

    fun reset() {
        states.values.forEach {
            it.held = false
            it.usedWhileHeld = false
            it.latched = false
            it.locked = false
        }
        recentEvents.clear()
    }

    /** Feeds one key event; returns true if any LED level changed. */
    fun onKey(keyCode: Int, action: Int, repeatCount: Int, downTime: Long, eventTime: Long): Boolean {
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        if (!markSeen(keyCode, action, downTime, eventTime)) return false
        val before = Modifier.values().map(::level)
        val modifier = modifierOf(keyCode)
        val down = action == KeyEvent.ACTION_DOWN
        if (modifier != null) {
            val state = states.getValue(modifier)
            if (down) {
                if (repeatCount == 0) {
                    state.held = true
                    state.usedWhileHeld = false
                }
            } else {
                state.held = false
                if (!state.usedWhileHeld) {
                    when {
                        state.locked -> state.locked = false
                        state.latched -> {
                            state.latched = false
                            state.locked = true
                        }
                        else -> state.latched = true
                    }
                }
            }
        } else if (down && repeatCount == 0) {
            states.values.forEach { state ->
                if (state.held) state.usedWhileHeld = true
                if (state.latched) state.latched = false // the latch applied to this key
            }
        }
        return Modifier.values().map(::level) != before
    }

    /** Pastiera's status snapshot with the observed modifier states in place of its own. */
    fun applyTo(snapshot: StatusBarController.StatusSnapshot): StatusBarController.StatusSnapshot {
        val shift = states.getValue(Modifier.SHIFT)
        val ctrl = states.getValue(Modifier.CTRL)
        val alt = states.getValue(Modifier.ALT)
        return snapshot.copy(
            capsLockEnabled = shift.locked,
            shiftPhysicallyPressed = shift.held,
            shiftOneShot = shift.latched,
            ctrlLatchActive = ctrl.locked,
            ctrlPhysicallyPressed = ctrl.held,
            ctrlOneShot = ctrl.latched,
            ctrlLatchFromNavMode = false,
            altLatchActive = alt.locked,
            altPhysicallyPressed = alt.held,
            altOneShot = alt.latched,
            symPage = when (level(Modifier.SYM)) {
                Level.LOCKED -> 2
                Level.ACTIVE -> 1
                Level.OFF -> 0
            }
        )
    }

    private fun markSeen(keyCode: Int, action: Int, downTime: Long, eventTime: Long): Boolean {
        val signature = ((downTime * 31 + eventTime) * 31 + keyCode) * 31 + action
        if (signature in recentEvents) return false
        recentEvents.addLast(signature)
        while (recentEvents.size > RECENT_EVENTS) recentEvents.removeFirst()
        return true
    }

    private fun modifierOf(keyCode: Int): Modifier? = when (keyCode) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Modifier.SHIFT
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> Modifier.CTRL
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Modifier.ALT
        KeyEvent.KEYCODE_SYM -> Modifier.SYM
        else -> null
    }

    private companion object {
        const val RECENT_EVENTS = 16
    }
}

/**
 * Hands key events seen by Pastiera's accessibility service to the input method while a hidden
 * app shows status LEDs. Apps such as Termux:X11 read hardware keys before any input method, so
 * this is the only way for the LEDs to follow them. Observing never consumes an event.
 */
internal object HiddenAppKeyObserver {
    @Volatile
    var sink: ((KeyEvent) -> Unit)? = null

    /** An app with Pastiera hidden is in front (key diagnostics are logged only then). */
    @Volatile
    var hiddenAppInFront: Boolean = false

    // The Titan 2 Elite's Ctrl keys and Sym report these non-standard scan codes
    private val TITAN_SPECIAL_SCAN_CODES = setOf(65, 151, 251, 253)

    /**
     * Key diagnostics (logcat tag FluxKeys; pastiera-flux.sh key-log), only while a hidden app is
     * in front and only for modifier, Sym, Fn, F-number and unknown keys: never text keys.
     */
    fun logKey(event: KeyEvent?, path: String) {
        if (!hiddenAppInFront || event == null || !isDiagnosticKey(event)) return
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return
        Log.i(
            "FluxKeys",
            "$path: ${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} " +
                "${KeyEvent.keyCodeToString(event.keyCode)} scan=${event.scanCode} " +
                "meta=0x${Integer.toHexString(event.metaState)} repeat=${event.repeatCount} device=${event.deviceId}"
        )
    }

    fun isDiagnosticKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        return KeyEvent.isModifierKey(code) ||
            code == KeyEvent.KEYCODE_SYM ||
            code == KeyEvent.KEYCODE_FUNCTION ||
            code == KeyEvent.KEYCODE_UNKNOWN ||
            code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ||
            event.scanCode in TITAN_SPECIAL_SCAN_CODES
    }

    /**
     * Set while a hidden app allows Pastiera's emoji/symbols panels. Returns true when the input
     * method took the event (it then never reaches the app). Called on the main thread: the
     * accessibility service and the input method share Pastiera's process.
     */
    @Volatile
    var interceptor: ((KeyEvent) -> Boolean)? = null

    fun intercept(event: KeyEvent): Boolean {
        val target = interceptor ?: return false
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        return target(event)
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun observe(event: KeyEvent) {
        val target = sink ?: return
        val copy = KeyEvent(event)
        if (Looper.myLooper() == Looper.getMainLooper()) target(copy) else mainHandler.post { target(copy) }
    }
}
