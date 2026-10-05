package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.shortcuts.AppActionDiscovery

/**
 * Apps' own shortcuts, the ones their launcher icon shows on a long press ("New message",
 * "Scan QR code"): each is a quick launcher command of its own. Read from the apps on the
 * phone, and kept until an app updates.
 */
class AppShortcutCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    private data class Cached(val updatedAt: Long, val commands: List<CommandTarget>)

    override fun getCommands(context: Context): List<CommandTarget> {
        if (!SettingsManager.getQuickLauncherAppShortcuts(context)) return emptyList()
        val pm = context.packageManager
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
        return launchers.flatMap { packageName -> commandsFor(context, packageName) }
    }

    private fun commandsFor(context: Context, packageName: String): List<CommandTarget> {
        val pm = context.packageManager
        val updatedAt = runCatching { pm.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull() ?: return emptyList()
        cache[packageName]?.takeIf { it.updatedAt == updatedAt }?.let { return it.commands }
        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
        val icon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
        val commands = AppActionDiscovery.discover(context, packageName).launcherShortcuts.map { action ->
            CommandTarget(
                id = "shortcut:$packageName:${action.id}",
                source = id,
                kind = CommandKind.Shortcut,
                label = action.label,
                subtitle = appName,
                icon = CommandIcon.DrawableIcon(icon),
                launch = CommandLaunchSpec.IntentUri(action = Intent.ACTION_VIEW, packageName = packageName, intentUri = action.intentUri),
                capabilities = setOf(CommandCapability.SendsIntent, CommandCapability.RequiresInstalledPackage),
                defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
                searchTokens = listOf(action.label, appName, "$appName ${action.label}", "shortcut")
            )
        }
        cache[packageName] = Cached(updatedAt, commands)
        return commands
    }

    companion object {
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    }
}
