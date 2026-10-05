package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.shortcuts.TermuxScripts
import it.palsoftware.pastiera.shortcuts.UserShortcuts

/** Termux:Widget's scripts and tasks (~/.shortcuts), each a quick launcher command of its own. */
class TermuxScriptCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    override fun getCommands(context: Context): List<CommandTarget> {
        if (!TermuxScripts.enabled(context) || !TermuxScripts.available(context)) return emptyList()
        val pm = context.packageManager
        val icon = runCatching { pm.getApplicationIcon(UserShortcuts.TERMUX_PACKAGE) }.getOrNull()
        // Scripts already added by hand (Add a shortcut) aren't listed twice
        val added = UserShortcuts.all(context).map { it.intentUri }
        return TermuxScripts.found(context).mapNotNull { script ->
            val intent = TermuxScripts.command(script)
            val path = intent.getStringExtra("com.termux.RUN_COMMAND_PATH").orEmpty()
            if (added.any { path in it }) return@mapNotNull null
            val label = TermuxScripts.label(script)
            val kind = TermuxScripts.kind(context, script.startsWith("tasks/"))
            CommandTarget(
                id = "termux:$script",
                source = id,
                kind = CommandKind.Shortcut,
                label = label,
                subtitle = kind,
                icon = CommandIcon.DrawableIcon(icon),
                launch = CommandLaunchSpec.IntentUri(
                    action = UserShortcuts.LAUNCH_ACTION,
                    packageName = UserShortcuts.TERMUX_PACKAGE,
                    intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
                ),
                capabilities = setOf(CommandCapability.SendsIntent),
                defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
                searchTokens = listOf(label, script, "termux $label", "termux")
            )
        }
    }
}
