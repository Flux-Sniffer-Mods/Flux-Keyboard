package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.legacy.LegacySettings
import org.json.JSONArray
import org.json.JSONObject

// SettingsManager: keyboard layouts, input styles, their suggestion languages and layout switching. The keys and defaults live in SettingsManager.kt.

/**
 * Returns the selected keyboard layout name.
 */
fun SettingsManager.getKeyboardLayout(context: Context): String {
    return getPreferences(context).getString(KEY_KEYBOARD_LAYOUT, DEFAULT_KEYBOARD_LAYOUT) ?: DEFAULT_KEYBOARD_LAYOUT
}

/**
 * Sets the keyboard layout name.
 */
fun SettingsManager.setKeyboardLayout(context: Context, layoutName: String) {
    getPreferences(context).edit()
        .putString(KEY_KEYBOARD_LAYOUT, layoutName)
        .apply()
}

/**
 * Returns whether keyboard layout should be resolved automatically from subtype/locale mapping.
 * If false, the manually selected layout is used across all locales.
 */
fun SettingsManager.isKeyboardLayoutAutoByLocale(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_KEYBOARD_LAYOUT_AUTO_BY_LOCALE,
        DEFAULT_KEYBOARD_LAYOUT_AUTO_BY_LOCALE
    )
}

/**
 * Enables/disables automatic keyboard layout resolution by locale mapping.
 */
fun SettingsManager.setKeyboardLayoutAutoByLocale(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_KEYBOARD_LAYOUT_AUTO_BY_LOCALE, enabled)
        .apply()
}

fun SettingsManager.notifyKeyboardLayoutAutoMappingUpdated(context: Context) {
    getPreferences(context).edit()
        .putLong(KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED, System.currentTimeMillis())
        .apply()
}

/**
 * Returns the manual physical keyboard profile override used for device-specific mappings.
 * Supported values: auto, key2, Q25, titan, titan2, titan2elite_qwerty, mp01,
 * clicks_razr, clicks_pixel, clicks_power.
 */
fun SettingsManager.getPhysicalKeyboardProfileOverride(context: Context): String {
    val value = getPreferences(context).getString(
        KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE,
        DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
    ) ?: DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
    return normalizePhysicalKeyboardProfileOverride(value)
}

/**
 * Sets the manual physical keyboard profile override used for device-specific mappings.
 * Invalid values are normalized to "auto".
 */
fun SettingsManager.setPhysicalKeyboardProfileOverride(context: Context, profile: String) {
    val normalized = normalizePhysicalKeyboardProfileOverride(profile)
    getPreferences(context).edit()
        .putString(KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE, normalized)
        .apply()
}

/**
 * Returns the symbol used for dedicated hardware currency keys.
 */
fun SettingsManager.getPhysicalKeyboardCurrencySymbol(context: Context): String {
    // Until chosen: the phone's own currency (its region's), when it's one of ours
    val value = getPreferences(context).getString(KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL, null)
        ?: return localCurrencySymbol(context)
    return normalizePhysicalKeyboardCurrencySymbol(value)
}

private fun SettingsManager.localCurrencySymbol(context: Context): String {
    val code = runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode }.getOrNull()
    return currencySymbolForCode(code)
        // A region without its own (or none set): the language version's usual currency
        ?: EDITION_CURRENCY[LanguageEdition.language(context)]
        ?: DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
}

private fun currencySymbolForCode(code: String?): String? = when (code) {
    "USD", "CAD", "AUD", "NZD", "MXN", "SGD", "HKD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY", "CNY" -> "¥"
    "INR" -> "₹"
    "RUB" -> "₽"
    "PLN" -> "zł"
    "UAH" -> "₴"
    "SEK", "NOK", "DKK", "ISK" -> "kr"
    "HUF" -> "Ft"
    "CZK" -> "Kč"
    "TRY" -> "₺"
    "AMD" -> "֏"
    "VND" -> "₫"
    "KRW" -> "₩"
    "BRL" -> "R$"
    "ILS" -> "₪"
    "RON" -> "lei"
    "CHF" -> "CHF"
    else -> null
}

