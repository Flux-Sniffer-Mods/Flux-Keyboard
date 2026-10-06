package it.palsoftware.pastiera

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import it.palsoftware.pastiera.legacy.LegacyMigrations
import org.json.JSONArray
import org.json.JSONObject
import it.palsoftware.pastiera.theme.KeyboardBackgroundImage
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_KEY_HEIGHT
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_ROW_GAP
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT
import it.palsoftware.pastiera.theme.SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT
import it.palsoftware.pastiera.theme.WallpaperKeyboardColours
import it.palsoftware.pastiera.SettingsManager.KeyboardThemeTarget
import it.palsoftware.pastiera.SettingsManager.KeyboardThemeSettings
import it.palsoftware.pastiera.SettingsManager.NamedKeyboardTheme
import it.palsoftware.pastiera.SettingsManager.KeyboardThemeDraft
import it.palsoftware.pastiera.SettingsManager.KeyboardThemeLayoutOverride

// SettingsManager: keyboard themes: colours, saved themes, drafts, per-layout overrides and the background picture. The keys and defaults live in SettingsManager.kt.

fun SettingsManager.defaultKeyboardTheme(): KeyboardThemeSettings = CLASSIC_CLOUD

private fun SettingsManager.defaultKeyboardTheme(target: KeyboardThemeTarget): KeyboardThemeSettings =
    when (target) {
        KeyboardThemeTarget.HARDWARE -> defaultKeyboardTheme()
        KeyboardThemeTarget.SOFTWARE -> defaultKeyboardTheme().copy(
            keyCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS,
            chromeCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS,
            keyHeightScale = SOFTWARE_THEME_DEFAULT_KEY_HEIGHT,
            numberRowHeightScale = SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT,
            rowGapScale = SOFTWARE_THEME_DEFAULT_ROW_GAP,
            ortholinear = true,
            showLeds = false,
            suggestionsHeightScale = SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            variationsHeightScale = SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT
        )
    }

/** Following the system: Classic Midnight in dark mode, Classic Cloud in light mode. */
private fun SettingsManager.defaultSystemKeyboardTheme(target: KeyboardThemeTarget, dark: Boolean): KeyboardThemeSettings {
    val base = if (dark) CLASSIC_MIDNIGHT else CLASSIC_CLOUD
    return when (target) {
        KeyboardThemeTarget.HARDWARE -> base
        KeyboardThemeTarget.SOFTWARE -> base.copy(
            keyCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS,
            chromeCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS,
            keyHeightScale = SOFTWARE_THEME_DEFAULT_KEY_HEIGHT,
            numberRowHeightScale = SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT,
            rowGapScale = SOFTWARE_THEME_DEFAULT_ROW_GAP,
            ortholinear = true,
            showLeds = false,
            suggestionsHeightScale = SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            variationsHeightScale = SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT
        )
    }
}

fun SettingsManager.keyboardThemeKeyForTarget(target: KeyboardThemeTarget): String =
    when (target) {
        KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_HARDWARE
        KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_SOFTWARE
    }

internal fun SettingsManager.keyboardThemeAssignmentModeKeyForTarget(target: KeyboardThemeTarget): String =
    when (target) {
        KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_HARDWARE
        KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_SOFTWARE
    }

internal fun SettingsManager.keyboardThemeLightKeyForTarget(target: KeyboardThemeTarget): String =
    when (target) {
        KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_LIGHT_HARDWARE
        KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_LIGHT_SOFTWARE
    }

internal fun SettingsManager.keyboardThemeDarkKeyForTarget(target: KeyboardThemeTarget): String =
    when (target) {
        KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_DARK_HARDWARE
        KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_DARK_SOFTWARE
    }

private fun SettingsManager.keyboardThemeLayoutOverridesKeyForTarget(target: KeyboardThemeTarget): String =
    when (target) {
        KeyboardThemeTarget.HARDWARE -> KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_HARDWARE
        KeyboardThemeTarget.SOFTWARE -> KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_SOFTWARE
    }

