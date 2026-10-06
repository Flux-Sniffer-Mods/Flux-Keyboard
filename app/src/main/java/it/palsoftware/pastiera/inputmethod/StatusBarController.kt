package it.palsoftware.pastiera.inputmethod

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.util.Log
import android.util.TypedValue
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.gif.KlipyGifs
import it.palsoftware.pastiera.data.symbols.SymbolSearch
import it.palsoftware.pastiera.data.symbols.Kaomoji
import it.palsoftware.pastiera.data.emoji.EmojiLayerRecents
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.SymCustomizationActivity
import it.palsoftware.pastiera.theme.KeyboardBackgroundImage
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.sym.SymPagesConfig
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver
import it.palsoftware.pastiera.data.variation.VariationRepository
import kotlin.math.max
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import it.palsoftware.pastiera.inputmethod.ui.ClipboardHistoryView
import it.palsoftware.pastiera.inputmethod.ui.EmojiPickerView
import it.palsoftware.pastiera.inputmethod.ui.HamburgerMenuView
import it.palsoftware.pastiera.inputmethod.ui.LedStatusView
import it.palsoftware.pastiera.inputmethod.ui.ModifierLedLayouts
import it.palsoftware.pastiera.inputmethod.ui.VariationBarView
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors
import it.palsoftware.pastiera.inputmethod.suggestions.ui.FullSuggestionsBar
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonPosition
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.languageCode
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardLayoutTemplates
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import android.content.res.AssetManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.device.T2eCornerCalibration
import it.palsoftware.pastiera.device.T2eCornerGeometry
import it.palsoftware.pastiera.activeEmojiLayerGifKey
import it.palsoftware.pastiera.emojiScreenClosesAfterInput
import it.palsoftware.pastiera.getAccessibilityLiveAnnouncementsEnabled
import it.palsoftware.pastiera.getAccessibilityReadSecondRowEnabled
import it.palsoftware.pastiera.getAccessibilitySuggestionsAnnouncementDelayMs
import it.palsoftware.pastiera.getEffectiveKeyboardTheme
import it.palsoftware.pastiera.getEmojiKeyLedEnabled
import it.palsoftware.pastiera.getEmojiLayerPages
import it.palsoftware.pastiera.getEmojiLayerRecentsKey
import it.palsoftware.pastiera.getEmojiPickerFocusSearch
import it.palsoftware.pastiera.getEmojiPickerKey
import it.palsoftware.pastiera.getKaomojiCloseOnTap
import it.palsoftware.pastiera.getKeyboardBackgroundFraming
import it.palsoftware.pastiera.getKeyboardLayout
import it.palsoftware.pastiera.getKlipyApiKey
import it.palsoftware.pastiera.getLongPressModifier
import it.palsoftware.pastiera.getLongPressThreshold
import it.palsoftware.pastiera.getModifierIndicatorShowsBottomStrip
import it.palsoftware.pastiera.getModifierIndicatorShowsStatusBar
import it.palsoftware.pastiera.getPhysicalKeyboardProfileOverride
import it.palsoftware.pastiera.getSearchKey
import it.palsoftware.pastiera.getSoftwareKeyboardLayoutStyle
import it.palsoftware.pastiera.getSoftwareKeyboardLongPressLayerPopupBelowKey
import it.palsoftware.pastiera.getSoftwareKeyboardLongPressLayerPopupEnabled
import it.palsoftware.pastiera.getSoftwareKeyboardNearestKeyTouchEnabled
import it.palsoftware.pastiera.getSoftwareKeyboardNumberRowEnabled
import it.palsoftware.pastiera.getStaticVariationBasePreset
import it.palsoftware.pastiera.getSuggestionsEnabled
import it.palsoftware.pastiera.getSymAutoCloseOnTouch
import it.palsoftware.pastiera.getSymMappings
import it.palsoftware.pastiera.getSymMappingsPage2
import it.palsoftware.pastiera.getSymPagesConfig
import it.palsoftware.pastiera.getSymbolsCloseOnTap
import it.palsoftware.pastiera.getSymbolsPages
import it.palsoftware.pastiera.getTitan2EliteContourLeds
import it.palsoftware.pastiera.getTitan2EliteFillCorners
import it.palsoftware.pastiera.getTitan2EliteRoundedCornerInsetsEnabled
import it.palsoftware.pastiera.getTitan2EliteStatusBarLiftPx
import it.palsoftware.pastiera.getTitan2EliteStraightOuterButtons
import it.palsoftware.pastiera.gifsAvailable
import it.palsoftware.pastiera.isExperimentalSuggestionsEnabled
import it.palsoftware.pastiera.isImeOverlayDebugLoggingEnabled
import it.palsoftware.pastiera.isTitan2LayoutEnabled
import it.palsoftware.pastiera.resolveEffectiveSoftwareKeyboardMode
import it.palsoftware.pastiera.resolveLongPressSymPage
import it.palsoftware.pastiera.setPendingRestoreSymPage

/**
 * Manages the status bar shown by the IME, handling view creation
 * and updating text/style based on modifier states.
 */
