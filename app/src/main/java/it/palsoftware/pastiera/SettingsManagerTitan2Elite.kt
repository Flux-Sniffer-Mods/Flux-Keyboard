package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

// SettingsManager: the Titan 2 Elite: rounded corners, status bar lift and its other device options. The keys and defaults live in SettingsManager.kt.

fun SettingsManager.isTitan2LayoutEnabled(context: Context): Boolean {
    val prefs = getPreferences(context)
    if (prefs.contains(KEY_TITAN2_LAYOUT_ENABLED)) {
        return prefs.getBoolean(KEY_TITAN2_LAYOUT_ENABLED, false)
    }
    return false // Flux Keyboard: centred by default; aligning is an option
}

fun SettingsManager.setTitan2LayoutEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TITAN2_LAYOUT_ENABLED, enabled)
        .apply()
}

fun SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(
        KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS,
        DeviceSpecific.isTitan2EliteDevice()
    )

fun SettingsManager.getTitan2EliteTopCornerMultiplier(context: Context): Int =
    getPreferences(context).getInt(KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER, 2).let {
        when (it) { 1, 4, 6 -> it; else -> 2 }
    }

fun SettingsManager.setTitan2EliteTopCornerMultiplier(context: Context, multiplier: Int) {
    getPreferences(context).edit()
        .putInt(KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER, when (multiplier) { 1, 4, 6 -> multiplier; else -> 2 })
        .apply()
}

fun SettingsManager.getTitan2EliteMaxIconShrink(context: Context): Int =
    getPreferences(context).getInt(KEY_TITAN2_ELITE_MAX_ICON_SHRINK, 90).coerceIn(0, 90)

fun SettingsManager.setTitan2EliteMaxIconShrink(context: Context, percent: Int) {
    getPreferences(context).edit().putInt(KEY_TITAN2_ELITE_MAX_ICON_SHRINK, percent.coerceIn(0, 90)).apply()
}

fun SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS, enabled)
        .apply()
}

/**
 * Paint the keyboard background into the display's rounded corners instead of clipping to them.
 * On by default on a Titan 2 Elite. Off while the LEDs are contoured: the rail runs along the
 * display curve, which a filled corner would cover.
 */
fun SettingsManager.getTitan2EliteFillCorners(context: Context): Boolean =
    !getTitan2EliteContourLeds(context) &&
        getPreferences(context).getBoolean(KEY_TITAN2_ELITE_FILL_CORNERS, DeviceSpecific.isTitan2EliteDevice())

fun SettingsManager.setTitan2EliteFillCorners(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TITAN2_ELITE_FILL_CORNERS, enabled).apply()
}

/**
 * Titan 2 Elite corner style. Contoured LEDs (true): the outer
 * buttons follow the display curve and one LED rail runs round it beneath them. Straight
 * buttons (false, the default): the outer buttons reach straight down into the corners, and the bar is
 * lifted [TITAN2_ELITE_DEFAULT_LIFT_DP] above the LEDs running along the corners.
 */
fun SettingsManager.getTitan2EliteContourLeds(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_TITAN2_ELITE_CONTOUR_LEDS, false)

fun SettingsManager.setTitan2EliteContourLeds(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_TITAN2_ELITE_CONTOUR_LEDS, enabled).apply()
}

/** Straight outer buttons: the corner style that isn't contoured LEDs. */
fun SettingsManager.getTitan2EliteStraightOuterButtons(context: Context): Boolean = !getTitan2EliteContourLeds(context)

/** How far the status bar sits above the modifier LEDs, in dp: fixed with straight buttons, none when contoured. */
fun SettingsManager.getTitan2EliteStatusBarLiftDp(context: Context): Int =
    if (getTitan2EliteContourLeds(context)) 0 else TITAN2_ELITE_DEFAULT_LIFT_DP

fun SettingsManager.getTitan2EliteStatusBarLiftPx(context: Context): Int =
    Math.round(getTitan2EliteStatusBarLiftDp(context) * context.resources.displayMetrics.density)

/**
 * Enables the calibrated rounded-corner layout once for Titan 2 Elite users receiving this
 * migration. Later user changes remain authoritative because the marker prevents reapplying it.
 */
fun SettingsManager.enforceTitan2EliteRoundedCornersOnce(context: Context) {
    val prefs = getPreferences(context)
    if (prefs.getBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNERS_ENFORCED_V1, false)) return

    prefs.edit().apply {
        if (DeviceSpecific.isTitan2EliteDevice()) {
            putBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS, true)
        }
        putBoolean(KEY_TITAN2_ELITE_ROUNDED_CORNERS_ENFORCED_V1, true)
    }.apply()
}
