package it.palsoftware.pastiera.airplay

import it.palsoftware.pastiera.FluxLog
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

/**
 * MPEG-TS, the container HLS plays: H.264 and AAC (ADTS) in 188-byte packets, with the tables
 * that say which is which at the start of each segment and the clock on every picture.
 */
internal class TsMuxer(private val withAudio: Boolean) {
    private val counters = HashMap<Int, Int>()

    private fun counter(pid: Int): Int {
        val c = counters[pid] ?: 0
        counters[pid] = (c + 1) and 0xF
        return c
    }

    /** The program tables (PAT and PMT): written first in every segment. */
    fun tables(out: ByteArrayOutputStream) {
        section(out, PID_PAT, byteArrayOf(
            0x00, 0xB0.toByte(), 0x0D, 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            0x00, 0x01, (0xE0 or (PID_PMT shr 8)).toByte(), (PID_PMT and 0xFF).toByte()
        ))
        val streams = ByteArrayOutputStream()
        streams.write(byteArrayOf(0x1B, (0xE0 or (PID_VIDEO shr 8)).toByte(), (PID_VIDEO and 0xFF).toByte(), 0xF0.toByte(), 0x00))
        if (withAudio) streams.write(byteArrayOf(0x0F, (0xE0 or (PID_AUDIO shr 8)).toByte(), (PID_AUDIO and 0xFF).toByte(), 0xF0.toByte(), 0x00))
        val length = 9 + streams.size() + 4
        section(out, PID_PMT, byteArrayOf(
            0x02, (0xB0 or (length shr 8)).toByte(), (length and 0xFF).toByte(), 0x00, 0x01, 0xC1.toByte(), 0x00, 0x00,
            (0xE0 or (PID_VIDEO shr 8)).toByte(), (PID_VIDEO and 0xFF).toByte(), 0xF0.toByte(), 0x00
        ) + streams.toByteArray())
    }

    private fun section(out: ByteArrayOutputStream, pid: Int, body: ByteArray) {
        val crc = crc32(body)
        val packet = ByteArray(PACKET) { 0xFF.toByte() }
        packet[0] = SYNC
        packet[1] = (0x40 or (pid shr 8)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x10 or counter(pid)).toByte()
        packet[4] = 0
        body.copyInto(packet, 5)
        for (i in 0 until 4) packet[5 + body.size + i] = (crc ushr (24 - 8 * i)).toByte()
        out.write(packet)
    }

    /** A picture (Annex B, its SPS and PPS in front on a keyframe) at [pts] (90 kHz). */
    fun video(out: ByteArrayOutputStream, frame: ByteArray, pts: Long, key: Boolean) {
        // Each picture opens with an access unit delimiter, as Apple's players expect
        val aud = startsWithAud(frame)
        val payload = if (aud) frame else AUD + frame
        pes(out, PID_VIDEO, 0xE0, payload, pts, pcr = maxOf(0, pts - PCR_LEAD), key = key, bounded = false)
    }

    /** One AAC frame with its ADTS header, at [pts] (90 kHz). */
    fun audio(out: ByteArrayOutputStream, adts: ByteArray, pts: Long) {
        if (withAudio) pes(out, PID_AUDIO, 0xC0, adts, pts, pcr = null, key = false, bounded = true)
    }

    private fun pes(out: ByteArrayOutputStream, pid: Int, streamId: Int, payload: ByteArray, pts: Long, pcr: Long?, key: Boolean, bounded: Boolean) {
        val header = ByteArray(14)
        header[2] = 1
        header[3] = streamId.toByte()
        val length = if (bounded) 8 + payload.size else 0
        if (length > 0xFFFF) return
        header[4] = (length shr 8).toByte(); header[5] = length.toByte()
        header[6] = 0x80.toByte(); header[7] = 0x80.toByte(); header[8] = 5
        header[9] = (0x21 or ((pts shr 29) and 0x0E).toInt()).toByte()
        header[10] = (pts shr 22).toByte()
        header[11] = (((pts shr 14) and 0xFE) or 1).toByte()
        header[12] = (pts shr 7).toByte()
        header[13] = (((pts shl 1) and 0xFE) or 1).toByte()
        val pes = header + payload
        var pos = 0
        var first = true
        while (pos < pes.size) {
            val remaining = pes.size - pos
            val fields = first && (pcr != null || key)
            var afBody = if (fields) 1 + (if (pcr != null) 6 else 0) else 0
            var adaptation = fields
            var space = PACKET - 4 - (if (fields) 1 + afBody else 0)
            if (remaining < space) {
                val stuff = space - remaining
                if (adaptation) afBody += stuff else { adaptation = true; afBody = stuff - 1 }
                space = remaining
            }
            val packet = ByteArray(PACKET) { 0xFF.toByte() }
            packet[0] = SYNC
            packet[1] = ((if (first) 0x40 else 0) or (pid shr 8)).toByte()
            packet[2] = (pid and 0xFF).toByte()
            packet[3] = ((if (adaptation) 0x30 else 0x10) or counter(pid)).toByte()
            var at = 4
            if (adaptation) {
                packet[at++] = afBody.toByte()
                if (afBody > 0) {
                    var flags = 0
                    if (fields && key) flags = flags or 0x40
                    if (fields && pcr != null) flags = flags or 0x10
                    packet[at] = flags.toByte()
                    if (fields && pcr != null) {
                        packet[at + 1] = (pcr shr 25).toByte()
                        packet[at + 2] = (pcr shr 17).toByte()
                        packet[at + 3] = (pcr shr 9).toByte()
                        packet[at + 4] = (pcr shr 1).toByte()
                        packet[at + 5] = (((pcr and 1) shl 7) or 0x7E).toByte()
                        packet[at + 6] = 0
                    }
                    at += afBody
                }
            }
            pes.copyInto(packet, at, pos, pos + space)
            pos += space
            out.write(packet)
            first = false
        }
    }