class StatusBarController(
    private val context: Context,
    private val mode: Mode = Mode.INPUT_VIEW,
    private val clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null
) {
    enum class Mode {
        INPUT_VIEW,
        CANDIDATES_ONLY
    }

    // Listener for variation selection
    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            variationBarView?.onVariationSelectedListener = value
        }
    
    // Listener for cursor movement (to update variations)
    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onCursorMovedListener = value
        }
    
    // Listener for speech recognition request
    var onSpeechRecognitionRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSpeechRecognitionRequested = value
        }

    var onAddUserWord: ((String) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onAddUserWord = value
        }

    var onAddUserWordSubstitutionRequested: ((String) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onAddUserWordSubstitutionRequested = value
        }

    var onSuggestionCommitted: (() -> Unit)? = null

    var onHideSuggestion: ((String) -> Unit)? = null

    var onDeleteUserSuggestion: ((String) -> Unit)? = null

    var canDeleteUserSuggestion: ((String) -> Boolean)? = null
    
    var onLanguageSwitchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onLanguageSwitchRequested = value
        }
    
    var onClipboardRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onClipboardRequested = value
        }
    
    var onEmojiPickerRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onEmojiPickerRequested = value
        }

    var onEmojiPageRequested: (() -> Unit)? = null
    
    var onSymbolsPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSymbolsPageRequested = value
        }

    var onSoftwareKeyboardSymToggleRequested: (() -> Unit)? = null

    var onSymCloseRequested: (() -> Unit)? = null
    // Emoji layer: its search button, and its Recents key when tapped
    var onEmojiLayerSearchRequested: (() -> Unit)? = null
    var onEmojiLayerRecentsToggled: (() -> Unit)? = null
    /** The symbols page's kaomoji key was tapped. */
    var onKaomojiKeyTapped: (() -> Unit)? = null
    /** The kaomoji pages' Q (the page before) was tapped. */
    var onKaomojiBackTapped: (() -> Unit)? = null
    /** A paged layer's P (the next page) was tapped. */
    var onLayerNextTapped: (() -> Unit)? = null
    /** The first kaomoji page's search key was tapped. */
    var onKaomojiSearchRequested: (() -> Unit)? = null
    /** An emoji was held on the emoji layer's pages: its skin tones. */
    var onEmojiVariantsRequested: ((String) -> Unit)? = null
    /** An emoji was tapped on the emoji layer (its variants page goes back). */
    var onEmojiLayerTyped: (() -> Unit)? = null
    // GIF search: the emoji layer's GIF key, and a GIF picked in the picker's GIF mode
    var onEmojiLayerGifRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onGifSearchRequested = value
        }
    // The status bar's and menu's GIF button: the same GIF search
    private val onGifSearchRequested: (() -> Unit) get() = { onEmojiLayerGifRequested?.invoke() }
    // Symbol search from the SYM symbols pages
    var onSymbolSearchRequested: (() -> Unit)? = null
    private var pendingSymbolSearch: Boolean = false
    private var pendingKaomojiSearch: Boolean = false
    var onGifChosen: ((it.palsoftware.pastiera.data.gif.GifResult) -> Unit)? = null
    private var pendingEmojiPickerGifs: Boolean = false
    private var lastSnapshotSymPage: Int = 0
    private var lastEmojiScreenFromEmojiKey: Boolean = false
    private var pendingEmojiPickerSearch: Boolean = false

    /** The next time the emoji picker shows, open its search. */
    /** The picker is showing: its search takes typing (the search key). */
    fun focusEmojiPickerSearch() {
        emojiPickerView?.focusSearch()
    }

    // Type to search: the letter that started it, typed into the search once it opens
    private var pendingSearchText: String? = null

    fun requestEmojiPickerSearch(initialText: String? = null) {
        pendingEmojiPickerSearch = true
        pendingSearchText = initialText
    }

    /** The next time the emoji picker shows, open its GIF search. */
    fun requestEmojiPickerGifs() {
        pendingEmojiPickerGifs = true
    }

    /** The next time the emoji picker shows, open its symbol search. */
    fun requestSymbolSearch(initialText: String? = null) {
        pendingSymbolSearch = true
        pendingKaomojiSearch = false
        pendingSearchText = initialText
    }

    /** The next time the emoji picker shows, open its kaomoji search. */
    fun requestKaomojiSearch() {
        pendingSymbolSearch = true
        pendingKaomojiSearch = true
        pendingSearchText = null
    }

    /**
     * Fired when the inline emoji picker toggles its search panel visibility. The host must
     * force a re-render because the panel state is internal to the picker and not part of the
     * status snapshot; with an active search the picker moves to a popup above the keyboard.
     */
    var onEmojiPickerSearchPanelToggled: ((Boolean) -> Unit)? = null

    var onUndoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onUndoRequested = value
        }

    var onRedoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onRedoRequested = value
        }

    var onExtraKeysRequested: (() -> Unit)? = null

    var onSoftwareKeyboardKeyPressed: ((Int) -> Unit)? = null

    var onSoftwareKeyboardModifierKeyDown: ((Int) -> Boolean)? = null

    var onSoftwareKeyboardModifierKeyUp: ((Int) -> Boolean)? = null

    var onSoftwareKeyboardKeyStroke: ((Int, String) -> Boolean)? = null

    var onSoftwareKeyboardShiftTapped: (() -> Unit)? = null

    var onSoftwareKeyboardNonShiftInteraction: (() -> Unit)? = null

    var onSoftwareKeyboardTextInput: ((String, android.view.inputmethod.InputConnection?, StatusSnapshot) -> Boolean)? = null

    var onSoftwareKeyboardBoundaryTextInput: ((String, android.view.inputmethod.InputConnection?) -> Boolean)? = null

    var onHamburgerMenuRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onHamburgerMenuRequested = value
        }

    var onMinimalUiToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onMinimalUiToggleRequested = value
        }

    var onSoftwareKeyboardModeToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSoftwareKeyboardModeToggleRequested = value
        }
    
    // Callback for speech recognition state changes (active/inactive)
    var onSpeechRecognitionStateChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            // Note: VariationBarView doesn't need this directly, but we can add it if needed
        }
    
    fun invalidateStaticVariations() {
        variationBarView?.invalidateStaticVariations()
    }

    /**
     * Sets the microphone button active state.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        variationBarView?.setMicrophoneButtonActive(isActive)
        hamburgerMenuView?.setMicrophoneActive(isActive)
        fullSuggestionsBar?.setMicrophoneButtonActive(isActive)
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        variationBarView?.updateMicrophoneAudioLevel(rmsdB)
        hamburgerMenuView?.updateMicrophoneAudioLevel(rmsdB)
        fullSuggestionsBar?.updateMicrophoneAudioLevel(rmsdB)
    }
    
    /**
     * Shows or hides the speech recognition hint message.
     * When showing, replaces the swipe hint with speech recognition message.
     */
    fun showSpeechRecognitionHint(show: Boolean) {
        variationBarView?.showSpeechRecognitionHint(show)
    }

    /**
     * Updates only the clipboard badge count without re-rendering variations.
     */
    fun updateClipboardCount(count: Int) {
        variationBarView?.updateClipboardCount(count)
        hamburgerMenuView?.updateClipboardCount(count)
        fullSuggestionsBar?.updateClipboardCount(count)
    }

    /**
     * Briefly highlights a suggestion slot using the original suggestion index
     * ordering (0=center, 1=right, 2=left). Used for trackpad/swipe commits.
     */
    fun flashSuggestionSlot(suggestionIndex: Int) {
        fullSuggestionsBar?.flashSuggestionAtIndex(suggestionIndex)
    }

    companion object {
        private const val TAG = "StatusBarController"
        private val DEFAULT_BACKGROUND = Color.parseColor("#000000")
        private const val TITAN_2_ELITE_CORNER_FALLBACK_RADIUS_DP = 50f
        private const val HARDWARE_SYM_KEY_HEIGHT_DP = 56f
    }

    data class StatusSnapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean,
        val symPage: Int, // 0 = off, 1 = emoji page, 2 = characters page
        // Flux Keyboard: SYM and the emoji key held down, or tapped to apply to the next key
        val symHeld: Boolean = false,
        val symSticky: Boolean = false,
        val emojiHeld: Boolean = false,
        val emojiSticky: Boolean = false,
        val symPhysicallyPressed: Boolean = false,
        val clipboardOverlay: Boolean = false, // show the clipboard as its own view
        val clipboardCount: Int = 0, // numero di elementi in clipboard
        val variations: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val addWordCandidate: String? = null,
        val lastInsertedChar: Char? = null,
        // Granular smart features flags
        val shouldDisableSuggestions: Boolean = false,
        val shouldDisableAutoCorrect: Boolean = false,
        val shouldDisableAutoCapitalize: Boolean = false,
        val shouldDisableDoubleSpaceToPeriod: Boolean = false,
        val shouldDisableVariations: Boolean = false,
        val isEmailField: Boolean = false,
        // UI latch flags for static variation bar layers.
        val shiftLayerLatched: Boolean = false,
        val altModifierLayerLatched: Boolean = false,
        val activeKeyboardLayoutName: String = "qwerty",
        // The emoji key opened the current SYM page (its auto-close follows the emoji key setting)
        val emojiScreenFromEmojiKey: Boolean = false,
        val softwareSymPreviewLabels: Map<Int, String> = emptyMap(),
        val softwareSymPreviewTextLabels: Map<String, String> = emptyMap(),
        val softwareCtrlPreviewLabels: Map<Int, String> = emptyMap(),
        val softwareCtrlPreviewIconRes: Map<Int, Int> = emptyMap(),
        val softwareCtrlPreviewActive: Boolean = false,
        val softwareAltPreviewLabels: Map<Int, String> = emptyMap(),
        val softwareAltPreviewActive: Boolean = false,
        // Legacy flag for backward compatibility
        val shouldDisableSmartFeatures: Boolean = false
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var modifiersContainer: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var symSurfaceContainer: FrameLayout? = null
    private var symSurfaceStack: LinearLayout? = null
    private var symSurfaceCloseButton: View? = null
    private var emojiKeyboardContainer: LinearLayout? = null
    private var emojiKeyboardHorizontalPaddingPx: Int = 0
    private var emojiKeyboardBottomPaddingPx: Int = 0
    private var clipboardHistoryView: ClipboardHistoryView? = null
    private var lastClipboardCountRendered: Int = -1
    private var lastClipboardAccessibleRendered: Boolean? = null
    private var emojiPickerView: EmojiPickerView? = null
    // Pastierina: the picker's search field sits in the middle of the compact bar
    private var emojiSearchInBar: Boolean = false
    // Pastierina: on the emoji layer and symbols pages, a search bar sits there instead (tap for
    // the picker's search)
    private var emojiLayerBarRow: LinearLayout? = null
    // What the emoji layer grid's slot after L held when last built: search, GIF or nothing
    private var lastEmojiLayerSlot: String? = null

    /** Hidden app with "Show status LEDs only": draw nothing but the LED strip, over the app. */
    var ledsOnlyMode: Boolean = false

    /** LEDs only, right on the screen's edge (minimal mode); hidden-keyboard apps keep the usual place. */
    var ledsAtScreenEdge: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if ((statusBarLayout as? ImeChromeLayout)?.ledsOnly == true) refreshWindowInsets()
        }
    private var emojiPickerSearchPopup: PopupWindow? = null
    private var emojiPickerSearchPopupShowPending: Boolean = false
    private var softwareKeyboardView: AospKeyboardView? = null

    // removeAllViews() on the keyboard container dispatches a touch CANCEL to the
    // removed AospKeyboardView, whose modifier release synchronously re-runs update()
    // and would addView() a still-attached child. Swaps run under this guard and
    // re-entrant callers skip the reparenting entirely.
    private var swappingKeyboardContainerChildren: Boolean = false
    private var emojiKeyButtons: MutableList<View> = mutableListOf()
    private var lastSymPageRendered: Int = 0
    private var lastSymMappingsRendered: Map<Int, String>? = null
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var wasSymActive: Boolean = false
    private var isTitan2Layout: Boolean = false

    // Trackpad debug
    private var trackpadDebugLaunched = false
    private var symShown: Boolean = false
    private var lastSymHeight: Int = 0
    private val defaultSymHeightPx: Int
        get() = dpToPx(600f) // fallback when nothing measured yet
    private val ledStatusView = LedStatusView(context)
    private val buttonRegistry = StatusBarButtonRegistry()
    private val variationBarView: VariationBarView? =
        VariationBarView(context, assets, imeServiceClass, buttonRegistry)
    private var variationsWrapper: View? = null
    private var hamburgerMenuView: HamburgerMenuView? = null
    private var pastierinaModeActive: Boolean = false
    private var fullSuggestionsBar: FullSuggestionsBar? = null
    private var expansionSuggestions: List<String> = emptyList()
    private var onExpansionSuggestionSelected: ((String) -> Unit)? = null
    private var baseLeftPadding: Int = 0
    private var baseRightPadding: Int = 0
    private var baseBottomPadding: Int = 0
    private var lastHamburgerInputConnection: android.view.inputmethod.InputConnection? = null
    private var lastInsetsLogSignature: String? = null
    private var softwareKeyboardShown: Boolean = false
    private var lastSoftwareKeyboardHeight: Int = 0
    private var lastSoftwareKeyboardSymPageRendered: Int = 0
    private var lastSoftwareKeyboardSymLayoutRendered: String? = null
    private var lastSoftwareKeyboardSymStyleRendered: AospKeyboardView.SoftwareLayoutStyle? = null
    
    init {
        onHamburgerMenuRequested = { toggleHamburgerMenu() }
    }

    private fun logImeOverlayInsetsIfEnabled(
        navBottom: Int,
        imeBottom: Int,
        cutoutBottom: Int,
        bottomInset: Int,
        appliedBottomPadding: Int
    ) {
        if (!SettingsManager.isImeOverlayDebugLoggingEnabled(context)) {
            return
        }

        val signature = "$navBottom|$imeBottom|$cutoutBottom|$bottomInset|$appliedBottomPadding"
        if (signature == lastInsetsLogSignature) {
            return
        }
        lastInsetsLogSignature = signature

        Log.d(
            TAG,
            "IME overlay insets: nav=$navBottom ime=$imeBottom cutout=$cutoutBottom " +
                "bottomInset=$bottomInset baseBottomPadding=$baseBottomPadding " +
                "appliedBottomPadding=$appliedBottomPadding"
        )
    }

    private fun activeInputStyle(): Pair<String?, String?> {
        val subtype = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.currentInputMethodSubtype
        val locale = subtype?.localeString()
        val layout = if (assets != null) {
            AdditionalSubtypeUtils.resolveInputStyleLayout(assets, context, subtype)
        } else {
            SettingsManager.getKeyboardLayout(context)
        }
        return locale to layout
    }

    private fun hardwareTheme(): SettingsManager.KeyboardThemeSettings {
        val (locale, layout) = activeInputStyle()
        return SettingsManager.getEffectiveKeyboardTheme(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale,
            layout
        )
    }

    private fun softwareTheme(): SettingsManager.KeyboardThemeSettings {
        val (locale, layout) = activeInputStyle()
        return SettingsManager.getEffectiveKeyboardTheme(
            context,
            SettingsManager.KeyboardThemeTarget.SOFTWARE,
            locale,
            layout
        )
    }

    private fun activeThemeSettings(
        isFullSoftwareKeyboardMode: Boolean =
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
    ): SettingsManager.KeyboardThemeSettings =
        if (isFullSoftwareKeyboardMode) softwareTheme() else hardwareTheme()

    private fun activeThemeColors(
        isFullSoftwareKeyboardMode: Boolean =
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
    ): KeyboardThemeColors =
        activeThemeSettings(isFullSoftwareKeyboardMode).toKeyboardThemeColors()

    private fun applyKeyboardThemeOverrides(activeColors: KeyboardThemeColors) {
        val backgroundImage = KeyboardBackgroundImage.bitmap(context)
        (context as? InputMethodService)?.window?.window?.let { imeWindow ->
            imeWindow.navigationBarColor = backgroundImage?.let { KeyboardBackgroundImage.averageColour(context) }
                ?: activeColors.background
            imeWindow.isNavigationBarContrastEnforced = false
        }
        statusBarLayout?.let { layout ->
            // Flux Keyboard: the background picture, under the (see-through) theme background
            val current = layout.background as? KeyboardBackgroundImage.Drawable
            when {
                backgroundImage == null && current != null -> layout.background = null
                backgroundImage != null && current?.bitmap !== backgroundImage ->
                    layout.background = KeyboardBackgroundImage.Drawable(backgroundImage)
            }
            (layout.background as? KeyboardBackgroundImage.Drawable)?.framing =
                SettingsManager.getKeyboardBackgroundFraming(context)
            layout.setBackgroundColor(activeColors.background)
        }
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val surfaceBackground = if (roundedCorners) Color.TRANSPARENT else activeColors.background
        symSurfaceStack?.setBackgroundColor(surfaceBackground)
        symSurfaceContainer?.setBackgroundColor(surfaceBackground)
        (statusBarLayout as? ImeChromeLayout)?.let { chrome ->
            // Keep the spacing around the individually rounded buttons in the chrome background.
            chrome.regularCornerColors = activeColors.background to activeColors.background
            chrome.compactCornerColors = activeColors.background to activeColors.background
            chrome.bottomFillColors = activeColors.background to activeColors.background
            chrome.expandedCloseColor = activeColors.background
            chrome.expandedKeyHeightPx = hardwareSymKeyHeightPx(activeColors)
            chrome.invalidate()
        }
        emojiKeyboardContainer?.setBackgroundColor(activeColors.background)
        variationBarView?.themeOverride = activeColors
        ledStatusView.themeOverride = activeColors
        fullSuggestionsBar?.themeOverride = activeColors
        hamburgerMenuView?.themeOverride = activeColors
        clipboardHistoryView?.themeOverride = activeColors
        emojiPickerView?.themeOverride = activeColors
        applySurfaceCloseButtonTheme(activeColors)
    }

    private fun modifierLedLayout(): it.palsoftware.pastiera.inputmethod.ui.ModifierLedLayout {
        return ModifierLedLayouts.resolve(
            physicalProfileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(context),
            titan2EliteAutoDetected = DeviceSpecific.isTitan2EliteDevice(),
            emojiLed = SettingsManager.getEmojiKeyLedEnabled(context) &&
                SettingsManager.getEmojiPickerKey(context) != android.view.KeyEvent.KEYCODE_UNKNOWN
        )
    }

    private fun statusBarCallbacks(): StatusBarCallbacks =
        StatusBarCallbacks(
            onClipboardRequested = onClipboardRequested,
            onSpeechRecognitionRequested = onSpeechRecognitionRequested,
            onEmojiPickerRequested = onEmojiPickerRequested,
            onGifSearchRequested = onGifSearchRequested,
            onLanguageSwitchRequested = onLanguageSwitchRequested,
            onHamburgerMenuRequested = onHamburgerMenuRequested,
            onMinimalUiToggleRequested = { handleMinimalUiToggleFromMenu() },
            onSoftwareKeyboardModeToggleRequested = onSoftwareKeyboardModeToggleRequested,
            onOpenSettings = { openSettings() },
            onSymbolsPageRequested = onSymbolsPageRequested,
            onUndoRequested = onUndoRequested,
            onRedoRequested = onRedoRequested,
            onExtraKeysRequested = onExtraKeysRequested,
            onHapticFeedback = { NotificationHelper.triggerHapticFeedback(context) }
        )

    private fun applyChromeZOrder() {
        // Keep rows in normal child order. A positive translationZ casts a
        // full-width shadow at the chrome/keyboard boundary in software mode.
        fullSuggestionsBar?.ensureView()?.apply {
            elevation = 0f
            translationZ = 0f
        }
        variationsWrapper?.apply {
            elevation = 0f
            translationZ = 0f
        }
        symSurfaceContainer?.translationZ = 0f
        symSurfaceStack?.translationZ = 0f
        emojiKeyboardContainer?.translationZ = 0f
    }

    private fun ensureMainChildOrder() {
        val layout = statusBarLayout ?: return
        val suggestions = fullSuggestionsBar?.ensureView()
        val modifiers = modifiersContainer
        val variations = variationsWrapper
        val surface = symSurfaceContainer
        val children = listOf(suggestions, modifiers, variations, surface).filterNotNull()
        val alreadyOrdered = children.withIndex().all { (index, child) ->
            child.parent === layout && layout.indexOfChild(child) == index
        }
        if (alreadyOrdered) {
            return
        }
        children.forEach { child ->
            val parent = child.parent
            if (parent === layout) {
                layout.removeView(child)
            } else if (parent is ViewGroup) {
                parent.removeView(child)
            }
        }
        children.forEach { layout.addView(it) }
    }

    private fun SettingsManager.KeyboardThemeSettings.toAospThemeOverride(): AospKeyboardView.ThemeOverride =
        AospKeyboardView.ThemeOverride(
            background = background,
            divider = divider,
            normalKey = normalKey,
            specialKey = specialKey,
            textAndIcons = textAndIcons,
            ledInactive = ledInactive,
            ledActive = ledActive,
            ledLocked = ledLocked,
            accent = accent,
            keyPopup = keyPopup,
            keyPopupSelected = keyPopupSelected,
            keyPopupStyle = keyPopupStyle,
            keyPopupAttached = keyPopupAttached,
            keyPopupTailEnabled = keyPopupTailEnabled,
            keyPreviewAfterLongPress = keyPreviewAfterLongPress,
            keyAlternatesPopupEnabled = keyAlternatesPopupEnabled,
            keyCornerRadiusRatio = keyCornerRadiusRatio,
            keyHeightScale = keyHeightScale,
            numberRowHeightScale = numberRowHeightScale,
            keyWidthScale = keyWidthScale,
            rowGapScale = rowGapScale,
            distributeHorizontalSpacing = distributeHorizontalSpacing,
            ortholinear = ortholinear
        )

    fun setPastierinaModeActive(active: Boolean) {
        if (pastierinaModeActive == active) {
            return
        }
        pastierinaModeActive = active
        updatePastierinaModeState()
        if (active) {
            variationBarView?.hideImmediate()
            hideHamburgerMenu()
        }
    }

    fun isPastierinaModeActive(): Boolean = pastierinaModeActive

    fun dismissEmojiPickerPopup() {
        emojiPickerSearchPopupShowPending = false
        emojiPickerSearchPopup?.let { popup ->
            if (popup.isShowing) popup.dismiss()
            popup.contentView = null
        }
    }

    fun getLayout(): LinearLayout? = statusBarLayout

    fun refreshWindowInsets() {
        statusBarLayout?.let { ViewCompat.requestApplyInsets(it) }
    }

    fun collapseLayout() {
        val layout = statusBarLayout ?: return
        hideHamburgerMenu()
        layout.visibility = View.GONE
        layout.layoutParams = (layout.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
        }
        layout.requestLayout()
        (layout.parent as? View)?.requestLayout()
    }

    fun expandLayout() {
        val layout = statusBarLayout ?: return
        restoreLayoutHeight(layout)
        layout.requestLayout()
        (layout.parent as? View)?.requestLayout()
    }

    fun getOrCreateLayout(emojiMapText: String = ""): LinearLayout {
        if (statusBarLayout == null) {
            statusBarLayout = ImeChromeLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = true
                clipToPadding = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
            }
            statusBarLayout?.let { layout ->
                baseLeftPadding = layout.paddingLeft
                baseRightPadding = layout.paddingRight
                baseBottomPadding = layout.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
                    // Use getInsetsIgnoringVisibility to get stable insets for navigation and gesture areas
                    // We should NOT include IME insets as that would add padding when the keyboard itself is shown
                    val navAndGestures = insets.getInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.systemGestures()
                    )
                    val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    val useTitan2EliteRoundedCornerInsets =
                        SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
                    val platformInsets = insets.toWindowInsets()
                    val bottomLeftRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        platformInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
                    } else {
                        0
                    }
                    val bottomRightRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        platformInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
                    } else {
                        0
                    }
                    val fallbackCornerRadius = if (useTitan2EliteRoundedCornerInsets) {
                        dpToPx(TITAN_2_ELITE_CORNER_FALLBACK_RADIUS_DP)
                    } else {
                        0
                    }
                    val bottomInset = max(navAndGestures.bottom, cutout.bottom)
                    // Only the LEDs showing (minimal and terminal modes): right on the screen's edge
                    val appliedBottomPadding = if ((view as? ImeChromeLayout)?.ledsOnly == true && ledsAtScreenEdge) 0
                        else baseBottomPadding + bottomInset
                    (view as? ImeChromeLayout)?.bottomCornerRadiiPx =
                        if (useTitan2EliteRoundedCornerInsets) {
                            Pair(
                                bottomLeftRadius.takeIf { it > 0 } ?: fallbackCornerRadius,
                                bottomRightRadius.takeIf { it > 0 } ?: fallbackCornerRadius
                            )
                        } else {
                            null
                        }
                    (view as? ImeChromeLayout)?.fillDisplayCorners =
                        SettingsManager.getTitan2EliteFillCorners(context)
                    ledStatusView.bottomCornerRadiiPx = (view as? ImeChromeLayout)?.bottomCornerRadiiPx
                    view.updatePadding(
                        left = baseLeftPadding,
                        right = baseRightPadding,
                        bottom = appliedBottomPadding
                    )
                    logImeOverlayInsetsIfEnabled(
                        navBottom = navAndGestures.bottom,
                        imeBottom = 0,
                        cutoutBottom = cutout.bottom,
                        bottomInset = bottomInset,
                        appliedBottomPadding = appliedBottomPadding
                    )
                    insets
                }
            }

            // Container for modifier indicators (horizontal, left-aligned).
            // Add left padding to avoid the IME collapse button.
            val leftPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                64f, 
                context.resources.displayMetrics
            ).toInt()
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                16f, 
                context.resources.displayMetrics
            ).toInt()
            val verticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8f, 
                context.resources.displayMetrics
            ).toInt()
            
            modifiersContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(leftPadding, verticalPadding, horizontalPadding, verticalPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            // Container for emoji grid (when SYM is active) - placed at the bottom
            val emojiKeyboardHorizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                context.resources.displayMetrics
            ).toInt()
            val emojiKeyboardBottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f, // Bottom padding to clear the IME controls
                context.resources.displayMetrics
            ).toInt()
            emojiKeyboardHorizontalPaddingPx = emojiKeyboardHorizontalPadding
            emojiKeyboardBottomPaddingPx = emojiKeyboardBottomPadding
            
            emojiKeyboardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                // No top padding, only horizontal and bottom
                setPadding(emojiKeyboardHorizontalPadding, 0, emojiKeyboardHorizontalPadding, emojiKeyboardBottomPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }
            
            // Keep the TextView for backward compatibility (hidden)
            emojiMapTextView = TextView(context).apply {
                visibility = View.GONE
            }

            variationsWrapper = variationBarView?.ensureView()
            attachHamburgerMenu(variationsWrapper)
            ledStatusView.layout = modifierLedLayout()
            val ledStrip = ledStatusView.ensureView()
            ledStatusView.onLongPressListener = { handleMinimalUiToggleFromMenu() }

            fullSuggestionsBar = FullSuggestionsBar(
                context,
                buttonRegistry,
                callbacksProvider = { statusBarCallbacks() }
            )
            if (assets != null && imeServiceClass != null) {
                fullSuggestionsBar?.setSubtypeCyclingParams(assets, imeServiceClass)
            }
            extraKeysRow?.let { row -> fullSuggestionsBar?.apply { ensureView(); setExtraKeys(row) } }

            symSurfaceStack = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = true
                clipToPadding = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
                addView(emojiKeyboardContainer)
                addView(ledStrip)
            }
            symSurfaceCloseButton = createSurfaceCloseButton().also { close ->
                // The shared SYM/picker close button sits in the bottom-right display corner
                close.setTag(
                    R.id.tag_outer_edge_button,
                    StatusBarButtonPosition.RIGHT
                )
            }
            symSurfaceContainer = FrameLayout(context).apply {
                clipChildren = true
                clipToPadding = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
                addView(symSurfaceStack)
                addView(symSurfaceCloseButton)
            }

            statusBarLayout?.apply {
                addView(fullSuggestionsBar?.ensureView())
                addView(modifiersContainer)
                variationsWrapper?.let { addView(it) }
                addView(symSurfaceContainer)
            }
            (statusBarLayout as? ImeChromeLayout)?.apply {
                surfaceView = symSurfaceContainer
                indicatorView = ledStrip
                expandedSurfaceView = emojiKeyboardContainer
                compactStatusRow = fullSuggestionsBar?.ensureView()
                expandedCloseButton = symSurfaceCloseButton
                onContourGeometryChanged = { geometry ->
                    ledStatusView.contourGeometry = geometry
                }
                contourLedOverlay = { canvas -> ledStatusView.drawRailOverlay(canvas) }
            }
            applyChromeZOrder()
            applyAccessibilitySecondRowReadPreference()
            statusBarLayout?.let { ViewCompat.requestApplyInsets(it) }
        } else if (emojiMapText.isNotEmpty()) {
            emojiMapTextView?.text = emojiMapText
        }
        return statusBarLayout!!
    }

    private fun restoreLayoutHeight(layout: View) {
        val params = layout.layoutParams ?: return
        if (params.height != 0) {
            return
        }
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        layout.layoutParams = params
    }

    private fun attachHamburgerMenu(wrapper: View?) {
        val frame = wrapper as? FrameLayout ?: return
        val menu = hamburgerMenuView ?: HamburgerMenuView(context, buttonRegistry).also { hamburgerMenuView = it }
        menu.attachTo(frame)
    }

    private fun activeHamburgerWrapper(): View? {
        return variationsWrapper
    }

    private fun showHamburgerMenu() {
        if (hamburgerMenuView == null) {
            attachHamburgerMenu(activeHamburgerWrapper())
        } else {
            attachHamburgerMenu(activeHamburgerWrapper())
        }
        val menu = hamburgerMenuView ?: return
        val callbacks = statusBarCallbacks().copy(onHamburgerMenuRequested = null)
        menu.show(callbacks) { hideHamburgerMenu() }
        // The menu has the row to itself: no status LEDs under its buttons (space kept, no jump)
        ledStatusView.getView()?.let { if (it.visibility == View.VISIBLE) it.visibility = View.INVISIBLE }
    }

    private fun hideHamburgerMenu() {
        hamburgerMenuView?.hide()
        fullSuggestionsBar?.hideHamburgerMenu()
        ledStatusView.getView()?.let { if (it.visibility == View.INVISIBLE) it.visibility = View.VISIBLE }
    }

    private fun menuBarOpen(): Boolean = hamburgerMenuView?.isVisible() == true

    private var extraKeysRow: it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysRow? = null

    /** The extra keys row in the bar's place (null: the bar). */
    fun setExtraKeys(row: it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysRow?) {
        extraKeysRow = row
        if (row != null) hideHamburgerMenu()
        fullSuggestionsBar?.apply {
            if (row != null) ensureView()
            setExtraKeys(row)
        }
    }

    fun resetSuggestionActionMode() {
        fullSuggestionsBar?.resetActionMode()
    }

    fun showExpansionSuggestions(suggestions: List<String>, onSelected: (String) -> Unit) {
        expansionSuggestions = suggestions.take(3)
        onExpansionSuggestionSelected = onSelected
    }

    fun clearExpansionSuggestions() {
        expansionSuggestions = emptyList()
        onExpansionSuggestionSelected = null
    }

    // Inline autofill: chips drawn by the password manager, shown in the middle of the bar
    private var inlineAutofillViews: List<View> = emptyList()
    private var inlineAutofillStrip: android.widget.HorizontalScrollView? = null

    /** The colours the bar's suggestion buttons use, for the password manager's chips. */
    fun suggestionChipColours(): Pair<Int, Int> = activeThemeColors().let { it.suggestion to it.textAndIcons }

    /** The height of the bar's suggestion buttons (the bar's own), for the password manager's chips. */
    fun suggestionChipHeight(): Int? = fullSuggestionsBar?.centerAccessoryHost()?.layoutParams?.height?.takeIf { it > 0 }

    fun showInlineAutofill(views: List<View>) {
        inlineAutofillViews = views
        // Each new set of chips gets its own lines in a debug export
        lastAutofillRender = null
    }

    fun clearInlineAutofill() {
        if (inlineAutofillViews.isEmpty() && inlineAutofillStrip?.parent == null) return
        inlineAutofillViews = emptyList()
        renderInlineAutofill(false)
    }

    private var lastAutofillRender: String? = null

    private fun noteAutofillRender(state: String) {
        if (state == lastAutofillRender) return
        lastAutofillRender = state
        DebugCaptureStore.recordAutofill("bar: $state")
    }

    private fun renderInlineAutofill(active: Boolean) {
        val bar = fullSuggestionsBar ?: run {
            if (active) noteAutofillRender("no suggestion bar to show chips in")
            return
        }
        val host = bar.centerAccessoryHost() ?: run {
            if (active) noteAutofillRender("bar has no space for chips")
            return
        }
        if (inlineAutofillViews.isNotEmpty() && !active) noteAutofillRender("chips held back (symbols, emoji or clipboard open)")
        val strip = inlineAutofillStrip
        if (!active) {
            if (strip?.parent != null) {
                (strip.parent as? ViewGroup)?.removeView(strip)
                if (!emojiSearchInBar) bar.setCenterAccessoryActive(false)
            }
            return
        }
        val scroller = strip ?: android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            // Chips centred in the bar while they fit, scrolling when they don't
            isFillViewport = true
            addView(
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                },
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }.also { inlineAutofillStrip = it }
        val row = scroller.getChildAt(0) as android.widget.LinearLayout
        if (row.childCount != inlineAutofillViews.size ||
            inlineAutofillViews.withIndex().any { (i, v) -> (row.getChildAt(i) as? ViewGroup)?.getChildAt(0) !== v }
        ) {
            row.removeAllViews()
            val gap = (4 * context.resources.displayMetrics.density).toInt()
            val theme = activeThemeColors()
            inlineAutofillViews.forEach { chip ->
                (chip.parent as? ViewGroup)?.removeView(chip)
                // The size the chip was made at (wrap content measures it at 0 wide)
                val made = chip.layoutParams
                val width = made?.width?.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT
                // The chip is see-through: a suggestion button's background behind it, the bar's
                // height, with the chip centred in it
                val button = FrameLayout(context).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 7 * context.resources.displayMetrics.density
                        setColor(theme.suggestion)
                    }
                    addView(chip, FrameLayout.LayoutParams(
                        width,
                        made?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.CENTER
                    ))
                }
                row.addView(button, android.widget.LinearLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { marginStart = gap; marginEnd = gap })
            }
        }
        if (scroller.parent !== host) {
            (scroller.parent as? ViewGroup)?.removeView(scroller)
            host.removeAllViews()
            host.addView(scroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        bar.setCenterAccessoryActive(true)
        noteAutofillRender("showing ${inlineAutofillViews.size} chips")
        // Once laid out: where the chips ended up, for a debug export when they can't be seen
        scroller.post {
            val chip = inlineAutofillViews.firstOrNull() ?: return@post
            fun View.describe() = "${width}x$height shown=$isShown"
            noteAutofillRender(
                "laid out: chip ${chip.describe()} strip ${scroller.describe()} host ${host.describe()} " +
                    "bar ${(host.parent as? View)?.describe()} window=${host.rootView?.let { "${it.width}x${it.height}" }}"
            )
        }
    }

    fun cancelSoftwareKeyboardTouchState() {
        // The software keyboard can also sit below the emoji picker while its search is
        // active, so prefer the cached instance over the container's first child.
        (softwareKeyboardView ?: emojiKeyboardContainer?.getChildAt(0) as? AospKeyboardView)
            ?.cancelActiveTouchState()
    }

    private fun toggleHamburgerMenu() {
        if (hamburgerMenuView?.isVisible() == true) {
            hideHamburgerMenu()
        } else {
            showHamburgerMenu()
        }
    }

    private fun updatePastierinaModeState() {
        hamburgerMenuView?.setMinimalUiActive(pastierinaModeActive)
        fullSuggestionsBar?.setMinimalUiActive(pastierinaModeActive)
    }

    private fun handleMinimalUiToggleFromMenu() {
        onMinimalUiToggleRequested?.invoke()
        if (!pastierinaModeActive) {
            hideHamburgerMenu()
        }
    }

    fun handleBackPressed(): Boolean {
        if (fullSuggestionsBar?.isHamburgerMenuVisible() == true || hamburgerMenuView?.isVisible() == true) {
            hideHamburgerMenu()
            return true
        }
        return false
    }

    fun handleEmojiPickerSearchKeyDown(
        event: KeyEvent?,
        ctrlActive: Boolean,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        if (event == null) return false
        return emojiPickerView?.handleSearchKeyDown(event, ctrlActive, resolveTypedText) == true
    }

    fun shouldConsumeEmojiPickerSearchKeyUp(event: KeyEvent?, ctrlActive: Boolean): Boolean {
        if (event == null) return false
        return emojiPickerView?.shouldConsumeSearchKeyUp(event, ctrlActive) == true
    }

    fun disableEmojiPickerSearchInputCapture() {
        emojiPickerView?.disableSearchInputCapture()
    }

    fun isEmojiPickerSearchInputActive(): Boolean {
        return emojiPickerView?.isSearchInputActive() == true
    }

    fun createEmojiPickerSearchInputConnection(): android.view.inputmethod.InputConnection? {
        return emojiPickerView?.createSearchInputConnection()
    }

    private fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Settings", e)
        }
    }

    /**
     * Ensures the layout is created before updating.
     * This is important for candidates view which may not have been created yet.
     */
    private fun ensureLayoutCreated(emojiMapText: String = ""): LinearLayout? {
        return statusBarLayout ?: getOrCreateLayout(emojiMapText)
    }
    
    /**
     * Recursively finds a clickable view at the given coordinates in the view hierarchy.
     * Coordinates are relative to the parent view.
     */
    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            // Single view: check if it's clickable and contains the point
            if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                return parent
            }
            return null
        }
        
        // For ViewGroup, check children first (they are on top)
        // Iterate in reverse to check topmost views first
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()
                
                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    // Point is inside this child, recurse with relative coordinates
                    val childX = x - childLeft
                    val childY = y - childTop
                    val found = findClickableViewAt(child, childX, childY)
                    if (found != null) {
                        return found
                    }
                    
                    // If child itself is clickable, return it
                    if (child.isClickable) {
                        return child
                    }
                }
            }
        }
        
        // If no child was found and parent is clickable, return parent
        if (parent.isClickable) {
            return parent
        }
        
        return null
    }

    private fun updateMenuBarModifierIndicators(
        container: LinearLayout,
        snapshot: StatusSnapshot,
        show: Boolean,
        theme: KeyboardThemeColors
    ) {
        container.removeAllViews()
        if (!show) {
            container.visibility = View.GONE
            return
        }

        val indicators = buildMenuBarModifierIndicators(snapshot)
        if (indicators.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        indicators.forEachIndexed { index, indicator ->
            val view = when (indicator) {
                is MenuBarModifierIndicator.Icon -> ImageView(context).apply {
                    setImageResource(indicator.resId)
                    setColorFilter(if (indicator.locked) theme.ledLocked else theme.ledActive)
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = indicator.description
                }
                is MenuBarModifierIndicator.Text -> TextView(context).apply {
                    text = indicator.label
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(if (indicator.locked) theme.ledLocked else theme.ledActive)
                    contentDescription = indicator.description
                }
            }
            container.addView(
                view,
                LinearLayout.LayoutParams(dpToPx(26f), dpToPx(26f)).apply {
                    if (index != indicators.lastIndex) marginEnd = dpToPx(2f)
                }
            )
        }
        container.visibility = View.VISIBLE
    }

    private fun buildMenuBarModifierIndicators(snapshot: StatusSnapshot): List<MenuBarModifierIndicator> {
        val indicators = mutableListOf<MenuBarModifierIndicator>()
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        if (shiftLocked || shiftActive) {
            indicators.add(
                MenuBarModifierIndicator.Icon(
                    resId = if (shiftLocked) R.drawable.shift_filled_24 else R.drawable.shift_24,
                    locked = shiftLocked,
                    description = "Shift"
                )
            )
        }

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        if (ctrlLocked || ctrlActive) {
            indicators.add(
                MenuBarModifierIndicator.Icon(
                    resId = R.drawable.keyboard_control_key_24,
                    locked = ctrlLocked,
                    description = "Ctrl"
                )
            )
        }

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        if (altLocked || altActive) {
            indicators.add(
                MenuBarModifierIndicator.Icon(
                    resId = R.drawable.keyboard_option_key_24,
                    locked = altLocked,
                    description = "Alt"
                )
            )
        }

        if (snapshot.symPage > 0 || snapshot.symPhysicallyPressed) {
            indicators.add(
                MenuBarModifierIndicator.Text(
                    label = "SYM",
                    locked = snapshot.symPage == 2,
                    description = "SYM"
                )
            )
        }

        return indicators
    }

    private sealed class MenuBarModifierIndicator(open val locked: Boolean, open val description: String) {
        data class Icon(
            val resId: Int,
            override val locked: Boolean,
            override val description: String
        ) : MenuBarModifierIndicator(locked, description)

        data class Text(
            val label: String,
            override val locked: Boolean,
            override val description: String
        ) : MenuBarModifierIndicator(locked, description)
    }
    
    /**
     * Updates the clipboard history view inline in the keyboard container.
     */
    private fun updateClipboardView(
        inputConnection: android.view.inputmethod.InputConnection? = null,
        softwareKeyboardHeight: Int? = null
    ) {
        val manager = clipboardHistoryManager ?: return
        val container = emojiKeyboardContainer ?: return
        // Clipboard page should be edge-to-edge; remove the SYM container side padding.
        container.setPadding(0, 0, 0, 0)

        // Reuse the same view to avoid flicker caused by removeAllViews()/recreate on each status update.
        val view = clipboardHistoryView ?: ClipboardHistoryView(context, manager) {
            onSymCloseRequested?.invoke()
        }.also { clipboardHistoryView = it }
        view.themeOverride = (if (
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) softwareTheme() else hardwareTheme()).toKeyboardThemeColors()
        if (view.parent !== container && !swappingKeyboardContainerChildren) {
            withKeyboardContainerSwap {
                container.removeAllViews()
                emojiKeyButtons.clear()
                container.addView(view)
            }
        }
        view.configureSoftwareKeyboardMode(softwareKeyboardHeight)
        view.configureRoundedLayout(
            SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        )
        view.setInputConnection(inputConnection)

        // Refresh only when needed (data changed), otherwise keep the list stable.
        val count = manager.getHistorySize()
        val accessible = manager.isHistoryAccessible()
        if (count != lastClipboardCountRendered || accessible != lastClipboardAccessibleRendered) {
            manager.prepareClipboardHistory()
            view.refresh()
            lastClipboardCountRendered = count
            lastClipboardAccessibleRendered = accessible
        }
        lastSymPageRendered = 3
    }

    /**
     * Updates the emoji picker view inline in the keyboard container.
     */
    private fun updateEmojiPickerView(
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection? = null,
        softwareKeyboardHeight: Int? = null
    ) {
        val container = emojiKeyboardContainer ?: return
        // Emoji picker page should be edge-to-edge; remove the SYM container side padding.
        val gap = expandedScreenGapPx()
        container.setPadding(0, gap, 0, gap)

        // Reuse the same view to avoid flicker caused by removeAllViews()/recreate on each status update.
        val view = emojiPickerView ?: EmojiPickerView(context) {
            onSymCloseRequested?.invoke()
        }.also { emojiPickerView = it }
        view.onSearchPanelVisibilityChanged = { visible ->
            onEmojiPickerSearchPanelToggled?.invoke(visible)
        }
        val freshOpen = lastSymPageRendered != 4
        val requested = pendingEmojiPickerSearch || pendingEmojiPickerGifs || pendingSymbolSearch
        val searchText = pendingSearchText
        pendingSearchText = null
        if (pendingEmojiPickerSearch) {
            // Opened from the emoji layer's search button: search once the picker is in place
            pendingEmojiPickerSearch = false
            view.post {
                view.openSearch()
                searchText?.let { view.handleSearchTextInput(it) }
            }
        } else if (freshOpen && !requested) {
            // Opened by the emoji key: its search takes typing at once, or after a tap
            val focus = SettingsManager.getEmojiPickerFocusSearch(context)
            view.post { view.applyOpenFocus(focus) }
        }
        view.onGifChosen = { gif -> onGifChosen?.invoke(gif) }
        if (pendingEmojiPickerGifs) {
            // Opened from the emoji layer's GIF key: GIF search once the picker is in place
            pendingEmojiPickerGifs = false
            view.post { view.openGifs() }
        }
        if (pendingSymbolSearch) {
            // Opened from a symbols page's search: symbol search once the picker is in place
            pendingSymbolSearch = false
            val kaomoji = pendingKaomojiSearch
            pendingKaomojiSearch = false
            view.post {
                view.openSymbols(kaomoji)
                searchText?.let { view.handleSearchTextInput(it) }
            }
        }
        view.themeOverride = (if (
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) softwareTheme() else hardwareTheme()).toKeyboardThemeColors()
        val wasDetachedFromHost = view.parent == null
        val needsContainerAttachment = view.parent !== container
        val pickerShownAboveSoftwareKeyboard =
            softwareKeyboardHeight != null &&
                view.isSearchPanelShowing() &&
                showEmojiPickerSearchPopup(
                    container,
                    view,
                    snapshot,
                    inputConnection,
                    softwareKeyboardHeight
                )
        if (!pickerShownAboveSoftwareKeyboard) {
            dismissEmojiPickerSearchPopup(view)
            if (!swappingKeyboardContainerChildren && (needsContainerAttachment || container.childCount != 1)) {
                withKeyboardContainerSwap {
                    view.reorderingWithinContainer {
                        if (container.getChildAt(0) === view) {
                            // Unstack without detaching the picker: only remove the
                            // views stacked below it (e.g. the software keyboard).
                            while (container.childCount > 1) {
                                container.removeViewAt(container.childCount - 1)
                            }
                        } else {
                            container.removeAllViews()
                            emojiKeyButtons.clear()
                            container.addView(view)
                        }
                    }
                }
            }
            view.configureSoftwareKeyboardMode(
                heightPx = softwareKeyboardHeight,
                onKeyboardLayoutRequested = if (softwareKeyboardHeight != null) onEmojiPickerRequested else null
            )
        } else {
            // Search active: the main IME window keeps its normal keyboard height. The compact
            // picker is rendered in a separate window above it, avoiding a Surface resize.
            view.configureSoftwareKeyboardMode(
                heightPx = null,
                onKeyboardLayoutRequested = null
            )
        }
        val roundedControls = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context) && !pickerShownAboveSoftwareKeyboard
        val colors = activeThemeColors()
        val iconSize = if (pastierinaModeActive) {
            (dpToPx(36f * colors.suggestionsHeightScale.coerceIn(0.65f, 1.6f)) - dpToPx(4f)) * 0.64f
        } else minOf(dpToPx(24f).toFloat(), hardwareSymKeyHeightPx(colors) * 0.48f)
        view.configureRoundedControls(roundedControls, hardwareSymKeyHeightPx(colors), iconSize, panelSideButtonWidthPx())
        (statusBarLayout as? ImeChromeLayout)?.expandedPickerButtons = if (roundedControls) view.edgeControls else null
        // The picker's search toggle is its bottom-left corner button (straight outer buttons)
        view.edgeControls.first.setTag(
            R.id.tag_outer_edge_button,
            if (roundedControls) StatusBarButtonPosition.LEFT else null
        )
        // Its close button is the bottom-right one, shaped and sized like the layers' close key
        view.edgeControls.second.setTag(
            R.id.tag_outer_edge_button,
            if (roundedControls) StatusBarButtonPosition.RIGHT else null
        )
        view.setInputConnection(inputConnection)
        val bar = fullSuggestionsBar
        val barHost = bar?.centerAccessoryHost()
        if (emojiSearchInBar && !pickerShownAboveSoftwareKeyboard && bar != null && barHost != null) {
            bar.setCenterAccessoryActive(true)
            view.setSearchFieldHost(barHost)
        } else {
            releaseEmojiSearchFromBar()
        }

        // Only scroll to top when view is just added (first open or switching pages)
        // Don't scroll if view is already in container (user is browsing)
        if (lastSymPageRendered != 4 || view.isStaleForCurrentEditor()) {
            // First time, switching from another page, or the field / emoji font changed.
            // Only a fresh opening leaves GIF or symbol search; a data refresh keeps it.
            view.refresh(resetModes = lastSymPageRendered != 4)
        } else if (wasDetachedFromHost) {
            view.scrollToTop() // View was just added (happens when reopening after being removed)
        }
        lastSymPageRendered = 4
    }

    private fun renderLedsOnly(
        snapshot: StatusSnapshot,
        layout: LinearLayout,
        emojiKeyboardView: View,
        symSurfaceView: FrameLayout
    ) {
        ledStatusView.getView()?.visibility = View.VISIBLE
        ledStatusView.layout = modifierLedLayout()
        // Only lit LEDs, where the LEDs normally are (between the corner buttons' places)
        ledStatusView.hideOffLeds = true
        val wasLedsOnly = (statusBarLayout as? ImeChromeLayout)?.ledsOnly == true
        (statusBarLayout as? ImeChromeLayout)?.ledsOnly = true
        if (!wasLedsOnly) refreshWindowInsets()
        ledStatusView.update(snapshot)
        hideHamburgerMenu()
        releaseEmojiSearchFromBar()
        updateEmojiLayerSearchBar(false)
        fullSuggestionsBar?.ensureView()?.visibility = View.GONE
        variationBarView?.hideImmediate()
        variationsWrapper?.visibility = View.GONE
        emojiKeyboardView.visibility = View.GONE
        setSurfaceCloseVisible(false)
        resetSymSurfaceToLedOnly(symSurfaceView)
        // Only the LEDs are drawn; the app stays visible (and touchable) around them, the
        // navigation bar's strip too (it takes the keyboard's colour only with the bar)
        (context as? InputMethodService)?.window?.window?.navigationBarColor = Color.TRANSPARENT
        layout.setBackgroundColor(Color.TRANSPARENT)
        symSurfaceStack?.setBackgroundColor(Color.TRANSPARENT)
        symSurfaceContainer?.setBackgroundColor(Color.TRANSPARENT)
        symShown = false
        wasSymActive = false
        lastSymPageRendered = 0
    }

    /**
     * Emoji layer in Pastierina: a search bar in the middle of the bar, looking like the picker's
     * own search field there. Physical keys keep typing the layer's emoji; tapping the bar opens
     * the picker with search ready, and the picker's field then takes this same spot.
     */
    private fun updateEmojiLayerSearchBar(show: Boolean, symbols: Boolean = false) {
        // The symbols page showing kaomoji: the bar searches the kaomoji
        val kaomoji = symbols && it.palsoftware.pastiera.core.SymLayoutController.kaomojiShown
        val bar = fullSuggestionsBar
        val host = bar?.centerAccessoryHost()
        val existing = emojiLayerBarRow
        if (show && bar != null && host != null) {
            val row = existing ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(context).apply {
                    textSize = 14f
                    setSingleLine(true)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(dpToPx(8f), 0, dpToPx(8f), 0)
                    isClickable = true
                    isFocusable = true
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                // GIF search: the emoji layer's GIF key (P unless changed), not a tab here
            }.also { emojiLayerBarRow = it }
            val searchBar = row.getChildAt(0) as TextView
            // Emoji layer: emoji search; symbols pages: symbol search
            searchBar.contentDescription = context.getString(
                when {
                    kaomoji -> R.string.kaomoji_search_placeholder
                    symbols -> R.string.symbol_search_placeholder
                    else -> R.string.emoji_layer_search_button
                }
            )
            searchBar.setOnClickListener {
                when {
                    kaomoji -> onKaomojiSearchRequested?.invoke()
                    symbols -> onSymbolSearchRequested?.invoke()
                    else -> onEmojiLayerSearchRequested?.invoke()
                }
            }
            if (row.parent !== host) {
                (row.parent as? ViewGroup)?.removeView(row)
                host.removeAllViews()
                host.addView(
                    row,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply { setMargins(dpToPx(4f), dpToPx(3f), dpToPx(4f), dpToPx(3f)) }
                )
            }
            // Same look as the picker's search field (EmojiPickerView)
            val theme = activeThemeColors()
            fun barBackground() = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(theme.suggestion)
                setStroke(dpToPx(1f), theme.divider)
                cornerRadius = dpToPx(7f).toFloat()
            }
            searchBar.hint = context.getString(
                when {
                    kaomoji -> R.string.kaomoji_search_placeholder
                    symbols -> R.string.symbol_search_placeholder
                    else -> R.string.emoji_picker_search_placeholder
                }
            )
            searchBar.setHintTextColor((theme.textAndIcons and 0x00FFFFFF) or (160 shl 24))
            searchBar.background = barBackground()
            bar.setCenterAccessoryActive(true)
        } else if (existing != null && existing.parent != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            if (!emojiSearchInBar) bar?.setCenterAccessoryActive(false)
        }
    }

    private fun releaseEmojiSearchFromBar() {
        emojiPickerView?.setSearchFieldHost(null)
        // Autofill chips keep the middle of the bar while they're shown
        if (inlineAutofillStrip?.parent == null) fullSuggestionsBar?.setCenterAccessoryActive(false)
    }

    private fun showEmojiPickerSearchPopup(
        container: ViewGroup,
        picker: EmojiPickerView,
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection?,
        softwareKeyboardHeight: Int
    ): Boolean {
        val layout = statusBarLayout ?: return false
        val keyboardView = ensureSoftwareKeyboardViewInstance(container)
        val keyboardHeight = softwareKeyboardHeight.takeIf { it > 0 }
            ?: measureSoftwareKeyboardDesiredHeight(keyboardView, layout).takeIf { it > 0 }
            ?: return false
        configureSoftwareKeyboard(keyboardView, snapshot, inputConnection, null)
        if (swappingKeyboardContainerChildren) {
            keyboardView.visibility = View.VISIBLE
            return true
        }

        if (container.childCount != 1 || container.getChildAt(0) !== keyboardView) {
            withKeyboardContainerSwap {
                picker.reorderingWithinContainer {
                    if (picker.parent === container) {
                        container.removeView(picker)
                    }
                    container.removeAllViews()
                    emojiKeyButtons.clear()
                    container.addView(
                        keyboardView,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            keyboardHeight
                        )
                    )
                }
            }
        }
        keyboardView.visibility = View.VISIBLE

        val popup = emojiPickerSearchPopup ?: PopupWindow(context).apply {
            isFocusable = false
            isTouchable = true
            isOutsideTouchable = false
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            animationStyle = 0
        }.also { emojiPickerSearchPopup = it }
        if (popup.contentView !== picker) {
            popup.contentView = picker
        }
        popup.width = context.resources.displayMetrics.widthPixels
        popup.height = EmojiPickerView.configuredHeightPx(context)
        if (!popup.isShowing && !emojiPickerSearchPopupShowPending) {
            emojiPickerSearchPopupShowPending = true
            layout.post {
                emojiPickerSearchPopupShowPending = false
                if (picker.isSearchPanelShowing() && !popup.isShowing && layout.isAttachedToWindow) {
                    popup.showAtLocation(layout, Gravity.BOTTOM, 0, keyboardHeight)
                }
            }
        }
        return true
    }

    private fun dismissEmojiPickerSearchPopup(picker: EmojiPickerView) {
        emojiPickerSearchPopupShowPending = false
        val popup = emojiPickerSearchPopup ?: return
        if (popup.isShowing) {
            picker.reorderingWithinContainer {
                popup.dismiss()
            }
        }
        if (popup.contentView === picker) {
            popup.contentView = null
        }
    }

    /**
     * Updates the emoji/character grid with the SYM mappings.
     * @param symMappings Le mappature da visualizzare
     * @param page The active page (1 = emoji, 2 = characters)
     * @param inputConnection The input connection that tapped buttons type into
     */
    private fun updateEmojiKeyboard(symMappings: Map<Int, String>, page: Int, inputConnection: android.view.inputmethod.InputConnection? = null) {
        val container = emojiKeyboardContainer ?: return
        // Restore default padding for emoji/symbols pages.
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val sidePadding = emojiKeyboardHorizontalPaddingPx
        val gap = expandedScreenGapPx()
        container.setPadding(sidePadding, gap, sidePadding, gap)
        val inputConnectionChanged = lastInputConnectionUsed != inputConnection
        val inputConnectionBecameAvailable = lastInputConnectionUsed == null && inputConnection != null
        // The slot after L: search outside Pastierina (which has it, and the GIF tab, in the bar)
        val searchInGrid = page in listOf(1, 2, 5) && !pastierinaModeActive
        val slot = if (searchInGrid) "search" else "none"
        if (lastSymPageRendered == page && lastSymMappingsRendered == symMappings && !inputConnectionChanged &&
            !inputConnectionBecameAvailable && lastEmojiLayerSlot == slot
        ) {
            return
        }
        lastEmojiLayerSlot = slot
        
        // Remove the existing keys
        container.removeAllViews()
        emojiKeyButtons.clear()
        
        // Keyboard rows
        val keyboardRows = listOf(
            listOf(android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_E, 
                   android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_Y, 
                   android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_I, android.view.KeyEvent.KEYCODE_O, 
                   android.view.KeyEvent.KEYCODE_P),
            listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D, 
                   android.view.KeyEvent.KEYCODE_F, android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_H, 
                   android.view.KeyEvent.KEYCODE_J, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_L),
            listOf(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C, 
                   android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_N, 
                   android.view.KeyEvent.KEYCODE_M)
        )
        
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Fixed key width, from the first row (10 keys)
        val maxKeysInRow = 10 // The first row has 10 keys
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f * 2, // padding sinistro + destro
            context.resources.displayMetrics
        ).toInt()
        val availableWidth = screenWidth - horizontalPadding
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        val fixedKeyWidth = (availableWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = layerKeyHeightPx(page)
        
        // Build each keyboard row
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isTitan2Layout) Gravity.START else Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            if (isTitan2Layout) {
                // Ortholinear layout for Titan 2
                when (rowIndex) {
                    0 -> { // Row 1: Q W E R T Y U I O P (10 keys)
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, index == row.size - 1)
                        }
                    }
                    1 -> { // Row 2: A S D F G H J K L (9 keys) -> Add placeholder at the end to make it 10
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, false)
                        }
                        if (searchInGrid) {
                            // Emoji layer outside Pastierina: search in the free slot after L
                            rowLayout.addView(createEmojiLayerSearchButton(keyHeight, fixedKeyWidth, page))
                        } else {
                            rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                        }
                    }
                    2 -> { // Row 3: Z X C V [Editor] [Globe] B N M [Close]
                        // Z X C V (4 keys)
                        for (i in 0..3) {
                            addKeyToRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, false)
                        }
                        // Z sits in the bottom-left display corner (straight outer buttons)
                        rowLayout.getChildAt(0)?.setTag(
                            R.id.tag_outer_edge_button,
                            StatusBarButtonPosition.LEFT
                        )
                        
                        // Editor button (left part of spacebar area)
                        val editorButton = createSymEditorButton(keyHeight, fixedKeyWidth, page)
                        rowLayout.addView(editorButton)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // Globe Button (right part of spacebar area)
                        val selectionButton = createKeyboardSelectionButton(keyHeight, fixedKeyWidth)
                        rowLayout.addView(selectionButton)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // B N M (3 keys)
                        for (i in 4..6) {
                            addKeyToRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, false)
                        }

                        rowLayout.addView(createGridCloseButton(), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                }
                container.addView(rowLayout)
                continue
            }
            
            // Default non-Titan 2 layout logic...
            // (The rest of the loop for non-Titan 2 remains the same)
            
            // Third row: a placeholder with the emoji picker button on the left
            // Without an emoji key, the bottom-left slot switches between emoji and symbols;
            // with one, it holds the pencil
            // A layer shown as pages has nothing to edit, so its corner swaps to the other layer
            // (symbols from the emoji layer, emoji from the symbols) for touch
            val swapButtonShown = SettingsManager.getEmojiPickerKey(context) == android.view.KeyEvent.KEYCODE_UNKNOWN ||
                layerShowsPages(page)
            // The bottom row: corner keys as wide as the bar's side buttons (less the grid's own
            // edge padding, which they reach across), the seven keys centred between spacers
            val bottomSideWidth = (panelSideButtonWidthPx() - horizontalPadding / 2).coerceAtLeast(fixedKeyWidth / 2)
            if (rowIndex == 2) {
                val leftPlaceholder = (if (swapButtonShown) createPlaceholderWithEmojiPickerButton(keyHeight, page)
                    else createPlaceholderWithPencilButton(keyHeight, page)).apply {
                    // The bottom-left key mirrors the close key: its shape and colour, in the left
                    // display corner, so both corners (and the LEDs between them) match
                    background = createCloseButtonBackground(activeThemeColors())
                    setTag(R.id.tag_outer_edge_button, StatusBarButtonPosition.LEFT)
                    // With the swap button there, holding it opens the editor (the pencil's job)
                    if (swapButtonShown) setOnLongClickListener {
                        openSymCustomization(page = page, keyCode = null, openPicker = false)
                        true
                    }
                }
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(bottomSideWidth, keyHeight))
                rowLayout.addView(View(context), LinearLayout.LayoutParams(0, keyHeight, 1f))
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val content = symMappings[keyCode] ?: ""
                
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                emojiKeyButtons.add(keyButton)
                keyButton.isLongClickable = true
                keyButton.setOnLongClickListener {
                    openSymCustomization(page = page, keyCode = keyCode, openPicker = true)
                    true
                }
                
                bindLayerKey(keyButton, keyCode, content, page, inputConnection)
                
                // Fixed width instead of weight
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    // Margin only between keys, not after the last one
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // Third row: a placeholder with the pencil icon on the right
            if (rowIndex == 2) {
                rowLayout.addView(View(context), LinearLayout.LayoutParams(0, keyHeight, 1f))
                rowLayout.addView(createGridCloseButton(), LinearLayout.LayoutParams(bottomSideWidth, keyHeight))
            }
            
            container.addView(rowLayout)
        }

        // Cache what was rendered to avoid rebuilding on each status refresh
        lastSymPageRendered = page
        lastSymMappingsRendered = HashMap(symMappings)
        lastInputConnectionUsed = inputConnection
    }

    private inline fun <T> withKeyboardContainerSwap(block: () -> T): T {
        swappingKeyboardContainerChildren = true
        try {
            return block()
        } finally {
            swappingKeyboardContainerChildren = false
        }
    }

    private fun updateSoftwareKeyboard(
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection? = null,
        symMappings: Map<Int, String>? = null
    ) {
        val container = emojiKeyboardContainer ?: return
        container.setPadding(0, 0, 0, emojiKeyboardBottomPaddingPx)
        val keyboardView = obtainSoftwareKeyboardView(container)
        configureSoftwareKeyboard(keyboardView, snapshot, inputConnection, symMappings)
        (keyboardView.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (
                params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                params.height != 0 ||
                params.weight != 1f
            ) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = 0
                params.weight = 1f
                keyboardView.layoutParams = params
            }
        }
        softwareKeyboardShown = true
        lastSymPageRendered = 0
        lastInputConnectionUsed = inputConnection
    }

    /**
     * Returns the cached software keyboard view, placing it as the container's only child
     * (weight-based) so it fills the whole surface. Used by the plain software keyboard page.
     */
    private fun obtainSoftwareKeyboardView(container: ViewGroup): AospKeyboardView {
        val view = ensureSoftwareKeyboardViewInstance(container)
        if (swappingKeyboardContainerChildren || (container.childCount == 1 && container.getChildAt(0) === view)) {
            view.visibility = View.VISIBLE
            return view
        }
        withKeyboardContainerSwap {
            container.removeAllViews()
            emojiKeyButtons.clear()
            container.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        view.visibility = View.VISIBLE
        return view
    }

    private fun ensureSoftwareKeyboardViewInstance(container: ViewGroup): AospKeyboardView {
        return softwareKeyboardView ?: AospKeyboardView(context).also { view ->
            var parent: ViewGroup? = container
            while (parent != null) {
                parent.clipChildren = false
                parent.clipToPadding = false
                parent = parent.parent as? ViewGroup
            }
            softwareKeyboardView = view
        }
    }

    private fun softwareKeyboardSearchTarget(snapshot: StatusSnapshot): EmojiPickerView? {
        if (snapshot.symPage != 4) return null
        val picker = emojiPickerView ?: return null
        if (!picker.isSearchInputActive()) return null
        return picker
    }

    private fun configureSoftwareKeyboard(
        keyboardView: AospKeyboardView,
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection?,
        symMappings: Map<Int, String>?
    ) {
        val uppercase = snapshot.capsLockEnabled || snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot
        val layoutName = resolveSoftwareKeyboardLayoutName(snapshot)
        keyboardView.visibility = View.VISIBLE
        keyboardView.listener = object : AospKeyboardView.Listener {
            override fun onText(text: String) {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val searchTarget = softwareKeyboardSearchTarget(snapshot)
                if (searchTarget != null && searchTarget.handleSearchTextInput(text)) {
                    return
                }
                val handled = onSoftwareKeyboardTextInput?.invoke(text, inputConnection, snapshot) == true
                if (!handled) {
                    inputConnection?.commitText(text, 1)
                }
            }

            override fun onBackspace() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val searchTarget = softwareKeyboardSearchTarget(snapshot)
                if (searchTarget != null && searchTarget.handleSearchBackspace()) {
                    return
                }
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }

            override fun onEnter() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val searchTarget = softwareKeyboardSearchTarget(snapshot)
                if (searchTarget != null) {
                    searchTarget.commitTopSearchResultAndClose()
                    return
                }
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }

            override fun onShift() {
                onSoftwareKeyboardShiftTapped?.invoke()
            }

            override fun onSymbols() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                prepareSoftwareKeyboardForSymbolTransition()
                onSoftwareKeyboardSymToggleRequested?.invoke()
            }

            override fun onCtrl() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT))
            }

            override fun onLanguageSwitch() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                onLanguageSwitchRequested?.invoke()
            }

            override fun onCursorMove(delta: Int) {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val connection = inputConnection ?: return
                val moved = if (delta < 0) {
                    TextSelectionHelper.moveCursorLeft(connection)
                } else {
                    TextSelectionHelper.moveCursorRight(connection)
                }
                if (moved) {
                    onCursorMovedListener?.invoke()
                }
            }

            override fun onKeyPressSound(keyCode: Int) {
                onSoftwareKeyboardKeyPressed?.invoke(keyCode)
            }

            override fun onModifierKeyDown(keyCode: Int): Boolean {
                return onSoftwareKeyboardModifierKeyDown?.invoke(keyCode) == true
            }

            override fun onModifierKeyUp(keyCode: Int): Boolean {
                return onSoftwareKeyboardModifierKeyUp?.invoke(keyCode) == true
            }

            override fun onKeyStroke(keyCode: Int, text: String): Boolean {
                return onSoftwareKeyboardKeyStroke?.invoke(keyCode, text) == true
            }

            override fun onSymbolText(text: String): Boolean {
                val connection = inputConnection ?: return false
                commitTouchSymbolAfterCloseIfNeeded(keyboardView, connection, text)
                return true
            }

            override fun onSymbolLongPress(keyCode: Int): Boolean {
                val page = snapshot.symPage
                if (page !in 1..2) {
                    return false
                }
                openSymCustomization(page = page, keyCode = keyCode, openPicker = true)
                return true
            }
        }
        keyboardView.layoutName = layoutName
        keyboardView.layoutStyle = softwareKeyboardLayoutStyle()
        keyboardView.includeNumberRow = SettingsManager.getSoftwareKeyboardNumberRowEnabled(context)
        keyboardView.nearestKeyTouchEnabled =
            SettingsManager.getSoftwareKeyboardNearestKeyTouchEnabled(context)
        keyboardView.shifted = uppercase
        keyboardView.shiftLocked = snapshot.capsLockEnabled
        keyboardView.ctrlOneShot = snapshot.ctrlOneShot
        keyboardView.ctrlLocked = snapshot.ctrlLatchActive || snapshot.ctrlLatchFromNavMode
        keyboardView.ctrlPressed = snapshot.ctrlPhysicallyPressed
        keyboardView.ctrlPreviewActive = snapshot.softwareCtrlPreviewActive
        keyboardView.altOneShot = snapshot.altOneShot
        keyboardView.altLocked = snapshot.altLatchActive
        keyboardView.altPressed = snapshot.altPhysicallyPressed
        keyboardView.altPreviewActive = snapshot.softwareAltPreviewActive
        keyboardView.symPageActive = snapshot.symPage in listOf(1, 2, 5)
        val activeSymProjection = if (snapshot.symPage in listOf(1, 2, 5) && symMappings != null) {
            SoftwareKeyboardSymLabels.project(
                page = snapshot.symPage,
                rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(layoutName, softwareKeyboardLayoutStyle()),
                symMappings = symMappings,
                layoutName = layoutName
            )
        } else {
            SoftwareKeyboardSymLabels.Projection(emptyMap(), emptyMap())
        }
        keyboardView.symPageLabels = activeSymProjection.contentByKeyCode
        keyboardView.symPageTextLabels = activeSymProjection.contentByBaseText
        keyboardView.symPreviewLabels = snapshot.softwareSymPreviewLabels
        keyboardView.symPreviewTextLabels = snapshot.softwareSymPreviewTextLabels
        keyboardView.ctrlPreviewLabels = snapshot.softwareCtrlPreviewLabels
        keyboardView.ctrlPreviewIconRes = snapshot.softwareCtrlPreviewIconRes
        keyboardView.altPreviewLabels = snapshot.softwareAltPreviewLabels
        val symKeySpec = nextSoftwareSymKeySpec(snapshot.symPage)
        keyboardView.symbolsLabel = symKeySpec.label
        keyboardView.symbolsIconRes = symKeySpec.iconRes
        keyboardView.spacebarLabel = buildSoftwareKeyboardSpacebarLabel(snapshot)
        keyboardView.longPressTimeoutMs = SettingsManager.getLongPressThreshold(context)
        keyboardView.longPressAlternatesProvider = { output ->
            resolveSoftwareKeyboardLongPressAlternates(output, snapshot)
        }
        keyboardView.longPressHintProvider = { output ->
            resolveSoftwareKeyboardAltLongPressHint(output, snapshot)
        }
        keyboardView.longPressLayerAlternatesProvider = { output ->
            resolveSoftwareKeyboardLongPressLayerAlternates(output, snapshot)
        }
        keyboardView.longPressLayerPopupBelowKey =
            SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context)
        keyboardView.themeOverride = softwareTheme().toAospThemeOverride()
    }

    private fun softwareKeyboardLayoutStyle(): AospKeyboardView.SoftwareLayoutStyle =
        when (SettingsManager.getSoftwareKeyboardLayoutStyle(context)) {
            SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT -> AospKeyboardView.SoftwareLayoutStyle.COMPACT
            SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO -> AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI -> AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO -> AospKeyboardView.SoftwareLayoutStyle.FULL_ISO
        }

    private fun prepareSoftwareKeyboardForSymbolTransition() {
        val activeColors = softwareTheme()
        emojiKeyboardContainer?.apply {
            setBackgroundColor(activeColors.background)
        }
        // Also covers the stacked layout where the keyboard is not the first container child.
        (softwareKeyboardView ?: emojiKeyboardContainer?.getChildAt(0) as? AospKeyboardView)
            ?.visibility = View.INVISIBLE
    }

    private data class SoftwareSymKeySpec(
        val label: String,
        val iconRes: Int? = null
    )

    private fun nextSoftwareSymKeySpec(currentPage: Int): SoftwareSymKeySpec {
        val pageValues = SettingsManager.getSymPagesConfig(context).enabledOrderedPages().mapNotNull { page ->
            when (page) {
                it.palsoftware.pastiera.sym.SymPagesConfig.PAGE_EMOJI -> 1
                it.palsoftware.pastiera.sym.SymPagesConfig.PAGE_SYMBOLS -> 2
                it.palsoftware.pastiera.sym.SymPagesConfig.PAGE_CLIPBOARD -> 3
                it.palsoftware.pastiera.sym.SymPagesConfig.PAGE_EMOJI_PICKER -> 4
                it.palsoftware.pastiera.sym.SymPagesConfig.PAGE_DEVICE -> 5
                else -> null
            }
        }
        if (pageValues.isEmpty()) {
            return SoftwareSymKeySpec("SYM")
        }
        val nextPage = if (currentPage == 0) {
            pageValues.firstOrNull()
        } else {
            val currentIndex = pageValues.indexOf(currentPage)
            when {
                currentIndex < 0 -> pageValues.firstOrNull()
                currentIndex == pageValues.lastIndex -> 0
                else -> pageValues[currentIndex + 1]
            }
        }
        return when (nextPage) {
            1 -> SoftwareSymKeySpec("", R.drawable.ic_emoji_emotions_24)
            2 -> SoftwareSymKeySpec("", R.drawable.ic_emoji_symbols_24)
            3 -> SoftwareSymKeySpec("", R.drawable.ic_content_paste_24)
            4 -> SoftwareSymKeySpec("", R.drawable.ic_emoji_emotions_24)
            else -> SoftwareSymKeySpec("ABC")
        }
    }

    private fun resolveSoftwareKeyboardLongPressAlternates(output: String, snapshot: StatusSnapshot): List<String> {
        if (output.isEmpty()) return emptyList()
        val baseChar = output.first()
        val keyCode = SoftwareKeyboardSymLabels.keyCodeForChar(
            baseChar,
            resolveSoftwareKeyboardLayoutName(snapshot)
        ) ?: return emptyList()
        return when (SettingsManager.getLongPressModifier(context)) {
            "alt" -> AltModifierMappingResolver.resolve(context.assets, context)[keyCode]?.let(::listOf).orEmpty()
            "shift" -> listOf(output.uppercase()).filter { it != output }
            "sym", "sym_symbols", "sym_emoji" -> {
                val map = softwareKeyboardLongPressSymMappings(SettingsManager.resolveLongPressSymPage(context))
                map[keyCode]?.let(::listOf).orEmpty()
            }
            "variations" -> {
                val variations = VariationRepository.loadVariations(
                    assets = context.assets,
                    context = context,
                    activeLayoutName = resolveSoftwareKeyboardLayoutName(snapshot)
                )
                variations[baseChar] ?: variations[baseChar.lowercaseChar()] ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun resolveSoftwareKeyboardAltLongPressHint(output: String, snapshot: StatusSnapshot): String? {
        if (output.isEmpty()) return null
        val keyCode = SoftwareKeyboardSymLabels.keyCodeForChar(
            output.first(),
            resolveSoftwareKeyboardLayoutName(snapshot)
        ) ?: return null
        return AltModifierMappingResolver.resolve(context.assets, context)[keyCode]
    }

    private fun resolveSoftwareKeyboardLongPressLayerAlternates(
        output: String,
        snapshot: StatusSnapshot
    ): List<AospKeyboardView.LongPressLayerAlternative> {
        if (!SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context) || output.isEmpty()) {
            return emptyList()
        }
        val keyCode = SoftwareKeyboardSymLabels.keyCodeForChar(
            output.first(),
            resolveSoftwareKeyboardLayoutName(snapshot)
        ) ?: return emptyList()
        val alternatives = mutableListOf<AospKeyboardView.LongPressLayerAlternative>()
        AltModifierMappingResolver.resolve(context.assets, context)[keyCode]?.takeIf { it.isNotBlank() }?.let { alt ->
            alternatives += AospKeyboardView.LongPressLayerAlternative(label = alt, output = alt)
        }
        SettingsManager.getSymPagesConfig(context)
            .normalizedOrder()
            .filter { it == SymPagesConfig.PAGE_EMOJI || it == SymPagesConfig.PAGE_SYMBOLS }
            .forEach { page ->
                val mapping = when (page) {
                    SymPagesConfig.PAGE_EMOJI -> softwareKeyboardLongPressSymMappings(1)
                    SymPagesConfig.PAGE_SYMBOLS -> softwareKeyboardLongPressSymMappings(2)
                    else -> emptyMap()
                }
                mapping[keyCode]?.takeIf { it.isNotBlank() }?.let { value ->
                    alternatives += AospKeyboardView.LongPressLayerAlternative(label = value, output = value)
                }
            }
        return alternatives.distinctBy { it.output }
    }

    private fun softwareKeyboardLongPressSymMappings(page: Int): Map<Int, String> {
        return when (page) {
            1 -> SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
                ?: KeyMappingLoader.loadSymKeyMappings(context.assets)
            2 -> SettingsManager.getSymMappingsPage2(context).takeIf { it.isNotEmpty() }
                ?: KeyMappingLoader.loadSymKeyMappingsPage2(context.assets)
            else -> emptyMap()
        }
    }

    private fun updateSoftwareSymbolKeyboard(
        symMappings: Map<Int, String>,
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection? = null
    ) {
        val page = snapshot.symPage
        val container = emojiKeyboardContainer ?: return
        container.setPadding(0, 0, 0, emojiKeyboardBottomPaddingPx)
        val inputConnectionChanged = lastInputConnectionUsed != inputConnection
        val layoutName = resolveSoftwareKeyboardLayoutName(snapshot)
        val layoutStyle = softwareKeyboardLayoutStyle()
        if (
            lastSymPageRendered == page &&
            lastSoftwareKeyboardSymPageRendered == page &&
            lastSoftwareKeyboardSymLayoutRendered == layoutName &&
            lastSoftwareKeyboardSymStyleRendered == layoutStyle &&
            lastSymMappingsRendered == symMappings &&
            !inputConnectionChanged
        ) {
            return
        }

        container.removeAllViews()
        emojiKeyButtons.clear()

        val rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(layoutName, layoutStyle)
        val softwareSymContentByChar = SoftwareKeyboardSymLabels.buildContentByChar(
            page = page,
            rows = rows,
            symMappings = symMappings,
            layoutName = layoutName
        )
        val keySpacing = dpToPx(2f)
        val keyHeight = ((lastSoftwareKeyboardHeight.takeIf { it > 0 } ?: dpToPx(200f)) - emojiKeyboardBottomPaddingPx) / 4
        val screenWidth = context.resources.displayMetrics.widthPixels
        val columns = maxOf(10, rows.maxOf { it.length }, rows.getOrNull(2)?.length?.plus(2) ?: 0)
        val fixedKeyWidth = ((screenWidth - keySpacing * (columns - 1)) / columns).coerceAtLeast(1)

        rows.forEachIndexed { rowIndex, row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    keyHeight
                )
            }
            if (rowIndex == 2) {
                rowLayout.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.shift_24) {
                    // Keep page stable; shifted symbol layers can be added later without touching PKB SYM.
                }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
            }
            row.forEachIndexed { index, labelChar ->
                val label = labelChar.toString().uppercase()
                val content = softwareSymContentByChar[labelChar] ?: softwareSymbolFallback(labelChar)
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                if (content.isNotEmpty() && inputConnection != null) {
                    keyButton.isClickable = true
                    keyButton.isFocusable = true
                    keyButton.setOnClickListener {
                        commitTouchSymbolAfterCloseIfNeeded(keyButton, inputConnection, content)
                    }
                }
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    if (index < row.length - 1) marginEnd = keySpacing
                })
            }
            if (rowIndex == 2) {
                rowLayout.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.backspace_24) {
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginStart = keySpacing })
            }
            container.addView(rowLayout)
        }

        val row4 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyHeight)
        }
        val symKeySpec = nextSoftwareSymKeySpec(page)
        row4.addView(createSoftwareSymbolControl(symKeySpec.label, keyHeight, fixedKeyWidth, iconRes = symKeySpec.iconRes) {
            onSoftwareKeyboardSymToggleRequested?.invoke()
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.keyboard_control_key_24) {
            sendSoftwareCtrlTap(inputConnection)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl(",", keyHeight, fixedKeyWidth) {
            inputConnection?.commitText(",", 1)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolSpaceControl(buildSoftwareKeyboardSpacebarLabel(snapshot), keyHeight, inputConnection), LinearLayout.LayoutParams(fixedKeyWidth * 4, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl(".", keyHeight, fixedKeyWidth) {
            inputConnection?.commitText(".", 1)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.keyboard_control_key_24) {
            sendSoftwareCtrlTap(inputConnection)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.keyboard_return_24) {
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
        container.addView(row4)

        lastSymPageRendered = page
        lastSoftwareKeyboardSymPageRendered = page
        lastSoftwareKeyboardSymLayoutRendered = layoutName
        lastSoftwareKeyboardSymStyleRendered = layoutStyle
        lastSymMappingsRendered = HashMap(symMappings)
        lastInputConnectionUsed = inputConnection
    }

    private fun softwareSymbolFallback(char: Char): String =
        when {
            char.isLetterOrDigit() -> ""
            else -> char.toString()
        }

    private fun createSoftwareSymbolControl(
        label: String,
        height: Int,
        width: Int,
        iconRes: Int? = null,
        onClick: () -> Unit
    ): TextView {
        val theme = activeThemeColors(isFullSoftwareKeyboardMode = true)
        return TextView(context).apply {
            text = label
            setTextColor(theme.textAndIcons)
            textSize = if (label.length <= 1) 22f else 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            iconRes?.let { resId ->
                val icon = ContextCompat.getDrawable(context, resId)?.mutate()
                icon?.setTint(theme.textAndIcons)
                val iconSize = (height * 0.46f).toInt().coerceAtLeast(dpToPx(18f))
                icon?.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawables(null, icon, null, null)
            }
            background = GradientDrawable().apply {
                setColor(theme.statusBarButton)
                setStroke(dpToPx(1f), theme.divider)
                cornerRadius = dpToPx(6f).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }

    private fun createSoftwareSymbolSpaceControl(
        label: String,
        height: Int,
        inputConnection: android.view.inputmethod.InputConnection?
    ): TextView {
        val view = createSoftwareSymbolControl(label, height, 0, onClick = {
            inputConnection?.commitText(" ", 1)
        })
        var downX = 0f
        var lastX = 0f
        var moved = false
        val step = dpToPx(18f).toFloat()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            onLanguageSwitchRequested?.invoke()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    lastX = event.x
                    moved = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, SettingsManager.getLongPressThreshold(context))
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val delta = event.x - lastX
                    if (kotlin.math.abs(delta) >= step) {
                        handler.removeCallbacks(longPressRunnable)
                        moved = true
                        val steps = (delta / step).toInt()
                        repeat(kotlin.math.abs(steps).coerceAtMost(4)) {
                            val connection = inputConnection ?: return@repeat
                            val didMove = if (steps > 0) {
                                TextSelectionHelper.moveCursorRight(connection)
                            } else {
                                TextSelectionHelper.moveCursorLeft(connection)
                            }
                            if (didMove) {
                                onCursorMovedListener?.invoke()
                            }
                        }
                        lastX += steps * step
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressTriggered && kotlin.math.abs(event.x - downX) < step) {
                        inputConnection?.commitText(" ", 1)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
        return view
    }

    private fun sendSoftwareCtrlTap(inputConnection: android.view.inputmethod.InputConnection?) {
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT))
    }

    private fun buildSoftwareKeyboardSpacebarLabel(snapshot: StatusSnapshot): String {
        val language = try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.currentInputMethodSubtype?.languageCode()
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: "??"
        val layout = when (resolveSoftwareKeyboardLayoutName(snapshot)) {
            "german_multitap_qwertz" -> "QWERTZ DE"
            "qwertz" -> "QWERTZ"
            "azerty" -> "AZERTY"
            else -> "QWERTY"
        }
        return "$language · $layout"
    }

    private fun resolveSoftwareKeyboardLayoutName(snapshot: StatusSnapshot): String {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val subtype = imm?.currentInputMethodSubtype
            AdditionalSubtypeUtils.resolveActiveLayout(context.assets, context, subtype)
        } catch (e: Exception) {
            snapshot.activeKeyboardLayoutName
        }
    }

    /**
     * A transparent placeholder that keeps the rows aligned.
     */
    private fun createPlaceholderButton(height: Int): View {
        return FrameLayout(context).apply {
            background = null // Trasparente
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            isClickable = false
            isFocusable = false
        }
    }
    
    /**
     * A placeholder with an emoji icon that opens the emoji picker (SYM page 4).
     */
    private fun createPlaceholderWithEmojiPickerButton(height: Int, page: Int): View {
        val theme = activeThemeColors()
        val placeholder = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        placeholder.background = null
        
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            28f,
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(if (page == 1) R.drawable.ic_emoji_symbols_24 else R.drawable.ic_sentiment_satisfied_24)
            setColorFilter(theme.textAndIcons)
            contentDescription = context.getString(R.string.status_bar_button_emoji_description)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = iconSize
            maxHeight = iconSize
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            // The whole corner key takes the tap, not just the icon
            isClickable = false
            isFocusable = false
        }

        placeholder.isClickable = true
        placeholder.contentDescription = button.contentDescription
        placeholder.setOnClickListener {
            if (page == 1) {
                onSymbolsPageRequested?.invoke()
            } else {
                onEmojiPageRequested?.invoke()
            }
        }

        placeholder.addView(button)
        return placeholder
    }
    
    /**
     * A placeholder with a pencil icon that opens SYM customisation.
     */
    private fun createPlaceholderWithPencilButton(height: Int, page: Int): View {
        val theme = activeThemeColors()
        val placeholder = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Background trasparente
        placeholder.background = null
        
        // Larger icon
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            28f, // Larger, to stand out
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(theme.textAndIcons)
            contentDescription = context.getString(R.string.sym_customization_button)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = iconSize
            maxHeight = iconSize
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        bindSymPencil(button, page)
        
        placeholder.addView(button)
        return placeholder
    }

    private fun createSymEditorButton(height: Int, width: Int, page: Int): View {
        val theme = activeThemeColors()
        val button = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.sym_customization_button)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(theme.textAndIcons)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        button.addView(icon)
        bindSymPencil(button, page)
        return button
    }

    /**
     * The pencil on a SYM page opens that layer's own mapping. On the symbol panels (the symbols
     * page and the Device SYM page) holding it opens the variations mapping instead.
     */
    private fun bindSymPencil(button: View, page: Int) {
        // A layer shown as pages has nothing of yours to edit: the symbols' pencil keeps the
        // variations, the emoji layer's goes
        if (layerShowsPages(page)) {
            if (page == 2) {
                button.contentDescription = context.getString(R.string.sym_symbols_pencil_description)
                button.setOnClickListener { openVariationsMapping() }
            } else button.visibility = View.INVISIBLE
            return
        }
        button.setOnClickListener { openSymCustomization(page = page, keyCode = null, openPicker = false) }
        if (page != 2 && page != 5) return
        button.contentDescription = context.getString(R.string.sym_symbols_pencil_description)
        button.setOnLongClickListener {
            openVariationsMapping()
            true
        }
    }

    private fun openVariationsMapping() {
        val currentSymPage = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .getInt("current_sym_page", 0)
        if (currentSymPage > 0) SettingsManager.setPendingRestoreSymPage(context, currentSymPage)
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SettingsActivity.EXTRA_DESTINATION, SettingsActivity.DESTINATION_CUSTOMIZATION)
            putExtra(SettingsActivity.EXTRA_CUSTOMIZATION_DESTINATION, SettingsActivity.CUSTOMIZATION_DESTINATION_VARIATIONS)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't open the variations mapping", e)
        }
    }

    /** The emoji layer (1) or the symbols page (2) shows pages to find things in, not your mapping. */
    private fun layerShowsPages(page: Int): Boolean =
        (page == 1 && SettingsManager.getEmojiLayerPages(context)) || (page == 2 && SettingsManager.getSymbolsPages(context))

    private fun openSymCustomization(page: Int, keyCode: Int?, openPicker: Boolean) {
        // Pages have no mapping of yours to edit (holding a key there does nothing)
        if (layerShowsPages(page)) return
        val prefs = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        val currentSymPage = prefs.getInt("current_sym_page", 0)
        if (currentSymPage > 0) {
            SettingsManager.setPendingRestoreSymPage(context, currentSymPage)
        }

        val intent = if (page == 5) {
            Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    SettingsActivity.EXTRA_DESTINATION,
                    SettingsActivity.DESTINATION_DEVICE_SYM_LAYER_EDITOR
                )
            }
        } else Intent(context, SymCustomizationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SymCustomizationActivity.EXTRA_INITIAL_PAGE, page)
            keyCode?.let { putExtra(SymCustomizationActivity.EXTRA_INITIAL_KEY_CODE, it) }
            putExtra(SymCustomizationActivity.EXTRA_OPEN_PICKER, openPicker)
            putExtra(SymCustomizationActivity.EXTRA_RETURN_AFTER_PICKER, openPicker && keyCode != null)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening SYM customisation", e)
        }
    }
    
    /**
     * A key of the emoji/character grid.
     * @param label The key's letter
     * @param content The emoji or character to show
     * @param height The key's height
     * @param page The active page (1 = emoji, 2 = characters)
     */
    /**
     * Titan 2 Elite (rounded corners): space above the emoji/SYM/picker screens, below the bar, and
     * below their bottom keys, giving the LEDs a band of their own. Same as the gap between rows.
     */
    private fun expandedScreenGapPx(): Int =
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) dpToPx(4f) else 0

    private fun createEmojiKeyButton(label: String, content: String, height: Int, page: Int): View {
        val theme = activeThemeColors()
        val keyLayout = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0) // No padding, so the emoji fills the key
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            contentDescription = buildSymKeyContentDescription(label, content)
        }
        
        // Key background with slightly rounded corners
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f, // Angoli leggermente arrotondati
            context.resources.displayMetrics
        )
        val drawable = GradientDrawable().apply {
            setColor(theme.normalKey)
            setCornerRadius(cornerRadius)
            setStroke(dpToPx(1f), theme.divider)
        }
        keyLayout.background = drawable
        
        // The emoji or character fills the key, centred
        // Calcola textSize in base all'altezza disponibile (convertendo da pixel a sp)
        val heightInDp = height / context.resources.displayMetrics.density
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val contentTextSize = if (page == 2 || roundedCorners) {
            // Smaller size for Unicode characters
            (heightInDp * 0.5f)
        } else {
            // Normal size for emoji
            (heightInDp * 0.75f)
        }
        
        // Emoji layer's Recents key: a text symbol, drawn larger and bold so it reads like a key
        val recentsSymbol = (page == 1 || page == 2) && (
            content == it.palsoftware.pastiera.core.SymLayoutController.RECENTS_KEY_LABEL ||
                content == it.palsoftware.pastiera.core.SymLayoutController.RECENTS_BACK_LABEL
            )
        // Emoji layer's GIF key: a short bold word, smaller than an emoji
        val gifLabel = page == 1 && content == it.palsoftware.pastiera.core.SymLayoutController.GIF_KEY_LABEL
        val contentText = TextView(context).apply {
            text = content
            textSize = when {
                recentsSymbol -> contentTextSize * 1.35f
                gifLabel -> contentTextSize * 0.55f
                else -> contentTextSize
            } // textSize è in sp
            gravity = Gravity.CENTER
            // Kaomoji and other long labels: one line, shrunk to fit the key
            if (page == 2 && content.codePointCount(0, content.length) > 2 && !recentsSymbol) {
                maxLines = 1
                androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 6, contentTextSize.toInt().coerceAtLeast(7), 1, android.util.TypedValue.COMPLEX_UNIT_SP
                )
            }
            // Emoji layer: glyphs centre on their own box, not on the font's extra padding
            if (page == 1) includeFontPadding = false
            if (recentsSymbol || gifLabel) setTypeface(null, android.graphics.Typeface.BOLD)
            if (roundedCorners) setTextColor(theme.textAndIcons)
            // Per pagina 2 (caratteri), rendi bianco e in grassetto
            if (page == 2) {
                setTextColor(theme.textAndIcons)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            // Fill the available width and height
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // Label (lettera) - posizionato in basso a destra, davanti all'emoji
        val labelPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f, // Pochissimo margine
            context.resources.displayMetrics
        ).toInt()
        
        val labelText = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(theme.textAndIcons)
            gravity = Gravity.END or Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = labelPadding
                bottomMargin = labelPadding
            }
        }
        
        if (page == 2 && label.isNotEmpty() && content.codePointCount(0, content.length) > 2 &&
            it.palsoftware.pastiera.core.SymLayoutController.kaomojiShown
        ) {
            // Kaomoji: the letter centred under it, in the same key, the kaomoji above it
            contentText.typeface = android.graphics.Typeface.DEFAULT
            labelText.layoutParams = (labelText.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                rightMargin = 0
                bottomMargin = labelPadding
            }
            labelText.gravity = Gravity.CENTER
            val letterHeight = (labelText.paint.fontMetrics.let { it.descent - it.ascent }).toInt() + labelPadding
            contentText.setPadding(dpToPx(2f), labelPadding, dpToPx(2f), letterHeight)
        } else if (page == 2 && label.isNotEmpty() && content.codePointCount(0, content.length) > 2) {
            // A long label on the symbols page (the kaomoji key): shrunk to fit beside its letter
            val letterSpace = labelText.paint.measureText(label).toInt() + labelPadding + dpToPx(2f)
            contentText.setPadding(dpToPx(2f), 0, letterSpace, 0)
        } else if (page == 1 && label.isNotEmpty()) {
            // Emoji layer: centre the emoji in the space left of the letter, with a small gap,
            // so wide emoji don't run into it
            val letterSpace = labelText.paint.measureText(label).toInt() + labelPadding + dpToPx(2f)
            contentText.setPadding(0, 0, letterSpace, 0)
        }

        val labels = it.palsoftware.pastiera.core.SymLayoutController
        if (page == 2 && content == labels.SYMBOLS_KEY_LABEL && labels.kaomojiShown) {
            // Back to the symbols: the same icon as the emoji page's corner button
            val letterSpace = labelText.paint.measureText(label).toInt() + labelPadding + dpToPx(2f)
            val iconSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, contentTextSize * 1.1f, context.resources.displayMetrics
            ).toInt().coerceAtMost(height - 2 * labelPadding)
            val icon = ImageView(context).apply {
                setImageResource(R.drawable.ic_emoji_symbols_24)
                setColorFilter(theme.textAndIcons)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER
                    rightMargin = letterSpace / 2
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            keyLayout.addView(icon)
            keyLayout.addView(labelText)
            return keyLayout
        }

        // Content first (behind), then the text (in front)
        keyLayout.addView(contentText)
        keyLayout.addView(labelText)
        
        return keyLayout
    }

    private fun buildSymKeyContentDescription(label: String, content: String): String {
        if (content.isBlank()) {
            return label
        }
        return context.getString(R.string.sym_key_content_description, label, content)
    }

    /**
     * The layer pages' close button: a key of its own in the bottom-right slot, the same size
     * and shape as the bottom-left one mirrored (it follows the right display corner), in the
     * close button's colour.
     */
    private fun createGridCloseButton(): View {
        val theme = activeThemeColors()
        return FrameLayout(context).apply {
            background = createCloseButtonBackground(theme)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.close)
            setTag(R.id.tag_outer_edge_button, StatusBarButtonPosition.RIGHT)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_close_24)
                setColorFilter(theme.textAndIcons)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
            setOnClickListener { onSymCloseRequested?.invoke() }
        }
    }

    private fun createSurfaceCloseButton(): View {
        val buttonSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            36f,
            context.resources.displayMetrics
        ).toInt()
        val buttonHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            context.resources.displayMetrics
        ).toInt()
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = createCloseButtonBackground()
            contentDescription = context.getString(R.string.close)
            setPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                buttonSize,
                buttonHeight,
                Gravity.BOTTOM or Gravity.END
            )
            setOnClickListener {
                onSymCloseRequested?.invoke()
            }
        }
    }

    /** Emoji layer: emoji search; symbols pages: symbol search. Either opens ready for typing. */
    private fun createEmojiLayerSearchButton(height: Int, width: Int, page: Int): View {
        val theme = activeThemeColors()
        val button = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.emoji_layer_search_button)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search_24)
            setColorFilter(theme.textAndIcons)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        button.addView(icon)
        if (page != 1) button.contentDescription = context.getString(R.string.symbol_search_placeholder)
        button.setOnClickListener {
            if (page == 1) onEmojiLayerSearchRequested?.invoke() else onSymbolSearchRequested?.invoke()
        }
        return button
    }

    private fun createKeyboardSelectionButton(height: Int, width: Int): View {
        val theme = activeThemeColors()
        val button = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.change_keyboard_button)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_globe_24)
            setColorFilter(theme.textAndIcons)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        button.addView(icon)
        button.setOnClickListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
        return button
    }

    private fun createCloseButtonBackground(theme: KeyboardThemeColors? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.statusBarButton ?: Color.argb(95, 220, 38, 38))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                6f,
                context.resources.displayMetrics
            )
        }
    }

    private fun applySurfaceCloseButtonTheme(theme: KeyboardThemeColors) {
        (symSurfaceCloseButton as? ImageView)?.apply {
            setColorFilter(theme.textAndIcons)
            background = createCloseButtonBackground(theme)
            val rounded = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
            // The clipboard's close button: the bottom-right corner key, as on the other pages
            setTag(R.id.tag_outer_edge_button, if (rounded) StatusBarButtonPosition.RIGHT else null)
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val screenWidth = statusBarLayout?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val rightInset = if (rounded) dpToPx(3.1f) else 0
                val targetWidth = if (rounded) panelSideButtonWidthPx() - rightInset else dpToPx(36f)
                val targetHeight = if (rounded) hardwareSymKeyHeightPx(theme) else dpToPx(32f)
                // The bottom margin is set with the surface layout (updateSurfaceCloseBottomMargin)
                if (params.width != targetWidth || params.height != targetHeight || params.rightMargin != rightInset) {
                    params.width = targetWidth
                    params.height = targetHeight
                    params.rightMargin = rightInset
                    layoutParams = params
                }
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(if (rounded) 12f else 4f), dpToPx(if (rounded) 12f else 4f))
            if (rounded) {
                background = android.graphics.drawable.InsetDrawable(background, 0, 0, dpToPx(3f), dpToPx(3f))
                drawable?.let { icon ->
                    val clipboardSize = if (pastierinaModeActive) {
                        (dpToPx(36f * theme.suggestionsHeightScale.coerceIn(0.65f, 1.6f)) - dpToPx(4f)) * 0.64f
                    } else {
                        minOf(dpToPx(24f).toFloat(), dpToPx(55f * theme.variationsHeightScale.coerceIn(0.65f, 1.6f)) * 0.48f)
                    }
                    val scale = clipboardSize / icon.intrinsicHeight.coerceAtLeast(1)
                    scaleType = ImageView.ScaleType.MATRIX
                    imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (layoutParams.width - icon.intrinsicWidth * scale) / 2f - paddingLeft - dpToPx(8f),
                            (layoutParams.height - icon.intrinsicHeight * scale) / 2f - paddingTop - dpToPx(2f)
                        )
                    }
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val displayRadius = (statusBarLayout as? ImeChromeLayout)?.bottomCornerRadiiPx?.second
                            ?: dpToPx(TITAN_2_ELITE_CORNER_FALLBACK_RADIUS_DP)
                        val radius = (displayRadius - reservedExpandedLedHeight()).coerceAtLeast(0).toFloat()
                        val horizontalRadius = (displayRadius - dpToPx(3.1f)).coerceAtLeast(0).toFloat()
                        val path = Path().apply {
                            addRoundRect(
                                RectF(0f, -2f * radius, view.width.toFloat(), view.height.toFloat()),
                                floatArrayOf(0f, 0f, 0f, 0f, horizontalRadius, radius, 0f, 0f),
                                Path.Direction.CW
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) outline.setPath(path)
                        else {
                            @Suppress("DEPRECATION")
                            // Android 10 takes only a convex outline: the plain rectangle when the shape isn't one
                            if (path.isConvex) outline.setConvexPath(path) else outline.setRect(0, 0, view.width, view.height)
                        }
                    }
                }
                // Straight corner buttons: square, reaching the corner; rounded only otherwise
                clipToOutline = !SettingsManager.getTitan2EliteStraightOuterButtons(context)
                invalidateOutline()
            } else {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
        }
    }

    private fun closeSymAfterTouchKeyIfNeeded(): Boolean {
        val shouldClose = if (lastSnapshotSymPage == 1) {
            // Emoji layer: the emoji key's own auto-close when the emoji key opened it
            SettingsManager.emojiScreenClosesAfterInput(
                context, isPicker = false, openedByEmojiKey = lastEmojiScreenFromEmojiKey, byTouch = true
            )
        } else if (lastSnapshotSymPage == 2) {
            SettingsManager.getSymbolsCloseOnTap(context)
        } else {
            SettingsManager.getSymAutoCloseOnTouch(context)
        }
        if (shouldClose) {
            onSymCloseRequested?.invoke()
        }
        return shouldClose
    }

    private fun commitTouchSymbolAfterCloseIfNeeded(
        anchor: View,
        inputConnection: android.view.inputmethod.InputConnection,
        content: String
    ) {
        val commit = {
            if (onSoftwareKeyboardBoundaryTextInput?.invoke(content, inputConnection) != true) {
                inputConnection.commitText(content, 1)
            }
        }
        if (closeSymAfterTouchKeyIfNeeded()) {
            anchor.post(commit)
        } else {
            commit()
        }
    }

    /**
     * A layer key's job: the search, GIF and Recents keys stand apart and do their thing; the
     * others type what they hold. The same on the Titan 2 layout and the centred one.
     */
    private fun bindLayerKey(
        keyButton: View,
        keyCode: Int,
        content: String,
        page: Int,
        inputConnection: android.view.inputmethod.InputConnection?
    ) {
        // Recents: the emoji layer's and the symbols page's (the same key)
        val recentsKey = (page == 1 || page == 2) && keyCode == SettingsManager.getEmojiLayerRecentsKey(context) &&
            (content == it.palsoftware.pastiera.core.SymLayoutController.RECENTS_KEY_LABEL ||
                content == it.palsoftware.pastiera.core.SymLayoutController.RECENTS_BACK_LABEL)
        // The GIF key only while it shows GIF (with recent emoji shown it holds one of them)
        val gifKey = page == 1 && content == it.palsoftware.pastiera.core.SymLayoutController.GIF_KEY_LABEL
        // The search key (on A on a paged layer's recents page)
        val searchKey = (page == 1 || page == 2 || page == 5) &&
            content == it.palsoftware.pastiera.core.SymLayoutController.SEARCH_KEY_LABEL
        val labels = it.palsoftware.pastiera.core.SymLayoutController
        val kaomojiKey = page == 2 && (content == labels.KAOMOJI_KEY_LABEL || (keyCode == labels.PAGE_EXTRA_KEY && content == labels.SYMBOLS_KEY_LABEL))
        // The page arrows: ‹ on Q, › on P (the kaomoji pages, and the layers shown as pages)
        val kaomojiBackKey = (page == 1 || page == 2) && keyCode == labels.PAGE_PREVIOUS_KEY && content == labels.KAOMOJI_PREVIOUS_LABEL
        val nextPageKey = (page == 1 || page == 2) && keyCode == labels.PAGE_NEXT_KEY && content == labels.KAOMOJI_NEXT_LABEL
        if (searchKey || gifKey || recentsKey || kaomojiKey || kaomojiBackKey || nextPageKey) {
            // Search, GIF and Recents stand apart from the mapped keys: the key colour with a
            // touch of the accent
            (keyButton.background as? GradientDrawable)?.mutate()?.let { background ->
                val theme = activeThemeColors()
                (background as GradientDrawable).setColor(
                    androidx.core.graphics.ColorUtils.blendARGB(theme.normalKey, theme.accent, 0.22f)
                )
            }
        }
        // A recents page's starters (not yet used, from the first pages): the key colour with a
        // touch of the accent's neighbour on the colour wheel, at the accent's brightness
        if ((page == 1 || page == 2) && content.isNotEmpty() && keyCode in labels.starterKeys &&
            !(searchKey || gifKey || recentsKey || kaomojiKey || kaomojiBackKey || nextPageKey)
        ) {
            (keyButton.background as? GradientDrawable)?.mutate()?.let { background ->
                val theme = activeThemeColors()
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(theme.accent, hsv)
                hsv[0] = (hsv[0] + 40f) % 360f
                val neighbour = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(theme.accent), hsv)
                (background as GradientDrawable).setColor(
                    androidx.core.graphics.ColorUtils.blendARGB(theme.normalKey, neighbour, 0.22f)
                )
            }
        }
        if (searchKey) {
            // The search key: this screen's search
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.contentDescription = context.getString(R.string.search_key_title)
            keyButton.setOnClickListener {
                when {
                    page == 1 -> onEmojiLayerSearchRequested?.invoke()
                    page == 2 && labels.kaomojiShown -> onKaomojiSearchRequested?.invoke()
                    else -> onSymbolSearchRequested?.invoke()
                }
            }
        } else if (gifKey) {
            // Emoji layer's GIF key: GIF search
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.contentDescription = context.getString(R.string.gif_tab_description)
            keyButton.setOnClickListener { onEmojiLayerGifRequested?.invoke() }
        } else if (kaomojiBackKey) {
            // The kaomoji pages' Q: the page before (the first goes round to the last)
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.contentDescription = context.getString(R.string.kaomoji_key_title)
            keyButton.setOnClickListener { onKaomojiBackTapped?.invoke() }
        } else if (nextPageKey) {
            // P: the next page (the last goes round to the first)
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.contentDescription = context.getString(R.string.layer_next_page)
            keyButton.setOnClickListener { onLayerNextTapped?.invoke() }
        } else if (kaomojiKey) {
            // Symbols page's kaomoji key: the kaomoji, their next page, or back
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.contentDescription = context.getString(R.string.kaomoji_key_title)
            keyButton.setOnClickListener { onKaomojiKeyTapped?.invoke() }
        } else if (recentsKey) {
            // Emoji layer's Recents key: recent emoji on the keys, or back
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.contentDescription = context.getString(R.string.emoji_layer_recents_key_title)
            keyButton.setOnClickListener { onEmojiLayerRecentsToggled?.invoke() }
        } else if (content.isNotEmpty() && inputConnection != null && page == 2 &&
            it.palsoftware.pastiera.core.SymLayoutController.kaomojiShown
        ) {
            // A kaomoji: typed, and the page closes if that's on (Settings: kaomoji tapped)
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.setOnClickListener {
                val commit = {
                    inputConnection.commitText(content, 1)
                    Kaomoji.addRecent(context, content)
                }
                if (SettingsManager.getKaomojiCloseOnTap(context)) {
                    onSymCloseRequested?.invoke()
                    keyButton.post { commit() }
                } else commit()
            }
        } else if (content.isNotEmpty() && inputConnection != null) {
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.setOnClickListener {
                commitTouchSymbolAfterCloseIfNeeded(keyButton, inputConnection, content)
                if (page == 2 || page == 5) SymbolSearch.addRecent(context, content)
                if (page == 1) {
                    EmojiLayerRecents.markUsed(context, content)
                    // On the emoji layer's pages, emoji used anywhere go on its recents page
                    if (SettingsManager.getEmojiLayerPages(context)) {
                        RecentEmojiManager.addRecentEmoji(context, content)
                        onEmojiLayerTyped?.invoke()
                    }
                }
            }
            // Held on the emoji layer's pages: its skin tones, one per key
            if (page == 1 && SettingsManager.getEmojiLayerPages(context)) {
                keyButton.isLongClickable = true
                keyButton.setOnLongClickListener {
                    onEmojiVariantsRequested?.invoke(content)
                    true
                }
            }
        }
    }

    private fun addKeyToRow(
        rowLayout: LinearLayout,
        keyCode: Int,
        symMappings: Map<Int, String>,
        width: Int,
        height: Int,
        spacing: Int,
        page: Int,
        inputConnection: android.view.inputmethod.InputConnection?,
        isLast: Boolean
    ) {
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        val label = keyLabels[keyCode] ?: ""
        val content = symMappings[keyCode] ?: ""
        val keyButton = createEmojiKeyButton(label, content, height, page)
        keyButton.isLongClickable = true
        keyButton.setOnLongClickListener {
            openSymCustomization(page = page, keyCode = keyCode, openPicker = true)
            true
        }
        
        bindLayerKey(keyButton, keyCode, content, page, inputConnection)

        rowLayout.addView(keyButton, LinearLayout.LayoutParams(width, height))
        if (!isLast) {
            rowLayout.addView(View(context), LinearLayout.LayoutParams(spacing, height))
        }
    }
    
    /**
     * A customisable emoji grid (for the customisation screen).
     * Returns a View that Compose can host through AndroidView.
     * 
     * @param symMappings Le mappature emoji da visualizzare
     * @param onKeyClick Called when a key is tapped (keyCode, emoji)
     */
    fun createCustomizableEmojiKeyboard(
        symMappings: Map<Int, String>,
        onKeyClick: (Int, String) -> Unit,
        page: Int = 1 // Default a pagina 1 (emoji)
    ): View {
        isTitan2Layout = SettingsManager.isTitan2LayoutEnabled(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(0, 0, 0, bottomPadding) // Bottom padding only
            // Black background so characters stay readable with a light theme
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Keyboard rows (the same as the real keyboard)
        val keyboardRows = listOf(
            listOf(android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_E, 
                   android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_Y, 
                   android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_I, android.view.KeyEvent.KEYCODE_O, 
                   android.view.KeyEvent.KEYCODE_P),
            listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D, 
                   android.view.KeyEvent.KEYCODE_F, android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_H, 
                   android.view.KeyEvent.KEYCODE_J, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_L),
            listOf(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C, 
                   android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_N, 
                   android.view.KeyEvent.KEYCODE_M)
        )
        
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Fixed key width, from the first row (10 keys)
        // A ViewTreeObserver gets the container's real width after layout
        val maxKeysInRow = 10 // The first row has 10 keys
        
        // A temporary width, updated after layout
        var fixedKeyWidth = 0
        
        container.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val containerWidth = container.width
                if (containerWidth > 0) {
                    val totalSpacing = keySpacing * (maxKeysInRow - 1)
                    fixedKeyWidth = (containerWidth - totalSpacing) / maxKeysInRow
                    
                    // Give every key the right width
                    for (i in 0 until container.childCount) {
                        val rowLayout = container.getChildAt(i) as? LinearLayout
                        rowLayout?.let { row ->
                            for (j in 0 until row.childCount) {
                                val child = row.getChildAt(j)
                                val layoutParams = child.layoutParams as? LinearLayout.LayoutParams
                                layoutParams?.let {
                                    if (it.width != keySpacing) {
                                        // Update width for keys and placeholders, but NOT for spacing views
                                        it.width = fixedKeyWidth
                                        child.layoutParams = it
                                    }
                                }
                            }
                        }
                    }
                    
                    // Remove the listener after the first layout
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        
        // Initial value from the screen width (the listener updates it)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        fixedKeyWidth = (screenWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Build each keyboard row (the same as the real keyboard)
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isTitan2Layout) Gravity.START else Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            if (isTitan2Layout) {
                // Ortholinear layout for Titan 2 (Customization Preview)
                when (rowIndex) {
                    0 -> { // Row 1: Q W E R T Y U I O P (10 keys)
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToPreviewRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, index == row.size - 1)
                        }
                    }
                    1 -> { // Row 2: A S D F G H J K L (9 keys) -> Add placeholder at the end to make it 10
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToPreviewRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, false)
                        }
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                    2 -> { // Row 3: Z X C V [Close Placeholder] [Globe Placeholder] B N M [Gap]
                        // Z X C V (4 keys)
                        for (i in 0..3) {
                            addKeyToPreviewRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, false)
                        }
                        
                        // Close Button Placeholder (no icon in customization preview)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // Globe Button Placeholder (no icon in customization preview)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // B N M (3 keys)
                        for (i in 4..6) {
                            addKeyToPreviewRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, false)
                        }

                        // Right Gap (placeholder for the physical cutout/space at the end of row 3)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                }
                container.addView(rowLayout)
                continue
            }
            
            // Third row: a transparent placeholder on the left
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val reserved = reservedLayerKeys(page)[keyCode]
                val emoji = symMappings[keyCode] ?: ""
                
                // The same createEmojiKeyButton as the real keyboard
                val keyButton = createEmojiKeyButton(label, reserved ?: emoji, keyHeight, page)
                
                // The search, GIF and Recents keys aren't mapped: greyed, showing their job
                if (reserved != null) greyOutReservedKey(keyButton) else keyButton.setOnClickListener {
                    onKeyClick(keyCode, emoji)
                }
                
                // Fixed width instead of weight (the same layout as the real keyboard)
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // Third row on the customisation screen: a transparent placeholder on the right
            // to keep the alignment (no pencil, no click listener)
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }

    /** The keys a layer keeps for itself (search, GIF, Recents), with the label each shows. */
    private fun reservedLayerKeys(page: Int): Map<Int, String> {
        val reserved = mutableMapOf<Int, String>()
        val unknown = android.view.KeyEvent.KEYCODE_UNKNOWN
        val labels = it.palsoftware.pastiera.core.SymLayoutController
        // Layers shown as pages keep Q and P for the page arrows
        if ((page == 1 && SettingsManager.getEmojiLayerPages(context)) || (page == 2 && SettingsManager.getSymbolsPages(context))) {
            reserved[labels.PAGE_PREVIOUS_KEY] = labels.KAOMOJI_PREVIOUS_LABEL
            reserved[labels.PAGE_NEXT_KEY] = labels.KAOMOJI_NEXT_LABEL
            return reserved
        }
        if (page == 1 || page == 2) {
            SettingsManager.getEmojiLayerRecentsKey(context).takeIf { it != unknown }?.let { reserved[it] = labels.RECENTS_KEY_LABEL }
            SettingsManager.getSearchKey(context).takeIf { it != unknown }?.let { reserved[it] = labels.SEARCH_KEY_LABEL }
        }
        if (page == 1) {
            SettingsManager.activeEmojiLayerGifKey(context).takeIf { it != unknown }?.let { reserved[it] = labels.GIF_KEY_LABEL }
        }
        if (page == 2) {
            labels.kaomojiKey(context).takeIf { it != unknown }?.let { reserved[it] = labels.KAOMOJI_KEY_LABEL }
        }
        return reserved
    }

    private fun greyOutReservedKey(keyButton: View) {
        keyButton.alpha = 0.38f
        keyButton.isClickable = false
        keyButton.isFocusable = false
        keyButton.isEnabled = false
    }

    private fun addKeyToPreviewRow(
        rowLayout: LinearLayout,
        keyCode: Int,
        symMappings: Map<Int, String>,
        width: Int,
        height: Int,
        spacing: Int,
        page: Int,
        onKeyClick: (Int, String) -> Unit,
        isLast: Boolean
    ) {
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        val label = keyLabels[keyCode] ?: ""
        val reserved = reservedLayerKeys(page)[keyCode]
        val emoji = symMappings[keyCode] ?: ""
        val keyButton = createEmojiKeyButton(label, reserved ?: emoji, height, page)
        if (reserved != null) greyOutReservedKey(keyButton) else keyButton.setOnClickListener {
            onKeyClick(keyCode, emoji)
        }
        rowLayout.addView(keyButton, LinearLayout.LayoutParams(width, height))
        if (!isLast) {
            rowLayout.addView(View(context), LinearLayout.LayoutParams(spacing, height))
        }
    }
    
    /**
     * Slides the emoji grid up into view (no fade).
     * @param backgroundView Il view dello sfondo da impostare a opaco immediatamente
     */
    private fun animateEmojiKeyboardIn(view: View, backgroundView: View? = null) {
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            view.alpha = 1f
            view.translationY = 0f
            view.visibility = View.VISIBLE
            backgroundView?.setBackgroundColor(activeThemeColors().background)
            return
        }
        val height = view.height
        if (height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }
        val measuredHeight = view.measuredHeight

        view.alpha = 1f
        view.translationY = measuredHeight.toFloat()
        view.visibility = View.VISIBLE

        // Restore the theme background, including its transparency, without animation.
        backgroundView?.let { bgView ->
            if (bgView.background !is ColorDrawable) {
                bgView.background = ColorDrawable(activeThemeColors().background)
            }
            (bgView.background as? ColorDrawable)?.color = activeThemeColors().background
        }

        val animator = ValueAnimator.ofFloat(measuredHeight.toFloat(), 0f).apply {
            duration = 125
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                view.translationY = value
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationY = 0f
                    view.alpha = 1f
                }
            })
        }
        animator.start()
    }
    
    /**
     * Slides the emoji grid down and fades it out.
     * @param backgroundView The background view (not animated, stays opaque)
     * @param onAnimationEnd Called when the animation ends
     */
    private fun animateEmojiKeyboardOut(view: View, backgroundView: View? = null, onAnimationEnd: (() -> Unit)? = null) {
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            view.visibility = View.GONE
            view.translationY = 0f
            view.alpha = 1f
            onAnimationEnd?.invoke()
            return
        }
        val height = view.height
        if (height == 0) {
            view.visibility = View.GONE
            onAnimationEnd?.invoke()
            return
        }

        // Background remains opaque, no animation

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
                view.translationY = height * (1f - progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.translationY = 0f
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }
        animator.start()
    }

    fun update(snapshot: StatusSnapshot, emojiMapText: String = "", inputConnection: android.view.inputmethod.InputConnection? = null, symMappings: Map<Int, String>? = null) {
        lastSnapshotSymPage = snapshot.symPage
        lastEmojiScreenFromEmojiKey = snapshot.emojiScreenFromEmojiKey
        isTitan2Layout = SettingsManager.isTitan2LayoutEnabled(context)
        val isFullSoftwareKeyboardMode =
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        val isSoftwareKeyboardClipboardPage = isFullSoftwareKeyboardMode && snapshot.symPage == 3
        val isSoftwareKeyboardEmojiPage = isFullSoftwareKeyboardMode && snapshot.symPage == 4
        val isSoftwareKeyboardSymbolPage = isFullSoftwareKeyboardMode && snapshot.symPage in listOf(1, 2, 5)
        val isSoftwareKeyboardOverlayPage =
            isSoftwareKeyboardSymbolPage || isSoftwareKeyboardClipboardPage || isSoftwareKeyboardEmojiPage
        if (!isSoftwareKeyboardEmojiPage) {
            dismissEmojiPickerPopup()
        }
        val activeTheme = activeThemeSettings(isFullSoftwareKeyboardMode)
        val activeColors = activeTheme.toKeyboardThemeColors()
        val softwareThemeSettings = if (isFullSoftwareKeyboardMode) activeTheme else softwareTheme()
        variationBarView?.onVariationSelectedListener = onVariationSelectedListener
        variationBarView?.onCursorMovedListener = onCursorMovedListener
        variationBarView?.updateInputConnection(inputConnection)
        variationBarView?.forceVariationAreaVisible = isFullSoftwareKeyboardMode
        variationBarView?.setSymModeActive((snapshot.symPage > 0 && !isSoftwareKeyboardOverlayPage) || snapshot.clipboardOverlay)
        variationBarView?.updateLanguageButtonText()
        updateClipboardCount(snapshot.clipboardCount)
        hamburgerMenuView?.refreshLanguageText()
        fullSuggestionsBar?.refreshLanguageText()
        updatePastierinaModeState()
        if (inputConnection !== lastHamburgerInputConnection) {
            hideHamburgerMenu()
            lastHamburgerInputConnection = inputConnection
        }
        if ((snapshot.symPage > 0 && !isFullSoftwareKeyboardMode) || snapshot.clipboardOverlay || (pastierinaModeActive && !isFullSoftwareKeyboardMode)) {
            hideHamburgerMenu()
        }
        
        val layout = ensureLayoutCreated(emojiMapText) ?: return
        restoreLayoutHeight(layout)
        ensureMainChildOrder()
        applyChromeZOrder()
        applyKeyboardThemeOverrides(activeColors)
        applyAccessibilitySecondRowReadPreference()
        val modifiersContainerView = modifiersContainer ?: return
        val emojiView = emojiMapTextView ?: return
        val emojiKeyboardView = emojiKeyboardContainer ?: return
        val symSurfaceView = symSurfaceContainer ?: return
        val symSurfaceStackView = symSurfaceStack ?: return
        emojiView.visibility = View.GONE
        
        if (snapshot.navModeActive) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        applyAccessibilityLiveRegionPreference(layout)
        
        if (layout.background !is ColorDrawable) {
            layout.background = ColorDrawable(activeTheme.background)
        } else if (snapshot.symPage == 0) {
            (layout.background as ColorDrawable).color = activeThemeColors().background
        }
        
        modifiersContainerView.visibility = View.GONE
        val showHardwareBottomIndicators = SettingsManager.getModifierIndicatorShowsBottomStrip(context)
        val showHardwareStatusBarIndicators = SettingsManager.getModifierIndicatorShowsStatusBar(context)
        fullSuggestionsBar?.setModifierMenuIndicatorsEnabled(
            !isFullSoftwareKeyboardMode && showHardwareStatusBarIndicators
        )
        fullSuggestionsBar?.updateModifierIndicators(snapshot)
        updateMenuBarModifierIndicators(
            container = modifiersContainerView,
            snapshot = snapshot,
            show = false,
            theme = activeColors
        )

        val activeLedLayout = modifierLedLayout()
        ledStatusView.layout = activeLedLayout
        (statusBarLayout as? ImeChromeLayout)?.ledSideWidthPx = panelSideButtonWidthPx()
        // Right Shift as the emoji key must not light the Sym side as a Shift
        ledStatusView.rightShiftIsShift = SettingsManager.getEmojiPickerKey(context) != KeyEvent.KEYCODE_SHIFT_RIGHT
        val showLedStrip = if (isFullSoftwareKeyboardMode) {
            softwareThemeSettings.showLeds
        } else {
            showHardwareBottomIndicators
        }
        // Corner style "Contoured LEDs": one LED rail along the display curve under the rounded
        // buttons, on every screen: the plain keyboard, Solderina, SYM and emoji pages and the
        // clipboard (not the full on-screen keyboard).
        val contourIntegratedIndicators =
            showLedStrip &&
                SettingsManager.getTitan2EliteContourLeds(context) &&
                !isFullSoftwareKeyboardMode &&
                ModifierLedLayouts.isSplit(activeLedLayout) &&
                (statusBarLayout as? ImeChromeLayout)?.bottomCornerRadiiPx != null
        ledStatusView.contourIntegrated = contourIntegratedIndicators
        (statusBarLayout as? ImeChromeLayout)?.contourIntegratedIndicators =
            contourIntegratedIndicators
        ledStatusView.getView()?.visibility = when {
            !showLedStrip -> View.GONE
            menuBarOpen() -> View.INVISIBLE
            else -> View.VISIBLE
        }
        if (showLedStrip) {
            ledStatusView.update(snapshot)
        }
        if (ledsOnlyMode) {
            renderLedsOnly(snapshot, layout, emojiKeyboardView, symSurfaceView)
            return
        }
        if (ledStatusView.hideOffLeds) {
            ledStatusView.hideOffLeds = false
            (statusBarLayout as? ImeChromeLayout)?.ledsOnly = false
            refreshWindowInsets()
            // The bar is back: the navigation bar's strip takes the keyboard's colour again
            applyKeyboardThemeOverrides(activeThemeColors())
            ledStatusView.update(snapshot)
        }
        val showSecondRow = !pastierinaModeActive
        val variationsBar = if (showSecondRow) variationBarView else null
        val variationsWrapperView = if (showSecondRow) variationsWrapper else null
        if (!showSecondRow) {
            variationBarView?.hideImmediate()
        }
        val experimentalEnabled = SettingsManager.isExperimentalSuggestionsEnabled(context)
        val suggestionsEnabledSetting = SettingsManager.getSuggestionsEnabled(context)
        // Keep the suggestion/status row stable in both full-status-bar and Pastierina mode.
        val expansionActive = expansionSuggestions.isNotEmpty()
        val autofillActive = inlineAutofillViews.isNotEmpty() && snapshot.symPage == 0 && !snapshot.clipboardOverlay
        // The extra keys row lives in the bar: open, it shows even where suggestions are off (terminals)
        val showFullBar = extraKeysRow != null || expansionActive || autofillActive || (
            suggestionsEnabledSetting &&
                (experimentalEnabled || isFullSoftwareKeyboardMode) &&
                (isFullSoftwareKeyboardMode || !snapshot.shouldDisableSuggestions) &&
                (snapshot.symPage == 0 || isSoftwareKeyboardOverlayPage) &&
                !snapshot.clipboardOverlay
            )
        emojiSearchInBar = pastierinaModeActive && !isFullSoftwareKeyboardMode &&
            snapshot.symPage == 4 && !snapshot.clipboardOverlay
        if (!emojiSearchInBar) releaseEmojiSearchFromBar()
        // Symbol search: read the bundled list before it's needed
        if (snapshot.symPage == 2 || snapshot.symPage == 5) {
            SymbolSearch.prewarm(context)
        }
        // GIF search is one key away (emoji layer) or one tap (picker): have featured GIFs ready,
        // except while the picker is there for symbol search (that gets the phone to itself)
        val symbolSearchInPicker = snapshot.symPage == 4 &&
            (pendingSymbolSearch || emojiPickerView?.isSymbolSearchOpen() == true)
        if ((snapshot.symPage == 1 || snapshot.symPage == 4) && !symbolSearchInPicker &&
            SettingsManager.gifsAvailable(context)
        ) {
            KlipyGifs.prefetchFeatured(context, SettingsManager.getKlipyApiKey(context))
        }
        // Emoji layer (1) and the symbols pages (2 symbols, 5 device) have a search bar there
        val barSearchPage = snapshot.symPage.takeIf {
            pastierinaModeActive && !isFullSoftwareKeyboardMode && !snapshot.clipboardOverlay && it in listOf(1, 2, 5)
        }
        updateEmojiLayerSearchBar(barSearchPage != null, symbols = barSearchPage != null && barSearchPage != 1)
        val suggestionsAnnouncementDelayMs = SettingsManager.getAccessibilitySuggestionsAnnouncementDelayMs(context)
        fullSuggestionsBar?.setAccessibilityAnnouncementConfig(
            liveAnnouncementsEnabled = isAccessibilityLiveAnnouncementsEnabled(),
            suggestionsAnnouncementDelayMs = suggestionsAnnouncementDelayMs
        )
        fullSuggestionsBar?.requireDictionaryForSuggestions = !expansionActive && !isFullSoftwareKeyboardMode
        fullSuggestionsBar?.update(
            if (expansionActive) expansionSuggestions else snapshot.suggestions,
            showFullBar,
            inputConnection,
            onVariationSelectedListener,
            if (expansionActive || isFullSoftwareKeyboardMode) false else snapshot.shouldDisableSuggestions,
            if (expansionActive) null else snapshot.addWordCandidate,
            onAddUserWord,
            onAddUserWordSubstitutionRequested,
            onSuggestionCommitted,
            onHideSuggestion,
            onDeleteUserSuggestion,
            canDeleteUserSuggestion,
            if (expansionActive) { _, suggestion -> onExpansionSuggestionSelected?.invoke(suggestion) } else null
        )
        renderInlineAutofill(autofillActive && !emojiSearchInBar)
        val shouldShowSoftwareKeyboard =
            isFullSoftwareKeyboardMode &&
                !snapshot.clipboardOverlay
        (layout as? ImeChromeLayout)?.expandedPickerButtons = null
        (layout as? ImeChromeLayout)?.softwareKeyboardModeActive = shouldShowSoftwareKeyboard
        if (snapshot.clipboardOverlay) {
            // Show clipboard as dedicated overlay (not part of SYM pages)
            updateClipboardView(
                inputConnection,
                softwareKeyboardHeight = lastSoftwareKeyboardHeight.takeIf { isFullSoftwareKeyboardMode && it > 0 }
            )
            variationsBar?.resetVariationsState()

            // Pin background and hide variations while showing clipboard grid
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(activeColors.background)
            }
            (layout.background as? ColorDrawable)?.color = activeThemeColors().background
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE
                isEnabled = false
                isClickable = false
            }
            variationsBar?.hideImmediate()

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val animationHeight = if (measured > 0) measured else defaultSymHeightPx
            emojiKeyboardView.setBackgroundColor(activeColors.background)
            emojiKeyboardView.visibility = View.VISIBLE
            val surfaceHeight = resolveSurfaceHeightWithOptionalLed(animationHeight, showLedStrip)
            setSurfaceCloseVisible(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
            applySymSurfaceLayout(
                symSurfaceView,
                symSurfaceStackView,
                emojiKeyboardView,
                surfaceHeight,
                reserveLedSpace = showLedStrip
            )
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = surfaceHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }

        if (shouldShowSoftwareKeyboard && (snapshot.symPage == 0 || isSoftwareKeyboardSymbolPage)) {
            updateSoftwareKeyboard(snapshot, inputConnection, symMappings)
            if (showSecondRow) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                val snapshotForVariations = snapshot.copy(
                    suggestions = emptyList(),
                    addWordCandidate = null,
                    variations = snapshot.variations.ifEmpty {
                        SettingsManager.getStaticVariationBasePreset(context)
                    },
                    shouldDisableVariations = false
                )
                variationsBar?.showVariations(snapshotForVariations, inputConnection)
            } else {
                variationBarView?.hideImmediate()
            }
            val measured = measureSoftwareKeyboardDesiredHeight(emojiKeyboardView, layout)
            val keyboardHeight = if (measured > 0) measured else defaultSymHeightPx
            lastSoftwareKeyboardHeight = keyboardHeight
            emojiKeyboardView.setBackgroundColor(softwareThemeSettings.background)
            emojiKeyboardView.visibility = View.VISIBLE
            applySymSurfaceLayout(
                symSurfaceView,
                symSurfaceStackView,
                emojiKeyboardView,
                resolveSurfaceHeightWithOptionalLed(keyboardHeight, showLedStrip),
                reserveLedSpace = showLedStrip
            )
            setSurfaceCloseVisible(false)
            symShown = snapshot.symPage in listOf(1, 2, 5)
            wasSymActive = snapshot.symPage in listOf(1, 2, 5)
            return
        } else {
            softwareKeyboardShown = false
        }

        if (snapshot.symPage > 0) {
            // Handle page 3 (clipboard), page 4 (emoji picker) vs pages 1-2 (emoji/symbols)
            if (snapshot.symPage == 3) {
                // Show clipboard history inline (similar to emoji grid)
                updateClipboardView(
                    inputConnection,
                    softwareKeyboardHeight = lastSoftwareKeyboardHeight.takeIf { isFullSoftwareKeyboardMode && it > 0 }
                )
            } else if (snapshot.symPage == 4) {
                // Show emoji picker view
                updateEmojiPickerView(
                    snapshot,
                    inputConnection,
                    softwareKeyboardHeight = lastSoftwareKeyboardHeight.takeIf { isFullSoftwareKeyboardMode && it > 0 }
                )
            } else if (isSoftwareKeyboardSymbolPage && symMappings != null) {
                updateSoftwareSymbolKeyboard(symMappings, snapshot, inputConnection)
            } else if (symMappings != null) {
                updateEmojiKeyboard(symMappings, snapshot.symPage, inputConnection)
            }
            variationsBar?.resetVariationsState()

            // Restore the theme background and hide variations while SYM animates.
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(activeColors.background)
            }
            (layout.background as? ColorDrawable)?.color = activeThemeColors().background
            if (isSoftwareKeyboardOverlayPage) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                val snapshotForVariations = if (snapshot.suggestions.isNotEmpty()) {
                    snapshot.copy(suggestions = emptyList(), addWordCandidate = null)
                } else snapshot
                variationsBar?.showVariations(snapshotForVariations, inputConnection)
            } else {
                variationsWrapperView?.apply {
                    visibility = View.INVISIBLE // keep space to avoid shrink/flash
                    isEnabled = false
                    isClickable = false
                }
                variationsBar?.hideImmediate()
            }

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val symHeight = resolveSymSurfaceHeight(
                snapshot = snapshot,
                measuredHeight = measured,
                isFullSoftwareKeyboardMode = isFullSoftwareKeyboardMode
            )
            val surfaceHeight = resolveSurfaceHeightWithOptionalLed(symHeight, showLedStrip)
            lastSymHeight = surfaceHeight
            emojiKeyboardView.setBackgroundColor(activeColors.background)
            emojiKeyboardView.visibility = View.VISIBLE
            applySymSurfaceLayout(
                symSurfaceView,
                symSurfaceStackView,
                emojiKeyboardView,
                surfaceHeight,
                reserveLedSpace = showLedStrip
            )
            // The layer pages (1, 2, 5) have their own close key in the grid
            setSurfaceCloseVisible(
                (snapshot.symPage == 3 && SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) ||
                (snapshot.symPage == 4 && (layout as? ImeChromeLayout)?.expandedPickerButtons != null))
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f // keep black visible immediately
                emojiKeyboardView.translationY = surfaceHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }
        
        if (emojiKeyboardView.visibility == View.VISIBLE) {
            animateEmojiKeyboardOut(emojiKeyboardView, layout) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                val snapshotForVariations = if (snapshot.suggestions.isNotEmpty()) {
                    snapshot.copy(suggestions = emptyList(), addWordCandidate = null)
                } else snapshot
                variationsBar?.showVariations(snapshotForVariations, inputConnection)
                setSurfaceCloseVisible(false)
                resetSymSurfaceToLedOnly(symSurfaceView)
            }
            symShown = false
            wasSymActive = false
            lastSymPageRendered = 0 // Reset when closing SYM page
        } else {
            emojiKeyboardView.visibility = View.GONE
            variationsWrapperView?.apply {
                visibility = View.VISIBLE
                isEnabled = true
                isClickable = true
            }
            val snapshotForVariations = if (snapshot.suggestions.isNotEmpty()) {
                snapshot.copy(suggestions = emptyList(), addWordCandidate = null)
            } else snapshot
            variationsBar?.showVariations(snapshotForVariations, inputConnection)
            setSurfaceCloseVisible(false)
            resetSymSurfaceToLedOnly(symSurfaceView)
            symShown = false
            wasSymActive = false
            lastSymPageRendered = 0 // Reset when closing SYM page
        }
    }

    private fun isAccessibilityLiveAnnouncementsEnabled(): Boolean {
        return SettingsManager.getAccessibilityLiveAnnouncementsEnabled(context)
    }

    private fun isAccessibilityReadSecondRowEnabled(): Boolean {
        return SettingsManager.getAccessibilityReadSecondRowEnabled(context)
    }

    private fun applyAccessibilityLiveRegionPreference(view: View) {
        if (view.accessibilityLiveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE) {
            view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
    }

    private fun applyAccessibilitySecondRowReadPreference() {
        val importance = if (isAccessibilityReadSecondRowEnabled()) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        variationsWrapper?.let { wrapper ->
            if (wrapper.importantForAccessibility != importance) {
                wrapper.importantForAccessibility = importance
            }
        }
        modifiersContainer?.let { container ->
            if (container.importantForAccessibility != importance) {
                container.importantForAccessibility = importance
            }
        }
    }

    private fun ensureEmojiKeyboardMeasuredHeight(view: View, parent: View, forceReMeasure: Boolean = false): Int {
        if (view.height > 0 && !forceReMeasure) {
            return view.height
        }
        val width = if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun measureSoftwareKeyboardDesiredHeight(view: View, parent: View): Int {
        val width = if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun applySymSurfaceLayout(
        surface: FrameLayout,
        stack: LinearLayout,
        content: View,
        surfaceHeight: Int,
        reserveLedSpace: Boolean
    ) {
        surface.visibility = View.VISIBLE
        val surfaceParams = surface.layoutParams as? LinearLayout.LayoutParams
        if (surfaceParams == null) {
            surface.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                surfaceHeight
            )
        } else if (
            surfaceParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            surfaceParams.height != surfaceHeight ||
            surfaceParams.weight != 0f
        ) {
            surfaceParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            surfaceParams.height = surfaceHeight
            surfaceParams.weight = 0f
            surface.layoutParams = surfaceParams
        }
        val stackParams = stack.layoutParams as? FrameLayout.LayoutParams
        if (stackParams == null) {
            stack.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else if (
            stackParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            stackParams.height != ViewGroup.LayoutParams.MATCH_PARENT
        ) {
            stackParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            stackParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            stack.layoutParams = stackParams
        }
        // In line with the content's bottom row: above the LED strip and the content's padding
        updateSurfaceCloseBottomMargin(
            (if (reserveLedSpace) reservedExpandedLedHeight() else 0) + content.paddingBottom
        )

        val contentParams = content.layoutParams as? LinearLayout.LayoutParams
        val targetContentHeight = 0
        val targetContentWeight = 1f
        if (contentParams == null) {
            content.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetContentHeight,
                targetContentWeight
            )
        } else if (
            contentParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            contentParams.height != targetContentHeight ||
            contentParams.weight != targetContentWeight
        ) {
            contentParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            contentParams.height = targetContentHeight
            contentParams.weight = targetContentWeight
            content.layoutParams = contentParams
        }
    }

    private fun setSurfaceCloseVisible(visible: Boolean) {
        symSurfaceCloseButton?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateSurfaceCloseBottomMargin(bottomMargin: Int) {
        val button = symSurfaceCloseButton ?: return
        val params = button.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.bottomMargin == bottomMargin) {
            return
        }
        params.bottomMargin = bottomMargin
        button.layoutParams = params
    }

    private fun resolveSurfaceHeightWithOptionalLed(contentHeight: Int, reserveLedSpace: Boolean): Int {
        if (!reserveLedSpace) {
            return contentHeight
        }
        val ledHeight = reservedExpandedLedHeight()
        return contentHeight + ledHeight
    }

    private fun reservedExpandedLedHeight(): Int =
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            dpToPx(LedStatusView.MERGED_LED_ZONE_HEIGHT_DP)
        }
        else measureLedStripHeight()

    private fun measureLedStripHeight(): Int {
        val ledStrip = ledStatusView.getView() ?: return 0
        if (ledStrip.measuredHeight > 0) {
            return ledStrip.measuredHeight
        }
        val width = statusBarLayout?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        ledStrip.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return ledStrip.measuredHeight
    }

    private fun resetSymSurfaceToLedOnly(surface: FrameLayout) {
        surface.layoutParams = (surface.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            weight = 0f
        }
    }

    private fun resolveSymSurfaceHeight(
        snapshot: StatusSnapshot,
        measuredHeight: Int,
        isFullSoftwareKeyboardMode: Boolean
    ): Int {
        if (!isFullSoftwareKeyboardMode && snapshot.symPage in listOf(1, 2, 5)) {
            // All hardware SYM pages use the same three key rows. Do not let
            // measurement under the previous page's weighted layout resize them.
            // Plus the space above and below the rows (Titan 2 Elite rounded corners).
            val gap = dpToPx(4f)
            return 3 * layerKeyHeightPx(snapshot.symPage) + 2 * gap + 2 * expandedScreenGapPx()
        }
        if (snapshot.symPage == 4 && measuredHeight > 0) {
            return measuredHeight
        }
        if (isFullSoftwareKeyboardMode && lastSoftwareKeyboardHeight > 0) {
            return lastSoftwareKeyboardHeight
        }
        return if (measuredHeight > 0) measuredHeight else defaultSymHeightPx
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * The side buttons' width on every panel (bar, emoji, symbols, kaomoji, GIFs, clipboard): the
     * suggestion bar's own, so the corners line up whichever panel is open.
     */
    private fun panelSideButtonWidthPx(): Int =
        fullSuggestionsBar?.sideButtonWidthPx()?.takeIf { it > 0 }
            ?: (context.resources.displayMetrics.widthPixels / 9)

    /** A layer page's key height: taller while the symbols page shows kaomoji (each with its letter under it). */
    private fun layerKeyHeightPx(page: Int): Int {
        val base = hardwareSymKeyHeightPx()
        return if (page == 2 && it.palsoftware.pastiera.core.SymLayoutController.kaomojiShown) (base * 1.4f).toInt() else base
    }

    private fun hardwareSymKeyHeightPx(theme: KeyboardThemeColors = activeThemeColors()): Int {
        if (!SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) return dpToPx(HARDWARE_SYM_KEY_HEIGHT_DP)
        return if (pastierinaModeActive) dpToPx(36f * theme.suggestionsHeightScale.coerceIn(0.65f, 1.6f))
        else dpToPx(55f * theme.variationsHeightScale.coerceIn(0.65f, 1.6f))
    }

    internal class ImeChromeLayout(context: Context) : LinearLayout(context) {
        private val screenAwakeController = ImeTouchScreenAwakeController(context)
        var regularCornerColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
        var compactCornerColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
        var bottomFillColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
        var expandedCloseColor: Int = Color.BLACK
        var expandedCloseButton: View? = null
        var expandedPickerButtons: Pair<View, View>? = null
        var expandedKeyHeightPx: Int = (HARDWARE_SYM_KEY_HEIGHT_DP * resources.displayMetrics.density).toInt()
        private val cornerFillPaint = Paint()
        private val calibrationPreviewListener: () -> Unit = { redrawForNewContour() }

        /** The contour changed: the corner buttons, LEDs and fills all redraw along it. */
        private fun redrawForNewContour() {
            applyBottomCornerClip()
            fun invalidateTree(view: View) {
                view.invalidate()
                view.background?.invalidateSelf()
                if (view is ViewGroup) for (index in 0 until view.childCount) invalidateTree(view.getChildAt(index))
            }
            invalidateTree(this)
            requestLayout()
        }
        // Kept as a field: SharedPreferences only holds listeners weakly
        private val chromePrefsListener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == SettingsManager.KEY_TITAN2_ELITE_FILL_CORNERS ||
                    key == SettingsManager.KEY_TITAN2_ELITE_STATUS_BAR_LIFT ||
                    key == SettingsManager.KEY_TITAN2_ELITE_STRAIGHT_OUTER_BUTTONS ||
                    key == SettingsManager.KEY_TITAN2_ELITE_CONTOUR_LEDS ||
                    key == it.palsoftware.pastiera.device.T2eCornerCalibration.KEY
                ) {
                    redrawForNewContour()
                }
            }

        /** How far the nested status row sits above the LED contour (Titan 2 Elite lift setting). */
        var nestedRowLiftPx: Int = 0
            private set

        /** Bottom edge of the nested status row after layout, or -1 when no row is nested. */
        var nestedRowBottomPx: Int = -1
            private set

        /** Straight outer buttons: the chrome x range between the corner buttons, for the LEDs. */
        var straightLedSpanPx: Pair<Int, Int>? = null
            private set
        // Parents whose clipping was lifted so a corner button can reach below them (original values)
        private val unclippedParents = HashMap<ViewGroup, Pair<Boolean, Boolean>>()
        private val outerBasePaddingBottom = java.util.WeakHashMap<View, Int>()
        private val outerNormalBottom = java.util.WeakHashMap<View, Int>()
        // Straight corner buttons also reach the display's side: padding and the gap they fill
        private val outerBasePaddingSide = java.util.WeakHashMap<View, Int>()
        private val outerSideReach = java.util.WeakHashMap<View, Int>()

        /** The panels' shared side-button width: the LEDs' length is measured from it on every panel. */
        var ledSideWidthPx: Int = 0
            set(value) {
                if (field != value) {
                    field = value
                    requestLayout()
                }
            }

        /** Only the LEDs show (an app with the keyboard hidden): they keep their usual span. */
        var ledsOnly: Boolean = false
            set(value) {
                if (field != value) {
                    field = value
                    requestLayout()
                }
            }

        /** Straight outer buttons: top of the band under the buttons, where the LEDs run. */
        var straightLedBandTopPx: Int = -1
            private set

        private fun straightOuterButtonsActive(): Boolean =
            bottomCornerRadiiPx != null && SettingsManager.getTitan2EliteStraightOuterButtons(context)

        /**
         * Straight outer buttons: the corner buttons of [scope] (the nested bar, or the expanded
         * emoji/SYM screen) reach down to the bottom of the chrome; the display's own curve crops
         * them. Their icon or label stays where it was. Each button's normal bottom is remembered,
         * so a pass that doesn't re-lay out the buttons gives the same result. Undoes everything
         * for buttons that no longer qualify, or when [scope] is null.
         */
        private fun layoutStraightOuterButtons(scope: ViewGroup?) {
            val extended = HashSet<View>()
            val parentsNeeded = HashSet<ViewGroup>()
            var spanLeft = 0
            var spanRight = width
            var bandTop = -1
            if (scope != null && straightOuterButtonsActive()) {
                val gap = (3f * resources.displayMetrics.density).toInt()
                fun visit(view: View, x: Int, y: Int) {
                    if (view.visibility != View.VISIBLE) return
                    val edge = view.getTag(R.id.tag_outer_edge_button)
                        as? StatusBarButtonPosition
                    if (edge != null) {
                        if (view.width <= 0) return
                        val bottomNow = y + view.height
                        val normalBottom = if (bottomNow >= height) {
                            // Already at the bottom: extended before, or there naturally (it
                            // still reaches out to the side)
                            outerNormalBottom[view] ?: bottomNow
                        } else {
                            bottomNow.also { outerNormalBottom[view] = it }
                        }
                        val base = outerBasePaddingBottom.getOrPut(view) { view.paddingBottom }
                        // Every ancestor up to and including the chrome: each one clips its
                        // children to their bounds, and the bar's parent is the chrome itself
                        var parent = view.parent as? ViewGroup
                        while (parent != null) {
                            parentsNeeded += parent
                            if (parent === this@ImeChromeLayout) break
                            parent = parent.parent as? ViewGroup
                        }
                        // And out to the display's side: the space between the button and the edge
                        val leftSide = edge == StatusBarButtonPosition.LEFT
                        val gapNow = if (leftSide) x else width - (x + view.width)
                        val reach = if (gapNow > 0) gapNow.also { outerSideReach[view] = it }
                            else outerSideReach[view] ?: 0
                        val baseSide = outerBasePaddingSide.getOrPut(view) {
                            if (leftSide) view.paddingLeft else view.paddingRight
                        }
                        // Content stays centred on the button's normal area
                        val paddingBottom = base + (height - normalBottom)
                        val paddingLeft = if (leftSide) baseSide + reach else view.paddingLeft
                        val paddingRight = if (leftSide) view.paddingRight else baseSide + reach
                        if (view.paddingBottom != paddingBottom || view.paddingLeft != paddingLeft ||
                            view.paddingRight != paddingRight
                        ) {
                            view.setPadding(paddingLeft, view.paddingTop, paddingRight, paddingBottom)
                        }
                        view.layout(
                            if (leftSide) view.left - gapNow else view.left,
                            view.top,
                            if (leftSide) view.right else view.right + gapNow,
                            view.top + (height - y)
                        )
                        extended += view
                        bandTop = maxOf(bandTop, normalBottom)
                        if (edge == StatusBarButtonPosition.LEFT) {
                            spanLeft = maxOf(spanLeft, x + view.width + gap)
                        } else {
                            spanRight = minOf(spanRight, x - gap)
                        }
                        return
                    }
                    if (view is ViewGroup) {
                        // An opaque overlay covering the whole group (the menu) hides the corner
                        // buttons behind it; only it and what's above it may reach down
                        var first = 0
                        for (index in view.childCount - 1 downTo 0) {
                            val child = view.getChildAt(index)
                            if (child.visibility == View.VISIBLE && child.background != null &&
                                child.left <= 0 && child.top <= 0 &&
                                child.width >= view.width && child.height >= view.height
                            ) {
                                first = index
                                break
                            }
                        }
                        for (index in first until view.childCount) {
                            val child = view.getChildAt(index)
                            visit(child, x + child.left, y + child.top)
                        }
                    }
                }
                visit(scope, scope.left, scope.top)
            }
            parentsNeeded.forEach { parent ->
                if (parent !in unclippedParents) {
                    unclippedParents[parent] = parent.clipChildren to parent.clipToPadding
                    parent.clipChildren = false
                    parent.clipToPadding = false
                }
            }
            unclippedParents.keys.filter { it !in parentsNeeded }.forEach { parent ->
                val (clipChildren, clipToPadding) = unclippedParents.remove(parent)!!
                parent.clipChildren = clipChildren
                parent.clipToPadding = clipToPadding
            }
            outerBasePaddingBottom.keys.filter { it !in extended }.forEach { view ->
                val base = outerBasePaddingBottom.remove(view) ?: return@forEach
                outerNormalBottom.remove(view)
                outerSideReach.remove(view)
                val side = outerBasePaddingSide.remove(view)
                val leftSide = view.getTag(R.id.tag_outer_edge_button) == StatusBarButtonPosition.LEFT
                view.setPadding(
                    if (leftSide && side != null) side else view.paddingLeft, view.paddingTop,
                    if (!leftSide && side != null) side else view.paddingRight, base
                )
            }
            // The LEDs run between the corner buttons, the same length on every panel: measured
            // from the panels' shared side-button width, not from where this panel's buttons end
            val fixedSide = ledSideWidthPx
            val ledGap = (3f * resources.displayMetrics.density).toInt()
            var span = if (extended.isNotEmpty() && spanLeft < spanRight) {
                if (fixedSide > 0 && width > 2 * (fixedSide + ledGap)) (fixedSide + ledGap) to (width - fixedSide - ledGap)
                else spanLeft to spanRight
            } else null
            var band = if (span != null) bandTop else -1
            if (span == null && ledsOnly && straightOuterButtonsActive() && width > 0) {
                // No corner buttons shown: the LEDs keep the span they have between them
                val side = fixedSide.takeIf { it > 0 } ?: (width / 10)
                span = (side + ledGap) to (width - side - ledGap)
                band = 0
            }
            if (span != straightLedSpanPx || band != straightLedBandTopPx) {
                straightLedSpanPx = span
                straightLedBandTopPx = band
                surfaceView?.let(::invalidateTree)
            }
        }

        private fun invalidateTree(view: View) {
            view.invalidate()
            if (view is ViewGroup) for (index in 0 until view.childCount) invalidateTree(view.getChildAt(index))
        }

        /** The lift that applies to [view]: the lift if it belongs to the nested row, else 0. */
        internal fun liftFor(view: View): Int {
            val row = nestedRow ?: return 0
            if (nestedRowLiftPx == 0) return 0
            var current: View? = view
            while (current != null && current !== this) {
                if (current === row) return nestedRowLiftPx
                current = current.parent as? View
            }
            return 0
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            it.palsoftware.pastiera.device.T2eCornerCalibration.addPreviewListener(calibrationPreviewListener)
            SettingsManager.getPreferences(context).registerOnSharedPreferenceChangeListener(chromePrefsListener)
        }

        /**
         * Contoured LEDs: every corner button on every page (the bar's own draw their curve
         * themselves) has its background cut to the contour, the same gap inside the LEDs.
         * Done as drawing starts, so a background set since (themes, page changes) is caught.
         */
        private fun fitCornerButtonsToContour(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view.getTag(R.id.tag_outer_edge_button) != null) {
                val background = view.background
                val wrapped = background as? it.palsoftware.pastiera.inputmethod.statusbar.ContourClipDrawable
                when {
                    background == null ||
                        background is it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable -> Unit
                    contourIntegratedIndicators && wrapped == null ->
                        view.background = it.palsoftware.pastiera.inputmethod.statusbar.ContourClipDrawable(view, background)
                    !contourIntegratedIndicators && wrapped != null -> view.background = wrapped.inner
                }
                return
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) fitCornerButtonsToContour(view.getChildAt(index))
        }

        override fun draw(canvas: Canvas) {
            fitCornerButtonsToContour(this)
            val radii = bottomCornerRadiiPx
            if (radii != null) {
                drawStatusRowSideFill(canvas, radii)
            }
            if (fillDisplayCorners || radii == null || radii.first <= 0 && radii.second <= 0) {
                super.draw(canvas)
                drawContourLeds(canvas)
                return
            }
            val calibration = it.palsoftware.pastiera.device.T2eCornerCalibration.read(context)
            val path = it.palsoftware.pastiera.device.T2eCornerGeometry.path(
                width.toFloat(), height.toFloat(), radii.first.toFloat(), radii.second.toFloat(),
                calibration
            ).apply {
                // Preserve the calibrated physical corners, but do not carry their
                // inward bottom offset across the straight center of the display.
                addRect(
                    radii.first.toFloat(),
                    0f,
                    width.toFloat() - radii.second,
                    height.toFloat(),
                    Path.Direction.CW
                )
            }
            // Content draws unclipped, then the square corners outside the display curve are
            // painted over with a smooth (anti-aliased) edge: a hard clip leaves stair-stepped
            // pixels along the curve. Repainted every frame, so what was drawn there before
            // (contoured LEDs, see-through keys) never stacks. The background picture where
            // there is one, else the keyboard's colour; contoured LEDs draw over it afterwards.
            super.draw(canvas)
            val outside = Path().apply {
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
                op(path, Path.Op.DIFFERENCE)
                // Straight corner buttons fill their corners, on every page: never cut
                if (straightOuterButtonsActive()) {
                    outerBasePaddingBottom.keys.toList().forEach { button ->
                        if (!button.isShown) return@forEach
                        val bounds = android.graphics.Rect(0, 0, button.width, button.height)
                        runCatching { offsetDescendantRectToMyCoords(button, bounds) }.onSuccess {
                            op(Path().apply { addRect(android.graphics.RectF(bounds), Path.Direction.CW) }, Path.Op.DIFFERENCE)
                        }
                    }
                }
            }
            cornerFillPaint.isAntiAlias = true
            cornerFillPaint.style = Paint.Style.FILL
            cornerFillPaint.color = bottomFillColors.first
            val picture = background as? it.palsoftware.pastiera.theme.KeyboardBackgroundImage.Drawable
            if (picture != null) {
                picture.setBounds(0, 0, width, height)
                picture.fillPath(canvas, outside)
            } else {
                canvas.drawPath(outside, cornerFillPaint)
            }
            cornerFillPaint.isAntiAlias = false
            drawContourLeds(canvas)
        }

        private fun drawStatusRowSideFill(canvas: Canvas, radii: Pair<Int, Int>) {
            val row = nestedRow ?: return
            val colors = if (row === compactStatusRow) compactCornerColors else regularCornerColors
            cornerFillPaint.style = Paint.Style.FILL
            cornerFillPaint.color = colors.first
            canvas.drawRect(0f, row.top.toFloat(), radii.first.toFloat(), row.bottom.toFloat(), cornerFillPaint)
            cornerFillPaint.color = colors.second
            canvas.drawRect(
                (width - radii.second).toFloat(),
                row.top.toFloat(),
                width.toFloat(),
                row.bottom.toFloat(),
                cornerFillPaint
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bottomCornerRadiiPx != null && expandedSurfaceView?.visibility == View.VISIBLE) {
                val surface = surfaceView
                val content = expandedSurfaceView
                if (surface != null && content != null) {
                    cornerFillPaint.color = bottomFillColors.first
                    canvas.drawRect(0f, (surface.top + content.bottom).toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
                    // With contoured LEDs the buttons keep a gap inside the LEDs: no button
                    // colour reaching down into the corners
                    if (!contourIntegratedIndicators) expandedCloseButton?.takeIf { it.visibility == View.VISIBLE }?.let { button ->
                        cornerFillPaint.color = expandedCloseColor
                        canvas.drawRect(
                            (surface.left + button.left).toFloat(), (surface.top + button.top).toFloat(),
                            width.toFloat(), height.toFloat(), cornerFillPaint
                        )
                    }
                    if (!contourIntegratedIndicators) expandedPickerButtons?.let { (leftButton, rightButton) ->
                        listOf(leftButton, rightButton).forEach { button ->
                            if (button.visibility != View.VISIBLE) return@forEach
                            val bounds = android.graphics.Rect(0, 0, button.width, button.height)
                            offsetDescendantRectToMyCoords(button, bounds)
                            cornerFillPaint.color = expandedCloseColor
                            canvas.drawRect(
                                if (button === leftButton) 0f else bounds.left.toFloat(), bounds.top.toFloat(),
                                if (button === rightButton) width.toFloat() else bounds.right.toFloat(),
                                height.toFloat(), cornerFillPaint
                            )
                        }
                    }
                }
            }
            val row = nestedRow ?: return
            val radii = bottomCornerRadiiPx ?: return
            val colors = if (row === compactStatusRow) compactCornerColors else regularCornerColors
            // Continue the row's themed surface to the bottom of the display.
            // Children draw afterward, keeping the modifier lights above the fill.
            cornerFillPaint.color = if (row === compactStatusRow) bottomFillColors.second else bottomFillColors.first
            canvas.drawRect(0f, row.bottom.toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
            cornerFillPaint.color = colors.second
            canvas.drawRect((width - radii.second).toFloat(), row.bottom.toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
        }
        var indicatorView: View? = null
        var expandedSurfaceView: View? = null
        var compactStatusRow: View? = null
        var contourIntegratedIndicators: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                requestLayout()
                // The corner buttons shape themselves round the LED rail: redraw them too, not
                // just this layout, or they keep covering the rail with their old shape
                fun invalidateTree(view: View) {
                    view.invalidate()
                    view.background?.invalidateSelf()
                    if (view is ViewGroup) for (index in 0 until view.childCount) invalidateTree(view.getChildAt(index))
                }
                invalidateTree(this)
            }
        var onContourGeometryChanged: ((LedStatusView.ContourGeometry?) -> Unit)? = null
        /**
         * Contoured LEDs: the band the bar's row keeps clear above them, so its buttons fit
         * above the LEDs instead of being pushed up out of the keyboard.
         */
        private fun contourRowInsetPx(): Int {
            val calibration = it.palsoftware.pastiera.device.T2eCornerCalibration.read(context)
            return kotlin.math.ceil(
                calibration.offsetPx + LedStatusView.contourButtonInsetPx(context) - calibration.shiftYPx
            ).toInt().coerceAtLeast(0)
        }

        /** Draws the contoured LEDs after everything else, so nothing covers them. */
        var contourLedOverlay: ((Canvas) -> Unit)? = null

        private fun drawContourLeds(canvas: Canvas) {
            if (contourIntegratedIndicators) contourLedOverlay?.invoke(canvas)
        }
        private var nestedRow: View? = null
        private var originalRowMargins = intArrayOf(0, 0, 0)
        private var originalRowOutline: ViewOutlineProvider? = null
        private var originalRowClip = false
        private var originalRowMinHeight = 0
        private val originalIconTransforms = mutableMapOf<ImageView, Pair<ImageView.ScaleType, Matrix>>()

        private fun updateNestedStatusRow() {
            indicatorView?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            (indicatorView?.layoutParams as? LayoutParams)?.topMargin = 0
            expandedSurfaceView?.clipToOutline = false
            nestedRow?.let { row ->
                (row.layoutParams as LayoutParams).apply {
                    leftMargin = originalRowMargins[0]
                    rightMargin = originalRowMargins[1]
                    bottomMargin = originalRowMargins[2]
                }
                row.outlineProvider = originalRowOutline
                row.clipToOutline = originalRowClip
                row.minimumHeight = originalRowMinHeight
            }
            nestedRow = null
            nestedRowLiftPx = 0
            nestedRowBottomPx = -1
            val radii = bottomCornerRadiiPx ?: return
            if (expandedSurfaceView?.visibility == View.VISIBLE && indicatorView?.visibility == View.VISIBLE) {
                val density = resources.displayMetrics.density
                val radius = maxOf(radii.first, radii.second)
                val stripHeight = (LedStatusView.MERGED_LED_ZONE_HEIGHT_DP * density).toInt()
                val ledHeight = maxOf(radius + density.toInt(), expandedKeyHeightPx + stripHeight)
                (indicatorView?.layoutParams as? LayoutParams)?.apply {
                    height = ledHeight
                    topMargin = -(ledHeight - stripHeight).coerceAtLeast(0)
                }
                expandedSurfaceView?.apply {
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            val path = it.palsoftware.pastiera.device.T2eCornerGeometry.path(
                                view.width.toFloat(),
                                view.height.toFloat(),
                                radii.first.toFloat(),
                                radii.second.toFloat(),
                                it.palsoftware.pastiera.device.T2eCornerCalibration.read(context),
                                stripHeight.toFloat()
                            ).apply {
                                // Only the corners follow the display: the contour's inward bottom
                                // offset across the straight centre clipped away the middle of the
                                // panels' bottom rows (the emoji picker's category tabs)
                                op(
                                    Path().apply {
                                        addRect(
                                            radii.first.toFloat(), 0f,
                                            view.width.toFloat() - radii.second, view.height.toFloat(),
                                            Path.Direction.CW
                                        )
                                    },
                                    Path.Op.UNION
                                )
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) outline.setPath(path)
                            else {
                                @Suppress("DEPRECATION")
                                // Android 10 takes only a convex outline: the plain rectangle when the shape isn't one
                                if (path.isConvex) outline.setConvexPath(path) else outline.setRect(0, 0, view.width, view.height)
                            }
                        }
                    }
                    // Straight corner buttons reach into the corners on these pages too: no clip
                    clipToOutline = !straightOuterButtonsActive()
                    invalidateOutline()
                }
                return
            }
            if (softwareKeyboardModeActive || expandedSurfaceView?.visibility != View.GONE ||
                indicatorView?.visibility != View.VISIBLE || surfaceView?.visibility != View.VISIBLE
            ) return
            val surfaceIndex = indexOfChild(surfaceView)
            val row = (surfaceIndex - 1 downTo 0)
                .map(::getChildAt).firstOrNull { it.visibility == View.VISIBLE } ?: return
            val params = row.layoutParams as LayoutParams
            nestedRow = row
            originalRowMargins = intArrayOf(params.leftMargin, params.rightMargin, params.bottomMargin)
            originalRowOutline = row.outlineProvider
            originalRowClip = row.clipToOutline
            originalRowMinHeight = row.minimumHeight
            // Reserve a dedicated band so both LED rows remain visibly separate.
            val inset = if (contourIntegratedIndicators) 0 else
                (LedStatusView.LED_ZONE_HEIGHT_DP * resources.displayMetrics.density).toInt()
            val radius = maxOf(radii.first, radii.second)
            val stripTop = (resources.displayMetrics.density).toInt()
            // The LED surface draws first; overlap its empty center with the row.
            val requestedRowHeight = params.height.coerceAtLeast(0)
            val bottomInset = if (contourIntegratedIndicators) contourRowInsetPx() else
                (LedStatusView.MERGED_LED_ZONE_HEIGHT_DP * resources.displayMetrics.density).toInt()
            // Lift: a taller LED surface raises the row by the same amount; onLayout keeps the
            // row's bottom that far above the LEDs, leaving them a clear band underneath.
            nestedRowLiftPx = if (contourIntegratedIndicators) 0 else SettingsManager.getTitan2EliteStatusBarLiftPx(context)
            indicatorView?.layoutParams?.height =
                maxOf(radius + stripTop, requestedRowHeight + bottomInset) + nestedRowLiftPx
            // A fixed-height row does not honor minimumHeight during measurement.
            // Overlapping more than that height puts the LED surface above the
            // row's top, while LinearLayout still reserves its full height below.
            // onLayout expands the row to meet the LEDs after measurement.
            val overlap = if (params.height >= 0) requestedRowHeight
                else (radius + stripTop - inset).coerceAtLeast(0)
            params.bottomMargin -= overlap
            row.minimumHeight = maxOf(originalRowMinHeight, overlap)
            // Each outer button draws against the calibrated contour; the chrome clips the display edge.
            row.outlineProvider = originalRowOutline
            row.clipToOutline = false
            row.invalidateOutline()
        }

        private var unstickPasses = 0

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            layoutChrome(changed, left, top, right, bottom)
            // Shaping the corner buttons here (their padding) asks for a layout mid-layout: the
            // views between them and this one stay marked as waiting, and every later change
            // inside them (the picker's tabs, a jump to a category) stopped there and never
            // reached the screen until something outside, like an LED, laid everything out.
            // Lay out again so those marks clear (a couple of times at most, in case the shapes
            // keep changing)
            // (A hidden child keeps its mark until it shows, and isn't laid out meanwhile)
            val stuck = (0 until childCount).any { index ->
                getChildAt(index).let { it.visibility != View.GONE && it.isLayoutRequested }
            }
            if (!stuck) {
                unstickPasses = 0
            } else if (unstickPasses < 2) {
                unstickPasses++
                post { if (isAttachedToWindow && !isLayoutRequested) requestLayout() }
            }
        }

        // Part of onLayout: the frame lays its children out first
        @SuppressLint("WrongCall")
        private fun layoutChrome(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            originalIconTransforms.forEach { (icon, original) ->
                icon.scaleType = original.first
                icon.imageMatrix = original.second
            }
            originalIconTransforms.clear()
            val row = nestedRow as? ViewGroup ?: run {
                // Expanded emoji/SYM screen: its bottom-row corner keys are the corner buttons
                layoutStraightOuterButtons(
                    if (expandedSurfaceView?.visibility == View.VISIBLE) surfaceView as? ViewGroup else null
                )
                return
            }
            val originalContentHeight = row.height
            // A fixed-height row can be shorter than the requested overlap.
            // Anchor its actual bottom to the inner LED contour after layout.
            // The straight lower indicators occupy only the lower LED row;
            // their top edge is closer to the bottom than the two-row side arcs.
            val bottomInset = if (contourIntegratedIndicators) contourRowInsetPx() else
                (LedStatusView.MERGED_LED_ZONE_HEIGHT_DP * resources.displayMetrics.density).toInt()
            surfaceView?.let { surface ->
                val targetBottom = surface.bottom - bottomInset - nestedRowLiftPx
                val extraHeight = (targetBottom - row.bottom).coerceAtLeast(0)
                // Fill the space up to the row's original top instead of translating
                // a short row downward and exposing an empty band above it.
                fun extendContent(view: View) {
                    val oldTop = if (view === row) view.top else 0
                    val oldLeft = view.left
                    val oldWidth = view.width
                    val oldHeight = view.height
                    val targetHeight = if (view === row) oldHeight + extraHeight
                        else (view.parent as View).height
                    view.measure(
                        MeasureSpec.makeMeasureSpec(oldWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
                    )
                    view.layout(oldLeft, oldTop, oldLeft + oldWidth, oldTop + targetHeight)
                    if (view is ViewGroup) {
                        for (index in 0 until view.childCount) {
                            val child = view.getChildAt(index)
                            if (child.visibility == View.VISIBLE &&
                                child.height >= originalContentHeight - 4f * resources.displayMetrics.density &&
                                child.height <= view.height
                            ) extendContent(child)
                        }
                    }
                }
                if (extraHeight > 0) extendContent(row)
            }
            nestedRowBottomPx = row.bottom
            val curvedButtons = mutableListOf<View>()
            fun collectCurvedButtons(view: View) {
                if (view.background is it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable) {
                    curvedButtons.add(view)
                }
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) collectCurvedButtons(view.getChildAt(index))
                }
            }
            collectCurvedButtons(row)

            if (contourIntegratedIndicators) {
                fun containsExtendableButton(view: View): Boolean {
                    if (view.visibility != View.VISIBLE) return false
                    if (view.isClickable && view.background != null) return true
                    if (view is ViewGroup) {
                        for (index in 0 until view.childCount) {
                            if (containsExtendableButton(view.getChildAt(index))) return true
                        }
                    }
                    return false
                }
                fun extendButtonBranches(view: ViewGroup, offsetY: Int) {
                    // Down to the keyboard's bottom edge, under the contoured LEDs.
                    // In the row's coordinates: the band above the LEDs, counted once from the
                    // keyboard's bottom edge, however the row itself was laid out
                    val visibleBottom = (this@ImeChromeLayout.height - this@ImeChromeLayout.paddingBottom - row.top)
                        .coerceAtLeast(0)
                    for (index in 0 until view.childCount) {
                        val child = view.getChildAt(index)
                        if (!containsExtendableButton(child)) continue
                        val absoluteBottom = offsetY + child.bottom
                        // Buttons reach the bottom edge; the contoured LEDs draw over them
                        val targetBottom = visibleBottom
                        val adjustment = targetBottom - absoluteBottom
                        if (adjustment != 0 && child.bottom + adjustment > child.top) {
                            child.layout(child.left, child.top, child.right, child.bottom + adjustment)
                        }
                        if (child is ViewGroup) extendButtonBranches(child, offsetY + child.top)
                    }
                }
                extendButtonBranches(row, 0)
                val surface = surfaceView
                if (surface != null && curvedButtons.size >= 2) {
                    val buttons = curvedButtons.map { button ->
                        button to android.graphics.Rect(0, 0, button.width, button.height).also { rect ->
                            offsetDescendantRectToMyCoords(button, rect)
                        }
                    }.sortedBy { it.second.left }
                    val surfaceTop = surface.top.toFloat()
                    val surfaceLeft = surface.left.toFloat()
                    val (leftButtonView, leftButton) = buttons.first()
                    val (rightButtonView, rightButton) = buttons.last()
                    fun contourFor(button: View): LedStatusView.ButtonContour? {
                        val drawable = button.background as?
                            it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable
                            ?: return null
                        val contour = drawable.outerContourCenterline() ?: return null
                        return LedStatusView.ButtonContour(
                            points = contour.points.map { point ->
                                android.graphics.PointF(point.x - surfaceLeft, point.y - surfaceTop)
                            },
                            borderHalfWidthPx = contour.borderHalfWidthPx
                        )
                    }
                    onContourGeometryChanged?.invoke(
                        LedStatusView.ContourGeometry(
                            // Chrome coordinates: the LED rail climbs the corner buttons to here
                            buttonTopPx = minOf(leftButton.top, rightButton.top).toFloat(),
                            leftButtonEndPx = leftButton.right.toFloat(),
                            rightButtonStartPx = rightButton.left.toFloat(),
                            leftButtonContour = contourFor(leftButtonView),
                            rightButtonContour = contourFor(rightButtonView)
                        )
                    )
                } else {
                    onContourGeometryChanged?.invoke(null)
                }
            } else {
                // Keep the existing vertical alignment when the contour LEDs are not active.
                for (index in 0 until row.childCount) {
                    val child = row.getChildAt(index)
                    if (child.visibility == View.VISIBLE && child.height < row.height) {
                        child.offsetTopAndBottom(row.height - row.paddingBottom - child.bottom)
                    }
                }
                onContourGeometryChanged?.invoke(null)
            }
            layoutStraightOuterButtons(row)
            val straightOuter = straightLedSpanPx != null
            val radii = bottomCornerRadiiPx ?: return
            fun fitIcons(view: View, offsetX: Int) {
                if (view is ImageView && view.visibility == View.VISIBLE &&
                    view.background !is it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable &&
                    // Straight corner buttons keep their icon centred on the row part
                    !(straightOuter && view.getTag(R.id.tag_outer_edge_button) != null)
                ) {
                    val onLeft = offsetX < radii.first
                    val onRight = offsetX + view.width > row.width - radii.second
                    val drawable = view.drawable
                    if ((onLeft || onRight) && drawable != null &&
                        drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0
                    ) {
                        originalIconTransforms[view] = view.scaleType to Matrix(view.imageMatrix)
                        val iconFraction = if (row === compactStatusRow) 0.64f else 0.48f
                        val iconHeight = if (row === compactStatusRow) {
                            (minOf(view.height, originalContentHeight) - 4f * resources.displayMetrics.density).coerceAtLeast(1f)
                        } else view.height.toFloat()
                        val size = minOf(view.width.toFloat(), iconHeight) * iconFraction
                        val requestedScale = size / maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight)
                        val scale = if (row === compactStatusRow) requestedScale else minOf(1f, requestedScale)
                        val iconCenterX = view.width / 2f
                        view.scaleType = ImageView.ScaleType.MATRIX
                        view.imageMatrix = Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(
                                iconCenterX - drawable.intrinsicWidth * scale / 2f - view.paddingLeft,
                                (view.height / 2f) -
                                    drawable.intrinsicHeight * scale / 2f - view.paddingTop
                            )
                        }
                    }
                }
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        val child = view.getChildAt(index)
                        fitIcons(child, offsetX + child.left)
                    }
                }
            }
            fitIcons(row, 0)
        }

        // (left, right) display corner radii in px; null disables the outline clip.
        var bottomCornerRadiiPx: Pair<Int, Int>? = null
            set(value) {
                if (field == value) {
                    applyBottomCornerClip()
                    invalidate()
                    requestLayout()
                    return
                }
                field = value
                applyBottomCornerClip()
                requestLayout()
            }

        var fillDisplayCorners: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        var surfaceView: View? = null
            set(value) {
                field = value
                requestLayout()
            }
        var softwareKeyboardModeActive: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                requestLayout()
            }

        init {
            setChildrenDrawingOrderEnabled(true)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            screenAwakeController.onTouchAction(event.actionMasked)
            return super.dispatchTouchEvent(event)
        }

        // Extend the shape above the view so even a bar shorter than the display
        // radius follows the original arc, rather than shrinking it to fit the bar.
        private fun applyBottomCornerClip() {
            val radii = bottomCornerRadiiPx
            val radius = radii?.let { maxOf(it.first, it.second) } ?: 0
            if (radius <= 0) {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
                return
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val left = radii!!.first.coerceIn(0, view.width / 2).toFloat()
                    val right = radii.second.coerceIn(0, view.width / 2).toFloat()
                    val path = it.palsoftware.pastiera.device.T2eCornerGeometry.path(
                        view.width.toFloat(), view.height.toFloat(), left, right,
                        it.palsoftware.pastiera.device.T2eCornerCalibration.read(context)
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        outline.setPath(path)
                    } else {
                        @Suppress("DEPRECATION")
                        // Android 10 takes only a convex outline: the plain rectangle when the shape isn't one
                        if (path.isConvex) outline.setConvexPath(path) else outline.setRect(0, 0, view.width, view.height)
                    }
                }
            }
            // draw() clips the content to the display curve unless "Fill corners" lets the
            // background reach the physical corners; the outer buttons still draw their own shape.
            fillDisplayCorners = SettingsManager.getTitan2EliteFillCorners(context)
            clipToOutline = false
            invalidateOutline()
        }

        override fun onDetachedFromWindow() {
            it.palsoftware.pastiera.device.T2eCornerCalibration.removePreviewListener(calibrationPreviewListener)
            SettingsManager.getPreferences(context).unregisterOnSharedPreferenceChangeListener(chromePrefsListener)
            screenAwakeController.release()
            super.onDetachedFromWindow()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            updateNestedStatusRow()
            val surface = surfaceView
            if (!softwareKeyboardModeActive || surface == null || surface.visibility == View.GONE) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            var totalChildHeight = 0
            var maxWidth = 0

            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.visibility == View.GONE) continue
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    0,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    0
                )
                val params = child.layoutParams as MarginLayoutParams
                totalChildHeight += child.measuredHeight + params.topMargin + params.bottomMargin
                maxWidth = maxOf(maxWidth, child.measuredWidth + params.leftMargin + params.rightMargin)
            }

            val measuredWidth = resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec)
            val desiredHeight = paddingTop + paddingBottom + totalChildHeight
            val measuredHeight = desiredHeight
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
            val surfaceIndex = surfaceView
                ?.let(::indexOfChild)
                ?.takeIf { it in 0 until childCount }
                ?: return super.getChildDrawingOrder(childCount, drawingPosition)

            // The LEDs draw above the status bar whenever it nests into the rounded corners, so
            // straight corner buttons and filled corners never cover them. (The surface is
            // transparent there; an open SYM or emoji screen keeps the usual order.)
            val ledsOnTop = contourIntegratedIndicators ||
                (bottomCornerRadiiPx != null && expandedSurfaceView?.visibility != View.VISIBLE) ||
                // Straight corner buttons reach down over the LED strip on every page
                straightOuterButtonsActive()
            if (ledsOnTop) {
                return if (drawingPosition == childCount - 1) {
                    surfaceIndex
                } else if (drawingPosition < surfaceIndex) {
                    drawingPosition
                } else {
                    drawingPosition + 1
                }
            }

            return if (drawingPosition == 0) {
                surfaceIndex
            } else {
                val shiftedPosition = drawingPosition - 1
                if (shiftedPosition < surfaceIndex) shiftedPosition else shiftedPosition + 1
            }
        }
    }

}
