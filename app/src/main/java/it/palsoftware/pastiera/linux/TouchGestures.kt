package it.palsoftware.pastiera.linux

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Touch on the desktop the way a phone has it, turned into the pointer (ported from
 * Titan-2-Elite-Debian-Chroot's input bridge):
 *   tap                      -> left click
 *   drag                     -> scroll, the content following the finger (a flick glides on)
 *   long-press, then lift    -> right click
 *   long-press, then drag    -> drag with the left button (select text, move a window)
 *   two-finger drag          -> scroll, either way
 *   two-finger tap           -> right click
 * Touches near an edge land on it, and one along the bottom edge holds the pointer there a moment
 * (which shows a panel hidden under the windows) rather than clicking.
 *
 * Plain numbers in and out, so it runs anywhere: [frame] with the fingers down (view pixels, none
 * once the last lifts) at each change, [tick] while [ticking] (the long-press timer, gliding, the
 * edge's hold), the pointer told through [Desk] in view pixels too.
 */
internal class TouchGestures(private val desk: Desk, density: Float, private val longPressMs: Long = 500) {
    interface Desk {
        /** Where the desktop's picture is on the view, in view pixels. */
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        /** The pointer at view point ([x], [y]) with [buttons] held (VNC's mask). */
        fun pointer(x: Float, y: Float, buttons: Int)
        /** A finger held long enough for a right click or a drag: felt, as on Android. */
        fun longPressed() {}
    }

    // The bridge's distances, measured in the Titan 2 Elite's pixels, as dp
    private val movePx = 8 * density          // travel before a touch counts as moving
    private val stillPx = 3 * density         // more than this before the long-press = not one
    private val edgePx = 5 * density          // this close to a side or the top lands on it
    private val bottomPx = 15 * density       // this close to the bottom is on the bottom edge
    private val scrollPx = 14 * density       // finger travel per wheel step
    private val flingMin = 270 * density      // px/s at lift-off to glide on
    private val flingStop = 57 * density      // px/s where a glide ends

    private enum class State { IDLE, PENDING, SCROLL, DRAG, TWO, EDGE }

    private var state = State.IDLE
    private var t0 = 0L
    private var startX = 0f
    private var startY = 0f
    private var held = false
    private var wobbled = false
    private var lastX = 0f
    private var lastY = 0f
    private var accX = 0f
    private var accY = 0f
    private var vertical = true
    private var scrolled = false
    private class Sample(val time: Long, val x: Float, val y: Float)
    private val samples = ArrayDeque<Sample>()          // the last 100 ms of a scroll
    private var edgeHold = 0L
    private var edgeX = 0f
    // Gliding after a flick: px/s each way and when it last moved
    private var flingVx = 0f
    private var flingVy = 0f
    private var flingAt = 0L
    private var gliding = false
    private var atX = 0f
    private var atY = 0f

    /** Whether [tick] needs calling (every frame or so): a timer, a glide or the edge's hold. */
    fun ticking(now: Long): Boolean =
        gliding || state == State.EDGE || now < edgeHold || (state == State.PENDING && !held && !wobbled)

    /** The fingers down now, in view pixels: none once the last one lifts. */
    fun frame(points: List<FloatArray>, now: Long) {
        val pts = points.map { snap(it[0], it[1]) }
        val n = pts.size
        when (state) {
            State.IDLE -> {
                if (n >= 1) gliding = false                    // a new touch stops a glide
                if (n == 1) {
                    state = State.PENDING; t0 = now; startX = pts[0][0]; startY = pts[0][1]
                    move(startX, startY)
                    // On the bottom edge: the panel's, never a click or a scroll
                    if (startY >= desk.bottom) { state = State.EDGE; edgeX = startX }
                } else if (n >= 2) beginTwo(pts, now)
            }
            State.EDGE -> if (n == 0) {
                val x = startX
                reset()
                edgeHold = now + EDGE_HOLD_MS; edgeX = x
            }
            State.PENDING -> when {
                n == 0 -> {
                    click(startX, startY, if (held) RIGHT else LEFT)   // a long-press lifted: right click
                    reset()
                }
                n >= 2 -> beginTwo(pts, now)
                !held && dist(pts[0], startX, startY) > stillPx -> {
                    wobbled = true                             // a slow scroll's start isn't a long-press
                    if (dist(pts[0], startX, startY) > movePx) beginScroll(pts[0], now)
                }
                held && dist(pts[0], startX, startY) > movePx -> {
                    state = State.DRAG                          // long-press, then moved: a drag
                    desk.pointer(startX, startY, 0)
                    desk.pointer(startX, startY, LEFT)
                    lastX = pts[0][0]; lastY = pts[0][1]
                    desk.pointer(lastX, lastY, LEFT)
                }
            }
            State.SCROLL -> when {
                n == 0 -> { startFling(now); reset() }
                n >= 2 -> beginTwo(pts, now)
                else -> scrollTo(pts[0], now)
            }
            State.DRAG -> if (n == 0) {
                desk.pointer(lastX, lastY, 0)
                reset()
            } else {
                lastX = pts[0][0]; lastY = pts[0][1]
                desk.pointer(lastX, lastY, LEFT)
            }
            State.TWO -> if (n == 0) {
                if (!scrolled && now - t0 < TWO_TAP_MS) click(atX, atY, RIGHT)   // two-finger tap
                reset()
            } else if (n >= 2) {
                val (cx, cy) = centroid(pts)
                scrollBy(cx - lastX, cy - lastY)
                lastX = cx; lastY = cy
            }
        }
    }

    /** Called about every frame while [ticking]: the long-press timer, a glide, the edge's hold. */
    fun tick(now: Long) {
        if (state == State.EDGE || now < edgeHold) {
            // A screen edge counts as reached only while the pointer keeps arriving at it, so it's
            // nudged along the edge line
            edgeX += if ((now / 50) % 2 == 0L) 2f else -2f
            move(edgeX, desk.bottom)
        }
        if (state == State.PENDING && !held && !wobbled && now - t0 >= longPressMs) {
            held = true
            desk.longPressed()
        }
        if (gliding) {
            val dt = (now - flingAt) / 1000f
            if (dt <= 0f) return
            scrollBy(flingVx * dt, flingVy * dt)
            val keep = FLING_DECAY.pow(dt / 0.016f)
            flingVx *= keep; flingVy *= keep
            flingAt = now
            if (max(abs(flingVx), abs(flingVy)) < flingStop) gliding = false
        }
    }

    /** The touch was taken away (by Android): a held button let go, everything forgotten. */
    fun cancel() {
        if (state == State.DRAG) desk.pointer(lastX, lastY, 0)
        gliding = false
        edgeHold = 0
        reset()
    }

    private fun reset() {
        state = State.IDLE
        held = false; wobbled = false; scrolled = false
        accX = 0f; accY = 0f
        samples.clear()
        edgeHold = 0
    }

    /** Near an edge: on it (the bottom edge reaching further in, for the panel). */
    private fun snap(x: Float, y: Float): FloatArray {
        var sx = x
        var sy = y
        if (sx < desk.left + edgePx) sx = desk.left
        if (sx > desk.right - edgePx) sx = desk.right
        if (sy < desk.top + edgePx) sy = desk.top
        if (sy > desk.bottom - bottomPx) sy = desk.bottom
        return floatArrayOf(sx, sy)
    }

    private fun move(x: Float, y: Float) {
        atX = x; atY = y
        desk.pointer(x, y, 0)
    }

    private fun click(x: Float, y: Float, button: Int) {
        move(x, y)
        desk.pointer(x, y, button)
        desk.pointer(x, y, 0)
    }

    private fun beginScroll(p: FloatArray, now: Long) {
        state = State.SCROLL
        move(startX, startY)                                   // the window under the finger scrolls
        vertical = abs(p[1] - startY) >= abs(p[0] - startX)    // one finger scrolls one way only
        lastX = startX; lastY = startY
        samples.clear()
        samples.addLast(Sample(now, startX, startY))
        scrollTo(p, now)
    }

    private fun scrollTo(p: FloatArray, now: Long) {
        val dx = if (vertical) 0f else p[0] - lastX
        val dy = if (vertical) p[1] - lastY else 0f
        scrollBy(dx, dy)
        lastX = p[0]; lastY = p[1]
        samples.addLast(Sample(now, p[0], p[1]))
        while (samples.size > 2 && now - samples.first().time > 100) samples.removeFirst()
    }

    private fun startFling(now: Long) {
        if (samples.size < 2) return
        val first = samples.first()
        val last = samples.last()
        val span = (last.time - first.time).toFloat()
        if (now - last.time > 80 || span < 10) return           // the finger stopped before lifting
        var vx = (last.x - first.x) / span * 1000
        var vy = (last.y - first.y) / span * 1000
        if (vertical) vx = 0f else vy = 0f
        if (max(abs(vx), abs(vy)) >= flingMin) {
            flingVx = vx; flingVy = vy; flingAt = now; gliding = true
            accX = 0f; accY = 0f
        }
    }

    /** The content follows the finger, as on Android: down scrolls up, right scrolls left. */
    private fun scrollBy(dx: Float, dy: Float) {
        accX += dx
        accY += dy
        while (abs(accY) >= scrollPx) {
            val up = accY > 0
            click(atX, atY, if (up) WHEEL_UP else WHEEL_DOWN)
            accY += if (up) -scrollPx else scrollPx
            scrolled = true
        }
        while (abs(accX) >= scrollPx) {
            val toLeft = accX > 0
            click(atX, atY, if (toLeft) WHEEL_LEFT else WHEEL_RIGHT)
            accX += if (toLeft) -scrollPx else scrollPx
            scrolled = true
        }
    }

    private fun beginTwo(pts: List<FloatArray>, now: Long) {
        state = State.TWO; t0 = now; scrolled = false
        accX = 0f; accY = 0f
        val (cx, cy) = centroid(pts)
        lastX = cx; lastY = cy
        move(cx, cy)
    }

    private fun centroid(pts: List<FloatArray>): Pair<Float, Float> =
        pts.sumOf { it[0].toDouble() }.toFloat() / pts.size to pts.sumOf { it[1].toDouble() }.toFloat() / pts.size

    private fun dist(p: FloatArray, x: Float, y: Float): Float {
        val dx = p[0] - x
        val dy = p[1] - y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val LEFT = 1
        const val MIDDLE = 2
        const val RIGHT = 4
        const val WHEEL_UP = 8
        const val WHEEL_DOWN = 16
        const val WHEEL_LEFT = 32
        const val WHEEL_RIGHT = 64
        private const val TWO_TAP_MS = 300
        private const val EDGE_HOLD_MS = 700
        private const val FLING_DECAY = 0.94f                   // speed kept per 16 ms while gliding
    }
}
