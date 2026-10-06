package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.commands.CommandJson
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.PastieraCommandSource
import it.palsoftware.pastiera.legacy.LegacySettings
import org.json.JSONObject
import it.palsoftware.pastiera.SettingsManager.LauncherShortcut
import it.palsoftware.pastiera.SettingsManager.CommandSourceVisibility
import it.palsoftware.pastiera.SettingsManager.QuickLauncherCommandCustomization

// SettingsManager: launcher shortcuts, Power Shortcuts and the quick launcher. The keys and defaults live in SettingsManager.kt.

/**
 * Sets a key's launcher shortcut (an app).
 */
fun SettingsManager.setLauncherShortcut(context: Context, keyCode: Int, packageName: String, appName: String) {
    setLauncherCommand(
        context = context,
        keyCode = keyCode,
        commandId = "app:$packageName",
        source = CommandSourceId.Apps.storageValue,
        kind = "App",
        title = appName,
        subtitle = packageName,
        launch = CommandLaunchSpec.AppPackage(packageName)
    )
}

fun SettingsManager.setQuickLauncherShortcut(context: Context, keyCode: Int) {
    setLauncherCommand(
        context = context,
        keyCode = keyCode,
        commandId = PastieraCommandSource.COMMAND_QUICK_LAUNCHER,
        source = CommandSourceId.Pastiera.storageValue,
        kind = "PastieraAction",
        title = "${it.palsoftware.pastiera.BuildConfig.APP_NAME} QuickLauncher",
        subtitle = "Open ${it.palsoftware.pastiera.BuildConfig.APP_NAME} search",
        launch = CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
    )
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
        .apply()
}

fun SettingsManager.setLauncherCommand(
    context: Context,
    keyCode: Int,
    commandId: String,
    source: String,
    kind: String,
    title: String,
    subtitle: String?,
    launch: CommandLaunchSpec
) {
    setLauncherAction(
        context,
        keyCode,
        LauncherShortcut(
            type = LauncherShortcut.TYPE_COMMAND,
            packageName = (launch as? CommandLaunchSpec.AppPackage)?.packageName,
            appName = title,
            commandId = commandId,
            commandSource = source,
            commandKind = kind,
            commandTitle = title,
            commandSubtitle = subtitle,
            commandLaunch = launch
        )
    )
}

/**
 * Sets a key's launcher action (any type).
 */
fun SettingsManager.setLauncherAction(context: Context, keyCode: Int, action: LauncherShortcut) {
    val prefs = getPreferences(context)
    val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
    
    try {
        val shortcuts = JSONObject(shortcutsJson)
        if (action.isQuickLauncherCommand()) {
            val keys = shortcuts.keys()
            val keysToRemove = mutableListOf<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                val shortcutObj = shortcuts.optJSONObject(key)
                if (
                    shortcutObj?.optString("type") == LauncherShortcut.TYPE_QUICK_LAUNCHER ||
                    shortcutObj?.optString("commandId") == PastieraCommandSource.COMMAND_QUICK_LAUNCHER
                ) {
                    keysToRemove.add(key)
                }
            }
            keysToRemove.forEach { shortcuts.remove(it) }
        }
        shortcuts.put(keyCode.toString(), JSONObject().apply {
            put("type", action.type)
            if (action.packageName != null) put("packageName", action.packageName)
            if (action.appName != null) put("appName", action.appName)
            if (action.action != null) put("action", action.action)
            if (action.data != null) put("data", action.data)
            if (action.commandId != null) put("commandId", action.commandId)
            if (action.commandSource != null) put("source", action.commandSource)
            if (action.commandKind != null) put("kind", action.commandKind)
            if (action.commandTitle != null) put("title", action.commandTitle)
            if (action.commandSubtitle != null) put("subtitle", action.commandSubtitle)
            if (action.commandLaunch != null) put("launch", CommandJson.launchToJson(action.commandLaunch))
        })
        prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error saving the action for key $keyCode", e)
    }
}

