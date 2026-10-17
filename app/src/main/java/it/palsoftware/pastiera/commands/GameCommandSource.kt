package it.palsoftware.pastiera.commands

import it.palsoftware.pastiera.apps.appLabel
import android.content.Context
import android.content.Intent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.gaming.GamesActivity
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.modules.FluxModules
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
        // question and profile included), not the emulator's own entry; with gaming mode's module on
        val gaming = FluxModules.on(context, FluxModule.GAMING)
        val games = if (!gaming) emptyList() else GamesActivity.games(context).map { (profile, intent) ->
            Triple(profile.name, profile.packages.firstOrNull().orEmpty(), intent)
        }
        return entries(context, gaming) + games.map { (name, pkg, intent) ->
            // An Xbox cloud game plays in Flux Keyboard's player: XCloud Games, not Flux Keyboard
            val appName = if (pkg == context.packageName) context.getString(it.palsoftware.pastiera.R.string.games_app_xbox)
                else context.appLabel(pkg)
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

    /** The Games app and XCloud Games (with gaming mode's module on), and streaming's settings (with streaming's), as commands of their own. */
    private fun entries(context: Context, gaming: Boolean): List<CommandTarget> {
        val games = context.getString(it.palsoftware.pastiera.R.string.games_app_title)
        val icon = CommandIcon.DrawableIcon(runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull())
        fun entry(key: String, label: String, subtitle: String, intent: Intent, tokens: List<String>) = CommandTarget(
            id = "game:flux:$key",
            source = id,
            kind = CommandKind.Shortcut,
            label = label,
            subtitle = subtitle,
            icon = icon,
            launch = CommandLaunchSpec.IntentUri(
                action = UserShortcuts.LAUNCH_ACTION,
                packageName = context.packageName,
                intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
            ),
            capabilities = setOf(CommandCapability.SendsIntent),
            defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
            searchTokens = tokens
        )
        val xcloud = Intent(context, it.palsoftware.pastiera.gaming.XboxPlayActivity::class.java)
            .setData(android.net.Uri.parse(it.palsoftware.pastiera.gaming.XboxPlayActivity.HOME))
        return listOfNotNull(
            entry("games", context.getString(it.palsoftware.pastiera.R.string.stream_flux_games), games,
                Intent(context, GamesActivity::class.java), listOf("Flux Games", "Games", "launcher")).takeIf { gaming },
            entry("xcloud", context.getString(it.palsoftware.pastiera.R.string.games_app_xbox), "Xbox Cloud Gaming",
                xcloud, listOf("XCloud", "Xbox", "Cloud", "Game Pass")).takeIf { gaming },
            entry("stream", context.getString(it.palsoftware.pastiera.R.string.stream_section), "Moonlight",
                GamesActivity.streamSettings(context), listOf("Stream", "Moonlight", "Streaming", "TV"))
                .takeIf { FluxModules.on(context, FluxModule.STREAMING) }
        )
    }

    companion object {
        private const val KEY_ENABLED = "game_quick_launcher"

        fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, on: Boolean) =
            SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, on).apply()
    }
}
