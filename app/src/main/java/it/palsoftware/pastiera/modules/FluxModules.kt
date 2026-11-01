package it.palsoftware.pastiera.modules

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GamesActivity
import it.palsoftware.pastiera.streaming.StreamApps
import it.palsoftware.pastiera.streaming.StreamHost
import it.palsoftware.pastiera.streaming.StreamHostService
import it.palsoftware.pastiera.streaming.StreamWake

/** Flux Titan Hub's modules: a tile each on its home screen. */
enum class FluxModule { KEYBOARD, GAMING, STREAMING, DESKTOP }

/**
 * Which modules are on. The keyboard, gaming mode and streaming are switched on when wanted
 * (their code comes with the app: switching one on adds its parts, such as the keyboard itself,
 * the Games and Streaming apps in the app list and streaming's services, and off takes them
 * away); the keyboard is on unless switched off. Everything else
 * asks here before doing a module's work.
 */
object FluxModules {
    private const val TAG = "FluxModules"
    private const val KEY_GAMING = "module_gaming"
    private const val KEY_STREAMING = "module_streaming"
    private const val KEY_KEYBOARD = "module_keyboard"
    private const val KEY_DESKTOP = "module_desktop"
    /** Once: the Streaming app in the app list for streaming already on before it existed. */
    private const val KEY_STREAMING_APP_SET_UP = "module_streaming_app_set_up"
    /** The keyboard itself, offered to Android as a keyboard while the module is on. */
    private const val KEYBOARD_SERVICE = "it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService"
    private const val KEY_SET_UP = "modules_set_up"

    fun on(context: Context, module: FluxModule): Boolean = when (module) {
        FluxModule.KEYBOARD -> SettingsManager.getPreferences(context).getBoolean(KEY_KEYBOARD, true)
        FluxModule.DESKTOP -> SettingsManager.getPreferences(context).getBoolean(KEY_DESKTOP, false)
        FluxModule.GAMING -> SettingsManager.getPreferences(context).getBoolean(KEY_GAMING, false)
        FluxModule.STREAMING -> SettingsManager.getPreferences(context).getBoolean(KEY_STREAMING, false)
    }

    /** Switched on and off from its tile. */
    fun switchable(module: FluxModule): Boolean = true

    /** Coming in a later version: shown, not opened. None now. */
    fun comingSoon(module: FluxModule): Boolean = false

    fun setOn(context: Context, module: FluxModule, on: Boolean) {
        if (!switchable(module)) return
        val app = context.applicationContext
        val key = when (module) { FluxModule.GAMING -> KEY_GAMING; FluxModule.STREAMING -> KEY_STREAMING; FluxModule.DESKTOP -> KEY_DESKTOP; else -> KEY_KEYBOARD }
        SettingsManager.getPreferences(app).edit().putBoolean(key, on).apply()
        when (module) {
            // Gaming mode itself, and the Games app in the app list, come and go with it
            FluxModule.GAMING -> {
                GameProfiles.setEnabled(app, on)
                GamesActivity.setEnabled(app, on)
            }
            // Off: nothing answers Moonlight any more (pairings are kept for when it's back), and
            // the Streaming app leaves the app list with it
            FluxModule.STREAMING -> {
                StreamApps.setInAppList(app, on)
                if (on) StreamWake.start(app) else {
                    if (StreamHostService.alwaysAvailable(app)) StreamHostService.setAlwaysAvailable(app, false)
                    if (StreamHostService.enabled(app)) StreamHostService.setEnabled(app, false)
                    StreamWake.stop(app)
                }
            }
            // Off: Android no longer offers it as a keyboard (another takes over if it was in
            // use), and its entry leaves the app list
            FluxModule.KEYBOARD -> {
                app.packageManager.setComponentEnabledSetting(
                    android.content.ComponentName(app, KEYBOARD_SERVICE),
                    if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                if (!on && it.palsoftware.pastiera.KeyboardActivity.inAppList(app)) it.palsoftware.pastiera.KeyboardActivity.setInAppList(app, false)
            }
            // Off: the desktop ends if it's running (Debian stays installed for when it's back)
            FluxModule.DESKTOP -> if (!on && it.palsoftware.pastiera.linux.LinuxDesktop.state.value == it.palsoftware.pastiera.linux.LinuxDesktop.State.Running) {
                it.palsoftware.pastiera.linux.LinuxDesktop.stop(app)
            }
            else -> Unit
        }
        // Its Quick Settings tile kept in step
        QuickTiles.refresh(app)
        FluxLog.w(TAG, "${module.name.lowercase()} ${if (on) "on" else "off"}")
    }

    /**
     * Once, as this version first starts: a fresh install has the keyboard alone; an update keeps
     * on what's in use (gaming mode or the Games app; streaming, always available or a paired
     * Moonlight client).
     */
    fun setUp(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getBoolean(KEY_SET_UP, false)) {
            // Streaming on from before the Streaming app: it joins the app list, as Games does
            if (!prefs.getBoolean(KEY_STREAMING_APP_SET_UP, false)) {
                if (on(context, FluxModule.STREAMING)) StreamApps.setInAppList(context, true)
                prefs.edit().putBoolean(KEY_STREAMING_APP_SET_UP, true).apply()
            }
            return
        }
        val gaming = GameProfiles.enabled(context) || GamesActivity.enabled(context)
        val streaming = StreamHostService.enabled(context) || StreamHostService.alwaysAvailable(context) ||
            runCatching { StreamHost.pairedCount(context) > 0 }.getOrDefault(false)
        prefs.edit().putBoolean(KEY_GAMING, gaming).putBoolean(KEY_STREAMING, streaming).putBoolean(KEY_SET_UP, true)
            .putBoolean(KEY_STREAMING_APP_SET_UP, true).apply()
        if (streaming) StreamApps.setInAppList(context, true)
        FluxLog.w(TAG, "modules set up: gaming ${if (gaming) "on" else "off"}, streaming ${if (streaming) "on" else "off"}")
    }
}