/**
 * Removes a key's launcher shortcut.
 */
fun SettingsManager.removeLauncherShortcut(context: Context, keyCode: Int) {
    val prefs = getPreferences(context)
    val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
    
    try {
        val shortcuts = JSONObject(shortcutsJson)
        shortcuts.remove(keyCode.toString())
        prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error removing the shortcut for key $keyCode", e)
    }
}

/**
 * Swaps two keys' launcher shortcuts (in one write).
 * If one key has none, the other's shortcut moves.
 */
fun SettingsManager.swapLauncherShortcuts(context: Context, fromKeyCode: Int, toKeyCode: Int) {
    val prefs = getPreferences(context)
    val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
    
    try {
        val shortcuts = JSONObject(shortcutsJson)
        
        // Get current shortcuts (if any)
        val fromShortcutObj = shortcuts.optJSONObject(fromKeyCode.toString())
        val toShortcutObj = shortcuts.optJSONObject(toKeyCode.toString())
        
        // Swap: remove both first
        shortcuts.remove(fromKeyCode.toString())
        shortcuts.remove(toKeyCode.toString())
        
        // Add swapped shortcuts
        if (fromShortcutObj != null) {
            shortcuts.put(toKeyCode.toString(), fromShortcutObj)
        }
        if (toShortcutObj != null) {
            shortcuts.put(fromKeyCode.toString(), toShortcutObj)
        }
        
        // Save atomically
        prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error swapping shortcuts between keys $fromKeyCode and $toKeyCode", e)
    }
}

/**
 * All saved launcher shortcuts.
 */
fun SettingsManager.getLauncherShortcuts(context: Context): Map<Int, LauncherShortcut> {
    ensureQuickLauncherDefaultShortcut(context)
    return getLauncherShortcutsRaw(context)
}

private fun SettingsManager.getLauncherShortcutsRaw(context: Context): Map<Int, LauncherShortcut> {
    val prefs = getPreferences(context)
    val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
    val shortcuts = mutableMapOf<Int, LauncherShortcut>()
    
    try {
        val json = JSONObject(shortcutsJson)
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val keyCode = key.toIntOrNull()
            if (keyCode != null) {
                val shortcutObj = json.getJSONObject(key)
                val type = shortcutObj.optString("type", LauncherShortcut.TYPE_APP)
                
                shortcuts[keyCode] = LauncherShortcut(
                    type = type,
                    packageName = shortcutObj.optString("packageName").takeIf { it.isNotEmpty() },
                    appName = shortcutObj.optString("appName").takeIf { it.isNotEmpty() },
                    action = shortcutObj.optString("action").takeIf { it.isNotEmpty() },
                    data = shortcutObj.optString("data").takeIf { it.isNotEmpty() },
                    commandId = shortcutObj.optString("commandId").takeIf { it.isNotEmpty() },
                    commandSource = shortcutObj.optString("source").takeIf { it.isNotEmpty() },
                    commandKind = shortcutObj.optString("kind").takeIf { it.isNotEmpty() },
                    commandTitle = shortcutObj.optString("title").takeIf { it.isNotEmpty() },
                    commandSubtitle = shortcutObj.optString("subtitle").takeIf { it.isNotEmpty() },
                    commandLaunch = CommandJson.launchFromJson(shortcutObj.optJSONObject("launch"))
                        ?: LegacySettings.launcherShortcutLaunchSpec(type, shortcutObj)
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading shortcuts", e)
    }
    
    return shortcuts
}

/**
 * A key's launcher shortcut.
 */
fun SettingsManager.getLauncherShortcut(context: Context, keyCode: Int): LauncherShortcut? {
    return getLauncherShortcuts(context)[keyCode]
}

fun SettingsManager.ensureQuickLauncherDefaultShortcut(context: Context) {
    val prefs = getPreferences(context)
    if (prefs.getBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, false)) {
        return
    }
    val shortcuts = getLauncherShortcutsRaw(context)
    if (shortcuts.values.any { it.isQuickLauncherCommand() }) {
        prefs.edit()
            .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
            .apply()
        return
    }
    if (shortcuts[KeyEvent.KEYCODE_SPACE] != null) {
        return
    }
    setQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
    prefs.edit()
        .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
        .apply()
}

fun SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context: Context): Boolean {
    val prefs = getPreferences(context)
    if (prefs.getBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, false)) {
        return false
    }
    val shortcuts = getLauncherShortcutsRaw(context)
    if (shortcuts.values.any { it.isQuickLauncherCommand() }) {
        return false
    }
    val spaceShortcut = shortcuts[KeyEvent.KEYCODE_SPACE]
    return spaceShortcut != null && !spaceShortcut.isQuickLauncherCommand()
}

