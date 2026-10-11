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
 * `ExtraScreenServer port token width height dpi fps kbps desktop passthrough`: it connects back
 * to 127.0.0.1:port, says the token, then sends frames ([ExtraScreen] reads them). Its input takes
 * `I` (a keyframe next), `P` (plugged-in keyboards, mice and controllers to the screen again, and
 * the screen given focus) and `Q` (close). Closing either ends it: the display goes away, what was
 * on it moves to the phone's screen, and the devices go back to the phone.
 *
 * `ExtraScreenServer clear p:port|d:descriptor...` only gives those devices back to the phone
 * (after a helper that couldn't).
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
        if (args.firstOrNull() == "clear") {
            args.drop(1).forEach { DevicePassThrough.release(it) }
            exitProcess(0)
        }
        val port = args[0].toInt()
        val token = args[1]
        val width = args[2].toInt()
        val height = args[3].toInt()
        val dpi = args[4].toInt()
        val fps = args[5].toInt()
        val kbps = args[6].toInt()
        val desktop = args.getOrNull(7) == "1"
        val passthrough = args.getOrNull(8) == "1"

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
        val displayId = display.display.displayId
        send(out, ExtraScreen.TYPE_DISPLAY, displayId.toLong(), ByteArray(0))
        val devices = if (passthrough) DevicePassThrough(display.display, displayId) { key ->
            runCatching { send(out, ExtraScreen.TYPE_DEVICE, 0, key.toByteArray()) }
        } else null
        devices?.update()

        Thread {
            runCatching {
                System.`in`.bufferedReader().forEachLine { line ->
                    when (line.trim()) {
                        "I" -> runCatching { codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
                        "P" -> devices?.update()
                        "F" -> devices?.focus()
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
        devices?.releaseAll()
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

/**
 * Plugged-in and Bluetooth keyboards, mice and controllers tied to the screen (Android's own
 * input-to-display association): their keys and pointer go there, not to the phone. The screen
 * also takes focus, for controllers' sticks (which go to the focused screen). Each device tied is
 * reported ([onTied]) so Flux Keyboard can give it back should this helper die first: a device
 * left tied to a screen that's gone stops working until the phone restarts.
 */
private class DevicePassThrough(private val display: android.view.Display, private val displayId: Int, private val onTied: (String) -> Unit) {
    private val tied = linkedSetOf<String>()
    private val uniqueId: String? = runCatching {
        android.view.Display::class.java.getMethod("getUniqueId").invoke(display) as String?
    }.getOrNull()

    @Synchronized
    fun update() {
        val unique = uniqueId ?: return
        val locations by lazy { locations() }
        for (id in android.view.InputDevice.getDeviceIds()) {
            val device = android.view.InputDevice.getDevice(id) ?: continue
            if (!device.isExternal || device.isVirtual || !wanted(device)) continue
            val key = if (Build.VERSION.SDK_INT >= 35) "d:${device.descriptor}"
                else locations[device.descriptor]?.let { "p:$it" } ?: continue
            if (key in tied) continue
            if (tie(key, unique)) { tied += key; onTied(key) }
        }
        focus()
    }

    private fun wanted(device: android.view.InputDevice): Boolean {
        val sources = device.sources
        fun has(source: Int) = sources and source == source
        return device.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC ||
            has(android.view.InputDevice.SOURCE_MOUSE) || has(android.view.InputDevice.SOURCE_TOUCHPAD) ||
            has(android.view.InputDevice.SOURCE_GAMEPAD) || has(android.view.InputDevice.SOURCE_JOYSTICK)
    }

    /** The screen in focus: keys and sticks without a screen of their own go to it. */
    fun focus() {
        runCatching {
            val service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
            service.javaClass.getMethod("focusTopTask", Int::class.javaPrimitiveType).invoke(service, displayId)
        }
    }

    @Synchronized
    fun releaseAll() {
        tied.forEach { release(it) }
        tied.clear()
    }

    private fun tie(key: String, unique: String): Boolean = runCatching {
        val manager = inputManager()
        val value = key.substring(2)
        if (key.startsWith("d:")) call(manager, "addUniqueIdAssociationByDescriptor", value, unique)
        else call(manager, "addUniqueIdAssociationByPort", value, unique) || call(manager, "addUniqueIdAssociation", value, unique)
    }.getOrDefault(false)

    /** Each device's location (its port) by its descriptor, from the input service's own dump. */
    private fun locations(): Map<String, String> = runCatching {
        val dump = ProcessBuilder("dumpsys", "input").redirectErrorStream(true).start().inputStream.bufferedReader().readText()
        val found = HashMap<String, String>()
        var descriptor: String? = null
        for (line in dump.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Descriptor:")) descriptor = trimmed.substringAfter(':').trim()
            else if (trimmed.startsWith("Location:")) {
                val location = trimmed.substringAfter(':').trim()
                descriptor?.let { if (location.isNotEmpty()) found.putIfAbsent(it, location) }
                descriptor = null
            }
        }
        found
    }.getOrDefault(emptyMap())

    companion object {
        fun inputManager(): Any {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "input")
            return Class.forName("android.hardware.input.IInputManager\$Stub")
                .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)!!
        }

        /** [name](args) on the input service if it has it; true when it ran. */
        fun call(manager: Any, name: String, vararg args: String): Boolean {
            val method = manager.javaClass.methods.firstOrNull { m ->
                m.name == name && m.parameterTypes.size == args.size && m.parameterTypes.all { it == String::class.java }
            } ?: return false
            method.invoke(manager, *args)
            return true
        }

        /** A tied device back to the phone. */
        fun release(key: String) {
            runCatching {
                val manager = inputManager()
                val value = key.substring(2)
                if (key.startsWith("d:")) call(manager, "removeUniqueIdAssociationByDescriptor", value)
                else call(manager, "removeUniqueIdAssociationByPort", value) || call(manager, "removeUniqueIdAssociation", value)
            }
        }
    }
}
