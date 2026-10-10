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
    private const val ACTION_PLACE = "it.palsoftware.pastiera.GAME_MODE_PLACE"

    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var profile: GameProfile? = null
    @Volatile private var frontPackage: String? = null

    /**
     * An app opened straight from one with a profile that has none itself: likely the game
     * player of a launcher (it may have a name of its own), offered to add to its profile.
     */
    @Volatile private var openedFromGame: String? = null
    fun lastApp(): String? = openedFromGame
    @Volatile private var input: Writer? = null
    private var inputProcess: Process? = null
    private var trackpad: Process? = null
    private var savedRotation: String? = null
    /** The size override before a sideways game's (null when none is applied). */
    private var savedSize: String? = null
    @Volatile private var screenW = 1080f
    @Volatile private var screenH = 1080f

    /** A text field of the game has the keyboard open: keys type, not play. */
    @Volatile var typing = false

    fun active(): Boolean = profile != null

    /** The placer saved new spots: play with them from now on. */
    internal fun placed(context: Context, updated: GameProfile) {
        GameProfiles.save(context, updated)
        if (profile?.id == updated.id) profile = GameTouchEditor.withDefaults(updated)
    }

    /** The app in front changed (from the accessibility helper). */
    fun onAppInFront(context: Context, packageName: String) {
        val app = context.applicationContext
        if (packageName == app.packageName || packageName == "com.android.systemui") return
        val previous = frontPackage
        if (previous != null && previous != packageName && GameProfiles.forPackage(app, previous).isNotEmpty() &&
            GameProfiles.forPackage(app, packageName).isEmpty() && !isHomeApp(app, packageName)
        ) openedFromGame = packageName
        frontPackage = packageName
        if (!GameProfiles.enabled(app)) {
            if (profile != null) worker.execute { stop(app) }
            return
        }
        worker.execute { followGame(app, packageName) }
    }

    /** A home screen app (going home from a game isn't opening its player). */
    private fun isHomeApp(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .any { it.activityInfo.packageName == packageName }
    }.getOrDefault(false)

    private val lastDetected = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val watcher = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var watch: java.util.concurrent.ScheduledFuture<*>? = null

    /**
     * The app's profile: the running game's, when it was started from inside the app and can be
     * told (GameDetect), else the one chosen last. While the app has several, it's checked again
     * every few seconds, for a game started from its own list.
     */
    private fun followGame(app: Context, packageName: String) {
        if (frontPackage != packageName) return
        val profiles = GameProfiles.forPackage(app, packageName)
        // A game seen starting switches its profile on once: a profile picked by hand from the
        // notification afterwards stays, until another game starts
        GameDetect.detect(packageName, profiles)?.let { seen ->
            if (lastDetected[packageName] != seen.id) {
                lastDetected[packageName] = seen.id
                GameProfiles.setActive(app, packageName, seen.id)
            }
        }
        val next = GameProfiles.active(app, packageName)
        if (next?.id != profile?.id) {
            if (next != null) start(app, next) else stop(app)
        }
        // GameHub and GameNative: their own screens take the keys as a controller, their games
        // (run through Wine) only by touch
        val launcher = next != null && GameApps.appFor(next).let { it == "GameHub" || it == "GameNative" }
        inMenus = launcher && !wineRunning()
        watch?.cancel(false)
        watch = if (launcher) {
            watcher.schedule({ worker.execute { followGame(app, packageName) } }, 2, java.util.concurrent.TimeUnit.SECONDS)
        } else if (next != null && profiles.size > 1) {
            watcher.schedule({ worker.execute { followGame(app, packageName) } }, 5, java.util.concurrent.TimeUnit.SECONDS)
        } else null
    }

    /** In a launcher's own screens (its library, a game's page), not a game. */
    @Volatile private var inMenus = false

    /** A game running: Wine's server, or the emulator it runs under. */
    private fun wineRunning(): Boolean {
        val names = AdbShell.run("ps -A -o NAME; true", 3_000) ?: return true
        return names.lineSequence().any { name ->
            name.contains("wineserver") || name.contains("box64") || name.endsWith(".exe", ignoreCase = true)
        }
    }

    /**
     * In a game (a profile on, nothing being typed, the placer shut): keys go to it as they
     * are, past the keyboard's own handling. Each launcher or emulator maps them itself.
     */
    fun passesKeys(): Boolean = profile != null && !typing && !GameTouchEditor.open

    private fun send(line: String) {
        val writer = input ?: return
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }
            .onFailure { Log.w(TAG, "input helper gone: $it"); input = null }
    }

    private fun start(context: Context, next: GameProfile) {
        if (profile == null) {
            if (!AdbShell.available()) return
            startInput(context)
        }
        // GameHub's games: its on-screen buttons pressed for the keys, unless placed by hand
        profile = GameTouchEditor.withDefaults(next)
        // Each game's own screen choice, also when switching game mid-way
        screen(context, next.screen)
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
        screen(context, ScreenMode.APP)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** The phone's own keyboard. */
    private fun keyboardDevice(): Int? = android.view.InputDevice.getDeviceIds().firstOrNull { id ->
        val device = android.view.InputDevice.getDevice(id) ?: return@firstOrNull false
        !device.isVirtual && device.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
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
            // The sticks come from the keyboard, as a controller's would from the controller
            keyboardDevice()?.let { send("D $it") }
        }.onFailure { Log.w(TAG, "input helper couldn't start: $it") }
    }

    // ---- The screen stays upright while a game runs ----

    /** The screen choice applied now (APP: nothing changed). */
    private var appliedScreen = ScreenMode.APP

    /** Moves the screen from the choice applied to [mode]: undoes the old one, then does the new. */
    private fun screen(context: Context, mode: ScreenMode) {
        if (mode == appliedScreen) return
        if (appliedScreen == ScreenMode.SIDEWAYS) sideways(context, false)
        if (appliedScreen != ScreenMode.APP) restoreRotation()
        appliedScreen = mode
        // Turning held at upright either way: a sideways-shaped screen still mustn't turn (an
        // app asking for landscape the other way round would show upside down)
        if (mode != ScreenMode.APP) keepUpright()
        if (mode == ScreenMode.SIDEWAYS) sideways(context, true)
    }

    private fun keepUpright() {
        savedRotation = AdbShell.run("settings get system accelerometer_rotation")?.trim()
        AdbShell.run(
            "cmd window set-ignore-orientation-request true; " +
                "settings put system accelerometer_rotation 0; settings put system user_rotation 0"
        )
    }

    /** The camera cutout's depth at the top of the upright screen, in pixels (0 without one). */
    private fun cutoutTop(context: Context): Int = runCatching {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        display?.cutout?.let { maxOf(it.safeInsetTop, it.safeInsetBottom, it.safeInsetLeft, it.safeInsetRight) } ?: 0
    }.getOrDefault(0)

    /** The landscape-shaped size for an upright [w] x [h] screen, short enough to sit clear of a [cutout] above and below. */
    internal fun sidewaysSize(w: Int, h: Int, cutout: Int): Pair<Int, Int> {
        // Shown at the screen's width, centred: its height scales by w/h, the rest is a band
        // above and below, each wider than the cutout
        val clear = ((h - 2 * cutout - 8).toLong() * h / w).toInt()
        val height = minOf(w, clear).coerceAtLeast(w / 2) and 1.inv()
        return h to height
    }

    /**
     * A sideways-only game (GameHub turns the screen whatever it's told): the screen's size is
     * swapped, so it's landscape-shaped while the phone stays upright and the game has no need to
     * turn it, a little shorter than the screen's width so it sits clear of the camera cutout.
     * Taps follow the new shape.
     */
    private fun sideways(context: Context, on: Boolean) {
        if (on == (savedSize != null)) return
        if (on) {
            val sizes = AdbShell.run("wm size") ?: return
            val physical = Regex("Physical size: (\\d+)x(\\d+)").find(sizes) ?: return
            val (w, h) = physical.destructured.toList().map { it.toInt() }.let { minOf(it[0], it[1]) to maxOf(it[0], it[1]) }
            savedSize = Regex("Override size: (\\d+x\\d+)").find(sizes)?.groupValues?.get(1) ?: ""
            val (width, height) = sidewaysSize(w, h, cutoutTop(context))
            AdbShell.run("wm size ${width}x$height")
            screenW = width.toFloat(); screenH = height.toFloat()
        } else {
            val saved = savedSize ?: return
            savedSize = null
            AdbShell.run(if (saved.isEmpty()) "wm size reset" else "wm size $saved")
            val sizes = AdbShell.run("wm size").orEmpty()
            val (w, h) = Regex("(\\d+)x(\\d+)").find(saved.ifEmpty { sizes })?.destructured?.toList()?.map { it.toFloat() }
                ?.let { minOf(it[0], it[1]) to maxOf(it[0], it[1]) } ?: (screenH to screenW)
            screenW = w; screenH = h
        }
        send("S ${screenW.toInt()} ${screenH.toInt()}")
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

    private const val DEAD_ZONE = 0.33f

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
                    val current = profile
                    if (current != null && (!inMenus && current.stickZones.containsKey(at / 2))) dragStick(at) else sendSticks()
                }
                TrackpadRole.MOUSE -> worker.execute { send("M ${dx * 1.5f} ${dy * 1.5f}") }
                TrackpadRole.WASD_KEYS, TrackpadRole.ARROW_KEYS -> directionKeys((x - originX) / radius, (y - originY) / radius)
                TrackpadRole.NONE -> Unit
            }
        }

        /** Direction keys held: up, left, down, right. */
        private val held = BooleanArray(4)

        /** Each direction's key down once pushed past a third of the way, up when back. */
        private fun directionKeys(vx: Float, vy: Float) {
            val keys = if (role == TrackpadRole.WASD_KEYS) {
                intArrayOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D)
            } else {
                intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            val want = booleanArrayOf(vy < -DEAD_ZONE, vx < -DEAD_ZONE, vy > DEAD_ZONE, vx > DEAD_ZONE)
            for (i in 0..3) {
                if (want[i] == held[i]) continue
                held[i] = want[i]
                val line = "K ${if (want[i]) 1 else 0} ${keys[i]} k"
                worker.execute { send(line) }
            }
        }

        private fun up() {
            when (role) {
                TrackpadRole.LEFT_STICK, TrackpadRole.RIGHT_STICK -> {
                    val at = if (role == TrackpadRole.LEFT_STICK) 0 else 2
                    sticks[at] = 0f; sticks[at + 1] = 0f
                    if (stickHeld[at / 2]) {
                        val finger = 1 + at / 2
                        worker.execute { send("T u $finger") }
                        stickHeld[at / 2] = false
                    } else sendSticks()
                }
                // A tap clicks
                TrackpadRole.MOUSE -> if (SystemClock.uptimeMillis() - downAt < 200 && moved < radius / 4) {
                    worker.execute { send("B 1 1"); send("B 0 1") }
                }
                TrackpadRole.WASD_KEYS, TrackpadRole.ARROW_KEYS -> directionKeys(0f, 0f)
                TrackpadRole.NONE -> Unit
            }
        }

        private val stickHeld = BooleanArray(2)

        /** An on-screen stick: a finger on its place, pushed as far as the stick goes. */
        private fun dragStick(at: Int) {
            val zone = profile?.stickZones?.get(at / 2) ?: return
            val cx = zone.first * screenW
            val cy = zone.second * screenH
            val reach = zone.third * screenW
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
        // Sticks placed on the game's own on-screen sticks (which a game may only take by touch),
        // drawn over it
        val place = PendingIntent.getBroadcast(
            context, 1, Intent(context, Receiver::class.java).setAction(ACTION_PLACE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Only where a stick is wanted
        val wantsSticks = listOf(current.leftHalf, current.rightHalf)
            .any { it == TrackpadRole.LEFT_STICK || it == TrackpadRole.RIGHT_STICK }
        if (wantsSticks) {
            builder.addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.game_mode_place_sticks), place).build())
        }
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
            if (intent.action == ACTION_PLACE) {
                profile?.let { GameTouchEditor.show(context.applicationContext, it) }
                return
            }
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
