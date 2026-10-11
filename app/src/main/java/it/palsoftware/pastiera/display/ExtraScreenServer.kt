package it.palsoftware.pastiera.display

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.view.Surface
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import kotlin.system.exitProcess

/**
 * A screen of its own, made by the ADB shell (`app_process`): a display Android treats like a
 * plugged-in one (apps open on it, touch and keys reach them, with focus of its own), drawn into
 * an H.264 encoder and sent to Flux Keyboard as it changes. Started as
 * `ExtraScreenServer port token width height dpi fps kbps desktop`: it connects back to
 * 127.0.0.1:port, says the token, then sends frames ([ExtraScreen] reads them). Its input takes
 * `I` (a keyframe next) and `Q` (close). Closing either ends it: the display goes away, and what
 * was on it moves to the phone's screen.
 */
object ExtraScreenServer {
    // DisplayManager's flags (some hidden)
    private const val PUBLIC = 1
    private const val PRESENTATION = 1 shl 1
    private const val OWN_CONTENT_ONLY = 1 shl 3
    private const val SUPPORTS_TOUCH = 1 shl 6
    private const val SHOW_SYSTEM_DECORATIONS = 1 shl 9
    private const val TRUSTED = 1 shl 10
    private const val OWN_DISPLAY_GROUP = 1 shl 11
    private const val ALWAYS_UNLOCKED = 1 shl 12
    private const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
    private const val OWN_FOCUS = 1 shl 14
    private const val DEVICE_DISPLAY_GROUP = 1 shl 15

    @Volatile private var running = true

    @JvmStatic
    fun main(args: Array<String>) {
        val port = args[0].toInt()
        val token = args[1]
        val width = args[2].toInt()
        val height = args[3].toInt()
        val dpi = args[4].toInt()
        val fps = args[5].toInt()
        val kbps = args[6].toInt()
        val desktop = args.getOrNull(7) == "1"

        Looper.prepareMainLooper()
        val socket = Socket(InetAddress.getLoopbackAddress(), port).apply { tcpNoDelay = true }
        val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 1 shl 16))
        out.write("$token\n".toByteArray())
        out.flush()

        val codec = encoder(width, height, fps, kbps)
        val surface = codec.createInputSurface()
        codec.start()
        val display = try {
            createDisplay(width, height, dpi, surface, desktop)
        } catch (e: Throwable) {
            System.err.println("no display: $e")
            exitProcess(1)
        }
        send(out, ExtraScreen.TYPE_DISPLAY, display.display.displayId.toLong(), ByteArray(0))

        Thread {
            runCatching {
                System.`in`.bufferedReader().forEachLine { line ->
                    when (line.trim()) {
                        "I" -> runCatching { codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
                        "Q" -> running = false
                    }
                }
            }
            // Flux Keyboard gone: the screen with it
            running = false
        }.apply { isDaemon = true; start() }

        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = codec.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    buffer.position(info.offset); buffer.get(data)
                    val type = when {
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 -> ExtraScreen.TYPE_CONFIG
                        info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 -> ExtraScreen.TYPE_KEYFRAME
                        else -> ExtraScreen.TYPE_FRAME
                    }
                    send(out, type, info.presentationTimeUs, data)
                }
                codec.releaseOutputBuffer(index, false)
            }
        } catch (_: IOException) {
        } catch (_: IllegalStateException) {
        }
        runCatching { display.release() }
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { socket.close() }
        exitProcess(0)
    }

    private fun send(out: DataOutputStream, type: Int, pts: Long, data: ByteArray) = synchronized(out) {
        out.writeByte(type)
        out.writeLong(pts)
        out.writeInt(data.size)
        out.write(data)
        out.flush()
    }

    private fun encoder(width: Int, height: Int, fps: Int, kbps: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, kbps * 1000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3600)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / fps)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            if (Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    /**
     * The display, with as much of its own as Android allows the shell: trusted (apps' windows
     * and focus there), unlocked, touchable. Older Android takes fewer of the flags. Not
     * destroying its content on removal: what's open on it moves to the phone's screen instead.
     */
    private fun createDisplay(width: Int, height: Int, dpi: Int, surface: Surface, desktop: Boolean): VirtualDisplay {
        val manager = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }.newInstance(ShellContext.get())
        val base = PUBLIC or PRESENTATION or OWN_CONTENT_ONLY or SUPPORTS_TOUCH or
            (if (desktop) SHOW_SYSTEM_DECORATIONS else 0)
        val own = TRUSTED or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED or TOUCH_FEEDBACK_DISABLED
        val attempts = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 34) base or own or OWN_FOCUS or DEVICE_DISPLAY_GROUP else null,
            if (Build.VERSION.SDK_INT >= 33) base or own else null,
            base
        )
        var failure: Throwable? = null
        for (flags in attempts) {
            try {
                return manager.createVirtualDisplay("Flux screen", width, height, dpi, surface, flags)
                    ?: throw IllegalStateException("no display")
            } catch (e: Throwable) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("no display")
    }
}

/** The shell's own context, as Android expects of a caller with the shell's user. */
private class ShellContext(base: Context) : ContextWrapper(base) {
    override fun getPackageName(): String = PACKAGE
    override fun getOpPackageName(): String = PACKAGE
    override fun getApplicationContext(): Context = this

    @android.annotation.SuppressLint("NewApi")
    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(Process.myUid()).setPackageName(PACKAGE).build()

    companion object {
        private const val PACKAGE = "com.android.shell"

        fun get(): Context {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            threadClass.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
            threadClass.getDeclaredField("mSystemThread").apply { isAccessible = true }.setBoolean(thread, true)
            val system = threadClass.getDeclaredMethod("getSystemContext").invoke(thread) as Context
            return ShellContext(system)
        }
    }
}
