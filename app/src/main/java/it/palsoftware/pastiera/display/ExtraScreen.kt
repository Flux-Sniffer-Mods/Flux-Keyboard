package it.palsoftware.pastiera.display

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.gaming.GameInputServer
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.Writer
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A screen of its own beside the phone's: made by the shell ([ExtraScreenServer]), apps opened
 * or moved onto it, its picture arriving as H.264 for whoever wants it (a Moonlight stream, a
 * preview on the phone), and keys and touch aimed at it. One at a time. Streaming a game uses it
 * as Separate screen; a Linux desktop can use it the same way.
 */
object ExtraScreen {
    private const val TAG = "FluxExtraScreen"
    internal const val TYPE_DISPLAY = 0
    const val TYPE_CONFIG = 1
    const val TYPE_FRAME = 2
    const val TYPE_KEYFRAME = 3
    /** A device tied to the screen by the helper (to give back to the phone if it can't). */
    internal const val TYPE_DEVICE = 4
    private const val KEY_DESKTOP_RESTORE = "extra_screen_desktop_restore"

    class Screen(val id: Int, val width: Int, val height: Int, val desktop: Boolean)

    /** The screen's picture as it's encoded: [TYPE_CONFIG] (SPS and PPS), then frames. */
    fun interface Frames { fun frame(type: Int, data: ByteArray, ptsUs: Long) }

    @Volatile var current: Screen? = null
        private set

    /** The phone gives its keys to the screen (the Games app's page for it is in front). */
    @Volatile var focused = false

    private var process: Process? = null
    private var commands: Writer? = null
    private var socket: Socket? = null
    @Volatile private var config: ByteArray? = null
    private val frames = CopyOnWriteArrayList<Frames>()
    private val watchers = CopyOnWriteArrayList<() -> Unit>()
    private var input: Writer? = null
    private var inputProcess: Process? = null
    private var appContext: Context? = null
    /** Devices the helper tied to the screen. */
    private val tied = java.util.concurrent.CopyOnWriteArraySet<String>()
    private var deviceListener: android.hardware.input.InputManager.InputDeviceListener? = null

    /** Told when a screen opens or closes. */
    fun watch(watcher: () -> Unit) { watchers += watcher }
    fun unwatch(watcher: () -> Unit) { watchers -= watcher }

    /** A density that suits a screen [height] rows high seen from a sofa: a TV's. */
    internal fun dpiFor(height: Int): Int = (height * 213 / 720).coerceIn(120, 640)

