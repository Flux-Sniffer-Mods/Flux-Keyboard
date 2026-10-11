package it.palsoftware.pastiera.gaming

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Gaming mode's input, started as the ADB shell user (`app_process`, through the built-in
 * shell or Shizuku): controller buttons and sticks, keys and the mouse, injected as Android's
 * own input. One command a line on its input:
 *
 * - `D id`: the device the events come from (the keyboard's)
 * - `S width height`: the screen, for the mouse
 * - `K 1|0 keyCode g|k`: a key down or up, as a controller button (g) or a keyboard key (k)
 * - `J lx ly rx ry [lt rt]`: both sticks, -1 to 1 (and the triggers, 0 to 1)
 * - `M dx dy`: the mouse moved; `MA x y`: the mouse moved to a point on the screen
 * - `W v h`: the mouse wheel turned, in notches (up and right positive)
 * - `X text`: text typed (Base64, UTF-8), as the keys that type it
 * - `B 1|0 1|2`: the left or right mouse button down or up
 * - `T d|m|u id x y`: a finger down, moved or up on the screen (several at once)
 * - `Z id`: the display the events are for (0, the phone's own; another, a screen of its own)
 * - `G 1|0`: a real controller plugged in or out (run as root: see [VirtualGamepad])
 * - `P index 1|0`: one of its buttons down or up; `H up down left right` (1 or 0): its d-pad;
 *   `A lx ly rx ry`: its sticks, -1 to 1
 */
object GameInputServer {
    private var deviceId = 0
    private var displayId = 0
    private var gamepad: VirtualGamepad? = null
    private var width = 1080f
    private var height = 1080f
    private var mouseX = 540f
    private var mouseY = 540f
    private var buttons = 0
    private var mouseDownTime = 0L
    private val fingers = LinkedHashMap<Int, Pair<Float, Float>>()
    private var touchDownTime = 0L
    private val touchDevice: Int by lazy {
        InputDevice.getDeviceIds().firstOrNull { id ->
            InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_TOUCHSCREEN) == true
        } ?: 0
    }

    private val injector: (InputEvent) -> Unit by lazy {
        // InputManagerGlobal from Android 14, InputManager before
        val manager = runCatching {
            Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)
        }.getOrNull() ?: Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)
        val inject = manager!!.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        val target = runCatching { InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType) }.getOrNull()
        val send: (InputEvent) -> Unit = { event ->
            if (displayId != 0) target?.invoke(event, displayId)
            inject.invoke(manager, event, 0)
        }
        send
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: break
            runCatching { handle(line.trim().split(' ')) }
        }
        // Gaming mode stopped: the controller unplugged
        gamepad?.close()
    }

    private fun handle(parts: List<String>) {
        val now = SystemClock.uptimeMillis()
        when (parts.firstOrNull()) {
            "D" -> deviceId = parts[1].toInt()
            "Z" -> displayId = parts[1].toInt()
            "S" -> {
                val w = parts[1].toFloat(); val h = parts[2].toFloat()
                // The mouse starts in the middle, and goes back there when the screen turns
                if (w != width || h != height) { width = w; height = h; mouseX = width / 2; mouseY = height / 2 }
            }
            "K" -> {
                val action = if (parts[1] == "1") KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                val source = if (parts[3] == "g") InputDevice.SOURCE_GAMEPAD else InputDevice.SOURCE_KEYBOARD
                injector(KeyEvent(now, now, action, parts[2].toInt(), 0, 0, deviceId, 0, 0, source))
            }
            "J" -> {
                val coords = MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_X, parts[1].toFloat())
                    setAxisValue(MotionEvent.AXIS_Y, parts[2].toFloat())
                    setAxisValue(MotionEvent.AXIS_Z, parts[3].toFloat())
                    setAxisValue(MotionEvent.AXIS_RZ, parts[4].toFloat())
                    parts.getOrNull(5)?.toFloat()?.let { setAxisValue(MotionEvent.AXIS_LTRIGGER, it); setAxisValue(MotionEvent.AXIS_BRAKE, it) }
                    parts.getOrNull(6)?.toFloat()?.let { setAxisValue(MotionEvent.AXIS_RTRIGGER, it); setAxisValue(MotionEvent.AXIS_GAS, it) }
                }
                val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_UNKNOWN }
                val event = MotionEvent.obtain(
                    now, now, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coords),
                    0, 0, 1f, 1f, deviceId, 0, InputDevice.SOURCE_JOYSTICK, 0
                )
                injector(event)
                event.recycle()
            }
            "G" -> if (parts[1] == "1") { if (gamepad == null) gamepad = VirtualGamepad.create() } else { gamepad?.close(); gamepad = null }
            "P" -> gamepad?.button(parts[1].toInt(), parts[2] == "1")
            "H" -> gamepad?.dpad(parts[1] == "1", parts[2] == "1", parts[3] == "1", parts[4] == "1")
            "A" -> gamepad?.sticks(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat())
            "M" -> {
                mouseX = (mouseX + parts[1].toFloat()).coerceIn(0f, width - 1)
                mouseY = (mouseY + parts[2].toFloat()).coerceIn(0f, height - 1)
                mouse(if (buttons != 0) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_HOVER_MOVE, now)
            }
            "MA" -> {
                mouseX = parts[1].toFloat().coerceIn(0f, width - 1)
                mouseY = parts[2].toFloat().coerceIn(0f, height - 1)
                mouse(if (buttons != 0) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_HOVER_MOVE, now)
            }
            "W" -> scroll(parts[1].toFloat(), parts[2].toFloat(), now)
            "X" -> {
                val text = String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT), Charsets.UTF_8)
                android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
                    ?.forEach { injector(it) }
            }
            "T" -> touch(parts[1], parts[2].toInt(), parts.getOrNull(3)?.toFloat() ?: 0f, parts.getOrNull(4)?.toFloat() ?: 0f, now)
            "B" -> {
                val button = if (parts[2] == "2") MotionEvent.BUTTON_SECONDARY else MotionEvent.BUTTON_PRIMARY
                if (parts[1] == "1") {
                    val first = buttons == 0
                    buttons = buttons or button
                    if (first) { mouseDownTime = now; mouse(MotionEvent.ACTION_DOWN, now) }
                    mouse(MotionEvent.ACTION_BUTTON_PRESS, now, button)
                } else {
                    buttons = buttons and button.inv()
                    mouse(MotionEvent.ACTION_BUTTON_RELEASE, now, button)
                    if (buttons == 0) mouse(MotionEvent.ACTION_UP, now)
                }
            }
        }
    }

    private fun touch(kind: String, id: Int, x: Float, y: Float, now: Long) {
        when (kind) {
            "d" -> {
                if (id in fingers) return
                if (fingers.isEmpty()) touchDownTime = now
                fingers[id] = x to y
                val index = fingers.keys.indexOf(id)
                sendTouch(if (fingers.size == 1) MotionEvent.ACTION_DOWN
                    else MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), now)
            }
            "m" -> {
                if (id !in fingers) return
                fingers[id] = x to y
                sendTouch(MotionEvent.ACTION_MOVE, now)
            }
            "u" -> {
                if (id !in fingers) return
                val index = fingers.keys.indexOf(id)
                sendTouch(if (fingers.size == 1) MotionEvent.ACTION_UP
                    else MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), now)
                fingers.remove(id)
            }
        }
    }

    private fun sendTouch(action: Int, now: Long) {
        val properties = fingers.keys.map { id ->
            MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coords = fingers.values.map { (x, y) ->
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            touchDownTime, now, action, fingers.size, properties, coords,
            0, 0, 1f, 1f, touchDevice, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        injector(event)
        event.recycle()
    }

    private fun scroll(vertical: Float, horizontal: Float, now: Long) {
        val coords = MotionEvent.PointerCoords().apply {
            x = mouseX; y = mouseY
            setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
            setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
        }
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coords),
            0, buttons, 1f, 1f, deviceId, 0, InputDevice.SOURCE_MOUSE, 0)
        injector(event)
        event.recycle()
    }

    private fun mouse(action: Int, now: Long, actionButton: Int = 0) {
        val coords = MotionEvent.PointerCoords().apply { x = mouseX; y = mouseY }
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val event = MotionEvent.obtain(
            if (buttons != 0 || action == MotionEvent.ACTION_UP) mouseDownTime else now, now, action, 1,
            arrayOf(properties), arrayOf(coords), 0, buttons, 1f, 1f, deviceId, 0, InputDevice.SOURCE_MOUSE, 0
        )
        if (actionButton != 0) runCatching {
            MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType).invoke(event, actionButton)
        }
        injector(event)
        event.recycle()
    }
}
