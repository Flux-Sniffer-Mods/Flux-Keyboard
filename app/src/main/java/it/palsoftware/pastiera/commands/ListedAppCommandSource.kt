package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import it.palsoftware.pastiera.apps.AppEnterStandards
import it.palsoftware.pastiera.apps.EnterStandard
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.shortcuts.AppCategory
import it.palsoftware.pastiera.shortcuts.AppIntent
import it.palsoftware.pastiera.shortcuts.AppShortcutPresets
import it.palsoftware.pastiera.shortcuts.StandardShortcut
import it.palsoftware.pastiera.getQuickLauncherListedAppShortcuts
import it.palsoftware.pastiera.getQuickLauncherListedAppsOff

/**
 * Flux Keyboard's own quick launcher commands for the apps in its app shortcut and Enter lists
 * (and, going by their Play Store category, every other app that offers the screens):
 * a new message, post or note (or an email to write), and the app's search, straight from the
 * quick launcher. The same screens the app shortcuts open; each only when the app on the phone
 * accepts it.
 */
class ListedAppCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    private data class Cached(val updatedAt: Long, val commands: List<CommandTarget>)

    override fun getCommands(context: Context): List<CommandTarget> {
        if (!SettingsManager.getQuickLauncherListedAppShortcuts(context)) return emptyList()
        val pm = context.packageManager
        val off = SettingsManager.getQuickLauncherListedAppsOff(context)
        val launchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.distinct().filter { it != context.packageName && it !in off }
        return launchers.flatMap { commandsFor(context, it) }
    }

    /** Whether [packageName] gets Flux Keyboard's shortcuts (whether or not they're turned off for it). */
    fun offersShortcuts(context: Context, packageName: String): Boolean = commandsFor(context, packageName).isNotEmpty()

    private fun commandsFor(context: Context, packageName: String): List<CommandTarget> {
        val preset = AppShortcutPresets.forPackage(packageName)
        val standard = AppEnterStandards.standardFor(packageName)
        val pm = context.packageManager
        val updatedAt = runCatching { pm.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull() ?: return emptyList()
        cache[packageName]?.takeIf { it.updatedAt == updatedAt }?.let { return it.commands }
        // Apps in neither list: what their Play Store category says they are, and only the
        // screens they declare themselves (a search screen, sharing text for a message or note)
        val appCategory = if (preset == null && standard == EnterStandard.AppDefault) {
            runCatching { pm.getApplicationInfo(packageName, 0).category }.getOrNull()
                ?: android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
        } else null

        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(preset?.appName ?: packageName)
        val icon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

        // What "new" is in this app, and the ways into it (the first the app accepts is used)
        val (newLabel, newIntents) = when {
            appCategory == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL ->
                R.string.quick_launcher_action_new_message to listOf(AppIntent.share())
            appCategory == android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY ->
                R.string.quick_launcher_action_new_note to listOf(AppIntent.share())
            appCategory != null -> R.string.quick_launcher_action_new_post to emptyList()
            standard == EnterStandard.Email ->
                R.string.quick_launcher_action_compose to listOf(AppIntent(AppIntent.ACTION_SENDTO, data = "mailto:"))
            standard == EnterStandard.Notes || preset?.category == AppCategory.Productivity ->
                R.string.quick_launcher_action_new_note to (preset?.suggested?.get(StandardShortcut.New) ?: listOf(AppIntent.share()))
            standard == EnterStandard.Chat || preset?.category == AppCategory.Communication ->
                R.string.quick_launcher_action_new_message to (preset?.suggested?.get(StandardShortcut.New) ?: listOf(AppIntent.share()))
            else ->
                R.string.quick_launcher_action_new_post to (preset?.suggested?.get(StandardShortcut.New).orEmpty())
        }
        val searchIntents = preset?.suggested?.get(StandardShortcut.Search) ?: listOf(AppIntent.search())

        val commands = listOfNotNull(
            command(context, packageName, appName, icon, "new", context.getString(newLabel), newIntents),
            command(context, packageName, appName, icon, "search", context.getString(R.string.quick_launcher_action_search), searchIntents)
        )
        cache[packageName] = Cached(updatedAt, commands)
        return commands
    }

    private fun command(
        context: Context,
        packageName: String,
        appName: String,
        icon: android.graphics.drawable.Drawable?,
        key: String,
        label: String,
        candidates: List<AppIntent>
    ): CommandTarget? {
        val intent = candidates.asSequence().map { toIntent(it, packageName) }.firstOrNull { intent ->
            context.packageManager.queryIntentActivities(intent, 0).any {
                it.activityInfo.packageName == packageName && it.activityInfo.exported && it.activityInfo.enabled
            }
        } ?: return null
        return CommandTarget(
            id = "listed:$packageName:$key",
            source = id,
            kind = CommandKind.Shortcut,
            label = label,
            subtitle = appName,
            icon = CommandIcon.DrawableIcon(icon),
            launch = CommandLaunchSpec.IntentUri(
                action = intent.action ?: Intent.ACTION_VIEW,
                packageName = packageName,
                intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
            ),
            capabilities = setOf(CommandCapability.SendsIntent, CommandCapability.RequiresInstalledPackage),
            defaultSurfaces = setOf(CommandSurface.QuickLauncher, CommandSurface.AssignedKey),
            searchTokens = listOf(label, appName, "$appName $label", "$label $appName")
        )
    }

    private fun toIntent(appIntent: AppIntent, packageName: String): Intent =
        Intent(appIntent.action).apply {
            val data = appIntent.data?.let(Uri::parse)
            when {
                data != null && appIntent.type != null -> setDataAndType(data, appIntent.type)
                data != null -> setData(data)
                appIntent.type != null -> setType(appIntent.type)
            }
            appIntent.categories.forEach(::addCategory)
            setPackage(packageName)
        }

    companion object {
        private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    }
}
