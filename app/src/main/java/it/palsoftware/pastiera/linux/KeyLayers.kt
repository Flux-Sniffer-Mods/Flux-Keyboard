package it.palsoftware.pastiera.linux

/**
 * The built-in keyboard's layers in the desktop, as Flux Keyboard has them (from
 * Titan-2-Elite-Debian-Chroot's keymap): Alt types each key's own second character (Flux
 * Keyboard's Alt map), Sym its symbols page. Each is on while held, for the next key when tapped,
 * and locked when tapped twice (till tapped again); Shift alike, a tap for the next key, two taps
 * for capitals throughout.
 */
internal class KeyLayers(
    private val alt: Map<Int, String>,
    private val sym: Map<Int, String>,
    private val symShifted: Map<Int, String> = emptyMap()
) {
    /** A modifier held, tapped for the next key, or tapped twice and locked. */
    class Sticky {
        private var held = false
        private var usedWhileHeld = false
        private var lastTap = Long.MIN_VALUE / 2
        var latched = false
            private set
        var locked = false
            private set
        val on get() = held || latched || locked
        val isHeld get() = held

        fun down() {
            if (held) return
            held = true
            usedWhileHeld = false
        }

        fun up(now: Long) {
            held = false
            if (usedWhileHeld) return                         // held for other keys: that was all
            when {
                locked -> locked = false                      // a tap ends a lock
                latched && now - lastTap <= DOUBLE_TAP_MS -> { latched = false; locked = true }
                latched -> latched = false                    // tapped again later: off
                else -> latched = true
            }
            lastTap = now
        }

        /** A key typed with it: a tap's latch is spent; a hold or a lock stays. */
        fun used() {
            if (held) usedWhileHeld = true
            latched = false
        }
    }

    val shift = Sticky()
    val altKey = Sticky()
    val symKey = Sticky()

    /** What [keyCode] types on the layer that's on (Sym before Alt), or null: the key as itself. */
    fun characters(keyCode: Int, shifted: Boolean): String? {
        if (symKey.on) {
            val c = (if (shifted) symShifted[keyCode] else null) ?: sym[keyCode]
            if (c != null) { symKey.used(); return c }
        }
        if (altKey.on) {
            val c = alt[keyCode]
            if (c != null) { altKey.used(); return c }
        }
        return null
    }

    private companion object {
        const val DOUBLE_TAP_MS = 400L
    }
}
