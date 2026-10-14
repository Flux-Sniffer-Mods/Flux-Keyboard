package it.palsoftware.pastiera.streaming

import android.annotation.SuppressLint
import it.palsoftware.pastiera.FluxLog
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Bundle
import android.util.Log
import it.palsoftware.pastiera.display.ExtraScreen
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusMSEncoder
import io.github.jaredmdobson.concentus.OpusMode
import io.github.jaredmdobson.concentus.OpusSignal
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * A UDP port Moonlight pings from the port it listens on: the first ping from the session's
 * client says where to send.
 */
internal class StreamPort(port: Int, private val client: InetAddress) {
    private val socket = DatagramSocket(null).apply {
        reuseAddress = true; bind(java.net.InetSocketAddress(port)); sendBufferSize = 1 shl 20
        // Marked as real-time (DSCP EF): routers that honour it send it ahead of other traffic
        runCatching { trafficClass = 0xB8 }
    }
    @Volatile var target: Pair<InetAddress, Int>? = null
        private set
    @Volatile private var open = true

    init {
        Thread({
            val buffer = ByteArray(256)
            while (open) {
                val packet = DatagramPacket(buffer, buffer.size)
                if (runCatching { socket.receive(packet) }.isFailure) break
                if (packet.address == client) target = packet.address to packet.port
            }
        }, "StreamPort$port").apply { isDaemon = true; start() }
    }

    fun send(data: ByteArray) {
        val (address, port) = target ?: return
        runCatching { socket.send(DatagramPacket(data, data.size, address, port)) }
    }

    fun close() {
        open = false
        runCatching { socket.close() }
    }
}

/**
 * The stream's picture as H.264 for Moonlight: the phone's screen mirrored into a display of the
 * size Moonlight asked for (the screen fitted inside it), or a screen of its own
 * ([ExtraScreen], Separate screen), switched either way mid-stream. Encoded as it changes, a
 * keyframe whenever Moonlight needs one.
 */
internal class StreamVideo(
    private val projection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrateKbps: Int,
    packetSize: Int,
    private val port: StreamPort
) {
    private val packetizer = StreamProtocol.VideoPacketizer(packetSize)
    private var codec: MediaCodec? = null
    /** The mirror: made once (Android allows one per capture), its encoder swapped as it's used. */
    private var display: VirtualDisplay? = null
    @Volatile private var running = false
    @Volatile private var separate = false
    private var config = ByteArray(0)
    private var frameIndex = 0

    private val screenFrames = ExtraScreen.Frames { type, data, pts ->
        if (separate) send(type == ExtraScreen.TYPE_CONFIG, type == ExtraScreen.TYPE_KEYFRAME, data, pts)
    }

    /** The phone's screen, mirrored. */
    @Synchronized
    fun start(dpi: Int) {
        separate = false
        ExtraScreen.removeFrames(screenFrames)
        if (codec != null) return
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            // Keyframes only when asked for: they're big, and Moonlight asks when it needs one
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3600)
            // A still screen keeps sending, so the Moonlight client doesn't think the stream stopped
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / fps)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (android.os.Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            // No frames that wait on later ones
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()
        codec = encoder
        running = true
        display?.setSurface(surface) ?: run {
            display = projection.createVirtualDisplay(
                "Moonlight", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null
            )
        }
        Thread({ drain(encoder) }, "StreamVideo").apply { priority = Thread.MAX_PRIORITY; start() }
        keyframe()
    }

    /** The extra screen's own picture instead of the mirror (it's open, at this stream's size). */
    @Synchronized
    fun useScreen() {
        stopMirror()
        separate = true
        ExtraScreen.addFrames(screenFrames)
    }

    /** A keyframe next, for a client starting or catching up after a loss. */
    fun keyframe() {
        if (separate) ExtraScreen.keyframe()
        else runCatching { codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
    }

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (running && codec === encoder) {
            val index = try { encoder.dequeueOutputBuffer(info, 100_000) } catch (e: IllegalStateException) { break }
            if (index < 0) continue
            val buffer = encoder.getOutputBuffer(index)
            if (buffer != null && info.size > 0) {
                val data = ByteArray(info.size)
                buffer.position(info.offset); buffer.get(data)
                send(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0,
                    info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0, data, info.presentationTimeUs)
            }
            runCatching { encoder.releaseOutputBuffer(index, false) }
        }
    }

    @Synchronized
    private fun send(isConfig: Boolean, key: Boolean, data: ByteArray, ptsUs: Long) {
        if (isConfig) {
            // SPS and PPS: sent again before every keyframe
            config = data
            return
        }
        val frame = if (key) config + data else data
        val timestamp = (ptsUs * 9 / 100).toInt()
        frameIndex++
        for (packet in packetizer.packetize(frame, frameIndex, key, timestamp)) port.send(packet)
    }

    private fun stopMirror() {
        val encoder = codec ?: return
        codec = null
        runCatching { display?.setSurface(null) }
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
    }

    @Synchronized
    fun stop() {
        running = false
        separate = false
        ExtraScreen.removeFrames(screenFrames)
        stopMirror()
        runCatching { display?.release() }
        display = null
    }
}

