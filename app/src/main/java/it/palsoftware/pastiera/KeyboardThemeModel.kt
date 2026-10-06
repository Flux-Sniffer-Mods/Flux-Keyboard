package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors

internal data class KeyboardThemePreset(
    val name: String,
    val background: Int,
    val divider: Int,
    val normalKey: Int,
    val specialKey: Int,
    val textAndIcons: Int,
    val ledInactive: Int,
    val ledActive: Int,
    val ledLocked: Int,
    val accent: Int,
    val cursorSwipe: Int = accent,
    val keyPopup: Int = specialKey,
    val keyPopupSelected: Int = accent,
    val suggestion: Int = normalKey,
    val statusBarButton: Int = specialKey,
    val keyCornerRadiusRatio: Float = 0.08f,
    val chromeCornerRadiusRatio: Float = 0.08f,
    val keyHeightScale: Float = 1f,
    val numberRowHeightScale: Float = 0.8f,
    val keyWidthScale: Float = 1f,
    val rowGapScale: Float = 0f,
    val distributeHorizontalSpacing: Boolean = true,
    val ortholinear: Boolean = false,
    val showLeds: Boolean = true,
    val suggestionsHeightScale: Float = 1f,
    val variationsHeightScale: Float = 1f,
    val keepsSoftwareGeometry: Boolean = false,
    val keyPopupStyle: String = SettingsManager.KEYBOARD_THEME_POPUP_STYLE_FLOATING,
    val keyPopupAttached: Boolean = true,
    val keyPopupTailEnabled: Boolean = true,
    val keyPreviewAfterLongPress: Boolean = false,
    val keyAlternatesPopupEnabled: Boolean = true
)

internal data class KeyboardThemeOption(
    val key: String,
    val preset: KeyboardThemePreset,
    val resetPreset: KeyboardThemePreset,
    val userSaved: Boolean = false
)

internal const val SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS = 0.19f
internal const val SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS = 0.20f
internal const val SOFTWARE_THEME_DEFAULT_KEY_HEIGHT = 1.5489256f
internal const val SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT = 0.8f
internal const val SOFTWARE_THEME_DEFAULT_ROW_GAP = 0.47933885f
internal const val SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT = 0.8982954f
internal const val SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT = 0.95914257f

internal fun KeyboardThemePreset.withSoftwareKeyboardDefaults(): KeyboardThemePreset =
    if (keepsSoftwareGeometry) {
        this
    } else {
        copy(
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

internal fun KeyboardThemePreset.toAospThemeOverride(): AospKeyboardView.ThemeOverride =
    AospKeyboardView.ThemeOverride(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        numberRowHeightScale = numberRowHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        keyPopupStyle = keyPopupStyle,
        keyPopupAttached = keyPopupAttached,
        keyPopupTailEnabled = keyPopupTailEnabled,
        keyPreviewAfterLongPress = keyPreviewAfterLongPress,
        keyAlternatesPopupEnabled = keyAlternatesPopupEnabled
    )

internal fun KeyboardThemePreset.toKeyboardThemeColors(): KeyboardThemeColors =
    KeyboardThemeColors(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        suggestionsHeightScale = suggestionsHeightScale,
        variationsHeightScale = variationsHeightScale
    )

internal fun KeyboardThemePreset.toSettingsTheme(): SettingsManager.KeyboardThemeSettings =
    SettingsManager.KeyboardThemeSettings(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        numberRowHeightScale = numberRowHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        showLeds = showLeds,
        suggestionsHeightScale = suggestionsHeightScale,
        variationsHeightScale = variationsHeightScale,
        keyPopupStyle = keyPopupStyle,
        keyPopupAttached = keyPopupAttached,
        keyPopupTailEnabled = keyPopupTailEnabled,
        keyPreviewAfterLongPress = keyPreviewAfterLongPress,
        keyAlternatesPopupEnabled = keyAlternatesPopupEnabled
    )

internal fun SettingsManager.KeyboardThemeSettings.toKeyboardThemePreset(name: String): KeyboardThemePreset =
    KeyboardThemePreset(
        name = name,
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        numberRowHeightScale = numberRowHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        showLeds = showLeds,
        suggestionsHeightScale = suggestionsHeightScale,
        variationsHeightScale = variationsHeightScale,
        keyPopupStyle = keyPopupStyle,
        keyPopupAttached = keyPopupAttached,
        keyPopupTailEnabled = keyPopupTailEnabled,
        keyPreviewAfterLongPress = keyPreviewAfterLongPress,
        keyAlternatesPopupEnabled = keyAlternatesPopupEnabled
    )

internal fun SettingsManager.NamedKeyboardTheme.toKeyboardThemeOption(): KeyboardThemeOption {
    val preset = theme.toKeyboardThemePreset(name)
    return KeyboardThemeOption(
        key = "saved:$name",
        preset = preset,
        resetPreset = preset,
        userSaved = true
    )
}

internal fun keyboardThemeSwatches(): List<Int> = keyboardThemePresets()
    .flatMap { preset ->
        listOf(
            preset.background,
            preset.divider,
            preset.normalKey,
            preset.specialKey,
            preset.textAndIcons,
            preset.ledInactive,
            preset.ledActive,
            preset.ledLocked,
            preset.cursorSwipe,
            preset.keyPopup,
            preset.keyPopupSelected,
            preset.suggestion,
            preset.statusBarButton
        )
    }
    .distinct()

/** The built-in themes: Classic Cloud (light) and Classic Midnight (dark). */
internal fun keyboardThemePresets(): List<KeyboardThemePreset> = listOf(
    KeyboardThemePreset("Classic Cloud", 0xFFCCD2DC.toInt(), 0xFF9EA5AF.toInt(), 0xFFFFFFFF.toInt(), 0xFFAFB6C2.toInt(), 0xFF000000.toInt(), 0xFFAEB5C0.toInt(), 0xFF007AFF.toInt(), 0xFFFF9500.toInt(), 0xFF007AFF.toInt(), 0xFF007AFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF007AFF.toInt(), 0xFFCCD2DC.toInt(), 0xFFAFB6C2.toInt(), 0.118f, 0.09f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, false, false, 0.9f, 0.88f, true, SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC),
    KeyboardThemePreset("Classic Midnight", 0xFF1C1C1E.toInt(), 0xFF4A4A4D.toInt(), 0xFF3A3A3C.toInt(), 0xFF2C2C2E.toInt(), 0xFFFFFFFF.toInt(), 0xFF404044.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFF0A84FF.toInt(), 0xFF0A84FF.toInt(), 0xFF3A3A3C.toInt(), 0xFF0A84FF.toInt(), 0xFF202124.toInt(), 0xFF2C2C2E.toInt(), 0.118f, 0.09f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, false, false, 0.9f, 0.88f, true, SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC)
)

