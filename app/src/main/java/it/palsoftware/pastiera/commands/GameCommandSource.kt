package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GameFolders
import it.palsoftware.pastiera.gaming.GameLaunchActivity
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.shortcuts.UserShortcuts

/**
 * Gaming mode's games, each a quick launcher command: the games found in the game folders and
 * the games with a profile, started as their home screen shortcuts start them.
 */
class GameCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    override fun getCommands(context: Context): List<CommandTarget> {
        if (!enabled(context)) return emptyList()
        val pm = context.packageManager
        val profiles = GameProfiles.all(context).filter { it.isGame }
        val fromProfiles = profiles.map { profile ->
            Triple(profile.name, profile.packages.firstOrNull().orEmpty(), GameLaunchActivity.intentFor(context, profile))
        }
        val known = profiles.mapNotNull { it.launch }.toSet()
        val fromFolders = GameFolders.scanned(context).filter { it.launch == null || it.launch !in known }.map { game ->
            Triple(game.name, game.packageName, GameLaunchActivity.intentFor(context, game))
        }
        return (fromProfiles + fromFolders).distinctBy { it.first.lowercase() to it.second }.map { (name, pkg, intent) ->
            val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            CommandTarget(
                id = "game:$pkg:$name",
                source = id,
                kind = CommandKind.Shortcut,
                label = name,
                subtitle = appName,
                icon = CommandIcon.DrawableIcon(runCatching { pm.getApplicationIcon(pkg) }.getOrNull()),
                launch = CommandLaunchSpec.IntentUri(
                    action = UserShortcuts.LAUNCH_ACTION,
                    packageName = context.packageName,
                    intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
                ),
                capabilities = setOf(CommandCapability.SendsIntent),
                defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
                searchTokens = listOf(name, appName, "game $name", "game")
            )
        }
    }

    companion object {
        private const val KEY_ENABLED = "game_quick_launcher"

        fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, on: Boolean) =
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
