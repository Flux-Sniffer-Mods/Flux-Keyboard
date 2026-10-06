package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionActivationPolicy
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionPresentation
import it.palsoftware.pastiera.inputmethod.expansion.TextExpansionEngine
import org.json.JSONObject

// SettingsManager: suggestions, auto-correction, snippets and shortcodes. The keys and defaults live in SettingsManager.kt.

/**
 * Returns whether Shift+Backspace performs forward delete.
 */
/** Whether a word outside the dictionary typed three times is added to it (on by default). */
fun SettingsManager.getLearnFrequentWords(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_LEARN_FREQUENT_WORDS, true)

fun SettingsManager.setLearnFrequentWords(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_LEARN_FREQUENT_WORDS, enabled).apply()
}

/**
 * Whether emails and phone numbers typed by hand are kept in the user dictionary and offered
 * again in email and phone fields (on by default; never while typing incognito).
 */
fun SettingsManager.getLearnContactDetails(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_LEARN_CONTACT_DETAILS, true)

fun SettingsManager.setLearnContactDetails(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_LEARN_CONTACT_DETAILS, enabled).apply()
}

/** Whether the suggestions offer to add an unknown word to the dictionary (on by default). */
fun SettingsManager.getShowAddWordSuggestion(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SHOW_ADD_WORD_SUGGESTION, true)

fun SettingsManager.setShowAddWordSuggestion(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SHOW_ADD_WORD_SUGGESTION, enabled).apply()
}

/** Whether Ctrl + Shift + D adds the last word typed to the dictionary (on by default). */
fun SettingsManager.getAddLastWordShortcut(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_ADD_LAST_WORD_SHORTCUT, true)

fun SettingsManager.setAddLastWordShortcut(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_ADD_LAST_WORD_SHORTCUT, enabled).apply()
}

fun SettingsManager.getInlineAutofillEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_INLINE_AUTOFILL, true)

fun SettingsManager.setInlineAutofillEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_INLINE_AUTOFILL, enabled).apply()
}

/** An emoji for the word you're typing, in the suggestion bar. */
/** Suggestion bar words in bold, easier to spot when typing fast (palsoftware/pastiera#310). */
fun SettingsManager.getSuggestionsBold(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SUGGESTIONS_BOLD, false)

fun SettingsManager.setSuggestionsBold(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SUGGESTIONS_BOLD, enabled).apply()
}

/** Keys that pick a suggestion (an [it.palsoftware.pastiera.inputmethod.SuggestionKeys] option). */
/** Until chosen: Ctrl+Shift+Q/W/E, or off while trackpad swipes pick the suggestions. */
fun SettingsManager.getSuggestionKeys(context: Context): String =
    getPreferences(context).getString(KEY_SUGGESTION_KEYS, null)
        ?.takeIf { option -> option in it.palsoftware.pastiera.inputmethod.SuggestionKeys.OPTIONS }
        ?: if (getTrackpadGesturesEnabled(context) && getTrackpadSuggestionSwipeDirections(context)) {
            it.palsoftware.pastiera.inputmethod.SuggestionKeys.OFF
        } else {
            it.palsoftware.pastiera.inputmethod.SuggestionKeys.CTRL_SHIFT_QWE
        }

fun SettingsManager.setSuggestionKeys(context: Context, option: String) {
    getPreferences(context).edit().putString(KEY_SUGGESTION_KEYS, option).apply()
}

fun SettingsManager.getEmojiSuggestionsEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_SUGGESTIONS, true)

fun SettingsManager.setEmojiSuggestionsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_SUGGESTIONS, enabled).apply()
}

/**
 * Returns whether auto-correction is enabled.
 */
fun SettingsManager.getAutoCorrectEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_AUTO_CORRECT_ENABLED, DEFAULT_AUTO_CORRECT_ENABLED)
}

/**
 * Sets whether auto-correction is enabled.
 */
fun SettingsManager.setAutoCorrectEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_AUTO_CORRECT_ENABLED, enabled)
        .apply()
}

/**
 * Returns whether inline suggestions are enabled.
 */
fun SettingsManager.getSuggestionsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SUGGESTIONS_ENABLED, DEFAULT_SUGGESTIONS_ENABLED)
}

/**
 * Enables or disables inline suggestions.
 */
fun SettingsManager.setSuggestionsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_SUGGESTIONS_ENABLED, enabled)
        .apply()
}

/**
 * Returns whether accent matching should be applied to suggestions and auto-replace.
 */
fun SettingsManager.getAccentMatchingEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_ACCENT_MATCHING_ENABLED, DEFAULT_ACCENT_MATCHING_ENABLED)
}

