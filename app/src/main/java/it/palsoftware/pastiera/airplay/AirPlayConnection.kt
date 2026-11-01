package it.palsoftware.pastiera.airplay

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * HomeKit's encrypted channel: what's sent goes in frames of up to 1024 bytes, each sealed with
 * ChaCha20-Poly1305 under its own counter, its 2-byte length (little-endian) in front and
 * checked as part of it.
 */
internal class HapCipher(private val outKey: ByteArray, private val inKey: ByteArray) {
    private var outCounter = 0L
    private var inCounter = 0L

    @Synchronized
    fun seal(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size + (data.size / FRAME + 1) * 18)
        var pos = 0
        do {
            val size = minOf(FRAME, data.size - pos)
            val length = byteArrayOf(size.toByte(), (size shr 8).toByte())
            out.write(length)
            out.write(HapCrypto.seal(outKey, HapCrypto.counterNonce(outCounter++), data.copyOfRange(pos, pos + size), length))
            pos += size
        } while (pos < data.size)
        return out.toByteArray()
    }

    /** One frame read from [input]: its plain bytes. */
    fun open(input: InputStream): ByteArray {
        val length = input.readExactly(2)
        val size = (length[0].toInt() and 0xFF) or ((length[1].toInt() and 0xFF) shl 8)
        val sealed = input.readExactly(size + 16)
        return HapCrypto.open(inKey, HapCrypto.counterNonce(inCounter++), sealed, length)
            ?: throw IOException("frame didn't decrypt")
    }

    companion object {
        const val FRAME = 1024
    }
}

internal fun InputStream.readExactly(size: Int): ByteArray {
    val data = ByteArray(size)
    var read = 0
    while (read < size) {
        val n = read(data, read, size - read)
        if (n < 0) throw EOFException()
        read += n
    }
    return data
}

/** A request or an answer: its first line, headers (names in lower case) and body. */
internal class Message(val first: String, val headers: Map<String, String>, val body: ByteArray) {
    /** An answer's status (200, 403…); 0 for a request. */
    val code: Int get() = first.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
    val ok: Boolean get() = code in 200..299
    /** The body as a property list, when it is one. */
    val plist: Map<*, *>? get() = Bplist.decode(body) as? Map<*, *>

    companion object {
        fun format(first: String, headers: List<Pair<String, Any>>, body: ByteArray?): ByteArray {
            val text = StringBuilder(first).append("\r\n")
            for ((name, value) in headers) text.append(name).append(": ").append(value).append("\r\n")
            if (body != null && body.isNotEmpty()) text.append("Content-Length: ").append(body.size).append("\r\n")
            text.append("\r\n")
            val head = text.toString().toByteArray(Charsets.UTF_8)
            return if (body == null || body.isEmpty()) head else head + body
        }
    }
}

/**
 * A connection to an AirPlay receiver (or its event channel): HTTP-like requests and answers,
 * encrypted once pairing has given it keys.
 */
internal class AirPlayConnection(private val socket: Socket) : Closeable {
    private val input = BufferedInputStream(socket.getInputStream(), 1 shl 16)
    private val output = socket.getOutputStream()
    @Volatile private var cipher: HapCipher? = null
    private var pending = ByteArray(0)
    private var pendingPos = 0

    val localAddress: InetAddress get() = socket.localAddress
    val remoteAddress: InetAddress get() = socket.inetAddress

    /** Encrypted from now on, both ways. */
    fun encrypt(outKey: ByteArray, inKey: ByteArray) { cipher = HapCipher(outKey, inKey) }

    @Synchronized
    fun request(
        method: String,
        uri: String,
        headers: List<Pair<String, Any>> = emptyList(),
        body: ByteArray? = null,
        protocol: String = "HTTP/1.1"
    ): Message {
        send(Message.format("$method $uri $protocol", headers, body))
        return read()
    }

    @Synchronized
    fun send(data: ByteArray) {
        output.write(cipher?.seal(data) ?: data)
        output.flush()
    }

    private fun readByte(): Int {
        if (pendingPos >= pending.size) {
            val c = cipher
            if (c == null) return input.read()
            pending = c.open(input)
            pendingPos = 0
            if (pending.isEmpty()) return readByte()
        }
        return pending[pendingPos++].toInt() and 0xFF
    }

    private fun line(): String {
        val text = ByteArrayOutputStream()
        while (true) {
            val b = readByte()
            if (b < 0) throw EOFException()
            if (b == '\n'.code) break
            if (b != '\r'.code) text.write(b)
            if (text.size() > 16_384) throw IOException("line too long")
        }
        return text.toString("UTF-8")
    }

    /** The next whole message from the other side. */
    fun read(): Message {
        var first = line()
        while (first.isEmpty()) first = line()
        val headers = LinkedHashMap<String, String>()
        while (true) {
            val header = line()
            if (header.isEmpty()) break
            val colon = header.indexOf(':')
            if (colon > 0) headers[header.substring(0, colon).trim().lowercase()] = header.substring(colon + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 0..(16 shl 20)) throw IOException("body too big")
        val body = ByteArray(length)
        for (i in 0 until length) {
            val b = readByte()
            if (b < 0) throw EOFException()
            body[i] = b.toByte()
        }
        return Message(first, headers, body)
    }

    override fun close() { runCatching { socket.close() } }

    companion object {
        fun open(host: InetAddress, port: Int, timeoutMs: Int = 6_000): AirPlayConnection {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = 15_000
            socket.tcpNoDelay = true
            socket.keepAlive = true
            return AirPlayConnection(socket)
        }
    }
}
