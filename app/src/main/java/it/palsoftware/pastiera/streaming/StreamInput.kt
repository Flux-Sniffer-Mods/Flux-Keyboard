package it.palsoftware.pastiera.streaming

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.adb.RootShell
import it.palsoftware.pastiera.gaming.GameInputServer
import it.palsoftware.pastiera.streaming.StreamProtocol.Input
import java.io.Writer

/**
 * Moonlight's keyboard, mouse and controllers, played on the phone through gaming mode's input
 * helper (run by the shell): keys as keys, the mouse as a mouse on the screen as it shows on the
 * Moonlight client, controllers as a real controller with root, else as controller buttons and sticks.
 */
internal class StreamInput(private val context: Context, private val streamWidth: Int, private val streamHeight: Int) {
    private var process: Process? = null
    private var input: Writer? = null
    private val pads = HashMap<Int, Input.Pad>()
    private var padPlugged = false
    private val root = RootShell.active
    /** A screen of its own the input goes to (Separate screen), else the phone's. */
    @Volatile private var screen: it.palsoftware.pastiera.display.ExtraScreen.Screen? = null

    /** Input to [target] from now on (null: the phone's screen). */
    fun target(target: it.palsoftware.pastiera.display.ExtraScreen.Screen?) {
        screen = target
        send("Z ${target?.id ?: 0}")
        val (w, h) = screen()
        send("S $w $h")
    }

    fun start() {
        val apk = context.applicationInfo.sourceDir
        runCatching {
            val started = AdbShell.newProcess(arrayOf("sh", "-c", "CLASSPATH='$apk' exec app_process /system/bin ${GameInputServer::class.java.name}"))
            listOf(started.inputStream, started.errorStream).forEach { stream ->
                Thread { runCatching { val buffer = ByteArray(256); while (stream.read(buffer) >= 0) Unit } }.apply { isDaemon = true; start() }
            }
            process = started
            input = started.outputStream.bufferedWriter()
            val (w, h) = screen()
            send("S $w $h")
            keyboardDevice()?.let { send("D $it") }
        }.onFailure { Log.w(TAG, "no input: $it") }
    }

    fun stop() {
        if (padPlugged) send("G 0")
        runCatching { input?.close() }
        input = null
        process?.destroy(); process = null
    }

