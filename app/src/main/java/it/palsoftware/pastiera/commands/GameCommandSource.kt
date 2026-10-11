package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GamesActivity
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
        // The same games as the Games app, each started the way it starts them (its first-start
        // question and profile included), not the emulator's own entry
        val games = GamesActivity.games(context).map { (profile, intent) ->
            Triple(profile.name, profile.packages.firstOrNull().orEmpty(), intent)
        }
        return games.map { (name, pkg, intent) ->
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
