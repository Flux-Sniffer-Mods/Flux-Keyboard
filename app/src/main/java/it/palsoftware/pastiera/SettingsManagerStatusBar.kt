package it.palsoftware.pastiera

import android.content.Context
import android.view.inputmethod.InputMethodManager
import org.json.JSONArray
import it.palsoftware.pastiera.SettingsManager.StatusBarSlotDefaults

// SettingsManager: the status bar, menu bar, modifier indicators and LED colours. The keys and defaults live in SettingsManager.kt.

/** Offer what you just copied as a suggestion to paste when you start typing in a field. */
/** Each status LED in its own colour (LedColors); off keeps the theme's LED colours. */
fun SettingsManager.getLedIndividualColorsEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_LED_INDIVIDUAL_COLORS, false)

fun SettingsManager.setLedIndividualColorsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_LED_INDIVIDUAL_COLORS, enabled).apply()
}

/** Locked LEDs sweep a gradient of their colour (off: a steady colour). */
fun SettingsManager.getLedLockedAnimationEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_LED_LOCKED_ANIMATION, false)

fun SettingsManager.setLedLockedAnimationEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_LED_LOCKED_ANIMATION, enabled).apply()
}

/** One LED's colour ([led]: shift, ctrl, alt or sym). */
fun SettingsManager.getLedColor(context: Context, led: String, default: Int): Int =
    getPreferences(context).getInt(LED_COLOR_KEY_PREFIX + led, default)

fun SettingsManager.setLedColor(context: Context, led: String, color: Int) {
    getPreferences(context).edit().putInt(LED_COLOR_KEY_PREFIX + led, color).apply()
}

/**
 * Gets the button assigned to the left slot.
 */
fun SettingsManager.getStatusBarSlotLeft(context: Context): String {
    return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_LEFT, DEFAULT_SLOT_LEFT)
        ?: DEFAULT_SLOT_LEFT
}

/**
 * Returns the default slot assignment for the status bar.
 */
fun SettingsManager.getDefaultStatusBarSlots(): StatusBarSlotDefaults {
    return StatusBarSlotDefaults(
        left = DEFAULT_SLOT_LEFT,
        right1 = DEFAULT_SLOT_RIGHT_1,
        right2 = DEFAULT_SLOT_RIGHT_2
    )
}

fun SettingsManager.getDefaultStatusBarSlotsLeft(): List<String> = listOf(DEFAULT_SLOT_LEFT)

fun SettingsManager.getDefaultStatusBarSlotsRight(): List<String> = listOf(DEFAULT_SLOT_RIGHT_1, DEFAULT_SLOT_RIGHT_2)

/**
 * Resets status bar slots to the defaults and returns the applied values.
 */