    companion object {
        const val PACKET = 188
        private const val SYNC = 0x47.toByte()
        private const val PID_PAT = 0x0000
        private const val PID_PMT = 0x1000
        const val PID_VIDEO = 0x0100
        const val PID_AUDIO = 0x0101
        /** The clock runs a little ahead of the pictures it times. */
        private const val PCR_LEAD = 9_000L
        private val AUD = byteArrayOf(0, 0, 0, 1, 0x09, 0xF0.toByte())

        private fun startsWithAud(frame: ByteArray): Boolean {
            val at = when {
                frame.size > 4 && frame[0] == 0.toByte() && frame[1] == 0.toByte() && frame[2] == 0.toByte() && frame[3] == 1.toByte() -> 4
                frame.size > 3 && frame[0] == 0.toByte() && frame[1] == 0.toByte() && frame[2] == 1.toByte() -> 3
                else -> return false
            }
            return frame[at].toInt() and 0x1F == 9
        }

        /** MPEG-2's CRC-32 (not zip's): the tables' check. */
        fun crc32(data: ByteArray): Int {
            var crc = -1
            for (b in data) {
                crc = crc xor ((b.toInt() and 0xFF) shl 24)
                repeat(8) { crc = if (crc and Int.MIN_VALUE != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return crc
        }

        /** The 7-byte ADTS header for one AAC-LC frame of [size] bytes. */
        fun adts(size: Int, sampleRate: Int = 48_000, channels: Int = 2): ByteArray {
            val rates = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)
            val index = rates.indexOf(sampleRate).takeIf { it >= 0 } ?: 3
            val length = size + 7
            return byteArrayOf(
                0xFF.toByte(), 0xF1.toByte(),
                ((1 shl 6) or (index shl 2) or (channels shr 2)).toByte(),
                (((channels and 3) shl 6) or (length shr 11)).toByte(),
                (length shr 3).toByte(),
                (((length and 7) shl 5) or 0x1F).toByte(),
                0xFC.toByte()
            )
        }
    }
}

/**
 * The live picture and sound cut into HLS segments of about a second, each starting on a
 * keyframe, the last few kept for the TV to fetch. [wantKeyframe] is asked for one as a segment
 * nears its length.
 */
internal class HlsStream(withAudio: Boolean, private val wantKeyframe: () -> Unit = {}) {
    class Segment(val sequence: Int, val duration: Double, val data: ByteArray)

    private val muxer = TsMuxer(withAudio)
    private val segments = ArrayDeque<Segment>()
    private var current: ByteArrayOutputStream? = null
    private var sequence = 0
    private var segmentStartUs = 0L
    private var baseUs = -1L
    private var config = ByteArray(0)
    private var asked = false

    private fun pts(us: Long) = (us - baseUs) * 9 / 100 + OFFSET

    @Synchronized
    fun video(data: ByteArray, ptsUs: Long, key: Boolean, isConfig: Boolean) {
        if (isConfig) { config = data; return }
        if (baseUs < 0) {
            if (!key) { wantKeyframe(); return }
            baseUs = ptsUs
        }
        if (ptsUs < baseUs) return
        val length = (ptsUs - segmentStartUs) / 1e6
        val open = current
        if (open == null || (key && length >= MIN_SECONDS) || length >= MAX_SECONDS) {
            if (open != null) close(open, length)
            val next = ByteArrayOutputStream(1 shl 20)
            muxer.tables(next)
            current = next
            segmentStartUs = ptsUs
            asked = false
        } else if (!asked && length >= ASK_SECONDS) {
            asked = true
            wantKeyframe()
        }
        muxer.video(current!!, if (key) config + data else data, pts(ptsUs), key)
    }

    @Synchronized
    fun audio(adts: ByteArray, ptsUs: Long) {
        val open = current ?: return
        if (ptsUs < baseUs) return
        muxer.audio(open, adts, pts(ptsUs))
    }

    private fun close(data: ByteArrayOutputStream, duration: Double) {
        segments.addLast(Segment(sequence++, duration, data.toByteArray()))
        while (segments.size > KEPT) segments.removeFirst()
    }

    @Synchronized
    fun ready(count: Int = LISTED): Boolean = segments.size >= count

    @Synchronized
    fun segment(number: Int): ByteArray? = segments.firstOrNull { it.sequence == number }?.data

    /** The live playlist: the newest segments, no end. */
    @Synchronized
    fun playlist(): String {
        val listed = segments.toList().takeLast(LISTED)
        val text = StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:$TARGET\n")
        text.append("#EXT-X-MEDIA-SEQUENCE:").append(listed.firstOrNull()?.sequence ?: 0).append('\n')
        for (segment in listed) {
            text.append(String.format(Locale.US, "#EXTINF:%.3f,\n", segment.duration))
            text.append("s").append(segment.sequence).append(".ts\n")
        }
        return text.toString()
    }

    companion object {
        /** Seconds a segment lasts at most, as the playlist says (rounded, so up to 1.49). */
        const val TARGET = 1
        private const val MIN_SECONDS = 0.95
        private const val ASK_SECONDS = 0.85
        private const val MAX_SECONDS = 1.45
        private const val LISTED = 4
        private const val KEPT = 12
        /** Ten seconds in, so sound that starts a little before the first picture isn't negative. */
        private const val OFFSET = 900_000L
    }
}

/** Serves [stream] to the TV over HTTP (to [allowed] alone, when given): `/flux.m3u8` and its segments. */
internal class HlsServer(private val stream: HlsStream, private val allowed: java.net.InetAddress? = null) : Closeable {
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    @Volatile private var open = true

    init {
        Thread({
            while (open) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                // The phone's screen is for the TV it's cast to, not anyone else on the network
                if (allowed != null && client.inetAddress != allowed) {
                    FluxLog.w(TAG, "turned away ${client.inetAddress}")
                    runCatching { client.close() }
                    continue
                }
                Thread({ serve(client) }, "HlsClient").apply { isDaemon = true; start() }
            }
        }, "HlsServer").apply { isDaemon = true; start() }
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 30_000
                socket.tcpNoDelay = true
                val input = BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream()
                while (open) {
                    val request = readHead(input) ?: break
                    val parts = request.first().split(' ')
                    val method = parts.getOrNull(0).orEmpty()
                    val path = parts.getOrNull(1).orEmpty().substringBefore('?')
                    val close = request.any { it.lowercase().startsWith("connection:") && it.lowercase().contains("close") }
                    val (type, body) = when {
                        path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl" to stream.playlist().toByteArray()
                        path.startsWith("/s") && path.endsWith(".ts") ->
                            "video/mp2t" to path.removePrefix("/s").removeSuffix(".ts").toIntOrNull()?.let(stream::segment)
                        else -> "text/plain" to null
                    }
                    val head = StringBuilder()
                    if (body == null) head.append("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n")
                    else head.append("HTTP/1.1 200 OK\r\nContent-Type: ").append(type)
                        .append("\r\nContent-Length: ").append(body.size)
                        .append("\r\nCache-Control: no-cache\r\n")
                    head.append(if (close) "Connection: close\r\n\r\n" else "Connection: keep-alive\r\n\r\n")
                    output.write(head.toString().toByteArray())
                    if (body != null && method != "HEAD") output.write(body)
                    output.flush()
                    if (close) break
                }
            }.onFailure { FluxLog.w(TAG, "client gone: $it") }
        }
    }

    /** A request's lines up to the blank one; null when the TV hung up. */
    private fun readHead(input: BufferedInputStream): List<String>? {
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) {
                if (line.isEmpty()) { if (lines.isNotEmpty()) return lines } else lines += line.toString()
                line.clear()
            } else if (b != '\r'.code) {
                line.append(b.toChar())
                if (line.length > 8192) return null
            }
        }
    }

    override fun close() {
        open = false
        runCatching { server.close() }
    }

    private companion object {
        const val TAG = "AirPlayHls"
    }
}