    @Synchronized
    private fun send(line: String) {
        val writer = input ?: return
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }
    }

    fun handle(event: Input) {
        when (event) {
            is Input.Key -> keyCode(event.virtualKey)?.let { send("K ${if (event.down) 1 else 0} $it k") }
            is Input.MouseMove -> send("M ${event.dx} ${event.dy}")
            is Input.MousePosition -> position(event)
            is Input.MouseButton -> when (event.button) {
                1 -> send("B ${if (event.down) 1 else 0} 1")
                3 -> send("B ${if (event.down) 1 else 0} 2")
                // The mouse's back button: Android's back
                4 -> send("K ${if (event.down) 1 else 0} ${KeyEvent.KEYCODE_BACK} k")
                else -> Unit
            }
            is Input.Scroll -> send("W ${event.vertical / 120f} ${event.horizontal / 120f}")
            is Input.Text -> send("X " + android.util.Base64.encodeToString(event.text.toByteArray(), android.util.Base64.NO_WRAP))
            is Input.Pad -> pad(event)
        }
    }

    /** The screen's size the way it's turned now. */
    private fun screen(): Pair<Int, Int> {
        screen?.let { return it.width to it.height }
        val size = Point()
        @Suppress("DEPRECATION")
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(size)
        return size.x.coerceAtLeast(1) to size.y.coerceAtLeast(1)
    }

    /** A point on the Moonlight client, on the phone's screen: the screen is fitted in the middle of the stream. */
    private fun position(event: Input.MousePosition) {
        val (w, h) = screen()
        send("S $w $h")
        val scale = minOf(streamWidth.toFloat() / w, streamHeight.toFloat() / h)
        val left = (streamWidth - w * scale) / 2
        val top = (streamHeight - h * scale) / 2
        val x = (event.x.toFloat() * streamWidth / event.width - left) / scale
        val y = (event.y.toFloat() * streamHeight / event.height - top) / scale
        send("MA ${x.coerceIn(0f, w - 1f)} ${y.coerceIn(0f, h - 1f)}")
    }

    private fun pad(event: Input.Pad) {
        val before = pads[event.number] ?: Input.Pad(event.number, 0, 0, 0, 0, 0, 0, 0)
        pads[event.number] = event
        // Sticks the Android way round: down is positive
        fun axis(v: Int) = (v / 32767f).coerceIn(-1f, 1f)
        val lx = axis(event.lx); val ly = -axis(event.ly); val rx = axis(event.rx); val ry = -axis(event.ry)
        val changed = event.buttons xor before.buttons
        val l2 = event.leftTrigger > 30; val r2 = event.rightTrigger > 30
        val l2Before = before.leftTrigger > 30; val r2Before = before.rightTrigger > 30
        if (root) {
            if (!padPlugged) { send("G 1"); padPlugged = true }
            for ((flag, index) in UHID_BUTTONS) if (changed and flag != 0) send("P $index ${if (event.buttons and flag != 0) 1 else 0}")
            if (l2 != l2Before) send("P 8 ${if (l2) 1 else 0}")
            if (r2 != r2Before) send("P 9 ${if (r2) 1 else 0}")
            if (changed and DPAD != 0) {
                val b = event.buttons
                send("H ${bit(b, StreamProtocol.PAD_UP)} ${bit(b, StreamProtocol.PAD_DOWN)} ${bit(b, StreamProtocol.PAD_LEFT)} ${bit(b, StreamProtocol.PAD_RIGHT)}")
            }
            send("A $lx $ly $rx $ry")
        } else {
            for ((flag, code) in KEY_BUTTONS) if (changed and flag != 0) send("K ${if (event.buttons and flag != 0) 1 else 0} $code g")
            if (l2 != l2Before) send("K ${if (l2) 1 else 0} ${KeyEvent.KEYCODE_BUTTON_L2} g")
            if (r2 != r2Before) send("K ${if (r2) 1 else 0} ${KeyEvent.KEYCODE_BUTTON_R2} g")
            send("J $lx $ly $rx $ry ${event.leftTrigger / 255f} ${event.rightTrigger / 255f}")
        }
    }

    private fun bit(buttons: Int, flag: Int) = if (buttons and flag != 0) 1 else 0

    private fun keyboardDevice(): Int? = android.view.InputDevice.getDeviceIds().firstOrNull { id ->
        val device = android.view.InputDevice.getDevice(id) ?: return@firstOrNull false
        !device.isVirtual && device.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    companion object {
        private const val TAG = "StreamInput"
        private const val DPAD = StreamProtocol.PAD_UP or StreamProtocol.PAD_DOWN or StreamProtocol.PAD_LEFT or StreamProtocol.PAD_RIGHT

        private val UHID_BUTTONS = listOf(
            StreamProtocol.PAD_A to 0, StreamProtocol.PAD_B to 1, StreamProtocol.PAD_X to 3, StreamProtocol.PAD_Y to 4,
            StreamProtocol.PAD_LB to 6, StreamProtocol.PAD_RB to 7, StreamProtocol.PAD_BACK to 10, StreamProtocol.PAD_START to 11,
            StreamProtocol.PAD_HOME to 12, StreamProtocol.PAD_LS to 13, StreamProtocol.PAD_RS to 14
        )

        private val KEY_BUTTONS = listOf(
            StreamProtocol.PAD_A to KeyEvent.KEYCODE_BUTTON_A, StreamProtocol.PAD_B to KeyEvent.KEYCODE_BUTTON_B,
            StreamProtocol.PAD_X to KeyEvent.KEYCODE_BUTTON_X, StreamProtocol.PAD_Y to KeyEvent.KEYCODE_BUTTON_Y,
            StreamProtocol.PAD_LB to KeyEvent.KEYCODE_BUTTON_L1, StreamProtocol.PAD_RB to KeyEvent.KEYCODE_BUTTON_R1,
            StreamProtocol.PAD_BACK to KeyEvent.KEYCODE_BUTTON_SELECT, StreamProtocol.PAD_START to KeyEvent.KEYCODE_BUTTON_START,
            StreamProtocol.PAD_HOME to KeyEvent.KEYCODE_BUTTON_MODE, StreamProtocol.PAD_LS to KeyEvent.KEYCODE_BUTTON_THUMBL,
            StreamProtocol.PAD_RS to KeyEvent.KEYCODE_BUTTON_THUMBR,
            StreamProtocol.PAD_UP to KeyEvent.KEYCODE_DPAD_UP, StreamProtocol.PAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            StreamProtocol.PAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT, StreamProtocol.PAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT
        )

        /** A Windows virtual key (what Moonlight sends) as Android's key. */
        internal fun keyCode(vk: Int): Int? = when (vk) {
            in 0x30..0x39 -> KeyEvent.KEYCODE_0 + (vk - 0x30)
            in 0x41..0x5A -> KeyEvent.KEYCODE_A + (vk - 0x41)
            in 0x60..0x69 -> KeyEvent.KEYCODE_NUMPAD_0 + (vk - 0x60)
            in 0x70..0x7B -> KeyEvent.KEYCODE_F1 + (vk - 0x70)
            else -> VK[vk]
        }

        private val VK = mapOf(
            0x08 to KeyEvent.KEYCODE_DEL, 0x09 to KeyEvent.KEYCODE_TAB, 0x0D to KeyEvent.KEYCODE_ENTER,
            0x10 to KeyEvent.KEYCODE_SHIFT_LEFT, 0x11 to KeyEvent.KEYCODE_CTRL_LEFT, 0x12 to KeyEvent.KEYCODE_ALT_LEFT,
            0x13 to KeyEvent.KEYCODE_BREAK, 0x14 to KeyEvent.KEYCODE_CAPS_LOCK, 0x1B to KeyEvent.KEYCODE_ESCAPE,
            0x20 to KeyEvent.KEYCODE_SPACE, 0x21 to KeyEvent.KEYCODE_PAGE_UP, 0x22 to KeyEvent.KEYCODE_PAGE_DOWN,
            0x23 to KeyEvent.KEYCODE_MOVE_END, 0x24 to KeyEvent.KEYCODE_MOVE_HOME,
            0x25 to KeyEvent.KEYCODE_DPAD_LEFT, 0x26 to KeyEvent.KEYCODE_DPAD_UP, 0x27 to KeyEvent.KEYCODE_DPAD_RIGHT, 0x28 to KeyEvent.KEYCODE_DPAD_DOWN,
            0x2C to KeyEvent.KEYCODE_SYSRQ, 0x2D to KeyEvent.KEYCODE_INSERT, 0x2E to KeyEvent.KEYCODE_FORWARD_DEL,
            0x5B to KeyEvent.KEYCODE_META_LEFT, 0x5C to KeyEvent.KEYCODE_META_RIGHT, 0x5D to KeyEvent.KEYCODE_MENU,
            0x6A to KeyEvent.KEYCODE_NUMPAD_MULTIPLY, 0x6B to KeyEvent.KEYCODE_NUMPAD_ADD, 0x6D to KeyEvent.KEYCODE_NUMPAD_SUBTRACT,
            0x6E to KeyEvent.KEYCODE_NUMPAD_DOT, 0x6F to KeyEvent.KEYCODE_NUMPAD_DIVIDE,
            0x90 to KeyEvent.KEYCODE_NUM_LOCK, 0x91 to KeyEvent.KEYCODE_SCROLL_LOCK,
            0xA0 to KeyEvent.KEYCODE_SHIFT_LEFT, 0xA1 to KeyEvent.KEYCODE_SHIFT_RIGHT, 0xA2 to KeyEvent.KEYCODE_CTRL_LEFT,
            0xA3 to KeyEvent.KEYCODE_CTRL_RIGHT, 0xA4 to KeyEvent.KEYCODE_ALT_LEFT, 0xA5 to KeyEvent.KEYCODE_ALT_RIGHT,
            0xA6 to KeyEvent.KEYCODE_BACK,
            0xBA to KeyEvent.KEYCODE_SEMICOLON, 0xBB to KeyEvent.KEYCODE_EQUALS, 0xBC to KeyEvent.KEYCODE_COMMA,
            0xBD to KeyEvent.KEYCODE_MINUS, 0xBE to KeyEvent.KEYCODE_PERIOD, 0xBF to KeyEvent.KEYCODE_SLASH,
            0xC0 to KeyEvent.KEYCODE_GRAVE, 0xDB to KeyEvent.KEYCODE_LEFT_BRACKET, 0xDC to KeyEvent.KEYCODE_BACKSLASH,
            0xDD to KeyEvent.KEYCODE_RIGHT_BRACKET, 0xDE to KeyEvent.KEYCODE_APOSTROPHE
        )
    }
}