/**
 * Toggles accent matching for suggestions.
 */
fun SettingsManager.setAccentMatchingEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_ACCENT_MATCHING_ENABLED, enabled)
        .apply()
}

/**
 * Master toggle for the experimental dictionary/suggestion engine.
 * When disabled, the IME will skip initialization and hide suggestion UI.
 */
fun SettingsManager.isExperimentalSuggestionsEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED, DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED)
}

fun SettingsManager.setExperimentalSuggestionsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED, enabled)
        .apply()
}

/** Opt-in candidates lifecycle for the hardware keyboard; existing installs keep the input view. */
fun SettingsManager.getExperimentalCandidatesViewEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_EXPERIMENTAL_CANDIDATES_VIEW_ENABLED, false)
}

fun SettingsManager.setExperimentalCandidatesViewEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_EXPERIMENTAL_CANDIDATES_VIEW_ENABLED, enabled)
        .apply()
}

/**
 * Optional debug logging for the suggestion engine.
 */
fun SettingsManager.isSuggestionDebugLoggingEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_SUGGESTION_DEBUG_LOGGING, DEFAULT_SUGGESTION_DEBUG_LOGGING)
}

/**
 * Optional debug logging for IME overlay / inset calculations.
 */
fun SettingsManager.isImeOverlayDebugLoggingEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_IME_OVERLAY_DEBUG_LOGGING, DEFAULT_IME_OVERLAY_DEBUG_LOGGING)
}

/**
 * Returns whether auto-replace on space/enter is enabled.
 */
fun SettingsManager.getAutoReplaceOnSpaceEnter(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_AUTO_REPLACE_ON_SPACE_ENTER, DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER)
}

/**
 * Enables or disables auto-replace on space/enter.
 */
fun SettingsManager.setAutoReplaceOnSpaceEnter(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_AUTO_REPLACE_ON_SPACE_ENTER, enabled)
        .apply()
}

/**
 * Returns the maximum edit distance for auto-replace (0-3).
 * 0 = off (no auto-replace), 1-3 = maximum distance allowed.
 */
fun SettingsManager.getMaxAutoReplaceDistance(context: Context): Int {
    return getPreferences(context).getInt(KEY_MAX_AUTO_REPLACE_DISTANCE, DEFAULT_MAX_AUTO_REPLACE_DISTANCE)
        .coerceIn(0, 3)
}

/**
 * Sets the maximum edit distance for auto-replace (0-3).
 * 0 = off (no auto-replace), 1-3 = maximum distance allowed.
 */
fun SettingsManager.setMaxAutoReplaceDistance(context: Context, distance: Int) {
    getPreferences(context).edit()
        .putInt(KEY_MAX_AUTO_REPLACE_DISTANCE, distance.coerceIn(0, 3))
        .apply()
}

/**
 * Returns whether keyboard proximity ranking is enabled for suggestions.
 * When enabled, suggestions consider keyboard distance to filter out unlikely typos.
 */
fun SettingsManager.getUseKeyboardProximity(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_USE_KEYBOARD_PROXIMITY, DEFAULT_USE_KEYBOARD_PROXIMITY)
}

/**
 * Enables or disables keyboard proximity ranking for suggestions.
 */
fun SettingsManager.setUseKeyboardProximity(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_USE_KEYBOARD_PROXIMITY, enabled)
        .apply()
}

/**
 * Returns whether edit type ranking is enabled for suggestions.
 * When enabled, suggestions are ranked by edit type (insert > substitute > delete).
 */
fun SettingsManager.getUseEditTypeRanking(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_USE_EDIT_TYPE_RANKING, DEFAULT_USE_EDIT_TYPE_RANKING)
}

/**
 * Enables or disables edit type ranking for suggestions.
 */
fun SettingsManager.setUseEditTypeRanking(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_USE_EDIT_TYPE_RANKING, enabled)
        .apply()
}

/**
 * Returns the list of languages enabled for auto-correction.
 * @return Set of language codes (e.g. "it", "en")
 */
fun SettingsManager.getAutoCorrectEnabledLanguages(context: Context): Set<String> {
    val prefs = getPreferences(context)
    val languagesString = prefs.getString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, null)
    
    // If languages are explicitly set, return them as-is (user controlled)
    if (languagesString != null && languagesString.isNotEmpty()) {
        return languagesString.split(",").toSet()
    }
    
    // Default: system language + x-pastiera, with fallback to English
    val systemLanguage = context.applicationContext.resources.configuration.locales[0].language.lowercase()
    val supportedLanguages = setOf("it", "en", "es", "fr", "de", "pl")
    
    val defaultLanguage = if (systemLanguage in supportedLanguages) {
        systemLanguage
    } else {
        "en" // Fallback to English
    }
    
    return setOf(defaultLanguage, "x-pastiera")
}