fun SettingsManager.isKeyboardThemePreferenceKey(key: String?): Boolean {
    return key == KEY_KEYBOARD_THEME_HARDWARE || key == KEY_KEYBOARD_THEME_SOFTWARE ||
        key == KEY_KEYBOARD_WALLPAPER_COLOURS || key == KEY_KEYBOARD_BACKGROUND_AUTO_COLOURS ||
        key == KEY_KEYBOARD_BACKGROUND_KEY_OPACITY || key == KEY_KEYBOARD_BACKGROUND_UPDATED ||
        key == KEY_KEYBOARD_BACKGROUND_FRAMING
}

fun SettingsManager.isModifierIndicatorPreferenceKey(key: String?): Boolean {
    return key == KEY_MODIFIER_INDICATOR_MODE
}

fun SettingsManager.getKeyboardThemePreviewViewportScale(context: Context): Float =
    getPreferences(context)
        .getFloat(KEY_KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE, KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN)
        .coerceIn(KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN, KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX)

fun SettingsManager.setKeyboardThemePreviewViewportScale(context: Context, scale: Float) {
    getPreferences(context).edit()
        .putFloat(
            KEY_KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE,
            scale.coerceIn(
                KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN,
                KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX
            )
        )
        .apply()
}

fun SettingsManager.getKeyboardTheme(context: Context, target: KeyboardThemeTarget): KeyboardThemeSettings {
    val defaults = defaultKeyboardTheme(target)
    val stored = getPreferences(context).getString(keyboardThemeKeyForTarget(target), null)
        ?: return defaults
    return try {
        val json = JSONObject(stored)
        KeyboardThemeSettings(
            background = json.optInt("background", defaults.background),
            divider = json.optInt("divider", defaults.divider),
            normalKey = json.optInt("normal_key", defaults.normalKey),
            specialKey = json.optInt("special_key", defaults.specialKey),
            textAndIcons = json.optInt("text_and_icons", defaults.textAndIcons),
            ledInactive = json.optInt("led_inactive", defaults.ledInactive),
            ledActive = json.optInt("led_active", defaults.ledActive),
            ledLocked = json.optInt("led_locked", defaults.ledLocked),
            accent = json.optInt("accent", defaults.accent),
            cursorSwipe = json.optInt("cursor_swipe", defaults.cursorSwipe),
            keyPopup = json.optInt("key_popup", defaults.keyPopup),
            keyPopupSelected = json.optInt("key_popup_selected", defaults.keyPopupSelected),
            suggestion = json.optInt("suggestion", defaults.suggestion),
            statusBarButton = json.optInt("status_bar_button", defaults.statusBarButton),
            keyCornerRadiusRatio = json.optDouble("key_corner_radius_ratio", defaults.keyCornerRadiusRatio.toDouble()).toFloat(),
            chromeCornerRadiusRatio = json.optDouble("chrome_corner_radius_ratio", defaults.chromeCornerRadiusRatio.toDouble()).toFloat(),
            keyHeightScale = json.optDouble("key_height_scale", defaults.keyHeightScale.toDouble()).toFloat(),
            numberRowHeightScale = json.optDouble("number_row_height_scale", defaults.numberRowHeightScale.toDouble()).toFloat(),
            keyWidthScale = json.optDouble("key_width_scale", defaults.keyWidthScale.toDouble()).toFloat(),
            rowGapScale = json.optDouble("row_gap_scale", defaults.rowGapScale.toDouble()).toFloat(),
            distributeHorizontalSpacing = json.optBoolean("distribute_horizontal_spacing", defaults.distributeHorizontalSpacing),
            ortholinear = json.optBoolean("ortholinear", defaults.ortholinear),
            showLeds = json.optBoolean("show_leds", defaults.showLeds),
            suggestionsHeightScale = json.optDouble("suggestions_height_scale", defaults.suggestionsHeightScale.toDouble()).toFloat(),
            variationsHeightScale = json.optDouble("variations_height_scale", defaults.variationsHeightScale.toDouble()).toFloat(),
            keyPopupStyle = normalizeKeyboardThemePopupStyle(json.optString("key_popup_style", defaults.keyPopupStyle)),
            keyPopupAttached = json.optBoolean("key_popup_attached", defaults.keyPopupAttached),
            keyPopupTailEnabled = json.optBoolean("key_popup_tail_enabled", defaults.keyPopupTailEnabled),
            keyPreviewAfterLongPress = json.optBoolean("key_preview_after_long_press", defaults.keyPreviewAfterLongPress),
            keyAlternatesPopupEnabled = json.optBoolean("key_alternates_popup_enabled", defaults.keyAlternatesPopupEnabled)
        )
    } catch (error: Exception) {
        Log.e(TAG, "Fehler beim Laden des Keyboard-Themes", error)
        defaults
    }
}

