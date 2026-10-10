package it.palsoftware.pastiera.gaming

import it.palsoftware.pastiera.adb.AdbShell

/**
 * A recording of every touch surface while fingers move on the trackpad: what each reports, and
 * the most fingers it reported at once, saved to share when two sticks don't move together.
 */
object TrackpadTest {
    const val FILE = "/storage/emulated/0/Download/Flux Keyboard/trackpad-test.txt"
    const val SECONDS = 8

    /** The surface that reported the most fingers at once (its path), and how many; null if the shell failed. */
    fun record(): Pair<String?, Int>? {
        val out = AdbShell.run(
            "f='$FILE'; mkdir -p \"\${f%/*}\"; getevent -pl > \"\$f\" 2>&1; " +
                "timeout $SECONDS getevent -lt >> \"\$f\" 2>&1; grep -E 'ABS_MT|SYN_' \"\$f\"; true",
            (SECONDS + 6) * 1_000L
        ) ?: return null
        return mostFingers(out)
    }

    /** From getevent -lt lines (each led by its device): per device, the most fingers in one report. */
    internal fun mostFingers(dump: String): Pair<String?, Int> {
        class Surface { var slot = 0; val down = HashSet<Int>(); var listed = 0; var most = 0 }
        val surfaces = HashMap<String, Surface>()
        val device = Regex("(/dev/input/event\\d+)")
        for (line in dump.lineSequence()) {
            val path = device.find(line)?.value ?: continue
            val s = surfaces.getOrPut(path) { Surface() }
            val value = line.trim().split(Regex("\\s+")).lastOrNull()?.toLongOrNull(16)
            when {
                line.contains("ABS_MT_SLOT") -> value?.let { s.slot = it.toInt() }
                line.contains("ABS_MT_TRACKING_ID") -> if (value == 0xffffffffL) s.down -= s.slot else s.down += s.slot
                line.contains("SYN_MT_REPORT") -> s.listed++
                line.contains("SYN_REPORT") -> {
                    s.most = maxOf(s.most, s.down.size, s.listed)
                    s.listed = 0
                }
            }
        }
        val best = surfaces.maxByOrNull { it.value.most }
        return best?.key to (best?.value?.most ?: 0)
    }
}
