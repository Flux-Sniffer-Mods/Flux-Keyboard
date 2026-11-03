package it.palsoftware.pastiera

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.apps.AppEnterStandards
import it.palsoftware.pastiera.apps.AppPackageChangeMonitor
import it.palsoftware.pastiera.clicks.ClicksPowerKeyboardController
import it.palsoftware.pastiera.settings.RecommendedSettings

class PastieraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Flux Core's parts (the shell, root) keep their settings in the keyboard's own, as before
        io.github.fluxsniffermods.core.FluxPrefs.fileName = SettingsManager.PREFS_FILE
        // Its log, background failures kept apart, the shell started (now or once Wi-Fi connects)
        // and root asked for when switched on; the keyboard checks for its own updates
        io.github.fluxsniffermods.core.FluxCore.init(this, BuildConfig.VERSION_NAME, checksUpdates = false)
        // A fresh install starts from the default configuration, before anything writes settings
        RecommendedSettings.applyIfFreshInstall(this)
        SettingsManager.keepDevBuildChoices(this)
        OfflineMode.load(this)
        AppEnterStandards.installSystemCategoryLookup(this)
        it.palsoftware.pastiera.update.clearStaleForkUpdateNotice(this)
        SettingsManager.initializeAltShiftLayoutSwitchDefault(this)
        SettingsManager.enforceTitan2EliteRoundedCornersOnce(this)
        AppPackageChangeMonitor.register(this)
        ClicksPowerKeyboardController.initialize(this)
        publishSoftwareKeyboardModeShortcut()
        Handler(Looper.getMainLooper()).post {
            AdditionalSubtypeUtils.registerAdditionalSubtypes(this)
            // Once, after the update that moved them out: what the hub's modules left behind
            it.palsoftware.pastiera.flux.MovedModules.tidy(this)
        }
    }

    private fun publishSoftwareKeyboardModeShortcut() {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(this, SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID)
            .setShortLabel(getString(R.string.software_keyboard_mode_toggle_shortcut_short))
            .setLongLabel(getString(R.string.software_keyboard_mode_toggle_shortcut_long))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(SoftwareKeyboardModeActions.ACTION_TOGGLE)
                    .setClass(this, SoftwareKeyboardModeActionActivity::class.java)
            )
            .build()
        // The QuickLauncher on the app icon's long-press, where key mappers can pick it too
        val quickLauncher = ShortcutInfo.Builder(this, QUICK_LAUNCHER_SHORTCUT_ID)
            .setShortLabel(getString(R.string.tutorial_quick_launcher_title))
            .setLongLabel(getString(R.string.quick_launcher_title))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(
                Intent(ACTION_QUICK_LAUNCHER)
                    .setClassName(this, "it.palsoftware.pastiera.QuickLauncherEntry")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            .build()
        runCatching {
            shortcutManager.removeDynamicShortcuts(listOf(SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID, QUICK_LAUNCHER_SHORTCUT_ID))
            shortcutManager.addDynamicShortcuts(listOf(quickLauncher, shortcut))
        }
    }

    companion object {
        private const val SOFTWARE_KEYBOARD_MODE_SHORTCUT_ID = "software_keyboard_mode_toggle"
        private const val QUICK_LAUNCHER_SHORTCUT_ID = "quick_launcher"
        /** Other apps open the QuickLauncher with this action. */
        const val ACTION_QUICK_LAUNCHER = "it.palsoftware.pastiera.action.QUICK_LAUNCHER"
    }
}
