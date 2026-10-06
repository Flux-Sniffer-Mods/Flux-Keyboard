package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.legacy.LegacySettings
import org.json.JSONArray
import org.json.JSONObject
import it.palsoftware.pastiera.SettingsManager.AppEnterBehaviorOverride

// SettingsManager: per-app behaviour: hidden apps, terminal mode, exact typing and the Enter key. The keys and defaults live in SettingsManager.kt.

/** Package names from free text (one per line, or separated by spaces, commas or semicolons). */
fun SettingsManager.parsePackageList(text: String): List<String> =
    text.split(Regex("[\\s,;]+"))
        .map { it.trim() }
        .filter { PACKAGE_NAME_REGEX.matches(it) }
        .distinct()

/**
 * Apps where the keyboard shows nothing and leaves every key to the app, e.g. an X11 desktop
 * such as Termux:X11 that handles the keyboard itself.
 */
/** Niagara Launcher by default: its own search reads keys directly, so the keyboard stays out of sight. */
fun SettingsManager.getHiddenKeyboardApps(context: Context): List<String> =
    parsePackageList(getPreferences(context).getString(KEY_HIDDEN_KEYBOARD_APPS, DEFAULT_HIDDEN_KEYBOARD_APPS) ?: "")

fun SettingsManager.setHiddenKeyboardApps(context: Context, packages: Collection<String>) {
    val clean = packages.joinToString("\n").let(::parsePackageList)
    getPreferences(context).edit()
        .putString(KEY_HIDDEN_KEYBOARD_APPS, clean.joinToString("\n"))
        .apply()
}

/**
 * Terminal mode: in these apps (Termux unless changed) the terminal is typed into like a
 * text field without smart features, so Alt and SYM type the keyboard's symbols, and every Ctrl
 * (held, tapped or latched) reaches the terminal as a real Ctrl.
 */
fun SettingsManager.getTerminalModeEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TERMINAL_MODE_ENABLED, true)

/**
 * Terminal mode keeps the keyboard out of sight, as for the Linux desktop: Alt and SYM still type
 * The keyboard's characters, only the clipboard and emoji picker show while open.
 */
fun SettingsManager.getTerminalModeHideKeyboard(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TERMINAL_MODE_HIDE_KEYBOARD, true)

fun SettingsManager.setTerminalModeHideKeyboard(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TERMINAL_MODE_HIDE_KEYBOARD, enabled).apply()
}

/**
 * Minimal mode: in every app the keyboard stays out of sight while its keys work as usual (Alt
 * layer, SYM, Ctrl shortcuts, corrections); the emoji and symbols pages still open on their keys.
 */
fun SettingsManager.getMinimalMode(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_MINIMAL_MODE, false)

fun SettingsManager.setMinimalMode(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_MINIMAL_MODE, enabled).apply()
}

/** In minimal mode, the status LEDs still show (on until changed). */
fun SettingsManager.getMinimalModeShowLeds(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_MINIMAL_MODE_SHOW_LEDS, true)

fun SettingsManager.setMinimalModeShowLeds(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_MINIMAL_MODE_SHOW_LEDS, enabled).apply()
}

/** In a terminal, swipes on the keys move its cursor (left, right) and recall commands (up, down). On until changed. */
fun SettingsManager.getTerminalModeSwipeCursor(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TERMINAL_MODE_SWIPE_CURSOR, true)

fun SettingsManager.setTerminalModeSwipeCursor(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TERMINAL_MODE_SWIPE_CURSOR, enabled).apply()
}

/** With the keyboard hidden in terminals, its status LEDs still show (on until changed). */
fun SettingsManager.getTerminalModeShowLeds(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TERMINAL_MODE_SHOW_LEDS, true)

fun SettingsManager.setTerminalModeShowLeds(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TERMINAL_MODE_SHOW_LEDS, enabled).apply()
}

/** What the emoji key does in terminal mode, a TerminalMode.EmojiKeyAction id (the previous command until chosen). */
fun SettingsManager.getTerminalModeEmojiKeyAction(context: Context): String =
    getPreferences(context).getString(KEY_TERMINAL_MODE_EMOJI_KEY, "up") ?: "up"

fun SettingsManager.setTerminalModeEmojiKeyAction(context: Context, id: String) {
    getPreferences(context).edit().putString(KEY_TERMINAL_MODE_EMOJI_KEY, id).apply()
}

fun SettingsManager.setTerminalModeEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TERMINAL_MODE_ENABLED, enabled).apply()
}