fun SettingsManager.getQuickLauncherShortcutKey(context: Context): Int? {
    return getLauncherShortcuts(context)
        .entries
        .firstOrNull { it.value.isQuickLauncherCommand() }
        ?.key
}

fun SettingsManager.isQuickLauncherShortcut(context: Context, keyCode: Int): Boolean {
    return getLauncherShortcut(context, keyCode)?.isQuickLauncherCommand() == true
}

fun SettingsManager.getCommandSourceVisibility(context: Context): List<CommandSourceVisibility> {
    val defaults = defaultCommandSourceVisibility()
    val stored = getPreferences(context).getString(KEY_COMMAND_SURFACE_SOURCES, null) ?: return defaults
    return try {
        val json = JSONObject(stored)
        defaults.map { default ->
            val sourceJson = json.optJSONObject(default.sourceId)
            if (sourceJson == null) {
                default
            } else {
                CommandSourceVisibility(
                    sourceId = default.sourceId,
                    quickLauncherEnabled = sourceJson.optBoolean("quick_launcher", default.quickLauncherEnabled)
                )
            }
        }
    } catch (error: Exception) {
        Log.e(TAG, "Error loading command source visibility", error)
        defaults
    }
}

fun SettingsManager.setCommandSourceVisibility(context: Context, visibility: List<CommandSourceVisibility>) {
    val json = JSONObject()
    visibility.forEach { item ->
        json.put(item.sourceId, JSONObject().apply {
            put("quick_launcher", item.quickLauncherEnabled)
        })
    }
    getPreferences(context).edit()
        .putString(KEY_COMMAND_SURFACE_SOURCES, json.toString())
        .apply()
}

fun SettingsManager.isCommandSourceEnabled(context: Context, sourceId: String, surface: CommandSurface): Boolean {
    if (surface != CommandSurface.QuickLauncher) return true
    val visibility = getCommandSourceVisibility(context).firstOrNull { it.sourceId == sourceId }
        ?: defaultCommandSourceVisibility().firstOrNull { it.sourceId == sourceId }
        ?: return false
    return visibility.quickLauncherEnabled
}

private fun SettingsManager.defaultCommandSourceVisibility(): List<CommandSourceVisibility> {
    return listOf(
        CommandSourceVisibility(CommandSourceId.Apps.storageValue, quickLauncherEnabled = true),
        CommandSourceVisibility(CommandSourceId.Pastiera.storageValue, quickLauncherEnabled = true),
        CommandSourceVisibility(CommandSourceId.AppActions.storageValue, quickLauncherEnabled = true),
        CommandSourceVisibility(CommandSourceId.DeviceControl.storageValue, quickLauncherEnabled = true),
        CommandSourceVisibility(CommandSourceId.NavActions.storageValue, quickLauncherEnabled = true)
    )
}

