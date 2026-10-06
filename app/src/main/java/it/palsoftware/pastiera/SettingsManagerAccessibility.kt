package it.palsoftware.pastiera

import android.content.Context

// SettingsManager: accessibility announcements, bounce keys and overlapping keys. The keys and defaults live in SettingsManager.kt.

/**
 * Returns whether Ctrl+letter app shortcuts should be resolved through
 * the keyboard's active layout before being passed to the target app.
 */
fun SettingsManager.getLayoutAwareCtrlShortcutsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_LAYOUT_AWARE_CTRL_SHORTCUTS,
        DEFAULT_LAYOUT_AWARE_CTRL_SHORTCUTS
    )
}

/**
 * Sets whether Ctrl+letter app shortcuts should use the keyboard's active layout.
 */
fun SettingsManager.setLayoutAwareCtrlShortcutsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_LAYOUT_AWARE_CTRL_SHORTCUTS, enabled)
        .apply()
}

/**
 * Returns whether accessibility live announcements are enabled.
 */
fun SettingsManager.getAccessibilityLiveAnnouncementsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED,
        DEFAULT_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED
    )
}

/**
 * Sets whether accessibility live announcements are enabled.
 */
fun SettingsManager.setAccessibilityLiveAnnouncementsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED, enabled)
        .apply()
}

/**
 * Returns whether accessibility should read the second IME row
 * (quick settings and variations).
 */
fun SettingsManager.getAccessibilityReadSecondRowEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED,
        DEFAULT_ACCESSIBILITY_READ_SECOND_ROW_ENABLED
    )
}

/**
 * Sets whether accessibility should read the second IME row
 * (quick settings and variations).
 */
fun SettingsManager.setAccessibilityReadSecondRowEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED, enabled)
        .apply()
}

/**
 * Returns the delay before suggestion row accessibility is re-enabled after updates.
 */
fun SettingsManager.getAccessibilitySuggestionsAnnouncementDelayMs(context: Context): Long {
    return getPreferences(context).getLong(
        KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS,
        DEFAULT_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS
    )
}

/**
 * Sets the delay before suggestion row accessibility is re-enabled after updates.
 */
fun SettingsManager.setAccessibilitySuggestionsAnnouncementDelayMs(context: Context, delayMs: Long) {
    val clamped = delayMs.coerceIn(
        MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS,
        MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS
    )
    getPreferences(context).edit()
        .putLong(KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS, clamped)
        .apply()
}

/**
 * Returns the optional global layout id used for variation ordering across all layouts.
 * Returns null when no override is configured.
 */
fun SettingsManager.getGlobalVariationLayoutOverride(context: Context): String? {
    val stored = getPreferences(context).getString(
        KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE,
        DEFAULT_GLOBAL_VARIATION_LAYOUT_OVERRIDE
    )?.trim().orEmpty()
    return stored.ifEmpty { null }
}

/**
 * Sets the optional global layout id used for variation ordering.
 * Pass null/blank to disable the override and use per-layout behavior.
 */
fun SettingsManager.setGlobalVariationLayoutOverride(context: Context, layoutName: String?) {
    val normalized = layoutName?.trim().orEmpty()
    val editor = getPreferences(context).edit()
    if (normalized.isEmpty()) {
        editor.remove(KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE)
    } else {
        editor.putString(KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE, normalized)
    }
    editor.apply()
    notifyVariationsUpdated(context)
}

fun SettingsManager.getMinAccessibilitySuggestionsAnnouncementDelayMs(): Long =
    MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS

fun SettingsManager.getMaxAccessibilitySuggestionsAnnouncementDelayMs(): Long =
    MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS

fun SettingsManager.getBounceKeysEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_BOUNCE_KEYS_ENABLED,
        DEFAULT_BOUNCE_KEYS_ENABLED
    )
}

fun SettingsManager.setBounceKeysEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BOUNCE_KEYS_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getBounceKeysDelayMs(context: Context): Long {
    return getPreferences(context).getLong(
        KEY_BOUNCE_KEYS_DELAY_MS,
        DEFAULT_BOUNCE_KEYS_DELAY_MS
    ).coerceIn(MIN_BOUNCE_KEYS_DELAY_MS, MAX_BOUNCE_KEYS_DELAY_MS)
}

fun SettingsManager.setBounceKeysDelayMs(context: Context, delayMs: Long) {
    getPreferences(context).edit()
        .putLong(
            KEY_BOUNCE_KEYS_DELAY_MS,
            delayMs.coerceIn(MIN_BOUNCE_KEYS_DELAY_MS, MAX_BOUNCE_KEYS_DELAY_MS)
        )
        .apply()
}

fun SettingsManager.getMinBounceKeysDelayMs(): Long = MIN_BOUNCE_KEYS_DELAY_MS

fun SettingsManager.getMaxBounceKeysDelayMs(): Long = MAX_BOUNCE_KEYS_DELAY_MS

fun SettingsManager.getBounceKeysCharacterKeysEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED,
        DEFAULT_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED
    )
}

fun SettingsManager.setBounceKeysCharacterKeysEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getBounceKeysModifierKeysEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED,
        DEFAULT_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED
    )
}

fun SettingsManager.setBounceKeysModifierKeysEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getBounceKeysSpaceEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_BOUNCE_KEYS_SPACE_ENABLED,
        DEFAULT_BOUNCE_KEYS_SPACE_ENABLED
    )
}

fun SettingsManager.setBounceKeysSpaceEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BOUNCE_KEYS_SPACE_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getBounceKeysEnterEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_BOUNCE_KEYS_ENTER_ENABLED,
        DEFAULT_BOUNCE_KEYS_ENTER_ENABLED
    )
}

fun SettingsManager.setBounceKeysEnterEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BOUNCE_KEYS_ENTER_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getBounceKeysBackspaceEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_BOUNCE_KEYS_BACKSPACE_ENABLED,
        DEFAULT_BOUNCE_KEYS_BACKSPACE_ENABLED
    )
}

fun SettingsManager.setBounceKeysBackspaceEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BOUNCE_KEYS_BACKSPACE_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getOverlappingKeysEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_OVERLAPPING_KEYS_ENABLED,
        DEFAULT_OVERLAPPING_KEYS_ENABLED
    )
}

fun SettingsManager.setOverlappingKeysEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_OVERLAPPING_KEYS_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getBounceKeysCategoryEnabled(
    context: Context,
    category: it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category
): Boolean {
    return when (category) {
        it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category.CHARACTER ->
            getBounceKeysCharacterKeysEnabled(context)
        it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category.MODIFIER ->
            getBounceKeysModifierKeysEnabled(context)
        it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category.SPACE ->
            getBounceKeysSpaceEnabled(context)
        it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category.ENTER ->
            getBounceKeysEnterEnabled(context)
        it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category.BACKSPACE ->
            getBounceKeysBackspaceEnabled(context)
        it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter.Category.UNSUPPORTED -> false
    }
}