/** Each language version's usual currency, for a phone whose region doesn't say. */
private val EDITION_CURRENCY = mapOf(
    "de" to "€", "fr" to "€", "it" to "€", "es" to "€", "nl" to "€", "pt" to "€", "el" to "€",
    "fi" to "€", "pl" to "zł", "ru" to "₽", "uk" to "₴", "da" to "kr", "no" to "kr", "sv" to "kr",
    "hu" to "Ft", "cs" to "Kč", "tr" to "₺", "hy" to "֏", "vi" to "₫", "en" to "$"
)

/**
 * The symbols page's defaults made yours: N holds your currency (Keyboard > Currency
 * symbol), M a second one, $ or, when yours is $, €.
 */
fun SettingsManager.personaliseSymbolsDefaults(context: Context, mappings: Map<Int, String>): Map<Int, String> {
    val currency = getPhysicalKeyboardCurrencySymbol(context)
    return mappings.mapValues { (keyCode, symbol) ->
        when {
            symbol == "\u00A4" -> currency
            // Two of the same would waste a key
            keyCode == KeyEvent.KEYCODE_M && symbol == "$" && currency == "$" -> "€"
            else -> symbol
        }
    }
}

/**
 * Sets the symbol used for dedicated hardware currency keys.
 */
fun SettingsManager.setPhysicalKeyboardCurrencySymbol(context: Context, symbol: String) {
    getPreferences(context).edit()
        .putString(KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL, normalizePhysicalKeyboardCurrencySymbol(symbol))
        .apply()
}

/**
 * Returns whether Alt+Shift shortcut for keyboard layout cycling is enabled.
 */
fun SettingsManager.isAltShiftLayoutSwitchEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_ALT_SHIFT_LAYOUT_SWITCH,
        DEFAULT_ALT_SHIFT_LAYOUT_SWITCH
    )
}

/**
 * Keeps Alt+Shift enabled for existing installations while using the safer disabled
 * default for installations created after this migration was introduced.
 */
fun SettingsManager.initializeAltShiftLayoutSwitchDefault(context: Context) {
    val preferences = getPreferences(context)
    if (preferences.contains(KEY_ALT_SHIFT_DEFAULT_INITIALIZED)) return

    val existingInstallation = preferences.all.isNotEmpty()
    val editor = preferences.edit()
    if (!preferences.contains(KEY_ALT_SHIFT_LAYOUT_SWITCH)) {
        editor.putBoolean(KEY_ALT_SHIFT_LAYOUT_SWITCH, existingInstallation)
    }
    editor.putBoolean(KEY_ALT_SHIFT_DEFAULT_INITIALIZED, true).apply()
}

/**
 * Enables/disables Alt+Shift shortcut for keyboard layout cycling.
 */
fun SettingsManager.setAltShiftLayoutSwitchEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ALT_SHIFT_LAYOUT_SWITCH, enabled)
        .apply()
}

/**
 * Returns whether Alt+Enter shortcut for keyboard layout cycling is enabled.
 */
fun SettingsManager.isAltEnterLayoutSwitchEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_ALT_ENTER_LAYOUT_SWITCH,
        DEFAULT_ALT_ENTER_LAYOUT_SWITCH
    )
}

/**
 * Enables/disables Alt+Enter shortcut for keyboard layout cycling.
 */
fun SettingsManager.setAltEnterLayoutSwitchEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ALT_ENTER_LAYOUT_SWITCH, enabled)
        .apply()
}

/**
 * Returns whether Ctrl+Space shortcut for keyboard layout cycling is enabled.
 */
fun SettingsManager.isCtrlSpaceLayoutSwitchEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_CTRL_SPACE_LAYOUT_SWITCH,
        DEFAULT_CTRL_SPACE_LAYOUT_SWITCH
    )
}

/**
 * Enables/disables Ctrl+Space shortcut for keyboard layout cycling.
 */
fun SettingsManager.setCtrlSpaceLayoutSwitchEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CTRL_SPACE_LAYOUT_SWITCH, enabled)
        .apply()
}

/**
 * Returns whether toast notification on layout switch is enabled.
 */
fun SettingsManager.isToastOnLayoutSwitchEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_TOAST_ON_LAYOUT_SWITCH,
        DEFAULT_TOAST_ON_LAYOUT_SWITCH
    )
}

/**
 * Enables/disables toast notification on layout switch.
 */
