package it.palsoftware.pastiera.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.SettingsManager
import java.io.File
import java.util.concurrent.Executors

/**
 * The keyboard's backlight, through Shizuku (the ADB shell, no root needed):
 * - Brightness and how long it stays lit after a key press.
 * - Follows the screen: on while the screen is on, off when it goes off.
 * - Follows the screen's brightness, fades included: brighter screen, brighter keys.
 * - Flashes for notifications, the keyboard as a notification light.
 *
 * On the Titan 2 the light isn't an LED under /sys/class/leds: it's behind Unihertz's vendor
 * service, the same one its Settings slider uses:
 *   service call agui_functional_service 2 s16 <key> s16 <value>   writes a key
 *   service call agui_functional_service 1 s16 <key>               reads one back
 * What the ADB shell can do with it (as PhysiBoard found on the phone):
 * - keyboard_brightness_timeout, in milliseconds; following the screen it's set to a day, so the
 *   light stays lit while the screen is on.
 * - am broadcast -a agui.action.CLOSE_KEYBOARD_LIGHT turns it off at once (screen off).
 * - Only a key press turns it on again: the screen coming on sends keycode 0, which types nothing.
 * - keyboard_led_brightness (0 to 100): written, then read back; following the screen's
 *   brightness only goes on when the phone keeps what was written.
 * Following the screen's brightness, the shell itself reads the display's real brightness from
 * the display service (adaptive brightness and fades included, which the brightness setting
 * doesn't show) while the screen is on, and writes the keyboard's level only when it changes.
 * Where that can't be read, the panel's own node, and failing that the brightness setting.
 */
object KeyboardBacklight {
    const val KEY_FOLLOW_SCREEN = "root_backlight_follow_screen"
    const val KEY_FOLLOW_BRIGHTNESS = "root_backlight_follow_brightness"
    const val KEY_NOTIFICATION_FLASH = "root_backlight_notification_flash"
    private const val KEY_SAVED_TIMEOUT = "root_backlight_saved_timeout"

    private const val SERVICE = "agui_functional_service"
    private const val BRIGHTNESS = "keyboard_led_brightness"
    private const val TIMEOUT = "keyboard_brightness_timeout"
    /** While following the screen the light stays lit for a day; the screen going off turns it off. */
    private const val FOLLOW_TIMEOUT_MS = 24 * 60 * 60 * 1000
    private const val CLOSE_LIGHT = "am broadcast -a agui.action.CLOSE_KEYBOARD_LIGHT"
    /**
     * The light only comes on for a key press (neither a brightness nor the master switch turns
     * it on), so the screen coming on sends one that types nothing: keycode 0, unknown.
     */
    private const val WAKE_LIGHT = "input keyevent 0"
    /** How often the screen is read while adaptive brightness changes it without a setting. */
    private const val ADAPTIVE_POLL_MS = 1000L
    /** How often the shown brightness is read between fades (30 times a second). */
    private const val SHOWN_POLL_MS = 33L
    private const val SCREEN_NODE = "/sys/class/leds/lcd-backlight"

    private sealed class Route {
        object Vendor : Route()
        object None : Route()
    }

    @Volatile private var route: Route? = null
    @Volatile private var lastLevel = -1
    private val worker = Executors.newSingleThreadExecutor()
    // The screen is read off the main thread (once a frame during a fade)
    private val pollHandler by lazy {
        Handler(android.os.HandlerThread("FluxKeyboardLight").apply { start() }.looper)
    }
    private var receiver: BroadcastReceiver? = null
    private var waitingForShizuku: rikka.shizuku.Shizuku.OnBinderReceivedListener? = null
    private var waitingContext: Context? = null
    /** The built-in shell came up after the keyboard: start then, as for Shizuku. */
    private val shellStarted: () -> Unit = {
        waitingContext?.let { app -> android.os.Handler(android.os.Looper.getMainLooper()).post { start(app) } }
    }
    private var poller: Runnable? = null

    private fun prefs(context: Context) = SettingsManager.getPreferences(context)
    fun followScreen(context: Context) = prefs(context).getBoolean(KEY_FOLLOW_SCREEN, false)
    fun followBrightness(context: Context) = prefs(context).getBoolean(KEY_FOLLOW_BRIGHTNESS, false)
    fun notificationFlash(context: Context) = prefs(context).getBoolean(KEY_NOTIFICATION_FLASH, false)

