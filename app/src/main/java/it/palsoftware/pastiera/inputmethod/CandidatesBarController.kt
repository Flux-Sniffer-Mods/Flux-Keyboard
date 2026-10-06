package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.view.inputmethod.InputConnection

/**
 * Coordinates the two StatusBarController instances (full input view vs
 * candidates-only view) so the IME service can treat them as a single surface.
 */
class CandidatesBarController(
    private val context: Context,
    clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    assets: AssetManager? = null,
    imeServiceClass: Class<*>? = null
) {

    private val inputStatusBar = StatusBarController(context, StatusBarController.Mode.INPUT_VIEW, clipboardHistoryManager, assets, imeServiceClass)
    private val candidatesStatusBar = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY, clipboardHistoryManager, assets, imeServiceClass)
    private var candidatesSurfaceActive = true

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            inputStatusBar.onVariationSelectedListener = value
            candidatesStatusBar.onVariationSelectedListener = value
        }

    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onCursorMovedListener = value
            candidatesStatusBar.onCursorMovedListener = value
        }

    var onSpeechRecognitionRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSpeechRecognitionRequested = value
            candidatesStatusBar.onSpeechRecognitionRequested = value
        }

    var onAddUserWord: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onAddUserWord = value
            candidatesStatusBar.onAddUserWord = value
        }

    var onAddUserWordSubstitutionRequested: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onAddUserWordSubstitutionRequested = value
            candidatesStatusBar.onAddUserWordSubstitutionRequested = value
        }

    var onSuggestionCommitted: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSuggestionCommitted = value
            candidatesStatusBar.onSuggestionCommitted = value
        }

    var onHideSuggestion: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onHideSuggestion = value
            candidatesStatusBar.onHideSuggestion = value
        }

    var onDeleteUserSuggestion: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onDeleteUserSuggestion = value
            candidatesStatusBar.onDeleteUserSuggestion = value
        }

    var canDeleteUserSuggestion: ((String) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.canDeleteUserSuggestion = value
            candidatesStatusBar.canDeleteUserSuggestion = value
        }

    var onLanguageSwitchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onLanguageSwitchRequested = value
            candidatesStatusBar.onLanguageSwitchRequested = value
        }
    
    var onClipboardRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onClipboardRequested = value
            candidatesStatusBar.onClipboardRequested = value
        }
    
    var onEmojiPickerRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPickerRequested = value
            candidatesStatusBar.onEmojiPickerRequested = value
        }

    var onEmojiPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPageRequested = value
            candidatesStatusBar.onEmojiPageRequested = value
        }

    var onEmojiPickerSearchPanelToggled: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPickerSearchPanelToggled = value
            candidatesStatusBar.onEmojiPickerSearchPanelToggled = value
        }
    
    var onSymbolsPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymbolsPageRequested = value
            candidatesStatusBar.onSymbolsPageRequested = value
        }

    var onSoftwareKeyboardSymToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardSymToggleRequested = value
            candidatesStatusBar.onSoftwareKeyboardSymToggleRequested = value
        }

    var onSymCloseRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymCloseRequested = value
            candidatesStatusBar.onSymCloseRequested = value
        }

    var onEmojiLayerSearchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiLayerSearchRequested = value
            candidatesStatusBar.onEmojiLayerSearchRequested = value
        }

    var onEmojiLayerRecentsToggled: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiLayerRecentsToggled = value
            candidatesStatusBar.onEmojiLayerRecentsToggled = value
        }

    var onKaomojiKeyTapped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onKaomojiKeyTapped = value
            candidatesStatusBar.onKaomojiKeyTapped = value
        }

    var onKaomojiBackTapped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onKaomojiBackTapped = value
            candidatesStatusBar.onKaomojiBackTapped = value
        }

    var onLayerNextTapped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onLayerNextTapped = value
            candidatesStatusBar.onLayerNextTapped = value
        }

    var onEmojiVariantsRequested: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiVariantsRequested = value
            candidatesStatusBar.onEmojiVariantsRequested = value
        }

    var onEmojiLayerTyped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiLayerTyped = value
            candidatesStatusBar.onEmojiLayerTyped = value
        }

    var onKaomojiSearchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onKaomojiSearchRequested = value
            candidatesStatusBar.onKaomojiSearchRequested = value
        }

    var onEmojiLayerGifRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiLayerGifRequested = value
            candidatesStatusBar.onEmojiLayerGifRequested = value
        }

    var onGifChosen: ((it.palsoftware.pastiera.data.gif.GifResult) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onGifChosen = value
            candidatesStatusBar.onGifChosen = value
        }

    var onSymbolSearchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymbolSearchRequested = value
            candidatesStatusBar.onSymbolSearchRequested = value
        }

    /** The next time the emoji picker shows, open its symbol search. */
    fun requestSymbolSearch(initialText: String? = null) {
        inputStatusBar.requestSymbolSearch(initialText)
        candidatesStatusBar.requestSymbolSearch(initialText)
    }

    /** The next time the emoji picker shows, open its kaomoji search. */
    fun requestKaomojiSearch() {
        inputStatusBar.requestKaomojiSearch()
        candidatesStatusBar.requestKaomojiSearch()
    }

    /** The next time the emoji picker shows, open its GIF search. */
    fun requestEmojiPickerGifs() {
        inputStatusBar.requestEmojiPickerGifs()
        candidatesStatusBar.requestEmojiPickerGifs()
    }

    /** The emoji picker is showing: its search (or GIF/symbol search) takes typing. */
    fun focusEmojiPickerSearch() {
        inputStatusBar.focusEmojiPickerSearch()
        candidatesStatusBar.focusEmojiPickerSearch()
    }

    /** The next time the emoji picker shows, open its search. */
    fun requestEmojiPickerSearch(initialText: String? = null) {
        inputStatusBar.requestEmojiPickerSearch(initialText)
        candidatesStatusBar.requestEmojiPickerSearch(initialText)
    }

    var onExtraKeysRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onExtraKeysRequested = value
            candidatesStatusBar.onExtraKeysRequested = value
        }

    var onUndoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onUndoRequested = value
            candidatesStatusBar.onUndoRequested = value
        }

    var onRedoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onRedoRequested = value
            candidatesStatusBar.onRedoRequested = value
        }

    var onSoftwareKeyboardKeyPressed: ((Int) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardKeyPressed = value
            candidatesStatusBar.onSoftwareKeyboardKeyPressed = value
        }

    var onSoftwareKeyboardModifierKeyDown: ((Int) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardModifierKeyDown = value
            candidatesStatusBar.onSoftwareKeyboardModifierKeyDown = value
        }

    var onSoftwareKeyboardModifierKeyUp: ((Int) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardModifierKeyUp = value
            candidatesStatusBar.onSoftwareKeyboardModifierKeyUp = value
        }

    var onSoftwareKeyboardKeyStroke: ((Int, String) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardKeyStroke = value
            candidatesStatusBar.onSoftwareKeyboardKeyStroke = value
        }

    var onSoftwareKeyboardShiftTapped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardShiftTapped = value
            candidatesStatusBar.onSoftwareKeyboardShiftTapped = value
        }

    var onSoftwareKeyboardNonShiftInteraction: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardNonShiftInteraction = value
            candidatesStatusBar.onSoftwareKeyboardNonShiftInteraction = value
        }

    var onSoftwareKeyboardTextInput: ((String, InputConnection?, StatusBarController.StatusSnapshot) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardTextInput = value
            candidatesStatusBar.onSoftwareKeyboardTextInput = value
        }

    var onSoftwareKeyboardBoundaryTextInput: ((String, InputConnection?) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardBoundaryTextInput = value
            candidatesStatusBar.onSoftwareKeyboardBoundaryTextInput = value
        }

    var onMinimalUiToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onMinimalUiToggleRequested = value
            candidatesStatusBar.onMinimalUiToggleRequested = value
        }

    var onSoftwareKeyboardModeToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardModeToggleRequested = value
            candidatesStatusBar.onSoftwareKeyboardModeToggleRequested = value
        }

    fun getInputView(emojiMapText: String = ""): LinearLayout {
        return inputStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun getCandidatesView(emojiMapText: String = ""): LinearLayout {
        return candidatesStatusBar.getOrCreateLayout(emojiMapText).also {
            if (!candidatesSurfaceActive) candidatesStatusBar.collapseLayout()
        }
    }

    /**
     * The framework hides its candidates frame with INVISIBLE, which still reserves the child's
     * measured height. Collapse the child while the full input view is active and explicitly
     * restore it before returning to hardware/candidates-only mode.
     */
    fun setCandidatesSurfaceActive(active: Boolean) {
        candidatesSurfaceActive = active
        if (active) candidatesStatusBar.expandLayout() else candidatesStatusBar.collapseLayout()
    }

    fun isInputViewActuallyRendered(): Boolean =
        inputStatusBar.getLayout().isActuallyRendered()

    fun isCandidatesViewActuallyRendered(): Boolean =
        candidatesStatusBar.getLayout().isActuallyRendered()

    private fun View?.isActuallyRendered(): Boolean {
        if (this == null || !isAttachedToWindow || windowVisibility != View.VISIBLE || !isShown || width <= 0 || height <= 0) {
            return false
        }
        val visibleBounds = Rect()
        return getGlobalVisibleRect(visibleBounds) &&
            visibleBounds.width() > 0 &&
            visibleBounds.height() > 0
    }

    /** Visible child bounds in IME-window coordinates; absent content owns no input region. */
    fun visibleBoundsInWindow(): Rect? {
        val view = listOf(inputStatusBar.getLayout(), candidatesStatusBar.getLayout())
            .firstOrNull { it.isActuallyRendered() } ?: return null
        val bounds = Rect()
        if (!view.getLocalVisibleRect(bounds)) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        bounds.offset(location[0], location[1])
        return bounds
    }

    /** Hidden app with "Show status LEDs only": both surfaces draw only the LED strip. */
    fun setLedsOnlyMode(active: Boolean) {
        inputStatusBar.ledsOnlyMode = active
        candidatesStatusBar.ledsOnlyMode = active
    }

    fun setPastierinaModeActive(active: Boolean) {
        inputStatusBar.setPastierinaModeActive(active)
        candidatesStatusBar.setPastierinaModeActive(active)
    }

    fun setExtraKeys(row: it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysRow?) {
        inputStatusBar.setExtraKeys(row)
        candidatesStatusBar.setExtraKeys(row)
    }

    fun handleBackPressed(): Boolean {
        return inputStatusBar.handleBackPressed() || candidatesStatusBar.handleBackPressed()
    }

    fun handleEmojiPickerSearchKeyDown(
        event: KeyEvent?,
        ctrlActive: Boolean,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        return inputStatusBar.handleEmojiPickerSearchKeyDown(event, ctrlActive, resolveTypedText) ||
            candidatesStatusBar.handleEmojiPickerSearchKeyDown(event, ctrlActive, resolveTypedText)
    }

    fun shouldConsumeEmojiPickerSearchKeyUp(event: KeyEvent?, ctrlActive: Boolean): Boolean {
        return inputStatusBar.shouldConsumeEmojiPickerSearchKeyUp(event, ctrlActive) ||
            candidatesStatusBar.shouldConsumeEmojiPickerSearchKeyUp(event, ctrlActive)
    }

    fun disableEmojiPickerSearchInputCapture() {
        inputStatusBar.disableEmojiPickerSearchInputCapture()
        candidatesStatusBar.disableEmojiPickerSearchInputCapture()
    }

    fun dismissEmojiPickerPopups() {
        inputStatusBar.dismissEmojiPickerPopup()
        candidatesStatusBar.dismissEmojiPickerPopup()
    }

    fun isEmojiPickerSearchInputActive(): Boolean {
        return inputStatusBar.isEmojiPickerSearchInputActive() ||
            candidatesStatusBar.isEmojiPickerSearchInputActive()
    }

    fun createEmojiPickerSearchInputConnection(): InputConnection? {
        return inputStatusBar.createEmojiPickerSearchInputConnection()
            ?: candidatesStatusBar.createEmojiPickerSearchInputConnection()
    }

    fun isPastierinaModeActive(): Boolean = inputStatusBar.isPastierinaModeActive()

    fun refreshWindowInsets() {
        inputStatusBar.refreshWindowInsets()
        candidatesStatusBar.refreshWindowInsets()
    }
    
    fun invalidateStaticVariations() {
        inputStatusBar.invalidateStaticVariations()
        candidatesStatusBar.invalidateStaticVariations()
    }

    fun setMicrophoneButtonActive(isActive: Boolean) {
        inputStatusBar.setMicrophoneButtonActive(isActive)
        candidatesStatusBar.setMicrophoneButtonActive(isActive)
    }
    
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        inputStatusBar.updateMicrophoneAudioLevel(rmsdB)
        candidatesStatusBar.updateMicrophoneAudioLevel(rmsdB)
    }
    
    fun showSpeechRecognitionHint(show: Boolean) {
        inputStatusBar.showSpeechRecognitionHint(show)
        candidatesStatusBar.showSpeechRecognitionHint(show)
    }

    fun updateStatusBars(
        snapshot: StatusBarController.StatusSnapshot,
        emojiMapText: String,
        inputConnection: InputConnection?,
        symMappings: Map<Int, String>?
    ) {
        inputStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        if (candidatesSurfaceActive) {
            candidatesStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        } else {
            candidatesStatusBar.collapseLayout()
        }
    }

    fun updateClipboardCount(count: Int) {
        inputStatusBar.updateClipboardCount(count)
        candidatesStatusBar.updateClipboardCount(count)
    }

    fun flashSuggestionSlot(suggestionIndex: Int) {
        inputStatusBar.flashSuggestionSlot(suggestionIndex)
        candidatesStatusBar.flashSuggestionSlot(suggestionIndex)
    }

    fun resetSuggestionActionMode() {
        inputStatusBar.resetSuggestionActionMode()
        candidatesStatusBar.resetSuggestionActionMode()
    }

    fun showExpansionSuggestions(suggestions: List<String>, onSelected: (String) -> Unit) {
        inputStatusBar.showExpansionSuggestions(suggestions, onSelected)
        candidatesStatusBar.showExpansionSuggestions(suggestions, onSelected)
    }

    /** Autofill chips for the bar on screen: a chip can only be in one place. */
    fun showInlineAutofill(views: List<android.view.View>) {
        if (candidatesSurfaceActive) {
            inputStatusBar.clearInlineAutofill()
            candidatesStatusBar.showInlineAutofill(views)
        } else {
            candidatesStatusBar.clearInlineAutofill()
            inputStatusBar.showInlineAutofill(views)
        }
    }

    fun suggestionChipColours(): Pair<Int, Int> = inputStatusBar.suggestionChipColours()

    fun suggestionChipHeight(): Int? = inputStatusBar.suggestionChipHeight()

    fun clearInlineAutofill() {
        inputStatusBar.clearInlineAutofill()
        candidatesStatusBar.clearInlineAutofill()
    }

    fun clearExpansionSuggestions() {
        inputStatusBar.clearExpansionSuggestions()
        candidatesStatusBar.clearExpansionSuggestions()
    }

    fun cancelSoftwareKeyboardTouchState() {
        inputStatusBar.cancelSoftwareKeyboardTouchState()
        candidatesStatusBar.cancelSoftwareKeyboardTouchState()
    }

}
