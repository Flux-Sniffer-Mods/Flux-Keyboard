package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import it.palsoftware.pastiera.core.Punctuation
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString

// SettingsManager: auto-capitalisation, punctuation spacing, smart quotes and the dash. The keys and defaults live in SettingsManager.kt.

/**
 * Returns the state of auto-capitalization for the first letter.
 */
fun SettingsManager.getAutoCapitalizeFirstLetter(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER)
}

/**
 * Sets the state of auto-capitalization for the first letter.
 */
fun SettingsManager.setAutoCapitalizeFirstLetter(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, enabled)
        .apply()
}

fun SettingsManager.getAutoCapitalizeRespectManualShiftOff(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF,
        DEFAULT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF
    )
}

fun SettingsManager.setAutoCapitalizeRespectManualShiftOff(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF, enabled)
        .apply()
}

fun SettingsManager.getAutoCapitalizeRestrictedFields(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_AUTO_CAPITALIZE_RESTRICTED_FIELDS,
        DEFAULT_AUTO_CAPITALIZE_RESTRICTED_FIELDS
    )
}

fun SettingsManager.setAutoCapitalizeRestrictedFields(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_AUTO_CAPITALIZE_RESTRICTED_FIELDS, enabled)
        .apply()
}

/**
 * Returns the state of auto-capitalization after period.
 */
/** A text emoticon (:) ;D <3) ends a sentence, so the next word gets a capital (on by default). */
fun SettingsManager.getAutoCapAfterEmoticon(context: Context): Boolean =
    getPreferences(context).getBoolean("auto_cap_after_emoticon", true)

fun SettingsManager.setAutoCapAfterEmoticon(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean("auto_cap_after_emoticon", enabled).apply()
    it.palsoftware.pastiera.core.EmoticonSentences.enabled = enabled
}

fun SettingsManager.getAutoCapitalizeAfterPeriod(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_AFTER_PERIOD, DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD)
}

/**
 * Sets the state of auto-capitalization after period.
 */
fun SettingsManager.setAutoCapitalizeAfterPeriod(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_AUTO_CAPITALIZE_AFTER_PERIOD, enabled)
        .apply()
}

/**
 * Returns the state of the double-space-to-period feature.
 */
fun SettingsManager.getDoubleSpaceToPeriod(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_DOUBLE_SPACE_TO_PERIOD, DEFAULT_DOUBLE_SPACE_TO_PERIOD)
}

/**
 * Sets the state of the double-space-to-period feature.
 */
fun SettingsManager.setDoubleSpaceToPeriod(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_DOUBLE_SPACE_TO_PERIOD, enabled)
        .apply()
}

/**
 * Returns the state of spaced-hyphen-to-en-dash smart punctuation.
 */
fun SettingsManager.getSpacedHyphenToEnDash(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SPACED_HYPHEN_TO_EN_DASH, DEFAULT_SPACED_HYPHEN_TO_EN_DASH)
}

/**
 * Sets the state of spaced-hyphen-to-en-dash smart punctuation.
 */
fun SettingsManager.setSpacedHyphenToEnDash(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SPACED_HYPHEN_TO_EN_DASH, enabled)
        .apply()
}

fun SettingsManager.getSpacedHyphenDashStyle(context: Context): String {
    val stored = getPreferences(context).getString(KEY_SPACED_HYPHEN_DASH_STYLE, DEFAULT_SPACED_HYPHEN_DASH_STYLE)
    return when (stored) {
        DASH_STYLE_EN,
        DASH_STYLE_EM -> stored
        else -> DEFAULT_SPACED_HYPHEN_DASH_STYLE
    }
}

fun SettingsManager.setSpacedHyphenDashStyle(context: Context, style: String) {
    getPreferences(context).edit()
        .putString(
            KEY_SPACED_HYPHEN_DASH_STYLE,
            when (style) {
                DASH_STYLE_EN,
                DASH_STYLE_EM -> style
                else -> DEFAULT_SPACED_HYPHEN_DASH_STYLE
            }
        )
        .apply()
}

fun SettingsManager.getMidWordQuoteToApostrophe(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_MID_WORD_QUOTE_TO_APOSTROPHE,
        DEFAULT_MID_WORD_QUOTE_TO_APOSTROPHE
    )
}

fun SettingsManager.setMidWordQuoteToApostrophe(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_MID_WORD_QUOTE_TO_APOSTROPHE, enabled)
        .apply()
}

fun SettingsManager.getFrenchPunctuationSpacing(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_FRENCH_PUNCTUATION_SPACING,
        DEFAULT_FRENCH_PUNCTUATION_SPACING
    )
}

fun SettingsManager.setFrenchPunctuationSpacing(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_FRENCH_PUNCTUATION_SPACING, enabled)
        .apply()
}

fun SettingsManager.getFrenchPunctuationOnlyFrenchLayouts(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_FRENCH_PUNCTUATION_ONLY_FRENCH,
        DEFAULT_FRENCH_PUNCTUATION_ONLY_FRENCH
    )
}

fun SettingsManager.setFrenchPunctuationOnlyFrenchLayouts(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_FRENCH_PUNCTUATION_ONLY_FRENCH, enabled)
        .apply()
}

fun SettingsManager.shouldApplyFrenchPunctuationSpacing(context: Context): Boolean {
    if (!getFrenchPunctuationSpacing(context)) return false
    if (!getFrenchPunctuationOnlyFrenchLayouts(context)) return true
    return currentImeLanguage(context) == "fr"
}

