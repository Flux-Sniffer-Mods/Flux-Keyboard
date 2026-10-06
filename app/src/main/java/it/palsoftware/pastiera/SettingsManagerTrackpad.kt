package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.SettingsManager.TrackpadAppMode

// SettingsManager: the trackpad: gestures, suggestion swipes, swipe to delete and per-app capture. The keys and defaults live in SettingsManager.kt.

/**
 * Returns the swipe incremental threshold in DIP.
 * This is the distance that must be traveled to move the cursor one position.
 */
fun SettingsManager.getSwipeIncrementalThreshold(context: Context): Float {
    return getPreferences(context).getFloat(KEY_SWIPE_INCREMENTAL_THRESHOLD, DEFAULT_SWIPE_INCREMENTAL_THRESHOLD)
}

/**
 * Sets the swipe incremental threshold in DIP.
 * The value is automatically clamped between MIN and MAX.
 */
fun SettingsManager.setSwipeIncrementalThreshold(context: Context, threshold: Float) {
    val clampedValue = threshold.coerceIn(MIN_SWIPE_INCREMENTAL_THRESHOLD, MAX_SWIPE_INCREMENTAL_THRESHOLD)
    getPreferences(context).edit()
        .putFloat(KEY_SWIPE_INCREMENTAL_THRESHOLD, clampedValue)
        .apply()
}

/**
 * Returns the minimum allowed value for the swipe incremental threshold.
 */
fun SettingsManager.getMinSwipeIncrementalThreshold(): Float = MIN_SWIPE_INCREMENTAL_THRESHOLD

/**
 * Returns the maximum allowed value for the swipe incremental threshold.
 */
fun SettingsManager.getMaxSwipeIncrementalThreshold(): Float = MAX_SWIPE_INCREMENTAL_THRESHOLD

/**
 * Returns the state of swipe-to-delete.
 */
fun SettingsManager.getSwipeToDelete(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SWIPE_TO_DELETE, DEFAULT_SWIPE_TO_DELETE)
}

/**
 * Sets the state of swipe-to-delete.
 */
fun SettingsManager.setSwipeToDelete(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SWIPE_TO_DELETE, enabled)
        .apply()
}

/** Left, up and right swipes pick the left, middle and right suggestion, wherever they start. */
fun SettingsManager.getTrackpadSuggestionSwipeDirections(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TRACKPAD_SUGGESTION_SWIPE_DIRECTIONS, false)

fun SettingsManager.setTrackpadSuggestionSwipeDirections(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TRACKPAD_SUGGESTION_SWIPE_DIRECTIONS, enabled).apply()
}

/** A swipe down on the trackpad deletes the previous word. */
fun SettingsManager.getTrackpadSwipeDownDeletesWord(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TRACKPAD_SWIPE_DOWN_DELETES_WORD, false)

fun SettingsManager.setTrackpadSwipeDownDeletesWord(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TRACKPAD_SWIPE_DOWN_DELETES_WORD, enabled).apply()
}

fun SettingsManager.getSwipeToDeleteProvider(context: Context): String {
    val value = getPreferences(context).getString(
        KEY_SWIPE_TO_DELETE_PROVIDER,
        DEFAULT_SWIPE_TO_DELETE_PROVIDER
    ).orEmpty()
    return if (SWIPE_TO_DELETE_PROVIDER_VALUES.contains(value)) {
        value
    } else {
        DEFAULT_SWIPE_TO_DELETE_PROVIDER
    }
}

fun SettingsManager.setSwipeToDeleteProvider(context: Context, provider: String) {
    val normalized = if (SWIPE_TO_DELETE_PROVIDER_VALUES.contains(provider)) {
        provider
    } else {
        DEFAULT_SWIPE_TO_DELETE_PROVIDER
    }
    getPreferences(context).edit()
        .putString(KEY_SWIPE_TO_DELETE_PROVIDER, normalized)
        .commit()
}

/** Password managers' chips (logins, one-time codes) in the suggestion bar, Android 11+. */
/** Keyboard swipes stay with the keyboard while a field is typed in, not the app (on by default). */
fun SettingsManager.getTrackpadCaptureWhileTyping(context: Context): Boolean =
    getPreferences(context).getBoolean("trackpad_capture_while_typing", true)