fun SettingsManager.setToastOnLayoutSwitchEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TOAST_ON_LAYOUT_SWITCH, enabled)
        .apply()
}

private fun SettingsManager.normalizePhysicalKeyboardProfileOverride(profile: String?): String {
    val normalized = profile?.trim().orEmpty()
    return when {
        normalized.equals("auto", ignoreCase = true) -> "auto"
        normalized.equals("key2", ignoreCase = true) -> "key2"
        normalized.equals("q25", ignoreCase = true) -> "Q25"
        normalized.equals("titan", ignoreCase = true) -> "titan"
        normalized.equals("titan2", ignoreCase = true) -> "titan2"
        normalized.equals("titan2elite_qwerty", ignoreCase = true) -> "titan2elite_qwerty"
        normalized.equals("mp01", ignoreCase = true) -> "mp01"
        normalized.equals("clicks_razr", ignoreCase = true) -> "clicks_razr"
        normalized.equals("clicks_pixel", ignoreCase = true) -> "clicks_pixel"
        normalized.equals("clicks_power", ignoreCase = true) -> "clicks_power"
        else -> DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
    }
}

private fun SettingsManager.normalizePhysicalKeyboardCurrencySymbol(symbol: String?): String {
    val normalized = symbol?.trim().orEmpty()
    return if (normalized in physicalKeyboardCurrencySymbols()) {
        normalized
    } else {
        DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
    }
}

fun SettingsManager.physicalKeyboardCurrencySymbols(): List<String> =
    listOf("€", "$", "£", "¥", "₹", "₽", "zł", "₴", "kr", "Ft", "Kč", "₺", "֏", "₫", "₩", "R$", "₪", "lei", "CHF", "₿", "¤")

/**
 * Saves the list of keyboard layouts used for cycling.
 * The caller is responsible for also selecting the active layout via setKeyboardLayout().
 */
fun SettingsManager.setKeyboardLayoutList(context: Context, layouts: List<String>) {
    val normalized = layouts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (normalized.isEmpty()) {
        // Clear the list to fall back to single-layout behaviour.
        getPreferences(context).edit()
            .remove(KEY_KEYBOARD_LAYOUT_LIST)
            .apply()
        return
    }
    val array = org.json.JSONArray()
    normalized.forEach { array.put(it) }
    getPreferences(context).edit()
        .putString(KEY_KEYBOARD_LAYOUT_LIST, array.toString())
        .apply()
}

/**
 * Gets the custom input styles preference string.
 * Returns default from predefined_subtypes resource if not set.
 */
fun SettingsManager.getCustomInputStyles(context: Context): String {
    val prefs = getPreferences(context)
    val custom = prefs.getString(KEY_CUSTOM_INPUT_STYLES, null)
    if (custom != null) {
        return custom
    }

    // Load default from predefined_subtypes resource
    return try {
        val arrayResId = context.resources.getIdentifier(
            "predefined_subtypes",
            "array",
            context.packageName
        )
        if (arrayResId == 0) {
            return ""
        }
        val array = context.resources.getStringArray(arrayResId)
        array.joinToString(";")
    } catch (e: Exception) {
        Log.e(TAG, "Error loading predefined subtypes", e)
        ""
    }
}

/**
 * Sets the custom input styles preference string.
 */
fun SettingsManager.setCustomInputStyles(context: Context, stylesString: String) {
    getPreferences(context).edit()
        .putString(KEY_CUSTOM_INPUT_STYLES, stylesString)
        .apply()
}

fun SettingsManager.isSystemInputStyleHidden(context: Context, locale: String, layout: String): Boolean {
    return hiddenSystemInputStyleKeys(context).contains(inputStyleKey(locale, layout))
}

fun SettingsManager.hideSystemInputStyle(context: Context, locale: String, layout: String) {
    val updated = hiddenSystemInputStyleKeys(context).toMutableSet()
    updated.add(inputStyleKey(locale, layout))
    saveHiddenSystemInputStyleKeys(context, updated)
}

fun SettingsManager.showSystemInputStyle(context: Context, locale: String, layout: String) {
    val updated = hiddenSystemInputStyleKeys(context).toMutableSet()
    updated.remove(inputStyleKey(locale, layout))
    saveHiddenSystemInputStyleKeys(context, updated)
}

