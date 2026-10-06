package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.legacy.LegacySettings
import org.json.JSONArray
import org.json.JSONObject
import it.palsoftware.pastiera.sym.SymPagesConfig

// SettingsManager: the SYM, emoji and symbols layers, the emoji picker, GIFs and search. The keys and defaults live in SettingsManager.kt.

/**
 * Returns custom SYM mappings.
 * Returns an empty map if there are no custom mappings.
 */
fun SettingsManager.getSymMappings(context: Context): Map<Int, String> {
    val prefs = getPreferences(context)
    val jsonString = prefs.getString(KEY_SYM_MAPPINGS_CUSTOM, null) ?: return emptyMap()
    
    return try {
        val jsonObject = JSONObject(jsonString)
        val mappingsObject = jsonObject.getJSONObject("mappings")
        val keyCodeMap = mapOf(
            "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
            "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
            "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
            "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
            "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
            "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
            "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
            "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
            "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
            "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
            "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
            "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
            "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
        )
        
        val result = mutableMapOf<Int, String>()
        val keys = mappingsObject.keys()
        while (keys.hasNext()) {
            val keyName = keys.next()
            val keyCode = keyCodeMap[keyName]
            val emoji = mappingsObject.getString(keyName)
            if (keyCode != null) {
                result[keyCode] = emoji
            }
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "Error loading custom SYM mappings", e)
        emptyMap()
    }
}

/**
 * Saves custom SYM mappings.
 */
fun SettingsManager.saveSymMappings(context: Context, mappings: Map<Int, String>) {
    try {
        val keyCodeToName = mapOf(
            KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
            KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
            KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
            KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
            KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
            KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
            KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
            KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
            KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
            KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
            KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
            KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
            KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
        )
        
        val mappingsObject = JSONObject()
        for ((keyCode, emoji) in mappings) {
            val keyName = keyCodeToName[keyCode]
            if (keyName != null) {
                mappingsObject.put(keyName, emoji)
            }
        }
        
        val jsonObject = JSONObject()
        jsonObject.put("mappings", mappingsObject)
        
        getPreferences(context).edit()
            .putString(KEY_SYM_MAPPINGS_CUSTOM, jsonObject.toString())
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error saving custom SYM mappings", e)
    }
}

/**
 * Resets custom SYM mappings back to defaults.
 */
fun SettingsManager.resetSymMappings(context: Context) {
    getPreferences(context).edit()
        .remove(KEY_SYM_MAPPINGS_CUSTOM)
        .apply()
}

/**
 * Returns custom SYM mappings for page 2.
 * Returns an empty map if there are no custom mappings.
 */
fun SettingsManager.getSymMappingsPage2(context: Context): Map<Int, String> {
    val prefs = getPreferences(context)
    val jsonString = prefs.getString(KEY_SYM_MAPPINGS_PAGE2_CUSTOM, null) ?: return emptyMap()
    
    return try {
        val jsonObject = JSONObject(jsonString)
        val mappingsObject = jsonObject.getJSONObject("mappings")
        val keyCodeMap = mapOf(
            "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
            "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
            "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
            "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
            "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
            "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
            "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
            "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
            "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
            "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
            "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
            "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
            "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
        )
        
        val result = mutableMapOf<Int, String>()
        val keys = mappingsObject.keys()
        while (keys.hasNext()) {
            val keyName = keys.next()
            val keyCode = keyCodeMap[keyName]
            val character = mappingsObject.getString(keyName)
            if (keyCode != null) {
                result[keyCode] = character
            }
        }
        result
    } catch (e: Exception) {
        Log.e(TAG, "Error loading custom SYM page 2 mappings", e)
        emptyMap()
    }
}

/**
 * Saves custom SYM mappings for page 2.
 */
fun SettingsManager.saveSymMappingsPage2(context: Context, mappings: Map<Int, String>) {
    try {
        val keyCodeToName = mapOf(
            KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
            KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
            KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
            KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
            KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
            KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
            KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
            KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
            KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
            KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
            KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
            KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
            KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
        )
        
        val mappingsObject = JSONObject()
        for ((keyCode, character) in mappings) {
            val keyName = keyCodeToName[keyCode]
            if (keyName != null) {
                mappingsObject.put(keyName, character)
            }
        }
        
        val jsonObject = JSONObject()
        jsonObject.put("mappings", mappingsObject)
        
        getPreferences(context).edit()
            .putString(KEY_SYM_MAPPINGS_PAGE2_CUSTOM, jsonObject.toString())
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error saving custom SYM page 2 mappings", e)
    }
}

