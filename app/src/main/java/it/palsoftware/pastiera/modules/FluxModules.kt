package it.palsoftware.pastiera.modules

import android.content.Context
import it.palsoftware.pastiera.FluxLog
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GamesActivity
import it.palsoftware.pastiera.streaming.StreamHost
import it.palsoftware.pastiera.streaming.StreamHostService
import it.palsoftware.pastiera.streaming.StreamWake

/** Flux Titan Hub's modules: a tile each on its home screen. */
enum class FluxModule { KEYBOARD, GAMING, STREAMING, DESKTOP }

/**
 * Which modules are on. The keyboard is the app itself, always there; gaming mode and streaming
 * are switched on when wanted (their code comes with the app: switching one on adds its parts,
 * such as the Games app in the app list and streaming's services, and off takes them away);
 * the desktop is still to come. Everything else asks here before doing a module's work.
 */
object FluxModules {
    private const val TAG = "FluxModules"
    private const val KEY_GAMING = "module_gaming"
    private const val KEY_STREAMING = "module_streaming"
    private const val KEY_SET_UP = "modules_set_up"

    fun on(context: Context, module: FluxModule): Boolean = when (module) {
        FluxModule.KEYBOARD -> true
        FluxModule.DESKTOP -> false
        FluxModule.GAMING -> SettingsManager.getPreferences(context).getBoolean(KEY_GAMING, false)
        FluxModule.STREAMING -> SettingsManager.getPreferences(context).getBoolean(KEY_STREAMING, false)
    }

    /** Switched on and off from its tile: not the keyboard (the app itself), not the desktop (to come). */
    fun switchable(module: FluxModule): Boolean = module == FluxModule.GAMING || module == FluxModule.STREAMING

    /** Coming in a later version: shown, not opened. */
    fun comingSoon(module: FluxModule): Boolean = module == FluxModule.DESKTOP

    fun setOn(context: Context, module: FluxModule, on: Boolean) {
        if (!switchable(module)) return
        val app = context.applicationContext
        val key = if (module == FluxModule.GAMING) KEY_GAMING else KEY_STREAMING
        SettingsManager.getPreferences(app).edit().putBoolean(key, on).apply()
        when (module) {
            // Gaming mode itself, and the Games app in the app list, come and go with it
            FluxModule.GAMING -> {
                GameProfiles.setEnabled(app, on)
                GamesActivity.setEnabled(app, on)
            }
            // Off: nothing answers Moonlight any more (pairings are kept for when it's back)
            FluxModule.STREAMING -> if (on) StreamWake.start(app) else {
                if (StreamHostService.alwaysAvailable(app)) StreamHostService.setAlwaysAvailable(app, false)
                if (StreamHostService.enabled(app)) StreamHostService.setEnabled(app, false)
                StreamWake.stop(app)
            }
            else -> Unit
        }
        FluxLog.w(TAG, "${module.name.lowercase()} ${if (on) "on" else "off"}")
    }

    /**
     * Once, as this version first starts: a fresh install has the keyboard alone; an update keeps
     * on what's in use (gaming mode or the Games app; streaming, always available or a paired
     * Moonlight client).
     */
    fun setUp(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getBoolean(KEY_SET_UP, false)) return
        val gaming = GameProfiles.enabled(context) || GamesActivity.enabled(context)
        val streaming = StreamHostService.enabled(context) || StreamHostService.alwaysAvailable(context) ||
            runCatching { StreamHost.pairedCount(context) > 0 }.getOrDefault(false)
        prefs.edit().putBoolean(KEY_GAMING, gaming).putBoolean(KEY_STREAMING, streaming).putBoolean(KEY_SET_UP, true).apply()
        FluxLog.w(TAG, "modules set up: gaming ${if (gaming) "on" else "off"}, streaming ${if (streaming) "on" else "off"}")
    }
}
