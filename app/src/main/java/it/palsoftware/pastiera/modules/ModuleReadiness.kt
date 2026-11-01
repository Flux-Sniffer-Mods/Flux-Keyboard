package it.palsoftware.pastiera.modules

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import it.palsoftware.pastiera.ImeIdentity
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.streaming.StreamHost
import it.palsoftware.pastiera.streaming.StreamHostService

/**
 * What a module still needs before it works, each with its fix on the module's page (the ready
 * checklist) and its tile's status on the home screen. Only what a module can't do without:
 * extras that ask for themselves when used aren't listed.
 */
object ModuleReadiness {
    enum class Need {
        /** The keyboard switched on in Android's keyboards. */
        KEYBOARD_ON,
        /** The keyboard chosen as the one in use. */
        KEYBOARD_CHOSEN,
        /** The keyboard's accessibility helper: gaming mode reads the keys through it. */
        ACCESSIBILITY,
        /** The built-in shell (or Shizuku) running. */
        SHELL,
        /** Drawing over other apps: placing keys on a game's own buttons. */
        OVERLAY,
        /** Notifications: gaming mode's (placing keys, profiles) and streaming's (pairing PINs, requests). */
        NOTIFICATIONS,
        /** A Moonlight device paired with the phone. */
        PAIRED
    }

    data class ImeStatus(val enabled: Boolean, val selected: Boolean)

    /** Whether the keyboard is switched on in Android, and chosen as the one in use. */
    fun imeStatus(context: Context): ImeStatus = runCatching {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val enabled = enabledMethods.any { it.packageName == ImeIdentity.packageName || ImeIdentity.matchesImeId(it.id) }
        if (!enabled) return@runCatching ImeStatus(false, false)
        val selected = runCatching {
            ImeIdentity.matchesImeId(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())
        }.getOrElse {
            // Android 14 and later may refuse to say which keyboard is in use: the only one switched on is
            enabledMethods.size == 1
        }
        ImeStatus(true, selected)
    }.getOrDefault(ImeStatus(false, false))

    fun accessibilityOn(context: Context): Boolean = runCatching {
        it.palsoftware.pastiera.apps.isPastieraAccessibilityServiceEnabled(context)
    }.getOrDefault(false)

    fun shellOn(): Boolean = runCatching { it.palsoftware.pastiera.adb.AdbShell.available() }.getOrDefault(false)

    fun notificationsOn(context: Context): Boolean =
        it.palsoftware.pastiera.inputmethod.NotificationHelper.hasNotificationPermission(context)

    /** A game is played by keys placed on its screen (the placer draws over it). */
    private fun placesKeys(context: Context): Boolean = runCatching {
        GameProfiles.all(context).any { it.taps.isNotEmpty() || it.streamTaps.isNotEmpty() }
    }.getOrDefault(false)

    /** A module's line on its home tile: what it still needs, or how it's doing. */
    data class Status(
        val needs: List<Need>,
        /** The Moonlight device streaming now. */
        val streamingTo: String? = null,
        /** Answering Moonlight: streaming on, or always available. */
        val listening: Boolean = false,
        val paired: Int = 0
    )

    fun status(context: Context, module: FluxModule): Status {
        val needs = needs(context, module)
        if (module != FluxModule.STREAMING) return Status(needs)
        return Status(
            needs,
            streamingTo = StreamHost.streamingDevice.takeIf { StreamHost.session != null },
            listening = StreamHostService.enabled(context) || StreamHostService.alwaysAvailable(context),
            paired = runCatching { StreamHost.pairedCount(context) }.getOrDefault(0)
        )
    }

    /** What [module] needs that isn't there yet, most important first. */
    fun needs(context: Context, module: FluxModule): List<Need> = when (module) {
        FluxModule.KEYBOARD -> {
            val ime = imeStatus(context)
            listOfNotNull(
                Need.KEYBOARD_ON.takeIf { !ime.enabled },
                Need.KEYBOARD_CHOSEN.takeIf { ime.enabled && !ime.selected }
            )
        }
        FluxModule.GAMING -> listOfNotNull(
            Need.SHELL.takeIf { !shellOn() },
            Need.ACCESSIBILITY.takeIf { !accessibilityOn(context) },
            Need.OVERLAY.takeIf { placesKeys(context) && !Settings.canDrawOverlays(context) },
            Need.NOTIFICATIONS.takeIf { !notificationsOn(context) }
        )
        FluxModule.STREAMING -> listOfNotNull(
            Need.SHELL.takeIf { !shellOn() },
            Need.NOTIFICATIONS.takeIf { !notificationsOn(context) },
            Need.PAIRED.takeIf { runCatching { StreamHost.pairedCount(context) == 0 }.getOrDefault(false) }
        )
        FluxModule.DESKTOP -> listOfNotNull(Need.SHELL.takeIf { !shellOn() })
    }
}
