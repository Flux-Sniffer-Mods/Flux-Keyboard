package it.palsoftware.pastiera.airplay

import android.annotation.SuppressLint
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
import it.palsoftware.pastiera.FluxLog

/**
 * The phone's screen and sound for AirPlay: the screen mirrored into a TV-shaped picture and
 * encoded as H.264, what plays captured and encoded as AAC, both cut into [stream]'s segments.
 * Sound needs the microphone permission (Android's rule for capturing what plays); without it
 * the picture goes alone.
 */
internal class AirPlayMedia(private val projection: MediaProjection, private val dpi: Int) {
    @Volatile private var running = false
    private var video: MediaCodec? = null
    private var display: VirtualDisplay? = null
    private var record: AudioRecord? = null
    private var audioCodec: MediaCodec? = null

    lateinit var stream: HlsStream
        private set

    fun start(): HlsStream {
        running = true
        val recorder = audioRecorder()
        stream = HlsStream(withAudio = recorder != null) { keyframe() }
        try {
            startVideo()
        } catch (e: Exception) {
            // No picture, no cast: the sound capture (and Android's microphone sign) ends with it
            runCatching { recorder?.release() }
            stop()
            throw e
        }
        if (recorder != null) startAudio(recorder)
        return stream
    }

    private fun startVideo() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            // A keyframe a second: each segment starts on one
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            // A still screen keeps making frames, so segments keep coming
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / FPS)
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
        }
        val encoder = runCatching { configured(format) }.getOrElse {
            // Some encoders take no profile asked for: their own then
            format.removeKey(MediaFormat.KEY_PROFILE)
            format.removeKey(MediaFormat.KEY_LEVEL)
            configured(format)
        }
        val surface = encoder.createInputSurface()
        encoder.start()
        video = encoder
        display = projection.createVirtualDisplay(
            "AirPlay", WIDTH, HEIGHT, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null
        )
        Thread({ drainVideo(encoder) }, "AirPlayVideo").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun configured(format: MediaFormat): MediaCodec {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            encoder.release()
            throw e
        }
        return encoder
    }

    private fun keyframe() {
        runCatching { video?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
    }

    // The codecs are let go by stop() while these loops may be mid-call: any call then ends them
    private fun drainVideo(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                val buffer = encoder.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    buffer.position(info.offset); buffer.get(data)
                    stream.video(data, info.presentationTimeUs,
                        key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0,
                        isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0)
                }
                encoder.releaseOutputBuffer(index, false)
            }
        } catch (_: IllegalStateException) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun audioRecorder(): AudioRecord? {
        val capture = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
        return runCatching {
            AudioRecord.Builder().setAudioFormat(format).setAudioPlaybackCaptureConfig(capture)
                .setBufferSizeInBytes(RATE * 4 / 5).build()
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.onFailure { FluxLog.w(TAG, "sound can't be captured: $it") }.getOrNull()
    }

    private fun startAudio(recorder: AudioRecord) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val encoder = runCatching {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
        }.onFailure { FluxLog.w(TAG, "no AAC encoder: $it") }.getOrNull() ?: run { recorder.release(); return }
        record = recorder
        audioCodec = encoder
        recorder.startRecording()
        Thread({ feedAudio(recorder, encoder) }, "AirPlayAudioIn").apply { priority = Thread.MAX_PRIORITY; start() }
        Thread({ drainAudio(encoder) }, "AirPlayAudio").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    private fun feedAudio(recorder: AudioRecord, encoder: MediaCodec) {
        val chunk = ByteArray(RATE / 50 * 4)
        var samples = 0L
        var startUs = -1L
        try {
            while (running) {
                val n = recorder.read(chunk, 0, chunk.size)
                if (n <= 0) { if (n < 0) break else continue }
                // Timed by the samples counted from the first, on the clock the pictures use
                if (startUs < 0) startUs = System.nanoTime() / 1000 - n / 4 * 1_000_000L / RATE
                val ptsUs = startUs + samples * 1_000_000L / RATE
                samples += n / 4
                val index = encoder.dequeueInputBuffer(20_000)
                if (index < 0) continue
                val buffer = encoder.getInputBuffer(index) ?: continue
                buffer.clear()
                val size = minOf(n, buffer.remaining())
                buffer.put(chunk, 0, size)
                encoder.queueInputBuffer(index, 0, size, ptsUs, 0)
            }
        } catch (_: IllegalStateException) {
        }
    }

    private fun drainAudio(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                val buffer = encoder.getOutputBuffer(index)
                if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    val frame = ByteArray(7 + info.size)
                    TsMuxer.adts(info.size, RATE, 2).copyInto(frame)
                    buffer.position(info.offset); buffer.get(frame, 7, info.size)
                    stream.audio(frame, info.presentationTimeUs)
                }
                encoder.releaseOutputBuffer(index, false)
            }
        } catch (_: IllegalStateException) {
        }
    }

    fun stop() {
        running = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { audioCodec?.stop() }
        runCatching { audioCodec?.release() }
        audioCodec = null
        runCatching { display?.release() }
        display = null
        runCatching { video?.stop() }
        runCatching { video?.release() }
        video = null
    }

    private companion object {
        const val TAG = "AirPlay"
        /** A TV's shape: the phone's screen fitted inside, a game held sideways filling it. */
        const val WIDTH = 1920
        const val HEIGHT = 1080
        const val FPS = 30
        const val BITRATE = 8_000_000
        const val RATE = 48_000
    }
}
