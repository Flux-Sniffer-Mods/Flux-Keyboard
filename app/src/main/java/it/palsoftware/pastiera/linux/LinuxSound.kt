package it.palsoftware.pastiera.linux

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The desktop's sound: PulseAudio in Linux sends what plays as raw 48 kHz stereo (16-bit) to
 * 127.0.0.1:5911, played here as the phone's media (so it's in a Moonlight stream or on AirPlay
 * too). Reconnects while the desktop runs; [stop] ends it.
 */
internal class LinuxSound {
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "LinuxSound").apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }
    }

    private fun loop() {
        while (running) {
            val connected = runCatching {
                Socket().also { it.connect(InetSocketAddress("127.0.0.1", PORT), 1_000); it.tcpNoDelay = true }
            }.getOrNull()
            if (connected == null) { runCatching { Thread.sleep(1_500) }; continue }
            socket = connected
            runCatching { play(connected.getInputStream()) }
            runCatching { connected.close() }
            socket = null
        }
    }

    private fun play(input: InputStream) {
        val minimum = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, RATE * 4 / 10))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            val buffer = ByteArray(RATE * 4 / 50)
            // Whole frames only: a frame split between reads would swap the channels
            var pending = 0
            while (running) {
                val n = input.read(buffer, pending, buffer.size - pending)
                if (n < 0) break
                pending += n
                val whole = pending - pending % 4
                if (whole > 0) {
                    track.write(buffer, 0, whole)
                    System.arraycopy(buffer, whole, buffer, 0, pending - whole)
                    pending -= whole
                }
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        thread = null
    }

    private companion object {
        const val PORT = 5911
        const val RATE = 48_000
    }
}
