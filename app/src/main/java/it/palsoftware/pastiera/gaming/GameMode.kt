package it.palsoftware.pastiera.gaming

import android.app.NotificationChannel
import it.palsoftware.pastiera.FluxLog
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
    /**
     * How far past the game's screen a touch can still go (x, y), in its pixels: a game drawn
     * smaller than the phone's screen has black bars around it, and a finger (and so a stick)
     * can reach into them.
     */
    @Volatile private var edges = 0f to 0f

    /** A text field of the game has the keyboard open: keys type, not play. */
    @Volatile var typing = false

    fun active(): Boolean = profile != null

    /** The placer saved new spots: play with them from now on. */
    /**
     * Where [profile]'s bottom screen is on a [w] x [h] screen (left, top, right, bottom, shares
     * of it): as placed, else where its emulator draws it upright by default (a 3DS's at the
     * top, its bottom screen narrower; a DS's in the middle), else the screen's lower half.
     */
    internal fun touchArea(profile: GameProfile?, w: Float, h: Float): List<Float> {
        profile?.touchArea?.let { return it }
        if (w <= 0f || h <= 0f) return listOf(0f, 0.5f, 1f, 1f)
        return when (profile?.packages?.firstNotNullOfOrNull { GameApps.twoScreens(it) }) {
            "3DS" -> {
                val s = minOf(w / 400f, h / 480f)
                val x0 = (w - 400f * s) / 2
                listOf((x0 + 40f * s) / w, 240f * s / h, (x0 + 360f * s) / w, 480f * s / h)
            }
            "DS" -> {
                val s = minOf(w / 256f, h / 384f)
                val x0 = (w - 256f * s) / 2; val y0 = (h - 384f * s) / 2
                listOf(x0 / w, (y0 + 192f * s) / h, (x0 + 256f * s) / w, (y0 + 384f * s) / h)
            }
            else -> listOf(0f, 0.5f, 1f, 1f)
        }
    }

    internal fun placed(context: Context, updated: GameProfile) {
        // Placed while streaming: kept apart from the phone's own placing
        val stored = GameProfiles.all(context).firstOrNull { it.id == updated.id } ?: updated
        // Only the placing is kept: the profile in use may carry a game's own screen over its app's
        val saved = (if (streamed()) stored.copy(streamStickZones = updated.stickZones, streamTaps = updated.taps)
            else stored.copy(stickZones = updated.stickZones, taps = updated.taps)).copy(stickTuning = updated.stickTuning, touchArea = updated.touchArea)
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
        // Another app in front: keys and modifiers let go there were never seen, so nothing is
        // kept as held
        if (previous != packageName) worker.execute { releaseControls() }
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

    // ---- Flux Keyboard's own player (Xbox Cloud Gaming in a web page) ----

    /** The player in front, taking the sticks (left x y, right x y) as they move. */
    @Volatile private var stickSink: ((FloatArray) -> Unit)? = null

    /**
     * The player in front: the trackpad's left half its left stick, given to [sink], and the
     * screen as [screenMode]. Keys reach the player itself.
     */
    fun attachPlayer(context: Context, screenMode: ScreenMode, sink: (FloatArray) -> Unit) {
        val app = context.applicationContext
        appContext = app
        FluxLayout.loadOverrides(app)
        worker.execute {
            stickSink = sink
            val player = GameProfile(
                id = "player", name = "player", packages = setOf(app.packageName),
                leftHalf = TrackpadRole.NONE, rightHalf = TrackpadRole.NONE, screen = screenMode
            )
            // Its keys sent through the input helper like any game's (started if it isn't)
            if (input == null && AdbShell.available()) startInput(app)
            releaseControls()
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
     * In a game (a profile on, nothing being typed, the placer shut): its keys are gaming mode's
     * ([onKeyEvent]), and the keyboard's own typing leaves every key alone.
     */
    fun passesKeys(): Boolean = profile != null && !typing && !GameTouchEditor.open

    // ---- The keys: what each one does in the game in front ----

    /**
     * How the game in front reads its controls, which decides what happens to every key.
     * Android hands keys sent through the input helper to apps as its own virtual keyboard's,
     * never the phone's keyboard, so a game that reads only the keyboard itself can't be sent
     * any: it is given the layout in its own controls instead.
     */
    internal enum class Route {
        /** It reads the keyboard itself, the layout written into its controls (Dolphin): every key goes to it untouched. */
        NATIVE,
        /** It reads keys from any keyboard (PPSSPP, the other emulators, Flux Keyboard's own player): each of the layout's keys is taken and its control's own key sent. */
        KEYS,
        /** It takes a controller: the layout's keys are taken and a controller's buttons and sticks sent. */
        PAD,
        /** A launcher's own screens: as [PAD], the left stick moving round them as the d-pad. */
        MENUS
    }

    internal fun route(): Route {
        // Flux Keyboard's own player (which isn't followed as the app in front)
        if (stickSink != null || profile?.id == "player") return Route.KEYS
        val front = frontPackage ?: return Route.PAD
        val emulator = GameApps.emulator(front)
        return when {
            front == appContext?.packageName -> Route.KEYS
            emulator == "Dolphin" -> Route.NATIVE
            emulator != null -> Route.KEYS
            inMenus -> Route.MENUS
            else -> Route.PAD
        }
    }

    /** The game in front takes the layout's keys (or reads them itself), rather than a controller's. */
    internal fun sendsKeys(): Boolean = route().let { it == Route.KEYS || it == Route.NATIVE }

    /** Modifier keys held (SYM, Shift, Alt): they change what the layout's keys are. Kept as they're pressed. */
    private val modifiersHeld = java.util.Collections.synchronizedSet(mutableSetOf<Int>())
    /** The layout's keys held (on [worker]). */
    private val layoutKeysHeld = mutableSetOf<Int>()
    /** The controls pressed now, and how they were sent: as a key (its code) or a controller's button (minus its code). */
    private val controlsDown = mutableMapOf<FluxLayout.Control, Int>()
    /** The sticks as the keys push them (left x y, right x y). */
    private val keySticks = FloatArray(4)

    /**
     * A key, before the app sees it; true when gaming mode takes it. In order: the phone's own
     * keys (Back asks again in a game, SYM with a volume key is the volume), keys placed on the
     * screen (they tap there), then the [route]: a game reading the keyboard itself gets every
     * key untouched; otherwise the modifiers and the layout's keys are taken and their controls
     * sent, and any other key goes to the game as it is.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!passesKeys()) return false
        // What gaming mode sends comes back through here (it has no scan code): on to the game
        if (event.scanCode == 0) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val first = event.repeatCount == 0
        // A key pressed on the keyboard is no finger on the trackpad (it is the trackpad)
        if (first) trackpadReader?.let { if (down) it.keyPressed(code) else it.keyReleased(code) }
        // The volume keys' own device (the phone's side buttons), for writing Dolphin's controls
        if (first && down && (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val device = event.device?.name
            appContext?.let { context -> worker.execute { FluxLayout.volumeSeenFrom(context, device) } }
        }
        if (code == KeyEvent.KEYCODE_BACK) return onGameScreen() && backGuard(event)
        if (symVolume(event)) return true
        // A key placed on the screen touches its spot while held, whatever else it is
        val tap = if (inMenus) null else profile?.taps?.get(code)
        val isModifier = code in FluxLayout.MODIFIER_KEYS
        if (tap == null) {
            // A game reading the keyboard itself has the layout in its own controls
            if (route() == Route.NATIVE) return false
            // Not the layout's with any modifier: the game gets it as it is
            if (!isModifier && !FluxLayout.isLayoutKey(code)) return false
        }
        // Nothing to send through (the input helper isn't running): the keys go to the game as
        // they are rather than nowhere, and the helper is started again
        if (input == null) {
            if (down && first) {
                FluxLog.w(TAG, "keys passed on as they are: the input helper isn't running")
                appContext?.let { context -> worker.execute { if (input == null && profile != null) restartInput(context) } }
            }
            return false
        }
        if (!first) return true
        if (tap != null) {
            val finger = 100 + code
            worker.execute { if (down) send("T d $finger ${tap.first * screenW} ${tap.second * screenH}") else send("T u $finger") }
            return true
        }
        if (isModifier) {
            // Kept straight away, so a key pressed right after is read with it
            if (down) modifiersHeld += code else modifiersHeld -= code
            worker.execute { refreshControls() }
            return true
        }
        worker.execute {
            if (down) {
                // Already held (its letting go wasn't seen): let go first, so this press is one
                if (code in layoutKeysHeld) { layoutKeysHeld -= code; refreshControls() }
                layoutKeysHeld += code
            } else layoutKeysHeld -= code
            refreshControls()
        }
        return true
    }

    /**
     * The control a key makes with [mods] held: the layout's (as changed in Keyboard controls),
     * A and B (X and Y) swapped for a game that wants them so. Dolphin has both written into
     * its own controls instead.
     */
    private fun controlOf(code: Int, mods: FluxLayout.Modifiers, route: Route): FluxLayout.Control? {
        val control = FluxLayout.controlFor(code, mods)
        return if (profile?.swapsFace == true && route != Route.NATIVE) FluxLayout.swapped(control) else control
    }

    /** What the modifiers held now make of the keys. */
    private fun modifiers(): FluxLayout.Modifiers {
        val held = synchronized(modifiersHeld) { modifiersHeld.toSet() }
        return FluxLayout.Modifiers(
            sym = KeyEvent.KEYCODE_SYM in held,
            rightShift = KeyEvent.KEYCODE_SHIFT_RIGHT in held,
            shift = KeyEvent.KEYCODE_SHIFT_LEFT in held || KeyEvent.KEYCODE_SHIFT_RIGHT in held,
            alt = KeyEvent.KEYCODE_ALT_LEFT in held || KeyEvent.KEYCODE_ALT_RIGHT in held
        )
    }

    /** The controls the keys and modifiers held make, sent where they've changed (on [worker]). */
    private fun refreshControls() {
        val route = route()
        val mods = modifiers()
        val wanted = layoutKeysHeld.mapNotNull { controlOf(it, mods, route) }.toMutableSet()
        // In a launcher's own screens the left stick moves round them as the d-pad does
        if (route == Route.MENUS) {
            val dpad = mapOf(FluxLayout.Control.LS_UP to FluxLayout.Control.UP, FluxLayout.Control.LS_DOWN to FluxLayout.Control.DOWN,
                FluxLayout.Control.LS_LEFT to FluxLayout.Control.LEFT, FluxLayout.Control.LS_RIGHT to FluxLayout.Control.RIGHT)
            dpad.forEach { (stick, pad) -> if (wanted.remove(stick)) wanted += pad }
        }
        // A game taking a controller: the stick directions are its sticks (or on-screen sticks
        // where placed). A game taking keys gets the stick keys, pressed like the buttons
        if (route == Route.PAD || route == Route.MENUS) {
            fun on(c: FluxLayout.Control) = if (c in wanted) 1f else 0f
            for (stick in 0..1) {
                val dirs = FluxLayout.STICK_CONTROLS.subList(stick * 4, stick * 4 + 4)
                wanted -= dirs.toSet()
                var x = on(dirs[3]) - on(dirs[2]); var y = on(dirs[1]) - on(dirs[0])
                if (x != 0f && y != 0f) { x *= 0.7071f; y *= 0.7071f }
                val at = stick * 2
                if (keySticks[at] == x && keySticks[at + 1] == y) continue
                keySticks[at] = x; keySticks[at + 1] = y
                sticks[at] = x; sticks[at + 1] = y
                if (x == 0f && y == 0f) releaseStick(at) else pushStick(at)
            }
        }
        for (c in controlsDown.keys - wanted) sendControl(c, false, route)
        for (c in wanted - controlsDown.keys) sendControl(c, true, route)
    }

    /** A control pressed or let go: as its key for a game taking keys, else as a controller's button. */
    private fun sendControl(control: FluxLayout.Control, down: Boolean, route: Route) {
        val line = if (down) {
            val keys = route == Route.KEYS
            val code = if (keys) FluxLayout.keyOf(control) else FluxLayout.GAMEPAD_KEYS[control] ?: return
            controlsDown[control] = if (keys) code else -code
            "K 1 $code ${if (keys) "k" else "g"}"
        } else {
            // Let go as it was pressed, even if the app in front has changed since
            val sent = controlsDown.remove(control) ?: return
            "K 0 ${kotlin.math.abs(sent)} ${if (sent > 0) "k" else "g"}"
        }
        if (down) FluxLog.w(TAG, "$control pressed: $line (${frontPackage}, ${route.name.lowercase()}, helper ${if (input != null) "running" else "not running"})")
        send(line)
    }

    /** Every control let go (the game ended, or another app came in front). */
    private fun releaseControls() {
        modifiersHeld.clear(); layoutKeysHeld.clear()
        symHeld = false; volumeTaken.clear()
        controlsDown.keys.toList().forEach { sendControl(it, false, Route.KEYS) }
        keySticks.fill(0f)
    }

    /** SYM is held: the volume keys change the volume, whatever the game makes of them. */
    @Volatile private var symHeld = false
    /** Volume keys taken for the volume while SYM was held (their letting go is too). */
    private val volumeTaken = mutableSetOf<Int>()

    /**
     * SYM with Volume Up or Down: the phone's volume, with its slider shown, as games take the
     * volume keys as triggers. SYM itself still reaches the game. True when the key is taken.
     */
    private fun symVolume(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_SYM) {
            if (event.action == KeyEvent.ACTION_DOWN) symHeld = true
            else if (event.action == KeyEvent.ACTION_UP) symHeld = false
            return false
        }
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_UP) return volumeTaken.remove(code)
        if (!symHeld && code !in volumeTaken) return false
        volumeTaken += code
        val audio = appContext?.getSystemService(android.media.AudioManager::class.java) ?: return true
        val direction = if (code == KeyEvent.KEYCODE_VOLUME_UP) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
        // Held down, it keeps going, as the volume keys do
        audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI)
        return true
    }

    /** The app screen in front: its app and its screen's class. */
    @Volatile private var window: Pair<String, String?>? = null

    /** An app's screen came to the front (from the accessibility helper). */
    fun onWindow(pkg: String, className: String?) {
        window = pkg to className
    }

    /**
     * The game itself is in front, where a stray Back would end it: not its app's file browser,
     * settings or a dialog, nor another app opened from it. An emulator or launcher plays on a
     * screen of its own; any other game is its whole app.
     */
    private fun onGameScreen(): Boolean {
        val (pkg, className) = window ?: return true
        if (profile?.packages?.contains(pkg) != true) return false
        if (GameApps.appFor(pkg) == null) return true
        // Flux Keyboard's own player (Xbox Cloud Gaming), or an emulator's or launcher's play screen
        val name = className ?: return true
        return PLAY_SCREENS.any { name.contains(it, ignoreCase = true) }
    }

    /** Names of emulators' and launchers' play screens (their other screens are menus). */
    private val PLAY_SCREENS = listOf("Emulation", "XServer", "PpssppActivity", "XboxPlay", "Wine", "Display", "Stream")

    /** When Back was last pressed in a game (the first press of two). */
    @Volatile private var backPressedAt = 0L
    /** The Back now held was taken (its letting go is too). */
    @Volatile private var backTaken = false
    @Volatile private var appContext: Context? = null

    /**
     * Back in a game: a first press only says to press again (a stray press doesn't leave the
     * game); a second within two seconds goes back. True when Back is taken.
     */
    private fun backGuard(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) return backTaken.also { backTaken = false }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return backTaken
        val now = android.os.SystemClock.uptimeMillis()
        if (now - backPressedAt < BACK_CONFIRM_MS) { backPressedAt = 0L; backTaken = false; return false }
        backPressedAt = now
        backTaken = true
        appContext?.let { context ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, it.palsoftware.pastiera.R.string.game_mode_back_again, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        return true
    }

    private const val BACK_CONFIRM_MS = 2_000L

    private fun send(line: String) {
        val writer = input ?: return
        runCatching { writer.write(line); writer.write("\n"); writer.flush() }
            .onFailure { FluxLog.w(TAG, "input helper gone: $it"); input = null }
    }

    /** The profile chosen for the app in front (a game, or the app's own); [profile] is what it plays with. */
    @Volatile private var activeId: String? = null

    private fun start(context: Context, chosen: GameProfile) {
        appContext = context.applicationContext
        FluxLayout.loadOverrides(context)
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
        // Another game: nothing held over from the one before
        releaseControls()
        // Each game's own screen choice (and size), also when switching game mid-way
        screen(context, next.screen, next.phoneHeight)
        startTrackpad(next)
        notify(context, next)
    }

    private fun stop(context: Context) {
        if (profile == null) return
        releaseControls()
        profile = null
        activeId = null
        trackpadRun++
        sticks.fill(0f); stickHeld.fill(false)
        trackpad?.destroy(); trackpad = null; trackpadReader = null
        runCatching { input?.close() }
        input = null
        inputProcess?.destroy(); inputProcess = null
        screen(context, ScreenMode.APP)
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }


    /** The input helper started again where it ended, aimed where the game is. */
    private fun restartInput(context: Context) {
        val w = screenW; val h = screenH
        runCatching { inputProcess?.destroy() }
        startInput(context)
        if (input == null) return
        extra?.let { send("Z ${it.id}") }
        screenW = w; screenH = h
        send("S ${w.toInt()} ${h.toInt()}")
        FluxLog.w(TAG, "input helper started again")
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
            // It ending by itself (the shell restarted): known straight away, so keys aren't lost
            Thread {
                runCatching { process.waitFor() }
                if (inputProcess === process) {
                    FluxLog.w(TAG, "input helper ended (exit ${runCatching { process.exitValue() }.getOrNull()})")
                    input = null
                }
            }.apply { isDaemon = true; start() }
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.getRealSize(size)
            // Upright while gaming: the short side across
            screenW = minOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
            screenH = maxOf(size.x, size.y).toFloat().takeIf { it > 0 } ?: 1080f
            send("S ${screenW.toInt()} ${screenH.toInt()}")
        }.onFailure { FluxLog.w(TAG, "input helper couldn't start: $it") }
    }

    // ---- The screen stays upright while a game runs ----

    private const val KEY_RESTORE_SIZE = "game_mode_restore_size"
    private const val KEY_RESTORE_DENSITY = "game_mode_restore_density"
    /** The density override before gaming mode's (empty: none); null when it set none. */
    private var savedDensity: String? = null

    /** `wm density` for the density saved before gaming mode changed it. */
    private fun densityCommand(saved: String) = if (saved.isEmpty()) "wm density reset" else "wm density $saved"
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
        val density = prefs.getString(KEY_RESTORE_DENSITY, null)
        if (size == null && rotation == null && density == null) return
        if (!AdbShell.available()) return
        // In one go, for a quick switch back
        AdbShell.run(listOfNotNull(
            size?.let { if (it.isEmpty()) "wm size reset" else "wm size $it" },
            density?.let { densityCommand(it) },
            rotation?.let {
                "cmd window set-ignore-orientation-request false; " +
                    "settings put system accelerometer_rotation ${it.takeIf { r -> r == "0" || r == "1" } ?: "1"}"
            }
        ).joinToString("; "), 5_000)
        prefs.edit().remove(KEY_RESTORE_SIZE).remove(KEY_RESTORE_ROTATION).remove(KEY_RESTORE_DENSITY).apply()
        savedSize = null
        savedRotation = null
        savedDensity = null
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

    /**
     * [action] on the main thread once the screen changes asked for so far are made, and a
     * moment more: a game opened as the screen changes size may restart into its menu (GameHub).
     */
    fun afterScreen(action: () -> Unit) = worker.execute {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, 700)
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
        var restoredDensity: String? = null
        if (appliedScreen == ScreenMode.SIDEWAYS) savedDensity?.let { saved ->
            commands += densityCommand(saved)
            restoredDensity = saved
            savedDensity = null
            prefs.edit().remove(KEY_RESTORE_DENSITY).apply()
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
            val state = AdbShell.run("settings get system accelerometer_rotation; wm size; wm density").orEmpty()
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
                    physicalSize = w to h
                    commands += "wm size ${sideways.first}x${sideways.second}"
                    // The density in step with the size, so the game's screen holds as much as the
                    // phone's own does (else a shorter screen at the same density cuts things off)
                    val physicalDensity = Regex("Physical density: (\\d+)").find(state)?.groupValues?.get(1)?.toIntOrNull()
                    if (physicalDensity != null) {
                        savedDensity = restoredDensity ?: Regex("Override density: (\\d+)").find(state)?.groupValues?.get(1) ?: ""
                        prefs.edit().putString(KEY_RESTORE_DENSITY, savedDensity).apply()
                        commands += "wm density ${sidewaysDensity(physicalDensity, w, sideways)}"
                    }
                }
            }
        }
        if (commands.isEmpty()) return
        AdbShell.run(commands.joinToString("; "), 5_000)
        // Taps follow the screen's shape
        val shape = sideways
        val own = extra
        edges = if (own == null && shape != null) physicalSize?.let { bars(shape, it) } ?: (0f to 0f) else 0f to 0f
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

    /** The phone's screen (short side, long side) when gaming mode last changed its shape. */
    private var physicalSize: Pair<Int, Int>? = null

    /**
     * The black bars around a screen [size] shown on a [physical] one (upright, as gaming mode
     * holds it): how far each side reaches past it, in [size]'s pixels.
     */
    internal fun bars(size: Pair<Int, Int>, physical: Pair<Int, Int>): Pair<Float, Float> {
        val scale = minOf(physical.first.toFloat() / size.first, physical.second.toFloat() / size.second)
        if (scale <= 0f) return 0f to 0f
        return ((physical.first / scale - size.first) / 2).coerceAtLeast(0f) to ((physical.second / scale - size.second) / 2).coerceAtLeast(0f)
    }

    /** The density for a sideways [size] on a screen [physicalShort] pixels across at [physical] dpi: its shorter side as many dp as the phone's. */
    internal fun sidewaysDensity(physical: Int, physicalShort: Int, size: Pair<Int, Int>): Int =
        (physical.toLong() * minOf(size.first, size.second) / physicalShort).toInt().coerceIn(physical / 3, physical)

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
    /** A letter key's column (of ten across) and row (of three), as the Titan's keyboard has them. */
    internal fun keyGridPlace(code: Int): Pair<Float, Int>? {
        if (code !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) return null
        val letter = 'a' + (code - KeyEvent.KEYCODE_A)
        KEY_ROWS.forEachIndexed { row, letters ->
            val column = letters.indexOf(letter)
            if (column >= 0) return (column + KEY_ROW_SHIFT[row]) to row
        }
        return null
    }

    private val KEY_ROWS = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    /** How far each row starts in from the left, in keys. */
    private val KEY_ROW_SHIFT = listOf(0f, 0f, 1f)

    /** The trackpad rows fingers have reached (top, bottom), kept from game to game. */
    @Volatile private var reachedRows: Pair<Float, Float>? = null

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
                FluxLog.w(TAG, "trackpad ${device.path}: x ${span.first}..${span.second}, y ${rows.first}..${rows.second}")
                val reader = TrackpadReader(span.first, span.second, rows.first, rows.second)
                trackpadReader = reader
                process.inputStream.bufferedReader().forEachLine { line -> reader.line(line) }
            }.onFailure { FluxLog.w(TAG, "trackpad couldn't be read: $it") }
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
        if (current != null && !inMenus && current.stickZones.containsKey(at / 2)) dragStick(at) else sendSticks()
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

    /** The last on-screen stick touch, for the log. */
    @Volatile private var lastDrag = ""

    /** An on-screen stick: a finger on its place, pushed as far as the stick goes. */
    private fun dragStick(at: Int) {
        val current = profile ?: return
        val zone = current.stickZones[at / 2] ?: return
        val (deadZone, range) = current.tuning(at / 2)
        val cx = zone.first * screenW
        val cy = zone.second * screenH
        // The circle's size is a share of the screen's shorter side (as the placer draws it), so
        // a sideways screen doesn't push it off the bottom
        val reach = zone.third * minOf(screenW, screenH) * range
        val finger = 1 + at / 2
        val first = !stickHeld[at / 2]
        stickHeld[at / 2] = true
        val (sx, sy) = stickShape(sticks[at], sticks[at + 1], deadZone)
        // Never off the phone's screen: a touch there is dropped, and the stick wouldn't move
        // that way. Into the black bars around a smaller game screen is fine, as a finger can
        val (barX, barY) = edges
        val x = (cx + sx * reach).coerceIn(1f - barX, screenW + barX - 2f)
        val y = (cy + sy * reach).coerceIn(1f - barY, screenH + barY - 2f)
        lastDrag = "stick ${at / 2} from ${cx.toInt()},${cy.toInt()} reach ${reach.toInt()} to ${x.toInt()},${y.toInt()} (bars ${barX.toInt()},${barY.toInt()})"
        worker.execute {
            if (first) send("T d $finger $cx $cy")
            send("T m $finger $x $y")
        }
    }

    /**
     * A stick's push as the game should see it: a circle, not a square (a diagonal reaches the
     * edge, no further), nothing inside the dead zone, and the rest spread over the whole range
     * so the edge is still a full push.
     */
    internal fun stickShape(x: Float, y: Float, deadZone: Float): Pair<Float, Float> {
        val length = kotlin.math.sqrt(x * x + y * y)
        if (length <= deadZone || length == 0f) return 0f to 0f
        val scaled = ((minOf(length, 1f) - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        return x / length * scaled to y / length * scaled
    }

    private fun sendSticks() {
        // Flux Keyboard's own player (Xbox Cloud Gaming) takes them itself
        stickSink?.let { sink -> val now = sticks.copyOf(); sink(now); return }
        // The real controller's sticks, with root; else stick events from the keyboard
        val line = "J ${sticks[0]} ${sticks[1]} ${sticks[2]} ${sticks[3]}"
        worker.execute { send(line) }
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
            /** Where the trackpad last put it. */
            var rawX = originX; var rawY = originY
            /** A stick kept where it was while a key is held (the trackpad isn't following its thumb). */
            var frozen = false
            fun isStick() = role == TrackpadRole.LEFT_STICK || role == TrackpadRole.RIGHT_STICK
            /** The furthest push each way (up, down), for the log. */
            var furthestUp = 0f; var furthestDown = 0f
        }

        private val fingers = HashMap<Int, Finger>()
        /** Fingers the trackpad stopped reporting but that are still down, by half (left true). */
        private val held = HashMap<Boolean, Finger>()
        private val mid = (min + max) / 2
        private val radius = (max - min) / 8f
        private var reachedTop = reachedRows?.first ?: ((top + bottom) / 2)
        private var reachedBottom = reachedRows?.second ?: ((top + bottom) / 2)
        private val slots = TouchSlots(::begin, ::move, ::up)
        private val lock = Any()
        private val handler = android.os.Handler(android.os.Looper.getMainLooper())
        private var pendingUp: Runnable? = null
        private var pendingSlot = -1

        fun line(line: String) = synchronized(lock) { slots.line(line) }

        /** Keys on the keyboard (the trackpad) held now. */
        private val heldKeys = HashSet<Int>()

        /**
         * Sticks whose finger the trackpad stopped reporting, by half (left true), kept where
         * they were pushed: the trackpad follows only one finger, so pressing a key drops the
         * stick's thumb (or stops reporting it) until the key is let go. Picked up again by
         * the next touch on its half, from where it first landed.
         */
        private val parked = HashMap<Boolean, Finger>()
        private var parkEnd: Runnable? = null

        /** A key on the keyboard itself, where a finger pressing it touches the trackpad. */
        private fun onKeyboard(code: Int) = keyGridPlace(code) != null ||
            code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DEL

        /**
         * A key went down: the keyboard is the trackpad, so a finger that landed just before is
         * that key being pressed, not a stick or pointer: it does nothing from now. The sticks
         * stay where they're pushed until the key is let go.
         */
        fun keyPressed(code: Int) = synchronized(lock) {
            if (!onKeyboard(code)) return@synchronized
            val now = SystemClock.uptimeMillis()
            for (f in fingers.values) {
                if (now - f.downAt > KEY_TOUCH_MS || f.role == TrackpadRole.NONE) continue
                end(f)
                f.role = TrackpadRole.NONE
            }
            heldKeys += code
            parkEnd?.let { handler.removeCallbacks(it); parkEnd = null }
            for (f in fingers.values) if (f.isStick()) f.frozen = true
            keyStats = KeyStats(code, now, fingers.entries.joinToString { "${it.key}:${it.value.role}@${it.value.rawX.toInt()},${it.value.rawY.toInt()}" }, held.size + parked.size)
        }

        fun keyReleased(code: Int) = synchronized(lock) {
            if (!heldKeys.remove(code)) return@synchronized
            keyStats?.takeIf { it.code == code }?.let { k ->
                FluxLog.w(TAG, "key $code held ${SystemClock.uptimeMillis() - k.at} ms: trackpad sent ${k.moves} moves " +
                    "(${k.firstMove} to ${k.lastMove}), ${k.downs} touches, ${k.ups} lifts; fingers at the press: ${k.fingers}, kept ${k.held}")
                keyStats = null
            }
            if (heldKeys.isNotEmpty()) return@synchronized
            for (f in fingers.values) f.frozen = false
            // The thumb has a moment to be found again before a kept stick lets go
            if (parked.isNotEmpty()) endParked(RETURN_MS)
        }

        /** What the trackpad did while a key was held, for the log. */
        private class KeyStats(val code: Int, val at: Long, val fingers: String, val held: Int) {
            var moves = 0; var downs = 0; var ups = 0
            var firstMove = ""; var lastMove = ""
        }
        private var keyStats: KeyStats? = null

        /** The kept sticks let go after [delay], unless a key is pressed or their thumb comes back first. */
        private fun endParked(delay: Long) {
            parkEnd?.let { handler.removeCallbacks(it) }
            val ending = Runnable {
                synchronized(lock) {
                    parkEnd = null
                    if (heldKeys.isNotEmpty()) return@synchronized
                    parked.values.forEach { end(it) }
                    parked.clear()
                }
            }
            parkEnd = ending
            handler.postDelayed(ending, delay)
        }

        private fun roleFor(current: GameProfile, left: Boolean): TrackpadRole = when {
            left -> current.leftHalf
            else -> current.rightHalf
        }

        private fun begin(slot: Int, x: Int, y: Int) {
            keyStats?.let { it.downs++ }
            val current = profile ?: return
            val now = SystemClock.uptimeMillis()
            // A key held: this touch is its finger (the trackpad left the thumb for it); the
            // finger before is kept where it was
            if (heldKeys.isNotEmpty()) {
                pendingUp?.let { handler.removeCallbacks(it); it.run() }
                fingers[slot] = Finger(TrackpadRole.NONE, x < mid, x.toFloat(), y.toFloat(), now)
                return
            }
            // A kept stick's thumb found again: the same stick, from where it first landed
            parked.remove(x < mid)?.let { back ->
                pendingUp?.let { handler.removeCallbacks(it); it.run() }
                if (parked.isEmpty()) parkEnd?.let { handler.removeCallbacks(it); parkEnd = null }
                fingers[slot] = back.apply { frozen = false; settleUntil = 0L; lastX = x.toFloat(); lastY = y.toFloat() }
                move(slot, x, y)
                return
            }
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
            if (role != TrackpadRole.NONE && (fingers.values + held.values + parked.values).any { it.role == role }) role = TrackpadRole.NONE
            reach(y)
            fingers[slot] = Finger(role, left, x.toFloat(), y.toFloat(), now).apply { rawX = x.toFloat(); rawY = y.toFloat() }
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

        /** A stick for a game taking keys, with no on-screen stick placed: its direction keys. */
        private fun byKeys(at: Int): Boolean =
            stickSink == null && profile?.stickZones?.containsKey(at / 2) != true && route() == Route.KEYS

        private fun move(slot: Int, reportedX: Int, reportedY: Int) {
            keyStats?.let { k -> k.moves++; "$slot@$reportedX,$reportedY".let { if (k.firstMove.isEmpty()) k.firstMove = it; k.lastMove = it } }
            val f = fingers[slot] ?: return
            f.rawX = reportedX.toFloat(); f.rawY = reportedY.toFloat()
            // While a key is held the trackpad isn't following the thumb: the stick stays put
            if (f.frozen) return
            val x = reportedX; val y = reportedY
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
            reach(y)
            val vy = push(y - f.originY, if (y < f.originY) f.originY - reachedTop else reachedBottom - f.originY)
            when (f.role) {
                TrackpadRole.LEFT_STICK, TrackpadRole.RIGHT_STICK -> {
                    val at = if (f.role == TrackpadRole.LEFT_STICK) 0 else 2
                    if (byKeys(at)) directionKeys(f, vx, vy, if (at == 0) LEFT_KEYS else RIGHT_KEYS)
                    else {
                        sticks[at] = vx.coerceIn(-1f, 1f)
                        sticks[at + 1] = vy.coerceIn(-1f, 1f)
                        f.furthestUp = maxOf(f.furthestUp, -vy); f.furthestDown = maxOf(f.furthestDown, vy)
                        pushStick(at)
                    }
                }
                TrackpadRole.MOUSE -> worker.execute { send("M ${dx * 1.5f} ${dy * 1.5f}") }
                TrackpadRole.POINTER -> point(f, x, y)
                TrackpadRole.BOTTOM_SCREEN -> stylus(f, x, y)
                TrackpadRole.WASD_KEYS -> directionKeys(f, vx, vy, WASD)
                TrackpadRole.ARROW_KEYS -> directionKeys(f, vx, vy, ARROWS)
                TrackpadRole.NONE -> Unit
            }
        }

        /**
         * The rows a finger has actually reached: the trackpad reports more than a finger can
         * get to (the bottom row runs past where the keys stop), so a stick pushed down by the
         * reported edge never got there. Starts at the reported middle and grows with each report.
         */
        private fun reach(y: Int) {
            if (y < reachedTop) reachedTop = y.toFloat()
            if (y > reachedBottom) reachedBottom = y.toFloat()
            reachedRows = reachedTop to reachedBottom
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

        /**
         * The stylus: the finger's place on the trackpad (its half, or all of it when both halves
         * are the bottom screen) as a place on the DS or 3DS's bottom screen, touched.
         */
        private fun stylus(f: Finger, x: Int, y: Int) {
            val whole = profile?.let { it.leftHalf == TrackpadRole.BOTTOM_SCREEN && it.rightHalf == TrackpadRole.BOTTOM_SCREEN } == true
            val from = if (whole || f.left) min else mid
            val to = if (whole || !f.left) max else mid
            val area = touchArea(profile, screenW, screenH)
            val px = (area[0] + ((x - from) / (to - from)).coerceIn(0f, 1f) * (area[2] - area[0])) * screenW
            val py = (area[1] + ((y - top) / (bottom - top)).coerceIn(0f, 1f) * (area[3] - area[1])) * screenH
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
            keyStats?.let { it.ups++ }
            pendingUp?.let { handler.removeCallbacks(it); it.run() }
            pendingSlot = slot
            val lifted = Runnable {
                synchronized(lock) {
                    pendingUp = null
                    fingers.remove(slot)?.let { f ->
                        // A stick's thumb dropped: kept a moment, as a key's finger may be why
                        // (and kept while a key is held)
                        if (f.isStick() && f.role != TrackpadRole.NONE) {
                            parked[f.left]?.let { end(it) }
                            parked[f.left] = f.apply { frozen = false }
                            if (heldKeys.isEmpty()) endParked(PARK_MS)
                        } else end(f)
                    }
                    // Every finger off: the ones the trackpad had stopped reporting too
                    // (a stick among them kept a moment for its thumb to be found again)
                    if (fingers.isEmpty() && heldKeys.isEmpty()) {
                        for (f in held.values) {
                            if (f.isStick() && parked[f.left] == null) parked[f.left] = f.apply { frozen = false } else end(f)
                        }
                        held.clear()
                        if (parked.isNotEmpty()) endParked(RETURN_MS)
                    }
                }
            }
            pendingUp = lifted
            handler.postDelayed(lifted, HANDOVER_MS)
        }

        private fun end(f: Finger) {
            if (f.role == TrackpadRole.LEFT_STICK || f.role == TrackpadRole.RIGHT_STICK) FluxLog.w(TAG,
                "trackpad stick: landed at y ${f.originY}, furthest up ${f.furthestUp}, down ${f.furthestDown}, " +
                    "rows reached $reachedTop..$reachedBottom, on screen ${screenW}x$screenH; last touch: $lastDrag; " +
                    "profile ${profile?.id}, zones ${profile?.stickZones}")
            when (f.role) {
                TrackpadRole.LEFT_STICK -> { directionKeys(f, 0f, 0f, LEFT_KEYS); releaseStick(0) }
                TrackpadRole.RIGHT_STICK -> { directionKeys(f, 0f, 0f, RIGHT_KEYS); releaseStick(2) }
                // A tap clicks
                TrackpadRole.MOUSE -> if (SystemClock.uptimeMillis() - f.downAt < 200 && f.moved < radius / 4) {
                    worker.execute { send("B 1 1"); send("B 0 1") }
                }
                TrackpadRole.POINTER, TrackpadRole.BOTTOM_SCREEN -> if (f.pointing) { f.pointing = false; worker.execute { send("T u $POINTER_FINGER") } }
                TrackpadRole.WASD_KEYS -> directionKeys(f, 0f, 0f, WASD)
                TrackpadRole.ARROW_KEYS -> directionKeys(f, 0f, 0f, ARROWS)
                TrackpadRole.NONE -> Unit
            }
        }

        companion object {
            private val WASD = intArrayOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D)
            private val ARROWS = intArrayOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            /** The right stick's keys in the same order: J, B, N, M. */
            private val RIGHT_KEYS = intArrayOf(
                FluxLayout.keyOf(FluxLayout.Control.RS_UP), FluxLayout.keyOf(FluxLayout.Control.RS_LEFT),
                FluxLayout.keyOf(FluxLayout.Control.RS_DOWN), FluxLayout.keyOf(FluxLayout.Control.RS_RIGHT)
            )
            /** The left stick's keys in the same order. */
            private val LEFT_KEYS = intArrayOf(
                FluxLayout.keyOf(FluxLayout.Control.LS_UP), FluxLayout.keyOf(FluxLayout.Control.LS_LEFT),
                FluxLayout.keyOf(FluxLayout.Control.LS_DOWN), FluxLayout.keyOf(FluxLayout.Control.LS_RIGHT)
            )
            private const val POINTER_FINGER = 3
            /** A glide's report this close to the last is the finger at rest. */
            private const val GLIDE_STEP = 12f
            /** How long a stick whose thumb dropped is kept, in case a key's finger is why. */
            private const val PARK_MS = 150L
            /** How long a stick kept while a key was held waits for its thumb once the key is let go. */
            private const val RETURN_MS = 400L
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