    /**
     * Opens a [width] x [height] screen, encoded at [fps] and [kbps]; with [desktop], Android's
     * desktop (where the phone has it: Android 16's desktop windowing, from Developer options)
     * instead of one app filling it. With [passthrough], plugged-in and Bluetooth keyboards, mice
     * and controllers go to the screen rather than the phone. Blocking (the shell starts it); null
     * when it couldn't open.
     */
    @Synchronized
    fun open(context: Context, width: Int, height: Int, fps: Int, kbps: Int, desktop: Boolean, passthrough: Boolean = false): Screen? {
        close()
        val app = context.applicationContext
        appContext = app
        if (!AdbShell.available()) return null
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = 10_000 }
        if (desktop) desktopMode(app, true)
        val apk = app.applicationInfo.sourceDir
        val w = width and 1.inv(); val h = height and 1.inv()
        var started: Process? = null
        var connection: Socket? = null
        try {
            started = AdbShell.newProcess(arrayOf("sh", "-c",
                "CLASSPATH='$apk' exec app_process /system/bin ${ExtraScreenServer::class.java.name} " +
                    "${server.localPort} $token $w $h ${dpiFor(h)} $fps $kbps ${if (desktop) 1 else 0} ${if (passthrough) 1 else 0}"))
            Thread { runCatching { started.inputStream.readBytes() } }.apply { isDaemon = true; start() }
            Thread {
                runCatching { started.errorStream.bufferedReader().forEachLine { Log.w(TAG, it) } }
            }.apply { isDaemon = true; start() }
            val accepted = server.accept()
            connection = accepted
            accepted.soTimeout = 10_000
            val stream = DataInputStream(BufferedInputStream(accepted.getInputStream(), 1 shl 16))
            val said = StringBuilder()
            while (true) {
                val b = stream.read()
                if (b < 0 || b == '\n'.code || said.length > 64) break
                said.append(b.toChar())
            }
            if (said.toString() != token) throw IllegalStateException("not the screen helper")
            if (stream.readByte().toInt() != TYPE_DISPLAY) throw IllegalStateException("no display")
            val id = stream.readLong().toInt()
            stream.skipBytes(stream.readInt())
            accepted.soTimeout = 0
            val screen = Screen(id, w, h, desktop)
            process = started
            commands = started.outputStream.bufferedWriter()
            socket = accepted
            current = screen
            Thread({ read(screen, stream) }, "ExtraScreen").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
            startInput(app, screen)
            if (passthrough) watchDevices(app)
            watchers.forEach { runCatching { it() } }
            return screen
        } catch (e: Exception) {
            Log.w(TAG, "screen couldn't open: $e")
            runCatching { connection?.close() }
            started?.destroy()
            if (desktop) desktopMode(app, false)
            return null
        } finally {
            runCatching { server.close() }
        }
    }

    private fun read(screen: Screen, stream: DataInputStream) {
        try {
            while (current === screen) {
                val type = stream.readByte().toInt()
                val pts = stream.readLong()
                val data = ByteArray(stream.readInt())
                stream.readFully(data)
                if (type == TYPE_DEVICE) { tied += String(data); continue }
                if (type == TYPE_CONFIG) config = data
                frames.forEach { it.frame(type, data, pts) }
            }
        } catch (_: Exception) {
        }
        // The helper ended by itself (the shell stopped): the screen's gone
        if (current === screen) close()
    }

    /** Closes the screen: what was on it moves to the phone's. */
    @Synchronized
    fun close() {
        val screen = current ?: return
        current = null
        focused = false
        unwatchDevices()
        runCatching { commands?.apply { write("Q\n"); flush() } }
        runCatching { commands?.close() }
        commands = null
        val ending = process
        process = null
        val devices = tied.toList()
        tied.clear()
        val app = appContext
        // The helper's given a moment to give devices back and close cleanly; anything it tied is
        // given back again after, in case it couldn't
        Thread {
            runCatching { ending?.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
            ending?.destroy()
            if (devices.isNotEmpty() && app != null) runCatching {
                val apk = app.applicationInfo.sourceDir
                AdbShell.run("CLASSPATH='$apk' app_process /system/bin ${ExtraScreenServer::class.java.name} clear " +
                    devices.joinToString(" ") { "'" + it.replace("'", "") + "'" }, 6_000)
            }
        }.apply { isDaemon = true; start() }
        runCatching { socket?.close() }
        socket = null
        config = null
        stopInput()
        appContext?.let { if (screen.desktop) desktopMode(it, false) }
        watchers.forEach { runCatching { it() } }
    }

    /** A keyframe next (a new viewer, or one catching up). */
    fun keyframe() = command("I")

    /** The screen given focus again (the phone took it): controllers' sticks go to the focused screen. */
    fun refocus() = command("F")

    private fun command(line: String) {
        val writer = commands ?: return
        synchronized(writer) { runCatching { writer.write(line + "\n"); writer.flush() } }
    }

    /** A keyboard, mouse or controller plugged in while the screen's open: tied to it too. */
    private fun watchDevices(context: Context) {
        val manager = context.getSystemService(android.hardware.input.InputManager::class.java) ?: return
        val listener = object : android.hardware.input.InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = command("P")
            override fun onInputDeviceChanged(deviceId: Int) = Unit
            override fun onInputDeviceRemoved(deviceId: Int) = Unit
        }
        deviceListener = listener
        manager.registerInputDeviceListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private fun unwatchDevices() {
        val listener = deviceListener ?: return
        deviceListener = null
        appContext?.getSystemService(android.hardware.input.InputManager::class.java)?.unregisterInputDeviceListener(listener)
    }

    /** [listener] gets the screen's picture from the next keyframe on. */
    fun addFrames(listener: Frames) {
        frames += listener
        config?.let { listener.frame(TYPE_CONFIG, it, 0) }
        keyframe()
    }

    fun removeFrames(listener: Frames) { frames -= listener }

    // ---- Android's desktop on the screen ----

    /** Android's desktop on screens of their own (Android 16, Developer options), put back after. */
    private fun desktopMode(context: Context, on: Boolean) {
        val prefs = SettingsManager.getPreferences(context)
        if (on) {
            val before = AdbShell.run("settings get global force_desktop_mode_on_external_displays")?.trim().orEmpty()
            if (!prefs.contains(KEY_DESKTOP_RESTORE)) prefs.edit().putString(KEY_DESKTOP_RESTORE, before).apply()
            AdbShell.run("settings put global force_desktop_mode_on_external_displays 1")
        } else {
            val before = prefs.getString(KEY_DESKTOP_RESTORE, null) ?: return
            when (before) {
                "1" -> Unit
                "0" -> AdbShell.run("settings put global force_desktop_mode_on_external_displays 0")
                else -> AdbShell.run("settings delete global force_desktop_mode_on_external_displays")
            }
            prefs.edit().remove(KEY_DESKTOP_RESTORE).apply()
        }
    }

    // ---- Apps on the screen ----

    /**
     * [intent]'s app on display [displayId] (0: the phone's): moved there with its task if it's
     * open (nothing lost), else opened there. Blocking (the shell).
     */
    fun bring(context: Context, intent: Intent, displayId: Int) {
        val component = intent.component ?: context.packageManager.resolveActivity(intent, 0)?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) } ?: return
        val task = rootTask(context, component)
        if (task != null) {
            AdbShell.run("am display move-stack $task $displayId")
            return
        }
        if (displayId == 0) return
        val launch = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(launch, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
        }.onFailure {
            AdbShell.run("am start --display $displayId -n ${component.flattenToShortString()}")
        }
    }

    /**
     * The root task [component]'s app is open in, from `am stack list`: its app's, or for Flux
     * Keyboard's own (the Xbox player), the one with that activity.
     */
    private fun rootTask(context: Context, component: ComponentName): Int? {
        val list = AdbShell.run("am stack list") ?: return null
        val own = component.packageName == context.packageName
        return rootTaskIn(list, component.packageName, if (own) component.className.substringAfterLast('.') else null)
    }

    internal fun rootTaskIn(list: String, packageName: String, activity: String?): Int? {
        var root: Int? = null
        for (line in list.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("RootTask") || trimmed.startsWith("Stack")) {
                root = Regex("\\bid=(\\d+)").find(trimmed)?.groupValues?.get(1)?.toIntOrNull()
                continue
            }
            if (!trimmed.startsWith("taskId=")) continue
            val app = trimmed.substringAfter(": ", "").substringBefore(' ')
            if (app.substringBefore('/') != packageName) continue
            if (activity != null && !app.substringAfter('/').endsWith(activity)) continue
            return root
        }
        return null
    }

    // ---- Keys and touch, aimed at the screen ----

    private fun startInput(context: Context, screen: Screen) {
        val apk = context.applicationInfo.sourceDir
        runCatching {
            val started = AdbShell.newProcess(arrayOf("sh", "-c", "CLASSPATH='$apk' exec app_process /system/bin ${GameInputServer::class.java.name}"))
            listOf(started.inputStream, started.errorStream).forEach { stream ->
                Thread { runCatching { stream.readBytes() } }.apply { isDaemon = true; start() }
            }
            inputProcess = started
            input = started.outputStream.bufferedWriter()
            send("Z ${screen.id}")
            send("S ${screen.width} ${screen.height}")
            keyboardDevice()?.let { send("D $it") }
        }.onFailure { Log.w(TAG, "no input for the screen: $it") }
    }

    private fun stopInput() {
        val writer = input
        val started = inputProcess
        input = null
        inputProcess = null
        worker.execute { runCatching { writer?.close() }; started?.destroy() }
    }

    /** Input in order, off the caller's thread. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun send(line: String) = worker.execute {
        val writer = input ?: return@execute
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }
    }

    private fun keyboardDevice(): Int? = InputDevice.getDeviceIds().firstOrNull { id ->
        val device = InputDevice.getDevice(id) ?: return@firstOrNull false
        !device.isVirtual && device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    /** Keys the phone keeps while its keys go to the screen. */
    private val PHONE_KEYS = setOf(
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH
    )

    /** A key the phone got, sent to the screen instead when it has the phone's keys. True when it took it. */
    fun key(event: KeyEvent): Boolean {
        if (!focused || current == null || event.keyCode in PHONE_KEYS) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        if (event.repeatCount > 0) return true
        val pad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || KeyEvent.isGamepadButton(event.keyCode)
        send("K ${if (event.action == KeyEvent.ACTION_DOWN) 1 else 0} ${event.keyCode} ${if (pad) "g" else "k"}")
        return true
    }

    /** A finger down (d), moved (m) or up (u) at a point on the screen. */
    fun touch(kind: Char, finger: Int, x: Float, y: Float) = send("T $kind $finger $x $y")
}