/**
 * Resets custom SYM mappings for page 2 back to defaults.
 */
fun SettingsManager.resetSymMappingsPage2(context: Context) {
    getPreferences(context).edit()
        .remove(KEY_SYM_MAPPINGS_PAGE2_CUSTOM)
        .apply()
}

/**
 * Sets the SYM page to restore when returning from settings.
 * @param context The context
 * @param page The SYM page to restore (0=disabled, 1=page1 emoji, 2=page2 characters)
 */
fun SettingsManager.setRestoreSymPage(context: Context, page: Int) {
    getPreferences(context).edit()
        .putInt(KEY_RESTORE_SYM_PAGE, page)
        .apply()
}

/**
 * Gets the SYM page to restore when returning from settings.
 * @param context The context
 * @return The SYM page to restore (0=disabled, 1=page1 emoji, 2=page2 characters), or 0 if not set
 */
fun SettingsManager.getRestoreSymPage(context: Context): Int {
    return getPreferences(context).getInt(KEY_RESTORE_SYM_PAGE, 0)
}

/**
 * Clears the SYM page restore state.
 * @param context The context
 */
fun SettingsManager.clearRestoreSymPage(context: Context) {
    getPreferences(context).edit()
        .remove(KEY_RESTORE_SYM_PAGE)
        .apply()
}

/**
 * Sets a pending SYM page state when opening SymCustomizationActivity.
 * This will be converted to restore_sym_page only if user presses back.
 * @param context The context
 * @param page The SYM page that was active (0=disabled, 1=page1 emoji, 2=page2 characters)
 */
fun SettingsManager.setPendingRestoreSymPage(context: Context, page: Int) {
    getPreferences(context).edit()
        .putInt(KEY_PENDING_RESTORE_SYM_PAGE, page)
        .apply()
}

/**
 * Gets the pending SYM page state.
 * @param context The context
 * @return The pending SYM page, or 0 if not set
 */
fun SettingsManager.getPendingRestoreSymPage(context: Context): Int {
    return getPreferences(context).getInt(KEY_PENDING_RESTORE_SYM_PAGE, 0)
}

/**
 * Clears the pending SYM page state.
 * @param context The context
 */
fun SettingsManager.clearPendingRestoreSymPage(context: Context) {
    getPreferences(context).edit()
        .remove(KEY_PENDING_RESTORE_SYM_PAGE)
        .apply()
}

/**
 * Confirms the pending restore by moving it to restore_sym_page.
 * Called when user presses back from SymCustomizationActivity.
 * @param context The context
 */
fun SettingsManager.confirmPendingRestoreSymPage(context: Context) {
    val pendingPage = getPendingRestoreSymPage(context)
    if (pendingPage > 0) {
        setRestoreSymPage(context, pendingPage)
        clearPendingRestoreSymPage(context)
    }
}

/**
 * Reads the SYM pages configuration (enabled pages and order).
 */
