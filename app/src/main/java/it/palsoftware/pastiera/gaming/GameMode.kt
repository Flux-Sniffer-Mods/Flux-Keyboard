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
        // Placed while streaming: kept apart from the phone's own placing
        val stored = GameProfiles.all(context).firstOrNull { it.id == updated.id } ?: updated
        // Only the placing is kept: the profile in use may carry a game's own screen over its app's
        val saved = if (streamed()) stored.copy(streamStickZones = updated.stickZones, streamTaps = updated.taps)
            else stored.copy(stickZones = updated.stickZones, taps = updated.taps)
        GameProfiles.save(context, saved)
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
        // An app that looks like a game, with no settings yet (or only found, not set up): asked
        // once whether to set it up
        if (GameProfiles.forPackage(app, packageName).isEmpty()) worker.execute { GameDetect.offer(app, packageName) }
        worker.execute { followGame(app, packageName) }
    }

    // ---- The red side key: the real controller's right trigger, read straight from the keys ----

    /** The Titan's red side key (scan code 249, beside the power button's). */
    private const val SIDE_KEY_SCAN = 249
    @Volatile private var sideKey: Process? = null

    /**
     * With root, the red side key reads as the right trigger: from its input device directly,
     * since Android may take it first (set its own action to none in the phone's settings so it
     * does nothing else). The volume keys are the left bumper and trigger.
     */
    private fun startSideKey() {
        stopSideKey()
        Thread {
            runCatching {
                val dump = AdbShell.run("getevent -p", 4_000) ?: return@Thread
                val path = sideKeyDevice(dump) ?: return@Thread
                val process = AdbShell.newProcess(arrayOf("getevent", "-l", path))
                if (!pad) { process.destroy(); return@Thread }
                sideKey = process
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (!line.contains("EV_KEY") || !line.contains("00f9")) return@forEachLine
                    val down = line.trimEnd().endsWith("DOWN")
                    if (down || line.trimEnd().endsWith("UP")) worker.execute { send("P 9 ${if (down) 1 else 0}") }
                }
            }.onFailure { Log.w(TAG, "side key couldn't be read: $it") }
        }.apply { isDaemon = true; start() }
    }

    private fun stopSideKey() { sideKey?.destroy(); sideKey = null }

    /** The input device with the red side key (key 00f9), from getevent -p. */
    internal fun sideKeyDevice(dump: String): String? {
        var device: String? = null
        for (line in dump.lineSequence()) {
            Regex("^add device \\d+:\\s+(\\S+)").find(line.trim())?.let { device = it.groupValues[1]; return@let }
            if (device != null && Regex("\\b00f9\\b").containsMatchIn(line)) return device
        }
        return null
    }

    // ---- Flux Keyboard's own player (Xbox Cloud Gaming in a web page) ----

    /** The player in front, taking the sticks (left x y, right x y) as they move. */
    @Volatile private var stickSink: ((FloatArray) -> Unit)? = null

    /**
     * The player in front: the trackpad's left half its left stick, given to [sink], and the
     * screen as [screenMode]. Keys reach the player itself.
     */
    fun attachPlayer(context: Context, screenMode: ScreenMode, sink: (FloatArray) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            stickSink = sink
            val player = GameProfile(
                id = "player", name = "player", packages = setOf(app.packageName),
                leftHalf = TrackpadRole.LEFT_STICK, rightHalf = TrackpadRole.NONE, screen = screenMode
            )
            profile = player
            activeId = null
            screen(app, screenMode)
            startTrackpad(player)
        }
    }

    /** The player gone from the front: the trackpad let go, the screen put back. */
    fun detachPlayer(context: Context) {
        val app = context.applicationContext
        worker.execute {
            if (stickSink == null) return@execute
            stickSink = null
            stop(app)
        }
    }

    /** The app gaming mode is playing a game in, if it is. */
    fun playing(): String? = frontPackage.takeIf { profile != null }

    /** [packageName] swiped away from the recent apps: gaming mode ends if it was playing there. */
    fun gone(context: Context, packageName: String) {
        val app = context.applicationContext
        worker.execute {
            if (frontPackage != packageName || profile == null) return@execute
            frontPackage = null
            stop(app)
            recover(app)
        }
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
    private val PAD_BUTTONS = FluxLayout.PAD_BUTTONS
    private val PAD_DPAD = setOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D)
    private val dpadHeld = mutableSetOf<Int>()

    /**
     * A key, before the app sees it: a key placed on the screen taps there; J B N M move the
     * right stick (with a real controller, or Right stick on keys); with a real controller, the
     * rest of [FluxLayout] presses its buttons. True when it took the key.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!passesKeys()) return false
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount == 0) trackpadReader?.keyPressed()
        // The red side key: the right trigger, read from its device (its own action kept away)
        if (pad && event.scanCode == SIDE_KEY_SCAN) return true
        // A key placed on the screen touches its spot while held, whatever else it is
        val tap = if (inMenus) null else profile?.taps?.get(code)
        if (tap != null) {
            if (event.repeatCount == 0) {
                val finger = 100 + code
                worker.execute { if (down) send("T d $finger ${tap.first * screenW} ${tap.second * screenH}") else send("T u $finger") }
            }
            return true
        }
        // Right stick on keys: J B N M push it (a real controller's, or an on-screen one; else
        // they reach the app, whose own controls map them)
        if ((pad || (profile?.rightStickKeys == true && profile?.stickZones?.containsKey(1) == true)) && code in RIGHT_STICK_KEYS) {
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
            extra?.let { send("Z ${it.id}"); screenW = it.width.toFloat(); screenH = it.height.toFloat(); send("S ${it.width} ${it.height}") }
        }
        activeId = chosen.id
        // A game plays with its app's settings unless it has its own (Dolphin: Wii or GameCube)
        val next = GameProfiles.effective(context, chosen)
        // GameHub's and GameNative's games: their on-screen sticks, unless placed by hand
        profile = GameTouchEditor.withDefaults(next.forScreen(streamed()))
        // With root, a profile can play as a real controller, plugged in while it's on
        val wantsPad = next.controller && it.palsoftware.pastiera.adb.RootShell.active
        if (wantsPad != pad) {
            pad = wantsPad
            dpadHeld.clear()
            worker.execute { send(if (wantsPad) "G 1" else "G 0") }
            if (wantsPad) startSideKey() else stopSideKey()
        }
        // Each game's own screen choice (and size), also when switching game mid-way
        screen(context, next.screen, next.phoneHeight)
        startTrackpad(next)
        notify(context, next)
    }

    private fun stop(context: Context) {
        if (profile == null) return
        profile = null
        activeId = null
        trackpadRun++
        sticks.fill(0f); stickHeld.fill(false); rightKeysHeld.clear()
        if (pad) { pad = false; dpadHeld.clear(); send("G 0"); stopSideKey() }
        trackpad?.destroy(); trackpad = null; trackpadReader = null
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
        if (profile != null || streamSize != null) return
        val prefs = pending(context)
        val size = prefs.getString(KEY_RESTORE_SIZE, null)
        val rotation = prefs.getString(KEY_RESTORE_ROTATION, null)
        if (size == null && rotation == null) return
        if (!AdbShell.available()) return
        // In one go, for a quick switch back
        AdbShell.run(listOfNotNull(
            size?.let { if (it.isEmpty()) "wm size reset" else "wm size $it" },
            rotation?.let {
                "cmd window set-ignore-orientation-request false; " +
                    "settings put system accelerometer_rotation ${it.takeIf { r -> r == "0" || r == "1" } ?: "1"}"
            }
        ).joinToString("; "), 5_000)
        prefs.edit().remove(KEY_RESTORE_SIZE).remove(KEY_RESTORE_ROTATION).apply()
        savedSize = null
        savedRotation = null
        appliedScreen = ScreenMode.APP
    }

    /** The screen choice applied now (APP: nothing changed). */
    private var appliedScreen = ScreenMode.APP
    /** What gaming mode last asked for, put back when streaming ends. */
    @Volatile private var requestedScreen = ScreenMode.APP
    @Volatile private var requestedHeight: Int? = null
    private var appliedHeight: Int? = null

    /** A landscape [size] drawn [height] rows high, its shape kept (never larger than it is). */
    internal fun scaled(size: Pair<Int, Int>, height: Int?): Pair<Int, Int> {
        if (height == null || height >= size.second) return size
        return ((size.first.toLong() * height / size.second).toInt() and 1.inv()) to (height and 1.inv())
    }
    /** Streaming's screen size (landscape-shaped), ahead of any game's choice; null when not streaming. */
    @Volatile private var streamSize: Pair<Int, Int>? = null
    private var appliedStream: Pair<Int, Int>? = null

    /** The screen at [size] while streaming to Moonlight, or back to what gaming mode wants (null). */
    fun streaming(context: Context, size: Pair<Int, Int>?) {
        val app = context.applicationContext
        worker.execute {
            streamSize = size
            screen(app, requestedScreen)
            // The game's keys placed for the screen in use now
            val id = activeId
            val current = profile
            if (id != null && current != null) GameProfiles.all(app).firstOrNull { it.id == id }?.let { chosen ->
                profile = GameTouchEditor.withDefaults(GameProfiles.effective(app, chosen).forScreen(streamed()))
            }
        }
    }

    /** A screen of its own the game plays on (Separate screen), else null: the phone's. */
    @Volatile private var extra: it.palsoftware.pastiera.display.ExtraScreen.Screen? = null

    /** Placed and played for a stream: mirrored at the stream's shape, or on a screen of its own. */
    private fun streamed() = streamSize != null || extra != null

    /**
     * The game on [screen], a screen of its own (null: back on the phone's): keys and the
     * trackpad aimed there, placed for the stream, and the phone's screen left as it is.
     */
    fun extraScreen(context: Context, screen: it.palsoftware.pastiera.display.ExtraScreen.Screen?) {
        val app = context.applicationContext
        worker.execute {
            if (extra?.id == screen?.id) return@execute
            extra = screen
            if (screen != null) {
                screen(app, requestedScreen)
                screenW = screen.width.toFloat(); screenH = screen.height.toFloat()
                send("Z ${screen.id}")
            } else {
                send("Z 0")
                val size = android.graphics.Point()
                @Suppress("DEPRECATION")
                app.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.getRealSize(size)
                screenW = minOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
                screenH = maxOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
                screen(app, requestedScreen)
            }
            send("S ${screenW.toInt()} ${screenH.toInt()}")
            val id = activeId
            if (id != null && profile != null) GameProfiles.all(app).firstOrNull { it.id == id }?.let { chosen ->
                profile = GameTouchEditor.withDefaults(GameProfiles.effective(app, chosen).forScreen(streamed()))
            }
        }
    }

    /** The screen of its own the game plays on now, if any. */
    fun extraScreen(): it.palsoftware.pastiera.display.ExtraScreen.Screen? = extra

    /**
     * Moves the screen from the choice applied to [mode]: undoes the old one, then does the new,
     * all in one go through the shell (one question first, when there's something to save), so
     * the switch is quick.
     */
    private fun screen(context: Context, requested: ScreenMode, height: Int? = requestedHeight) {
        requestedScreen = requested
        requestedHeight = height
        // Streaming to Moonlight comes first: the screen at the stream's size, whatever the game's choice
        // On a screen of its own, the phone's is left as it is
        val stream = streamSize.takeIf { extra == null }
        val mode = if (extra != null) ScreenMode.APP else if (stream != null) ScreenMode.SIDEWAYS else requested
        val size = if (stream == null && extra == null) height else null
        if (mode == appliedScreen && stream == appliedStream && size == appliedHeight) return
        val prefs = pending(context)
        val commands = mutableListOf<String>()
        var restoredSize: String? = null
        var restoredRotation: String? = null
        if (appliedScreen == ScreenMode.SIDEWAYS) savedSize?.let { saved ->
            commands += if (saved.isEmpty()) "wm size reset" else "wm size $saved"
            restoredSize = saved
            savedSize = null
            prefs.edit().remove(KEY_RESTORE_SIZE).apply()
        }
        if (appliedScreen != ScreenMode.APP) savedRotation?.let { saved ->
            commands += "cmd window set-ignore-orientation-request false; " +
                "settings put system accelerometer_rotation ${saved.takeIf { it == "0" || it == "1" } ?: "1"}"
            restoredRotation = saved
            savedRotation = null
            prefs.edit().remove(KEY_RESTORE_ROTATION).apply()
        }
        appliedScreen = mode
        appliedStream = stream
        appliedHeight = size
        var sideways: Pair<Int, Int>? = null
        if (mode != ScreenMode.APP) {
            // Turning held at upright either way: a sideways-shaped screen still mustn't turn (an
            // app asking for landscape the other way round would show upside down)
            val state = AdbShell.run("settings get system accelerometer_rotation; wm size").orEmpty()
            // What's kept to put back is what was there before gaming mode, not its own last change
            savedRotation = restoredRotation ?: state.lineSequence().firstOrNull()?.trim()?.takeIf { it == "0" || it == "1" } ?: "1"
            prefs.edit().putString(KEY_RESTORE_ROTATION, savedRotation).apply()
            commands += "cmd window set-ignore-orientation-request true; " +
                "settings put system accelerometer_rotation 0; settings put system user_rotation 0"
            if (mode == ScreenMode.SIDEWAYS) {
                val physical = Regex("Physical size: (\\d+)x(\\d+)").find(state)?.destructured?.toList()?.map { it.toInt() }
                if (physical != null) {
                    val (w, h) = minOf(physical[0], physical[1]) to maxOf(physical[0], physical[1])
                    savedSize = restoredSize ?: Regex("Override size: (\\d+x\\d+)").find(state)?.groupValues?.get(1) ?: ""
                    prefs.edit().putString(KEY_RESTORE_SIZE, savedSize).apply()
                    sideways = stream ?: scaled(sidewaysSize(w, h, cutoutTop(context)), size)
                    commands += "wm size ${sideways.first}x${sideways.second}"
                }
            }
        }
        if (commands.isEmpty()) return
        AdbShell.run(commands.joinToString("; "), 5_000)
        // Taps follow the screen's shape
        val shape = sideways
        val own = extra
        if (own != null) {
            screenW = own.width.toFloat(); screenH = own.height.toFloat()
            send("S ${own.width} ${own.height}")
            return
        }
        if (shape != null) {
            screenW = shape.first.toFloat(); screenH = shape.second.toFloat()
        } else if (restoredSize != null) {
            val size = restoredSize!!.ifEmpty { AdbShell.run("wm size").orEmpty() }
            Regex("(\\d+)x(\\d+)").find(size)?.destructured?.toList()?.map { it.toFloat() }?.let {
                screenW = minOf(it[0], it[1]); screenH = maxOf(it[0], it[1])
            }
        }
        if (shape != null || restoredSize != null) send("S ${screenW.toInt()} ${screenH.toInt()}")
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

    // ---- The trackpad: sticks or a mouse ----

    @Volatile private var trackpadReader: TrackpadReader? = null

    /** Which trackpad reading is current: one started before gaming mode stopped or switched ends itself. */
    @Volatile private var trackpadRun = 0

    private fun startTrackpad(current: GameProfile) {
        val run = ++trackpadRun
        trackpad?.destroy(); trackpad = null; trackpadReader = null
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
                val rows = device.yRange?.takeIf { it.isValid }?.let { it.min to it.max } ?: (0f to 600f)
                val reader = TrackpadReader(span.first, span.second, rows.first, rows.second)
                trackpadReader = reader
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
        // Flux Keyboard's own player (Xbox Cloud Gaming) takes them itself
        stickSink?.let { sink -> val now = sticks.copyOf(); sink(now); return }
        // The real controller's sticks, with root; else stick events from the keyboard
        val line = (if (pad) "A" else "J") + " ${sticks[0]} ${sticks[1]} ${sticks[2]} ${sticks[3]}"
        worker.execute { send(line) }
    }

    /** The right stick's keys (up, left, right, down), with Right stick on keys. */
    private val RIGHT_STICK_KEYS = FluxLayout.RIGHT_STICK
    private val rightKeysHeld = mutableSetOf<Int>()

    /** J B N M held: the right stick pushed their way (a diagonal no further than a straight push). */
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
    private class TrackpadReader(private val min: Float, private val max: Float, private val top: Float, private val bottom: Float) {
        private class Finger(var role: TrackpadRole, val left: Boolean, var originX: Float, var originY: Float, val downAt: Long) {
            var lastX = originX; var lastY = originY
            var moved = 0f
            /** Direction keys held: up, left, down, right. */
            val held = BooleanArray(4)
            /** Gliding after a handover until then (at the latest): its moves aren't the finger's. */
            var settleUntil = 0L
            /** Its origin is taken once it has settled (a finger new to the trackpad's report). */
            var reorigin = false
            /** Touching the screen, as the pointer. */
            var pointing = false
        }

        private val fingers = HashMap<Int, Finger>()
        /** Fingers the trackpad stopped reporting but that are still down, by half (left true). */
        private val held = HashMap<Boolean, Finger>()
        private val mid = (min + max) / 2
        private val radius = (max - min) / 8f
        private val slots = TouchSlots(::begin, ::move, ::up)
        private val lock = Any()
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var pendingUp: Runnable? = null
        private var pendingSlot = -1

        fun line(line: String) = synchronized(lock) { slots.line(line) }

        /**
         * A key went down: the keyboard is the trackpad, so a finger that landed just before is
         * that key being pressed, not a stick or pointer: it does nothing from now.
         */
        fun keyPressed() = synchronized(lock) {
            val now = SystemClock.uptimeMillis()
            for (f in fingers.values) {
                if (now - f.downAt > KEY_TOUCH_MS || f.role == TrackpadRole.NONE) continue
                end(f)
                f.role = TrackpadRole.NONE
            }
        }

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
                    .apply { settleUntil = now + GLIDE_MS; lastX = x.toFloat(); lastY = y.toFloat() }
                return
            }
            val left = x < mid
            var role = roleFor(current, left)
            if (role != TrackpadRole.NONE && (fingers.values + held.values).any { it.role == role }) role = TrackpadRole.NONE
            fingers[slot] = Finger(role, left, x.toFloat(), y.toFloat(), now)
        }

        /**
         * Still gliding after a handover: a report is the finger's own once it's well inside its
         * half and has slowed down (the glide eases in), or the glide's time is up.
         */
        private fun gliding(f: Finger, x: Int, y: Int): Boolean {
            val now = SystemClock.uptimeMillis()
            if (now >= f.settleUntil) return false
            val inside = if (f.left) x < mid - radius else x > mid + radius
            val step = abs(x - f.lastX) + abs(y - f.lastY)
            f.lastX = x.toFloat(); f.lastY = y.toFloat()
            if (!inside || step > GLIDE_STEP) return true
            f.settleUntil = 0L
            return false
        }

        /** A stick with nowhere to go (no real controller, no on-screen stick): its keys instead. */
        private fun byKeys(at: Int): Boolean = stickSink == null && !pad && profile?.stickZones?.containsKey(at / 2) != true

        private fun move(slot: Int, x: Int, y: Int) {
            val f = fingers[slot] ?: return
            if (gliding(f, x, y)) return
            if (f.reorigin) {
                f.reorigin = false
                f.originX = x.toFloat(); f.originY = y.toFloat()
                f.lastX = f.originX; f.lastY = f.originY
                return
            }
            val dx = x - f.lastX; val dy = y - f.lastY
            f.moved += abs(dx) + abs(dy)
            f.lastX = x.toFloat(); f.lastY = y.toFloat()
            val vx = push(x - f.originX, if (x < f.originX) f.originX - (if (f.left) min else mid) else (if (f.left) mid else max) - f.originX)
            val vy = push(y - f.originY, if (y < f.originY) f.originY - top else bottom - f.originY)
            when (f.role) {
                TrackpadRole.LEFT_STICK, TrackpadRole.RIGHT_STICK -> {
                    val at = if (f.role == TrackpadRole.LEFT_STICK) 0 else 2
                    if (byKeys(at)) directionKeys(f, vx, vy, if (at == 0) FluxLayout.LEFT_STICK else RIGHT_KEYS)
                    else {
                        sticks[at] = vx.coerceIn(-1f, 1f)
                        sticks[at + 1] = vy.coerceIn(-1f, 1f)
                        pushStick(at)
                    }
                }
                TrackpadRole.MOUSE -> worker.execute { send("M ${dx * 1.5f} ${dy * 1.5f}") }
                TrackpadRole.POINTER -> point(f, x, y)
                TrackpadRole.WASD_KEYS -> directionKeys(f, vx, vy, WASD)
                TrackpadRole.ARROW_KEYS -> directionKeys(f, vx, vy, ARROWS)
                TrackpadRole.NONE -> Unit
            }
        }

        /**
         * How far a stick is pushed (-1 to 1) by a finger [moved] from where it landed, with
         * [room] left to the trackpad's edge that way: a finger landing near an edge still
         * reaches full push in the room it has (never less than a third of the usual reach).
         */
        private fun push(moved: Float, room: Float): Float = moved / room.coerceIn(radius / 3, radius)

        /** The pointer: the finger's place on its half, as a place on the whole screen, touched. */
        private fun point(f: Finger, x: Int, y: Int) {
            val from = if (f.left) min else mid
            val px = ((x - from) / (mid - min)).coerceIn(0f, 1f) * screenW
            val py = ((y - top) / (bottom - top)).coerceIn(0f, 1f) * screenH
            val first = !f.pointing
            f.pointing = true
            worker.execute { send(if (first) "T d $POINTER_FINGER $px $py" else "T m $POINTER_FINGER $px $py") }
        }

        /** Each direction's key (up, left, down, right) down once pushed past a third of the way, up when back. */
        private fun directionKeys(f: Finger, vx: Float, vy: Float, keys: IntArray) {
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
                TrackpadRole.LEFT_STICK -> { directionKeys(f, 0f, 0f, FluxLayout.LEFT_STICK); releaseStick(0) }
                TrackpadRole.RIGHT_STICK -> { directionKeys(f, 0f, 0f, RIGHT_KEYS); releaseStick(2) }
                // A tap clicks
                TrackpadRole.MOUSE -> if (SystemClock.uptimeMillis() - f.downAt < 200 && f.moved < radius / 4) {
                    worker.execute { send("B 1 1"); send("B 0 1") }
                }
                TrackpadRole.POINTER -> if (f.pointing) { f.pointing = false; worker.execute { send("T u $POINTER_FINGER") } }
                TrackpadRole.WASD_KEYS -> directionKeys(f, 0f, 0f, WASD)
                TrackpadRole.ARROW_KEYS -> directionKeys(f, 0f, 0f, ARROWS)
                TrackpadRole.NONE -> Unit
            }
        }

        companion object {
            private val WASD = intArrayOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D)
            private val ARROWS = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            /** The right stick's keys in the same order: J, B, N, M. */
            private val RIGHT_KEYS = intArrayOf(KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M)
            private const val POINTER_FINGER = 3
            /** A glide's report this close to the last is the finger at rest. */
            private const val GLIDE_STEP = 12f
            /** A finger landing this soon before a key goes down is that key. */
            private const val KEY_TOUCH_MS = 150L
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
        val place = placeKeys(context)
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
    /** Opens the on-screen key placer for the game playing (from a notification). */
    fun placeKeys(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, Receiver::class.java).setAction(ACTION_PLACE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_PLACE) {
                profile?.let { GameTouchEditor.show(context.applicationContext, it) }
                return
            }
            if (intent.action == GameDetect.ACTION_NOT_GAME) {
                intent.getStringExtra(GameDetect.EXTRA_PACKAGE)?.let { GameDetect.dismiss(context, it) }
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