fun SettingsManager.getQuickLauncherCommandCustomizations(context: Context): Map<String, QuickLauncherCommandCustomization> {
    val stored = getPreferences(context).getString(KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS, null)
        ?: return emptyMap()
    return try {
        val json = JSONObject(stored)
        val result = mutableMapOf<String, QuickLauncherCommandCustomization>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val commandId = keys.next()
            val item = json.optJSONObject(commandId) ?: continue
            result[commandId] = QuickLauncherCommandCustomization(
                commandId = commandId,
                favorite = item.optBoolean("favorite", false),
                hidden = item.optBoolean("hidden", false),
                customSearch = item.optString("custom_search", ""),
                favoriteOrder = item.optInt("favorite_order", Int.MAX_VALUE),
                color = if (item.has("color")) item.optInt("color") else null
            )
        }
        result
    } catch (error: Exception) {
        Log.e(TAG, "Error loading quick launcher command customisations", error)
        emptyMap()
    }
}

fun SettingsManager.setQuickLauncherCommandCustomization(
    context: Context,
    customization: QuickLauncherCommandCustomization
) {
    val current = getQuickLauncherCommandCustomizations(context).toMutableMap()
    if (
        !customization.favorite &&
        !customization.hidden &&
        customization.customSearch.isBlank() &&
        customization.favoriteOrder == Int.MAX_VALUE &&
        customization.color == null
    ) {
        current.remove(customization.commandId)
    } else {
        current[customization.commandId] = customization.copy(customSearch = customization.customSearch.trim())
    }
    val json = JSONObject()
    current.values.sortedBy { it.commandId }.forEach { item ->
        json.put(item.commandId, JSONObject().apply {
            if (item.favorite) put("favorite", true)
            if (item.hidden) put("hidden", true)
            if (item.customSearch.isNotBlank()) put("custom_search", item.customSearch)
            if (item.favoriteOrder != Int.MAX_VALUE) put("favorite_order", item.favoriteOrder)
            item.color?.let { put("color", it) }
        })
    }
    getPreferences(context).edit()
        .putString(KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS, json.toString())
        .apply()
}

fun SettingsManager.getQuickLauncherHighlightFavorites(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES, true)
}

fun SettingsManager.setQuickLauncherHighlightFavorites(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherFavoriteColor(context: Context): Int {
    return getPreferences(context).getInt(KEY_QUICK_LAUNCHER_FAVORITE_COLOR, DEFAULT_QUICK_LAUNCHER_FAVORITE_COLOR)
}

fun SettingsManager.setQuickLauncherFavoriteColor(context: Context, color: Int) {
    getPreferences(context).edit()
        .putInt(KEY_QUICK_LAUNCHER_FAVORITE_COLOR, color)
        .apply()
}

fun SettingsManager.getQuickLauncherIconColors(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_ICON_COLORS, false)
}

fun SettingsManager.setQuickLauncherIconColors(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_ICON_COLORS, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherShowAliasFirst(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST, true)
}

fun SettingsManager.setQuickLauncherShowAliasFirst(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherStaticTopHighlight(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, false)
}

fun SettingsManager.setQuickLauncherStaticTopHighlight(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherStaticTopHighlightColor(context: Context): Int {
    return getPreferences(context).getInt(
        KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR,
        DEFAULT_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR
    )
}

fun SettingsManager.setQuickLauncherStaticTopHighlightColor(context: Context, color: Int) {
    getPreferences(context).edit()
        .putInt(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR, color)
        .apply()
}

/**
 * Whether launcher shortcuts are on.
 */
fun SettingsManager.getLauncherShortcutsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_LAUNCHER_SHORTCUTS_ENABLED, DEFAULT_LAUNCHER_SHORTCUTS_ENABLED)
}

/**
 * Turns launcher shortcuts on or off.
 */
fun SettingsManager.setLauncherShortcutsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_LAUNCHER_SHORTCUTS_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherAutoStartSingle(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_AUTO_START_SINGLE,
        DEFAULT_QUICK_LAUNCHER_AUTO_START_SINGLE
    )
}

fun SettingsManager.setQuickLauncherAutoStartSingle(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_AUTO_START_SINGLE, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherLimitResults(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_LIMIT_RESULTS,
        DEFAULT_QUICK_LAUNCHER_LIMIT_RESULTS
    )
}