fun SettingsManager.getKeyboardThemeAssignmentMode(context: Context, target: KeyboardThemeTarget): String {
    val stored = getPreferences(context).getString(
        keyboardThemeAssignmentModeKeyForTarget(target),
        // Until chosen, the keyboard follows the system's dark or light mode
        KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
    )
    return if (stored == KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
        KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
    } else {
        KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED
    }
}

fun SettingsManager.setKeyboardThemeAssignmentMode(context: Context, target: KeyboardThemeTarget, mode: String) {
    val normalized = if (mode == KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
        KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
    } else {
        KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED
    }
    getPreferences(context).edit()
        .putString(keyboardThemeAssignmentModeKeyForTarget(target), normalized)
        .apply()
}

fun SettingsManager.getKeyboardThemeSystemSlot(
    context: Context,
    target: KeyboardThemeTarget,
    dark: Boolean
): KeyboardThemeSettings {
    val defaults = defaultSystemKeyboardTheme(target, dark)
    val key = if (dark) keyboardThemeDarkKeyForTarget(target) else keyboardThemeLightKeyForTarget(target)
    val stored = getPreferences(context).getString(key, null) ?: return defaults
    return try {
        keyboardThemeFromJson(JSONObject(stored), defaults)
    } catch (error: Exception) {
        Log.e(TAG, "Fehler beim Laden des System-Keyboard-Themes", error)
        defaults
    }
}

fun SettingsManager.setKeyboardThemeSystemSlot(
    context: Context,
    target: KeyboardThemeTarget,
    dark: Boolean,
    theme: KeyboardThemeSettings
) {
    val key = if (dark) keyboardThemeDarkKeyForTarget(target) else keyboardThemeLightKeyForTarget(target)
    getPreferences(context).edit()
        .putString(key, keyboardThemeToJson(theme).toString())
        .apply()
}

fun SettingsManager.isSystemDarkTheme(context: Context): Boolean {
    val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
}

fun SettingsManager.getEffectiveKeyboardTheme(context: Context, target: KeyboardThemeTarget): KeyboardThemeSettings {
    return getEffectiveKeyboardTheme(context, target, locale = null, layout = null)
}

fun SettingsManager.getEffectiveKeyboardTheme(
    context: Context,
    target: KeyboardThemeTarget,
    locale: String?,
    layout: String?
): KeyboardThemeSettings {
    LegacyMigrations.removedBuiltInThemes(context)
    val theme = findKeyboardThemeLayoutOverride(context, target, locale, layout)?.theme
        ?: if (getKeyboardThemeAssignmentMode(context, target) == KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
            getKeyboardThemeSystemSlot(context, target, dark = isSystemDarkTheme(context))
        } else {
            getKeyboardTheme(context, target)
        }
    // Flux Keyboard: colours from the wallpaper, over whichever theme applies
    val coloured = if (getKeyboardWallpaperColours(context)) WallpaperKeyboardColours.recolour(context, theme) else theme
    // Flux Keyboard: a picture behind the keyboard, keys shaded against it
    val luminance = KeyboardBackgroundImage.luminance(context) ?: return coloured
    return KeyboardBackgroundImage.recolour(
        coloured, luminance, getKeyboardBackgroundAutoColours(context), getKeyboardBackgroundKeyOpacity(context),
        KeyboardBackgroundImage.averageColour(context)
    )
}