    private fun vendorGet(key: String): String? =
        AdbShell.run("service call $SERVICE 1 s16 $key")
            // Parcel dump: the string sits between single quotes, dots for padding
            ?.lines()?.filter { "'" in it }?.joinToString("") { it.substringAfter("'").substringBefore("'") }
            ?.replace(".", "")?.replace(" ", "")?.takeIf { it.isNotEmpty() }

    /** Writes a key through the open shell session (no new process each time). */
    private fun vendorSet(key: String, value: Int) {
        if (!AdbShell.send("service call $SERVICE 2 s16 $key s16 $value")) {
            AdbShell.run("service call $SERVICE 2 s16 $key s16 $value")
        }
    }

    /**
     * How the light is reached on this phone, found once: the vendor service being there is
     * enough (its brightness key is often unset until the Settings slider is moved).
     */
    private fun route(): Route {
        route?.let { return it }
        if (!AdbShell.available()) {
            problem = Problem.NoShizuku
            return Route.None
        }
        // Asked by name first (one line back), then in the whole list, then by reading a key;
        // a shell that doesn't answer isn't an answer, so it's asked again next time
        val check = AdbShell.run("service check $SERVICE")
        var found = check != null && "not found" !in check && "found" in check
        var list: String? = null
        if (!found) {
            list = AdbShell.run("service list")
            found = list?.contains(SERVICE) == true
        }
        if (!found) found = vendorGet(TIMEOUT) != null
        problem = when {
            found -> null
            check == null && list == null -> Problem.NoAnswer
            else -> Problem.NoService
        }
        if (found) route = Route.Vendor else if (problem == Problem.NoService) route = Route.None
        return if (found) Route.Vendor else Route.None
    }

    /** Why the light couldn't be reached, for the Shizuku page. */
    enum class Problem { NoShizuku, NoAnswer, NoService }

    @Volatile var problem: Problem? = null
        private set

    /** Looks for the light again (after Shizuku restarts, say). */
    fun recheck(context: Context): Boolean {
        route = null
        brightnessKept = null
        val found = supported()
        if (found) start(context)
        return found
    }

    /** Whether the phone keeps a brightness written to it (checked once, by reading it back). */
    @Volatile private var brightnessKept: Boolean? = null

    private fun checkBrightnessKept(level: Int): Boolean {
        brightnessKept?.let { return it }
        AdbShell.run("service call $SERVICE 2 s16 $BRIGHTNESS s16 $level")
        // Only a different number read back means it isn't kept; an unreadable answer isn't one
        val kept = vendorGet(BRIGHTNESS)?.toIntOrNull()?.let { it == level } ?: true
        brightnessKept = kept
        return kept
    }

    /** Whether the light's brightness can be set (null until it's been tried). */
    fun brightnessSupported(): Boolean? = brightnessKept

    /** Whether this phone has a keyboard light Flux Keyboard can reach. */
    fun supported(): Boolean = route() != Route.None

    /** The brightness now, 0 to 100 (null when it can't be read). */
    fun brightness(): Int? = when (route()) {
        Route.Vendor -> vendorGet(BRIGHTNESS)?.toIntOrNull()
        Route.None -> null
    }

    /** Seconds the light stays lit after a key press (Titan 2 only; null elsewhere). */
    fun timeoutSeconds(): Int? =
        if (route() == Route.Vendor) vendorGet(TIMEOUT)?.toIntOrNull()?.let { it / 1000 } else null

    /** Sets the brightness, 0 to 100. */
    // The level waiting to be set: while one is being set, newer ones replace it (a fade
    // at the screen's refresh rate asks faster than the shell can answer)
    private val pending = java.util.concurrent.atomic.AtomicInteger(-1)

    fun set(level: Int) {
        if (pending.getAndSet(level.coerceIn(0, 100)) != -1) return
        worker.execute {
            val latest = pending.getAndSet(-1)
            if (latest >= 0) apply(latest)
        }
    }

    private fun apply(level: Int) {
        if (level == lastLevel) return
        when (route()) {
            Route.Vendor -> if (brightnessKept == null) checkBrightnessKept(level) else vendorSet(BRIGHTNESS, level)
            Route.None -> return
        }
        lastLevel = level
    }

