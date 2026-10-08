package it.palsoftware.pastiera.gaming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.adb.AdbShell
import it.palsoftware.pastiera.inputmethod.trackpad.ShizukuTrackpadDeviceDiscovery
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadInputDeviceDiscovery
import java.io.Writer
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Gaming mode: in an app with a game profile, the keyboard plays the game. Keys become
 * controller buttons or other keys, the trackpad's halves sticks or a mouse, all sent through
 * the ADB shell ([GameInputServer]); the screen can stay upright. Keys reach it through the
 * accessibility helper, as the keyboard itself isn't open in a game. Typing into a text field
 * in the game works as usual.
 */
object GameMode {
    private const val TAG = "FluxGameMode"
    private const val CHANNEL = "game_mode"
    private const val NOTIFICATION_ID = 7302
    private const val ACTION_NEXT = "it.palsoftware.pastiera.GAME_MODE_NEXT"

    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var profile: GameProfile? = null
    @Volatile private var frontPackage: String? = null
    @Volatile private var input: Writer? = null
    private var inputProcess: Process? = null
    private var trackpad: Process? = null
    private var deviceSent = -1
    private var savedRotation: String? = null
    @Volatile private var screenW = 1080f
    @Volatile private var screenH = 1080f

    /** A text field of the game has the keyboard open: keys type, not play. */
    @Volatile var typing = false

    fun active(): Boolean = profile != null

    /** The app in front changed (from the accessibility helper). */
    fun onAppInFront(context: Context, packageName: String) {
        val app = context.applicationContext
        if (packageName == app.packageName || packageName == "com.android.systemui") return
        frontPackage = packageName
        val next = if (GameProfiles.enabled(app)) GameProfiles.active(app, packageName) else null
        if (next?.id == profile?.id) return
        worker.execute { if (next != null) start(app, next) else stop(app) }
    }