fun SettingsManager.setQuickLauncherLimitResults(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_LIMIT_RESULTS, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherTextFieldShortcuts(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS,
        DEFAULT_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS
    )
}

fun SettingsManager.setQuickLauncherTextFieldShortcuts(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS, enabled)
        .apply()
}

/**
 * Stable: full releases only (0.92). Dev: also each dev build (0.93-flux.<time>). Until
 * chosen, it follows the installed build: a dev build stays on dev builds, a release on releases.
 */
fun SettingsManager.getForkUpdateChannel(context: Context): String =
    when (getPreferences(context).getString(KEY_FORK_UPDATE_CHANNEL, null)) {
        FORK_UPDATE_CHANNEL_DEV -> FORK_UPDATE_CHANNEL_DEV
        FORK_UPDATE_CHANNEL_STABLE -> FORK_UPDATE_CHANNEL_STABLE
        else -> if (BuildConfig.VERSION_NAME.contains("-flux.")) FORK_UPDATE_CHANNEL_DEV else FORK_UPDATE_CHANNEL_STABLE
    }

/**
 * A dev build's choices stay when it updates to a full release: the Dev update channel and
 * Developer options, which otherwise follow the installed build, are kept as settings. Also
 * for a release installed over a dev build, while What's new still remembers the dev build.
 */
fun SettingsManager.keepDevBuildChoices(context: Context) {
    val prefs = getPreferences(context)
    val onDevBuild = BuildConfig.VERSION_NAME.contains("-flux.") ||
        prefs.getString(KEY_LAST_SEEN_WHATS_NEW_VERSION, null)?.contains("-flux.") == true
    if (!onDevBuild) return
    val edit = prefs.edit()
    if (!prefs.contains(KEY_FORK_UPDATE_CHANNEL)) edit.putString(KEY_FORK_UPDATE_CHANNEL, FORK_UPDATE_CHANNEL_DEV)
    if (!prefs.contains(KEY_DEVELOPER_OPTIONS_ENABLED)) edit.putBoolean(KEY_DEVELOPER_OPTIONS_ENABLED, true)
    edit.apply()
}

fun SettingsManager.setForkUpdateChannel(context: Context, channel: String) {
    getPreferences(context).edit().putString(KEY_FORK_UPDATE_CHANNEL, channel).apply()
}

/** Flux Keyboard's own shortcuts (New message, Search) for the apps in its shortcut and Enter lists. */
/** Apps whose Flux Keyboard shortcuts you turned off (from their long-press menu). */
fun SettingsManager.getQuickLauncherListedAppsOff(context: Context): Set<String> =
    getPreferences(context).getStringSet("quick_launcher_listed_apps_off", null).orEmpty()

fun SettingsManager.setQuickLauncherListedAppOff(context: Context, packageName: String, off: Boolean) {
    val current = getQuickLauncherListedAppsOff(context)
    getPreferences(context).edit()
        .putStringSet("quick_launcher_listed_apps_off", if (off) current + packageName else current - packageName)
        .apply()
}

fun SettingsManager.getQuickLauncherListedAppShortcuts(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_LISTED_APP_SHORTCUTS, true)

fun SettingsManager.setQuickLauncherListedAppShortcuts(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_QUICK_LAUNCHER_LISTED_APP_SHORTCUTS, enabled).apply()
}

/** Apps' own launcher shortcuts ("New message") are quick launcher results. */
fun SettingsManager.getQuickLauncherAppShortcuts(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_APP_SHORTCUTS, true)

fun SettingsManager.setQuickLauncherAppShortcuts(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_QUICK_LAUNCHER_APP_SHORTCUTS, enabled).apply()
}

/** Back from Niagara's search, before opening anything, returns to the app it was opened from. */
fun SettingsManager.getNiagaraBackReturns(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_NIAGARA_BACK_RETURNS, true)

