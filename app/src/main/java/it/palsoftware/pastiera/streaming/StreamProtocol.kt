package it.palsoftware.pastiera.streaming

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The wire formats of a GameStream session, as Moonlight reads them: the RTSP handshake's
 * messages, video and audio over RTP, the control stream's encrypted messages and the input
 * packets inside them. No sockets here, so it can be checked on its own.
 */
internal object StreamProtocol {

    // ---- RTSP ----

    data class RtspRequest(val method: String, val target: String, val cseq: Int, val headers: Map<String, String>, val body: String)

    /** An RTSP request's start line and headers (the body is read after, by its length). */
    fun parseRtspHead(head: String, body: String = ""): RtspRequest? {
        val lines = head.split("\r\n").filter { it.isNotEmpty() }
        val start = lines.firstOrNull()?.split(' ') ?: return null
        if (start.size < 2) return null
        val headers = lines.drop(1).filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        return RtspRequest(start[0], start[1], headers["cseq"]?.toIntOrNull() ?: 0, headers, body)
    }

    fun rtspResponse(cseq: Int, headers: List<Pair<String, String>> = emptyList(), body: String = "", status: String = "200 OK"): ByteArray {
        val text = StringBuilder("RTSP/1.0 $status\r\nCSeq: $cseq\r\n")
        headers.forEach { (name, value) -> text.append(name).append(": ").append(value).append("\r\n") }
        if (body.isNotEmpty()) text.append("Content-Length: ").append(body.toByteArray().size).append("\r\n")
        text.append("\r\n").append(body)
        return text.toString().toByteArray()
    }

    /** An SDP attribute's value ("a=x-nv-video[0].packetSize:1392" -> "1392"). */
    fun sdp(body: String, key: String): String? = body.lineSequence().map { it.trim() }
        .firstOrNull { it.startsWith("a=$key:") }?.substringAfter("a=$key:")?.trim()

    /** What Moonlight is told about the host in DESCRIBE: H.264 only, the control stream's newer encryption, sound for 2, 6 or 8 speakers. */
    fun describe(): String = buildString {
        append("a=x-ss-general.featureFlags:0\n")
        append("a=x-ss-general.encryptionSupported:$ENC_CONTROL_V2\n")
        append("a=x-ss-general.encryptionRequested:$ENC_CONTROL_V2\n")
        // Channels, streams, coupled streams and the speakers' order (Moonlight turns this round itself)
        append("a=fmtp:97 surround-params=642012453\n")
        append("a=fmtp:97 surround-params=85301245673\n")
    }

    const val ENC_CONTROL_V2 = 0x01
    const val ENC_AUDIO = 0x04
    const val FF_AUDIO_ENCRYPTION = 0x20

    // ---- Video: a frame as RTP packets ----

    private const val RTP_HEADER = 16
    private const val NV_HEADER = 16
    private const val MAX_SHARDS = 255
    private const val MAX_BLOCKS = 4

    /**
     * Cuts encoded frames into Moonlight's video packets of [packetSize] (its NV header and
     * payload; the RTP header comes on top). No FEC: every packet of a frame is needed, which
     * is fine on a home network, and a lost frame is answered with a new keyframe.
     */
    class VideoPacketizer(private val packetSize: Int) {
        private var sequence = 0

