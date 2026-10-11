package it.palsoftware.pastiera.streaming

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * One Moonlight session: from its launch (the client's key and screen size) through the RTSP
 * handshake (what it wants: bitrate, packet size, sound), then the stream itself once the
 * phone's screen can be captured and Moonlight says play. Video and sound go out by UDP, the
 * control stream brings input and keyframe requests back.
 */
internal class StreamSession(
    private val context: Context,
    val client: InetAddress,
    val appId: Int,
    private val width: Int,
    private val height: Int,
    private var fps: Int,
    private val key: ByteArray,
    private val keyId: Int,
    private val onEnded: (StreamSession) -> Unit
) {
    private var rtsp: ServerSocket? = null
    private var control: StreamControl? = null
    private var videoPort: StreamPort? = null
    private var audioPort: StreamPort? = null
    private var video: StreamVideo? = null
    private var audio: StreamAudio? = null
    private var input: StreamInput? = null
    @Volatile private var projection: MediaProjection? = null
    @Volatile private var playing = false
    @Volatile private var ended = false

    // What Moonlight asked for in its announcement
    private var bitrateKbps = 20_000
    private var packetSize = 1392
    private var channels = 2
    private var packetMs = 5
    private var audioEncrypted = false

    fun start() {
        videoPort = StreamPort(VIDEO_PORT, client)
        audioPort = StreamPort(AUDIO_PORT, client)
        control = StreamControl(StreamProtocol.ControlCipher(key), ::message) { end(notify = false) }.also { it.start() }
        // Bound again straight after the last session: its closed connections may linger
        val server = ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(RTSP_PORT)) }
        rtsp = server
        Thread({
            while (!ended && !server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching { rtsp(socket) }.onFailure { Log.w(TAG, "rtsp: $it") }
                runCatching { socket.close() }
            }
        }, "StreamRtsp").apply { isDaemon = true; start() }
    }

    /** The phone's screen, captured for this session (asked for when it was launched). */
    fun onProjection(capture: MediaProjection) {
        projection = capture
        media()
    }

    /** Ends the session: Moonlight is told when [notify] (it didn't leave by itself). */
    fun end(notify: Boolean = true) {
        if (ended) return
        ended = true
        if (notify) runCatching { control?.end() }
        runCatching { video?.stop() }; video = null
        runCatching { audio?.stop() }; audio = null
        runCatching { input?.stop() }; input = null
        runCatching { projection?.stop() }; projection = null
        runCatching { control?.stop() }; control = null
        runCatching { rtsp?.close() }; rtsp = null
        videoPort?.close(); audioPort?.close()
        onEnded(this)
    }

    // ---- RTSP: one request per connection, answered, then closed ----

    private fun rtsp(socket: Socket) {
        socket.soTimeout = 10_000
        val stream = socket.getInputStream()
        val head = readHead(stream) ?: return
        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val body = if (length > 0) String(readExactly(stream, length)) else ""
        val request = StreamProtocol.parseRtspHead(head, body) ?: return
        val out = socket.getOutputStream()
        val response = when (request.method) {
            "OPTIONS" -> StreamProtocol.rtspResponse(request.cseq)
            "DESCRIBE" -> StreamProtocol.rtspResponse(request.cseq, body = StreamProtocol.describe())
            "SETUP" -> {
                val type = request.target.substringAfter("streamid=").substringBefore('/')
                val port = when (type) { "audio" -> AUDIO_PORT; "video" -> VIDEO_PORT; "control" -> StreamControl.PORT; else -> 0 }
                if (port == 0) StreamProtocol.rtspResponse(request.cseq, status = "404 NOT FOUND")
                else StreamProtocol.rtspResponse(request.cseq, listOf("Session" to "DEADBEEFCAFE;timeout = 90", "Transport" to "server_port=$port"))
            }
            "ANNOUNCE" -> { announce(request.body); StreamProtocol.rtspResponse(request.cseq) }
            "PLAY" -> { playing = true; media(); StreamProtocol.rtspResponse(request.cseq) }
            else -> StreamProtocol.rtspResponse(request.cseq, status = "404 NOT FOUND")
        }
        out.write(response)
        out.flush()
    }

    private fun announce(body: String) {
        fun int(key: String) = StreamProtocol.sdp(body, key)?.toIntOrNull()
        (int("x-ml-video.configuredBitrateKbps") ?: int("x-nv-vqos[0].bw.maximumBitrateKbps"))?.let { bitrateKbps = it.coerceIn(1_000, 100_000) }
        int("x-nv-video[0].packetSize")?.let { packetSize = it.coerceIn(256, 1500) }
        int("x-nv-video[0].maxFPS")?.let { fps = it.coerceIn(10, 120) }
        int("x-nv-audio.surround.numChannels")?.let { channels = if (it == 6 || it == 8) it else 2 }
        int("x-nv-aqos.packetDuration")?.let { packetMs = if (it == 10) 10 else 5 }
        val features = int("x-nv-general.featureFlags") ?: 0
        val encryption = int("x-ss-general.encryptionEnabled") ?: 0
        audioEncrypted = features and StreamProtocol.FF_AUDIO_ENCRYPTION != 0 || encryption and StreamProtocol.ENC_AUDIO != 0
    }

    private fun readHead(stream: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        val end = "\r\n\r\n"
        while (matched < 4) {
            val b = stream.read()
            if (b < 0) return null
            buffer.write(b)
            matched = if (b.toChar() == end[matched]) matched + 1 else if (b.toChar() == '\r') 1 else 0
            if (buffer.size() > 64 * 1024) return null
        }
        return buffer.toString()
    }

    private fun readExactly(stream: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = stream.read(data, read, length - read)
            if (n < 0) break
            read += n
        }
        return data.copyOf(read)
    }

    // ---- The stream ----

    /** Starts video, sound and input once Moonlight has said play and the screen can be captured. */
    @Synchronized
    private fun media() {
        val capture = projection ?: return
        if (!playing || video != null || ended) return
        runCatching {
            video = StreamVideo(capture, width, height, fps, bitrateKbps, packetSize, videoPort!!).also {
                it.start(context.resources.displayMetrics.densityDpi)
                it.keyframe()
            }
            audio = StreamAudio(capture, channels, packetMs, if (audioEncrypted) key else null, keyId, audioPort!!).also { it.start() }
            input = StreamInput(context, width, height).also { it.start() }
        }.onFailure {
            Log.w(TAG, "stream couldn't start: $it")
            end()
        }
    }

    private fun message(type: Int, payload: ByteArray) {
        when (type) {
            StreamProtocol.CONTROL_IDR, StreamProtocol.CONTROL_INVALIDATE -> video?.keyframe()
            StreamProtocol.CONTROL_INPUT -> StreamProtocol.parseInput(payload)?.let { event -> input?.handle(event) }
        }
    }

    companion object {
        private const val TAG = "StreamSession"
        const val RTSP_PORT = 48010
        const val VIDEO_PORT = 47998
        const val AUDIO_PORT = 48000
    }
}
