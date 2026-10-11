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

    /**
     * Profiles were deleted, or gaming mode was switched off, from Flux Keyboard's own settings
     * (an app gaming mode doesn't follow): the one in use gone, the app's next one takes over, or
     * gaming mode stops, rather than staying on everywhere until another app comes to the front.
     */
    fun profilesChanged(context: Context) {
        val app = context.applicationContext
        val id = activeId ?: return
        worker.execute {
            if (activeId != id) return@execute
            val stillThere = GameProfiles.all(app).any { it.id == id }
            if (stillThere && GameProfiles.enabled(app)) return@execute
            val next = frontPackage?.takeIf { GameProfiles.enabled(app) }?.let { GameProfiles.active(app, it) }
            if (next != null) start(app, next) else stop(app)
        }
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
        // Another app in front: the screen back to how it was, even if gaming mode stopped
        // without putting it back (its process ended mid-game)
        if (GameProfiles.forPackage(app, packageName).isEmpty() && !GameTouchEditor.open) worker.execute { recover(app) }
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
        GameDetect.detect(packageName, profiles.filter { it.isGame })?.let { seen ->
            if (lastDetected[packageName] != seen.id) {
                lastDetected[packageName] = seen.id
                GameProfiles.setActive(app, packageName, seen.id)
            }
        }
        val next = GameProfiles.active(app, packageName)
        if (next?.id != activeId) {
            if (next != null) start(app, next) else stop(app)
        }
        // GameHub and GameNative: their own screens take the keys as a controller, their games
        // (run through Wine) only by touch
        val launcher = next != null && GameApps.appFor(next).let { it == "GameHub" || it == "GameNative" }
        inMenus = launcher && !wineRunning()
        watch?.cancel(false)
        watch = if (launcher) {
            watcher.schedule({ worker.execute { followGame(app, packageName) } }, 2, java.util.concurrent.TimeUnit.SECONDS)
        } else if (next != null && profiles.any { it.isGame }) {
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

    /** The profile plays as a real controller (root): its keys become the controller's buttons. */
    @Volatile private var pad = false

    /** Which of the controller's buttons each key is (see [VirtualGamepad.button]). */
    private val PAD_BUTTONS = mapOf(
        KeyEvent.KEYCODE_L to 0, KeyEvent.KEYCODE_P to 1, KeyEvent.KEYCODE_K to 3, KeyEvent.KEYCODE_O to 4,
        KeyEvent.KEYCODE_Q to 6, KeyEvent.KEYCODE_E to 7, KeyEvent.KEYCODE_Z to 8, KeyEvent.KEYCODE_C to 9,
        KeyEvent.KEYCODE_VOLUME_UP to 6, KeyEvent.KEYCODE_VOLUME_DOWN to 8,
        KeyEvent.KEYCODE_DEL to 10, KeyEvent.KEYCODE_ENTER to 11, KeyEvent.KEYCODE_F to 13, KeyEvent.KEYCODE_J to 14
    )
    private val PAD_DPAD = setOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D)
    private val dpadHeld = mutableSetOf<Int>()

    /**
     * A key, before the app sees it: a key placed on the screen taps there; with a real controller, the controller's own keys (W A S D
     * the d-pad, O K P L its Y X B A, Q E Z C the shoulders and triggers, Enter Start, Backspace
     * Select, F and J the stick presses) press its buttons; true when it took the key.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!passesKeys()) return false
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        // A key placed on the screen touches its spot while held, whatever else it is
        val tap = if (inMenus) null else profile?.taps?.get(code)
        if (tap != null) {
            if (event.repeatCount == 0) {
                val finger = 100 + code
                worker.execute { if (down) send("T d $finger ${tap.first * screenW} ${tap.second * screenH}") else send("T u $finger") }
            }
            return true
        }
        // Right stick on keys: Y G H B push it
        if (profile?.rightStickKeys == true && code in RIGHT_STICK_KEYS) {
            if (event.repeatCount == 0) rightStickKey(code, down)
            return true
        }
        if (!pad) return false
        val button = PAD_BUTTONS[code]
        when {
            button != null -> if (event.repeatCount == 0) worker.execute { send("P $button ${if (down) 1 else 0}") }
            code in PAD_DPAD -> {
                val changed = if (down) dpadHeld.add(code) else dpadHeld.remove(code)
                if (changed) {
                    fun held(key: Int) = if (key in dpadHeld) 1 else 0
                    val line = "H ${held(KeyEvent.KEYCODE_W)} ${held(KeyEvent.KEYCODE_S)} ${held(KeyEvent.KEYCODE_A)} ${held(KeyEvent.KEYCODE_D)}"
                    worker.execute { send(line) }
                }
            }
            else -> return false
        }
        return true
    }

    private fun send(line: String) {
        val writer = input ?: return
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }
            .onFailure { Log.w(TAG, "input helper gone: $it"); input = null }
    }

    /** The profile chosen for the app in front (a game, or the app's own); [profile] is what it plays with. */
    @Volatile private var activeId: String? = null

    private fun start(context: Context, chosen: GameProfile) {
        if (profile == null) {
            if (!AdbShell.available()) return
            startInput(context)
        }
        activeId = chosen.id
        // A game plays with its app's settings unless it has its own (Dolphin: Wii or GameCube)
        val next = GameProfiles.effective(context, chosen)
        // GameHub's and GameNative's games: their on-screen sticks, unless placed by hand
        profile = GameTouchEditor.withDefaults(next)
        // With root, a profile can play as a real controller, plugged in while it's on
        val wantsPad = next.controller && it.palsoftware.pastiera.adb.RootShell.active
        if (wantsPad != pad) {
            pad = wantsPad
            dpadHeld.clear()
            worker.execute { send(if (wantsPad) "G 1" else "G 0") }
        }
        // Each game's own screen choice, also when switching game mid-way
        screen(context, next.screen)
        startTrackpad(next)
        notify(context, next)
    }

    private fun stop(context: Context) {
        if (profile == null) return
        profile = null
        activeId = null
        trackpadRun++
        sticks.fill(0f); stickHeld.fill(false); rightKeysHeld.clear()
        if (pad) { pad = false; dpadHeld.clear(); send("G 0") }
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

    private const val KEY_RESTORE_SIZE = "game_mode_restore_size"
    private const val KEY_RESTORE_ROTATION = "game_mode_restore_rotation"

    /** What gaming mode changed on the screen, kept until it's put back: it outlives the process. */
    private fun pending(context: Context) = it.palsoftware.pastiera.SettingsManager.getPreferences(context)

    /**
     * Puts the screen's size and turning back if gaming mode changed them and no game is playing:
     * for when it ended without undoing them (blocking: runs the shell).
     */
    fun recover(context: Context) {
        if (profile != null) return
        val prefs = pending(context)
        val size = prefs.getString(KEY_RESTORE_SIZE, null)
        val rotation = prefs.getString(KEY_RESTORE_ROTATION, null)
        if (size == null && rotation == null) return
        if (!AdbShell.available()) return
        if (size != null) AdbShell.run(if (size.isEmpty()) "wm size reset" else "wm size $size")
        if (rotation != null) AdbShell.run(
            "cmd window set-ignore-orientation-request false; " +
                "settings put system accelerometer_rotation ${rotation.takeIf { it == "0" || it == "1" } ?: "1"}"
        )
        prefs.edit().remove(KEY_RESTORE_SIZE).remove(KEY_RESTORE_ROTATION).apply()
        savedSize = null
        savedRotation = null
        appliedScreen = ScreenMode.APP
    }

    /** The screen choice applied now (APP: nothing changed). */
    private var appliedScreen = ScreenMode.APP

    /** Moves the screen from the choice applied to [mode]: undoes the old one, then does the new. */
    private fun screen(context: Context, mode: ScreenMode) {
        if (mode == appliedScreen) return
        if (appliedScreen == ScreenMode.SIDEWAYS) sideways(context, false)
        if (appliedScreen != ScreenMode.APP) restoreRotation(context)
        appliedScreen = mode
        // Turning held at upright either way: a sideways-shaped screen still mustn't turn (an
        // app asking for landscape the other way round would show upside down)
        if (mode != ScreenMode.APP) keepUpright(context)
        if (mode == ScreenMode.SIDEWAYS) sideways(context, true)
    }

    private fun keepUpright(context: Context) {
        savedRotation = AdbShell.run("settings get system accelerometer_rotation")?.trim()
        pending(context).edit().putString(KEY_RESTORE_ROTATION, savedRotation ?: "1").apply()
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
            pending(context).edit().putString(KEY_RESTORE_SIZE, savedSize).apply()
            val (width, height) = sidewaysSize(w, h, cutoutTop(context))
            AdbShell.run("wm size ${width}x$height")
            screenW = width.toFloat(); screenH = height.toFloat()
        } else {
            val saved = savedSize ?: return
            savedSize = null
            AdbShell.run(if (saved.isEmpty()) "wm size reset" else "wm size $saved")
            pending(context).edit().remove(KEY_RESTORE_SIZE).apply()
            val sizes = AdbShell.run("wm size").orEmpty()
            val (w, h) = Regex("(\\d+)x(\\d+)").find(saved.ifEmpty { sizes })?.destructured?.toList()?.map { it.toFloat() }
                ?.let { minOf(it[0], it[1]) to maxOf(it[0], it[1]) } ?: (screenH to screenW)
            screenW = w; screenH = h
        }
        send("S ${screenW.toInt()} ${screenH.toInt()}")
    }

    private fun restoreRotation(context: Context) {
        val saved = savedRotation ?: return
        savedRotation = null
        pending(context).edit().remove(KEY_RESTORE_ROTATION).apply()
        AdbShell.run(
            "cmd window set-ignore-orientation-request false; " +
                "settings put system accelerometer_rotation ${saved.takeIf { it == "0" || it == "1" } ?: "1"}"
        )
    }

    // ---- The trackpad: sticks or a mouse ----

    /** Which trackpad reading is current: one started before gaming mode stopped or switched ends itself. */
    @Volatile private var trackpadRun = 0

    private fun startTrackpad(current: GameProfile) {
        val run = ++trackpadRun
        trackpad?.destroy(); trackpad = null
        if (current.leftHalf == TrackpadRole.NONE && current.rightHalf == TrackpadRole.NONE) return
        Thread {
            runCatching {
                val device = TrackpadInputDeviceDiscovery.selectAutomatic(ShizukuTrackpadDeviceDiscovery.discoverBlocking())
                    ?: return@Thread
                // Stopped (or restarted) while the trackpad was being found
                if (run != trackpadRun || profile == null) return@Thread
                val process = AdbShell.newProcess(arrayOf("getevent", "-l", device.path))
                if (run != trackpadRun || profile == null) { process.destroy(); return@Thread }
                trackpad = process
                val span = device.xRange?.takeIf { it.isValid }?.let { it.min to it.max } ?: (0f to 1440f)
                val reader = TrackpadReader(span.first, span.second)
                process.inputStream.bufferedReader().forEachLine { line -> reader.line(line) }
            }.onFailure { Log.w(TAG, "trackpad couldn't be read: $it") }
        }.apply { isDaemon = true; start() }
    }

    private const val DEAD_ZONE = 0.33f
    /** The Titan's trackpad reports one finger: moving to another, it lifts and touches again this soon. */
    private const val HANDOVER_MS = 30L
    /** ...and glides from the last finger's spot to the new one's over this long. */
    private const val GLIDE_MS = 220L

    /** The sticks (left x y, right x y), -1 to 1: from the trackpad, and the right one from keys too. */
    private val sticks = FloatArray(4)
    private val stickHeld = BooleanArray(2)

    /** A stick moved: on the game's on-screen stick where one is placed, else as a controller's. */
    @Synchronized
    private fun pushStick(at: Int) {
        val current = profile
        if (current != null && !pad && !inMenus && current.stickZones.containsKey(at / 2)) dragStick(at) else sendSticks()
    }

    @Synchronized
    private fun releaseStick(at: Int) {
        sticks[at] = 0f; sticks[at + 1] = 0f
        if (stickHeld[at / 2]) {
            val finger = 1 + at / 2
            worker.execute { send("T u $finger") }
            stickHeld[at / 2] = false
        } else sendSticks()
    }

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
        // The real controller's sticks, with root; else stick events from the keyboard
        val line = (if (pad) "A" else "J") + " ${sticks[0]} ${sticks[1]} ${sticks[2]} ${sticks[3]}"
        worker.execute { send(line) }
    }

    /** The right stick's keys (up, left, right, down), with Right stick on keys. */
    private val RIGHT_STICK_KEYS = intArrayOf(KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_B)
    private val rightKeysHeld = mutableSetOf<Int>()

    /** Y G H B held: the right stick pushed their way (a diagonal no further than a straight push). */
    private fun rightStickKey(code: Int, down: Boolean) {
        val changed = if (down) rightKeysHeld.add(code) else rightKeysHeld.remove(code)
        if (!changed) return
        fun held(i: Int) = if (RIGHT_STICK_KEYS[i] in rightKeysHeld) 1f else 0f
        var x = held(2) - held(1)
        var y = held(3) - held(0)
        if (x != 0f && y != 0f) { x *= 0.7071f; y *= 0.7071f }
        if (x == 0f && y == 0f) releaseStick(2) else { sticks[2] = x; sticks[3] = y; pushStick(2) }
    }

    /**
     * The trackpad as the game's controls: each finger plays the half it lands on (the left half
     * always the left stick's when it's one). The Titan's trackpad reports one finger at a time,
     * moving to the one that moved last: the finger it leaves is taken as still down, its stick
     * held where it was, until the trackpad comes back to it or every finger is lifted.
     */
    private class TrackpadReader(private val min: Float, private val max: Float) {
        private class Finger(val role: TrackpadRole, val left: Boolean, var originX: Float, var originY: Float, val downAt: Long) {
            var lastX = originX; var lastY = originY
            var moved = 0f
            /** Direction keys held: up, left, down, right. */
            val held = BooleanArray(4)
            /** Gliding after a handover until then: its moves aren't the finger's. */
            var settleUntil = 0L
            /** Its origin is taken once it has settled (a finger new to the trackpad's report). */
            var reorigin = false
        }

        private val fingers = HashMap<Int, Finger>()
        /** Fingers the trackpad stopped reporting but that are still down, by half (left true). */
        private val held = HashMap<Boolean, Finger>()
        private val radius = (max - min) / 8f
        private val slots = TouchSlots(::begin, ::move, ::up)
        private val lock = Any()
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var pendingUp: Runnable? = null
        private var pendingSlot = -1

        fun line(line: String) = synchronized(lock) { slots.line(line) }

        private fun roleFor(current: GameProfile, left: Boolean): TrackpadRole = when {
            left -> current.leftHalf
            current.rightStickKeys && current.rightHalf == TrackpadRole.RIGHT_STICK -> TrackpadRole.NONE
            else -> current.rightHalf
        }

        private fun begin(slot: Int, x: Int, y: Int) {
            val current = profile ?: return
            val now = SystemClock.uptimeMillis()
            val gone = pendingUp?.let { handler.removeCallbacks(it); pendingUp = null; fingers.remove(pendingSlot) }
            if (gone != null) {
                // A handover: the finger before is still down, and this one is on the other half
                // (where it is isn't known yet: the trackpad glides there from the last spot)
                held[gone.left] = gone
                val left = !gone.left
                val back = held.remove(left)
                fingers[slot] = (back ?: Finger(roleFor(current, left), left, x.toFloat(), y.toFloat(), now).apply { reorigin = true })
                    .apply { settleUntil = now + GLIDE_MS }
                return
            }
            val left = x < (min + max) / 2
            var role = roleFor(current, left)
            if (role != TrackpadRole.NONE && (fingers.values + held.values).any { it.role == role }) role = TrackpadRole.NONE
            fingers[slot] = Finger(role, left, x.toFloat(), y.toFloat(), now)
        }

        private fun move(slot: Int, x: Int, y: Int) {
            val f = fingers[slot] ?: return
            if (SystemClock.uptimeMillis() < f.settleUntil) return
            if (f.reorigin) {
                f.reorigin = false
                f.originX = x.toFloat(); f.originY = y.toFloat()
                f.lastX = f.originX; f.lastY = f.originY
                return
            }
            val dx = x - f.lastX; val dy = y - f.lastY
            f.moved += abs(dx) + abs(dy)
            f.lastX = x.toFloat(); f.lastY = y.toFloat()
            when (f.role) {
                TrackpadRole.LEFT_STICK, TrackpadRole.RIGHT_STICK -> {
                    val at = if (f.role == TrackpadRole.LEFT_STICK) 0 else 2
                    sticks[at] = ((x - f.originX) / radius).coerceIn(-1f, 1f)
                    sticks[at + 1] = ((y - f.originY) / radius).coerceIn(-1f, 1f)
                    pushStick(at)
                }
                TrackpadRole.MOUSE -> worker.execute { send("M ${dx * 1.5f} ${dy * 1.5f}") }
                TrackpadRole.WASD_KEYS, TrackpadRole.ARROW_KEYS -> directionKeys(f, (x - f.originX) / radius, (y - f.originY) / radius)
                TrackpadRole.NONE -> Unit
            }
        }

        /** Each direction's key down once pushed past a third of the way, up when back. */
        private fun directionKeys(f: Finger, vx: Float, vy: Float) {
            val keys = if (f.role == TrackpadRole.WASD_KEYS) {
                intArrayOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D)
            } else {
                intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            val want = booleanArrayOf(vy < -DEAD_ZONE, vx < -DEAD_ZONE, vy > DEAD_ZONE, vx > DEAD_ZONE)
            for (i in 0..3) {
                if (want[i] == f.held[i]) continue
                f.held[i] = want[i]
                val line = "K ${if (want[i]) 1 else 0} ${keys[i]} k"
                worker.execute { send(line) }
            }
        }

        /** Lifted, or a handover: which, is known when the next finger comes (or doesn't) soon after. */
        private fun up(slot: Int) {
            pendingUp?.let { handler.removeCallbacks(it); it.run() }
            pendingSlot = slot
            val lifted = Runnable {
                synchronized(lock) {
                    pendingUp = null
                    fingers.remove(slot)?.let { end(it) }
                    // Every finger off: the ones the trackpad had stopped reporting too
                    if (fingers.isEmpty()) { held.values.forEach { end(it) }; held.clear() }
                }
            }
            pendingUp = lifted
            handler.postDelayed(lifted, HANDOVER_MS)
        }

        private fun end(f: Finger) {
            when (f.role) {
                TrackpadRole.LEFT_STICK -> releaseStick(0)
                TrackpadRole.RIGHT_STICK -> releaseStick(2)
                // A tap clicks
                TrackpadRole.MOUSE -> if (SystemClock.uptimeMillis() - f.downAt < 200 && f.moved < radius / 4) {
                    worker.execute { send("B 1 1"); send("B 0 1") }
                }
                TrackpadRole.WASD_KEYS, TrackpadRole.ARROW_KEYS -> directionKeys(f, 0f, 0f)
                TrackpadRole.NONE -> Unit
            }
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
        // Keys and sticks placed on the game's own on-screen controls (for games that only take
        // touch), drawn over it
        val place = PendingIntent.getBroadcast(
            context, 1, Intent(context, Receiver::class.java).setAction(ACTION_PLACE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.app.Notification.Action.Builder(null, context.getString(R.string.game_mode_place_keys), place).build())
        val pkg = frontPackage
        if (pkg != null && GameProfiles.appProfiles(context, pkg).size > 1) {
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
            // Next: the app's other settings (Dolphin's GameCube and Wii)
            val pkg = frontPackage ?: return
            val profiles = GameProfiles.appProfiles(context, pkg).takeIf { it.size > 1 } ?: return
            val at = profiles.indexOfFirst { it.id == profile?.id }
            val next = profiles[(at + 1) % profiles.size]
            GameProfiles.setActive(context, pkg, next.id)
            val app = context.applicationContext
            worker.execute { start(app, next) }
        }
    }
}
