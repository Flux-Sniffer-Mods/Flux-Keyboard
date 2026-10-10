package it.palsoftware.pastiera.gaming

/**
 * The trackpad's fingers, from getevent -l lines: each finger has its own slot (ABS_MT_SLOT),
 * starts with a tracking ID and ends when the ID turns to ffffffff, so both sticks can move at
 * once. A surface that reports only BTN_TOUCH and positions counts as one finger in slot 0.
 * Each finger is reported once its report (SYN_REPORT) is complete: [onDown], then [onMove] while
 * it moves, then [onUp].
 */
internal class TouchSlots(
    private val onDown: (slot: Int, x: Int, y: Int) -> Unit,
    private val onMove: (slot: Int, x: Int, y: Int) -> Unit,
    private val onUp: (slot: Int) -> Unit
) {
    private class Finger {
        var x = 0; var y = 0
        var xSet = false; var ySet = false
        /** Touching, as far as the surface has said. */
        var down = false
        /** [onDown] sent. */
        var started = false
        var changed = false
    }

    private val fingers = HashMap<Int, Finger>()
    private var slot = 0
    /** The surface reports tracking IDs: fingers start and end by them, not by BTN_TOUCH. */
    private var tracked = false

    private fun finger(at: Int) = fingers.getOrPut(at) { Finger() }

    fun line(line: String) {
        when {
            line.contains("ABS_MT_SLOT") -> value(line)?.let { slot = it.toInt() }
            line.contains("ABS_MT_TRACKING_ID") -> value(line)?.let { id ->
                tracked = true
                val f = finger(slot)
                if (id == 0xffffffffL || id < 0) f.down = false
                else { f.down = true; f.started = false; f.xSet = false; f.ySet = false }
                f.changed = true
            }
            line.contains("ABS_MT_POSITION_X") -> value(line)?.let { finger(slot).apply { x = it.toInt(); xSet = true; changed = true } }
            line.contains("ABS_MT_POSITION_Y") -> value(line)?.let { finger(slot).apply { y = it.toInt(); ySet = true; changed = true } }
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> if (!tracked) {
                finger(0).apply { down = true; started = false; xSet = false; ySet = false; changed = true }
            }
            // Every finger off (it also ends them on a surface without tracking IDs)
            line.contains("BTN_TOUCH") && line.contains("UP") -> fingers.values.forEach { it.down = false; it.changed = true }
            line.contains("SYN_REPORT") -> report()
        }
    }

    private fun report() {
        for ((at, f) in fingers) {
            if (!f.changed) continue
            f.changed = false
            when {
                !f.down -> if (f.started) { f.started = false; onUp(at) }
                !f.xSet || !f.ySet -> Unit
                f.started -> onMove(at, f.x, f.y)
                else -> { f.started = true; onDown(at, f.x, f.y) }
            }
        }
    }

    /** The last field, in hex (ffffffff for a lifted finger's tracking ID). */
    private fun value(line: String): Long? = line.trim().split(Regex("\\s+")).lastOrNull()?.toLongOrNull(16)
}