/**
 * Sets the list of languages enabled for auto-correction.
 * @param languages Set of language codes (e.g. "it", "en")
 */
fun SettingsManager.setAutoCorrectEnabledLanguages(context: Context, languages: Set<String>) {
    val languagesString = languages.joinToString(",")
    getPreferences(context).edit()
        .putString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, languagesString)
        .apply()
}

/**
 * Returns custom corrections for a language.
 */
fun SettingsManager.getCustomAutoCorrections(context: Context, languageCode: String): Map<String, String> {
    val prefs = getPreferences(context)
    val key = "auto_correct_custom_$languageCode"
    val jsonString = prefs.getString(key, null) ?: return emptyMap()
    
    return try {
        val jsonObject = JSONObject(jsonString)
        val corrections = mutableMapOf<String, String>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val correctionKey = keys.next()
            // Skip the special name field
            if (correctionKey != LANGUAGE_NAME_KEY) {
                val value = jsonObject.getString(correctionKey)
                corrections[correctionKey] = value
            }
        }
        corrections
    } catch (e: Exception) {
        Log.e(TAG, "Error loading custom corrections for $languageCode", e)
        emptyMap()
    }
}

fun SettingsManager.getSnippetsEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SNIPPETS_ENABLED, DEFAULT_SNIPPETS_ENABLED)

fun SettingsManager.setSnippetsEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SNIPPETS_ENABLED, enabled).apply()
}

fun SettingsManager.getSnippetsPrefix(context: Context): String {
    val stored = getPreferences(context).getString(KEY_SNIPPETS_PREFIX, DEFAULT_SNIPPETS_PREFIX)
    return stored?.takeIf(TextExpansionEngine::isValidSnippetPrefix) ?: DEFAULT_SNIPPETS_PREFIX
}

fun SettingsManager.setSnippetsPrefix(context: Context, prefix: String): Boolean {
    if (!TextExpansionEngine.isValidSnippetPrefix(prefix)) return false
    getPreferences(context).edit().putString(KEY_SNIPPETS_PREFIX, prefix).apply()
    return true
}

fun SettingsManager.getSnippets(context: Context): LinkedHashMap<String, String> {
    val json = getPreferences(context).getString(KEY_SNIPPETS, null) ?: return linkedMapOf()
    return runCatching {
        val objectValue = JSONObject(json)
        linkedMapOf<String, String>().apply {
            objectValue.keys().forEach { key ->
                if (TextExpansionEngine.isValidSnippetShortcut(key)) {
                    put(key.lowercase(java.util.Locale.ROOT), objectValue.getString(key))
                }
            }
        }
    }.getOrElse {
        Log.e(TAG, "Error loading snippets", it)
        linkedMapOf()
    }
}

fun SettingsManager.saveSnippets(context: Context, snippets: Map<String, String>) {
    val json = JSONObject()
    snippets.forEach { (shortcut, replacement) ->
        val normalized = shortcut.trim().lowercase(java.util.Locale.ROOT)
        if (TextExpansionEngine.isValidSnippetShortcut(normalized) && !replacement.isBlank()) {
            json.put(normalized, replacement)
        }
    }
    getPreferences(context).edit().putString(KEY_SNIPPETS, json.toString()).apply()
}

fun SettingsManager.getSnippetsPresentation(context: Context): ExpansionPresentation = ExpansionPresentation.fromStorage(
    getPreferences(context).getString(KEY_SNIPPETS_PRESENTATION, null)
)

fun SettingsManager.setSnippetsPresentation(context: Context, presentation: ExpansionPresentation) {
    getPreferences(context).edit().putString(KEY_SNIPPETS_PRESENTATION, presentation.storageValue).apply()
}

fun SettingsManager.getSnippetsActivationPolicy(context: Context): ExpansionActivationPolicy {
    val prefs = getPreferences(context)
    return ExpansionActivationPolicy(
        exactOnSpace = prefs.getBoolean(KEY_SNIPPETS_EXACT_ON_SPACE, true),
        acceptPrefixWithSpace = prefs.getBoolean(KEY_SNIPPETS_ACCEPT_PREFIX_WITH_SPACE, false),
        acceptWithTab = prefs.getBoolean(KEY_SNIPPETS_ACCEPT_WITH_TAB, true),
        acceptWithEnter = prefs.getBoolean(KEY_SNIPPETS_ACCEPT_WITH_ENTER, false)
    )
}

