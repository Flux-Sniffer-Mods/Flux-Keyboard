package it.palsoftware.pastiera.commands

import android.content.Context
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R

class PastieraCommandSource : CommandSource {
    override val id = CommandSourceId.Pastiera

    override fun getCommands(context: Context): List<CommandTarget> {
        return listOf(
            CommandTarget(
                id = COMMAND_QUICK_LAUNCHER,
                source = id,
                kind = CommandKind.PastieraAction,
                label = "${it.palsoftware.pastiera.BuildConfig.APP_NAME} QuickLauncher",
                subtitle = "Open ${it.palsoftware.pastiera.BuildConfig.APP_NAME} search",
                icon = CommandIcon.Search,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_QUICK_LAUNCHER),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf(it.palsoftware.pastiera.BuildConfig.APP_NAME, "QuickLauncher", "Search")
            ),
            CommandTarget(
                id = COMMAND_MAIN_ACTIVITY,
                source = id,
                kind = CommandKind.PastieraAction,
                label = it.palsoftware.pastiera.BuildConfig.APP_NAME,
                subtitle = "Open app settings",
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_MAIN_ACTIVITY),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf(it.palsoftware.pastiera.BuildConfig.APP_NAME, "Settings")
            ),
            CommandTarget(
                id = COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE,
                source = id,
                kind = CommandKind.PastieraAction,
                label = "Toggle Keyboard Mode",
                subtitle = "Switch Virtual / Hardware",
                icon = CommandIcon.DrawableIcon(ContextCompat.getDrawable(context, R.drawable.expansion_panels_24)),
                launch = CommandLaunchSpec.InternalAction(ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE),
                capabilities = setOf(CommandCapability.AdjustsDeviceState),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf("Keyboard", "Software", "Virtual", "Hardware", "Toggle")
            ),
            // Nav Mode with the selection anchored at the cursor
            CommandTarget(
                id = COMMAND_SELECT_FROM_CURSOR,
                source = id,
                kind = CommandKind.PastieraAction,
                label = context.getString(R.string.select_from_cursor_title),
                subtitle = context.getString(R.string.select_from_cursor_subtitle),
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_SELECT_FROM_CURSOR),
                capabilities = setOf(CommandCapability.AdjustsDeviceState),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Select", "Selection", "Highlight", "Cursor")
            ),
            // Incognito typing and Offline mode together, from a key or here
            CommandTarget(
                id = COMMAND_TOGGLE_PRIVATE_MODE,
                source = id,
                kind = CommandKind.PastieraAction,
                label = context.getString(R.string.private_mode_title),
                subtitle = context.getString(R.string.private_mode_subtitle),
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_TOGGLE_PRIVATE_MODE),
                capabilities = setOf(CommandCapability.AdjustsDeviceState),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf("Private", "Incognito", "Offline", "Privacy")
            )
        )
    }

    companion object {
        const val COMMAND_QUICK_LAUNCHER = "pastiera.quick_launcher"
        const val COMMAND_MAIN_ACTIVITY = "pastiera.main"
        const val COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE = "pastiera.toggle_software_keyboard_mode"
        const val ACTION_OPEN_QUICK_LAUNCHER = "open_quick_launcher"
        const val ACTION_OPEN_MAIN_ACTIVITY = "open_main_activity"
        const val ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE = "toggle_software_keyboard_mode"
        const val COMMAND_TOGGLE_PRIVATE_MODE = "pastiera.toggle_private_mode"
        const val COMMAND_SELECT_FROM_CURSOR = "pastiera.select_from_cursor"
        const val ACTION_SELECT_FROM_CURSOR = "select_from_cursor"
        const val ACTION_TOGGLE_PRIVATE_MODE = "toggle_private_mode"
    }
}