fun SettingsManager.setNiagaraBackReturns(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_NIAGARA_BACK_RETURNS, enabled).apply()
}

fun SettingsManager.getQuickLauncherAltSpaceInTextFields(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS,
        DEFAULT_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS
    )
}

fun SettingsManager.setQuickLauncherAltSpaceInTextFields(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherAltShortcutsOutsideTextFields(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS,
        DEFAULT_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS
    )
}

fun SettingsManager.setQuickLauncherAltShortcutsOutsideTextFields(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherRespectKeyboardLayout(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT,
        DEFAULT_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT
    )
}

fun SettingsManager.setQuickLauncherRespectKeyboardLayout(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherTypoTolerantRanking(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING,
        DEFAULT_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING
    )
}

fun SettingsManager.setQuickLauncherTypoTolerantRanking(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherWidthPercent(context: Context): Int {
    return getPreferences(context)
        .getInt(KEY_QUICK_LAUNCHER_WIDTH_PERCENT, DEFAULT_QUICK_LAUNCHER_WIDTH_PERCENT)
        .coerceIn(50, 100)
}

fun SettingsManager.setQuickLauncherWidthPercent(context: Context, percent: Int) {
    getPreferences(context).edit()
        .putInt(KEY_QUICK_LAUNCHER_WIDTH_PERCENT, percent.coerceIn(50, 100))
        .apply()
}

fun SettingsManager.getQuickLauncherPillMode(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_QUICK_LAUNCHER_PILL_MODE,
        DEFAULT_QUICK_LAUNCHER_PILL_MODE
    )
}

fun SettingsManager.setQuickLauncherPillMode(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_QUICK_LAUNCHER_PILL_MODE, enabled)
        .apply()
}

fun SettingsManager.getQuickLauncherAnimationDurationMs(context: Context): Int {
    return getPreferences(context)
        .getInt(KEY_QUICK_LAUNCHER_ANIMATION_DURATION_MS, DEFAULT_QUICK_LAUNCHER_ANIMATION_DURATION_MS)
        .coerceIn(QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS, QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS)
}

fun SettingsManager.setQuickLauncherAnimationDurationMs(context: Context, durationMs: Int) {
    getPreferences(context).edit()
        .putInt(
            KEY_QUICK_LAUNCHER_ANIMATION_DURATION_MS,
            durationMs.coerceIn(
                QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS,
                QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS
            )
        )
        .apply()
}

fun SettingsManager.getQuickLauncherBehavior(context: Context): String {
    val value = getPreferences(context).getString(
        KEY_QUICK_LAUNCHER_BEHAVIOR,
        QUICK_LAUNCHER_BEHAVIOR_PASTIERA
    ) ?: QUICK_LAUNCHER_BEHAVIOR_PASTIERA
    return normalizeQuickLauncherBehavior(value)
}

fun SettingsManager.setQuickLauncherBehavior(context: Context, behavior: String) {
    getPreferences(context).edit()
        .putString(KEY_QUICK_LAUNCHER_BEHAVIOR, normalizeQuickLauncherBehavior(behavior))
        .apply()
}

private fun SettingsManager.normalizeQuickLauncherBehavior(behavior: String): String {
    return when (behavior.trim().lowercase()) {
        QUICK_LAUNCHER_BEHAVIOR_NIAGARA -> QUICK_LAUNCHER_BEHAVIOR_NIAGARA
        else -> QUICK_LAUNCHER_BEHAVIOR_PASTIERA
    }
}

/**
 * Whether Power Shortcuts are on.
 */
fun SettingsManager.getPowerShortcutsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_POWER_SHORTCUTS_ENABLED, DEFAULT_POWER_SHORTCUTS_ENABLED)
}

/**
 * Turns Power Shortcuts on or off.
 */
fun SettingsManager.setPowerShortcutsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_POWER_SHORTCUTS_ENABLED, enabled)
        .apply()
}