/** Over a background picture: keys see-through and shaded against it. */
fun SettingsManager.getKeyboardBackgroundAutoColours(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_KEYBOARD_BACKGROUND_AUTO_COLOURS, true)

fun SettingsManager.setKeyboardBackgroundAutoColours(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_KEYBOARD_BACKGROUND_AUTO_COLOURS, enabled).apply()
}

/** How solid the keys are over a background picture, in percent. */
fun SettingsManager.getKeyboardBackgroundKeyOpacity(context: Context): Int =
    getPreferences(context).getInt(KEY_KEYBOARD_BACKGROUND_KEY_OPACITY, KEYBOARD_BACKGROUND_KEY_OPACITY_DEFAULT).coerceIn(0, 100)

fun SettingsManager.setKeyboardBackgroundKeyOpacity(context: Context, percent: Int) {
    getPreferences(context).edit().putInt(KEY_KEYBOARD_BACKGROUND_KEY_OPACITY, percent.coerceIn(0, 100)).apply()
}

/** Where the background picture sits behind the keyboard. */
fun SettingsManager.getKeyboardBackgroundFraming(context: Context): KeyboardBackgroundImage.Framing =
    KeyboardBackgroundImage.Framing.decode(getPreferences(context).getString(KEY_KEYBOARD_BACKGROUND_FRAMING, null))

fun SettingsManager.setKeyboardBackgroundFraming(context: Context, framing: KeyboardBackgroundImage.Framing) {
    getPreferences(context).edit().putString(KEY_KEYBOARD_BACKGROUND_FRAMING, framing.encode()).apply()
}

/** Tells the keyboard the picture changed. */
fun SettingsManager.touchKeyboardBackgroundImage(context: Context) {
    getPreferences(context).edit().putLong(KEY_KEYBOARD_BACKGROUND_UPDATED, System.currentTimeMillis()).apply()
}

/** Keyboard colours from the wallpaper (Material You, Android 12+). */
fun SettingsManager.getKeyboardWallpaperColours(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_KEYBOARD_WALLPAPER_COLOURS, false)

fun SettingsManager.setKeyboardWallpaperColours(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_KEYBOARD_WALLPAPER_COLOURS, enabled).apply()
}

fun SettingsManager.getKeyboardThemeLayoutOverrides(
    context: Context,
    target: KeyboardThemeTarget
): List<KeyboardThemeLayoutOverride> {
    val stored = getPreferences(context).getString(keyboardThemeLayoutOverridesKeyForTarget(target), null)
        ?: return emptyList()
    return try {
        val array = JSONArray(stored)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val locale = item.optString("locale", "").trim().takeIf { it.isNotBlank() }
                val layout = item.optString("layout", "").trim().takeIf { it.isNotBlank() }
                if (locale == null && layout == null) continue
                val themeObject = item.optJSONObject("theme") ?: continue
                add(
                    KeyboardThemeLayoutOverride(
                        locale = locale?.let(::normalizeKeyboardThemeOverrideLocale),
                        layout = layout,
                        theme = keyboardThemeFromJson(themeObject, defaultKeyboardTheme(target))
                    )
                )
            }
        }
    } catch (error: Exception) {
        Log.e(TAG, "Fehler beim Laden der Keyboard-Theme-Overrides", error)
        emptyList()
    }
}

fun SettingsManager.setKeyboardThemeLayoutOverrides(
    context: Context,
    target: KeyboardThemeTarget,
    overrides: List<KeyboardThemeLayoutOverride>
) {
    val array = JSONArray()
    overrides
        .mapNotNull { override ->
            val locale = override.locale?.let(::normalizeKeyboardThemeOverrideLocale)?.takeIf { it.isNotBlank() }
            val layout = override.layout?.trim()?.takeIf { it.isNotBlank() }
            if (locale == null && layout == null) {
                null
            } else {
                JSONObject().apply {
                    if (locale != null) put("locale", locale)
                    if (layout != null) put("layout", layout)
                    put("theme", keyboardThemeToJson(override.theme))
                }
            }
        }
        .forEach { array.put(it) }

    getPreferences(context).edit()
        .putString(keyboardThemeLayoutOverridesKeyForTarget(target), array.toString())
        .apply()
}