fun SettingsManager.getSymPagesConfig(context: Context): SymPagesConfig {
    val prefs = getPreferences(context)
    val jsonString = prefs.getString(KEY_SYM_PAGES_CONFIG, null) ?: return DEFAULT_SYM_PAGES_CONFIG

    return try {
        val jsonObject = JSONObject(jsonString)
        val schemaVersion = jsonObject.optInt("schemaVersion", 1)
        val deviceEnabled = jsonObject.optBoolean("deviceEnabled", false)
        val emojiEnabled = jsonObject.optBoolean("emojiEnabled", true)
        val symbolsEnabled = jsonObject.optBoolean("symbolsEnabled", true)
        val clipboardEnabled = jsonObject.optBoolean("clipboardEnabled", false)
        val emojiPickerEnabled = jsonObject.optBoolean("emojiPickerEnabled", false)

        val parsedOrder = if (jsonObject.has("symPageOrder")) {
            val orderArray = jsonObject.optJSONArray("symPageOrder")
            val collected = mutableListOf<String>()
            if (orderArray != null) {
                for (i in 0 until orderArray.length()) {
                    val pageId = orderArray.optString(i, "").trim()
                    if (pageId.isNotEmpty()) {
                        collected.add(pageId)
                    }
                }
            }
            collected
        } else {
            LegacySettings.symPageOrderFromEmojiFirst(
                jsonObject.optBoolean(LegacySettings.SYM_PAGES_EMOJI_FIRST_FIELD, true)
            )
        }

        val parsedConfig = SymPagesConfig(
            deviceEnabled = deviceEnabled,
            emojiEnabled = emojiEnabled,
            symbolsEnabled = symbolsEnabled,
            clipboardEnabled = clipboardEnabled,
            emojiPickerEnabled = emojiPickerEnabled,
            symPageOrder = parsedOrder
        )
        val migratedConfig = if (schemaVersion < SYM_PAGES_SCHEMA_VERSION &&
            LegacySettings.isDefaultFromBeforeDevicePage(parsedConfig)) {
            parsedConfig.copy(deviceEnabled = true)
        } else {
            parsedConfig
        }
        if (schemaVersion < SYM_PAGES_SCHEMA_VERSION) {
            setSymPagesConfig(context, migratedConfig)
        }
        migratedConfig
    } catch (e: Exception) {
        Log.e(TAG, "Error loading SYM pages config", e)
        DEFAULT_SYM_PAGES_CONFIG
    }
}

/**
 * Persists the SYM pages configuration (enabled pages and order).
 */
fun SettingsManager.setSymPagesConfig(context: Context, config: SymPagesConfig) {
    try {
        val jsonObject = JSONObject().apply {
            put("schemaVersion", SYM_PAGES_SCHEMA_VERSION)
            put("deviceEnabled", config.deviceEnabled)
            put("emojiEnabled", config.emojiEnabled)
            put("symbolsEnabled", config.symbolsEnabled)
            put("clipboardEnabled", config.clipboardEnabled)
            put("emojiPickerEnabled", config.emojiPickerEnabled)
            // Still written so older builds read the same order
            put(LegacySettings.SYM_PAGES_EMOJI_FIRST_FIELD, config.prefersEmojiLongPressLayer())
            val orderArray = org.json.JSONArray()
            config.normalizedOrder().forEach { orderArray.put(it) }
            put("symPageOrder", orderArray)
        }

        getPreferences(context).edit()
            .putString(KEY_SYM_PAGES_CONFIG, jsonObject.toString())
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error saving SYM pages config", e)
    }
}

fun SettingsManager.getAltModifierBinding(context: Context): AltModifierBinding {
    val prefs = getPreferences(context)
    prefs.getString(KEY_ALT_MODIFIER_BINDING, null)?.let {
        return AltModifierBinding.fromPersistedValue(it)
    }

    return LegacySettings.moveAltCharacterLayerBinding(prefs, KEY_ALT_MODIFIER_BINDING)
        ?: AltModifierBinding.fromPersistedValue(null)
}

fun SettingsManager.setAltModifierBinding(context: Context, binding: AltModifierBinding) {
    getPreferences(context).edit()
        .putString(KEY_ALT_MODIFIER_BINDING, binding.persistedValue)
        .remove(LegacySettings.KEY_ALT_CHARACTER_LAYER_BINDING)
        .apply()
}

/**
 * Gets whether SYM layout should auto-close after key press.
 * @param context The context
 * @return true if SYM should auto-close, false otherwise
 */
fun SettingsManager.getSymAutoClose(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SYM_AUTO_CLOSE, DEFAULT_SYM_AUTO_CLOSE)
}

/**
 * Sets whether SYM layout should auto-close after key press.
 * @param context The context
 * @param enabled true to enable auto-close, false to disable
 */
fun SettingsManager.setSymAutoClose(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SYM_AUTO_CLOSE, enabled)
        .apply()
}

/** The emoji layer as pages (Q back, P on), recent emoji first. On by default. */
fun SettingsManager.getEmojiLayerPages(context: Context): Boolean =
    getPreferences(context).getBoolean("emoji_layer_pages", true)