        fun packetize(frame: ByteArray, frameIndex: Int, keyframe: Boolean, timestamp: Int): List<ByteArray> {
            val shard = packetSize - NV_HEADER
            // Sunshine's short frame header: its type, and the frame's kind (2 a keyframe, 1 not)
            val payloadSize = 8 + frame.size
            val payload = ByteArray(payloadSize)
            payload[0] = 0x01
            payload[3] = if (keyframe) 2 else 1
            val last = payloadSize % shard
            val lastLength = if (last == 0) shard else last
            payload[4] = lastLength.toByte(); payload[5] = (lastLength shr 8).toByte()
            System.arraycopy(frame, 0, payload, 8, frame.size)

            val total = (payloadSize + shard - 1) / shard
            val blocks = ((total + MAX_SHARDS - 1) / MAX_SHARDS).coerceIn(1, MAX_BLOCKS)
            val perBlock = (total + blocks - 1) / blocks
            val packets = ArrayList<ByteArray>(total)
            var offset = 0
            for (block in 0 until blocks) {
                val count = minOf(perBlock, total - block * perBlock)
                for (x in 0 until count) {
                    val packet = ByteArray(RTP_HEADER + NV_HEADER + shard)
                    val buffer = ByteBuffer.wrap(packet)
                    // RTP, with the 4-byte extension Moonlight skips
                    buffer.put(0x90.toByte()).put(0).putShort(sequence.toShort()).putInt(timestamp).putInt(0).putInt(0)
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.putInt(sequence shl 8)
                    buffer.putInt(frameIndex)
                    var flags = 0x1
                    if (x == 0) flags = flags or 0x4
                    if (x == count - 1) flags = flags or 0x2
                    buffer.put(flags.toByte()).put(0).put(0x10).put(((block shl 4) or ((blocks - 1) shl 6)).toByte())
                    buffer.putInt((x shl 12) or (count shl 22))
                    val length = minOf(shard, payloadSize - offset).coerceAtLeast(0)
                    System.arraycopy(payload, offset, packet, RTP_HEADER + NV_HEADER, length)
                    offset += length
                    packets += packet
                    sequence++
                }
            }
            return packets
        }
    }

    // ---- Audio ----