fun SettingsManager.getTerminalModeApps(context: Context): List<String> {
    val prefs = getPreferences(context)
    val stored = prefs.getString(KEY_TERMINAL_MODE_APPS, null) ?: return listOf(TERMUX_PACKAGE)
    val apps = parsePackageList(stored)
    // Termux joins a list saved before it was the default, once (removing it later sticks)
    if (!prefs.getBoolean(KEY_TERMINAL_MODE_TERMUX_ADDED, false)) {
        val withTermux = if (TERMUX_PACKAGE in apps) apps else listOf(TERMUX_PACKAGE) + apps
        prefs.edit()
            .putString(KEY_TERMINAL_MODE_APPS, withTermux.joinToString("\n"))
            .putBoolean(KEY_TERMINAL_MODE_TERMUX_ADDED, true)
            .apply()
        return withTermux
    }
    return apps
}

fun SettingsManager.setTerminalModeApps(context: Context, packages: Collection<String>) {
    val clean = packages.joinToString("\n").let(::parsePackageList)
    getPreferences(context).edit()
        .putString(KEY_TERMINAL_MODE_APPS, clean.joinToString("\n"))
        .putBoolean(KEY_TERMINAL_MODE_TERMUX_ADDED, true)
        .apply()
}

/** Apps where the keyboard types exactly what you key: no auto-correct, replacements or auto-capitals. */
fun SettingsManager.getExactTypingApps(context: Context): List<String> =
    parsePackageList(getPreferences(context).getString(KEY_EXACT_TYPING_APPS, "") ?: "")

fun SettingsManager.setExactTypingApps(context: Context, packages: Collection<String>) {
    val clean = packages.joinToString("\n").let(::parsePackageList)
    getPreferences(context).edit().putString(KEY_EXACT_TYPING_APPS, clean.joinToString("\n")).apply()
}

/** Fields that ask for no suggestions (TYPE_TEXT_FLAG_NO_SUGGESTIONS) also get exact typing. Off by default. */
fun SettingsManager.getExactTypingForNoSuggestionFields(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EXACT_TYPING_NO_SUGGESTIONS, false)

fun SettingsManager.setExactTypingForNoSuggestionFields(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EXACT_TYPING_NO_SUGGESTIONS, enabled).apply()
}

/** Exact typing applies to this field: its app is on the list, or it asks for no suggestions and that's honoured. */
fun SettingsManager.isExactTypingField(context: Context, packageName: String?, inputType: Int): Boolean =
    (!packageName.isNullOrBlank() && packageName in getExactTypingApps(context)) ||
        (getExactTypingForNoSuggestionFields(context) &&
            (inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 &&
            // A field that also asks for auto-correct wants smart typing, whatever its
            // no-suggestions flag says (Instagram's message box asks for both)
            (inputType and android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) == 0)

/** Terminal mode applies to [packageName] (never while the keyboard is hidden for it). */
fun SettingsManager.isTerminalModeApp(context: Context, packageName: String?): Boolean =
    !packageName.isNullOrBlank() &&
        getTerminalModeEnabled(context) &&
        packageName in getTerminalModeApps(context) &&
        !isKeyboardHiddenForApp(context, packageName)

fun SettingsManager.isKeyboardHiddenForApp(context: Context, packageName: String?): Boolean =
    !packageName.isNullOrBlank() && packageName in getHiddenKeyboardApps(context)

/**
 * Hidden apps with a per-app option on. Until the first per-app change, the earlier global
 * switch ([legacyKey]) still applies to every app that was hidden.
 */
private fun SettingsManager.hiddenAppsWithOption(context: Context, key: String, legacyKey: String): Set<String> {
    val prefs = getPreferences(context)
    if (!prefs.contains(key)) {
        // Until chosen: Niagara keeps its LEDs and the emoji and symbols panels, for quick
        // replies from its notifications
        return if (prefs.getBoolean(legacyKey, false)) getHiddenKeyboardApps(context).toSet()
        else setOf("bitpit.launcher").intersect(getHiddenKeyboardApps(context).toSet())
    }
    return parsePackageList(prefs.getString(key, "").orEmpty()).toSet()
}

private fun SettingsManager.setHiddenAppOption(context: Context, key: String, legacyKey: String, packageName: String, enabled: Boolean) {
    val apps = hiddenAppsWithOption(context, key, legacyKey).toMutableSet()
    if (enabled) apps += packageName else apps -= packageName
    getPreferences(context).edit()
        .putString(key, apps.sorted().joinToString("\n"))
        .remove(legacyKey)
        .apply()
}

/** This hidden app keeps the modifier LEDs visible, drawn over it. */
fun SettingsManager.hiddenAppShowsLeds(context: Context, packageName: String?): Boolean =
    !packageName.isNullOrBlank() &&
        packageName in hiddenAppsWithOption(context, KEY_HIDDEN_APPS_LEDS, LegacySettings.KEY_HIDDEN_APPS_SHOW_LEDS)

fun SettingsManager.setHiddenAppShowsLeds(context: Context, packageName: String, enabled: Boolean) =
    setHiddenAppOption(context, KEY_HIDDEN_APPS_LEDS, LegacySettings.KEY_HIDDEN_APPS_SHOW_LEDS, packageName, enabled)

/** In this hidden app, the emoji picker key and Sym still open the keyboard's emoji and symbols. */
fun SettingsManager.hiddenAppAllowsPanels(context: Context, packageName: String?): Boolean =
    !packageName.isNullOrBlank() &&
        packageName in hiddenAppsWithOption(context, KEY_HIDDEN_APPS_PANELS, LegacySettings.KEY_HIDDEN_APPS_ALLOW_PANELS)

fun SettingsManager.setHiddenAppAllowsPanels(context: Context, packageName: String, enabled: Boolean) =
    setHiddenAppOption(context, KEY_HIDDEN_APPS_PANELS, LegacySettings.KEY_HIDDEN_APPS_ALLOW_PANELS, packageName, enabled)

fun SettingsManager.getAppEnterBehaviorEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_APP_ENTER_BEHAVIOR_ENABLED, true)
}

