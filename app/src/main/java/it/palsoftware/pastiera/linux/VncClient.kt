package it.palsoftware.pastiera.linux

import org.bouncycastle.crypto.engines.DESEngine
import org.bouncycastle.crypto.params.KeyParameter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A VNC (RFB 3.8) client for the Linux desktop's own server on this phone: the password the app
 * gave it, the picture as 32-bit pixels (raw and copied rectangles, the size changing), and the
 * keyboard and pointer sent back. [onFrame] is told which part changed.
 */
internal class VncClient(
    input: InputStream,
    output: OutputStream,
    private val onFrame: (x: Int, y: Int, w: Int, h: Int) -> Unit = { _, _, _, _ -> },
    private val onResize: (w: Int, h: Int) -> Unit = { _, _ -> },
    private val onClipboard: (String) -> Unit = {}
) {
    private val input = DataInputStream(BufferedInputStream(input, 1 shl 16))
    private val output = DataOutputStream(BufferedOutputStream(output, 1 shl 12))
    private var socket: Socket? = null

    @Volatile var width = 0
        private set
    @Volatile var height = 0
        private set
    /** The desktop's pixels, ARGB, a row after another. */
    @Volatile var pixels = IntArray(0)
        private set
    var name = ""
        private set
    @Volatile private var open = true

    /** Greets the server and logs in with [password]; false when it turned it down. */
    fun handshake(password: String): Boolean {
        val version = String(ByteArray(12).also { input.readFully(it) }, Charsets.US_ASCII)
        if (!version.startsWith("RFB ")) throw IOException("not VNC: $version")
        val minor = version.substring(8, 11).toIntOrNull() ?: 3
        val ours = if (minor >= 8) "RFB 003.008\n" else "RFB 003.003\n"
        output.write(ours.toByteArray(Charsets.US_ASCII)); output.flush()
        val type: Int
        if (minor >= 7) {
            val count = input.readUnsignedByte()
            if (count == 0) throw IOException(reason())
            val types = ByteArray(count).also { input.readFully(it) }.map { it.toInt() and 0xFF }
            type = when {
                VNC_AUTH in types -> VNC_AUTH
                NO_AUTH in types -> NO_AUTH
                else -> throw IOException("no way to log in: $types")
            }
            output.writeByte(type); output.flush()
        } else {
            type = input.readInt()
            if (type == 0) throw IOException(reason())
        }
        if (type == VNC_AUTH) {
            val challenge = ByteArray(16).also { input.readFully(it) }
            output.write(response(password, challenge)); output.flush()
        }
        if (type == VNC_AUTH || minor >= 8) {
            if (input.readInt() != 0) return false
        }
        // Shared: the desktop stays as it is for any other viewer
        output.writeByte(1); output.flush()
        width = input.readUnsignedShort()
        height = input.readUnsignedShort()
        input.skipBytes(16)
        name = String(ByteArray(input.readInt()).also { input.readFully(it) }, Charsets.UTF_8)
        pixels = IntArray(width * height)
        // 32-bit pixels, red, green, blue from high to low: Android's own order once alpha's added
        output.writeByte(0); output.write(ByteArray(3))
        output.write(byteArrayOf(32, 24, 0, 1, 0, 255.toByte(), 0, 255.toByte(), 0, 255.toByte(), 16, 8, 0, 0, 0, 0))
        val encodings = intArrayOf(ENCODING_COPY_RECT, ENCODING_RAW, ENCODING_EXTENDED_DESKTOP_SIZE, ENCODING_DESKTOP_SIZE)
        output.writeByte(2); output.writeByte(0); output.writeShort(encodings.size)
        encodings.forEach { output.writeInt(it) }
        output.flush()
        return true
    }

    private fun reason(): String = runCatching {
        String(ByteArray(input.readInt()).also { input.readFully(it) }, Charsets.UTF_8)
    }.getOrDefault("refused")

    /** Asks for the picture: all of it, or only what changed. */
    fun request(incremental: Boolean) = send {
        writeByte(3); writeByte(if (incremental) 1 else 0)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
    }

    /** Reads the server's messages until the connection ends, asking for more after each picture. */
    fun run() {
        request(false)
        while (open) {
            when (val type = input.readUnsignedByte()) {
                0 -> { update(); request(true) }
                1 -> { input.skipBytes(3); input.skipBytes(input.readUnsignedShort() * 6) }
                2 -> Unit
                3 -> {
                    input.skipBytes(3)
                    val text = ByteArray(input.readInt()).also { input.readFully(it) }
                    onClipboard(String(text, Charsets.ISO_8859_1))
                }
                else -> throw IOException("unknown message $type")
            }
        }
    }

    private fun update() {
        input.skipBytes(1)
        val count = input.readUnsignedShort()
        repeat(count) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            when (val encoding = input.readInt()) {
                ENCODING_RAW -> raw(x, y, w, h)
                ENCODING_COPY_RECT -> copy(input.readUnsignedShort(), input.readUnsignedShort(), x, y, w, h)
                ENCODING_DESKTOP_SIZE -> resized(w, h)
                ENCODING_EXTENDED_DESKTOP_SIZE -> {
                    // x is why (0 the server, 1 this client asked), y whether it worked
                    val screens = input.readUnsignedByte()
                    input.skipBytes(3)
                    repeat(screens) { index ->
                        val id = input.readInt()
                        input.skipBytes(8)
                        flags = input.readInt()
                        if (index == 0) screenId = id
                    }
                    resizable = true
                    if (x != 1 || y == 0) resized(w, h)
                }
                else -> throw IOException("unknown encoding $encoding")
            }
        }
    }

    private fun resized(w: Int, h: Int) {
        if (w == width && h == height) return
        width = w; height = h
        pixels = IntArray(w * h)
        onResize(w, h)
    }

    /** The server can be asked for another size (it said so with ExtendedDesktopSize). */
    @Volatile var resizable = false
        private set
    private var screenId = 0
    private var flags = 0

    /** Asks the server to make the desktop [w] x [h] (where [resizable]): it answers with the new size. */
    fun setSize(w: Int, h: Int) = send {
        writeByte(251); writeByte(0); writeShort(w); writeShort(h)
        writeByte(1); writeByte(0)
        writeInt(screenId); writeShort(0); writeShort(0); writeShort(w); writeShort(h); writeInt(flags)
    }

    private val row = ByteArray(4 * 4096)

    private fun raw(x: Int, y: Int, w: Int, h: Int) {
        val target = pixels
        val stride = width
        val row = if (w * 4 <= row.size) row else ByteArray(w * 4)
        for (line in 0 until h) {
            input.readFully(row, 0, w * 4)
            val start = (y + line) * stride + x
            if (y + line >= height || x + w > stride) continue
            var i = 0
            for (col in 0 until w) {
                // Little-endian 0x00RRGGBB
                target[start + col] = (0xFF shl 24) or
                    ((row[i + 2].toInt() and 0xFF) shl 16) or ((row[i + 1].toInt() and 0xFF) shl 8) or (row[i].toInt() and 0xFF)
                i += 4
            }
        }
        onFrame(x, y, w, h)
    }

    private fun copy(sx: Int, sy: Int, x: Int, y: Int, w: Int, h: Int) {
        val target = pixels
        val stride = width
        if (sx + w > stride || x + w > stride || sy + h > height || y + h > height) return
        // Rows in the order that doesn't overwrite what's still to be copied
        val rows = if (y > sy) (h - 1 downTo 0) else (0 until h)
        for (line in rows) System.arraycopy(target, (sy + line) * stride + sx, target, (y + line) * stride + x, w)
        onFrame(x, y, w, h)
    }

    fun key(keysym: Int, down: Boolean) = send {
        writeByte(4); writeByte(if (down) 1 else 0); writeShort(0); writeInt(keysym)
    }

    /** The pointer at [x], [y] with [buttons] held (1 left, 2 middle, 4 right, 8 and 16 the wheel). */
    fun pointer(x: Int, y: Int, buttons: Int) = send {
        writeByte(5); writeByte(buttons)
        writeShort(x.coerceIn(0, maxOf(0, width - 1))); writeShort(y.coerceIn(0, maxOf(0, height - 1)))
    }

    fun clipboard(text: String) = send {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        writeByte(6); write(ByteArray(3)); writeInt(bytes.size); write(bytes)
    }

    private fun send(write: DataOutputStream.() -> Unit) {
        runCatching { synchronized(output) { output.write(); output.flush() } }
    }

    /** Logged in: the picture may now go quiet for as long as nothing changes. */
    fun live() { runCatching { socket?.soTimeout = 0 } }

    fun close() {
        open = false
        runCatching { socket?.close() }
    }

    companion object {
        const val NO_AUTH = 1
        const val VNC_AUTH = 2
        const val ENCODING_RAW = 0
        const val ENCODING_COPY_RECT = 1
        const val ENCODING_DESKTOP_SIZE = -223
        const val ENCODING_EXTENDED_DESKTOP_SIZE = -308

        fun connect(host: String, port: Int, onFrame: (Int, Int, Int, Int) -> Unit, onResize: (Int, Int) -> Unit,
                    onClipboard: (String) -> Unit): VncClient {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 3_000)
            socket.tcpNoDelay = true
            // A server that takes the connection but never speaks is given up on, not waited on forever
            socket.soTimeout = 8_000
            return VncClient(socket.getInputStream(), socket.getOutputStream(), onFrame, onResize, onClipboard)
                .also { it.socket = socket }
        }

        /** VNC's login: the challenge encrypted with DES, the password (8 bytes) as the key, each byte's bits reversed. */
        fun response(password: String, challenge: ByteArray): ByteArray {
            val key = ByteArray(8)
            password.toByteArray(Charsets.ISO_8859_1).take(8).forEachIndexed { i, b -> key[i] = reverse(b) }
            val des = DESEngine().apply { init(true, KeyParameter(key)) }
            val out = ByteArray(16)
            des.processBlock(challenge, 0, out, 0)
            des.processBlock(challenge, 8, out, 8)
            return out
        }

        private fun reverse(b: Byte): Byte {
            var v = b.toInt() and 0xFF
            var r = 0
            repeat(8) { r = (r shl 1) or (v and 1); v = v shr 1 }
            return r.toByte()
        }
    }
}