fun SettingsManager.setTrackpadCaptureWhileTyping(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("trackpad_capture_while_typing", enabled).apply()
}

fun SettingsManager.getTrackpadAppMode(context: Context): TrackpadAppMode {
    val id = getPreferences(context).getString("trackpad_app_mode", null)
    return TrackpadAppMode.entries.firstOrNull { it.id == id } ?: TrackpadAppMode.OFF
}

fun SettingsManager.setTrackpadAppMode(context: Context, mode: TrackpadAppMode) {
    getPreferences(context).edit().putString("trackpad_app_mode", mode.id).apply()
    it.palsoftware.pastiera.clicks.ClicksAccessibilityKeyBridge.refreshTrackpadClaim()
}

fun SettingsManager.getTrackpadApps(context: Context): List<String> =
    parsePackageList(getPreferences(context).getString("trackpad_apps", null) ?: "")

fun SettingsManager.setTrackpadApps(context: Context, packages: Collection<String>) {
    val clean = packages.joinToString("\n").let(::parsePackageList)
    getPreferences(context).edit().putString("trackpad_apps", clean.joinToString("\n")).apply()
    it.palsoftware.pastiera.clicks.ClicksAccessibilityKeyBridge.refreshTrackpadClaim()
}

/** Whether keyboard swipes are kept from [packageName] (it doesn't scroll with them). */
fun SettingsManager.trackpadBlockedIn(context: Context, packageName: String): Boolean = when (getTrackpadAppMode(context)) {
    TrackpadAppMode.OFF -> false
    TrackpadAppMode.BLOCK -> packageName in getTrackpadApps(context)
    TrackpadAppMode.KEEP -> packageName !in getTrackpadApps(context)
}

/**
 * Returns whether trackpad gesture suggestions are enabled.
 * @param context The context
 * @return Whether trackpad gestures are enabled
 */
fun SettingsManager.getTrackpadGesturesEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_TRACKPAD_GESTURES_ENABLED, DEFAULT_TRACKPAD_GESTURES_ENABLED)
}

/**
 * Sets whether trackpad gesture suggestions are enabled.
 * @param context The context
 * @param enabled Whether to enable trackpad gestures
 */
fun SettingsManager.setTrackpadGesturesEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TRACKPAD_GESTURES_ENABLED, enabled)
        .commit()  // Use commit() instead of apply() to ensure synchronous write
}

fun SettingsManager.getTrackpadGestureAddWordEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_TRACKPAD_GESTURE_ADD_WORD_ENABLED,
        DEFAULT_TRACKPAD_GESTURE_ADD_WORD_ENABLED
    )
}

fun SettingsManager.setTrackpadGestureAddWordEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TRACKPAD_GESTURE_ADD_WORD_ENABLED, enabled)
        .commit()
}

fun SettingsManager.getTrackpadGestureAddWordFullWidthEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_TRACKPAD_GESTURE_ADD_WORD_FULL_WIDTH_ENABLED,
        DEFAULT_TRACKPAD_GESTURE_ADD_WORD_FULL_WIDTH_ENABLED
    )
}

fun SettingsManager.setTrackpadGestureAddWordFullWidthEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TRACKPAD_GESTURE_ADD_WORD_FULL_WIDTH_ENABLED, enabled)
        .commit()
}

fun SettingsManager.getMinTrackpadSwipeThreshold(): Float = MIN_TRACKPAD_SWIPE_THRESHOLD

fun SettingsManager.getMaxTrackpadSwipeThreshold(): Float = MAX_TRACKPAD_SWIPE_THRESHOLD

fun SettingsManager.getTrackpadSuggestionSwipeThreshold(context: Context): Float {
    val prefs = getPreferences(context)
    return prefs.getFloat(
        KEY_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
        prefs.getFloat(KEY_TRACKPAD_SWIPE_THRESHOLD, DEFAULT_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD)
    ).coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
}

fun SettingsManager.setTrackpadSuggestionSwipeThreshold(context: Context, threshold: Float) {
    val clamped = threshold.coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
    getPreferences(context).edit()
        .putFloat(KEY_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD, clamped)
        .commit()
}

/**
 * How far a left or right swipe goes to take the left or right suggestion (Swipe
 * directions). Until set, the same as the suggestion swipe's default.
 */
