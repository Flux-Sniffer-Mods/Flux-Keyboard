package it.palsoftware.pastiera.display

import android.app.ActivityOptions
import it.palsoftware.pastiera.FluxLog
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
    /** The phone's keys go to the screen while [focused] (else the phone keeps them). */
    @Volatile var keysToScreen = true

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
    private var restoreFreeform = true

    @Synchronized
    fun open(context: Context, width: Int, height: Int, fps: Int, kbps: Int, desktop: Boolean, passthrough: Boolean = false): Screen? {
        close()
        val app = context.applicationContext
        appContext = app
        if (!AdbShell.available()) return null
        val token = java.util.UUID.randomUUID().toString().replace("-", "")
        restoreFreeform = true
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = 10_000 }
        if (desktop) desktopMode(app, true)
        // A game's screen is fullscreen even where Developer options make other screens desktops
        // (Enable freeform windows on secondary display): off while it's made, then put back
        val freeformScreens = if (desktop) null
            else AdbShell.run("settings get global force_desktop_mode_on_external_displays")?.trim()?.takeIf { it == "1" }
        if (freeformScreens != null) AdbShell.run("settings put global force_desktop_mode_on_external_displays 0")
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
                runCatching { started.errorStream.bufferedReader().forEachLine { FluxLog.w(TAG, it) } }
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
            if (freeformScreens != null) { AdbShell.run("settings put global force_desktop_mode_on_external_displays 1"); restoreFreeform = false }
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
            FluxLog.w(TAG, "screen couldn't open: $e")
            if (freeformScreens != null && restoreFreeform) AdbShell.run("settings put global force_desktop_mode_on_external_displays 1")
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
        hidePointer()
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

    /**
     * Developer options, Window management: Force activities to be resizeable, Enable
     * non-resizable in Multi-window, Enable freeform windows, switched on for good (Android reads
     * them as it starts, so switching them off after each stream would need a restart every
     * time; when they were off, the phone says a restart makes the desktop appear). Enable
     * freeform windows on secondary display is switched on for the stream only, below.
     */
    private val WINDOWING = listOf("force_resizable_activities", "enable_non_resizable_multi_window", "enable_freeform_support")
    /** Newer Android's desktop windowing switches, where the phone has them (harmless where it doesn't). */
    private val DESKTOP_FEATURES = listOf("override_desktop_mode_features", "override_desktop_experience_features")

    private fun windowing(context: Context) {
        val now = AdbShell.run(WINDOWING.joinToString("; ") { "settings get global $it" }).orEmpty().lines().map { it.trim() }
        val wasOn = WINDOWING.indices.all { now.getOrNull(it) == "1" }
        AdbShell.run((WINDOWING + DESKTOP_FEATURES).joinToString("; ") { "settings put global $it 1" })
        if (wasOn) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, it.palsoftware.pastiera.R.string.stream_desktop_restart, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Android's desktop on screens of their own (Android 16, Developer options), put back after. */
    private fun desktopMode(context: Context, on: Boolean) {
        val prefs = SettingsManager.getPreferences(context)
        if (on) {
            windowing(context)
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

    /** Android's desktop setting back as it was, after Flux Keyboard ended with a screen open (blocking). */
    fun recover(context: Context) {
        if (current == null) desktopMode(context.applicationContext, false)
    }

    // ---- Apps on the screen ----

    /**
     * [intent]'s app on display [displayId] (0: the phone's): moved there with its task if it's
     * open (nothing lost), else opened there. Blocking (the shell).
     */
    fun bring(context: Context, intent: Intent, displayId: Int) {
        val component = intent.component ?: context.packageManager.resolveActivity(intent, 0)?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) } ?: return
        // A game's shortcut (Flux Keyboard's launcher) opens another app: that app's task is the one to move
        val trampoline = component.className == it.palsoftware.pastiera.gaming.GameLaunchActivity::class.java.name
        val target = if (trampoline) it.palsoftware.pastiera.gaming.GameLaunchActivity.packageOf(context, intent) else null
        val task = if (target != null && target != context.packageName) rootTask(context, ComponentName(target, ""), byApp = true)
            else if (!trampoline) rootTask(context, component) else null
        if (task != null) {
            AdbShell.run("am display move-stack $task $displayId")
            return
        }
        if (displayId == 0) return
        // Started by the shell, so it opens even with Flux Keyboard in the background (Android
        // doesn't let a background app open things on another screen); Flux Keyboard's own
        // screens that only it can open go through its game launcher, which the shell can open
        val exported = runCatching { context.packageManager.getActivityInfo(component, 0).exported }.getOrDefault(false)
        val launch = if (exported) intent else it.palsoftware.pastiera.gaming.GameLaunchActivity.intentFor(context,
            it.palsoftware.pastiera.gaming.GameLibrary.Game(component.className.substringAfterLast('.'), component.packageName,
                launch = intent.toUri(Intent.URI_INTENT_SCHEME)))
        val uri = Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).toUri(Intent.URI_INTENT_SCHEME).replace("'", "")
        // Filling the screen, not in a window of its own (Android's desktop keeps its windows)
        val fullscreen = current?.desktop != true
        val said = AdbShell.run("am start --display $displayId" + (if (fullscreen) " --windowingMode $WINDOWING_FULLSCREEN" else "") + " '$uri'")
        FluxLog.w(TAG, "am start on screen $displayId: $uri -> ${said?.trim()}")
        val started = said?.takeUnless { it.contains("Error", true) || it.contains("Exception") }
        if (started == null) runCatching {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            if (fullscreen) runCatching {
                ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(options, WINDOWING_FULLSCREEN)
            }
            context.startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
        }
    }

    /**
     * Opens [intent] from the background on the phone's screen: through the shell (Android
     * blocks a background app opening things), Flux Keyboard's own screens through its game
     * launcher, else as usual.
     */
    fun startFromBackground(context: Context, intent: Intent) {
        val component = intent.component ?: context.packageManager.resolveActivity(intent, 0)?.activityInfo
            ?.let { ComponentName(it.packageName, it.name) }
        val exported = component != null && runCatching { context.packageManager.getActivityInfo(component, 0).exported }.getOrDefault(false)
        val launch = if (exported || component == null) intent else it.palsoftware.pastiera.gaming.GameLaunchActivity.intentFor(context,
            it.palsoftware.pastiera.gaming.GameLibrary.Game(component.className.substringAfterLast('.'), component.packageName,
                launch = intent.toUri(Intent.URI_INTENT_SCHEME)))
        val uri = Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).toUri(Intent.URI_INTENT_SCHEME).replace("'", "")
        val said = if (AdbShell.available()) AdbShell.run("am start '$uri'") else null
        FluxLog.w(TAG, "am start: $uri -> ${said?.trim()}")
        val started = said != null && !said.contains("Error", true) && !said.contains("Exception")
        if (!started) runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * The root task [component]'s app is open in, from `am stack list`: its app's, or for Flux
     * Keyboard's own (the Xbox player), the one with that activity.
     */
    private fun rootTask(context: Context, component: ComponentName, byApp: Boolean = false): Int? {
        val list = AdbShell.run("am stack list") ?: return null
        val own = component.packageName == context.packageName && !byApp
        return rootTaskIn(list, component.packageName, if (own) component.className.substringAfterLast('.') else null)
    }

    /** WindowConfiguration.WINDOWING_MODE_FULLSCREEN. */
    private const val WINDOWING_FULLSCREEN = 1

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
        }.onFailure { FluxLog.w(TAG, "no input for the screen: $it") }
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
        if (!focused || !keysToScreen || current == null || event.keyCode in PHONE_KEYS) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        if (event.repeatCount > 0) return true
        val pad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || KeyEvent.isGamepadButton(event.keyCode)
        send("K ${if (event.action == KeyEvent.ACTION_DOWN) 1 else 0} ${event.keyCode} ${if (pad) "g" else "k"}")
        return true
    }

    /** A finger down (d), moved (m) or up (u) at a point on the screen. */
    fun touch(kind: Char, finger: Int, x: Float, y: Float) = send("T $kind $finger $x $y")

    // ---- A mouse on the screen (the phone's screen as its trackpad), with a pointer drawn ----

    @Volatile private var pointerX = -1f
    @Volatile private var pointerY = -1f
    private var pointer: android.view.View? = null

    /** The mouse moved by [dx], [dy] screen pixels. */
    fun mouseMove(context: Context, dx: Float, dy: Float) {
        val screen = current ?: return
        if (pointerX < 0) { pointerX = screen.width / 2f; pointerY = screen.height / 2f }
        pointerX = (pointerX + dx).coerceIn(0f, screen.width - 1f)
        pointerY = (pointerY + dy).coerceIn(0f, screen.height - 1f)
        send("MA $pointerX $pointerY")
        showPointer(context, screen)
    }

    /** The left (or [right]) mouse button pressed or let go. */
    fun mouseButton(down: Boolean, right: Boolean = false) = send("B ${if (down) 1 else 0} ${if (right) 2 else 1}")

    /** The mouse wheel turned, in notches (up and right positive). */
    fun scroll(vertical: Float, horizontal: Float) = send("W $vertical $horizontal")

    /**
     * The pointer drawn on the screen where the mouse is (Android draws none for a mouse it
     * didn't see plugged in): a small arrow in a window of Flux Keyboard's on that display.
     */
    private fun showPointer(context: Context, screen: Screen) {
        val x = pointerX.toInt(); val y = pointerY.toInt()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (current !== screen) return@post
            runCatching {
                val view = pointer ?: run {
                    if (!android.provider.Settings.canDrawOverlays(context)) return@post
                    val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(screen.id) ?: return@post
                    val displayContext = context.createDisplayContext(display)
                    val windows = displayContext.getSystemService(android.view.WindowManager::class.java) ?: return@post
                    val arrow = PointerView(displayContext)
                    val size = (screen.height / 40).coerceAtLeast(20)
                    windows.addView(arrow, pointerParams(x, y, size))
                    pointerWindows = windows
                    pointer = arrow
                    arrow
                }
                val params = view.layoutParams as android.view.WindowManager.LayoutParams
                params.x = x; params.y = y
                pointerWindows?.updateViewLayout(view, params)
            }
        }
    }

    private var pointerWindows: android.view.WindowManager? = null

    private fun pointerParams(x: Int, y: Int, size: Int) = android.view.WindowManager.LayoutParams(
        size, (size * 1.5f).toInt(),
        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; this.x = x; this.y = y }

    private fun hidePointer() {
        val view = pointer ?: return
        val windows = pointerWindows
        pointer = null; pointerWindows = null
        pointerX = -1f; pointerY = -1f
        android.os.Handler(android.os.Looper.getMainLooper()).post { runCatching { windows?.removeView(view) } }
    }

    /** An arrow pointer: white with a dark edge, readable on any picture. */
    private class PointerView(context: Context) : android.view.View(context) {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        private val edge = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val path = android.graphics.Path().apply {
                moveTo(1f, 1f); lineTo(1f, h * 0.8f); lineTo(w * 0.3f, h * 0.6f); lineTo(w * 0.5f, h - 1f)
                lineTo(w * 0.65f, h * 0.92f); lineTo(w * 0.45f, h * 0.55f); lineTo(w - 1f, h * 0.55f); close()
            }
            canvas.drawPath(path, fill)
            canvas.drawPath(path, edge)
        }
    }
}