fun SettingsManager.setEmojiLayerPages(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("emoji_layer_pages", enabled).apply()
}

/** The symbols page as pages (Q back, P on), recent symbols first, kaomoji last. On by default. */
fun SettingsManager.getSymbolsPages(context: Context): Boolean =
    getPreferences(context).getBoolean("symbols_pages", true)

fun SettingsManager.setSymbolsPages(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("symbols_pages", enabled).apply()
}

/** An emoji typed with its key closes the emoji layer (until changed, as SYM auto-close was). */
fun SettingsManager.getEmojiLayerCloseOnKey(context: Context): Boolean =
    getPreferences(context).getBoolean("emoji_layer_close_on_key", getSymAutoClose(context))

fun SettingsManager.setEmojiLayerCloseOnKey(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("emoji_layer_close_on_key", enabled).apply()
}

/** An emoji tapped on the layer closes it (until changed, as SYM auto-close on touch was). */
fun SettingsManager.getEmojiLayerCloseOnTap(context: Context): Boolean =
    getPreferences(context).getBoolean(
        "emoji_layer_close_on_tap", getSymAutoClose(context) && getSymAutoCloseOnTouch(context)
    )

fun SettingsManager.setEmojiLayerCloseOnTap(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("emoji_layer_close_on_tap", enabled).apply()
}

/** A symbol typed with its key closes the symbols page (until changed, as SYM auto-close was). */
fun SettingsManager.getSymbolsCloseOnKey(context: Context): Boolean =
    getPreferences(context).getBoolean("symbols_close_on_key", getSymAutoClose(context))

fun SettingsManager.setSymbolsCloseOnKey(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("symbols_close_on_key", enabled).apply()
}

/** A symbol tapped on the symbols page closes it (until changed, as SYM auto-close on touch was). */
fun SettingsManager.getSymbolsCloseOnTap(context: Context): Boolean =
    getPreferences(context).getBoolean(
        "symbols_close_on_tap", getSymAutoClose(context) && getSymAutoCloseOnTouch(context)
    )

fun SettingsManager.setSymbolsCloseOnTap(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("symbols_close_on_tap", enabled).apply()
}

/** A kaomoji typed with its key closes the symbols page (off until changed). */
fun SettingsManager.getKaomojiCloseOnKey(context: Context): Boolean =
    getPreferences(context).getBoolean("kaomoji_close_on_key", false)

fun SettingsManager.setKaomojiCloseOnKey(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("kaomoji_close_on_key", enabled).apply()
}

/** A kaomoji tapped on screen closes the symbols page (off until changed). */
fun SettingsManager.getKaomojiCloseOnTap(context: Context): Boolean =
    getPreferences(context).getBoolean("kaomoji_close_on_tap", false)

fun SettingsManager.setKaomojiCloseOnTap(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("kaomoji_close_on_tap", enabled).apply()
}

fun SettingsManager.getSymAutoCloseOnTouch(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_SYM_AUTO_CLOSE_ON_TOUCH,
        DEFAULT_SYM_AUTO_CLOSE_ON_TOUCH
    )
}

fun SettingsManager.setSymAutoCloseOnTouch(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SYM_AUTO_CLOSE_ON_TOUCH, enabled)
        .apply()
}

fun SettingsManager.getEmojiPickerExpandedHeight(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_EMOJI_PICKER_EXPANDED_HEIGHT,
        DEFAULT_EMOJI_PICKER_EXPANDED_HEIGHT
    )
}

fun SettingsManager.setEmojiPickerExpandedHeight(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_EMOJI_PICKER_EXPANDED_HEIGHT, enabled)
        .apply()
}

/**
 * True if [keyCode] can be dedicated to the emoji picker. Any key the device has is fine
 * except ones that type text ([isPrintingKey], letters, digits) or are needed while typing.
 */
fun SettingsManager.isAllowedEmojiPickerKey(keyCode: Int, isPrintingKey: Boolean = false): Boolean =
    keyCode > KeyEvent.KEYCODE_UNKNOWN &&
        !isPrintingKey &&
        keyCode !in EMOJI_PICKER_KEY_DENYLIST &&
        keyCode !in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 &&
        keyCode !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z