fun SettingsManager.resetStatusBarSlotsToDefault(context: Context): StatusBarSlotDefaults {
    val defaults = getDefaultStatusBarSlots()
    setStatusBarSlotLeft(context, defaults.left)
    setStatusBarSlotRight1(context, defaults.right1)
    setStatusBarSlotRight2(context, defaults.right2)
    setStatusBarSlotsLeft(context, getDefaultStatusBarSlotsLeft())
    setStatusBarSlotsRight(context, getDefaultStatusBarSlotsRight())
    setStatusBarVariationsVisible(context, DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE)
    setDynamicVariationBarSlotCount(context, DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
    setDynamicVariationBarResizeToContent(context, DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT)
    return defaults
}

fun SettingsManager.defaultMenuBarButtons(context: Context): List<String> =
    MENU_BAR_BUTTON_OPTIONS.filter { button ->
        button !in MENU_BAR_OFF_BY_DEFAULT &&
            !(button == STATUS_BAR_BUTTON_LANGUAGE && hasSingleInputLanguage(context))
    }

/** Whether only one input language is on for this keyboard (false when it can't tell). */
fun SettingsManager.hasSingleInputLanguage(context: Context): Boolean = runCatching {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
        as? android.view.inputmethod.InputMethodManager ?: return false
    val ime = imm.enabledInputMethodList.firstOrNull { it.packageName == context.packageName }
        ?: return false
    imm.getEnabledInputMethodSubtypeList(ime, true).size <= 1
}.getOrDefault(false)

/** The menu bar's buttons, in order (its close button always comes first). */
fun SettingsManager.getMenuBarButtons(context: Context): List<String> {
    val stored = getPreferences(context).getString(KEY_MENU_BAR_BUTTONS, null)
        ?: return defaultMenuBarButtons(context)
    return stored.split(',').map { it.trim() }.filter { it in MENU_BAR_BUTTON_OPTIONS }.distinct()
}

fun SettingsManager.setMenuBarButtons(context: Context, buttons: List<String>) {
    val clean = buttons.filter { it in MENU_BAR_BUTTON_OPTIONS }.distinct()
    getPreferences(context).edit().putString(KEY_MENU_BAR_BUTTONS, clean.joinToString(",")).apply()
}

fun SettingsManager.resetMenuBarButtons(context: Context) {
    getPreferences(context).edit().remove(KEY_MENU_BAR_BUTTONS).apply()
}

fun SettingsManager.getStatusBarSlotsLeft(context: Context): List<String> {
    return getStatusBarSlotsList(
        context = context,
        key = KEY_STATUS_BAR_SLOTS_LEFT,
        fallback = listOf(getStatusBarSlotLeft(context))
    )
}

fun SettingsManager.setStatusBarSlotsLeft(context: Context, buttonIds: List<String>) {
    val normalized = normalizeStatusBarSlots(buttonIds)
    getPreferences(context).edit()
        .putString(KEY_STATUS_BAR_SLOTS_LEFT, statusBarSlotsToJson(normalized))
        .putString(KEY_STATUS_BAR_SLOT_LEFT, normalized.firstOrNull() ?: STATUS_BAR_BUTTON_NONE)
        .apply()
}

fun SettingsManager.getStatusBarSlotsRight(context: Context): List<String> {
    return getStatusBarSlotsList(
        context = context,
        key = KEY_STATUS_BAR_SLOTS_RIGHT,
        fallback = listOf(getStatusBarSlotRight1(context), getStatusBarSlotRight2(context))
    )
}

fun SettingsManager.setStatusBarSlotsRight(context: Context, buttonIds: List<String>) {
    val normalized = normalizeStatusBarSlots(buttonIds)
    getPreferences(context).edit()
        .putString(KEY_STATUS_BAR_SLOTS_RIGHT, statusBarSlotsToJson(normalized))
        .putString(KEY_STATUS_BAR_SLOT_RIGHT_1, normalized.getOrNull(0) ?: STATUS_BAR_BUTTON_NONE)
        .putString(KEY_STATUS_BAR_SLOT_RIGHT_2, normalized.getOrNull(1) ?: STATUS_BAR_BUTTON_NONE)
        .apply()
}

fun SettingsManager.getDefaultPastierinaStatusBarSlotsLeft(): List<String> = listOf(DEFAULT_PASTIERINA_SLOT_LEFT)

fun SettingsManager.getDefaultPastierinaStatusBarSlotsRight(): List<String> = listOf(DEFAULT_PASTIERINA_SLOT_RIGHT)

fun SettingsManager.resetPastierinaStatusBarSlotsToDefault(context: Context) {
    setPastierinaStatusBarSlotsLeft(context, getDefaultPastierinaStatusBarSlotsLeft())
    setPastierinaStatusBarSlotsRight(context, getDefaultPastierinaStatusBarSlotsRight())
}

fun SettingsManager.getPastierinaStatusBarSlotsLeft(context: Context): List<String> {
    return getStatusBarSlotsList(
        context = context,
        key = KEY_PASTIERINA_STATUS_BAR_SLOTS_LEFT,
        fallback = getDefaultPastierinaStatusBarSlotsLeft()
    )
}

fun SettingsManager.setPastierinaStatusBarSlotsLeft(context: Context, buttonIds: List<String>) {
    val normalized = normalizeStatusBarSlots(buttonIds)
    getPreferences(context).edit()
        .putString(KEY_PASTIERINA_STATUS_BAR_SLOTS_LEFT, statusBarSlotsToJson(normalized))
        .apply()
}

fun SettingsManager.getPastierinaStatusBarSlotsRight(context: Context): List<String> {
    return getStatusBarSlotsList(
        context = context,
        key = KEY_PASTIERINA_STATUS_BAR_SLOTS_RIGHT,
        fallback = getDefaultPastierinaStatusBarSlotsRight()
    )
}

fun SettingsManager.setPastierinaStatusBarSlotsRight(context: Context, buttonIds: List<String>) {
    val normalized = normalizeStatusBarSlots(buttonIds)
    getPreferences(context).edit()
        .putString(KEY_PASTIERINA_STATUS_BAR_SLOTS_RIGHT, statusBarSlotsToJson(normalized))
        .apply()
}

/**
 * Sets the button for the left slot.
 */
fun SettingsManager.setStatusBarSlotLeft(context: Context, buttonId: String) {
    getPreferences(context).edit()
        .putString(KEY_STATUS_BAR_SLOT_LEFT, buttonId)
        .apply()
}

/**
 * Gets the button assigned to the first right slot.
 */
fun SettingsManager.getStatusBarSlotRight1(context: Context): String {
    return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_RIGHT_1, DEFAULT_SLOT_RIGHT_1)
        ?: DEFAULT_SLOT_RIGHT_1
}