fun SettingsManager.upsertKeyboardThemeLayoutOverride(
    context: Context,
    target: KeyboardThemeTarget,
    locale: String?,
    layout: String?,
    theme: KeyboardThemeSettings
) {
    val normalizedLocale = locale?.let(::normalizeKeyboardThemeOverrideLocale)?.takeIf { it.isNotBlank() }
    val normalizedLayout = layout?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedLocale == null && normalizedLayout == null) return
    val updated = getKeyboardThemeLayoutOverrides(context, target)
        .filterNot { it.locale == normalizedLocale && it.layout == normalizedLayout }
        .toMutableList()
    updated += KeyboardThemeLayoutOverride(normalizedLocale, normalizedLayout, theme)
    setKeyboardThemeLayoutOverrides(context, target, updated)
}

fun SettingsManager.removeKeyboardThemeLayoutOverride(
    context: Context,
    target: KeyboardThemeTarget,
    locale: String?,
    layout: String?
) {
    val normalizedLocale = locale?.let(::normalizeKeyboardThemeOverrideLocale)?.takeIf { it.isNotBlank() }
    val normalizedLayout = layout?.trim()?.takeIf { it.isNotBlank() }
    val updated = getKeyboardThemeLayoutOverrides(context, target)
        .filterNot { it.locale == normalizedLocale && it.layout == normalizedLayout }
    setKeyboardThemeLayoutOverrides(context, target, updated)
}

private fun SettingsManager.findKeyboardThemeLayoutOverride(
    context: Context,
    target: KeyboardThemeTarget,
    locale: String?,
    layout: String?
): KeyboardThemeLayoutOverride? {
    val normalizedLocale = locale?.let(::normalizeKeyboardThemeOverrideLocale)?.takeIf { it.isNotBlank() }
    val normalizedLanguage = normalizedLocale?.substringBefore('-')
    val normalizedLayout = layout?.trim()?.takeIf { it.isNotBlank() }
    return getKeyboardThemeLayoutOverrides(context, target)
        .mapNotNull { override ->
            val score = keyboardThemeOverrideMatchScore(
                override = override,
                locale = normalizedLocale,
                language = normalizedLanguage,
                layout = normalizedLayout
            )
            score?.let { override to it }
        }
        .maxByOrNull { it.second }
        ?.first
}

private fun SettingsManager.keyboardThemeOverrideMatchScore(
    override: KeyboardThemeLayoutOverride,
    locale: String?,
    language: String?,
    layout: String?
): Int? {
    var score = 0
    override.locale?.let { overrideLocale ->
        val overrideLanguage = overrideLocale.substringBefore('-')
        score += when {
            locale != null && overrideLocale.equals(locale, ignoreCase = true) -> 16
            language != null && overrideLanguage.equals(language, ignoreCase = true) -> 8
            else -> return null
        }
    }
    override.layout?.let { overrideLayout ->
        if (layout == null || !overrideLayout.equals(layout, ignoreCase = true)) return null
        score += 4
    }
    return if (score > 0) score else null
}

private fun SettingsManager.normalizeKeyboardThemeOverrideLocale(locale: String): String =
    locale.trim().replace('_', '-')

fun SettingsManager.setKeyboardTheme(
    context: Context,
    target: KeyboardThemeTarget,
    theme: KeyboardThemeSettings
) {
    val json = keyboardThemeToJson(theme)
    getPreferences(context).edit()
        .putString(keyboardThemeKeyForTarget(target), json.toString())
        .apply()
}

fun SettingsManager.keyboardThemeToJsonString(theme: KeyboardThemeSettings): String =
    keyboardThemeToJson(theme).toString()

