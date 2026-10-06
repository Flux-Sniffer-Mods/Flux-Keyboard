package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.SettingsManager.StatusBarPresentationMode
import it.palsoftware.pastiera.SettingsManager.SoftwareKeyboardMode
import it.palsoftware.pastiera.SettingsManager.SoftwareKeyboardLayoutStyle
import it.palsoftware.pastiera.SettingsManager.SoftwareKeyboardModifierKey

// SettingsManager: the status bar presentation and the software (on-screen) keyboard. The keys and defaults live in SettingsManager.kt.

fun SettingsManager.getStatusBarPresentationMode(context: Context): StatusBarPresentationMode {
    val value = getPreferences(context).getString(
        KEY_PASTIERINA_MODE_OVERRIDE,
        StatusBarPresentationMode.FULL_STATUS_BAR.storageValue
    )
    return when (value) {
        StatusBarPresentationMode.PASTIERINA.storageValue,
        "force_minimal" -> StatusBarPresentationMode.PASTIERINA
        else -> StatusBarPresentationMode.FULL_STATUS_BAR
    }
}

fun SettingsManager.setStatusBarPresentationMode(context: Context, mode: StatusBarPresentationMode) {
    getPreferences(context).edit()
        .putString(KEY_PASTIERINA_MODE_OVERRIDE, mode.storageValue)
        .apply()
}

fun SettingsManager.getPastierinaModeActive(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_PASTIERINA_MODE_ACTIVE, false)
}

fun SettingsManager.setPastierinaModeActive(context: Context, isActive: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_PASTIERINA_MODE_ACTIVE, isActive)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardMode(context: Context): SoftwareKeyboardMode {
    val value = getPreferences(context).getString(
        KEY_SOFTWARE_KEYBOARD_MODE,
        SoftwareKeyboardMode.AUTO.storageValue
    )
    return SoftwareKeyboardMode.values().firstOrNull { it.storageValue == value }
        ?: SoftwareKeyboardMode.AUTO
}

fun SettingsManager.setSoftwareKeyboardMode(context: Context, mode: SoftwareKeyboardMode) {
    getPreferences(context).edit()
        .putString(KEY_SOFTWARE_KEYBOARD_MODE, mode.storageValue)
        .remove(KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardModeRuntimeOverride(context: Context): SoftwareKeyboardMode? {
    val value = getPreferences(context).getString(
        KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE,
        null
    ) ?: return null
    return SoftwareKeyboardMode.values()
        .firstOrNull { it.storageValue == value && it != SoftwareKeyboardMode.AUTO }
}

fun SettingsManager.setSoftwareKeyboardModeRuntimeOverride(
    context: Context,
    mode: SoftwareKeyboardMode?
) {
    val editor = getPreferences(context).edit()
    if (mode == null || mode == SoftwareKeyboardMode.AUTO) {
        editor.remove(KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE)
    } else {
        editor.putString(KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE, mode.storageValue)
    }
    editor.apply()
}

fun SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS,
        DEFAULT_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS
    )
}

fun SettingsManager.setSoftwareKeyboardModeToggleToastsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS, enabled)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardLayoutStyle(context: Context): SoftwareKeyboardLayoutStyle {
    val value = getPreferences(context).getString(
        KEY_SOFTWARE_KEYBOARD_LAYOUT_STYLE,
        SoftwareKeyboardLayoutStyle.COMPACT.storageValue
    )
    return SoftwareKeyboardLayoutStyle.values().firstOrNull { it.storageValue == value }
        ?: SoftwareKeyboardLayoutStyle.COMPACT
}

fun SettingsManager.setSoftwareKeyboardLayoutStyle(context: Context, style: SoftwareKeyboardLayoutStyle) {
    getPreferences(context).edit()
        .putString(KEY_SOFTWARE_KEYBOARD_LAYOUT_STYLE, style.storageValue)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardNumberRowEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SOFTWARE_KEYBOARD_NUMBER_ROW_ENABLED, true)

fun SettingsManager.setSoftwareKeyboardNumberRowEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SOFTWARE_KEYBOARD_NUMBER_ROW_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardNearestKeyTouchEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SOFTWARE_KEYBOARD_NEAREST_KEY_TOUCH_ENABLED, true)

fun SettingsManager.setSoftwareKeyboardNearestKeyTouchEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SOFTWARE_KEYBOARD_NEAREST_KEY_TOUCH_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(
        KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED,
        DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED
    )

fun SettingsManager.setSoftwareKeyboardLongPressLayerPopupEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context: Context): Boolean =
    getPreferences(context).getBoolean(
        KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY,
        DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY
    )

fun SettingsManager.setSoftwareKeyboardLongPressLayerPopupBelowKey(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY, enabled)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardLeftModifierKey(context: Context): SoftwareKeyboardModifierKey =
    getSoftwareKeyboardModifierKey(
        context = context,
        key = KEY_SOFTWARE_KEYBOARD_LEFT_MODIFIER_KEY,
        defaultValue = SoftwareKeyboardModifierKey.CTRL
    )

fun SettingsManager.setSoftwareKeyboardLeftModifierKey(context: Context, modifierKey: SoftwareKeyboardModifierKey) {
    getPreferences(context).edit()
        .putString(KEY_SOFTWARE_KEYBOARD_LEFT_MODIFIER_KEY, modifierKey.storageValue)
        .apply()
}

fun SettingsManager.getSoftwareKeyboardRightModifierKey(context: Context): SoftwareKeyboardModifierKey =
    getSoftwareKeyboardModifierKey(
        context = context,
        key = KEY_SOFTWARE_KEYBOARD_RIGHT_MODIFIER_KEY,
        defaultValue = SoftwareKeyboardModifierKey.ALT
    )

fun SettingsManager.setSoftwareKeyboardRightModifierKey(context: Context, modifierKey: SoftwareKeyboardModifierKey) {
    getPreferences(context).edit()
        .putString(KEY_SOFTWARE_KEYBOARD_RIGHT_MODIFIER_KEY, modifierKey.storageValue)
        .apply()
}

private fun SettingsManager.getSoftwareKeyboardModifierKey(
    context: Context,
    key: String,
    defaultValue: SoftwareKeyboardModifierKey
): SoftwareKeyboardModifierKey {
    val value = getPreferences(context).getString(key, defaultValue.storageValue)
    return SoftwareKeyboardModifierKey.values().firstOrNull { it.storageValue == value }
        ?: defaultValue
}

fun SettingsManager.resolveEffectiveSoftwareKeyboardMode(context: Context): SoftwareKeyboardMode {
    getSoftwareKeyboardModeRuntimeOverride(context)?.let { return it }
    val configured = getSoftwareKeyboardMode(context)
    if (configured != SoftwareKeyboardMode.AUTO) {
        return configured
    }
    return it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardAutoDetector.resolve(context)
}