    /** The brightness chosen on the Root page (the level used when not following the screen's). */
    fun setChosen(context: Context, level: Int) {
        prefs(context).edit().putInt("root_backlight_level", level.coerceIn(0, 100)).apply()
        lastLevel = -1
        set(level)
    }

    fun chosen(context: Context): Int = prefs(context).getInt("root_backlight_level", -1)

    fun setTimeoutSeconds(seconds: Int) {
        worker.execute { if (route() == Route.Vendor) vendorSet(TIMEOUT, seconds.coerceIn(1, 3600) * 1000) }
    }

    /** Whether the panel's own level can be read here (SELinux often says no without root). */
    @Volatile private var panelReadable: Boolean? = null

    /**
     * The screen's brightness as a share of its maximum (0 to 1): the panel's own level when
     * it can be read (it ramps with fades), otherwise the brightness setting, read as the
     * system stores it (the float setting where there is one, else 0 to 255).
     */
    private fun screenShare(context: Context): Float {
        shownShare(context)?.let { return it }
        if (panelReadable != false) {
            val panel = runCatching {
                val now = File("$SCREEN_NODE/brightness").readText().trim().toInt()
                val max = File("$SCREEN_NODE/max_brightness").readText().trim().toInt()
                now.toFloat() / max.coerceAtLeast(1)
            }.getOrNull()
            panelReadable = panel != null
            if (panel != null) return panel.coerceIn(0f, 1f)
        }
        val resolver = context.contentResolver
        runCatching { Settings.System.getFloat(resolver, "screen_brightness_float") }.getOrNull()
            ?.takeIf { it in 0f..1f }?.let { return it }
        val setting = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
        return (setting / 255f).coerceIn(0f, 1f)
    }

    @Volatile private var shownReadable: Boolean? = null