/** The dedicated emoji picker key, or KEYCODE_UNKNOWN when the feature is off. */
fun SettingsManager.getEmojiPickerKey(context: Context): Int {
    val keyCode = getPreferences(context).getInt(KEY_EMOJI_PICKER_KEY, DEFAULT_EMOJI_PICKER_KEY)
    return if (keyCode == KeyEvent.KEYCODE_UNKNOWN || isAllowedEmojiPickerKey(keyCode)) {
        keyCode
    } else {
        DEFAULT_EMOJI_PICKER_KEY
    }
}

/** Stores [keyCode] (KEYCODE_UNKNOWN turns the feature off). Returns false if not allowed. */
fun SettingsManager.setEmojiPickerKey(context: Context, keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && !isAllowedEmojiPickerKey(keyCode)) return false
    getPreferences(context).edit()
        .putInt(KEY_EMOJI_PICKER_KEY, keyCode)
        .apply()
    return true
}

/** A tap on SYM applies it to the next key (its symbol); a second tap opens the symbols. */
fun SettingsManager.getSymStickyTap(context: Context): Boolean = getPreferences(context).getBoolean(KEY_SYM_STICKY_TAP, false)

fun SettingsManager.setSymStickyTap(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SYM_STICKY_TAP, enabled).apply()
}

/** A tap on the emoji key applies it to the next key (its emoji); a second tap opens the emoji screen. */
fun SettingsManager.getEmojiStickyTap(context: Context): Boolean = getPreferences(context).getBoolean(KEY_EMOJI_STICKY_TAP, false)

fun SettingsManager.setEmojiStickyTap(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_STICKY_TAP, enabled).apply()
}

/** A fifth status LED for the emoji key; the other four shrink to make room. */
fun SettingsManager.getEmojiKeyLedEnabled(context: Context): Boolean = getPreferences(context).getBoolean(KEY_EMOJI_KEY_LED, true)

fun SettingsManager.setEmojiKeyLedEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_KEY_LED, enabled).apply()
}

/** The emoji key opens the emoji layer (SYM page 1) instead of the emoji picker. */
fun SettingsManager.getEmojiKeyOpensLayer(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_KEY_OPENS_LAYER, false)

fun SettingsManager.setEmojiKeyOpensLayer(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_KEY_OPENS_LAYER, enabled).apply()
}

/** Emoji screens of the emoji key close after an emoji is entered (separate from SYM auto-close). */
fun SettingsManager.getEmojiKeyAutoClose(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_KEY_AUTO_CLOSE, false)

fun SettingsManager.setEmojiKeyAutoClose(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_KEY_AUTO_CLOSE, enabled).apply()
}

/**
 * Whether an emoji screen closes after an emoji is entered. With an emoji key set, the emoji
 * picker, and the emoji layer when the emoji key opened it, follow the emoji key's own
 * setting; everything else follows SYM auto-close ([byTouch]: also needs "close after
 * on-screen SYM keys").
 */
fun SettingsManager.emojiScreenClosesAfterInput(
    context: Context,
    isPicker: Boolean,
    openedByEmojiKey: Boolean,
    byTouch: Boolean
): Boolean {
    if (getEmojiPickerKey(context) != KeyEvent.KEYCODE_UNKNOWN && (isPicker || openedByEmojiKey)) {
        return getEmojiKeyAutoClose(context)
    }
    // The emoji layer: its own switches for typed and tapped emoji
    if (!isPicker) return if (byTouch) getEmojiLayerCloseOnTap(context) else getEmojiLayerCloseOnKey(context)
    return if (byTouch) getSymAutoCloseOnTouch(context) else getSymAutoClose(context)
}

/**
 * In apps with Pastiera hidden (Termux:X11), send the Titan's Ctrl and Sym on as standard Left
 * Ctrl and Right Alt: Android gives them Unihertz key codes such apps can't use. On by default.
 */
fun SettingsManager.getHiddenAppStandardModifiers(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_HIDDEN_APP_STANDARD_MODIFIERS, true)

fun SettingsManager.setHiddenAppStandardModifiers(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_HIDDEN_APP_STANDARD_MODIFIERS, enabled).apply()
}

