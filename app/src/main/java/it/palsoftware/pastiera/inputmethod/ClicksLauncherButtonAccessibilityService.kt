package it.palsoftware.pastiera.inputmethod

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import it.palsoftware.pastiera.SettingsManager

/**
 * The keyboard's optional helper, for what an input method can't do alone:
 * - the buttons of a supported Clicks Power Keyboard you assigned (Android handles Left Meta
 *   before an input method receives it);
 * - in apps where the keyboard is hidden, the keys for the status LEDs, the emoji key and Sym;
 * - the app in front, for screen size per app (window changes only, never window content).
 * Each part stays inert unless its option is on.
 */
class ClicksLauncherButtonAccessibilityService : AccessibilityService() {
    private val mapper = ClicksLauncherAccessibilityKeyMapper()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Hidden apps: keys for Pastiera's emoji/symbols panels go to Pastiera, not the app
        val taken = HiddenAppKeyObserver.intercept(event)
        HiddenAppKeyObserver.logKey(event, if (taken) "accessibility, to the keyboard" else "accessibility, to the app")
        if (taken) return true
        // Hidden apps with status LEDs: let the LEDs follow keys the app reads before any IME
        HiddenAppKeyObserver.observe(event)
        val device = event.device
        val result = mapper.map(
            event = event,
            isClicksPowerKeyboard = device?.let(DeviceSpecific::isClicksPowerKeyboard) == true,
            redButtonMode = SettingsManager.getClicksButtonMode(this),
            launcherButtonMode = SettingsManager.getClicksMetaButtonMode(this),
            altButtonMode = SettingsManager.getClicksAltButtonMode(this),
            microphoneButtonMode = SettingsManager.getClicksMicrophoneButtonMode(this)
        )
        result.directAction?.let { ClicksButtonDirectActionExecutor.execute(this, it) }
        val forwardedEvent = result.forwardedEvent
        if (forwardedEvent != null && !ClicksAccessibilityKeyBridge.dispatch(forwardedEvent)) {
            // Fail open when Pastiera has no active editor. Keeping the mapper's held-modifier
            // state would otherwise consume the following native key events as well.
            mapper.resetDevice(event.deviceId)
            return false
        }
        return result.consume
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // An app's window in front: screen size per app follows it, keyboard or not
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // A window that isn't an app's own screen (a widget updating on the home screen, or
            // the app coming back from recents) only counts once Android confirms what's in front
            val confirmed = isActivity(pkg, event.className?.toString())
            // The quick launcher opens over the app you're in: that app keeps its screen size
            val overlay = pkg == packageName && event.className?.toString()?.contains("QuickLauncher") == true
            if (!overlay) it.palsoftware.pastiera.adb.PerAppDensity.onAppInFront(this, pkg, needsConfirming = !confirmed)
            // Keyboard swipes per app follow the app's own screens
            if (confirmed && pkg != frontPackage) {
                frontPackage = pkg
                claimTrackpad(ClicksAccessibilityKeyBridge.trackpadCaptured)
            }
        }
    }

    private val activities = HashMap<String, Boolean>()

    private fun isActivity(pkg: String, className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        val key = "$pkg/$className"
        return activities.getOrPut(key) {
            runCatching { packageManager.getActivityInfo(android.content.ComponentName(pkg, className), 0) }.isSuccess
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Keyboard swipes: taken from apps while a field is typed in, handed to the keyboard
        ClicksAccessibilityKeyBridge.onTrackpadCaptureChanged = { captured -> claimTrackpad(captured) }
        claimTrackpad(ClicksAccessibilityKeyBridge.trackpadCaptured)
    }

    /** The app whose screen is in front, for keyboard swipes per app. */
    private var frontPackage: String? = null

    /** Swipes kept from the app in front (Settings > Trackpad: keyboard swipes per app). */
    private fun blockedHere(): Boolean = frontPackage?.let { pkg ->
        SettingsManager.trackpadBlockedIn(this, pkg)
    } == true

    private fun claimTrackpad(typing: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val captured = typing || blockedHere()
        android.os.Handler(mainLooper).post {
            runCatching {
                // Android 14: the service's info names the sources it takes before apps
                val info = serviceInfo ?: return@runCatching
                info.motionEventSources = if (captured) android.view.InputDevice.SOURCE_TOUCHPAD else 0
                serviceInfo = info
            }
        }
    }

    override fun onMotionEvent(event: android.view.MotionEvent) {
        // Taken only to keep them from the app (not typing): dropped
        if (!ClicksAccessibilityKeyBridge.trackpadCaptured) return
        ClicksAccessibilityKeyBridge.dispatch(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ClicksAccessibilityKeyBridge.onTrackpadCaptureChanged = null
        mapper.reset()
        super.onDestroy()
    }
}