fun SettingsManager.getAdditionalSuggestionLocalesForInputStyle(
    context: Context,
    locale: String,
    layout: String
): List<String> {
    val jsonString = getPreferences(context).getString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, null)
        ?: return emptyList()
    return try {
        val root = org.json.JSONObject(jsonString)
        val array = suggestionLocalesArrayForInputStyle(root, locale, layout) ?: return emptyList()
        suggestionLocalesFromArray(array)
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing input style suggestion locales", e)
        emptyList()
    }
}

private fun SettingsManager.suggestionLocalesArrayForInputStyle(
    root: org.json.JSONObject,
    locale: String,
    layout: String
): org.json.JSONArray? {
    val normalizedLocale = normalizeSuggestionLocaleTag(locale)
    val language = normalizedLocale.substringBefore("-")
    val exactKey = inputStyleSuggestionKey(locale, layout)
    val languageLayoutKey = inputStyleKey(language, layout)

    root.optJSONArray(exactKey)?.let { return it }
    root.optJSONArray(languageLayoutKey)?.let { return it }

    LegacySettings.suggestionLayoutAliases(layout).forEach { legacyLayout ->
        root.optJSONArray(inputStyleKey(normalizedLocale, legacyLayout))?.let { return it }
        root.optJSONArray(inputStyleKey(language, legacyLayout))?.let { return it }
    }

    return null
}

private fun SettingsManager.suggestionLocalesFromArray(array: org.json.JSONArray): List<String> {
    return buildList {
        for (i in 0 until array.length()) {
            val tag = array.optString(i).trim()
            if (tag.isNotBlank()) {
                add(normalizeSuggestionLocaleTag(tag))
            }
        }
    }.distinct()
}

fun SettingsManager.setAdditionalSuggestionLocalesForInputStyle(
    context: Context,
    locale: String,
    layout: String,
    locales: List<String>
) {
    val prefs = getPreferences(context)
    val root = try {
        org.json.JSONObject(prefs.getString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, null) ?: "{}")
    } catch (_: Exception) {
        org.json.JSONObject()
    }
    val key = inputStyleSuggestionKey(locale, layout)
    val normalized = locales
        .map { normalizeSuggestionLocaleTag(it) }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalized.isEmpty()) {
        root.remove(key)
    } else {
        val array = org.json.JSONArray()
        normalized.forEach { array.put(it) }
        root.put(key, array)
    }
    prefs.edit()
        .putString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, root.toString())
        .apply()
}

fun SettingsManager.removeAdditionalSuggestionLocalesForInputStyle(
    context: Context,
    locale: String,
    layout: String
) {
    val prefs = getPreferences(context)
    val root = try {
        org.json.JSONObject(prefs.getString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, null) ?: "{}")
    } catch (_: Exception) {
        return
    }
    root.remove(inputStyleSuggestionKey(locale, layout))
    prefs.edit()
        .putString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, root.toString())
        .apply()
}

private fun SettingsManager.inputStyleSuggestionKey(locale: String, layout: String): String {
    return inputStyleKey(locale, layout)
}

private fun SettingsManager.inputStyleKey(locale: String, layout: String): String {
    return "${normalizeSuggestionLocaleTag(locale)}:${layout.trim()}"
}

private fun SettingsManager.normalizeSuggestionLocaleTag(locale: String): String {
    return locale.trim().replace('_', '-')
}

private fun SettingsManager.hiddenSystemInputStyleKeys(context: Context): Set<String> {
    val jsonString = getPreferences(context).getString(KEY_HIDDEN_SYSTEM_INPUT_STYLES, null)
        ?: return emptySet()
    return try {
        val array = org.json.JSONArray(jsonString)
        buildSet {
            for (i in 0 until array.length()) {
                val key = array.optString(i).trim()
                if (key.isNotBlank()) add(key)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing hidden system input styles", e)
        emptySet()
    }
}

private fun SettingsManager.saveHiddenSystemInputStyleKeys(context: Context, keys: Set<String>) {
    val array = org.json.JSONArray()
    keys.sorted().forEach { array.put(it) }
    getPreferences(context).edit()
        .putString(KEY_HIDDEN_SYSTEM_INPUT_STYLES, array.toString())
        .apply()
}
