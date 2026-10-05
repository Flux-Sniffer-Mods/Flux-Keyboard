package it.palsoftware.pastiera.commands

import android.content.Context
import it.palsoftware.pastiera.shortcuts.UserShortcuts

/** Shortcuts added by hand ("Add a shortcut"): each is a quick launcher command of its own */
class UserShortcutCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    override fun getCommands(context: Context): List<CommandTarget> {
        val pm = context.packageManager
        return UserShortcuts.all(context).mapNotNull { shortcut ->
            // A shortcut whose app was uninstalled isn't offered (it's kept, in case it comes back)
            val appInfo = runCatching { pm.getApplicationInfo(shortcut.packageName, 0) }.getOrNull()
                ?: return@mapNotNull null
            val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(shortcut.packageName)
            CommandTarget(
                id = "user_shortcut:${shortcut.id}",
                source = id,
                kind = CommandKind.Shortcut,
                label = shortcut.label,
                subtitle = it.palsoftware.pastiera.shortcuts.TermuxScripts.runsInBackground(shortcut.intentUri)
                    ?.let { background -> it.palsoftware.pastiera.shortcuts.TermuxScripts.kind(context, background) }
                    ?: appName,
                icon = CommandIcon.DrawableIcon(UserShortcuts.icon(context, shortcut)),
                launch = CommandLaunchSpec.IntentUri(
                    action = UserShortcuts.LAUNCH_ACTION,
                    packageName = shortcut.packageName,
                    intentUri = shortcut.intentUri
                ),
                capabilities = setOf(CommandCapability.SendsIntent),
                defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
                searchTokens = listOf(shortcut.label, appName, "$appName ${shortcut.label}", "shortcut")
            )
        }
    }
}