/**
 * Sets the button for the first right slot.
 */
fun SettingsManager.setStatusBarSlotRight1(context: Context, buttonId: String) {
    getPreferences(context).edit()
        .putString(KEY_STATUS_BAR_SLOT_RIGHT_1, buttonId)
        .apply()
}

/**
 * Gets the button assigned to the second right slot.
 */
fun SettingsManager.getStatusBarSlotRight2(context: Context): String {
    return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_RIGHT_2, DEFAULT_SLOT_RIGHT_2)
        ?: DEFAULT_SLOT_RIGHT_2
}

/**
 * Sets the button for the second right slot.
 */
fun SettingsManager.setStatusBarSlotRight2(context: Context, buttonId: String) {
    getPreferences(context).edit()
        .putString(KEY_STATUS_BAR_SLOT_RIGHT_2, buttonId)
        .apply()
}

private fun SettingsManager.getStatusBarSlotsList(
    context: Context,
    key: String,
    fallback: List<String>
): List<String> {
    val stored = getPreferences(context).getString(key, null)
    if (!stored.isNullOrBlank()) {
        runCatching {
            val array = JSONArray(stored)
            val parsed = buildList {
                for (index in 0 until array.length()) {
                    add(array.optString(index, STATUS_BAR_BUTTON_NONE))
                }
            }
            return normalizeStatusBarSlots(parsed)
        }
    }
    return normalizeStatusBarSlots(fallback)
}

private fun SettingsManager.normalizeStatusBarSlots(buttonIds: List<String>): List<String> {
    val available = getAvailableStatusBarButtons().toSet()
    return buttonIds.map { buttonId ->
        if (buttonId in available) buttonId else STATUS_BAR_BUTTON_NONE
    }
}

private fun SettingsManager.statusBarSlotsToJson(buttonIds: List<String>): String {
    val array = JSONArray()
    buttonIds.forEach { array.put(it) }
    return array.toString()
}

fun SettingsManager.getStatusBarVariationsVisible(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_STATUS_BAR_VARIATIONS_VISIBLE,
        DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE
    )
}

fun SettingsManager.setStatusBarVariationsVisible(context: Context, visible: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_STATUS_BAR_VARIATIONS_VISIBLE, visible)
        .apply()
}

fun SettingsManager.areStatusBarVariationsEnabled(context: Context): Boolean {
    return getStatusBarVariationsVisible(context)
}

fun SettingsManager.setStatusBarVariationsEnabled(context: Context, enabled: Boolean) {
    setStatusBarVariationsVisible(context, enabled)
}

fun SettingsManager.getDynamicVariationBarSlotCount(context: Context): Int {
    return getPreferences(context).getInt(
        KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
        DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT
    ).coerceIn(MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT, MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
}

fun SettingsManager.setDynamicVariationBarSlotCount(context: Context, count: Int) {
    getPreferences(context).edit()
        .putInt(
            KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
            count.coerceIn(MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT, MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
        )
        .apply()
}

fun SettingsManager.getDynamicVariationBarResizeToContent(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT,
        DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT
    )
}

fun SettingsManager.setDynamicVariationBarResizeToContent(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT, enabled)
        .apply()
}

