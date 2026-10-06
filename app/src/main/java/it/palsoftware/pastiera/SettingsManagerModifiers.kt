package it.palsoftware.pastiera

import android.content.Context

// SettingsManager: modifier keys: long press, tap latches, double-tap locks. The keys and defaults live in SettingsManager.kt.

/**
 * Returns the long-press threshold in milliseconds.
 */
fun SettingsManager.getLongPressThreshold(context: Context): Long {
    return getPreferences(context).getLong(KEY_LONG_PRESS_THRESHOLD, DEFAULT_LONG_PRESS_THRESHOLD)
}

/**
 * Sets the long-press threshold in milliseconds.
 * The value is automatically clamped between MIN and MAX.
 */
fun SettingsManager.setLongPressThreshold(context: Context, threshold: Long) {
    val clampedValue = threshold.coerceIn(MIN_LONG_PRESS_THRESHOLD, MAX_LONG_PRESS_THRESHOLD)
    getPreferences(context).edit()
        .putLong(KEY_LONG_PRESS_THRESHOLD, clampedValue)
        .apply()
}

fun SettingsManager.getShiftTapLatches(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_SHIFT_TAP_LATCHES,
        DEFAULT_MODIFIER_TAP_LATCHES
    )
}

fun SettingsManager.setShiftTapLatches(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SHIFT_TAP_LATCHES, enabled)
        .apply()
}

fun SettingsManager.getAltTapLatches(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_ALT_TAP_LATCHES,
        DEFAULT_MODIFIER_TAP_LATCHES
    )
}

fun SettingsManager.setAltTapLatches(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ALT_TAP_LATCHES, enabled)
        .apply()
}

fun SettingsManager.getCtrlTapLatches(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_CTRL_TAP_LATCHES,
        DEFAULT_MODIFIER_TAP_LATCHES
    )
}

fun SettingsManager.setCtrlTapLatches(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CTRL_TAP_LATCHES, enabled)
        .apply()
}

/** Two quick taps lock the modifier (Caps Lock for Shift); off, the second tap lets it go. */
fun SettingsManager.getShiftDoubleTapLocks(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SHIFT_DOUBLE_TAP_LOCKS, true)

fun SettingsManager.setShiftDoubleTapLocks(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SHIFT_DOUBLE_TAP_LOCKS, enabled).apply()
}

fun SettingsManager.getAltDoubleTapLocks(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_ALT_DOUBLE_TAP_LOCKS, true)

fun SettingsManager.setAltDoubleTapLocks(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_ALT_DOUBLE_TAP_LOCKS, enabled).apply()
}

fun SettingsManager.getCtrlDoubleTapLocks(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_CTRL_DOUBLE_TAP_LOCKS, true)

fun SettingsManager.setCtrlDoubleTapLocks(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_CTRL_DOUBLE_TAP_LOCKS, enabled).apply()
}

fun SettingsManager.getAltLatchStaysOnSpace(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_ALT_LATCH_STAYS_ON_SPACE,
        DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE
    )
}

fun SettingsManager.setAltLatchStaysOnSpace(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ALT_LATCH_STAYS_ON_SPACE, enabled)
        .apply()
}

fun SettingsManager.getCtrlLatchStaysOnSpace(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_CTRL_LATCH_STAYS_ON_SPACE,
        DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE
    )
}

fun SettingsManager.setCtrlLatchStaysOnSpace(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CTRL_LATCH_STAYS_ON_SPACE, enabled)
        .apply()
}

/**
 * Returns the minimum allowed value for the long-press threshold.
 */
fun SettingsManager.getMinLongPressThreshold(): Long = MIN_LONG_PRESS_THRESHOLD

/**
 * Returns the maximum allowed value for the long-press threshold.
 */
fun SettingsManager.getMaxLongPressThreshold(): Long = MAX_LONG_PRESS_THRESHOLD

/** Smart toggle: Alt lock switches off after an opening quote or bracket typed with Alt. */
fun SettingsManager.getSmartAltOffAfterOpening(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SMART_ALT_OFF_AFTER_OPENING, false)

fun SettingsManager.setSmartAltOffAfterOpening(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SMART_ALT_OFF_AFTER_OPENING, enabled).apply()
}

/** Smart toggle: a tapped Ctrl latch switches off after one shortcut (not after cursor moves). */
fun SettingsManager.getSmartCtrlOffAfterShortcut(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SMART_CTRL_OFF_AFTER_SHORTCUT, true)

fun SettingsManager.setSmartCtrlOffAfterShortcut(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SMART_CTRL_OFF_AFTER_SHORTCUT, enabled).apply()
}

/**
 * Returns the long-press action, including optional fixed SYM key layers.
 */
fun SettingsManager.getLongPressModifier(context: Context): String {
    val stored = getPreferences(context).getString(KEY_LONG_PRESS_MODIFIER, DEFAULT_LONG_PRESS_MODIFIER)
        ?: DEFAULT_LONG_PRESS_MODIFIER
    return when (stored) {
        "alt", "shift", "variations", "sym", "sym_symbols", "sym_emoji" -> stored
        else -> DEFAULT_LONG_PRESS_MODIFIER
    }
}

/**
 * Sets the long-press action.
 */
fun SettingsManager.setLongPressModifier(context: Context, modifier: String) {
    val validModifier = when (modifier) {
        "shift" -> "shift"
        "variations" -> "variations"
        "sym" -> "sym"
        "sym_symbols" -> "sym_symbols"
        "sym_emoji" -> "sym_emoji"
        else -> "alt"
    }
    getPreferences(context).edit()
        .putString(KEY_LONG_PRESS_MODIFIER, validModifier)
        .apply()
}

/** Returns 1 for Emoji and 2 for Symbols. */
fun SettingsManager.resolveLongPressSymPage(context: Context): Int = when (getLongPressModifier(context)) {
    "sym_emoji" -> 1
    "sym_symbols" -> 2
    else -> if (getSymPagesConfig(context).prefersEmojiLongPressLayer()) 1 else 2
}
