package it.palsoftware.pastiera.commands

import android.content.Context
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.adb.AdbShell

/**
 * ADB shortcuts through Shizuku (no root), for keys (Customize keys) and the quick launcher:
 * force-stop the app in front, Battery Saver on or off, and the app in front off the network.
 * Only while Shizuku allows Flux Keyboard.
 */
class AdbCommandSource : CommandSource {
    override val id = CommandSourceId.Pastiera

    override fun getCommands(context: Context): List<CommandTarget> {
        if (!AdbShell.available()) return emptyList()
        fun command(action: String, label: Int, tokens: List<String>) = CommandTarget(
            // The ids keys were assigned with before (root.…) still run these
            id = "root.$action",
            source = id,
            kind = CommandKind.PastieraAction,
            label = context.getString(label),
            subtitle = context.getString(R.string.adb_command_subtitle),
            icon = CommandIcon.Settings,
            launch = CommandLaunchSpec.InternalAction("root_$action"),
            capabilities = setOf(CommandCapability.AdjustsDeviceState),
            defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
            searchTokens = tokens + listOf("Shizuku", "ADB")
        )
        return listOf(
            command(FORCE_STOP, R.string.root_force_stop_title, listOf("Force stop", "Kill", "Close app")),
            command(BATTERY_SAVER, R.string.root_battery_saver_title, listOf("Battery", "Saver", "Power")),
            command(BLOCK_NETWORK, R.string.root_block_network_title, listOf("Network", "Offline", "Block", "Internet"))
        ) + it.palsoftware.pastiera.adb.ScreenDensity.Preset.entries.map { preset ->
            command(preset.action, preset.titleRes, listOf("Density", "DPI", "Screen size", "Smallest width", "Display size"))
        }
    }

    companion object {
        const val FORCE_STOP = "force_stop"
        const val BATTERY_SAVER = "battery_saver"
        const val BLOCK_NETWORK = "block_network"

        /** Runs an ADB shortcut; false when it isn't one. */
        fun execute(context: Context, action: String): Boolean {
            val front = it.palsoftware.pastiera.inputmethod.launcher.QuickLauncherOpener.foregroundPackage
            when (action.removePrefix("root_")) {
                FORCE_STOP -> front?.takeIf { it != context.packageName }?.let { AdbShell.runAsync("am force-stop $it") }
                BATTERY_SAVER -> AdbShell.runAsync(
                    "if [ \"$(settings get global low_power)\" = \"1\" ]; then cmd power set-mode 0; else cmd power set-mode 1; fi"
                )
                // Android 14+: the app in front off the network (its own firewall chain), or back on
                BLOCK_NETWORK -> front?.takeIf { it != context.packageName }?.let { pkg ->
                    AdbShell.runAsync(
                        "cmd connectivity set-chain3-enabled true; " +
                            "if cmd connectivity get-package-networking-enabled $pkg | grep -qi false; " +
                            "then cmd connectivity set-package-networking-enabled true $pkg; " +
                            "else cmd connectivity set-package-networking-enabled false $pkg; fi"
                    )
                }
                else -> {
                    val preset = it.palsoftware.pastiera.adb.ScreenDensity.Preset.byAction(action.removePrefix("root_")) ?: return false
                    it.palsoftware.pastiera.adb.ScreenDensity.apply(context, preset)
                }
            }
            return true
        }
    }
}