/**
 * What the phone plays, captured as it plays (games and media; apps can opt out) and sent as Opus
 * in Moonlight's short packets, for 2, 6 or 8 speakers (sound beyond the front two left silent).
 */
internal class StreamAudio(
    private val projection: MediaProjection,
    private val channels: Int,
    private val packetMs: Int,
    private val key: ByteArray?,
    private val keyId: Int,
    private val port: StreamPort
) {
    @Volatile private var running = false
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        val recorder = runCatching {
            AudioRecord.Builder().setAudioFormat(format).setAudioPlaybackCaptureConfig(capture)
                .setBufferSizeInBytes(RATE / 1000 * 4 * 100).build()
        }.onFailure { FluxLog.w(TAG, "sound can't be captured: $it") }.getOrNull() ?: return false
        record = recorder
        running = true
        recorder.startRecording()
        Thread({ run(recorder) }, "StreamAudio").apply { priority = Thread.MAX_PRIORITY; start() }
        return true
    }

    private fun encoder(): OpusMSEncoder {
        val (streams, coupled, bitrate) = when (channels) {
            6 -> Triple(4, 2, 256_000)
            8 -> Triple(5, 3, 450_000)
            else -> Triple(1, 1, 96_000)
        }
        return OpusMSEncoder.Create(RATE, channels, streams, coupled, ShortArray(channels) { it.toShort() }, OpusApplication.OPUS_APPLICATION_RESTRICTED_LOWDELAY).apply {
            setBitrate(bitrate)
            setComplexity(6)
            setForceMode(OpusMode.MODE_CELT_ONLY)
            setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC)
        }
    }

    private fun run(recorder: AudioRecord) {
        val encoder = runCatching { encoder() }.getOrElse { FluxLog.w(TAG, "no Opus encoder: $it"); return }
        val frame = RATE / 1000 * packetMs
        val stereo = ShortArray(frame * 2)
        val pcm = if (channels == 2) stereo else ShortArray(frame * channels)
        val out = ByteArray(1400)
        var sequence = 0
        var timestamp = 0
        while (running) {
            var read = 0
            while (read < stereo.size && running) {
                val n = recorder.read(stereo, read, stereo.size - read)
                if (n <= 0) break
                read += n
            }
            if (read < stereo.size) continue
            if (channels != 2) for (i in 0 until frame) {
                pcm[i * channels] = stereo[i * 2]; pcm[i * channels + 1] = stereo[i * 2 + 1]
            }
            val size = runCatching { encoder.encodeMultistream(pcm, 0, frame, out, 0, out.size) }.getOrDefault(-1)
            if (size <= 0) continue
            port.send(StreamProtocol.audioPacket(out.copyOf(size), sequence, timestamp, key, keyId))
            sequence = (sequence + 1) and 0xFFFF
            timestamp += packetMs
        }
    }

    fun stop() {
        running = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
    }

    companion object {
        private const val TAG = "StreamAudio"
        private const val RATE = 48_000
    }
}