fun SettingsManager.keyboardThemeFromJsonString(value: String): KeyboardThemeSettings? {
    return try {
        val json = JSONObject(value)
        if (!hasSupportedKeyboardThemeSchema(json)) return null
        keyboardThemeFromJson(json, defaultKeyboardTheme())
    } catch (error: Exception) {
        Log.e(TAG, "Fehler beim Importieren des Keyboard-Themes", error)
        null
    }
}

private fun SettingsManager.hasSupportedKeyboardThemeSchema(json: JSONObject): Boolean {
    val requiredIntegerKeys = listOf(
        "background",
        "divider",
        "normal_key",
        "special_key",
        "text_and_icons",
        "led_inactive",
        "led_active",
        "led_locked",
        "accent",
        "cursor_swipe",
        "key_popup",
        "key_popup_selected",
        "suggestion",
        "status_bar_button"
    )
    val requiredFloatKeys = listOf(
        "key_corner_radius_ratio",
        "chrome_corner_radius_ratio",
        "key_height_scale",
        "key_width_scale",
        "row_gap_scale"
    )
    val optionalFloatKeys = listOf(
        "number_row_height_scale",
        "suggestions_height_scale",
        "variations_height_scale"
    )
    val requiredBooleanKeys = listOf(
        "distribute_horizontal_spacing",
        "ortholinear",
        "show_leds"
    )
    val optionalBooleanKeys = listOf(
        "key_popup_attached",
        "key_popup_tail_enabled",
        "key_preview_after_long_press",
        "key_alternates_popup_enabled"
    )

    if (!requiredIntegerKeys.all { key -> json.opt(key).isJsonInt() }) return false
    if (!requiredFloatKeys.all { key -> json.opt(key).isFiniteJsonNumber() }) return false
    if (!requiredBooleanKeys.all { key -> json.opt(key) is Boolean }) return false
    if (!optionalFloatKeys.all { key -> !json.has(key) || json.opt(key).isFiniteJsonNumber() }) return false
    if (!optionalBooleanKeys.all { key -> !json.has(key) || json.opt(key) is Boolean }) return false
    return !json.has("key_popup_style") || json.opt("key_popup_style") in setOf(
            KEYBOARD_THEME_POPUP_STYLE_FLOATING,
            KEYBOARD_THEME_POPUP_STYLE_CLASSIC
        )
}

fun SettingsManager.getSavedKeyboardThemes(context: Context): List<NamedKeyboardTheme> {
    val stored = getPreferences(context).getString(KEY_KEYBOARD_THEME_SAVED_THEMES, null)
        ?: return emptyList()
    return try {
        val array = JSONArray(stored)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val themeObject = item.optJSONObject("theme") ?: continue
                if (name.isNotEmpty()) {
                    add(NamedKeyboardTheme(name, keyboardThemeFromJson(themeObject, defaultKeyboardTheme())))
                }
            }
        }
    } catch (error: Exception) {
        Log.e(TAG, "Fehler beim Laden gespeicherter Keyboard-Themes", error)
        emptyList()
    }
}

fun SettingsManager.saveKeyboardTheme(
    context: Context,
    name: String,
    theme: KeyboardThemeSettings
) {
    val normalizedName = name.trim().ifEmpty { "Custom" }
    val themes = getSavedKeyboardThemes(context)
        .filterNot { it.name.equals(normalizedName, ignoreCase = true) } +
        NamedKeyboardTheme(normalizedName, theme)
    persistSavedKeyboardThemes(context, themes)
}

fun SettingsManager.deleteKeyboardTheme(context: Context, name: String) {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return

    val themes = getSavedKeyboardThemes(context)
        .filterNot { it.name.equals(normalizedName, ignoreCase = true) }
    persistSavedKeyboardThemes(context, themes)
}