fun SettingsManager.setAppEnterBehaviorEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_APP_ENTER_BEHAVIOR_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getAppEnterBehaviorPreset(context: Context): String {
    val stored = getPreferences(context).getString(
        KEY_APP_ENTER_BEHAVIOR_PRESET,
        ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE
    ) ?: ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE
    return normalizeEnterBehaviorPreset(stored)
}

fun SettingsManager.setAppEnterBehaviorPreset(context: Context, preset: String) {
    getPreferences(context).edit()
        .putString(KEY_APP_ENTER_BEHAVIOR_PRESET, normalizeEnterBehaviorPreset(preset))
        .apply()
}

fun SettingsManager.getAppEnterBehaviorOverrides(context: Context): List<AppEnterBehaviorOverride> {
    val stored = getPreferences(context).getString(KEY_APP_ENTER_BEHAVIOR_OVERRIDES, null)
        ?: return emptyList()
    return runCatching {
        val array = JSONArray(stored)
        buildList {
            val seen = mutableSetOf<String>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val packageName = item.optString("packageName", "")
                if (packageName.isBlank() || !seen.add(packageName)) continue
                add(
                    AppEnterBehaviorOverride(
                        packageName = packageName,
                        behavior = normalizeEnterBehavior(item.optString("behavior", ENTER_BEHAVIOR_APP_DEFAULT)),
                        sendStrategy = normalizeEnterSendStrategy(
                            item.optString("sendStrategy", ENTER_SEND_STRATEGY_AUTO)
                        ),
                        additionalSendShortcut = normalizeEnterAdditionalSendShortcut(
                            item.optString("additionalSendShortcut", ENTER_ADDITIONAL_SEND_SHORTCUT_NONE)
                        )
                    )
                )
            }
        }
    }.getOrElse {
        Log.e(TAG, "Error loading app enter behavior overrides", it)
        emptyList()
    }
}

fun SettingsManager.setAppEnterBehaviorOverrides(context: Context, overrides: List<AppEnterBehaviorOverride>) {
    val array = JSONArray()
    overrides
        .filter { it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
        .forEach { override ->
            array.put(
                JSONObject().apply {
                    put("packageName", override.packageName)
                    put("behavior", normalizeEnterBehavior(override.behavior))
                    put("sendStrategy", normalizeEnterSendStrategy(override.sendStrategy))
                    put("additionalSendShortcut", normalizeEnterAdditionalSendShortcut(override.additionalSendShortcut))
                }
            )
        }
    getPreferences(context).edit()
        .putString(KEY_APP_ENTER_BEHAVIOR_OVERRIDES, array.toString())
        .apply()
}

private fun SettingsManager.normalizeEnterBehaviorPreset(preset: String): String {
    return when (preset) {
        ENTER_BEHAVIOR_PRESET_APP_DEFAULT,
        ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE,
        ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND,
        ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_SHIFT_SEND,
        ENTER_BEHAVIOR_PRESET_CUSTOM -> preset
        else -> ENTER_BEHAVIOR_PRESET_APP_DEFAULT
    }
}

private fun SettingsManager.normalizeEnterBehavior(behavior: String): String {
    return when (behavior) {
        ENTER_BEHAVIOR_APP_DEFAULT,
        ENTER_BEHAVIOR_ENTER_NEWLINE,
        ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
        ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND,
        ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND -> behavior
        else -> ENTER_BEHAVIOR_APP_DEFAULT
    }
}

private fun SettingsManager.normalizeEnterSendStrategy(strategy: String): String {
    return when (strategy) {
        ENTER_SEND_STRATEGY_AUTO,
        ENTER_SEND_STRATEGY_EDITOR_ACTION,
        ENTER_SEND_STRATEGY_CTRL_ENTER,
        ENTER_SEND_STRATEGY_PLAIN_ENTER -> strategy
        else -> ENTER_SEND_STRATEGY_AUTO
    }
}

private fun SettingsManager.normalizeEnterAdditionalSendShortcut(shortcut: String): String {
    return when (shortcut) {
        ENTER_ADDITIONAL_SEND_SHORTCUT_NONE,
        ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER -> shortcut
        else -> ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    }
}
