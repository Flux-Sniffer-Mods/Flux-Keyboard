package it.palsoftware.pastiera.gaming

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A real controller, made with root through the kernel's UHID device: Android, emulators and
 * launchers see a plugged-in gamepad (16 buttons, a d-pad, two analogue sticks), not keys.
 * Runs inside gaming mode's input helper, which root starts as root. Closed, it's unplugged.
 */
class VirtualGamepad private constructor(private val uhid: RandomAccessFile) {
    private var buttons = 0
    private var hat = 8
    private val axes = IntArray(4)

    /** A button down or up: 0 A, 1 B, 3 X, 4 Y, 6 L1, 7 R1, 8 L2, 9 R2, 10 Select, 11 Start, 12 Home, 13 L3, 14 R3. */
    fun button(index: Int, down: Boolean) {
        buttons = if (down) buttons or (1 shl index) else buttons and (1 shl index).inv()
        send()
    }

    /** The d-pad from its four directions held. */
    fun dpad(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        hat = hatOf(up, down, left, right)
        send()
    }

    /** Both sticks, -1 to 1: left x, y, right x, y. */
    fun sticks(lx: Float, ly: Float, rx: Float, ry: Float) {
        floatArrayOf(lx, ly, rx, ry).forEachIndexed { i, v -> axes[i] = (v.coerceIn(-1f, 1f) * 127).toInt() }
        send()
    }

    private fun send() {
        val report = report(buttons, hat, axes)
        val event = ByteBuffer.allocate(4 + 2 + report.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(UHID_INPUT2).putShort(report.size.toShort()).put(report)
        runCatching { uhid.write(event.array()) }
    }

    fun close() {
        runCatching { uhid.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(UHID_DESTROY).array()) }
        runCatching { uhid.close() }
    }

    companion object {
        private const val UHID_DESTROY = 1
        private const val UHID_CREATE2 = 11
        private const val UHID_INPUT2 = 12

        /**
         * The controller as HID describes it: 16 buttons (Android reads 1 to 15 as A, B, C, X,
         * Y, Z, L1, R1, L2, R2, Select, Start, Home, L3, R3), a hat as the d-pad, and X, Y, Z,
         * Rz as the two sticks.
         */
        internal val DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xA1.toByte(), 0x01,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,
            0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14,
            0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x42, 0x65, 0x00,
            0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x03,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35, 0x15, 0x81.toByte(), 0x25, 0x7F,
            0x75, 0x08, 0x95.toByte(), 0x04, 0x81.toByte(), 0x02,
            0xC0.toByte()
        )

        /** One input report: the buttons, the hat (8: none), then the four axes. */
        internal fun report(buttons: Int, hat: Int, axes: IntArray): ByteArray = byteArrayOf(
            (buttons and 0xFF).toByte(), ((buttons shr 8) and 0xFF).toByte(), (hat and 0x0F).toByte(),
            axes[0].toByte(), axes[1].toByte(), axes[2].toByte(), axes[3].toByte()
        )

        /** The hat's direction (0 up, clockwise to 7 up-left), 8 when nothing is held. */
        internal fun hatOf(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Int {
            val y = (if (down) 1 else 0) - (if (up) 1 else 0)
            val x = (if (right) 1 else 0) - (if (left) 1 else 0)
            return when (y to x) {
                -1 to 0 -> 0; -1 to 1 -> 1; 0 to 1 -> 2; 1 to 1 -> 3
                1 to 0 -> 4; 1 to -1 -> 5; 0 to -1 -> 6; -1 to -1 -> 7
                else -> 8
            }
        }

        /** Plugs the controller in (needs root to open /dev/uhid); null when it can't. */
        fun create(): VirtualGamepad? = runCatching {
            val uhid = RandomAccessFile("/dev/uhid", "rw")
            val name = "Flux Keyboard controller".toByteArray()
            val event = ByteBuffer.allocate(4 + 128 + 64 + 64 + 2 + 2 + 4 + 4 + 4 + 4 + DESCRIPTOR.size).order(ByteOrder.LITTLE_ENDIAN)
            event.putInt(UHID_CREATE2)
            event.put(name.copyOf(128)).put(ByteArray(64)).put(ByteArray(64))
            event.putShort(DESCRIPTOR.size.toShort())
            event.putShort(0x03) // USB
            event.putInt(0x1209) // pid.codes: Android's generic controller layout applies
            event.putInt(0x5846)
            event.putInt(1)
            event.putInt(0)
            event.put(DESCRIPTOR)
            uhid.write(event.array())
            // The kernel's own messages to the device (start, open, close): read and let go
            Thread {
                val buffer = ByteArray(4380)
                runCatching { while (uhid.read(buffer) >= 0) Unit }
            }.apply { isDaemon = true; start() }
            VirtualGamepad(uhid)
        }.getOrNull()
    }
}