fun SettingsManager.getKeyboardThemeDrafts(context: Context): List<KeyboardThemeDraft> {
    val stored = getPreferences(context).getString(KEY_KEYBOARD_THEME_DRAFTS, null)
        ?: return emptyList()
    return try {
        val array = JSONArray(stored)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val themeObject = item.optJSONObject("theme") ?: continue
                val populatedArray = item.optJSONArray("populated_fields")
                val populatedFields = buildSet {
                    if (populatedArray != null) {
                        for (fieldIndex in 0 until populatedArray.length()) {
                            populatedArray.optString(fieldIndex).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }
                if (name.isNotEmpty()) {
                    add(
                        KeyboardThemeDraft(
                            name = name,
                            theme = keyboardThemeFromJson(themeObject, defaultKeyboardTheme()),
                            populatedFields = populatedFields
                        )
                    )
                }
            }
        }
    } catch (error: Exception) {
        Log.e(TAG, "Fehler beim Laden gespeicherter Keyboard-Theme-Entwürfe", error)
        emptyList()
    }
}

fun SettingsManager.saveKeyboardThemeDraft(context: Context, draft: KeyboardThemeDraft) {
    val normalizedName = draft.name.trim().ifEmpty { "Untitled theme" }
    val drafts = getKeyboardThemeDrafts(context)
        .filterNot { it.name.equals(normalizedName, ignoreCase = true) } +
        draft.copy(name = normalizedName)
    persistKeyboardThemeDrafts(context, drafts)
}

fun SettingsManager.deleteKeyboardThemeDraft(context: Context, name: String) {
    val drafts = getKeyboardThemeDrafts(context)
        .filterNot { it.name.equals(name.trim(), ignoreCase = true) }
    persistKeyboardThemeDrafts(context, drafts)
}

private fun SettingsManager.persistKeyboardThemeDrafts(context: Context, drafts: List<KeyboardThemeDraft>) {
    val array = JSONArray().apply {
        drafts.forEach { draft ->
            put(JSONObject().apply {
                put("name", draft.name)
                put("theme", keyboardThemeToJson(draft.theme))
                put("populated_fields", JSONArray(draft.populatedFields.toList()))
            })
        }
    }
    getPreferences(context).edit()
        .putString(KEY_KEYBOARD_THEME_DRAFTS, array.toString())
        .apply()
}

private fun SettingsManager.persistSavedKeyboardThemes(
    context: Context,
    themes: List<NamedKeyboardTheme>
) {
    val array = JSONArray().apply {
        themes.forEach { savedTheme ->
            put(JSONObject().apply {
                put("name", savedTheme.name)
                put("theme", keyboardThemeToJson(savedTheme.theme))
            })
        }
    }
    getPreferences(context).edit()
        .putString(KEY_KEYBOARD_THEME_SAVED_THEMES, array.toString())
        .apply()
}

