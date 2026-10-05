package it.palsoftware.pastiera.shortcuts

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONObject

/**
 * Stored app shortcut settings: the master switch, and per app (by package) whether it is
 * on, its name when you added it yourself, and the shortcuts you changed.
 *
 *   app_shortcuts_apps = { "<package>": { "enabled": false, "name": "…", "map": { "Search": "ctrl:34" | "off" } } }
 */
object AppShortcutSettings {
    private const val TAG = "AppShortcutSettings"
    const val KEY_ENABLED = "app_shortcuts_enabled"
    const val KEY_APPS = "app_shortcuts_apps"
    const val KEY_SUGGESTIONS = "app_shortcuts_suggestions"
    private const val OFF = "off"

    @Volatile private var cachedRaw: String? = null
    @Volatile private var cachedEnabled: Boolean = true
    @Volatile private var cachedSuggestions: Boolean = true
    @Volatile private var cachedConfig: AppShortcutConfig = AppShortcutConfig()

    fun config(context: Context): AppShortcutConfig {
        val prefs = SettingsManager.getPreferences(context)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val suggestions = prefs.getBoolean(KEY_SUGGESTIONS, true)
        val raw = prefs.getString(KEY_APPS, null)
        if (raw == cachedRaw && enabled == cachedEnabled && suggestions == cachedSuggestions) return cachedConfig
        val config = AppShortcutConfig(enabled = enabled, suggestionsEnabled = suggestions, apps = parseApps(raw))
        cachedRaw = raw
        cachedEnabled = enabled
        cachedSuggestions = suggestions
        cachedConfig = config
        return config
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean(KEY_SUGGESTIONS, enabled).apply()
    }

    /** Replaces one app's settings; null forgets the app (a preset app goes back to its preset). */
    fun setApp(context: Context, packageName: String, settings: AppShortcutAppSettings?) {
        val apps = config(context).apps.toMutableMap()
        if (settings == null) apps.remove(packageName) else apps[packageName] = settings
        SettingsManager.getPreferences(context).edit().putString(KEY_APPS, serializeApps(apps)).apply()
    }

    internal fun parseApps(raw: String?): Map<String, AppShortcutAppSettings> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                for (packageName in root.keys()) {
                    val item = root.optJSONObject(packageName) ?: continue
                    val map = item.optJSONObject("map")
                    val overrides = buildMap<StandardShortcut, KeyCombo?> {
                        if (map != null) {
                            for (name in map.keys()) {
                                val shortcut = StandardShortcut.byName(name) ?: continue
                                val value = map.optString(name)
                                if (value == OFF) put(shortcut, null)
                                else KeyCombo.parse(value)?.let { put(shortcut, it) }
                            }
                        }
                    }
                    put(
                        packageName,
                        AppShortcutAppSettings(
                            enabled = item.optBoolean("enabled", true),
                            appName = item.optString("name").takeIf { it.isNotBlank() },
                            overrides = overrides
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "Unreadable app shortcut settings", it)
            emptyMap()
        }
    }

    internal fun serializeApps(apps: Map<String, AppShortcutAppSettings>): String {
        val root = JSONObject()
        apps.forEach { (packageName, settings) ->
            val map = JSONObject()
            settings.overrides.forEach { (shortcut, combo) -> map.put(shortcut.name, combo?.serialize() ?: OFF) }
            root.put(packageName, JSONObject().apply {
                if (!settings.enabled) put("enabled", false)
                settings.appName?.let { put("name", it) }
                if (map.length() > 0) put("map", map)
            })
        }
        return root.toString()
    }
}
