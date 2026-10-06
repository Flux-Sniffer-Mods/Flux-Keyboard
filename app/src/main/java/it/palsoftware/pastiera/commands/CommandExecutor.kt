package it.palsoftware.pastiera.commands

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Toast
import it.palsoftware.pastiera.MainActivity
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SoftwareKeyboardModeActions
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.inputmethod.QuickLauncherActivity
import rikka.shizuku.Shizuku
import it.palsoftware.pastiera.device.PhoneTrackpadSettings
import it.palsoftware.pastiera.getSoftwareKeyboardModeToggleToastsEnabled

class CommandExecutor(
    private val context: Context,
    private val navModeController: NavModeController? = null,
    private val inputConnectionProvider: (() -> InputConnection?)? = null,
    private val showToast: Boolean = true
) {
    fun execute(command: CommandTarget): CommandExecutionResult {
        return execute(command.launch)
    }

    fun execute(launch: CommandLaunchSpec): CommandExecutionResult {
        return when (launch) {
            is CommandLaunchSpec.AppPackage -> launchPackage(launch.packageName)
            is CommandLaunchSpec.IntentUri -> startIntent(launch)
            is CommandLaunchSpec.InternalAction -> executeInternalAction(launch.actionId)
            is CommandLaunchSpec.NavAction -> executeNavAction(launch)
        }
    }

    private fun launchPackage(packageName: String): CommandExecutionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return fail("Package not available")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            CommandExecutionResult.Success
        } catch (error: Exception) {
            Log.e(TAG, "Failed to launch package $packageName", error)
            fail("Could not open app")
        }
    }

    private fun startIntent(spec: CommandLaunchSpec.IntentUri): CommandExecutionResult {
        if (spec.action == it.palsoftware.pastiera.shortcuts.UserShortcuts.LAUNCH_ACTION) {
            // A shortcut added by hand: started as the app that made it built it
            return try {
                val intent = Intent.parseUri(spec.intentUri ?: return fail("Command not available"), Intent.URI_INTENT_SCHEME)
                intent.selector = null
                // Stored intents can come back from a backup: never pass on access to our own files
                intent.removeFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
                intent.clipData = null
                // A Termux task runs through Termux's command service
                if (intent.component?.className == it.palsoftware.pastiera.shortcuts.UserShortcuts.TERMUX_RUN_COMMAND_SERVICE) {
                    context.startForegroundService(intent)
                    return CommandExecutionResult.Success
                }
                // A contact's direct dial needs the phone permission home screens hold: without
                // it, open the dialer with the number ready
                if (intent.action == Intent.ACTION_CALL &&
                    context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    intent.action = Intent.ACTION_DIAL
                    intent.component = null
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                CommandExecutionResult.Success
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start a hand-added shortcut", error)
                fail("Command failed")
            }
        }
        spec.intentUri?.let { uri ->
            // An app's own shortcut: only if the app still lets other apps open it
            val packageName = spec.packageName ?: return fail("Command not available")
            val action = it.palsoftware.pastiera.shortcuts.DiscoveredAction("", "", uri)
            val intent = it.palsoftware.pastiera.shortcuts.AppActionDiscovery.intentFor(context, packageName, action)
                ?: return fail("Command not available")
            return try {
                context.startActivity(intent)
                CommandExecutionResult.Success
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start app shortcut", error)
                fail("Command failed")
            }
        }
        return try {
            val intent = Intent(spec.action, spec.data?.let(Uri::parse)).apply {
                spec.packageName?.let(::setPackage)
                spec.componentName?.let { component ->
                    ComponentName.unflattenFromString(component)?.let(::setComponent)
                }
                spec.categories.forEach(::addCategory)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (spec.flags.contains("clear_top")) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                spec.flags
                    .mapNotNull { flag -> flag.split("=", limit = 2).takeIf { it.size == 2 } }
                    .forEach { (key, value) -> putExtra(key, value) }
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return fail("Command not available")
            }
            context.startActivity(intent)
            CommandExecutionResult.Success
        } catch (error: SecurityException) {
            Log.e(TAG, "Security error starting command intent", error)
            fail("Command blocked")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start command intent", error)
            fail("Command failed")
        }
    }

    private fun executeInternalAction(actionId: String): CommandExecutionResult {
        // ADB shortcuts through Shizuku (root_… ids kept for keys assigned before)
        if (actionId.startsWith("root_")) {
            return if (AdbCommandSource.execute(context, actionId)) CommandExecutionResult.Success else fail("Unknown action")
        }
        return when (actionId) {
            PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER -> {
                try {
                    val intent = QuickLauncherActivity.createOpenIntent(context)
                    context.startActivity(intent)
                    CommandExecutionResult.Success
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to open QuickLauncher", error)
                    fail("Could not open QuickLauncher")
                }
            }
            PastieraCommandSource.ACTION_OPEN_MAIN_ACTIVITY -> {
                try {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    CommandExecutionResult.Success
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to open Pastiera", error)
                    fail("Could not open ${it.palsoftware.pastiera.BuildConfig.APP_NAME}")
                }
            }
            PastieraCommandSource.ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE -> toggleSoftwareKeyboardMode()
            PastieraCommandSource.ACTION_SELECT_FROM_CURSOR -> {
                val controller = navModeController ?: return fail("Nav mode unavailable")
                controller.startSelectingFromCursor()
                CommandExecutionResult.Success
            }
            PastieraCommandSource.ACTION_TOGGLE_EXTRA_KEYS ->
                if (it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysToggle.toggle()) CommandExecutionResult.Success
                else fail("The keyboard isn't running")
            PastieraCommandSource.ACTION_TOGGLE_PRIVATE_MODE -> {
                it.palsoftware.pastiera.core.PrivateMode.toggle(context)
                CommandExecutionResult.Success
            }
            DeviceControlCommandSource.ACTION_HOME_SCREEN -> goHome()
            DeviceControlCommandSource.ACTION_PHONE_TRACKPAD_SETTINGS ->
                if (it.palsoftware.pastiera.device.PhoneTrackpadSettings.open(context)) CommandExecutionResult.Success
                else fail("Could not open the trackpad settings")
            DeviceControlCommandSource.ACTION_MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            DeviceControlCommandSource.ACTION_MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            DeviceControlCommandSource.ACTION_MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            DeviceControlCommandSource.ACTION_VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
            DeviceControlCommandSource.ACTION_VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
            DeviceControlCommandSource.ACTION_VOLUME_MUTE -> adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
            DeviceControlCommandSource.ACTION_BRIGHTNESS_UP -> sendShellKeyEvent(KeyEvent.KEYCODE_BRIGHTNESS_UP)
            DeviceControlCommandSource.ACTION_BRIGHTNESS_DOWN -> sendShellKeyEvent(KeyEvent.KEYCODE_BRIGHTNESS_DOWN)
            else -> fail("Unknown action")
        }
    }

    private fun goHome(): CommandExecutionResult {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandExecutionResult.Success
        } catch (error: Exception) {
            Log.e(TAG, "Failed to go home", error)
            fail("Could not go home")
        }
    }

    private fun toggleSoftwareKeyboardMode(): CommandExecutionResult {
        val next = SoftwareKeyboardModeActions.toggleTemporaryMode(context)
        if (showToast && SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context)) {
            val message = when (next) {
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL ->
                    context.getString(R.string.software_keyboard_mode_toggle_now_virtual)
                SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE ->
                    context.getString(R.string.software_keyboard_mode_toggle_now_hardware)
                SettingsManager.SoftwareKeyboardMode.AUTO ->
                    context.getString(R.string.software_keyboard_mode_auto_short)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        return CommandExecutionResult.Success
    }

    private fun dispatchMediaKey(keyCode: Int): CommandExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return fail("Audio unavailable")
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return CommandExecutionResult.Success
    }

    private fun adjustVolume(direction: Int): CommandExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return fail("Audio unavailable")
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return CommandExecutionResult.Success
    }

    private fun sendShellKeyEvent(keyCode: Int): CommandExecutionResult {
        return try {
            val shizukuAvailable = Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!shizukuAvailable) {
                return fail("Shizuku required")
            }
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("input", "keyevent", keyCode.toString()),
                null,
                null
            ) as Process
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                CommandExecutionResult.Success
            } else {
                fail("Command failed")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to send shell keyevent $keyCode", error)
            fail("Command failed")
        }
    }

    private fun executeNavAction(launch: CommandLaunchSpec.NavAction): CommandExecutionResult {
        val controller = navModeController ?: return fail("Nav mode unavailable")
        val inputConnection = inputConnectionProvider?.invoke() ?: return fail("No input context")
        return if (controller.executeMapping(launch.mappingType, launch.value, null, inputConnection)) {
            CommandExecutionResult.Success
        } else {
            fail("Nav action failed")
        }
    }

    private fun fail(reason: String): CommandExecutionResult.Failed {
        if (showToast) {
            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        }
        return CommandExecutionResult.Failed(reason)
    }

    companion object {
        private const val TAG = "CommandExecutor"
    }
}