    /** A key, before the app sees it: true when gaming mode took it. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val current = profile ?: return false
        if (typing) return false
        val action = current.keys[event.keyCode] ?: return false
        if (event.repeatCount > 0) return true
        val down = event.action == KeyEvent.ACTION_DOWN
        val device = event.deviceId
        worker.execute {
            if (device != deviceSent) { send("D $device"); deviceSent = device }
            // GameNative: the button's place on Flux Keyboard's on-screen controls, touched
            val place = if (current.touchControls) GameNativeBridge.BUTTONS[action] else null
            when {
                place != null -> {
                    val finger = 10 + action.ordinal
                    if (down) send("T d $finger ${place.first * screenW} ${place.second * screenH}") else send("T u $finger")
                }
                action == GameAction.MOUSE_LEFT -> send("B ${if (down) 1 else 0} 1")
                action == GameAction.MOUSE_RIGHT -> send("B ${if (down) 1 else 0} 2")
                else -> send("K ${if (down) 1 else 0} ${action.keyCode} ${if (action.gamepad) "g" else "k"}")
            }
        }
        return true
    }

    private fun send(line: String) {
        val writer = input ?: return
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }
            .onFailure { Log.w(TAG, "input helper gone: $it"); input = null }
    }

    private fun start(context: Context, next: GameProfile) {
        if (profile == null) {
            if (!AdbShell.available()) return
            startInput(context)
            if (GameProfiles.keepPortrait(context)) keepUpright()
        }
        profile = next
        startTrackpad(next)
        notify(context, next)
    }

    private fun stop(context: Context) {
        if (profile == null) return
        profile = null
        trackpad?.destroy(); trackpad = null
        runCatching { input?.close() }
        input = null
        inputProcess?.destroy(); inputProcess = null
        deviceSent = -1
        restoreRotation()
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun startInput(context: Context) {
        val apk = context.applicationInfo.sourceDir
        runCatching {
            val process = AdbShell.newProcess(arrayOf(
                "sh", "-c", "CLASSPATH='$apk' exec app_process /system/bin ${GameInputServer::class.java.name}"
            ))
            listOf(process.inputStream, process.errorStream).forEach { stream ->
                Thread { runCatching { val buffer = ByteArray(256); while (stream.read(buffer) >= 0) Unit } }
                    .apply { isDaemon = true; start() }
            }
            inputProcess = process
            input = process.outputStream.bufferedWriter()
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.getRealSize(size)
            // Upright while gaming: the short side across
            screenW = minOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
            screenH = maxOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
            send("S ${screenW.toInt()} ${screenH.toInt()}")
        }.onFailure { Log.w(TAG, "input helper couldn't start: $it") }
    }

    // ---- The screen stays upright while a game runs ----

    private fun keepUpright() {
        savedRotation = AdbShell.run("settings get system accelerometer_rotation")?.trim()
        AdbShell.run(
            "cmd window set-ignore-orientation-request true; " +
                "settings put system accelerometer_rotation 0; settings put system user_rotation 0"
        )
    }

    private fun restoreRotation() {
        val saved = savedRotation ?: return
        savedRotation = null
        AdbShell.run(
            "cmd window set-ignore-orientation-request false; " +
                "settings put system accelerometer_rotation ${saved.takeIf { it == "0" || it == "1" } ?: "1"}"
        )
    }

    // ---- The trackpad: sticks or a mouse ----

    private fun startTrackpad(current: GameProfile) {
        trackpad?.destroy(); trackpad = null
        if (current.leftHalf == TrackpadRole.NONE && current.rightHalf == TrackpadRole.NONE) return
        Thread {
            runCatching {
                val device = TrackpadInputDeviceDiscovery.selectAutomatic(ShizukuTrackpadDeviceDiscovery.discoverBlocking())
                    ?: return@Thread
                val process = AdbShell.newProcess(arrayOf("getevent", "-l", device.path))
                trackpad = process
                val span = device.xRange?.takeIf { it.isValid }?.let { it.min to it.max } ?: (0f to 1440f)
                val reader = TrackpadReader(span.first, span.second)
                process.inputStream.bufferedReader().forEachLine { line -> reader.line(line) }
            }.onFailure { Log.w(TAG, "trackpad couldn't be read: $it") }
        }.apply { isDaemon = true; start() }
    }

    private class TrackpadReader(private val min: Float, private val max: Float) {
        private var x = 0; private var y = 0
        private var down = false; private var started = false
        private var originX = 0f; private var originY = 0f
        private var lastX = 0f; private var lastY = 0f
        private var downAt = 0L; private var moved = 0f
        private var role = TrackpadRole.NONE
        private val sticks = FloatArray(4)
        private val radius = (max - min) / 8f

        fun line(line: String) {
            when {
                line.contains("BTN_TOUCH") && line.contains("DOWN") -> { down = true; started = false }
                line.contains("BTN_TOUCH") && line.contains("UP") -> { if (started) up(); down = false }
                line.contains("ABS_MT_POSITION_X") -> value(line)?.let { x = it }
                line.contains("ABS_MT_POSITION_Y") -> value(line)?.let { y = it }
                line.contains("SYN_REPORT") && down -> if (started) move() else begin()
            }
        }

        private fun value(line: String): Int? = line.trim().split(Regex("\\s+")).lastOrNull()?.toIntOrNull(16)

        private fun begin() {
            started = true
            val current = profile ?: return
            role = if (x < (min + max) / 2) current.leftHalf else current.rightHalf
            originX = x.toFloat(); originY = y.toFloat()
            lastX = originX; lastY = originY
            downAt = SystemClock.uptimeMillis(); moved = 0f
        }

        private fun move() {
            val dx = x - lastX; val dy = y - lastY
            moved += abs(dx) + abs(dy)
            lastX = x.toFloat(); lastY = y.toFloat()
            when (role) {
                TrackpadRole.LEFT_STICK, TrackpadRole.RIGHT_STICK -> {
                    val at = if (role == TrackpadRole.LEFT_STICK) 0 else 2
                    sticks[at] = ((x - originX) / radius).coerceIn(-1f, 1f)
                    sticks[at + 1] = ((y - originY) / radius).coerceIn(-1f, 1f)
                    if (profile?.touchControls == true) dragStick(at) else sendSticks()
                }
                TrackpadRole.MOUSE -> worker.execute { send("M ${dx * 1.5f} ${dy * 1.5f}") }
                TrackpadRole.NONE -> Unit
            }
        }

        private fun up() {
            when (role) {
                TrackpadRole.LEFT_STICK, TrackpadRole.RIGHT_STICK -> {
                    val at = if (role == TrackpadRole.LEFT_STICK) 0 else 2
                    sticks[at] = 0f; sticks[at + 1] = 0f
                    if (profile?.touchControls == true) {
                        val finger = 1 + at / 2
                        worker.execute { send("T u $finger") }
                        stickHeld[at / 2] = false
                    } else sendSticks()
                }
                // A tap clicks
                TrackpadRole.MOUSE -> if (SystemClock.uptimeMillis() - downAt < 200 && moved < radius / 4) {
                    worker.execute { send("B 1 1"); send("B 0 1") }
                }
                TrackpadRole.NONE -> Unit
            }
        }

        private val stickHeld = BooleanArray(2)

        /** GameNative: a finger on the stick's place, pushed as far as the stick goes. */
        private fun dragStick(at: Int) {
            val centre = if (at == 0) GameNativeBridge.LEFT_STICK else GameNativeBridge.RIGHT_STICK
            val cx = centre.first * screenW
            val cy = centre.second * screenH
            val reach = GameNativeBridge.STICK_REACH * screenW
            val finger = 1 + at / 2
            val first = !stickHeld[at / 2]
            stickHeld[at / 2] = true
            val x = cx + sticks[at] * reach
            val y = cy + sticks[at + 1] * reach
            worker.execute {
                if (first) send("T d $finger $cx $cy")
                send("T m $finger $x $y")
            }
        }

        private fun sendSticks() {
            val line = "J ${sticks[0]} ${sticks[1]} ${sticks[2]} ${sticks[3]}"
            worker.execute { send(line) }
        }
    }

    // ---- The notification: which profile, and the next one for apps with several games ----

    private fun notify(context: Context, current: GameProfile) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.game_mode_title), NotificationManager.IMPORTANCE_LOW)
        )
        val builder = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.game_mode_title))
            .setContentText(current.name)
            .setOngoing(true)
        val pkg = frontPackage
        if (pkg != null && GameProfiles.forPackage(context, pkg).size > 1) {
            val next = PendingIntent.getBroadcast(
                context, 0, Intent(context, Receiver::class.java).setAction(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.game_mode_next_profile), next).build())
        }
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    /** Next profile: the game launcher's next game. */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = frontPackage ?: return
            val profiles = GameProfiles.forPackage(context, pkg).takeIf { it.isNotEmpty() } ?: return
            val at = profiles.indexOfFirst { it.id == profile?.id }
            val next = profiles[(at + 1) % profiles.size]
            GameProfiles.setActive(context, pkg, next.id)
            val app = context.applicationContext
            worker.execute { start(app, next) }
        }
    }
}