fun SettingsManager.getModifierIndicators(context: Context): Set<String> {
    return normalizeModifierIndicators(
        getPreferences(context).getString(
            KEY_MODIFIER_INDICATOR_MODE,
            encodeModifierIndicators(DEFAULT_MODIFIER_INDICATORS)
        )
    )
}

fun SettingsManager.setModifierIndicators(context: Context, indicators: Set<String>) {
    getPreferences(context).edit()
        .putString(KEY_MODIFIER_INDICATOR_MODE, encodeModifierIndicators(normalizeModifierIndicators(indicators)))
        .apply()
}

fun SettingsManager.getModifierIndicatorShowsBottomStrip(context: Context): Boolean {
    return MODIFIER_INDICATOR_BOTTOM_STRIP in getModifierIndicators(context)
}

fun SettingsManager.getModifierIndicatorShowsMenuBar(context: Context): Boolean {
    return MODIFIER_INDICATOR_MENU_BAR in getModifierIndicators(context)
}

fun SettingsManager.getModifierIndicatorShowsStatusBar(context: Context): Boolean {
    return MODIFIER_INDICATOR_STATUS_BAR in getModifierIndicators(context)
}

private fun SettingsManager.normalizeModifierIndicators(stored: String?): Set<String> {
    return when (stored) {
        MODIFIER_INDICATOR_MODE_OFF -> emptySet()
        MODIFIER_INDICATOR_MODE_BOTTOM -> setOf(MODIFIER_INDICATOR_BOTTOM_STRIP)
        MODIFIER_INDICATOR_MODE_BOTTOM_AND_MENU -> setOf(
            MODIFIER_INDICATOR_BOTTOM_STRIP,
            MODIFIER_INDICATOR_STATUS_BAR
        )
        MODIFIER_INDICATOR_MODE_MENU -> setOf(MODIFIER_INDICATOR_STATUS_BAR)
        else -> normalizeModifierIndicators(
            stored
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: DEFAULT_MODIFIER_INDICATORS
        )
    }
}

private fun SettingsManager.normalizeModifierIndicators(indicators: Set<String>): Set<String> {
    val allowed = setOf(
        MODIFIER_INDICATOR_BOTTOM_STRIP,
        MODIFIER_INDICATOR_MENU_BAR,
        MODIFIER_INDICATOR_STATUS_BAR
    )
    val normalized = indicators.filter { it in allowed }.toSet()
    return if (normalized.isEmpty() && indicators.isNotEmpty()) {
        DEFAULT_MODIFIER_INDICATORS
    } else {
        normalized
    }
}

private fun SettingsManager.encodeModifierIndicators(indicators: Set<String>): String {
    val order = listOf(
        MODIFIER_INDICATOR_BOTTOM_STRIP,
        MODIFIER_INDICATOR_MENU_BAR,
        MODIFIER_INDICATOR_STATUS_BAR
    )
    return order.filter { it in indicators }.joinToString(",")
}

/**
 * Returns all available button options for dropdown selection.
 */
fun SettingsManager.getAvailableStatusBarButtons(): List<String> {
    return listOf(
        STATUS_BAR_BUTTON_NONE,
        STATUS_BAR_BUTTON_CLIPBOARD,
        STATUS_BAR_BUTTON_EMOJI,
        STATUS_BAR_BUTTON_GIF,
        STATUS_BAR_BUTTON_MICROPHONE,
        STATUS_BAR_BUTTON_LANGUAGE,
        STATUS_BAR_BUTTON_HAMBURGER,
        STATUS_BAR_BUTTON_MINIMAL_UI,
        STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE,
        STATUS_BAR_BUTTON_SETTINGS,
        STATUS_BAR_BUTTON_SYMBOLS,
        STATUS_BAR_BUTTON_UNDO,
        STATUS_BAR_BUTTON_REDO
    )
}
