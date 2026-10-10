package it.palsoftware.pastiera.gaming

import kotlin.math.abs

/**
 * The trackpad's fingers, from getevent -l lines: each finger has its own slot (ABS_MT_SLOT),
 * starts with a tracking ID and ends when the ID turns to ffffffff, so both sticks can move at
 * once. Older drivers instead list every finger touching in each report, each ended by
 * SYN_MT_REPORT: those are matched to the fingers before them by their tracking IDs or, without,
 * by which is nearest. A surface that reports only BTN_TOUCH and positions counts as one finger
 * in slot 0.
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

    /** Older drivers: the fingers of the report being read (x, y, tracking ID or -1). */
    private var listed = false
    private val contacts = mutableListOf<Triple<Int, Int, Long>>()
    private var cx: Int? = null; private var cy: Int? = null; private var cid = -1L
    private val ids = HashMap<Int, Long>()

    private fun finger(at: Int) = fingers.getOrPut(at) { Finger() }

    fun line(line: String) {
        // Kept for older drivers too, which only show themselves at the end of their first finger
        if (!listed) when {
            line.contains("ABS_MT_TRACKING_ID") -> value(line)?.let { cid = it }
            line.contains("ABS_MT_POSITION_X") -> value(line)?.let { cx = it.toInt() }
            line.contains("ABS_MT_POSITION_Y") -> value(line)?.let { cy = it.toInt() }
            line.contains("SYN_REPORT") -> { cx = null; cy = null; cid = -1L }
        }
        when {
            line.contains("ABS_MT_SLOT") -> value(line)?.let { slot = it.toInt() }
            line.contains("SYN_MT_REPORT") -> {
                // The first one: what was read so far was this finger, not a slot's
                if (!listed) { listed = true; fingers.clear() }
                val x = cx; val y = cy
                if (x != null && y != null) contacts += Triple(x, y, cid)
                cx = null; cy = null; cid = -1L
            }
            listed && line.contains("ABS_MT_TRACKING_ID") -> value(line)?.let { cid = it }
            listed && line.contains("ABS_MT_POSITION_X") -> value(line)?.let { cx = it.toInt() }
            listed && line.contains("ABS_MT_POSITION_Y") -> value(line)?.let { cy = it.toInt() }
            listed && line.contains("SYN_REPORT") -> listedReport()
            listed && line.contains("BTN_TOUCH") -> if (line.contains("UP")) { contacts.clear(); listedReport() }
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

    /** Older drivers: this report's fingers, each matched to one before it or a new one. */
    private fun listedReport() {
        val now = contacts.toList()
        contacts.clear()
        val free = fingers.filterValues { it.down }.keys.toMutableSet()
        val placed = HashMap<Int, Triple<Int, Int, Long>>()
        val left = now.toMutableList()
        // By tracking ID first, then the closest pairs of finger and contact
        for (c in now.filter { it.third >= 0 }) {
            val match = free.firstOrNull { ids[it] == c.third } ?: continue
            free -= match; placed[match] = c; left -= c
        }
        val pairs = free.filter { (ids[it] ?: -1L) < 0 }.flatMap { at ->
            val f = fingers.getValue(at)
            left.filter { it.third < 0 }.map { c -> Triple(at, c, abs(f.x - c.first).toLong() + abs(f.y - c.second)) }
        }.sortedBy { it.third }
        for ((at, c, _) in pairs) {
            if (at !in free || c !in left) continue
            free -= at; placed[at] = c; left -= c
        }
        for (c in left) {
            val at = (0..fingers.size + placed.size).first { it !in placed && it !in free && fingers[it]?.down != true }
            placed[at] = c
        }
        for ((at, c) in placed) {
            val f = finger(at)
            if (!f.down) { f.down = true; f.started = false }
            f.x = c.first; f.y = c.second; f.xSet = true; f.ySet = true; f.changed = true
            ids[at] = c.third
        }
        for (at in free) { finger(at).apply { down = false; changed = true }; ids.remove(at) }
        report()
    }

    /** The last field, in hex (ffffffff for a lifted finger's tracking ID). */
    private fun value(line: String): Long? = line.trim().split(Regex("\\s+")).lastOrNull()?.toLongOrNull(16)
}