    /** One Opus packet as RTP (payload type 97), encrypted when Moonlight asked for it. */
    fun audioPacket(opus: ByteArray, sequence: Int, timestamp: Int, key: ByteArray?, keyId: Int): ByteArray {
        val payload = if (key == null) opus else {
            // AES-CBC, its IV the key ID plus the sequence number
            val iv = ByteArray(16)
            ByteBuffer.wrap(iv).putInt(keyId + (sequence and 0xFFFF))
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                doFinal(opus)
            }
        }
        return ByteBuffer.allocate(12 + payload.size).put(0x80.toByte()).put(97).putShort(sequence.toShort())
            .putInt(timestamp).putInt(0).put(payload).array()
    }

    // ---- Control stream messages ----

    const val CONTROL_IDR = 0x0302
    const val CONTROL_INVALIDATE = 0x0301
    const val CONTROL_INPUT = 0x0206
    const val CONTROL_TERMINATION = 0x0109

    /** The control stream's messages, encrypted with the session's key (AES-GCM, Sunshine's 12-byte IVs). */
    class ControlCipher(private val key: ByteArray) {
        private var sequence = 0

        private fun iv(seq: Int, origin: Char): ByteArray = ByteArray(12).also {
            it[0] = seq.toByte(); it[1] = (seq shr 8).toByte(); it[2] = (seq shr 16).toByte(); it[3] = (seq shr 24).toByte()
            it[10] = origin.code.toByte(); it[11] = 'C'.code.toByte()
        }

        /** A message for Moonlight. */
        fun seal(type: Int, payload: ByteArray): ByteArray {
            val seq = sequence++
            val plain = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(type.toShort()).putShort(payload.size.toShort()).put(payload).array()
            val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv(seq, 'H')))
                doFinal(plain)
            }
            // Moonlight's order: the tag before the ciphertext
            val cipherLength = sealed.size - 16
            return ByteBuffer.allocate(8 + sealed.size).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(1).putShort((4 + sealed.size).toShort()).putInt(seq)
                .put(sealed, cipherLength, 16).put(sealed, 0, cipherLength).array()
        }

        /** A message from Moonlight: its type and payload, or null when it isn't one. */
        fun open(data: ByteArray): Pair<Int, ByteArray>? = runCatching {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.short.toInt() != 1) return null
            val length = buffer.short.toInt() and 0xFFFF
            val seq = buffer.int
            if (length < 4 + 16 + 4 || data.size < 4 + length) return null
            val tag = data.copyOfRange(8, 24)
            val cipher = data.copyOfRange(24, 4 + length)
            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv(seq, 'C')))
                doFinal(cipher + tag)
            }
            val inner = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN)
            val type = inner.short.toInt() and 0xFFFF
            val size = inner.short.toInt() and 0xFFFF
            type to plain.copyOfRange(4, minOf(plain.size, 4 + size))
        }.getOrNull()
    }

    /** Moonlight's own client side of [ControlCipher.seal], for checking it. */
    internal fun sealAsClient(key: ByteArray, seq: Int, type: Int, payload: ByteArray): ByteArray {
        val plain = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(type.toShort()).putShort(payload.size.toShort()).put(payload).array()
        val iv = ByteArray(12).also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(seq); it[10] = 'C'.code.toByte(); it[11] = 'C'.code.toByte() }
        val sealed = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            doFinal(plain)
        }
        val cipherLength = sealed.size - 16
        return ByteBuffer.allocate(8 + sealed.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1).putShort((4 + sealed.size).toShort()).putInt(seq)
            .put(sealed, cipherLength, 16).put(sealed, 0, cipherLength).array()
    }

    // ---- Input ----

    sealed class Input {
        data class Key(val down: Boolean, val virtualKey: Int, val modifiers: Int) : Input()
        data class MouseMove(val dx: Int, val dy: Int) : Input()
        data class MousePosition(val x: Int, val y: Int, val width: Int, val height: Int) : Input()
        data class MouseButton(val down: Boolean, val button: Int) : Input()
        data class Scroll(val vertical: Int, val horizontal: Int) : Input()
        data class Text(val text: String) : Input()
        data class Pad(
            val number: Int, val buttons: Int, val leftTrigger: Int, val rightTrigger: Int,
            val lx: Int, val ly: Int, val rx: Int, val ry: Int
        ) : Input()
    }

    /** One input packet from Moonlight (its size big-endian, its kind little-endian). */
    fun parseInput(data: ByteArray): Input? {
        if (data.size < 8) return null
        val magic = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val be = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val le = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        return runCatching {
            when (magic) {
                0x03, 0x04 -> Input.Key(magic == 0x03, le.getShort(9).toInt() and 0xFF, data[11].toInt())
                0x07 -> Input.MouseMove(be.getShort(8).toInt(), be.getShort(10).toInt())
                0x05 -> Input.MousePosition(be.getShort(8).toInt(), be.getShort(10).toInt(), be.getShort(14).toInt() + 1, be.getShort(16).toInt() + 1)
                0x08, 0x09 -> Input.MouseButton(magic == 0x08, data[8].toInt() and 0xFF)
                0x0A -> Input.Scroll(be.getShort(8).toInt(), 0)
                0x55000001 -> Input.Scroll(0, be.getShort(8).toInt())
                0x17 -> Input.Text(String(data, 8, data.size - 8, Charsets.UTF_8).trimEnd('\u0000'))
                0x0C -> Input.Pad(
                    number = le.getShort(10).toInt(),
                    buttons = (le.getShort(16).toInt() and 0xFFFF) or ((le.getShort(30).toInt() and 0xFFFF) shl 16),
                    leftTrigger = data[18].toInt() and 0xFF, rightTrigger = data[19].toInt() and 0xFF,
                    lx = le.getShort(20).toInt(), ly = le.getShort(22).toInt(),
                    rx = le.getShort(24).toInt(), ry = le.getShort(26).toInt()
                )
                else -> null
            }
        }.getOrNull()
    }

    // Moonlight's controller buttons
    const val PAD_UP = 0x0001
    const val PAD_DOWN = 0x0002
    const val PAD_LEFT = 0x0004
    const val PAD_RIGHT = 0x0008
    const val PAD_START = 0x0010
    const val PAD_BACK = 0x0020
    const val PAD_LS = 0x0040
    const val PAD_RS = 0x0080
    const val PAD_LB = 0x0100
    const val PAD_RB = 0x0200
    const val PAD_HOME = 0x0400
    const val PAD_A = 0x1000
    const val PAD_B = 0x2000
    const val PAD_X = 0x4000
    const val PAD_Y = 0x8000
}