fun SettingsManager.setSnippetsActivationPolicy(context: Context, policy: ExpansionActivationPolicy) {
    getPreferences(context).edit()
        .putBoolean(KEY_SNIPPETS_EXACT_ON_SPACE, policy.exactOnSpace)
        .putBoolean(KEY_SNIPPETS_ACCEPT_PREFIX_WITH_SPACE, policy.acceptPrefixWithSpace)
        .putBoolean(KEY_SNIPPETS_ACCEPT_WITH_TAB, policy.acceptWithTab)
        .putBoolean(KEY_SNIPPETS_ACCEPT_WITH_ENTER, policy.acceptWithEnter)
        .apply()
}

fun SettingsManager.getEmojiShortcodesEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_SHORTCODES_ENABLED, false)

fun SettingsManager.setEmojiShortcodesEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_SHORTCODES_ENABLED, enabled).apply()
}

fun SettingsManager.getSymbolShortcodesEnabled(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_SYMBOL_SHORTCODES_ENABLED, false)

fun SettingsManager.setSymbolShortcodesEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_SYMBOL_SHORTCODES_ENABLED, enabled).apply()
}

fun SettingsManager.getEmojiSymbolsPresentation(context: Context): ExpansionPresentation = ExpansionPresentation.fromStorage(
    getPreferences(context).getString(KEY_EMOJI_SYMBOLS_PRESENTATION, null)
)

fun SettingsManager.setEmojiSymbolsPresentation(context: Context, presentation: ExpansionPresentation) {
    getPreferences(context).edit()
        .putString(KEY_EMOJI_SYMBOLS_PRESENTATION, presentation.storageValue)
        .apply()
}

fun SettingsManager.getEmojiSymbolsActivationPolicy(context: Context): ExpansionActivationPolicy {
    val prefs = getPreferences(context)
    return ExpansionActivationPolicy(
        exactOnSpace = prefs.getBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_SPACE, false),
        acceptPrefixWithSpace = prefs.getBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_PREFIX_WITH_SPACE, false),
        acceptWithTab = prefs.getBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_TAB, true),
        acceptWithEnter = prefs.getBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_ENTER, false)
    )
}

fun SettingsManager.setEmojiSymbolsActivationPolicy(context: Context, policy: ExpansionActivationPolicy) {
    getPreferences(context).edit()
        .putBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_SPACE, policy.exactOnSpace)
        .putBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_PREFIX_WITH_SPACE, policy.acceptPrefixWithSpace)
        .putBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_TAB, policy.acceptWithTab)
        .putBoolean(KEY_EMOJI_SYMBOLS_ACCEPT_WITH_ENTER, policy.acceptWithEnter)
        .apply()
}

fun SettingsManager.getEmojiSymbolsExactOnClose(context: Context): Boolean =
    getPreferences(context).getBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_CLOSE, true)

fun SettingsManager.setEmojiSymbolsExactOnClose(context: Context, enabled: Boolean) {
    getPreferences(context).edit().putBoolean(KEY_EMOJI_SYMBOLS_EXACT_ON_CLOSE, enabled).apply()
}

/**
 * Returns the display name of a custom language from JSON.
 */
fun SettingsManager.getCustomLanguageName(context: Context, languageCode: String): String? {
    val prefs = getPreferences(context)
    val key = "auto_correct_custom_$languageCode"
    val jsonString = prefs.getString(key, null) ?: return null
    
    return try {
        val jsonObject = JSONObject(jsonString)
        if (jsonObject.has(LANGUAGE_NAME_KEY)) {
            jsonObject.getString(LANGUAGE_NAME_KEY)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading language name for $languageCode", e)
        null
    }
}

/**
 * Saves custom corrections for a language.
 * @param languageName The display name of the language (optional, if null it is not saved/updated)
 */
fun SettingsManager.saveCustomAutoCorrections(
    context: Context, 
    languageCode: String, 
    corrections: Map<String, String>,
    languageName: String? = null
) {
    try {
        val jsonObject = JSONObject()
        
        // Save the language name if provided
        if (languageName != null) {
            jsonObject.put(LANGUAGE_NAME_KEY, languageName)
        } else {
            // If not provided, try to keep the existing name
            val existingName = getCustomLanguageName(context, languageCode)
            if (existingName != null) {
                jsonObject.put(LANGUAGE_NAME_KEY, existingName)
            }
        }
        
        // Save corrections
        corrections.forEach { (key, value) ->
            // Skip the special field if present in the corrections
            if (key != LANGUAGE_NAME_KEY) {
                jsonObject.put(key, value)
            }
        }
        
        val key = "auto_correct_custom_$languageCode"
        getPreferences(context).edit()
            .putString(key, jsonObject.toString())
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error saving custom corrections for $languageCode", e)
    }
}
