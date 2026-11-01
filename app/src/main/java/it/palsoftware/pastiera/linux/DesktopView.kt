package it.palsoftware.pastiera.linux

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * The desktop's picture, fitted to the view, and touch and mouse turned into the pointer: a tap
 * clicks, a long press right-clicks, a drag holds the button, two fingers scroll. A mouse (or the
 * phone's trackpad acting as one) moves, clicks and scrolls as itself.
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
        area.intersect(0, 0, target.width, target.height)
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

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pressed = false
    private var longPressed = false
    private var scrollY = 0f
    private val longPress = Runnable {
        val vnc = client ?: return@Runnable
        longPressed = true
        vnc.pointer(desktopX(downX), desktopY(downY), RIGHT)
        vnc.pointer(desktopX(downX), desktopY(downY), 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vnc = client ?: return true
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return mouse(event, vnc)
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y
                dragging = false; pressed = false; longPressed = false
                vnc.pointer(desktopX(x), desktopY(y), 0)
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPress)
                if (pressed) { vnc.pointer(desktopX(x), desktopY(y), 0); pressed = false }
                scrollY = (event.getY(0) + event.getY(1)) / 2
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    // Two fingers: the wheel, a step every so far moved
                    val middle = (event.getY(0) + event.getY(1)) / 2
                    val step = slop * 3
                    while (abs(middle - scrollY) >= step) {
                        val button = if (middle < scrollY) WHEEL_DOWN else WHEEL_UP
                        vnc.pointer(desktopX(downX), desktopY(downY), button)
                        vnc.pointer(desktopX(downX), desktopY(downY), 0)
                        scrollY += if (middle < scrollY) -step else step
                    }
                    return true
                }
                if (longPressed) return true
                if (!dragging && (abs(x - downX) > slop || abs(y - downY) > slop)) {
                    removeCallbacks(longPress)
                    dragging = true
                    pressed = true
                    vnc.pointer(desktopX(downX), desktopY(downY), LEFT)
                }
                if (dragging) vnc.pointer(desktopX(x), desktopY(y), LEFT)
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                when {
                    longPressed -> Unit
                    pressed -> vnc.pointer(desktopX(x), desktopY(y), 0)
                    !dragging -> {
                        vnc.pointer(desktopX(x), desktopY(y), LEFT)
                        vnc.pointer(desktopX(x), desktopY(y), 0)
                    }
                }
                pressed = false
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (pressed) vnc.pointer(desktopX(x), desktopY(y), 0)
                pressed = false
            }
        }
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
                val amount = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (amount != 0f) {
                    val button = if (amount > 0) WHEEL_UP else WHEEL_DOWN
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
    }
}