    /**
     * The brightness the screen shows right now, 0 to 1 (adaptive brightness and fades
     * included), from Android's own display info (hidden API, Android 12 and later). Null where
     * it can't be read.
     */
    private fun shownShare(context: Context): Float? {
        if (shownReadable == false || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
        val share = runCatching {
            if (shownReadable == null) org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/Display;", "Landroid/hardware/display/BrightnessInfo;")
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                .getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val info = android.view.Display::class.java.getMethod("getBrightnessInfo").invoke(display)!!
            val now = info.javaClass.getField("brightness").getFloat(info)
            val max = runCatching { info.javaClass.getField("brightnessMaximum").getFloat(info) }.getOrDefault(1f)
            (now / max.takeIf { it > 0f }!!).coerceIn(0f, 1f)
        }.getOrNull()
        shownReadable = share != null
        return share
    }

    private fun levelForScreen(context: Context): Int {
        val share = screenShare(context)
        return if (share <= 0f) 0 else (share * 100).toInt().coerceIn(1, 100)
    }

    private fun onLevel(context: Context): Int =
        if (followBrightness(context)) levelForScreen(context) else chosen(context).takeIf { it >= 0 } ?: 100

    /** Starts following the screen, as set (called as the keyboard starts; idempotent). */
    fun start(context: Context) {
        stop(context)
        val app = context.applicationContext
        val following = followScreen(app) || followBrightness(app)
        if (!AdbShell.available()) {
            // Shizuku comes up after the keyboard (at boot, or started later): start then
            if (following && waitingForShizuku == null) {
                waitingContext = app
                val listener = rikka.shizuku.Shizuku.OnBinderReceivedListener {
                    if (AdbShell.available()) start(app)
                }
                waitingForShizuku = listener
                runCatching { rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(listener) }
                it.palsoftware.pastiera.adb.shell.BuiltInShell.addStartListener(shellStarted)
            }
            return
        }
        waitingForShizuku?.let { runCatching { rikka.shizuku.Shizuku.removeBinderReceivedListener(it) } }
        it.palsoftware.pastiera.adb.shell.BuiltInShell.removeStartListener(shellStarted)
        waitingForShizuku = null
        worker.execute { keepLit(app, following) }
        if (!following) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        stopPolling()
                        // Never timing out, the light needs telling the screen is off
                        if (followScreen(ctx) || followBrightness(ctx)) worker.execute { closeLight() }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (followScreen(ctx) || followBrightness(ctx)) {
                            worker.execute { if (route() == Route.Vendor) AdbShell.send(WAKE_LIGHT) }
                            set(onLevel(ctx))
                        }
                        startPolling(ctx)
                    }
                }
            }
        }.also {
            ContextCompat.registerReceiver(
                app, it, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        lastLevel = -1
        set(onLevel(app))
        startPolling(app)
    }

    /**
     * Following the screen, the light stays lit instead of timing out; the phone's own timeout
     * is kept and put back when neither option is on.
     */
    private fun keepLit(context: Context, following: Boolean) {
        if (route() != Route.Vendor) return
        val prefs = prefs(context)
        val saved = prefs.getInt(KEY_SAVED_TIMEOUT, -1)
        if (following) {
            if (saved < 0) {
                vendorGet(TIMEOUT)?.toIntOrNull()?.let { prefs.edit().putInt(KEY_SAVED_TIMEOUT, it).apply() }
            }
            vendorSet(TIMEOUT, FOLLOW_TIMEOUT_MS)
        } else if (saved >= 0) {
            vendorSet(TIMEOUT, saved)
            prefs.edit().remove(KEY_SAVED_TIMEOUT).apply()
        }
    }

    /** Turns the light off now (the screen went off): the vendor's own broadcast, then level 0. */
    private fun closeLight() {
        AdbShell.send(CLOSE_LIGHT)
        lastLevel = -1
    }

    private var observer: android.database.ContentObserver? = null
    private var displayListener: android.hardware.display.DisplayManager.DisplayListener? = null

    /**
     * Following the brightness: Android says when the brightness setting changes (the slider,
     * a quick setting) and when the display changes; adaptive brightness moves the screen
     * without either, so then it's also read once a second. Nothing runs while the screen is off.
     */
    /**
     * The shell loop: the display's real brightness (what the panel shows, adaptive brightness and
     * fades included, not the setting) from the display service, a few times a second; or, where
     * that can't be read, the panel's own node once a frame. The keyboard is set to the same share
     * of its range when it changes. It ends at once (exit 3) when neither can be read, and the app
     * follows the setting.
     */
    private fun followLoop(frameMs: Double): String = buildString {
        val frame = "%.4f".format(java.util.Locale.ROOT, frameMs / 1000)
        // The display service's brightness, 0 to 100 (empty when it can't be read)
        append("shown() { f=\$(dumpsys display 2>/dev/null | grep -m1 -o 'mScreenBrightness=[0-9.]*'); f=\${f#*=}; ")
        append("[ -z \"\$f\" ] && return; i=\${f%%.*}; d=0; case \"\$f\" in *.*) d=\${f#*.}00; d=\${d%\"\${d#??}\"};; esac; ")
        append("v=\$(( i * 100 + 10#\$d )); [ \$v -gt 100 ] && v=100; [ \$v -lt 1 ] && [ \"\$f\" != 0 ] && [ \"\$f\" != 0.0 ] && v=1; echo \$v; }; ")
        // The panel's node, where the display service doesn't say
        append("node=; for n in /sys/class/backlight/*/brightness /sys/class/leds/lcd-backlight/brightness; do ")
        append("read b 2>/dev/null < \"\$n\" && [ -n \"\$b\" ] && { node=\$n; break; }; done; ")
        append("max=0; [ -n \"\$node\" ] && read max 2>/dev/null < \"\${node%/brightness}/max_brightness\"; ")
        append("[ \"\${max:-0}\" -gt 0 ] 2>/dev/null || node=; ")
        append("use=shown; [ -z \"\$(shown)\" ] && use=node; [ \$use = node ] && [ -z \"\$node\" ] && exit 3; ")
        append("p=-1; while :; do l=; ")
        append("if [ \$use = shown ]; then l=\$(shown); s=0.25; ")
        append("else read b 2>/dev/null < \"\$node\" && [ -n \"\$b\" ] && { l=\$(( b * 100 / max )); [ \$b -gt 0 ] && [ \$l -lt 1 ] && l=1; }; s=$frame; fi; ")
        append("if [ -n \"\$l\" ] && [ \"\$l\" != \"\$p\" ]; then service call $SERVICE 2 s16 $BRIGHTNESS s16 \$l >/dev/null 2>&1; p=\$l; fi; ")
        append("sleep \$s; done")
    }

    @Volatile private var loop: Process? = null

    private fun stopLoop() {
        loop?.let { process -> runCatching { process.destroy() } }
        loop = null
    }

    private fun startPolling(context: Context) {
        stopPolling()
        if (!followBrightness(context)) return
        // First choice: the screen's shown brightness, read here 30 times a second and once a
        // frame while it's changing, so fades stay smooth without a process per read
        if (shownShare(context) != null) {
            followShown(context)
            return
        }
        // Next: the shell follows the panel itself, once a frame
        worker.execute {
            if (route() != Route.Vendor || brightnessKept == false) return@execute
            val refresh = context.getSystemService(android.hardware.display.DisplayManager::class.java)
                ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate?.takeIf { it > 0f } ?: 60f
            val started = AdbShell.startLoop(followLoop(1000.0 / refresh))
            if (started != null) {
                loop = started
                lastLevel = -1
            } else {
                pollHandler.post { followSetting(context) }
            }
        }
    }

    private fun followShown(context: Context) {
        val app = context.applicationContext
        val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val refresh = app.getSystemService(android.hardware.display.DisplayManager::class.java)
            ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.refreshRate?.takeIf { it > 0f } ?: 60f
        val frameMs = (1000f / refresh.coerceAtMost(120f)).toLong().coerceAtLeast(8L)
        var lastShare = -1f
        var changedAt = 0L
        val tick = object : Runnable {
            override fun run() {
                val now = android.os.SystemClock.uptimeMillis()
                if (power?.isInteractive == false) {
                    pollHandler.postDelayed(this, 1_000L)
                    return
                }
                val share = shownShare(app)
                if (share != null && share != lastShare) {
                    lastShare = share
                    changedAt = now
                    if (brightnessKept != false) set(levelForScreen(app))
                }
                // Once a frame for a second after a change (a fade), else 30 times a second
                pollHandler.postDelayed(this, if (now - changedAt < 1_000L) frameMs else SHOWN_POLL_MS)
            }
        }
        poller = tick
        pollContext = app
        pollHandler.post(tick)
    }

    /** Where the shell can't read the panel: the brightness setting, as Android reports it changing. */
    private fun followSetting(context: Context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        var lastShare = -1f
        fun update() {
            if (power?.isInteractive == false || brightnessKept == false) return
            val share = screenShare(context)
            if (share != lastShare) {
                lastShare = share
                set(levelForScreen(context))
            }
        }
        val resolver = context.contentResolver
        observer = object : android.database.ContentObserver(pollHandler) {
            override fun onChange(selfChange: Boolean) = update()
        }.also { hook ->
            listOf(Settings.System.SCREEN_BRIGHTNESS, "screen_brightness_float", Settings.System.SCREEN_BRIGHTNESS_MODE)
                .forEach { name ->
                    runCatching { resolver.registerContentObserver(Settings.System.getUriFor(name), false, hook) }
                }
        }
        val displays = context.getSystemService(android.hardware.display.DisplayManager::class.java)
        displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) = update()
        }.also { runCatching { displays?.registerDisplayListener(it, pollHandler) } }
        val tick = object : Runnable {
            override fun run() {
                val adaptive = runCatching {
                    Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                }.getOrDefault(0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                if (adaptive) update()
                pollHandler.postDelayed(this, ADAPTIVE_POLL_MS)
            }
        }
        poller = tick
        pollHandler.post { update() }
        pollHandler.post(tick)
        this.pollContext = context.applicationContext
    }

    private var pollContext: Context? = null

    private fun stopPolling() {
        stopLoop()
        poller?.let { pollHandler.removeCallbacks(it) }
        poller = null
        val context = pollContext ?: return
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
        displayListener?.let { listener ->
            runCatching { context.getSystemService(android.hardware.display.DisplayManager::class.java)?.unregisterDisplayListener(listener) }
        }
        displayListener = null
        pollContext = null
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        stopPolling()
    }

    /** Three quick flashes for a notification, then back to where it was. */
    fun flash(context: Context) {
        if (!notificationFlash(context)) return
        worker.execute {
            val before = brightness() ?: return@execute
            repeat(3) {
                lastLevel = -1; apply(100)
                Thread.sleep(180)
                apply(0)
                Thread.sleep(180)
            }
            lastLevel = -1
            apply(before)
        }
    }
}