private fun SettingsManager.keyboardThemeFromJson(
    json: JSONObject,
    defaults: KeyboardThemeSettings
): KeyboardThemeSettings =
    KeyboardThemeSettings(
        background = json.optInt("background", defaults.background),
        divider = json.optInt("divider", defaults.divider),
        normalKey = json.optInt("normal_key", defaults.normalKey),
        specialKey = json.optInt("special_key", defaults.specialKey),
        textAndIcons = json.optInt("text_and_icons", defaults.textAndIcons),
        ledInactive = json.optInt("led_inactive", defaults.ledInactive),
        ledActive = json.optInt("led_active", defaults.ledActive),
        ledLocked = json.optInt("led_locked", defaults.ledLocked),
        accent = json.optInt("accent", defaults.accent),
        cursorSwipe = json.optInt("cursor_swipe", defaults.cursorSwipe),
        keyPopup = json.optInt("key_popup", defaults.keyPopup),
        keyPopupSelected = json.optInt("key_popup_selected", defaults.keyPopupSelected),
        suggestion = json.optInt("suggestion", defaults.suggestion),
        statusBarButton = json.optInt("status_bar_button", defaults.statusBarButton),
        keyCornerRadiusRatio = json.optDouble("key_corner_radius_ratio", defaults.keyCornerRadiusRatio.toDouble()).toFloat(),
        chromeCornerRadiusRatio = json.optDouble("chrome_corner_radius_ratio", defaults.chromeCornerRadiusRatio.toDouble()).toFloat(),
        keyHeightScale = json.optDouble("key_height_scale", defaults.keyHeightScale.toDouble()).toFloat(),
        numberRowHeightScale = json.optDouble("number_row_height_scale", defaults.numberRowHeightScale.toDouble()).toFloat(),
        keyWidthScale = json.optDouble("key_width_scale", defaults.keyWidthScale.toDouble()).toFloat(),
        rowGapScale = json.optDouble("row_gap_scale", defaults.rowGapScale.toDouble()).toFloat(),
        distributeHorizontalSpacing = json.optBoolean("distribute_horizontal_spacing", defaults.distributeHorizontalSpacing),
        ortholinear = json.optBoolean("ortholinear", defaults.ortholinear),
        showLeds = json.optBoolean("show_leds", defaults.showLeds),
        suggestionsHeightScale = json.optDouble("suggestions_height_scale", defaults.suggestionsHeightScale.toDouble()).toFloat(),
        variationsHeightScale = json.optDouble("variations_height_scale", defaults.variationsHeightScale.toDouble()).toFloat(),
        keyPopupStyle = normalizeKeyboardThemePopupStyle(json.optString("key_popup_style", defaults.keyPopupStyle)),
        keyPopupAttached = json.optBoolean("key_popup_attached", defaults.keyPopupAttached),
        keyPopupTailEnabled = json.optBoolean("key_popup_tail_enabled", defaults.keyPopupTailEnabled),
        keyPreviewAfterLongPress = json.optBoolean("key_preview_after_long_press", defaults.keyPreviewAfterLongPress),
        keyAlternatesPopupEnabled = json.optBoolean("key_alternates_popup_enabled", defaults.keyAlternatesPopupEnabled)
    )

private fun SettingsManager.keyboardThemeToJson(theme: KeyboardThemeSettings): JSONObject =
    JSONObject().apply {
        put("background", theme.background)
        put("divider", theme.divider)
        put("normal_key", theme.normalKey)
        put("special_key", theme.specialKey)
        put("text_and_icons", theme.textAndIcons)
        put("led_inactive", theme.ledInactive)
        put("led_active", theme.ledActive)
        put("led_locked", theme.ledLocked)
        put("accent", theme.accent)
        put("cursor_swipe", theme.cursorSwipe)
        put("key_popup", theme.keyPopup)
        put("key_popup_selected", theme.keyPopupSelected)
        put("suggestion", theme.suggestion)
        put("status_bar_button", theme.statusBarButton)
        put("key_corner_radius_ratio", theme.keyCornerRadiusRatio.toDouble())
        put("chrome_corner_radius_ratio", theme.chromeCornerRadiusRatio.toDouble())
        put("key_height_scale", theme.keyHeightScale.toDouble())
        put("number_row_height_scale", theme.numberRowHeightScale.toDouble())
        put("key_width_scale", theme.keyWidthScale.toDouble())
        put("row_gap_scale", theme.rowGapScale.toDouble())
        put("distribute_horizontal_spacing", theme.distributeHorizontalSpacing)
        put("ortholinear", theme.ortholinear)
        put("show_leds", theme.showLeds)
        put("suggestions_height_scale", theme.suggestionsHeightScale.toDouble())
        put("variations_height_scale", theme.variationsHeightScale.toDouble())
        put("key_popup_style", normalizeKeyboardThemePopupStyle(theme.keyPopupStyle))
        put("key_popup_attached", theme.keyPopupAttached)
        put("key_popup_tail_enabled", theme.keyPopupTailEnabled)
        put("key_preview_after_long_press", theme.keyPreviewAfterLongPress)
        put("key_alternates_popup_enabled", theme.keyAlternatesPopupEnabled)
    }

private fun SettingsManager.normalizeKeyboardThemePopupStyle(value: String): String =
    when (value) {
        KEYBOARD_THEME_POPUP_STYLE_CLASSIC -> KEYBOARD_THEME_POPUP_STYLE_CLASSIC
        else -> KEYBOARD_THEME_POPUP_STYLE_FLOATING
    }
