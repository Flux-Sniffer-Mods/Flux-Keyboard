package it.palsoftware.pastiera.linux

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/**
 * The desktop's picture, fitted to the view, and touch and mouse turned into the pointer: touch
 * the way a phone has it ([TouchGestures]: a tap clicks, a drag scrolls, a long-press right-clicks
 * or, moved, drags), a mouse (or the phone's trackpad acting as one) moving, clicking and
 * scrolling as itself.
 */
@SuppressLint("ViewConstructor")
internal class DesktopView(context: Context) : View(context) {
    @Volatile private var client: VncClient? = null
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dirty = Rect()
    private var posted = false
    private val lock = Any()
    private var scale = 1f
    private var left = 0f
    private var top = 0f

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    /** Told the view's size when it's laid out anew. */
    var onSized: (Int, Int) -> Unit = { _, _ -> }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) onSized(w, h)
    }

    fun attach(vnc: VncClient) {
        client = vnc
        post { resize(vnc.width, vnc.height) }
    }

    /** The desktop changed size (or first came): a new picture of it. */
    fun resize(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        synchronized(lock) { dirty.set(0, 0, w, h) }
        apply()
        requestLayout()
    }

    /** Part of the picture changed (on the VNC thread): copied in on the screen's next turn. */
    fun changed(x: Int, y: Int, w: Int, h: Int) {
        synchronized(lock) {
            dirty.union(x, y, x + w, y + h)
            if (posted) return
            posted = true
        }
        post { apply() }
    }

    private fun apply() {
        val vnc = client ?: return
        val target = bitmap ?: return
        val area = synchronized(lock) {
            posted = false
            Rect(dirty).also { dirty.setEmpty() }
        }
        if (area.isEmpty || target.width != vnc.width || target.height != vnc.height) return
        if (!area.intersect(0, 0, target.width, target.height)) return
        runCatching { target.setPixels(vnc.pixels, area.top * vnc.width + area.left, vnc.width, area.left, area.top, area.width(), area.height()) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val picture = bitmap ?: return
        scale = minOf(width.toFloat() / picture.width, height.toFloat() / picture.height)
        left = (width - picture.width * scale) / 2
        top = (height - picture.height * scale) / 2
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        canvas.drawBitmap(picture, 0f, 0f, paint)
        canvas.restore()
    }

    private fun desktopX(x: Float) = ((x - left) / scale).toInt()
    private fun desktopY(y: Float) = ((y - top) / scale).toInt()

    // ---- Touch ----

    /** Touch the way a phone has it (a drag scrolls, a long-press right-clicks or drags): [TouchGestures]. */
    private val gestures = TouchGestures(object : TouchGestures.Desk {
        override val left get() = this@DesktopView.left
        override val top get() = this@DesktopView.top
        override val right get() = this@DesktopView.left + (bitmap?.width ?: 0) * scale
        override val bottom get() = this@DesktopView.top + (bitmap?.height ?: 0) * scale
        override fun pointer(x: Float, y: Float, buttons: Int) {
            val vnc = client ?: return
            val picture = bitmap ?: return
            vnc.pointer(desktopX(x).coerceIn(0, picture.width - 1), desktopY(y).coerceIn(0, picture.height - 1), buttons)
        }
        override fun longPressed() {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }, resources.displayMetrics.density, ViewConfiguration.getLongPressTimeout().toLong())

    /** Each frame while the gestures want it: the long-press timer, a glide, the bottom edge's hold. */
    private var ticking = false
    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            gestures.tick(now)
            if (gestures.ticking(now)) postOnAnimation(this) else ticking = false
        }
    }

    private fun tickWhileNeeded() {
        if (!ticking && gestures.ticking(SystemClock.uptimeMillis())) {
            ticking = true
            postOnAnimation(ticker)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        ticking = false
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vnc = client ?: return true
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(event, vnc)
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gestures.cancel()
        } else {
            // The fingers still down: the one lifting now left out
            val lifting = when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> event.actionIndex
                else -> -1
            }
            val fingers = (0 until event.pointerCount).filter { it != lifting }.map { floatArrayOf(event.getX(it), event.getY(it)) }
            gestures.frame(fingers, event.eventTime)
        }
        tickWhileNeeded()
        return true
    }

    // ---- Mouse ----

    private fun buttons(event: MotionEvent): Int {
        var mask = 0
        if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) mask = mask or LEFT
        if (event.buttonState and MotionEvent.BUTTON_TERTIARY != 0) mask = mask or MIDDLE
        if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) mask = mask or RIGHT
        return mask
    }

    private fun mouse(event: MotionEvent, vnc: VncClient): Boolean {
        vnc.pointer(desktopX(event.x), desktopY(event.y), buttons(event))
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val vnc = client ?: return super.onGenericMotionEvent(event)
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return super.onGenericMotionEvent(event)
        val x = desktopX(event.x)
        val y = desktopY(event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> vnc.pointer(x, y, buttons(event))
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> vnc.pointer(x, y, buttons(event))
            MotionEvent.ACTION_SCROLL -> {
                val down = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val across = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                for ((amount, button) in listOf(down to (if (down > 0) WHEEL_UP else WHEEL_DOWN), across to (if (across > 0) WHEEL_RIGHT else WHEEL_LEFT))) {
                    if (amount == 0f) continue
                    vnc.pointer(x, y, buttons(event) or button)
                    vnc.pointer(x, y, buttons(event))
                }
            }
            else -> return super.onGenericMotionEvent(event)
        }
        return true
    }

    private companion object {
        const val LEFT = 1
        const val MIDDLE = 2
        const val RIGHT = 4
        const val WHEEL_UP = 8
        const val WHEEL_DOWN = 16
        const val WHEEL_LEFT = 32
        const val WHEEL_RIGHT = 64
    }
}