/** The emoji picker opens with its search taking typing. On by default. */
fun SettingsManager.getEmojiPickerFocusSearch(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_PICKER_FOCUS_SEARCH, true)

fun SettingsManager.setEmojiPickerFocusSearch(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_PICKER_FOCUS_SEARCH, enabled).apply()
}

/** GIF search opens with its search taking typing. On by default. */
fun SettingsManager.getGifFocusSearch(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_GIF_FOCUS_SEARCH, true)

fun SettingsManager.setGifFocusSearch(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_GIF_FOCUS_SEARCH, enabled).apply()
}

/** On the emoji layer, a letter key starts emoji search with that letter (off: it types its emoji). */
fun SettingsManager.getEmojiLayerTypeToSearch(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_LAYER_TYPE_TO_SEARCH, false)

fun SettingsManager.setEmojiLayerTypeToSearch(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_LAYER_TYPE_TO_SEARCH, enabled).apply()
}

/** On the symbols pages, a letter key starts symbol search with that letter (off: it types its symbol). */
fun SettingsManager.getSymbolsTypeToSearch(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SYMBOLS_TYPE_TO_SEARCH, false)

fun SettingsManager.setSymbolsTypeToSearch(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SYMBOLS_TYPE_TO_SEARCH, enabled).apply()
}

/**
 * The key that opens search on the emoji layer and the symbols pages, and puts typing into
 * the emoji and GIF picker's search: A unless changed (KEYCODE_UNKNOWN = off).
 */
fun SettingsManager.getSearchKey(context: Context): Int {
    val keyCode = getPreferences(context).getInt(KEY_SEARCH_KEY, KeyEvent.KEYCODE_Q)
    return if (keyCode in EMOJI_LAYER_KEYS) keyCode else KeyEvent.KEYCODE_UNKNOWN
}

fun SettingsManager.setSearchKey(context: Context, keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
        if (keyCode !in EMOJI_LAYER_KEYS) return false
        // One key, one job: not the Recents or GIF key
        if (keyCode == getEmojiLayerRecentsKey(context) || keyCode == getEmojiLayerGifKey(context)) return false
    }
    getPreferences(context).edit().putInt(KEY_SEARCH_KEY, keyCode).apply()
    return true
}

/** Offline mode (see [OfflineMode]): nothing in Pastiera goes online. Off by default. */
fun SettingsManager.isOfflineMode(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_OFFLINE_MODE, false)

fun SettingsManager.setOfflineMode(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
    OfflineMode.update(enabled)
}

/** GIF search is switched on and allowed online (not in offline mode). */
fun SettingsManager.gifsAvailable(context: Context): Boolean = getGifsEnabled(context) && !isOfflineMode(context)

/**
 * Emoji, symbols and GIFs used recently (and favourite GIFs) come first in their searches.
 * On by default.
 */
fun SettingsManager.getRecentsFirstInSearch(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_RECENTS_FIRST_IN_SEARCH, true)

fun SettingsManager.setRecentsFirstInSearch(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_RECENTS_FIRST_IN_SEARCH, enabled).apply()
}

/** GIF search shows the favourite GIFs at its top (with an empty search). On by default. */
fun SettingsManager.getGifShowFavourites(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_GIF_SHOW_FAVOURITES, true)

fun SettingsManager.setGifShowFavourites(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_GIF_SHOW_FAVOURITES, enabled).apply()
}

/** GIF search shows the recently sent GIFs (with an empty search). On by default. */
fun SettingsManager.getGifShowRecents(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_GIF_SHOW_RECENTS, true)

fun SettingsManager.setGifShowRecents(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_GIF_SHOW_RECENTS, enabled).apply()
}

/** Enter in emoji search picks the first emoji and closes (after a pick, only closes). On by default. */
fun SettingsManager.getEmojiSearchEnterPicks(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_SEARCH_ENTER_PICKS, true)

fun SettingsManager.setEmojiSearchEnterPicks(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_SEARCH_ENTER_PICKS, enabled).apply()
}

/** Enter in symbol search picks the first symbol and closes (after a pick, only closes). On by default. */
fun SettingsManager.getSymbolSearchEnterPicks(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SYMBOL_SEARCH_ENTER_PICKS, true)