fun SettingsManager.getTrackpadSideSwipeThreshold(context: Context): Float {
    val prefs = getPreferences(context)
    return prefs.getFloat(KEY_TRACKPAD_SIDE_SWIPE_THRESHOLD, DEFAULT_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD)
        .coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
}

fun SettingsManager.setTrackpadSideSwipeThreshold(context: Context, threshold: Float) {
    getPreferences(context).edit()
        .putFloat(KEY_TRACKPAD_SIDE_SWIPE_THRESHOLD, threshold.coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD))
        .apply()
}

fun SettingsManager.getTrackpadDeleteSwipeThreshold(context: Context): Float {
    val prefs = getPreferences(context)
    return prefs.getFloat(
        KEY_TRACKPAD_DELETE_SWIPE_THRESHOLD,
        prefs.getFloat(KEY_TRACKPAD_SWIPE_THRESHOLD, DEFAULT_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD)
    ).coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
}

fun SettingsManager.setTrackpadDeleteSwipeThreshold(context: Context, threshold: Float) {
    val clamped = threshold.coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
    getPreferences(context).edit()
        .putFloat(KEY_TRACKPAD_DELETE_SWIPE_THRESHOLD, clamped)
        .commit()
}

fun SettingsManager.getTrackpadProvider(context: Context): String {
    val value = getPreferences(context).getString(KEY_TRACKPAD_PROVIDER, DEFAULT_TRACKPAD_PROVIDER).orEmpty()
    return if (TRACKPAD_PROVIDER_VALUES.contains(value)) value else DEFAULT_TRACKPAD_PROVIDER
}

/** Where keyboard swipes come from, picked by hand (it then stays as picked). */
fun SettingsManager.setTrackpadProvider(context: Context, provider: String) {
    val normalized = if (TRACKPAD_PROVIDER_VALUES.contains(provider)) provider else DEFAULT_TRACKPAD_PROVIDER
    getPreferences(context).edit()
        .putString(KEY_TRACKPAD_PROVIDER, normalized)
        .putBoolean(KEY_TRACKPAD_PROVIDER_CHOSEN, true)
        .commit()
}

/**
 * Shizuku users get keyboard swipes through Shizuku (they then work in every app, Scroll
 * assistant or not), unless they picked a source by hand: on the Titan 2 phones, once Shizuku is
 * running and allowed. Returns whether it switched.
 */
fun SettingsManager.adoptShizukuTrackpadIfUnchosen(context: Context): Boolean {
    val prefs = getPreferences(context)
    if (prefs.getBoolean(KEY_TRACKPAD_PROVIDER_CHOSEN, false)) return false
    if (getTrackpadProvider(context) == TRACKPAD_PROVIDER_SHIZUKU) return false
    if (!it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2Device()) return false
    if (it.palsoftware.pastiera.resolveShizukuStatus() != it.palsoftware.pastiera.ShizukuStatus.Connected) return false
    prefs.edit().putString(KEY_TRACKPAD_PROVIDER, TRACKPAD_PROVIDER_SHIZUKU).apply()
    return true
}

fun SettingsManager.getTrackpadShizukuDevice(context: Context): String {
    val value = getPreferences(context)
        .getString(KEY_TRACKPAD_SHIZUKU_DEVICE, TRACKPAD_SHIZUKU_DEVICE_AUTO)
        .orEmpty()
    return if (value == TRACKPAD_SHIZUKU_DEVICE_AUTO || isTrackpadEventNode(value)) {
        value
    } else {
        TRACKPAD_SHIZUKU_DEVICE_AUTO
    }
}

fun SettingsManager.setTrackpadShizukuDevice(context: Context, device: String) {
    val normalized = if (
        device == TRACKPAD_SHIZUKU_DEVICE_AUTO || isTrackpadEventNode(device)
    ) {
        device
    } else {
        TRACKPAD_SHIZUKU_DEVICE_AUTO
    }
    getPreferences(context).edit()
        .putString(KEY_TRACKPAD_SHIZUKU_DEVICE, normalized)
        .apply()
}

private fun SettingsManager.isTrackpadEventNode(value: String): Boolean {
    return Regex("^/dev/input/event\\d+$").matches(value)
}