private fun SettingsManager.currentImeLanguage(context: Context): String? {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
    val localeString = imm.currentInputMethodSubtype?.localeString() ?: return null
    return try {
        AdditionalSubtypeUtils.localeFromSubtypeString(localeString)
            .language
            .lowercase()
            .takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse current IME locale: $localeString", e)
        localeString
            .replace('_', '-')
            .substringBefore('-')
            .lowercase()
            .takeIf { it.isNotBlank() }
    }
}

fun SettingsManager.getCommaSpace(context: Context): Boolean {
    return getPreferences(context).getBoolean(
        KEY_COMMA_SPACE,
        DEFAULT_COMMA_SPACE
    )
}

/**
 * Flux Keyboard: "Space after comma" is Punctuation spacing's comma row now. Turned on, it
 * moves there (no space before a comma, one after) and switches itself off.
 */
fun SettingsManager.foldCommaSpaceIntoPunctuationSpacing(context: Context) {
    if (!getCommaSpace(context)) return
    setAutoSpacePunctuation(context, getAutoSpacePunctuation(context) + ",")
    setSpaceAfterPunctuation(context, getSpaceAfterPunctuation(context) + ",")
    setCommaSpace(context, false)
}

fun SettingsManager.setCommaSpace(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_COMMA_SPACE, enabled)
        .apply()
}

fun SettingsManager.getAutoSpacePunctuation(context: Context): String {
    val stored = getPreferences(context).getString(
        KEY_AUTO_SPACE_PUNCTUATION,
        DEFAULT_AUTO_SPACE_PUNCTUATION
    ) ?: DEFAULT_AUTO_SPACE_PUNCTUATION
    return normalizeAutoSpacePunctuation(stored)
}

fun SettingsManager.setAutoSpacePunctuation(context: Context, punctuation: String) {
    getPreferences(context).edit()
        .putString(KEY_AUTO_SPACE_PUNCTUATION, normalizeAutoSpacePunctuation(punctuation))
        .apply()
}

/** Punctuation typed straight into more (":-)", ":D") skips its space after, so emoticons keep their shape */
fun SettingsManager.getEmoticonPunctuation(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOTICON_PUNCTUATION, true)

fun SettingsManager.setEmoticonPunctuation(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOTICON_PUNCTUATION, enabled).apply()
}

fun SettingsManager.getSpaceAfterPunctuation(context: Context): String {
    val stored = getPreferences(context).getString(
        KEY_SPACE_AFTER_PUNCTUATION,
        DEFAULT_SPACE_AFTER_PUNCTUATION
    ) ?: DEFAULT_SPACE_AFTER_PUNCTUATION
    return normalizeAutoSpacePunctuation(stored)
}

fun SettingsManager.setSpaceAfterPunctuation(context: Context, punctuation: String) {
    getPreferences(context).edit()
        .putString(KEY_SPACE_AFTER_PUNCTUATION, normalizeAutoSpacePunctuation(punctuation))
        .apply()
}

private fun SettingsManager.normalizeAutoSpacePunctuation(punctuation: String): String =
    punctuation
        .filter { it in Punctuation.AUTO_SPACE_CANDIDATES }
        .toSet()
        .let { selected -> Punctuation.AUTO_SPACE_CANDIDATES.filter { it in selected } }

fun SettingsManager.getSmartQuotes(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SMART_QUOTES, DEFAULT_SMART_QUOTES)
}

fun SettingsManager.setSmartQuotes(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SMART_QUOTES, enabled)
        .apply()
}

fun SettingsManager.getSmartQuotesStyle(context: Context): String {
    val stored = getPreferences(context).getString(KEY_SMART_QUOTES_STYLE, DEFAULT_SMART_QUOTES_STYLE)
    return when (stored) {
        SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
        SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
        SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
        SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
        SMART_QUOTES_STYLE_ENGLISH_CURLY -> stored
        else -> DEFAULT_SMART_QUOTES_STYLE
    }
}

fun SettingsManager.setSmartQuotesStyle(context: Context, style: String) {
    getPreferences(context).edit()
        .putString(
            KEY_SMART_QUOTES_STYLE,
            when (style) {
                SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
                SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
                SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
                SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
                SMART_QUOTES_STYLE_ENGLISH_CURLY -> style
                else -> DEFAULT_SMART_QUOTES_STYLE
            }
        )
        .apply()
}

fun SettingsManager.getShiftBackspaceDelete(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SHIFT_BACKSPACE_DELETE, DEFAULT_SHIFT_BACKSPACE_DELETE)
}

/**
 * Sets whether Shift+Backspace performs forward delete.
 */
fun SettingsManager.setShiftBackspaceDelete(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SHIFT_BACKSPACE_DELETE, enabled)
        .apply()
}

/**
 * Returns whether Alt+Backspace performs forward delete.
 */
fun SettingsManager.getAltBackspaceDelete(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_ALT_BACKSPACE_DELETE, DEFAULT_ALT_BACKSPACE_DELETE)
}

/**
 * Sets whether Alt+Backspace performs forward delete.
 */
fun SettingsManager.setAltBackspaceDelete(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ALT_BACKSPACE_DELETE, enabled)
        .apply()
}

/**
 * Returns whether Backspace at line start performs forward delete.
 */
fun SettingsManager.getBackspaceAtStartDelete(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_BACKSPACE_AT_START_DELETE, DEFAULT_BACKSPACE_AT_START_DELETE)
}

/**
 * Sets whether Backspace at line start performs forward delete.
 */
fun SettingsManager.setBackspaceAtStartDelete(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_BACKSPACE_AT_START_DELETE, enabled)
        .apply()
}