fun SettingsManager.setSymbolSearchEnterPicks(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SYMBOL_SEARCH_ENTER_PICKS, enabled).apply()
}

/** Enter in GIF search sends the first GIF (which closes the picker). On by default. */
fun SettingsManager.getGifSearchEnterPicks(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_GIF_SEARCH_ENTER_PICKS, true)

fun SettingsManager.setGifSearchEnterPicks(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_GIF_SEARCH_ENTER_PICKS, enabled).apply()
}

/** The emoji layer key that opens GIF search (while it's on): P unless changed (KEYCODE_UNKNOWN = off). */
fun SettingsManager.getEmojiLayerGifKey(context: Context): Int {
    val keyCode = getPreferences(context).getInt(KEY_EMOJI_LAYER_GIF_KEY, KeyEvent.KEYCODE_P)
    return if (keyCode in EMOJI_LAYER_KEYS) keyCode else KeyEvent.KEYCODE_UNKNOWN
}

fun SettingsManager.setEmojiLayerGifKey(context: Context, keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode !in EMOJI_LAYER_KEYS) return false
    // One key, one job: not the Recents key or the search key
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode == getEmojiLayerRecentsKey(context)) return false
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode == getSearchKey(context)) return false
    getPreferences(context).edit().putInt(KEY_EMOJI_LAYER_GIF_KEY, keyCode).apply()
    return true
}

/** The emoji layer's GIF key while GIF search is on and it isn't also the Recents key, else KEYCODE_UNKNOWN. */
fun SettingsManager.activeEmojiLayerGifKey(context: Context): Int {
    if (!gifsAvailable(context)) return KeyEvent.KEYCODE_UNKNOWN
    val keyCode = getEmojiLayerGifKey(context)
    return if (keyCode == getEmojiLayerRecentsKey(context)) KeyEvent.KEYCODE_UNKNOWN else keyCode
}

/** GIF search (KLIPY): a GIF key on the emoji layer and a GIF tab in the emoji picker. */
fun SettingsManager.getGifsEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_GIFS_ENABLED, false)

fun SettingsManager.setGifsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_GIFS_ENABLED, enabled).apply()
}

/** The KLIPY key GIF search uses: the user's own, else the build's built-in one (may be empty). */
fun SettingsManager.getKlipyApiKey(context: Context): String =
    getUserKlipyApiKey(context).ifEmpty { BuildConfig.KLIPY_API_KEY.trim() }

/** Only the key the user entered (the settings field never shows the built-in one). */
fun SettingsManager.getUserKlipyApiKey(context: Context): String =
    getPreferences(context).getString(KEY_KLIPY_API_KEY, "").orEmpty().trim()

/** This build has a KLIPY key built in (set as a CI secret when it was built). */
fun SettingsManager.hasBuiltInKlipyApiKey(): Boolean = BuildConfig.KLIPY_API_KEY.isNotBlank()

fun SettingsManager.setKlipyApiKey(context: Context, apiKey: String) {
    getPreferences(context).edit().putString(KEY_KLIPY_API_KEY, apiKey.trim()).apply()
}

/** The emoji layer key that shows recent emoji instead of its own: Q unless changed (KEYCODE_UNKNOWN = off). */
fun SettingsManager.getEmojiLayerRecentsKey(context: Context): Int {
    val keyCode = getPreferences(context).getInt(KEY_EMOJI_LAYER_RECENTS_KEY, KeyEvent.KEYCODE_A)
    return if (keyCode in EMOJI_LAYER_KEYS) keyCode else KeyEvent.KEYCODE_UNKNOWN
}

fun SettingsManager.setEmojiLayerRecentsKey(context: Context, keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode !in EMOJI_LAYER_KEYS) return false
    // One key, one job: not the GIF key while GIF search is on, nor the search key
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && getGifsEnabled(context) && keyCode == getEmojiLayerGifKey(context)) return false
    if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode == getSearchKey(context)) return false
    getPreferences(context).edit().putInt(KEY_EMOJI_LAYER_RECENTS_KEY, keyCode).apply()
    return true
}
