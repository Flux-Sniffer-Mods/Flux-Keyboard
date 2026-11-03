package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import io.github.fluxsniffermods.core.FluxApp
import it.palsoftware.pastiera.apps.appLabel
import it.palsoftware.pastiera.flux.GamingBridge
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.shortcuts.UserShortcuts

/**
 * Flux Gaming's games, each a quick launcher command, started as their home screen shortcuts
 * start them; and the Flux apps' own pages (the Games app, Xbox Cloud Gaming, Streaming, the
 * desktop) as commands of their own. What Flux Gaming last said, asked again each time.
 */
class GameCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    override fun getCommands(context: Context): List<CommandTarget> {
        GamingBridge.refreshLauncher(context)
        val pm = context.packageManager
        val games = GamingBridge.launcherGames.map { game ->
            val pkg = game.packages.firstOrNull().orEmpty()
            // An Xbox cloud game plays in Flux Gaming's player: Xbox Cloud Gaming, not Flux Gaming
            val appName = if (pkg == FluxApp.GAMING.packageName) context.getString(R.string.games_app_xbox)
                else context.appLabel(pkg)
            target(context, "game:$pkg:${game.name}", game.name, appName,
                runCatching { pm.getApplicationIcon(pkg) }.getOrNull(), game.launch, listOf(game.name, appName, "game ${game.name}", "game"))
        }
        return entries(context) + games
    }

    private fun target(context: Context, key: String, label: String, subtitle: String, icon: android.graphics.drawable.Drawable?,
                       intent: Intent, tokens: List<String>) = CommandTarget(
        id = key,
        source = id,
        kind = CommandKind.Shortcut,
        label = label,
        subtitle = subtitle,
        icon = CommandIcon.DrawableIcon(icon),
        launch = CommandLaunchSpec.IntentUri(
            action = UserShortcuts.LAUNCH_ACTION,
            packageName = context.packageName,
            intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
        ),
        capabilities = setOf(CommandCapability.SendsIntent),
        defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
        searchTokens = tokens
    )

    /** The other Flux apps' pages: the Games app and Xbox Cloud Gaming, Streaming, the desktop. */
    private fun entries(context: Context): List<CommandTarget> {
        val pm = context.packageManager
        fun icon(app: FluxApp) = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
        fun launch(app: FluxApp) = pm.getLaunchIntentForPackage(app.packageName)
        val (xcloud, gamesApp) = GamingBridge.launcherEntries
        return listOfNotNull(
            gamesApp?.let { target(context, "game:flux:games", context.getString(R.string.stream_flux_games),
                FluxApp.GAMING.title, icon(FluxApp.GAMING), it, listOf("Flux Games", "Games", "launcher")) },
            xcloud?.let { target(context, "game:flux:xcloud", context.getString(R.string.games_app_xbox),
                "Xbox Cloud Gaming", icon(FluxApp.GAMING), it, listOf("XCloud", "Xbox", "Cloud", "Game Pass")) },
            launch(FluxApp.STREAMING)?.let { target(context, "game:flux:stream", FluxApp.STREAMING.title, "Moonlight",
                icon(FluxApp.STREAMING), it, listOf("Stream", "Moonlight", "Streaming", "TV")) },
            launch(FluxApp.DESKTOP)?.let { target(context, "game:flux:desktop", FluxApp.DESKTOP.title, "Linux",
                icon(FluxApp.DESKTOP), it, listOf("Desktop", "Linux", "Debian", "Ubuntu")) }
        )
    }
}
