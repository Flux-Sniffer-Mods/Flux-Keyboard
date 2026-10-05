package it.palsoftware.pastiera.inputmethod

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.InputContextState

/**
 * Central helper for smart auto-capitalization rules.
 * Provides a single decision path and a single entry point to enable/clear smart Shift.
 */
object AutoCapitalizeHelper {
    private var smartShiftRequested = false

    private data class AutoCapSettings(
        val autoCapFirstLetter: Boolean,
        val autoCapAfterPeriod: Boolean
    )

    data class CursorContext(
        val before: CharSequence,
        val after: CharSequence
    )

    private fun resolveAutoCapSettings(
        context: android.content.Context,
        inputConnection: InputConnection
    ): AutoCapSettings {
        val userAutoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
        val userAutoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)

        // Respect user preferences: if explicitly disabled, don't apply auto-cap
        // even if the input field requests it. If enabled, always apply.
        // This gives users full control over the feature.
        val autoCapFirstLetter = userAutoCapFirstLetter
        val autoCapAfterPeriod = userAutoCapAfterPeriod

        return AutoCapSettings(autoCapFirstLetter, autoCapAfterPeriod)
    }

    /**
     * Reads the cursor context, ignoring any selected text (treated as removed/replaced).
     * Prefers ExtractedText; falls back to surrounding text APIs.
     */
    /**
     * Invisible characters some apps keep in an empty field (Instagram's and Keep's zero-width
     * spaces, an embedded object's placeholder): they aren't text, so the field still counts as
     * empty and gets its capital.
     */
    private val INVISIBLE = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\uFFFC]")

    internal fun visible(text: CharSequence): CharSequence =
        if (INVISIBLE.containsMatchIn(text)) INVISIBLE.replace(text, "") else text

    /**
     * The cursor sits at the very start of the field, as the app last reported it. Some apps
     * (Instagram) don't let the keyboard read their field; at its start, it still gets its capital.
     */
    @Volatile var cursorAtStart = false

    private fun readContext(inputConnection: InputConnection): CursorContext? =
        (runCatching { readRawContext(inputConnection) }.getOrNull()
            ?: if (cursorAtStart) CursorContext("", "") else null)
            ?.let { CursorContext(visible(it.before), visible(it.after)) }

    private fun readRawContext(inputConnection: InputConnection): CursorContext? {
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val text = extracted.text
            val selStart = extracted.selectionStart
            val selEnd = extracted.selectionEnd
            if (selStart >= 0 && selEnd >= selStart && selEnd <= text.length) {
                val before = text.subSequence(0, selStart)
                val after = text.subSequence(selEnd, text.length)
                return CursorContext(before, after)
            }
        }

        val before = inputConnection.getTextBeforeCursor(200, 0) ?: return null
        val after = inputConnection.getTextAfterCursor(200, 0) ?: ""
        val selected = inputConnection.getSelectedText(0) ?: ""

        val beforeEffective = when {
            selected.isEmpty() -> before
            before.length >= selected.length && before.endsWith(selected) ->
                before.dropLast(selected.length)
            else -> before
        }
        val afterEffective = when {
            selected.isEmpty() -> after
            after.length >= selected.length && after.startsWith(selected) ->
                after.drop(selected.length)
            else -> after
        }

        return CursorContext(beforeEffective, afterEffective)
    }

    /**
     * Checks if the given text ends with sentence-ending punctuation (.!?)
     * followed by whitespace. Used for auto-capitalization and double-space-to-period.
     * 
     * @param textBeforeCursor The text before the cursor position
     * @param requireWhitespaceAfter If true, requires whitespace after punctuation (for auto-cap).
     *                               If false, only checks if punctuation exists (for double-space prevention).
     * @return true if text ends with sentence-ending punctuation (with optional whitespace requirement)
     */
    fun hasSentenceEndingPunctuation(
        textBeforeCursor: CharSequence,
        requireWhitespaceAfter: Boolean = true
    ): Boolean {
        if (textBeforeCursor.isEmpty()) return false
        
        val lastNonWhitespaceIndex = textBeforeCursor.indexOfLast { !it.isWhitespace() }
        if (lastNonWhitespaceIndex < 0) return false
        
        // A text emoticon (:) ;D <3) ends a sentence too ("Capital after an emoticon")
        if (it.palsoftware.pastiera.core.EmoticonSentences.endsWithEmoticon(textBeforeCursor.subSequence(0, lastNonWhitespaceIndex + 1))) {
            return !requireWhitespaceAfter || lastNonWhitespaceIndex < textBeforeCursor.length - 1
        }

        val lastNonWhitespaceChar = textBeforeCursor[lastNonWhitespaceIndex]
        val isSentencePunctuation = when (lastNonWhitespaceChar) {
            '.' -> {
                val prevIndex = lastNonWhitespaceIndex - 1
                val isNotDoublePeriod = !(prevIndex >= 0 && textBeforeCursor[prevIndex] == '.')
                if (!isNotDoublePeriod) return false
                
                if (requireWhitespaceAfter) {
                    // All characters after period must be whitespace (end of sentence)
                    lastNonWhitespaceIndex < textBeforeCursor.length - 1 &&
                        (lastNonWhitespaceIndex + 1 until textBeforeCursor.length)
                            .all { textBeforeCursor[it].isWhitespace() }
                } else {
                    true // Just check if it's a period (not double)
                }
            }
            '!', '?' -> {
                if (requireWhitespaceAfter) {
                    // All characters after punctuation must be whitespace (end of sentence)
                    lastNonWhitespaceIndex < textBeforeCursor.length - 1 &&
                        (lastNonWhitespaceIndex + 1 until textBeforeCursor.length)
                            .all { textBeforeCursor[it].isWhitespace() }
                } else {
                    true // Just check if it's sentence-ending punctuation
                }
            }
            else -> false
        }
        
        return isSentencePunctuation
    }

    /**
     * Pure decision: should smart auto-cap request Shift given before/after?
     */
    private fun shouldAutoCap(
        settings: AutoCapSettings,
        before: CharSequence,
        after: CharSequence
    ): Boolean {
        val isCursorAtStart = before.isEmpty()
        val isAfterNewline = before.lastOrNull() == '\n'

        if (settings.autoCapFirstLetter && (isCursorAtStart || isAfterNewline)) {
            return true
        }

        if (settings.autoCapAfterPeriod && before.isNotEmpty()) {
            if (hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)) {
                return true
            }
        }

        return false
    }

    private fun clearSmartShift(
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (smartShiftRequested) {
            val changed = disableShift()
            smartShiftRequested = false
            if (changed) onUpdateStatusBar()
        }
    }

    /**
     * Single entry point to evaluate and set/clear smart Shift.
     * Also considers field-specific capitalization flags (CAP_WORDS, CAP_SENTENCES).
     * User settings take precedence over CAP_WORDS and CAP_SENTENCES.
     * CAP_CHARACTERS remains an explicit caps-lock field requirement.
     */
    /** The app in front, for the debug export's Automatic Shift log. */
    @Volatile var debugPackage: String? = null

    private fun trace(result: String, reason: String, context: CursorContext? = null) {
        runCatching {
            DebugCaptureStore.recordAutoCap(debugPackage, result, reason, context?.before, context?.after)
        }
    }

    fun maybeEnableSmartShift(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit,
        inputContextState: InputContextState? = null
    ) {
        fun on(reason: String, cursor: CursorContext? = null) {
            val took = enableShift()
            if (took) {
                smartShiftRequested = true
                onUpdateStatusBar()
            }
            trace(if (took) "shift" else "shift (already on, or turned off by hand here)", reason, cursor)
        }
        fun off(reason: String, cursor: CursorContext? = null) {
            clearSmartShift(disableShift, onUpdateStatusBar)
            trace("no shift", reason, cursor)
        }
        val ic = inputConnection ?: run {
            off("no input connection")
            return
        }
        // Scripts without capitals (Thai, Arabic, CJK…) use Shift for other letters: never auto-Shift
        if (isCaselessLanguage(currentLanguageCode(context))) {
            off("caseless language")
            return
        }

        // A kind of field without automatic Shift (Settings > Capitals) gets none, even when the
        // app asks for capitals
        if (shouldDisableAutoCapitalize && inputContextState?.requiresCapCharacters != true) {
            off(if (inputContextState?.exactTyping == true) "exact typing field" else "field type not chosen for Automatic Shift, or a password")
            return
        }

        // Check field-specific capitalization flags first.
        if (inputContextState != null) {
            // CAP_CHARACTERS is handled separately (caps lock)
            if (inputContextState.requiresCapCharacters) {
                off("field asks for all capitals")
                return
            }
            
            // CAP_WORDS: capitalize at start of word when enabled by the user.
            if (
                inputContextState.requiresCapWords &&
                SettingsManager.getAutoCapitalizeFirstLetter(context)
            ) {
                if (isAtStartOfWord(ic)) on("cap words: start of word") else off("cap words: inside a word")
                return
            }
            
            // CAP_SENTENCES: capitalize at start of sentence, but respect user settings
            if (inputContextState.requiresCapSentences) {
                // Check user settings first - if both disabled, skip and fall through to normal logic
                val userAutoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
                val userAutoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)
                
                if (!userAutoCapFirstLetter && !userAutoCapAfterPeriod) {
                    // User has disabled auto-cap, skip this block and continue to normal logic below
                    // (which will also check shouldDisableAutoCapitalize and user settings)
                } else {
                    // User has at least one setting enabled, proceed with CAP_SENTENCES logic
                    val cursorContext = readContext(ic)
                    if (cursorContext != null) {
                        val before = cursorContext.before
                        val isCursorAtStart = before.isEmpty()
                        val isAfterNewline = before.lastOrNull() == '\n'
                        val isAfterSentenceEnd = hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)
                        
                        // Check if we should capitalize based on user settings
                        val shouldCapitalize = when {
                            // Capitalize at start of field or after newline if "first letter" is enabled
                            (isCursorAtStart || isAfterNewline) && userAutoCapFirstLetter -> true
                            // Capitalize after sentence-ending punctuation if "after period" is enabled
                            isAfterSentenceEnd && userAutoCapAfterPeriod -> true
                            else -> false
                        }
                        
                        if (shouldCapitalize) on("cap sentences: start of sentence", cursorContext)
                        else off("cap sentences: mid-sentence", cursorContext)
                        return
                    } else {
                        off("cap sentences: field can't be read")
                        return
                    }
                }
            }
        }

        // Only check shouldDisableAutoCapitalize for user settings-based auto-cap
        if (shouldDisableAutoCapitalize) {
            off("field type not chosen for Automatic Shift")
            return
        }

        // Fall back to user settings-based auto-capitalization
        val settings = resolveAutoCapSettings(context, ic)
        if (!settings.autoCapFirstLetter && !settings.autoCapAfterPeriod) {
            off("capitals turned off in settings")
            return
        }

        val cursorContext = readContext(ic) ?: run {
            off("field can't be read")
            return
        }

        val shouldCapitalize = shouldAutoCap(settings, cursorContext.before, cursorContext.after)
        if (shouldCapitalize) on("start of sentence", cursorContext)
        else off("mid-sentence", cursorContext)
    }

    /**
     * Languages written in scripts without capital letters. On their layouts Shift picks other
     * letters (Thai: d is ก, D is ฏ), so auto-capitals would type the wrong ones
     * (palsoftware/pastiera#302).
     */
    private val CASELESS_LANGUAGES = setOf(
        "th", "lo", "km", "my", "zh", "ja", "ko", "ar", "fa", "ur", "ps", "he", "yi",
        "hi", "mr", "ne", "sa", "bn", "as", "pa", "gu", "or", "ta", "te", "kn", "ml", "si",
        "am", "ti", "ka", "bo", "dz", "dv"
    )

    fun isCaselessLanguage(languageCode: String?): Boolean =
        languageCode != null && languageCode.lowercase().substringBefore('_').substringBefore('-') in CASELESS_LANGUAGES

    private fun currentLanguageCode(context: android.content.Context): String? = runCatching {
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.currentInputMethodSubtype?.languageTag?.takeIf { it.isNotBlank() }
            ?: imm?.currentInputMethodSubtype?.locale
    }.getOrNull()

    fun shouldAutoCapitalizeAtCursor(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean
    ): Boolean {
        if (inputConnection == null || shouldDisableAutoCapitalize) return false
        if (isCaselessLanguage(currentLanguageCode(context))) return false
        val settings = resolveAutoCapSettings(context, inputConnection)
        if (!settings.autoCapFirstLetter && !settings.autoCapAfterPeriod) {
            return false
        }
        val cursorContext = readContext(inputConnection) ?: return false
        return shouldAutoCap(settings, cursorContext.before, cursorContext.after)
    }

    // Thin wrappers kept for call sites; all delegate to maybeEnableSmartShift.
    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun checkAutoCapitalizeOnSelectionChange(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit,
        inputContextState: InputContextState? = null
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar,
            inputContextState = inputContextState
        )
    }

    fun checkAutoCapitalizeOnRestart(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit,
        inputContextState: InputContextState? = null
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar,
            inputContextState = inputContextState
        )
    }

    fun enableAfterPunctuation(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    /**
     * Checks if the cursor is at the start of a word (after space or word-separating punctuation).
     * Used for textCapWords to determine if the next letter should be capitalized.
     */
    fun isAtStartOfWord(inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        
        val cursorContext = readContext(inputConnection) ?: return false
        val before = cursorContext.before
        
        // At start of field
        if (before.isEmpty()) return true
        
        // Check if last character is whitespace or word-separating punctuation (apostrophe excluded)
        val lastChar = before.lastOrNull() ?: return false
        return lastChar.isWhitespace() || lastChar in it.palsoftware.pastiera.core.Punctuation.BOUNDARY
    }
    
    /**
     * Checks if the cursor is at the start of a sentence (after sentence-ending punctuation).
     * Used for textCapSentences to determine if the next letter should be capitalized.
     */
    fun isAtStartOfSentence(inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        
        val cursorContext = readContext(inputConnection) ?: return false
        val before = cursorContext.before
        
        // At start of field
        if (before.isEmpty()) return true
        
        // Check if text ends with sentence-ending punctuation followed by whitespace
        return hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)
    }
    
    /**
     * Handles input field capitalization flags (CAP_CHARACTERS, CAP_WORDS, CAP_SENTENCES).
     * This is called when entering a new input field to apply field-specific
     * capitalization rules.
     * User settings take precedence over CAP_WORDS and CAP_SENTENCES.
     */
    fun handleInputFieldCapitalizationFlags(
        context: android.content.Context,
        state: InputContextState,
        inputConnection: InputConnection?,
        enableCapsLock: () -> Unit,
        enableShiftOneShot: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!state.isEditable) return
        
        // Handle textCapCharacters: enable caps lock automatically
        if (state.requiresCapCharacters) {
            enableCapsLock()
            onUpdateStatusBar()
            return // CAP_CHARACTERS takes precedence
        }
        
        // Handle textCapWords: enable shift one-shot if at start of word
        if (
            state.requiresCapWords &&
            SettingsManager.getAutoCapitalizeFirstLetter(context)
        ) {
            if (isAtStartOfWord(inputConnection)) {
                if (enableShiftOneShot()) {
                    smartShiftRequested = true
                    onUpdateStatusBar()
                }
            }
        }
        
        // Handle textCapSentences: enable shift one-shot if at start of sentence
        // But respect user settings first (only for CAP_SENTENCES)
        if (state.requiresCapSentences) {
            val userAutoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
            val userAutoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)
            
            // Only proceed if user has at least one auto-cap setting enabled
            if (userAutoCapFirstLetter || userAutoCapAfterPeriod) {
                if (inputConnection != null) {
                    val cursorContext = readContext(inputConnection)
                    if (cursorContext != null) {
                        val before = cursorContext.before
                        val isCursorAtStart = before.isEmpty()
                        val isAfterNewline = before.lastOrNull() == '\n'
                        val isAfterSentenceEnd = hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)
                        
                        // Check if we should capitalize based on user settings
                        val shouldCapitalize = when {
                            // Capitalize at start of field or after newline if "first letter" is enabled
                            (isCursorAtStart || isAfterNewline) && userAutoCapFirstLetter -> true
                            // Capitalize after sentence-ending punctuation if "after period" is enabled
                            isAfterSentenceEnd && userAutoCapAfterPeriod -> true
                            else -> false
                        }
                        
                        if (shouldCapitalize) {
                            if (enableShiftOneShot()) {
                                smartShiftRequested = true
                                onUpdateStatusBar()
                            }
                        }
                    }
                }
            }
            // If user disabled both, do nothing (don't force capitalization)
        }
    }
    
    /**
     * Checks if capitalization should be applied after a boundary key (space, enter, punctuation)
     * based on field capitalization flags. This is called when a boundary key is pressed.
     * User settings take precedence over CAP_WORDS and CAP_SENTENCES.
     */
    fun shouldCapitalizeAfterBoundary(
        context: android.content.Context,
        state: InputContextState,
        inputConnection: InputConnection?,
        keyCode: Int
    ): Boolean {
        if (!state.isEditable || inputConnection == null) return false
        
        // CAP_CHARACTERS doesn't need per-character checks (caps lock handles it)
        if (state.requiresCapCharacters) return false
        
        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        val isEnter = keyCode == KeyEvent.KEYCODE_ENTER
        
        // For CAP_WORDS: capitalize after space or enter
        if (
            state.requiresCapWords &&
            SettingsManager.getAutoCapitalizeFirstLetter(context) &&
            (isSpace || isEnter)
        ) {
            return true
        }
        
        // For CAP_SENTENCES: capitalize after space/enter if text ends with sentence-ending punctuation
        // But respect user settings first (only for CAP_SENTENCES)
        if (state.requiresCapSentences && (isSpace || isEnter)) {
            // Check if user has enabled auto-cap after period
            if (!SettingsManager.getAutoCapitalizeAfterPeriod(context)) {
                return false
            }
            
            val cursorContext = readContext(inputConnection) ?: return false
            val before = cursorContext.before
            // Use hasSentenceEndingPunctuation to properly check for ". " or "! " or "? "
            return hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)
        }
        
        return false
    }
}
