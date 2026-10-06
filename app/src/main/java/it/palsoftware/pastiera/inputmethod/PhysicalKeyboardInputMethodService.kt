package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.legacy.LegacySettings
import it.palsoftware.pastiera.clipboard.PasteSuggestion
import it.palsoftware.pastiera.shortcuts.AppShortcutRemapper
import it.palsoftware.pastiera.shortcuts.AppShortcutSettings
import it.palsoftware.pastiera.shortcuts.KeyCombo
import it.palsoftware.pastiera.shortcuts.ShortcutAction
import it.palsoftware.pastiera.shortcuts.toIntent
import it.palsoftware.pastiera.inputmethod.suggestions.EmojiSuggestion
import it.palsoftware.pastiera.data.emoji.EmojiSearchRepository
import it.palsoftware.pastiera.shortcuts.AppActionDiscovery
import it.palsoftware.pastiera.shortcuts.DiscoveredAppActions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import it.palsoftware.pastiera.AppBroadcastActions
import it.palsoftware.pastiera.clicks.ClicksPowerKeyboardController
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.desktop.DesktopKeyboardLayout
import it.palsoftware.pastiera.data.gif.GifCollections
import it.palsoftware.pastiera.SoftwareKeyboardModeActions
import android.inputmethodservice.InputMethodService
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKey
import it.palsoftware.pastiera.getExtraKeysTerminal
import it.palsoftware.pastiera.getExtraKeysText
import it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeySets
import it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysRow
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.CursorAnchorInfo
import it.palsoftware.pastiera.clipboard.ClipboardDao
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import android.content.ClipDescription
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import it.palsoftware.pastiera.data.gif.GifResult
import it.palsoftware.pastiera.data.gif.KlipyGifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.apps.AppEnterStandards
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.DeferredPunctuationSpaceTracker
import it.palsoftware.pastiera.core.ContactDetails
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.suggestions.SuggestionController
import it.palsoftware.pastiera.core.suggestions.SuggestionKind
import it.palsoftware.pastiera.core.suggestions.SuggestionResult
import it.palsoftware.pastiera.core.suggestions.SuggestionSettings
import it.palsoftware.pastiera.data.emoji.EmojiCompatSupport
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver
import it.palsoftware.pastiera.data.variation.VariationRepository
import it.palsoftware.pastiera.inputmethod.SpeechRecognitionActivity
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardLayoutTemplates
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import it.palsoftware.pastiera.inputmethod.telex.VietnameseTelexProcessor
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadEventDeviceResolver
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadGestureDetector
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadAxisRange
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadCoordinateMapper
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionRuntimeConfig
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionTriggerKind
import it.palsoftware.pastiera.inputmethod.expansion.SnippetExpansionSource
import it.palsoftware.pastiera.inputmethod.expansion.EmojiShortcodeSource
import it.palsoftware.pastiera.inputmethod.expansion.SymbolShortcodeSource
import it.palsoftware.pastiera.inputmethod.expansion.TextExpansionController
import java.util.Locale
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.clipboard.ClipboardHistoryManager
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import it.palsoftware.pastiera.device.T2eCornerCalibration
import it.palsoftware.pastiera.clicks.ClicksAccessibilityKeyBridge
import it.palsoftware.pastiera.clicks.ClicksButtonDirectAction
import it.palsoftware.pastiera.clicks.ClicksButtonDirectActionExecutor
import it.palsoftware.pastiera.clicks.ClicksPowerButtonEventMapper
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardAutoDetector
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardDeviceTransitionPolicy
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardTextInputHandler
import it.palsoftware.pastiera.inputmethod.keys.AccidentalKeyPressFilter
import it.palsoftware.pastiera.inputmethod.keys.AccidentalKeyPressPolicy
import it.palsoftware.pastiera.inputmethod.keys.BounceKeyFilter
import it.palsoftware.pastiera.inputmethod.keys.ClicksPowerKeyboardLayout
import it.palsoftware.pastiera.inputmethod.keys.MultiTapController
import it.palsoftware.pastiera.inputmethod.keys.PhysicalKeyResolver
import it.palsoftware.pastiera.inputmethod.launcher.LauncherShortcutController
import it.palsoftware.pastiera.inputmethod.launcher.QuickLauncherOpener
import it.palsoftware.pastiera.inputmethod.voice.SpeechRecognitionManager
import it.palsoftware.pastiera.getAccentMatchingEnabled
import it.palsoftware.pastiera.getAddLastWordShortcut
import it.palsoftware.pastiera.getAdditionalSuggestionLocalesForInputStyle
import it.palsoftware.pastiera.getAltDoubleTapLocks
import it.palsoftware.pastiera.getAltLatchStaysOnSpace
import it.palsoftware.pastiera.getAltModifierBinding
import it.palsoftware.pastiera.getAltTapLatches
import it.palsoftware.pastiera.getAppEnterBehaviorEnabled
import it.palsoftware.pastiera.getAppEnterBehaviorOverrides
import it.palsoftware.pastiera.getAppEnterBehaviorPreset
import it.palsoftware.pastiera.getAutoCapAfterEmoticon
import it.palsoftware.pastiera.getAutoCapitalizeFirstLetter
import it.palsoftware.pastiera.getAutoCapitalizeRespectManualShiftOff
import it.palsoftware.pastiera.getAutoCorrectEnabled
import it.palsoftware.pastiera.getAutoReplaceOnSpaceEnter
import it.palsoftware.pastiera.getAutoSpacePunctuation
import it.palsoftware.pastiera.getClearAltOnSpace
import it.palsoftware.pastiera.getClicksAltButtonMode
import it.palsoftware.pastiera.getClicksButtonMode
import it.palsoftware.pastiera.getClicksCloseInputOnDisconnect
import it.palsoftware.pastiera.getClicksMetaButtonMode
import it.palsoftware.pastiera.getClicksMicrophoneButtonMode
import it.palsoftware.pastiera.getClicksNumberRowInputMode
import it.palsoftware.pastiera.getClicksOverlappingKeysMode
import it.palsoftware.pastiera.getClicksShowKeyboardOnlyWithTextFocus
import it.palsoftware.pastiera.getCommaSpace
import it.palsoftware.pastiera.getCtrlDoubleTapLocks
import it.palsoftware.pastiera.getCtrlLatchStaysOnSpace
import it.palsoftware.pastiera.getCtrlTapLatches
import it.palsoftware.pastiera.getEmojiKeyOpensLayer
import it.palsoftware.pastiera.getEmojiPickerKey
import it.palsoftware.pastiera.getEmojiShortcodesEnabled
import it.palsoftware.pastiera.getEmojiStickyTap
import it.palsoftware.pastiera.getEmojiSuggestionsEnabled
import it.palsoftware.pastiera.getEmojiSymbolsActivationPolicy
import it.palsoftware.pastiera.getEmojiSymbolsExactOnClose
import it.palsoftware.pastiera.getEmojiSymbolsPresentation
import it.palsoftware.pastiera.getHiddenAppStandardModifiers
import it.palsoftware.pastiera.getInlineAutofillEnabled
import it.palsoftware.pastiera.getKeyboardLayout
import it.palsoftware.pastiera.getLauncherShortcut
import it.palsoftware.pastiera.getLayoutAwareCtrlShortcutsEnabled
import it.palsoftware.pastiera.getLearnContactDetails
import it.palsoftware.pastiera.getLongPressThreshold
import it.palsoftware.pastiera.getMaxAutoReplaceDistance
import it.palsoftware.pastiera.getModifierIndicatorShowsMenuBar
import it.palsoftware.pastiera.getModifierIndicators
import it.palsoftware.pastiera.getNavModeCtrlHoldEnabled
import it.palsoftware.pastiera.getNavModeEnabled
import it.palsoftware.pastiera.getTerminalModeSwipeCursor
import it.palsoftware.pastiera.getOverlappingKeysEnabled
import it.palsoftware.pastiera.getPastierinaModeActive
import it.palsoftware.pastiera.getPhysicalKeyboardProfileOverride
import it.palsoftware.pastiera.getPowerShortcutsEnabled
import it.palsoftware.pastiera.getQuickLauncherAltShortcutsOutsideTextFields
import it.palsoftware.pastiera.getQuickLauncherAltSpaceInTextFields
import it.palsoftware.pastiera.getQuickLauncherTextFieldShortcuts
import it.palsoftware.pastiera.getShiftBackspaceDelete
import it.palsoftware.pastiera.getShiftDoubleTapLocks
import it.palsoftware.pastiera.getShiftTapLatches
import it.palsoftware.pastiera.getShowAddWordSuggestion
import it.palsoftware.pastiera.getSmartCtrlOffAfterShortcut
import it.palsoftware.pastiera.getSnippets
import it.palsoftware.pastiera.getSnippetsActivationPolicy
import it.palsoftware.pastiera.getSnippetsEnabled
import it.palsoftware.pastiera.getSnippetsPrefix
import it.palsoftware.pastiera.getSnippetsPresentation
import it.palsoftware.pastiera.getSoftwareKeyboardLayoutStyle
import it.palsoftware.pastiera.getSoftwareKeyboardMode
import it.palsoftware.pastiera.getSoftwareKeyboardModeToggleToastsEnabled
import it.palsoftware.pastiera.getSuggestionKeys
import it.palsoftware.pastiera.getSuggestionsEnabled
import it.palsoftware.pastiera.getSwipeToDelete
import it.palsoftware.pastiera.getSwipeToDeleteProvider
import it.palsoftware.pastiera.getSymStickyTap
import it.palsoftware.pastiera.getSymbolShortcodesEnabled
import it.palsoftware.pastiera.getTerminalModeEmojiKeyAction
import it.palsoftware.pastiera.getTerminalModeHideKeyboard
import it.palsoftware.pastiera.getTerminalModeShowLeds
import it.palsoftware.pastiera.getMinimalMode
import it.palsoftware.pastiera.adoptShizukuTrackpadIfUnchosen
import it.palsoftware.pastiera.getMinimalModeShowLeds
import it.palsoftware.pastiera.getTrackpadCaptureWhileTyping
import it.palsoftware.pastiera.getTrackpadDeleteSwipeThreshold
import it.palsoftware.pastiera.getTrackpadGestureAddWordEnabled
import it.palsoftware.pastiera.getTrackpadGestureAddWordFullWidthEnabled
import it.palsoftware.pastiera.getTrackpadGesturesEnabled
import it.palsoftware.pastiera.getTrackpadProvider
import it.palsoftware.pastiera.getTrackpadShizukuDevice
import it.palsoftware.pastiera.getTrackpadSideSwipeThreshold
import it.palsoftware.pastiera.getTrackpadSuggestionSwipeDirections
import it.palsoftware.pastiera.getTrackpadSuggestionSwipeThreshold
import it.palsoftware.pastiera.getTrackpadSwipeDownDeletesWord
import it.palsoftware.pastiera.getUseEditTypeRanking
import it.palsoftware.pastiera.getUseKeyboardProximity
import it.palsoftware.pastiera.hiddenAppAllowsPanels
import it.palsoftware.pastiera.hiddenAppShowsLeds
import it.palsoftware.pastiera.initializeNavModeMappingsFile
import it.palsoftware.pastiera.isAltEnterLayoutSwitchEnabled
import it.palsoftware.pastiera.isAltShiftLayoutSwitchEnabled
import it.palsoftware.pastiera.isClicksNumberRowRepeatEnabled
import it.palsoftware.pastiera.isCtrlSpaceLayoutSwitchEnabled
import it.palsoftware.pastiera.isExactTypingField
import it.palsoftware.pastiera.isExperimentalSuggestionsEnabled
import it.palsoftware.pastiera.isKeyboardHiddenForApp
import it.palsoftware.pastiera.isKeyboardThemePreferenceKey
import it.palsoftware.pastiera.isModifierIndicatorPreferenceKey
import it.palsoftware.pastiera.isQuickLauncherShortcut
import it.palsoftware.pastiera.isStaticVariationBarLayerStickyEnabled
import it.palsoftware.pastiera.isSuggestionDebugLoggingEnabled
import it.palsoftware.pastiera.isTerminalModeApp
import it.palsoftware.pastiera.isToastOnLayoutSwitchEnabled
import it.palsoftware.pastiera.resolveEffectiveSoftwareKeyboardMode
import it.palsoftware.pastiera.shouldApplyFrenchPunctuationSpacing

/**
 * Input method service specialized for physical keyboards.
 * Handles advanced features such as long press that simulates Alt+key.
 */
class PhysicalKeyboardInputMethodService : InputMethodService(), ClicksAccessibilityKeyBridge.Target {

    companion object {
        /** A terminal swipe: a character per half a key's width, a line per key's height or so. */
        private const val TERMINAL_SWIPE_CHARACTER_KEYS = 0.5f
        private const val TERMINAL_SWIPE_LINE_KEYS = 1.2f
        /** Root page: read the keyboard's touch pad as root (and pause the scroll module while typing) */
        private const val PASTE_SUGGESTION_WINDOW_MS = 60_000L
        private const val TAG = "PastieraInputMethod"
        private const val TRACKPAD_DEBUG_TAG = "TrackpadDebug"
        private const val NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS = 2f
        private const val KEYBOARD_SURFACE_TRANSITION_DELAY_MS = 32L
        private const val KEYBOARD_DEVICE_SURFACE_TRANSITION_DELAY_MS = 250L
        private const val MODIFIER_ICON_OFF = 0
        private const val MODIFIER_ICON_ACTIVE = 1
        private const val MODIFIER_ICON_LOCKED = 2
        private const val DISCORD_PACKAGE_NAME = "com.discord"
        private const val FACEBOOK_MESSENGER_PACKAGE_NAME = "com.facebook.orca"
        private val MESSENGER_ENTER_BEHAVIOR_PACKAGES = setOf(
            "com.whatsapp",
            CompatibilityWorkarounds.TELEGRAM_PACKAGE_NAME,
            "org.thoughtcrime.securesms",
            DISCORD_PACKAGE_NAME,
            "im.vector.app",
            "com.google.android.apps.messaging",
            "ch.threema.app",
            "ch.threema.app.libre",
            "com.instagram.android",
            FACEBOOK_MESSENGER_PACKAGE_NAME
        )
        private val ENTER_BEHAVIOR_SEND_ACTION_PACKAGES = MESSENGER_ENTER_BEHAVIOR_PACKAGES -
            DISCORD_PACKAGE_NAME
        private val SOFTWARE_PREVIEW_KEY_CODES = listOf(
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_ENTER
        )
    }

    // SharedPreferences for settings
    private lateinit var prefs: SharedPreferences
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastSystemLocalesSignature: String = ""

    private lateinit var alternateCharacterManager: AlternateCharacterManager
    
    // Speech recognition using SpeechRecognizer (modern approach)
    private var speechRecognitionManager: SpeechRecognitionManager? = null
    private var isSpeechRecognitionActive: Boolean = false
    private var pendingSpeechRecognition: Boolean = false
    
    // Broadcast receiver for speech recognition (deprecated, kept for backwards compatibility)
    private var speechResultReceiver: BroadcastReceiver? = null
    // Broadcast receiver for permission request result
    private var permissionResultReceiver: BroadcastReceiver? = null
    // Broadcast receiver for user dictionary updates
    private var userDictionaryReceiver: BroadcastReceiver? = null
    // Broadcast receiver for additional IME subtypes updates
    private lateinit var candidatesBarController: CandidatesBarController
    private lateinit var textExpansionController: TextExpansionController
    private lateinit var emojiShortcodeSource: EmojiShortcodeSource
    private lateinit var symbolShortcodeSource: SymbolShortcodeSource
    private val expansionAssetScope = CoroutineScope(Dispatchers.IO)

    // Keycode for the SYM key
    private val KEYCODE_SYM = 63

    // Minimal Phone (MP01) custom hardware keycodes
    private val KEYCODE_EM = 666  // Emoji key
    private val KEYCODE_MIC = 667 // Mic / speech-to-text key

    // Single instance to show toasts without overlapping
    private var lastLayoutToastText: String? = null
    private var lastLayoutToastTime: Long = 0
    private var suppressNextLayoutReload: Boolean = false
    private var activeKeyboardLayoutName: String = "qwerty"
    private var consumeAltEnterUntilKeyUp: Boolean = false
    // The key of a suggestion shortcut, whose key-up is ours too
    private var suggestionKeyUpPending: Int = KeyEvent.KEYCODE_UNKNOWN
    // The focused app is on the "hide keyboard in these apps" list: no UI, keys go to the app
    private var keyboardHiddenForApp: Boolean = false
    // ...but with "Show status LEDs only": the LED strip stays, following observed modifiers
    private var hiddenAppShowsLeds: Boolean = false
    // ...and this hidden app lets the emoji key and Sym open the keyboard (per-app option)
    private var hiddenAppAllowsPanels: Boolean = false
    private val observedModifierLeds = ObservedModifierLeds()
    // Hidden app with the panels option: the keyboard's surface is up for an emoji/symbols panel
    private var hiddenAppPanelShown: Boolean = false
    // Keys whose press went to the hidden app / to Pastiera (their release goes the same way)
    private val hiddenAppPassedThroughKeys = mutableSetOf<Int>()
    private val hiddenAppPastieraKeys = mutableSetOf<Int>()
    // Keys the accessibility service handed to Pastiera (their release follows)
    private val hiddenAppInterceptedKeys = mutableSetOf<Int>()
    // Key-up of the dedicated emoji picker key still to be swallowed (KEYCODE_UNKNOWN = none)
    private var emojiPickerKeyUpPending: Int = KeyEvent.KEYCODE_UNKNOWN
    // An emoji key that is also a modifier (Right Shift) opens the picker on release, unless it
    // was held for a chord such as Ctrl+Shift+Q
    private var emojiPickerKeyChorded: Boolean = false
    private var dispatchingSoftwareKeyboardKey: Boolean = false
    
    // For Power Shortcuts
    private var powerShortcutToast: android.widget.Toast? = null
    
    // Mapping Ctrl+key -> action or keycode (loaded from JSON)
    private val ctrlKeyMap = mutableMapOf<Int, KeyMappingLoader.CtrlMapping>()
    
    // Accessor properties for backwards compatibility with existing code
    private var capsLockEnabled: Boolean
        get() = modifierStateController.capsLockEnabled
        set(value) { modifierStateController.capsLockEnabled = value }
    
    private var shiftPressed: Boolean
        get() = modifierStateController.shiftPressed
        set(value) { modifierStateController.shiftPressed = value }
    
    private var ctrlLatchActive: Boolean
        get() = modifierStateController.ctrlLatchActive
        set(value) { modifierStateController.ctrlLatchActive = value }
    
    private var altLatchActive: Boolean
        get() = modifierStateController.altLatchActive
        set(value) { modifierStateController.altLatchActive = value }
    
    private var ctrlPressed: Boolean
        get() = modifierStateController.ctrlPressed
        set(value) { modifierStateController.ctrlPressed = value }
    
    private var altPressed: Boolean
        get() = modifierStateController.altPressed
        set(value) { modifierStateController.altPressed = value }
    
    private var shiftPhysicallyPressed: Boolean
        get() = modifierStateController.shiftPhysicallyPressed
        set(value) { modifierStateController.shiftPhysicallyPressed = value }
    
    private var ctrlPhysicallyPressed: Boolean
        get() = modifierStateController.ctrlPhysicallyPressed
        set(value) { modifierStateController.ctrlPhysicallyPressed = value }
    
    private var altPhysicallyPressed: Boolean
        get() = modifierStateController.altPhysicallyPressed
        set(value) { modifierStateController.altPhysicallyPressed = value }
    
    private var shiftOneShot: Boolean
        get() = modifierStateController.shiftOneShot
        set(value) { modifierStateController.shiftOneShot = value }

    private var ctrlOneShot: Boolean
        get() = modifierStateController.ctrlOneShot
        set(value) { modifierStateController.ctrlOneShot = value }
    
    private var altOneShot: Boolean
        get() = modifierStateController.altOneShot
        set(value) { modifierStateController.altOneShot = value }
    
    private var ctrlLatchFromNavMode: Boolean
        get() = modifierStateController.ctrlLatchFromNavMode
        set(value) { modifierStateController.ctrlLatchFromNavMode = value }
    
    // Flag to track whether we are in a valid input context
    private var isInputViewActive = false
    private var emojiSearchExternalSelectionStart: Int? = null
    private var emojiSearchExternalSelectionEnd: Int? = null
    private var emojiSearchCursorAnchorMonitoringRequested: Boolean = false
    private var ignoreNextEmojiSearchCursorAnchorUpdate: Boolean = false
    
    // Snapshot of the current input context (numeric/password/restricted fields, etc.)
    private var inputContextState: InputContextState = InputContextState.EMPTY
    
    private val isNumericField: Boolean
        get() = inputContextState.isNumericField
    
    private val shouldDisableSmartFeatures: Boolean
        get() = inputContextState.shouldDisableSmartFeatures

    private val shouldDisableAutoCapitalize: Boolean
        get() {
            if (inputContextState.isPasswordField || inputContextState.exactTyping) return true
            // Automatic Shift by kind of field (Settings > Text input)
            val type = ShiftFieldTypes.of(currentInputEditorInfo) ?: return inputContextState.shouldDisableAutoCapitalize
            return type !in ShiftFieldTypes.enabled(this)
        }
    
    // Current package name
    private var currentPackageName: String? = null
    
    // Constants
    private val DOUBLE_TAP_THRESHOLD = 500L
    // Shift, Ctrl or Alt held this long with no other key: a hold, which leaves them as they were.
    // Shorter is a tap: a deliberate press of a physical key can take 300 ms or more.
    private val MODIFIER_HOLD_MS = 500L
    // How much of a field is read to learn or match an email or phone number
    private val CONTACT_TEXT_LIMIT = 320
    private val CURSOR_UPDATE_DELAY = 50L
    private val MULTI_TAP_TIMEOUT_MS = 400L

    // Modifier/nav/SYM controllers
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var navModeController: NavModeController
    private lateinit var symLayoutController: SymLayoutController
    private lateinit var textInputController: TextInputController
    private lateinit var autoCorrectionManager: AutoCorrectionManager
    private lateinit var suggestionController: SuggestionController
    private lateinit var variationStateController: VariationStateController
    private lateinit var inputEventRouter: InputEventRouter
    private lateinit var typingSoundPlayer: TypingSoundPlayer
    private var skipNextSelectionUpdateAfterCommit: Boolean = false
    private var editorHasActiveSelection: Boolean = false
    private lateinit var keyboardVisibilityController: KeyboardVisibilityController
    private lateinit var launcherShortcutController: LauncherShortcutController
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private var latestSuggestionResults: List<SuggestionResult> = emptyList()
    private var lastRenderedStatusSnapshot: StatusBarController.StatusSnapshot? = null
    private var lastRenderedEmojiMapText: String? = null
    private var lastRenderedSymMappings: Map<Int, String>? = null
    private var lastRenderedStatusInputConnection: android.view.inputmethod.InputConnection? = null
    private var lastRenderedPastierinaModeActive: Boolean? = null
    private var lastRenderedSoftwareKeyboardMode: SettingsManager.SoftwareKeyboardMode? = null
    private var lastRenderedModifierIndicators: Set<String>? = null
    private var requestedInputViewShown: Boolean = true
    private var suppressedAutoCapContextKey: String? = null
    private var clearAltOnSpaceEnabled: Boolean = false
    private var physicalKeyboardProfileOverride: String = "auto"
    private var isLanguageSwitchInProgress: Boolean = false
    // Whether nav mode was on before entering a text field
    private var navModeWasActiveBeforeEditableField: Boolean = false

    // Trackpad gesture detection
    private val trackpadScope = CoroutineScope(Dispatchers.IO)
    // Sends GIFs picked in the emoji picker (download, then rich content or a link)
    private val gifScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var trackpadGestureDetector: TrackpadGestureDetector
    private var modifierStateBeforeHold: it.palsoftware.pastiera.core.ModifierStateController.LogicalState? = null
    private var variationInteractedDuringHold: Boolean = false
    private var modifierDownTimes = mutableMapOf<Int, Long>()
    private var otherKeyInteractedDuringHold: Boolean = false
    // Ctrl was held while a shortcut opened another field (the find bar Ctrl+F opens)
    private var ctrlHeldIntoNewField = false
    private var shiftLayerLatched: Boolean = false
    private var altModifierLayerLatched: Boolean = false
    private var lastShiftTapUpTime: Long = 0L
    private var lastAltTapUpTime: Long = 0L
    private var symTogglePendingOnKeyUp: Boolean = false
    // Flux Keyboard: SYM or the emoji key tapped, so the next key types its symbol or emoji
    private var symSticky: Boolean = false
    private var emojiSticky: Boolean = false
    private var symChordUsedSinceKeyDown: Boolean = false
    private var symPhysicallyPressed: Boolean = false
    private var nativeTrackpadGestureStart: NativeTrackpadGestureStart? = null
    private var nativeTrackpadLastX: Float = 0f
    private var nativeTrackpadLastY: Float = 0f
    private var nativeTrackpadLastEventTimeUptimeMs: Long = 0L
    private var nativeTrackpadGestureHandled: Boolean = false
    private var nativeTrackpadGestureAtMs: Long = 0L
    private var trackpadDecorMotionView: View? = null

    private val multiTapHandler = Handler(Looper.getMainLooper())
    private val multiTapController = MultiTapController(
        handler = multiTapHandler,
        timeoutMs = MULTI_TAP_TIMEOUT_MS
    )
    private val bounceKeyFilter = BounceKeyFilter()
    private val clicksPowerShiftTapFilter = ClicksPowerShiftTapFilter()
    private val accidentalKeyPressFilter = AccidentalKeyPressFilter()
    private val physicalKeyResolver = PhysicalKeyResolver()
    private val clicksPowerButtonEventMapper = ClicksPowerButtonEventMapper()
    private var dispatchingClicksAccessibilityKeyEvent = false
    private var replayingProtectedNumberKey = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var inputManager: InputManager? = null
    private var lastObservedAutoSoftwareKeyboardMode: SettingsManager.SoftwareKeyboardMode? = null
    private var pendingInputDeviceModeRefresh: Runnable? = null
    private var pendingKeyboardSurfaceTransition: Runnable? = null
    private var clicksConnectionChangePending: Boolean = false
    private var clicksDisconnectPending: Boolean = false
    private val connectedClicksInputDeviceIds = mutableSetOf<Int>()
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            SoftwareKeyboardAutoDetector.onInputDevicesChanged()
            val clicksConnected = InputDevice.getDevice(deviceId)
                ?.takeIf(DeviceSpecific::isClicksPowerKeyboard)
                ?.let { connectedClicksInputDeviceIds.add(deviceId) } == true
            scheduleInputDeviceModeRefresh(clicksConnectionChanged = clicksConnected)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            clicksPowerShiftTapFilter.resetDevice(deviceId)
            accidentalKeyPressFilter.resetDevice(deviceId)
            clicksPowerButtonEventMapper.resetDevice(deviceId)
            val clicksDisconnected = connectedClicksInputDeviceIds.remove(deviceId)
            if (
                clicksDisconnected &&
                SettingsManager.getClicksCloseInputOnDisconnect(this@PhysicalKeyboardInputMethodService)
            ) {
                SoftwareKeyboardAutoDetector.beginClosingInputForClicksDisconnect()
                requestHideSelf(0)
            }
            SoftwareKeyboardAutoDetector.onInputDevicesChanged()
            scheduleInputDeviceModeRefresh(
                clicksConnectionChanged = clicksDisconnected,
                clicksDisconnected = clicksDisconnected
            )
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            clicksPowerShiftTapFilter.resetDevice(deviceId)
            accidentalKeyPressFilter.resetDevice(deviceId)
            clicksPowerButtonEventMapper.resetDevice(deviceId)
            SoftwareKeyboardAutoDetector.onInputDevicesChanged()
            val wasClicksKeyboard = deviceId in connectedClicksInputDeviceIds
            val device = InputDevice.getDevice(deviceId)
            val isClicksKeyboard = device != null && DeviceSpecific.isClicksPowerKeyboard(device)
            if (isClicksKeyboard) {
                connectedClicksInputDeviceIds += deviceId
            } else {
                connectedClicksInputDeviceIds -= deviceId
            }
            scheduleInputDeviceModeRefresh(
                clicksConnectionChanged = wasClicksKeyboard != isClicksKeyboard
            )
        }
    }
    private var pendingStatusBarUpdate: Runnable? = null
    private var lastSystemStatusIconResId: Int? = null
    private var pendingSelectionAutoCapCheck: Runnable? = null
    private val clipboardCleanupIntervalMs = 60_000L
    private val clipboardCleanupRunnable = object : Runnable {
        override fun run() {
            val retention = SettingsManager.getClipboardRetentionTime(this@PhysicalKeyboardInputMethodService)
            clipboardHistoryManager.prepareClipboardHistory()
            val count = clipboardHistoryManager.getHistorySize()
            uiHandler.post {
                if (::candidatesBarController.isInitialized) {
                    candidatesBarController.updateClipboardCount(count)
                }
            }
            uiHandler.postDelayed(this, clipboardCleanupIntervalMs)
        }
    }

    private fun startClipboardCleanupTimer() {
        uiHandler.removeCallbacks(clipboardCleanupRunnable)
        uiHandler.postDelayed(clipboardCleanupRunnable, clipboardCleanupIntervalMs)
    }

    private fun stopClipboardCleanupTimer() {
        uiHandler.removeCallbacks(clipboardCleanupRunnable)
    }

    private val symPage: Int
        get() = if (::symLayoutController.isInitialized) symLayoutController.currentSymPage() else 0

    // The input type an app gave its field, before onStartInput adds NO_SUGGESTIONS for the
    // keyboard's own reasons: exact typing must follow what the app asked for, not that flag
    private var appInputType: Pair<EditorInfo, Int>? = null

    private fun updateInputContextState(info: EditorInfo?) {
        val state = InputContextState.fromEditorInfo(info)
        val requestedType = appInputType?.takeIf { it.first === info }?.second ?: info?.inputType ?: 0
        inputContextState = if (info != null && SettingsManager.isExactTypingField(this, info.packageName, requestedType)) {
            state.copy(exactTyping = true)
        } else {
            state
        }
    }

    private fun markSelectionUpdateSkipAfterCommit() {
        skipNextSelectionUpdateAfterCommit = true
        if (SettingsManager.isSuggestionDebugLoggingEnabled(this)) {
            Log.d(TAG, "markSelectionUpdateSkipAfterCommit() set skip flag")
        }
    }

    @Suppress("DEPRECATION")
    private fun updateNavModeStatusIcon(isActive: Boolean) {
        // Deprecated but still works on current Android versions; use for quick nav mode indicator.
        if (isActive) {
            showStatusIcon(R.drawable.ic_nav_mode_status)
            lastSystemStatusIconResId = R.drawable.ic_nav_mode_status
        } else {
            hideStatusIcon()
            lastSystemStatusIconResId = null
        }
    }

    @Suppress("DEPRECATION")
    private fun updateSystemStatusModifierIcon(
        snapshot: StatusBarController.StatusSnapshot,
        effectiveSoftwareKeyboardMode: SettingsManager.SoftwareKeyboardMode
    ) {
        if (snapshot.navModeActive) {
            if (lastSystemStatusIconResId != R.drawable.ic_nav_mode_status) {
                showStatusIcon(R.drawable.ic_nav_mode_status)
                lastSystemStatusIconResId = R.drawable.ic_nav_mode_status
            }
            return
        }

        val iconResId = if (
            SettingsManager.getModifierIndicatorShowsMenuBar(this) &&
            effectiveSoftwareKeyboardMode != SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) {
            systemStatusModifierIconResId(snapshot)
        } else {
            null
        }

        if (iconResId == lastSystemStatusIconResId) {
            return
        }

        if (iconResId != null) {
            showStatusIcon(iconResId)
        } else {
            hideStatusIcon()
        }
        lastSystemStatusIconResId = iconResId
    }

    private fun systemStatusModifierIconResId(snapshot: StatusBarController.StatusSnapshot): Int? {
        val shiftState = when {
            snapshot.capsLockEnabled -> MODIFIER_ICON_LOCKED
            snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot -> MODIFIER_ICON_ACTIVE
            else -> MODIFIER_ICON_OFF
        }
        val ctrlState = when {
            snapshot.ctrlLatchActive -> MODIFIER_ICON_LOCKED
            snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot -> MODIFIER_ICON_ACTIVE
            else -> MODIFIER_ICON_OFF
        }
        val altState = when {
            snapshot.altLatchActive -> MODIFIER_ICON_LOCKED
            snapshot.altPhysicallyPressed || snapshot.altOneShot -> MODIFIER_ICON_ACTIVE
            else -> MODIFIER_ICON_OFF
        }

        return modifierCombinationStatusIconResId(
            shiftState = shiftState,
            ctrlState = ctrlState,
            altState = altState
        ) ?: if (snapshot.symPage > 0) R.drawable.ic_status_modifier_sym else null
    }

    private fun modifierCombinationStatusIconResId(
        shiftState: Int,
        ctrlState: Int,
        altState: Int
    ): Int? = when ("$shiftState$ctrlState$altState") {
        "001" -> R.drawable.ic_status_modifiers_s0_c0_a1
        "002" -> R.drawable.ic_status_modifiers_s0_c0_a2
        "010" -> R.drawable.ic_status_modifiers_s0_c1_a0
        "011" -> R.drawable.ic_status_modifiers_s0_c1_a1
        "012" -> R.drawable.ic_status_modifiers_s0_c1_a2
        "020" -> R.drawable.ic_status_modifiers_s0_c2_a0
        "021" -> R.drawable.ic_status_modifiers_s0_c2_a1
        "022" -> R.drawable.ic_status_modifiers_s0_c2_a2
        "100" -> R.drawable.ic_status_modifiers_s1_c0_a0
        "101" -> R.drawable.ic_status_modifiers_s1_c0_a1
        "102" -> R.drawable.ic_status_modifiers_s1_c0_a2
        "110" -> R.drawable.ic_status_modifiers_s1_c1_a0
        "111" -> R.drawable.ic_status_modifiers_s1_c1_a1
        "112" -> R.drawable.ic_status_modifiers_s1_c1_a2
        "120" -> R.drawable.ic_status_modifiers_s1_c2_a0
        "121" -> R.drawable.ic_status_modifiers_s1_c2_a1
        "122" -> R.drawable.ic_status_modifiers_s1_c2_a2
        "200" -> R.drawable.ic_status_modifiers_s2_c0_a0
        "201" -> R.drawable.ic_status_modifiers_s2_c0_a1
        "202" -> R.drawable.ic_status_modifiers_s2_c0_a2
        "210" -> R.drawable.ic_status_modifiers_s2_c1_a0
        "211" -> R.drawable.ic_status_modifiers_s2_c1_a1
        "212" -> R.drawable.ic_status_modifiers_s2_c1_a2
        "220" -> R.drawable.ic_status_modifiers_s2_c2_a0
        "221" -> R.drawable.ic_status_modifiers_s2_c2_a1
        "222" -> R.drawable.ic_status_modifiers_s2_c2_a2
        else -> null
    }

    private fun refreshStatusBar() {
        updateStatusBarText()
    }

    private fun scheduleInputDeviceModeRefresh(
        clicksConnectionChanged: Boolean = false,
        clicksDisconnected: Boolean = false
    ) {
        clicksConnectionChangePending = clicksConnectionChangePending || clicksConnectionChanged
        clicksDisconnectPending = clicksDisconnectPending || clicksDisconnected
        pendingInputDeviceModeRefresh?.let { uiHandler.removeCallbacks(it) }
        val refresh = Runnable {
            pendingInputDeviceModeRefresh = null
            val didClicksConnectionChange = clicksConnectionChangePending
            val didClicksDisconnect = clicksDisconnectPending
            clicksConnectionChangePending = false
            clicksDisconnectPending = false
            refreshSoftwareKeyboardModeForConnectedDevices(
                clicksConnectionChanged = didClicksConnectionChange,
                clicksDisconnected = didClicksDisconnect
            )
        }
        pendingInputDeviceModeRefresh = refresh
        uiHandler.postDelayed(refresh, 120L)
    }

    private fun refreshSoftwareKeyboardModeForConnectedDevices(
        clicksConnectionChanged: Boolean,
        clicksDisconnected: Boolean
    ) {
        alternateCharacterManager.reloadModifierAndDeviceSymMappings()
        updateStatusBarText()
        val autoMode = SoftwareKeyboardAutoDetector.resolve(this)
        val previousAutoMode = lastObservedAutoSoftwareKeyboardMode
        lastObservedAutoSoftwareKeyboardMode = autoMode
        val configuredMode = SettingsManager.getSoftwareKeyboardMode(this)
        val transition = SoftwareKeyboardDeviceTransitionPolicy.plan(
            configuredMode = configuredMode,
            previousAutoMode = previousAutoMode,
            autoMode = autoMode,
            clicksConnectionChanged = clicksConnectionChanged,
            clicksDisconnected = clicksDisconnected,
            closeInputOnClicksDisconnect = SettingsManager.getClicksCloseInputOnDisconnect(this)
        ) ?: return
        if (transition.clearTemporaryOverride) {
            SoftwareKeyboardModeActions.clearTemporaryMode(this)
        }
        scheduleKeyboardSurfaceTransition(
            mode = transition.mode,
            closeInput = transition.closeInput,
            requireActiveTextField = SettingsManager.getClicksShowKeyboardOnlyWithTextFocus(this),
            delayMs = KEYBOARD_DEVICE_SURFACE_TRANSITION_DELAY_MS
        )
    }

    private fun scheduleKeyboardSurfaceTransition(
        mode: SettingsManager.SoftwareKeyboardMode,
        closeInput: Boolean = false,
        requireActiveTextField: Boolean = false,
        delayMs: Long = KEYBOARD_SURFACE_TRANSITION_DELAY_MS
    ) {
        pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
        val transition = Runnable {
            pendingKeyboardSurfaceTransition = null
            if (::textExpansionController.isInitialized) textExpansionController.clear()
            invalidateRenderedStatusSnapshot()
            if (closeInput) {
                requestHideSelf(0)
                return@Runnable
            }
            keyboardVisibilityController.onKeyboardSurfaceChanged(
                ensureInputViewShown = mode == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
                requireActiveTextField = requireActiveTextField
            )
        }
        pendingKeyboardSurfaceTransition = transition
        // A status-bar tap must finish dispatching before its own IME surface is replaced.
        // Two UI frames avoid InputDispatcher waiting for the disappearing touch target.
        uiHandler.postDelayed(transition, delayMs)
    }

    private fun toggleSoftwareKeyboardModeFromStatusBar() {
        val next = SoftwareKeyboardModeActions.toggleTemporaryMode(this)
        if (SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(this)) {
            val message = when (next) {
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL ->
                    getString(R.string.software_keyboard_mode_toggle_now_virtual)
                SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE ->
                    getString(R.string.software_keyboard_mode_toggle_now_hardware)
                SettingsManager.SoftwareKeyboardMode.AUTO ->
                    getString(R.string.software_keyboard_mode_auto_short)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleStatusBarTextUpdate(delayMs: Long = CURSOR_UPDATE_DELAY) {
        pendingStatusBarUpdate?.let { uiHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingStatusBarUpdate = null
            updateStatusBarText()
        }
        pendingStatusBarUpdate = runnable
        uiHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingSelectionDrivenUiWork() {
        pendingStatusBarUpdate?.let { uiHandler.removeCallbacks(it) }
        pendingStatusBarUpdate = null
        pendingSelectionAutoCapCheck?.let { uiHandler.removeCallbacks(it) }
        pendingSelectionAutoCapCheck = null
    }

    /**
     * The bar's chips (autofill, one-time codes, paste, emails and numbers) aren't part of the
     * status the bar is redrawn for, so showing or clearing one forces the redraw.
     */
    private fun refreshBarChips() {
        invalidateRenderedStatusSnapshot()
        updateStatusBarText()
    }

    private fun invalidateRenderedStatusSnapshot() {
        lastRenderedStatusSnapshot = null
        lastRenderedEmojiMapText = null
        lastRenderedSymMappings = null
        lastRenderedStatusInputConnection = null
        lastRenderedPastierinaModeActive = null
        lastRenderedSoftwareKeyboardMode = null
        lastRenderedModifierIndicators = null
    }

    private fun checkAutoCapitalizeOnSelectionChange(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ) {
        val perfStart = ImePerfLogger.mark()
        val state = inputContextState
        try {
            AutoCapitalizeHelper.checkAutoCapitalizeOnSelectionChange(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                oldSelStart,
                oldSelEnd,
                newSelStart,
                newSelEnd,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        } finally {
            ImePerfLogger.logDuration(
                label = "checkAutoCapitalizeOnSelectionChange",
                startNanos = perfStart,
                thresholdMs = 8L,
                details = "pkg=$currentPackageName"
            )
        }
    }

    private fun scheduleAutoCapitalizeOnSelectionChange(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ) {
        pendingSelectionAutoCapCheck?.let { uiHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingSelectionAutoCapCheck = null
            checkAutoCapitalizeOnSelectionChange(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        }
        pendingSelectionAutoCapCheck = runnable
        uiHandler.postDelayed(runnable, CURSOR_UPDATE_DELAY * 2)
    }

    private val startAutoCapRechecks = mutableListOf<Runnable>()

    // Fields that don't say what they are ("Other" in Automatic Shift)
    private var otherFieldShift = false
    private var otherFieldCapitalNext = false
    private var otherFieldLastChar: Char? = null

    /**
     * A field that doesn't say what it is can't be read, so the keyboard follows what's typed in
     * it: the first letter, and the first after Enter or after . ! ? and a space, get a capital.
     * Returns true when it typed the capital itself.
     */
    private fun otherFieldCapital(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.repeatCount != 0 || keyboardHiddenForApp || terminalModeActive) return false
        if (!::modifierStateController.isInitialized || KeyEvent.isModifierKey(keyCode)) return false
        val otherModifier = event.isCtrlPressed || event.isAltPressed || event.isMetaPressed ||
            ctrlPressed || ctrlOneShot || ctrlLatchActive || altPressed || altOneShot || altLatchActive ||
            symLayoutController.isSymActive()
        if (otherModifier) return false
        when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val shifted = event.isShiftPressed || modifierStateController.shiftOneShot ||
                    modifierStateController.capsLockEnabled || modifierStateController.shiftPressed
                if (otherFieldCapitalNext && !shifted) {
                    val capital = it.palsoftware.pastiera.data.layout.LayoutMappingRepository.getCharacter(keyCode, isShift = true)
                        ?: event.getUnicodeChar(KeyEvent.META_SHIFT_ON).takeIf { it > 0 }?.toChar()
                    if (capital != null && currentInputConnection?.commitText(capital.toString(), 1) == true) {
                        otherFieldCapitalNext = false
                        otherFieldLastChar = capital
                        return true
                    }
                }
                otherFieldCapitalNext = false
                otherFieldLastChar = 'a'
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                otherFieldCapitalNext = true
                otherFieldLastChar = '\n'
            }
            KeyEvent.KEYCODE_SPACE -> {
                if (otherFieldLastChar in setOf('.', '!', '?')) otherFieldCapitalNext = true
                otherFieldLastChar = ' '
            }
            KeyEvent.KEYCODE_DEL -> Unit
            else -> event.unicodeChar.takeIf { it > 0 }?.let { otherFieldLastChar = it.toChar() }
        }
        return false
    }

    private val EDIT_STEP_CHECK_MS = 150L
    private val EDIT_STEP_IDLE_MS = 900L
    private var editHistoryRecord: Runnable? = null

    /**
     * Ctrl+Z's history: a step at the end of each word (a space, a newline or punctuation was just
     * typed), or after a pause in typing.
     */
    private fun scheduleEditHistoryRecord(delay: Long) {
        editHistoryRecord?.let { uiHandler.removeCallbacks(it) }
        val runnable = Runnable {
            editHistoryRecord = null
            val ic = currentInputConnection ?: return@Runnable
            if (delay == EDIT_STEP_CHECK_MS) {
                val last = runCatching { ic.getTextBeforeCursor(1, 0) }.getOrNull()?.lastOrNull()
                if (last == null || !(last.isWhitespace() || last in ".,!?;:")) {
                    scheduleEditHistoryRecord(EDIT_STEP_IDLE_MS)
                    return@Runnable
                }
            }
            it.palsoftware.pastiera.core.EditHistory.record(ic)
        }
        editHistoryRecord = runnable
        uiHandler.postDelayed(runnable, delay)
    }

    /** Ctrl+Z (or Ctrl+Shift+Z, Ctrl+Y to redo) from the keyboard's own history; false: the app's. */
    internal fun undoFromHistory(redo: Boolean): Boolean {
        val ic = currentInputConnection ?: return false
        editHistoryRecord?.let { uiHandler.removeCallbacks(it) }
        editHistoryRecord = null
        return if (redo) it.palsoftware.pastiera.core.EditHistory.redo(ic) else it.palsoftware.pastiera.core.EditHistory.undo(ic)
    }

    /**
     * A field that has just opened (a new note in Keep, WhatsApp's box after sending) often
     * can't be read yet, and an empty field sends no cursor update to check again on. Checks
     * again shortly after, while nothing has been typed, so it starts with a capital.
     */
    private fun scheduleStartAutoCapRechecks() {
        startAutoCapRechecks.forEach { uiHandler.removeCallbacks(it) }
        startAutoCapRechecks.clear()
        // Some apps (Instagram, Keep) fill their field in a little after it opens
        listOf(120L, 450L, 1000L).forEach { delay ->
            val runnable = Runnable {
                if (!inputContextState.isEditable) return@Runnable
                AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                    this,
                    currentInputConnection,
                    shouldDisableAutoCapitalize,
                    enableShift = { requestAutoCapShiftOneShot() },
                    disableShift = { modifierStateController.consumeShiftOneShot() },
                    onUpdateStatusBar = { updateStatusBarText() },
                    inputContextState = inputContextState
                )
            }
            startAutoCapRechecks += runnable
            uiHandler.postDelayed(runnable, delay)
        }
    }

    private fun isPureModifierKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
            keyCode == KEYCODE_SYM
    }

    private fun isMinimalPhoneHardwareActive(): Boolean {
        return DeviceSpecific.isMinimalPhoneDevice(physicalKeyboardProfileOverride)
    }

    private fun openQuickLauncher(): Boolean = QuickLauncherOpener.open(this, currentInputEditorInfo?.packageName)
    
    /**
     * Starts voice input using SpeechRecognizer via SpeechRecognitionManager.
     */
    private fun startSpeechRecognition() {
        // If recognition is already active, toggle it off
        if (isSpeechRecognitionActive) {
            stopSpeechRecognition()
            return
        }
        
        // Check microphone permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "RECORD_AUDIO permission not granted, requesting...")
            pendingSpeechRecognition = true
            val intent = Intent(this, PermissionRequestActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            return
        }
        
        // Initialize manager if not already created
        if (speechRecognitionManager == null) {
            speechRecognitionManager = SpeechRecognitionManager(
                context = this,
                inputConnectionProvider = { currentInputConnection },
                onError = { errorMessage ->
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                },
                onRecognitionStateChanged = { isActive ->
                    // Update internal state
                    isSpeechRecognitionActive = isActive
                    
                    // Reset Alt and Ctrl modifiers when recognition starts
                    if (isActive) {
                        modifierStateController.clearAltState()
                        modifierStateController.clearCtrlState()
                    }
                    
                    // Update microphone button color and hint message based on recognition state
                    uiHandler.post {
                        candidatesBarController.setMicrophoneButtonActive(isActive)
                        candidatesBarController.showSpeechRecognitionHint(isActive)
                        // Reset audio level when recognition stops
                        if (!isActive) {
                            candidatesBarController.updateMicrophoneAudioLevel(-10f)
                        } else {
                            // Update status bar after resetting modifiers
                            updateStatusBarText()
                        }
                    }
                },
                shouldDisableAutoCapitalize = { shouldDisableAutoCapitalize },
                onAudioLevelChanged = { rmsdB ->
                    // Update microphone button based on audio level
                    uiHandler.post {
                        candidatesBarController.updateMicrophoneAudioLevel(rmsdB)
                    }
                }
            )
        }
        
        speechRecognitionManager?.startRecognition()
    }

    /**
     * Stops voice input if active.
     */
    private fun stopSpeechRecognition() {
        speechRecognitionManager?.stopRecognition()
    }

    private fun getSuggestionSettings(): SuggestionSettings {
        val suggestionsEnabled = SettingsManager.getSuggestionsEnabled(this)
        // Exact typing: suggestions stay, but nothing rewrites or spaces what you typed
        val exact = inputContextState.exactTyping
        return SuggestionSettings(
            textReplacementsEnabled = SettingsManager.getAutoCorrectEnabled(this) && !exact,
            suggestionsEnabled = suggestionsEnabled,
            accentMatching = SettingsManager.getAccentMatchingEnabled(this),
            autoReplaceOnSpaceEnter = SettingsManager.getAutoReplaceOnSpaceEnter(this) && !exact,
            maxAutoReplaceDistance = SettingsManager.getMaxAutoReplaceDistance(this),
            maxSuggestions = 3,
            useKeyboardProximity = SettingsManager.getUseKeyboardProximity(this),
            useEditTypeRanking = SettingsManager.getUseEditTypeRanking(this),
            frenchPunctuationSpacing = SettingsManager.shouldApplyFrenchPunctuationSpacing(this),
            commaSpace = SettingsManager.getCommaSpace(this) && !exact,
            autoSpacePunctuation = if (exact) "" else SettingsManager.getAutoSpacePunctuation(this)
        )
    }

    private fun clearAltOnBoundaryIfNeeded(keyCode: Int, updateStatusBar: () -> Unit) {
        if (!clearAltOnSpaceEnabled) return
        val isBoundary = keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER
        if (!isBoundary) return
        val hasAlt = altLatchActive || altOneShot
        if (!hasAlt) return
        if (altLatchActive && SettingsManager.getAltLatchStaysOnSpace(this)) {
            altOneShot = false
            updateStatusBar()
            return
        }
        modifierStateController.clearAltState()
        updateStatusBar()
    }

    private fun sendCtrlShortcut(keyCode: Int, shift: Boolean = false): Boolean {
        val ic = currentInputConnection ?: return false
        val now = System.currentTimeMillis()
        val metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or
            if (shift) {
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            } else {
                0
            }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
        return true
    }

    /**
     * Resolves a meaningful editor action for Enter. Returns null for unspecified fields
     * or when actions are explicitly disabled. Works for both single-line and multiline fields.
     */
    private fun resolveEditorAction(info: EditorInfo?): Int? {
        if (info == null) return null
        val imeOptions = info.imeOptions
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            return null
        }

        val action = when {
            info.actionId != 0 -> info.actionId
            else -> imeOptions and EditorInfo.IME_MASK_ACTION
        }

        return when (action) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS -> action
            else -> null
        }
    }

    /** Whether the field can hold more than one line (a message, note or post, not a search box). */
    private fun acceptsNewLines(info: EditorInfo?): Boolean {
        val type = info?.inputType ?: return false
        if (type and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_CLASS_TEXT) return false
        return type and (android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0
    }

    /**
     * Whether the field says it sends (a message or comment box). Most chat apps declare Send and
     * also ask for Enter to stay a new line (IME_FLAG_NO_ENTER_ACTION), so the flag is ignored here.
     */
    private fun declaresSendAction(info: EditorInfo): Boolean =
        info.actionId == EditorInfo.IME_ACTION_SEND ||
            info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEND

    private fun resolveAppEnterBehavior(info: EditorInfo?): String? {
        val packageName = info?.packageName ?: return null
        if (!SettingsManager.getAppEnterBehaviorEnabled(this)) return null

        val override = SettingsManager.getAppEnterBehaviorOverrides(this)
            .firstOrNull { it.packageName == packageName }
            ?.behavior
        if (override != null && override != SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT) {
            return override
        }
        if (packageName !in MESSENGER_ENTER_BEHAVIOR_PACKAGES) {
            // An app you set to "App default" keeps its own Enter
            if (override != null) return null
            // Every other app follows its category's standard (AppEnterStandards)
            return AppEnterStandards.behaviorFor(
                packageName,
                SettingsManager.getAppEnterBehaviorPreset(this),
                fieldSends = declaresSendAction(info)
            )
        }

        return when (SettingsManager.getAppEnterBehaviorPreset(this)) {
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE ->
                if (packageName == DISCORD_PACKAGE_NAME) {
                    null
                } else {
                    SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE
                }
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_SHIFT_SEND ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND
            else -> null
        }
    }

    private fun resolveAppEnterAdditionalSendShortcut(info: EditorInfo?): String {
        val packageName = info?.packageName ?: return SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        if (!SettingsManager.getAppEnterBehaviorEnabled(this)) return SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE

        return SettingsManager.getAppEnterBehaviorOverrides(this)
            .firstOrNull { it.packageName == packageName }
            ?.additionalSendShortcut
            ?: SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    }

    private fun resolveAppEnterSendStrategy(info: EditorInfo?): String? {
        val packageName = info?.packageName ?: return null
        if (!SettingsManager.getAppEnterBehaviorEnabled(this)) return null

        val override = SettingsManager.getAppEnterBehaviorOverrides(this)
            .firstOrNull { it.packageName == packageName }
        val configuredStrategy = override?.sendStrategy
            ?: SettingsManager.ENTER_SEND_STRATEGY_AUTO
        if (configuredStrategy != SettingsManager.ENTER_SEND_STRATEGY_AUTO) {
            return configuredStrategy
        }

        return when {
            packageName == DISCORD_PACKAGE_NAME -> SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER
            packageName in ENTER_BEHAVIOR_SEND_ACTION_PACKAGES ->
                SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION
            override != null -> SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION
            else -> AppEnterStandards.sendStrategyFor(packageName)
        }
    }

    private fun consumeUnsupportedEnterSend(
        keyCode: Int,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        val hadCtrl = ctrlLatchFromNavMode ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            navModeController.isNavModeActive()
        if (hadCtrl) {
            val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
            modifierStateController.clearCtrlState(resetPressedState = false)
            if (wasNavModeLatched) {
                navModeController.cancelNotification()
                navModeController.refreshNavModeState()
            }
            updateStatusBarText()
        }
        notifyDebugKeyEvent(
            keyCode,
            event,
            "KEY_DOWN",
            origin = "ime_service",
            outputKeyCode = null,
            outputKeyCodeName = outputKeyCodeName
        )
        return true
    }

    private fun performPlainEnterSend(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        inputConnection.finishComposingText()
        val now = System.currentTimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, 0)
        val downPerformed = inputConnection.sendKeyEvent(down)
        val upPerformed = inputConnection.sendKeyEvent(up)
        val performed = downPerformed && upPerformed
        val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
        modifierStateController.clearCtrlState(resetPressedState = false)
        if (wasNavModeLatched) {
            navModeController.cancelNotification()
            navModeController.refreshNavModeState()
        }
        updateStatusBarText()
        if (performed) {
            suggestionController.onContextReset()
        }
        notifyDebugKeyEvent(
            keyCode,
            event,
            "KEY_DOWN",
            origin = "ime_service",
            outputKeyCode = KeyEvent.KEYCODE_ENTER,
            outputKeyCodeName = if (performed) outputKeyCodeName else "${outputKeyCodeName}_rejected"
        )
        return true
    }

    private fun performCtrlEnterSend(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        inputConnection.finishComposingText()
        val now = System.currentTimeMillis()
        val metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, metaState)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, metaState)
        val downPerformed = inputConnection.sendKeyEvent(down)
        val upPerformed = inputConnection.sendKeyEvent(up)
        val performed = downPerformed && upPerformed
        val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
        modifierStateController.clearCtrlState(resetPressedState = false)
        if (wasNavModeLatched) {
            navModeController.cancelNotification()
            navModeController.refreshNavModeState()
        }
        updateStatusBarText()
        if (performed) {
            suggestionController.onContextReset()
        }
        notifyDebugKeyEvent(
            keyCode,
            event,
            "KEY_DOWN",
            origin = "ime_service",
            outputKeyCode = KeyEvent.KEYCODE_ENTER,
            outputKeyCodeName = if (performed) outputKeyCodeName else "${outputKeyCodeName}_rejected"
        )
        return true
    }

    private fun isShiftModifierActive(event: KeyEvent?): Boolean {
        return event?.isShiftPressed == true || shiftPressed || shiftOneShot || shiftLayerLatched
    }

    private fun isCtrlModifierActive(event: KeyEvent?): Boolean {
        return event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
    }

    private fun commitEnterNewline(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        inputConnection.finishComposingText()
        inputConnection.commitText("\n", 1)
        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableAutoCapitalize
        ) { updateStatusBarText() }
        suggestionController.onContextReset()
        notifyDebugKeyEvent(
            keyCode,
            event,
            "KEY_DOWN",
            origin = "ime_service",
            unicodeCharOverride = '\n'.code,
            outputKeyCode = null,
            outputKeyCodeName = outputKeyCodeName
        )
        return true
    }

    private fun performEnterEditorAction(
        keyCode: Int,
        actionId: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        consumeCtrlState: Boolean = false,
        consumeOnFailure: Boolean = false
    ): Boolean {
        inputConnection.finishComposingText()
        // Skip autocorrection when Enter is mapped to an IME action.
        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableAutoCapitalize
        ) { updateStatusBarText() }
        val performed = inputConnection.performEditorAction(actionId)
        if (performed || consumeOnFailure) {
            if (consumeCtrlState) {
                val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
                modifierStateController.clearCtrlState(resetPressedState = false)
                if (wasNavModeLatched) {
                    navModeController.cancelNotification()
                    navModeController.refreshNavModeState()
                }
                updateStatusBarText()
            }
            if (performed) {
                suggestionController.onContextReset()
            }
            notifyDebugKeyEvent(
                keyCode,
                event,
                "KEY_DOWN",
                origin = "ime_service",
                outputKeyCode = null,
                outputKeyCodeName = if (performed) {
                    "editor_action_$actionId"
                } else {
                    "editor_action_${actionId}_rejected"
                }
            )
        }
        return performed || consumeOnFailure
    }

    private fun performConfiguredAppEnterSend(
        keyCode: Int,
        info: EditorInfo?,
        inputConnection: InputConnection,
        event: KeyEvent?,
        consumeCtrlState: Boolean
    ): Boolean {
        return when (resolveAppEnterSendStrategy(info)) {
            SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION -> {
                val actionId = resolveEditorAction(info) ?: EditorInfo.IME_ACTION_SEND
                performEnterEditorAction(
                    keyCode = keyCode,
                    actionId = actionId,
                    inputConnection = inputConnection,
                    event = event,
                    consumeCtrlState = consumeCtrlState,
                    consumeOnFailure = true
                )
            }
            SettingsManager.ENTER_SEND_STRATEGY_CTRL_ENTER ->
                performCtrlEnterSend(keyCode, inputConnection, event, "app_ctrl_enter_send")
            SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER ->
                performPlainEnterSend(keyCode, inputConnection, event, "app_plain_enter_send")
            else -> consumeUnsupportedEnterSend(keyCode, event, "app_enter_send_unsupported")
        }
    }

    /**
     * Executes the field's editor action on Enter (e.g., Search/Go/Done) instead of inserting
     * a newline. Works for both single-line and multiline fields if they have an IME action configured.
     * Nav mode keeps its own Enter remapping, so we skip it here.
     */
    private fun handleEnterAsEditorAction(
        keyCode: Int,
        info: EditorInfo?,
        inputConnection: InputConnection?,
        event: KeyEvent?,
        isAutoCorrectEnabled: Boolean,
        ctrlActiveBeforePrelude: Boolean = false
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ENTER) {
            return false
        }

        val ic = inputConnection ?: return false
        val actionId = resolveEditorAction(info)
        val ctrlActiveForEnter = ctrlActiveBeforePrelude || isCtrlModifierActive(event)
        val symEnterSendActive =
            symTogglePendingOnKeyUp &&
                resolveAppEnterAdditionalSendShortcut(info) == SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER

        if (symEnterSendActive) {
            symChordUsedSinceKeyDown = true
            symTogglePendingOnKeyUp = false
            return performConfiguredAppEnterSend(
                keyCode = keyCode,
                info = info,
                inputConnection = ic,
                event = event,
                consumeCtrlState = false
            )
        }

        val appEnterBehavior = resolveAppEnterBehavior(info)
        // Search boxes and address bars always, and one-line fields where the app's Enter would
        // type a new line: Enter does the field's own action (search, go, next). A message box
        // that sends with Enter keeps Shift + Enter for a new line (WhatsApp's isn't marked
        // multi-line, but takes new lines)
        val enterMakesNewLine = appEnterBehavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE ||
            appEnterBehavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ||
            appEnterBehavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND
        if (actionId != null && !navModeController.isNavModeActive() &&
            (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO ||
                (enterMakesNewLine && !acceptsNewLines(info)))
        ) {
            return performEnterEditorAction(keyCode, actionId, ic, event, consumeCtrlState = ctrlActiveForEnter)
        }

        when (appEnterBehavior) {
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE -> {
                if (navModeController.isNavModeActive() && ctrlActiveForEnter) {
                    return performConfiguredAppEnterSend(
                        keyCode = keyCode,
                        info = info,
                        inputConnection = ic,
                        event = event,
                        consumeCtrlState = true
                    )
                }
                return commitEnterNewline(keyCode, ic, event, "app_enter_newline")
            }
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND -> {
                if (!isShiftModifierActive(event) && !ctrlActiveForEnter) {
                    return commitEnterNewline(keyCode, ic, event, "app_enter_newline")
                }
                return performConfiguredAppEnterSend(
                    keyCode = keyCode,
                    info = info,
                    inputConnection = ic,
                    event = event,
                    consumeCtrlState = ctrlActiveForEnter
                )
            }
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND -> {
                if (!ctrlActiveForEnter) {
                    return commitEnterNewline(keyCode, ic, event, "app_enter_newline")
                }
                return performConfiguredAppEnterSend(
                    keyCode = keyCode,
                    info = info,
                    inputConnection = ic,
                    event = event,
                    consumeCtrlState = true
                )
            }
            SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE -> {
                if (ctrlActiveForEnter) {
                    return performConfiguredAppEnterSend(
                        keyCode = keyCode,
                        info = info,
                        inputConnection = ic,
                        event = event,
                        consumeCtrlState = true
                    )
                }
                if (isShiftModifierActive(event)) {
                    return commitEnterNewline(keyCode, ic, event, "app_shift_enter_newline")
                }
                return performConfiguredAppEnterSend(
                    keyCode = keyCode,
                    info = info,
                    inputConnection = ic,
                    event = event,
                    consumeCtrlState = false
                )
            }
        }

        if (navModeController.isNavModeActive()) {
            return false
        }
        return actionId?.let { performEnterEditorAction(keyCode, it, ic, event) } ?: false
    }

    private fun notifyDebugKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        action: String,
        origin: String,
        unicodeCharOverride: Int? = null,
        outputKeyCode: Int? = null,
        outputKeyCodeName: String? = null
    ) {
        KeyboardEventTracker.notifyKeyEvent(
            keyCode = keyCode,
            event = event,
            action = action,
            origin = origin,
            altLatchActive = altLatchActive,
            altOneShot = altOneShot,
            shiftLatchActive = shiftLayerLatched,
            ctrlLatchActive = ctrlLatchActive,
            symPage = symPage,
            resolvedLayout = activeKeyboardLayoutName,
            unicodeCharOverride = unicodeCharOverride,
            outputKeyCode = outputKeyCode,
            outputKeyCodeName = outputKeyCodeName
        )
    }

    private fun resolveAltMappedUnicodeForDebug(
        keyCode: Int,
        altActive: Boolean
    ): Int? {
        if (!altActive) return null
        val mapped = alternateCharacterManager.getAltModifierMappings()[keyCode] ?: return null
        if (mapped.isEmpty()) return null
        return mapped.codePointAt(0)
    }

    private fun handleSuggestionsUpdated(suggestions: List<SuggestionResult>) {
        latestSuggestionResults = suggestions
        DebugCaptureStore.recordSuggestionsUpdated(suggestions)
        scheduleStatusBarTextUpdate()
    }

    private fun visibleSuggestionStrings(): List<String> {
        if (latestSuggestionResults.isEmpty()) return emptyList()

        val hasWordStartSuggestion = latestSuggestionResults.any {
            it.kind == SuggestionKind.NEXT_WORD || it.kind == SuggestionKind.STARTER_WORD
        }
        val forceWordStartCapital = if (hasWordStartSuggestion) {
            val modifierSnapshot = modifierStateController.snapshot()
            modifierSnapshot.capsLockEnabled ||
                modifierSnapshot.shiftPhysicallyPressed ||
                modifierSnapshot.shiftOneShot ||
                shiftLayerLatched
        } else {
            false
        }
        val locale = getLocaleFromSubtype()

        val words = latestSuggestionResults.map { suggestion ->
            when (suggestion.kind) {
                SuggestionKind.NEXT_WORD,
                SuggestionKind.STARTER_WORD -> recaseWordStartSuggestion(
                    suggestion.candidate,
                    forceWordStartCapital,
                    locale
                )
                SuggestionKind.CURRENT_WORD -> suggestion.candidate
            }
        }
        return withEmojiSuggestion(words)
    }

    /**
     * The emoji for the word being typed, in the third slot (the bar's left one): after the word
     * suggestions, or instead of the third.
     */
    private fun withEmojiSuggestion(words: List<String>): List<String> {
        if (!SettingsManager.getEmojiSuggestionsEnabled(this)) return words
        if (latestSuggestionResults.none { it.kind == SuggestionKind.CURRENT_WORD }) return words
        val word = suggestionController.currentWord()
        if (word.isBlank()) return words
        val index = EmojiSearchRepository.cachedSearchIndex(this) ?: run {
            requestEmojiSuggestionIndex()
            return words
        }
        val emoji = EmojiSearchRepository.suggestionFor(index, word) ?: return words
        // Keycaps (1️⃣) have a digit, so they would be taken for a word
        if (!EmojiSuggestion.isEmoji(emoji) || emoji in words) return words
        return words.take(2) + emoji
    }

    private var emojiSuggestionIndexRequested = false

    private fun requestEmojiSuggestionIndex() {
        if (emojiSuggestionIndexRequested) return
        emojiSuggestionIndexRequested = true
        expansionAssetScope.launch {
            runCatching { EmojiSearchRepository.getSearchIndex(this@PhysicalKeyboardInputMethodService) }
            emojiSuggestionIndexRequested = false
        }
    }

    private fun recaseWordStartSuggestion(
        candidate: String,
        forceLeadingCapital: Boolean,
        locale: Locale
    ): String {
        val firstLetterIndex = candidate.indexOfFirst { it.isLetter() }
        if (firstLetterIndex < 0) return candidate

        val firstLetter = candidate[firstLetterIndex]
        val replacement = if (forceLeadingCapital) {
            firstLetter.titlecase(locale)
        } else {
            firstLetter.lowercase(locale)
        }
        return candidate.substring(0, firstLetterIndex) +
            replacement +
            candidate.substring(firstLetterIndex + 1)
    }

    private fun autoCapContextKey(): String? {
        val ic = currentInputConnection ?: return null
        return try {
            val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: return null
            val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
            "$before|$after"
        } catch (_: Exception) {
            null
        }
    }

    private fun suppressAutoCapAtCurrentCursor() {
        suppressedAutoCapContextKey = autoCapContextKey()
    }

    private fun suppressAutoCapRenderingAtCursorIfNeeded() {
        if (!SettingsManager.getAutoCapitalizeRespectManualShiftOff(this)) {
            clearAutoCapSuppression()
            return
        }
        if (
            AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                context = this,
                inputConnection = currentInputConnection,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize
            )
        ) {
            suppressAutoCapAtCurrentCursor()
        }
    }

    private fun clearAutoCapSuppression() {
        suppressedAutoCapContextKey = null
    }

    private fun isAutoCapSuppressedAtCursor(): Boolean {
        val suppressed = suppressedAutoCapContextKey ?: return false
        return autoCapContextKey() == suppressed
    }

    private fun requestAutoCapShiftOneShot(): Boolean {
        if (isAutoCapSuppressedAtCursor()) return false
        return modifierStateController.requestShiftOneShotFromAutoCap()
    }

    /**
     * Initializes the input context for a field.
     * This method contains all common initialization logic that must run
     * regardless of whether input view or candidates view is shown.
     */
    private fun initializeInputContext(restarting: Boolean) {
        if (restarting) {
            return
        }
        
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        val canCheckAutoCapitalize = isEditable && !shouldDisableAutoCapitalize
        
        if (!isReallyEditable) {
            isInputViewActive = false
            
            if (canCheckAutoCapitalize) {
                AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
                    this,
                    currentInputConnection,
                    shouldDisableAutoCapitalize,
                    enableShift = { requestAutoCapShiftOneShot() },
                    disableShift = { modifierStateController.consumeShiftOneShot() },
                    onUpdateStatusBar = { updateStatusBarText() }
                )
            }
            return
        }
        
        isInputViewActive = true
        
        enforceSmartFeatureDisabledState()
        
        if (ctrlLatchFromNavMode && ctrlLatchActive) {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                navModeController.exitNavMode()
            }
        }
        
        AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
            this,
            currentInputConnection,
            shouldDisableAutoCapitalize,
            enableShift = { requestAutoCapShiftOneShot() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = { updateStatusBarText() }
        )
        
        symLayoutController.restoreSymPageIfNeeded { updateStatusBarText() }
        
        alternateCharacterManager.reloadLongPressThreshold()
        alternateCharacterManager.resetTransientState()
    }
    
    private fun enforceSmartFeatureDisabledState() {
        // The candidates surface also contains the keyboard's hardware-keyboard status bar.
        // Individual smart features hide their own content; the surface itself stays visible.
        deactivateVariations()
    }
    
    /**
     * Loads keyboard layout using the central resolver (auto-by-locale or manual override).
     */
    private fun loadKeyboardLayout() {
        val layoutName = try {
            val imm = getSystemService(InputMethodManager::class.java)
            val currentSubtype = imm.currentInputMethodSubtype
            AdditionalSubtypeUtils.resolveInputStyleLayout(assets, this, currentSubtype)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting layout from subtype, using preferences", e)
            SettingsManager.getKeyboardLayout(this)
        }
        activeKeyboardLayoutName = layoutName
        val layout = LayoutMappingRepository.loadLayout(assets, layoutName, this)
        Log.d(TAG, "Keyboard layout loaded: $layoutName")
    }
    
    /**
     * Gets the character from the selected keyboard layout for a given keyCode and shift state.
     * If the keyCode is mapped in the layout, returns that character.
     * Otherwise, returns the character from the event (if available).
     * This ensures that keyboard layouts work correctly regardless of Android's system layout settings.
     */
    private fun getCharacterFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): Char? {
        // First, try to get the character from the selected layout
        val layoutChar = LayoutMappingRepository.getCharacter(keyCode, isShift)
        if (layoutChar != null) {
            return layoutChar
        }
        // If not mapped in layout, fall back to event's unicode character
        if (event != null && event.unicodeChar != 0) {
            return event.unicodeChar.toChar()
        }
        return null
    }

    private fun switchToLayout(layoutName: String, showToast: Boolean) {
        activeKeyboardLayoutName = layoutName
        LayoutMappingRepository.loadLayout(assets, layoutName, this)
        variationStateController = VariationStateController(
            VariationRepository.loadVariations(assets, this, activeKeyboardLayoutName)
        )
        updateStatusBarText()

        // Update suggestion engine's keyboard layout for proximity-based ranking
        suggestionController?.updateKeyboardLayout(layoutName)
    }

    private fun isVietnameseTelexActive(): Boolean {
        return VietnameseTelexProcessor.isActiveForLayout(activeKeyboardLayoutName)
    }

    private fun handleVietnameseTelexKey(keyCode: Int, event: KeyEvent?, inputConnection: InputConnection?): Boolean {
        if (!isVietnameseTelexActive()) return false
        val ic = inputConnection ?: return false
        if (event == null || event.repeatCount > 0) return false
        if (!LayoutMappingRepository.isMapped(keyCode)) return false

        val char = LayoutMappingRepository.getCharacterStringWithModifiers(
            keyCode = keyCode,
            isShiftPressed = event.isShiftPressed,
            capsLockEnabled = capsLockEnabled,
            shiftOneShot = shiftOneShot
        )
        if (char.length != 1) return false

        val rewrite = VietnameseTelexProcessor.rewrite(
            textBeforeCursor = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty(),
            keyChar = char[0]
        ) ?: return false

        ic.finishComposingText()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(rewrite.replaceCount, 0)
        ic.commitText(rewrite.replacement, 1)
        ic.endBatchEdit()

        if (shiftOneShot) {
            modifierStateController.consumeShiftOneShot()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatusBarText()
        }, CURSOR_UPDATE_DELAY)
        return true
    }

    /**
     * Cycles to the next enabled input method subtype (language).
     * Prevents multiple simultaneous switches to avoid dictionary loading conflicts.
     */
    private fun cycleToNextLanguage() {
        if (isLanguageSwitchInProgress) {
            Log.d(TAG, "Language switch already in progress, ignoring request")
            return
        }

        isLanguageSwitchInProgress = true
        try {
            val switched = SubtypeCycler.cycleToNextSubtype(
                context = this,
                imeServiceClass = PhysicalKeyboardInputMethodService::class.java,
                assets = assets,
                showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            )

            // Reset flag; keep a short delay when a switch happened to avoid rapid repeats
            val delayMs = if (switched) 300L else 0L
            uiHandler.postDelayed({ isLanguageSwitchInProgress = false }, delayMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error cycling language", e)
            isLanguageSwitchInProgress = false
        }
    }
    
    private fun showPowerShortcutToast(message: String) {
        uiHandler.post {
            val now = System.currentTimeMillis()
            val sameText = lastLayoutToastText == message
            val sinceLast = now - lastLayoutToastTime
            
            if (!sameText || sinceLast > 1000) {
                lastLayoutToastText = message
                lastLayoutToastTime = now
                powerShortcutToast?.cancel()
                powerShortcutToast = android.widget.Toast.makeText(
                    applicationContext,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                )
                powerShortcutToast?.show()
            }
        }
    }

    private fun handleMultiTapCommit(
        keyCode: Int,
        mapping: LayoutMapping,
        useUppercase: Boolean,
        inputConnection: InputConnection?,
        allowLongPress: Boolean
    ): Boolean {
        val ic = inputConnection ?: return false
        val tapResult = multiTapController.handleTap(keyCode, mapping, useUppercase, ic)
        if (tapResult.handled && allowLongPress) {
            tapResult.committedText?.let { committedText ->
                alternateCharacterManager.scheduleLongPressOnly(keyCode, ic, committedText)
            }
        }
        if (tapResult.handled) {
            if (SettingsManager.isSuggestionDebugLoggingEnabled(this)) {
                Log.d(TAG, "multiTap commit text='${tapResult.committedText}' replaced=${tapResult.replacedInWindow}")
            }
            // Prevent onUpdateSelection from re-triggering suggestion recalculation for the same commit.
            markSelectionUpdateSkipAfterCommit()
            tapResult.committedText?.let { committedText ->
                if (tapResult.replacedInWindow) {
                    // Replace the last character in the tracker to stay in sync with the text field.
                    suggestionController.currentSuggestions() // touch to keep listener consistent (noop)
                    suggestionController.onCharacterCommitted("\b$committedText", inputConnection)
                } else {
                    suggestionController.onCharacterCommitted(committedText, inputConnection)
                }
            }
        }
        return tapResult.handled
    }
    
    private fun reloadNavModeMappings() {
        try {
            ctrlKeyMap.clear()
            val assets = assets
            ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
            Log.d(TAG, "Nav mode mappings reloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading nav mode mappings", e)
        }
    }
    
    /**
     * Checks if a keycode corresponds to an alphabetic key (A-Z).
     * Returns true only for alphabetic keys, false for all others (modifiers, volume, etc.).
     */
    private fun isAlphabeticKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_Z -> true
            else -> false
        }
    }

    private fun isShortcutKey(keyCode: Int): Boolean =
        isAlphabeticKey(keyCode) ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_DEL ||
                keyCode == KeyEvent.KEYCODE_SPACE

    private fun updateModifierTapLatchSettings() {
        if (!::modifierStateController.isInitialized) {
            return
        }
        modifierStateController.shiftTapLatches = SettingsManager.getShiftTapLatches(this)
        modifierStateController.altTapLatches = SettingsManager.getAltTapLatches(this)
        modifierStateController.ctrlTapLatches = SettingsManager.getCtrlTapLatches(this)
        modifierStateController.shiftDoubleTapLocks = SettingsManager.getShiftDoubleTapLocks(this)
        modifierStateController.altDoubleTapLocks = SettingsManager.getAltDoubleTapLocks(this)
        modifierStateController.ctrlDoubleTapLocks = SettingsManager.getCtrlDoubleTapLocks(this)
    }

    override fun onCreate() {
        super.onCreate()
        ClicksAccessibilityKeyBridge.register(this)
        // Root: the keyboard backlight follows the screen, when set (the Root page)
        it.palsoftware.pastiera.adb.KeyboardBacklight.start(this)
        // A code arriving while typing is offered straight away
        // A newer code replaces the chip too when the bar shows one already
        it.palsoftware.pastiera.otp.OneTimeCodes.onNewCode = { if (isInputViewShown || isInputViewActive || pasteSuggestionShown) offerOneTimeCode() }
        EmojiCompatSupport.ensureLoaded(this)
        lastSystemLocalesSignature = resources.configuration.locales.toLanguageTags()
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
        physicalKeyboardProfileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(this)

        // Clear legacy nav mode notification since we now rely on the status icon only.
        NotificationHelper.cancelNavModeNotification(this)

        modifierStateController = ModifierStateController(DOUBLE_TAP_THRESHOLD)
        updateModifierTapLatchSettings()
        navModeController = NavModeController(this, modifierStateController)
        navModeController.setOnNavModeChangedListener { isActive ->
            updateNavModeStatusIcon(isActive)
        }
        inputEventRouter = InputEventRouter(this, navModeController).apply {
            onCommitText = { markSelectionUpdateSkipAfterCommit() }
        }
        typingSoundPlayer = TypingSoundPlayer(this).apply { reload() }
        textInputController = TextInputController(
            context = this,
            modifierStateController = modifierStateController,
            doubleTapThreshold = DOUBLE_TAP_THRESHOLD
        )
        autoCorrectionManager = AutoCorrectionManager(this)
        val suggestionDebugLogging = SettingsManager.isSuggestionDebugLoggingEnabled(this)
        
        // Get locale from current IME subtype
        val initialLocale = getLocaleFromSubtype()
        
        suggestionController = SuggestionController(
            context = this,
            assets = assets,
            settingsProvider = { getSuggestionSettings() },
            isEnabled = { SettingsManager.isExperimentalSuggestionsEnabled(this) },
            debugLogging = suggestionDebugLogging,
            onSuggestionsUpdated = { suggestions -> handleSuggestionsUpdated(suggestions) },
            currentLocale = initialLocale,
            keyboardLayoutProvider = { SettingsManager.getKeyboardLayout(this) },
            activeSuggestionLocalesProvider = { getAdditionalSuggestionLocalesForActiveInputStyle() }
        )
        inputEventRouter.suggestionController = suggestionController
        // The phone's own spell checker (Gboard's, say) as a second opinion on misspelt words
        val phoneSpellChecker = it.palsoftware.pastiera.spellcheck.PhoneSpellChecker(this)
        suggestionController.externalSuggestions = { word, locale, onResult -> phoneSpellChecker.suggest(word, locale, onResult) }
        suggestionController.textAfterCursorProvider = { currentInputConnection?.getTextAfterCursor(32, 0) }
        // The spell checker reads the loaded dictionary instead of loading its own
        it.palsoftware.pastiera.spellcheck.PastieraSpellCheckerService.keyboardController =
            java.lang.ref.WeakReference(suggestionController)
        
        // Preload dictionary in background so it's ready when user focuses a field
        suggestionController.preloadDictionary()

        // Initialize clipboard history manager first (needed by candidatesBarController)
        clipboardHistoryManager = ClipboardHistoryManager(this)
        clipboardHistoryManager.onCreate()

        candidatesBarController = CandidatesBarController(this, clipboardHistoryManager, assets, PhysicalKeyboardInputMethodService::class.java)
        val snippetExpansionSource = SnippetExpansionSource {
            SettingsManager.getSnippets(this)
        }
        emojiShortcodeSource = EmojiShortcodeSource(assets)
        symbolShortcodeSource = SymbolShortcodeSource(assets)
        textExpansionController = TextExpansionController(
            context = this,
            handler = Handler(Looper.getMainLooper()),
            inputConnectionProvider = { currentInputConnection },
            inputContextProvider = { inputContextState },
            isSelectionCollapsedProvider = { !editorHasActiveSelection },
            anchorProvider = { window?.window?.decorView },
            configsProvider = {
                listOf(
                    ExpansionRuntimeConfig(
                        source = snippetExpansionSource,
                        triggerKind = ExpansionTriggerKind.PREFIX,
                        enabled = SettingsManager.getSnippetsEnabled(this),
                        prefix = SettingsManager.getSnippetsPrefix(this).first(),
                        presentation = SettingsManager.getSnippetsPresentation(this),
                        activationPolicy = SettingsManager.getSnippetsActivationPolicy(this)
                    ),
                    ExpansionRuntimeConfig(
                        source = emojiShortcodeSource,
                        triggerKind = ExpansionTriggerKind.COLON_SHORTCODE,
                        enabled = SettingsManager.getEmojiShortcodesEnabled(this),
                        presentation = SettingsManager.getEmojiSymbolsPresentation(this),
                        activationPolicy = SettingsManager.getEmojiSymbolsActivationPolicy(this),
                        exactOnClose = SettingsManager.getEmojiSymbolsExactOnClose(this)
                    ),
                    ExpansionRuntimeConfig(
                        source = symbolShortcodeSource,
                        triggerKind = ExpansionTriggerKind.COLON_SHORTCODE,
                        enabled = SettingsManager.getSymbolShortcodesEnabled(this),
                        presentation = SettingsManager.getEmojiSymbolsPresentation(this),
                        activationPolicy = SettingsManager.getEmojiSymbolsActivationPolicy(this),
                        exactOnClose = SettingsManager.getEmojiSymbolsExactOnClose(this)
                    )
                )
            },
            showSuggestionBar = { labels, onSelected ->
                candidatesBarController.showExpansionSuggestions(labels, onSelected)
            },
            clearSuggestionBar = { candidatesBarController.clearExpansionSuggestions() },
            requestSurfaceUpdate = { refreshBarChips() },
            onCommitted = {
                markSelectionUpdateSkipAfterCommit()
                suggestionController.onContextReset()
                suggestionController.readInitialContext(currentInputConnection)
                updateStatusBarText()
            }
        )
        prepareEnabledExpansionAssets()
        candidatesBarController.onAddUserWord = { word ->
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            suggestionController.addUserWord(word)
            suggestionController.clearPendingAddWord()
            updateStatusBarText()
        }
        candidatesBarController.onAddUserWordSubstitutionRequested = { word ->
            showAddSubstitutionDialog(word)
        }
        candidatesBarController.onSuggestionCommitted = {
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            if (shiftOneShot) {
                modifierStateController.consumeShiftOneShot()
            }
            variationInteractedDuringHold = true
            suggestionController.readInitialContext(currentInputConnection)
            updateStatusBarText()
        }
        candidatesBarController.onHideSuggestion = { suggestion ->
            suggestionController.dismissSuggestion(suggestion, hardDeleteUserWord = false)
            updateStatusBarText()
            NotificationHelper.triggerHapticFeedback(this)
        }
        candidatesBarController.onDeleteUserSuggestion = { suggestion ->
            suggestionController.dismissSuggestion(suggestion, hardDeleteUserWord = true)
            updateStatusBarText()
            NotificationHelper.triggerHapticFeedback(this)
        }
        candidatesBarController.canDeleteUserSuggestion = { suggestion ->
            suggestionController.userDictionarySnapshot().any { entry ->
                entry.word.equals(suggestion, ignoreCase = true)
            }
        }
        candidatesBarController.onLanguageSwitchRequested = {
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            cycleToNextLanguage()
        }

        // Register listener for variation selection (both controllers)
        val variationListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onBoundaryTextRequested(
                variation: String,
                inputConnection: InputConnection
            ): Boolean {
                return handleBoundaryTextBeforeCommit(variation, inputConnection)
            }

            override fun onVariationSelected(variation: String) {
                val keepLayerLatchedAfterVariation =
                    SettingsManager.isStaticVariationBarLayerStickyEnabled(this@PhysicalKeyboardInputMethodService)
                val hasLatchedLayer = shiftLayerLatched || altModifierLayerLatched
                if (hasLatchedLayer && !keepLayerLatchedAfterVariation) {
                    shiftLayerLatched = false
                    altModifierLayerLatched = false
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    modifierStateBeforeHold = null
                }
                variationInteractedDuringHold = true
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        candidatesBarController.onVariationSelectedListener = variationListener

        // Register listener for cursor movement (both controllers)
        val cursorListener = {
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            updateStatusBarText()
        }
        candidatesBarController.onCursorMovedListener = cursorListener

        // Register listener for speech recognition
        candidatesBarController.onSpeechRecognitionRequested = {
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            startSpeechRecognition()
        }
        // Register listener for clipboard page
        candidatesBarController.onClipboardRequested = {
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            ensureImeSurfaceVisible()
            // Toggle clipboard as SYM page 3
            symLayoutController.openClipboardPage()
            updateStatusBarText()
        }
        // Register listener for emoji picker page
        candidatesBarController.onEmojiPickerRequested = {
            if (shiftLayerLatched || altModifierLayerLatched) {
                shiftLayerLatched = false
                altModifierLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            ensureImeSurfaceVisible()
            // Toggle emoji picker as SYM page 4
            symLayoutController.openEmojiPickerPage()
            updateStatusBarText()
        }
        candidatesBarController.onEmojiPageRequested = {
            ensureImeSurfaceVisible()
            symLayoutController.openEmojiPage()
            updateStatusBarText()
        }
        // Register listener for symbols page
        candidatesBarController.onSymbolsPageRequested = {
            ensureImeSurfaceVisible()
            // Toggle symbols as SYM page 2
            symLayoutController.openSymbolsPage()
            updateStatusBarText()
        }
        candidatesBarController.onSoftwareKeyboardSymToggleRequested = {
            ensureImeSurfaceVisible()
            symLayoutController.toggleSymPage()
            updateStatusBarText()
        }
        candidatesBarController.onSymCloseRequested = {
            if (symLayoutController.closeSymPage()) {
                updateStatusBarText()
            }
        }
        candidatesBarController.onEmojiLayerSearchRequested = {
            // Emoji layer's search button: the picker, with its search ready for typing
            candidatesBarController.requestEmojiPickerSearch()
            symLayoutController.openEmojiPickerPage()
            updateStatusBarText()
        }
        candidatesBarController.onEmojiLayerGifRequested = {
            // Emoji layer's GIF key: the picker, in GIF search
            candidatesBarController.requestEmojiPickerGifs()
            symLayoutController.openEmojiPickerPage()
            updateStatusBarText()
        }
        candidatesBarController.onGifChosen = { gif -> sendGif(gif) }
        candidatesBarController.onSymbolSearchRequested = {
            // A symbols page's search: the picker, in symbol search
            candidatesBarController.requestSymbolSearch()
            symLayoutController.openEmojiPickerPage()
            updateStatusBarText()
        }
        candidatesBarController.onKaomojiBackTapped = {
            if (symLayoutController.previousPage()) updateStatusBarText()
        }
        candidatesBarController.onLayerNextTapped = {
            if (symLayoutController.nextPage()) updateStatusBarText()
        }
        candidatesBarController.onKaomojiKeyTapped = {
            if (symLayoutController.kaomojiKeyPressed()) updateStatusBarText()
        }
        candidatesBarController.onEmojiVariantsRequested = { emoji ->
            if (symLayoutController.showEmojiVariants(emoji)) updateStatusBarText()
        }
        candidatesBarController.onEmojiLayerTyped = {
            if (symLayoutController.closeEmojiVariants()) updateStatusBarText()
        }
        candidatesBarController.onKaomojiSearchRequested = {
            candidatesBarController.requestKaomojiSearch()
            symLayoutController.openEmojiPickerPage()
            updateStatusBarText()
        }
        candidatesBarController.onEmojiLayerRecentsToggled = {
            if (symLayoutController.toggleEmojiLayerRecents()) {
                updateStatusBarText()
            }
        }
        candidatesBarController.onEmojiPickerSearchPanelToggled = {
            // The picker's search panel state is not part of the rendered snapshot; force a
            // re-render so the picker moves to its popup surface while searching.
            lastRenderedStatusSnapshot = null
            updateStatusBarText()
        }
        candidatesBarController.onUndoRequested = {
            variationInteractedDuringHold = true
            if (!undoFromHistory(redo = false)) sendCtrlShortcut(KeyEvent.KEYCODE_Z)
        }
        candidatesBarController.onRedoRequested = {
            variationInteractedDuringHold = true
            if (!undoFromHistory(redo = true)) sendCtrlShortcut(KeyEvent.KEYCODE_Y)
        }
        candidatesBarController.onSoftwareKeyboardKeyPressed = { keyCode ->
            typingSoundPlayer.play(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardModifierKeyDown = { keyCode ->
            handleSoftwareKeyboardModifierKeyDown(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardModifierKeyUp = { keyCode ->
            handleSoftwareKeyboardModifierKeyUp(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardKeyStroke = { keyCode, _ ->
            handleSoftwareKeyboardKeyStroke(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardShiftTapped = {
            val wasShiftOneShot = modifierStateController.shiftOneShot
            val downResult = modifierStateController.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
            if (wasShiftOneShot && !modifierStateController.shiftOneShot) {
                suppressAutoCapRenderingAtCursorIfNeeded()
            }
            val upResult = modifierStateController.handleShiftKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
            if (
                downResult.shouldUpdateStatusBar ||
                downResult.shouldRefreshStatusBar ||
                upResult.shouldUpdateStatusBar ||
                upResult.shouldRefreshStatusBar
            ) {
                updateStatusBarText()
            }
        }
        candidatesBarController.onSoftwareKeyboardNonShiftInteraction = {
            modifierStateController.registerNonModifierKey()
        }
        candidatesBarController.onSoftwareKeyboardTextInput = { text, inputConnection, snapshot ->
            val ic = inputConnection ?: currentInputConnection
            val consumedShiftOneShot = text.length == 1 &&
                text[0].isLetter() &&
                modifierStateController.consumeShiftOneShot()
            val handled = handleSoftwareKeyboardTextInput(text, ic, snapshot)
            if (consumedShiftOneShot) {
                updateStatusBarText()
            }
            handled
        }
        candidatesBarController.onSoftwareKeyboardBoundaryTextInput = { text, inputConnection ->
            handleBoundaryTextBeforeCommit(text, inputConnection)
        }
        candidatesBarController.onMinimalUiToggleRequested = {
            keyboardVisibilityController.togglePastierinaMode()
        }
        candidatesBarController.onSoftwareKeyboardModeToggleRequested = {
            toggleSoftwareKeyboardModeFromStatusBar()
        }
        val postClipboardBadgeUpdate: () -> Unit = {
            val count = clipboardHistoryManager.getHistorySize()
            uiHandler.post {
                candidatesBarController.updateClipboardCount(count)
            }
        }
        clipboardHistoryManager.setHistoryChangeListener(object : ClipboardDao.Listener {
            override fun onClipInserted(position: Int) {
                postClipboardBadgeUpdate()
            }

            override fun onClipsRemoved(position: Int, count: Int) {
                postClipboardBadgeUpdate()
            }

            override fun onClipMoved(oldPosition: Int, newPosition: Int) {
                postClipboardBadgeUpdate()
            }
        })
        clipboardHistoryManager.addAccessStateListener {
            uiHandler.post {
                candidatesBarController.updateClipboardCount(clipboardHistoryManager.getHistorySize())
            }
        }
        alternateCharacterManager = AlternateCharacterManager(
            assets = assets,
            prefs = prefs,
            context = this,
            activeLayoutNameProvider = { activeKeyboardLayoutName }
        )
        alternateCharacterManager.reloadSymMappings() // Load custom mappings for page 1 if present
        alternateCharacterManager.reloadSymMappings2() // Load custom mappings for page 2 if present
        alternateCharacterManager.onBoundaryTextRequested = { text, inputConnection ->
            handleBoundaryTextBeforeCommit(text, inputConnection)
        }
        // Register callback to be notified when an Alt character is inserted after long press.
        // Variations are updated automatically by updateStatusBarText().
        alternateCharacterManager.onAltCharInserted = { char ->
            DeferredPunctuationSpaceTracker.onTextCommitted(this, char.toString())
            updateStatusBarText()
            val ic = currentInputConnection
            // Apostrophe is never a boundary: use centralized punctuation set.
            val punctuationSet = it.palsoftware.pastiera.core.Punctuation.BOUNDARY
            val normalizedChar = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(char)
            if (normalizedChar == '\'') {
                inputEventRouter.handleInWordApostrophe(ic, pendingApostrophe = false)
            } else if (normalizedChar.isLetter()) {
                // Variations-mode long-press replaces a letter: keep suggestion context in sync.
                markSelectionUpdateSkipAfterCommit()
                suggestionController.onCharacterCommitted(normalizedChar.toString(), ic)
            } else if (normalizedChar !in punctuationSet) {
                // Non-boundary Alt long-press (e.g., numbers/symbols) resets current word tracking
                suggestionController.onContextReset()
            }
        }
        // Track normal characters committed via Alt short press (no long press triggered)
        alternateCharacterManager.onNormalCharCommitted = { text ->
            if (::suggestionController.isInitialized) {
                // Avoid double-tracking plain letters already handled by the main pipeline.
                val ch = text.firstOrNull()
                val shouldTrack = ch == null || !ch.isLetter()
                if (shouldTrack) {
                    // Avoid double suggestion dispatch: skip the immediate selection update after commit.
                    markSelectionUpdateSkipAfterCommit()
                    suggestionController.onCharacterCommitted(text, currentInputConnection)
                }
            }
        }
        symLayoutController = SymLayoutController(this, prefs, alternateCharacterManager)
        // Emoji layer's GIF key (physical): the same as its on-screen GIF key
        symLayoutController.onEmojiLayerGifKey = {
            uiHandler.post { candidatesBarController.onEmojiLayerGifRequested?.invoke() }
        }
        // Type to search: the emoji layer's or a symbols page's search, already holding the letter
        symLayoutController.onSearchKey = { target ->
            uiHandler.post {
                when (target) {
                    SymLayoutController.SearchTarget.EMOJI_LAYER -> {
                        candidatesBarController.requestEmojiPickerSearch()
                        symLayoutController.openEmojiPickerPage()
                    }
                    SymLayoutController.SearchTarget.SYMBOLS -> {
                        candidatesBarController.requestSymbolSearch()
                        symLayoutController.openEmojiPickerPage()
                    }
                    SymLayoutController.SearchTarget.KAOMOJI -> {
                        candidatesBarController.requestKaomojiSearch()
                        symLayoutController.openEmojiPickerPage()
                    }
                    SymLayoutController.SearchTarget.PICKER ->
                        candidatesBarController.focusEmojiPickerSearch()
                }
                updateStatusBarText()
            }
        }
        symLayoutController.onTypeToSearch = { emoji, text ->
            uiHandler.post {
                // Lower case, as the rest of the search is typed
                if (emoji) {
                    candidatesBarController.requestEmojiPickerSearch(text.lowercase())
                } else {
                    candidatesBarController.requestSymbolSearch(text.lowercase())
                }
                symLayoutController.openEmojiPickerPage()
                updateStatusBarText()
            }
        }
        keyboardVisibilityController = KeyboardVisibilityController(
            context = this,
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { isInputViewActive },
            hasActiveTextField = { inputContextState.isEditable },
            isNavModeLatched = { ctrlLatchFromNavMode },
            currentInputConnection = { currentInputConnection },
            isInputViewShown = { isInputViewShown },
            renderedSurface = {
                when {
                    candidatesBarController.isInputViewActuallyRendered() ->
                        KeyboardVisibilityController.RenderedSurface.FULL_INPUT_VIEW
                    candidatesBarController.isCandidatesViewActuallyRendered() ->
                        KeyboardVisibilityController.RenderedSurface.CANDIDATES_VIEW
                    else -> KeyboardVisibilityController.RenderedSurface.HIDDEN
                }
            },
            setRequestedInputViewShown = { shown -> requestedInputViewShown = shown },
            attachInputView = { view -> setInputView(view) },
            attachCandidatesView = { view -> setCandidatesView(view) },
            setCandidatesSurfaceActive = candidatesBarController::setCandidatesSurfaceActive,
            setCandidatesViewShown = { shown -> setCandidatesViewShown(shown) },
            synchronizeCandidatesContainerVisibility = ::synchronizeCandidatesContainerVisibility,
            postToUi = { action -> uiHandler.post(action) },
            postToUiDelayed = { delayMs, action -> uiHandler.postDelayed(action, delayMs) },
            showInputWindow = { showInput -> showWindow(showInput) },
            hideInputWindow = { hideWindow() },
            requestHideInputView = { requestHideSelf(0) },
            requestShowInputView = ::requestKeyboardInputView,
            trace = ::traceImeVisibility,
            refreshStatusBar = {
                invalidateRenderedStatusSnapshot()
                refreshStatusBar()
            },
            isHiddenForApp = { hiddenAppSurfaceBlocked() }
        )
        keyboardVisibilityController.onMinimalModeToggled = ::onMinimalModeToggled
        candidatesBarController.onExtraKeysRequested = { toggleExtraKeys() }
        it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysToggle.handler = { uiHandler.post { toggleExtraKeys() } }
        inputManager = getSystemService(InputManager::class.java)
        InputDevice.getDeviceIds().forEach { deviceId ->
            InputDevice.getDevice(deviceId)
                ?.takeIf(DeviceSpecific::isClicksPowerKeyboard)
                ?.let { connectedClicksInputDeviceIds += deviceId }
        }
        lastObservedAutoSoftwareKeyboardMode = SoftwareKeyboardAutoDetector.resolve(this)
        inputManager?.registerInputDeviceListener(inputDeviceListener, uiHandler)
        launcherShortcutController = LauncherShortcutController(this)
        // Callbacks that handle nav mode during Power Shortcuts
        launcherShortcutController.setNavModeCallbacks(
            exitNavMode = { navModeController.exitNavMode() },
            enterNavMode = { navModeController.enterNavMode() }
        )

        // Initialize keyboard layout
        loadKeyboardLayout()
        
        // Initialize nav mode mappings file if needed
        it.palsoftware.pastiera.SettingsManager.initializeNavModeMappingsFile(this)
        ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
        variationStateController = VariationStateController(
            VariationRepository.loadVariations(assets, this, activeKeyboardLayoutName)
        )
        keyboardVisibilityController.syncStatusBarPresentationModeFromSettings()
        
        // Load auto-correction rules
        AutoCorrector.loadCorrections(assets, this)
        
        // Register additional subtypes (custom input styles)
        AdditionalSubtypeUtils.registerAdditionalSubtypes(this)
        
        // Trackpad gestures detector (instantiated early to avoid late-init issues in listener)
        Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Building initial trackpad gesture detector...")
        trackpadGestureDetector = buildTrackpadGestureDetector()
        Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Initial detector built")

        // Register listener for SharedPreferences changes
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            Log.d(TRACKPAD_DEBUG_TAG, "SharedPrefs changed: key=$key")
            if (key == it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.PREF_SWITCH_BY_APP) {
                alternateCharacterManager.setEmojiLayerOverride(
                    if (it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.switchByApp(this)) {
                        it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.forApp(currentInputEditorInfo?.packageName)?.let { profile -> it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.layerMappings(this, profile) }
                    } else null
                )
                Handler(Looper.getMainLooper()).post { updateStatusBarText() }
            } else if (key != null && key.startsWith("led_")) {
                // LED colours: repaint the LEDs even though no modifier changed
                Handler(Looper.getMainLooper()).post {
                    invalidateRenderedStatusSnapshot()
                    updateStatusBarText()
                }
            } else if (key == it.palsoftware.pastiera.data.mappings.CustomDeviceSymProfiles.PREF_KEY ||
                key == it.palsoftware.pastiera.data.mappings.CustomDeviceSymProfiles.PREF_CHOICE
            ) {
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                Handler(Looper.getMainLooper()).post { updateStatusBarText() }
            } else if (key == "sym_mappings_custom") {
                Log.d(TAG, "SYM mappings page 1 changed, reloading...")
                // Reload SYM mappings for page 1
                alternateCharacterManager.reloadSymMappings()
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_mappings_page2_custom") {
                Log.d(TAG, "SYM mappings page 2 changed, reloading...")
                // Reload SYM mappings for page 2
                alternateCharacterManager.reloadSymMappings2()
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_pages_config") {
                Log.d(TAG, "SYM pages configuration changed, refreshing status bar...")
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (
                key == SettingsManager.KEY_ALT_MODIFIER_BINDING ||
                key == LegacySettings.KEY_ALT_CHARACTER_LAYER_BINDING
            ) {
                SettingsManager.getAltModifierBinding(this)
                Log.d(TAG, "Alt modifier binding changed, reloading mappings...")
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                Handler(Looper.getMainLooper()).post { updateStatusBarText() }
            } else if (key == "clear_alt_on_space") {
                clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
            } else if (key == "emoji_shortcodes_enabled" || key == "symbol_shortcodes_enabled") {
                prepareEnabledExpansionAssets()
            } else if (key == "shift_tap_latches" || key == "alt_tap_latches" || key == "ctrl_tap_latches" ||
                key?.endsWith("_double_tap_locks") == true) {
                updateModifierTapLatchSettings()
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "physical_keyboard_profile_override") {
                Log.d(TAG, "Physical keyboard profile override changed, reloading Device SYM and Alt modifier mappings...")
                physicalKeyboardProfileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(this)
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "physical_keyboard_currency_symbol") {
                Log.d(TAG, "Physical keyboard currency symbol changed, reloading Device SYM and Alt modifier mappings...")
                alternateCharacterManager.reloadModifierAndDeviceSymMappings()
                // The symbols page's defaults carry it too
                alternateCharacterManager.reloadSymMappings2()
            } else if (key != null && (key.startsWith("auto_correct_custom_") || key == "auto_correct_enabled_languages")) {
                Log.d(TAG, "Auto-correction rules changed, reloading...")
                // Reload auto-corrections (including new custom languages)
                AutoCorrector.loadCorrections(assets, this)
            } else if (key == "variations_updated") {
                Log.d(TAG, "Variations file changed, reloading...")
                // Reload variations from file
                variationStateController = VariationStateController(
                    VariationRepository.loadVariations(assets, this, activeKeyboardLayoutName)
                )
                candidatesBarController.invalidateStaticVariations()
                // Update status bar to reflect new variations
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "nav_mode_mappings_updated") {
                Log.d(TAG, "Nav mode mappings changed, reloading...")
                // Reload nav mode key mappings
                reloadNavModeMappings()
            } else if (key == "keyboard_layout") {
                if (suppressNextLayoutReload) {
                    Log.d(TAG, "Keyboard layout change observed, reload suppressed")
                    suppressNextLayoutReload = false
                } else {
                    Log.d(TAG, "Keyboard layout changed, reloading...")
                    val layoutName = SettingsManager.getKeyboardLayout(this)
                    switchToLayout(layoutName, showToast = false)
                }
            } else if (key == "keyboard_layout_auto_by_locale" || key == SettingsManager.KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED) {
                Log.d(TAG, "Keyboard layout auto mode/mapping changed, resolving active layout...")
                loadKeyboardLayout()
                switchToLayout(activeKeyboardLayoutName, showToast = false)
            } else if (key == AdditionalSubtypeUtils.PREF_CUSTOM_INPUT_STYLES) {
                Log.d(TAG, "Custom input styles changed, re-registering subtypes...")
                AdditionalSubtypeUtils.registerAdditionalSubtypes(this)
            } else if (key == "trackpad_gestures_enabled") {
                val newValue = SettingsManager.getTrackpadGesturesEnabled(this)
                Log.d(TRACKPAD_DEBUG_TAG, "SharedPrefs listener: trackpad_gestures_enabled changed to $newValue")
                Log.d(TAG, "Trackpad gestures setting changed, restarting detection...")
                if (::trackpadGestureDetector.isInitialized) {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector initialized, stopping old detector...")
                    trackpadGestureDetector.stop()
                    Log.d(TRACKPAD_DEBUG_TAG, "Building new detector...")
                    trackpadGestureDetector = buildTrackpadGestureDetector()
                    if (shouldStartShizukuTrackpadDetector()) {
                        Log.d(TRACKPAD_DEBUG_TAG, "Starting new Shizuku detector...")
                        trackpadGestureDetector.start()
                    } else {
                        Log.d(TRACKPAD_DEBUG_TAG, "Detector start skipped after gestures change")
                    }
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector restart complete for gestures_enabled change")
                } else {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector NOT initialized yet, skipping restart")
                }
            } else if (
                key == "trackpad_swipe_threshold" ||
                key == "trackpad_suggestion_swipe_threshold" ||
                key == "trackpad_delete_swipe_threshold"
            ) {
                val suggestionValue = SettingsManager.getTrackpadSuggestionSwipeThreshold(this)
                val deleteValue = SettingsManager.getTrackpadDeleteSwipeThreshold(this)
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "SharedPrefs listener: trackpad thresholds changed: suggestion=$suggestionValue, delete=$deleteValue"
                )
                Log.d(TAG, "Trackpad swipe threshold changed, restarting detection...")
                if (::trackpadGestureDetector.isInitialized) {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector initialized, stopping old detector...")
                    trackpadGestureDetector.stop()
                    Log.d(TRACKPAD_DEBUG_TAG, "Building new detector...")
                    trackpadGestureDetector = buildTrackpadGestureDetector()
                    if (shouldStartShizukuTrackpadDetector()) {
                        Log.d(TRACKPAD_DEBUG_TAG, "Starting new Shizuku detector...")
                        trackpadGestureDetector.start()
                    } else {
                        Log.d(TRACKPAD_DEBUG_TAG, "Detector start skipped after swipe threshold change")
                    }
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector restart complete for swipe_threshold change")
                } else {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector NOT initialized yet, skipping restart")
                }
            } else if (key == "trackpad_provider" || key == "trackpad_shizuku_device") {
                val newValue = SettingsManager.getTrackpadProvider(this)
                val shizukuDevice = SettingsManager.getTrackpadShizukuDevice(this)
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "SharedPrefs listener: trackpad input changed: provider=$newValue, shizukuDevice=$shizukuDevice"
                )
                if (::trackpadGestureDetector.isInitialized) {
                    trackpadGestureDetector.stop()
                    trackpadGestureDetector = buildTrackpadGestureDetector()
                    if (shouldStartShizukuTrackpadDetector()) {
                        Log.d(TRACKPAD_DEBUG_TAG, "Starting Shizuku detector for provider change")
                        trackpadGestureDetector.start()
                    }
                }
                attachTrackpadDecorViewMotionHook("provider_changed")
            } else if (key == "pastierina_mode_override") {
                keyboardVisibilityController.syncStatusBarPresentationModeFromSettings()
            } else if (key == SettingsManager.KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS ||
                key == SettingsManager.KEY_TITAN2_ELITE_FILL_CORNERS ||
                key == SettingsManager.KEY_TITAN2_ELITE_CONTOUR_LEDS ||
                key == it.palsoftware.pastiera.device.T2eCornerCalibration.KEY ||
                key == SettingsManager.KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER ||
                key == SettingsManager.KEY_TITAN2_ELITE_MAX_ICON_SHRINK) {
                if (::candidatesBarController.isInitialized) {
                    candidatesBarController.refreshWindowInsets()
                }
            } else if (
                key == "experimental_candidates_view_enabled" ||
                key == "software_keyboard_mode" ||
                key == SettingsManager.KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE
            ) {
                invalidateRenderedStatusSnapshot()
                val effectiveMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(this)
                scheduleKeyboardSurfaceTransition(
                    mode = effectiveMode
                )
            } else if (
                key == "software_keyboard_layout_style" ||
                key == "software_keyboard_number_row_enabled" ||
                key == "software_keyboard_left_modifier_key" ||
                key == "software_keyboard_right_modifier_key"
            ) {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "software_keyboard_nearest_key_touch_enabled") {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (SettingsManager.isKeyboardThemePreferenceKey(key)) {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (SettingsManager.isModifierIndicatorPreferenceKey(key)) {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (
                key == SettingsManager.KEY_TYPING_SOUND_MODE ||
                key == SettingsManager.KEY_TYPING_SOUND_OUTPUT_MODE ||
                key == SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME ||
                key == SettingsManager.KEY_TYPING_SOUND_UPDATED_AT
            ) {
                typingSoundPlayer.reload()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.d(TRACKPAD_DEBUG_TAG, "onCreate: SharedPreferences listener registered")
        
        // Register broadcast receiver for speech recognition
        speechResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast receiver called - action: ${intent?.action}")
                if (intent?.action == SpeechRecognitionActivity.ACTION_SPEECH_RESULT) {
                    val text = intent.getStringExtra(SpeechRecognitionActivity.EXTRA_TEXT)
                    Log.d(TAG, "Broadcast received with text: $text")
                    if (text != null && text.isNotEmpty()) {
                        Log.d(TAG, "Received speech recognition result: $text")
                        
                        // Delay text insertion to give the system time to restore InputConnection
                        // after the speech recognition activity has closed.
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Try multiple times if InputConnection is not immediately available
                            var attempts = 0
                            val maxAttempts = 10
                            
                            fun tryInsertText() {
                                val inputConnection = currentInputConnection
                                if (inputConnection != null) {
                                    inputConnection.commitText(text, 1)
                                    Log.d(TAG, "Speech text inserted successfully: $text")
                                } else {
                                    attempts++
                                    if (attempts < maxAttempts) {
                                        Log.d(TAG, "InputConnection not available, attempt $attempts/$maxAttempts, retrying in 100ms...")
                                        Handler(Looper.getMainLooper()).postDelayed({ tryInsertText() }, 100)
                                    } else {
                                        Log.w(TAG, "InputConnection not available after $maxAttempts attempts, text not inserted: $text")
                                    }
                                }
                            }
                            
                            tryInsertText()
                        }, 300) // Wait 300ms before trying to insert text
                    }
                }
            }
        }
        
        val filter = IntentFilter(SpeechRecognitionActivity.ACTION_SPEECH_RESULT)
        
        // Not exported on every Android version: other apps can't type into fields through it
        ContextCompat.registerReceiver(this, speechResultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        Log.d(TAG, "Broadcast receiver registered for: ${SpeechRecognitionActivity.ACTION_SPEECH_RESULT}")
        
        // Register broadcast receiver for permission request result
        permissionResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    PermissionRequestActivity.ACTION_PERMISSION_GRANTED -> {
                        Log.d(TAG, "RECORD_AUDIO permission granted, retrying speech recognition")
                        if (pendingSpeechRecognition) {
                            pendingSpeechRecognition = false
                            // Retry speech recognition now that permission is granted
                            startSpeechRecognition()
                        }
                    }
                    PermissionRequestActivity.ACTION_PERMISSION_DENIED -> {
                        Log.w(TAG, "RECORD_AUDIO permission denied by user")
                        pendingSpeechRecognition = false
                    }
                }
            }
        }
        
        val permissionFilter = IntentFilter().apply {
            addAction(PermissionRequestActivity.ACTION_PERMISSION_GRANTED)
            addAction(PermissionRequestActivity.ACTION_PERMISSION_DENIED)
        }
        
        // Not exported on every Android version: other apps can't type into fields through it
        ContextCompat.registerReceiver(this, permissionResultReceiver, permissionFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        Log.d(TAG, "Broadcast receiver registered for permission request results")
        
        // Register broadcast receiver for user dictionary updates
        userDictionaryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AppBroadcastActions.USER_DICTIONARY_UPDATED) {
                    Log.d(TAG, "User dictionary updated, refreshing...")
                    if (::suggestionController.isInitialized) {
                        suggestionController.refreshUserDictionary()
                    }
                } else if (intent?.action == AppBroadcastActions.DICTIONARY_INSTALLED) {
                    // A language's dictionary arrived: in use, it loads now
                    val language = intent.getStringExtra(it.palsoftware.pastiera.dictionaries.DictionaryDownloads.EXTRA_LANGUAGE)
                    if (language != null && ::suggestionController.isInitialized) {
                        suggestionController.onDictionaryInstalled(language)
                    }
                }
            }
        }
        
        val userDictFilter = IntentFilter(AppBroadcastActions.USER_DICTIONARY_UPDATED).apply {
            addAction(AppBroadcastActions.DICTIONARY_INSTALLED)
        }
        // Not exported on every Android version: other apps can't type into fields through it
        ContextCompat.registerReceiver(this, userDictionaryReceiver, userDictFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        Log.d(TAG, "Broadcast receiver registered for user dictionary updates")
        
        // Start trackpad gesture detection
        if (shouldStartShizukuTrackpadDetector()) {
            Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Calling initial Shizuku trackpadGestureDetector.start()...")
            trackpadGestureDetector.start()
            Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Initial Shizuku start() call completed")
        } else {
            Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Initial Shizuku detector start skipped")
        }
    }

    private fun handleSoftwareKeyboardTextInput(
        text: String,
        inputConnection: InputConnection?,
        snapshot: StatusBarController.StatusSnapshot
    ): Boolean {
        val ic = inputConnection ?: return false

        if (text == " ") {
            if (::textExpansionController.isInitialized &&
                textExpansionController.handleKeyDown(KeyEvent.KEYCODE_SPACE)
            ) {
                return true
            }
            DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, text)
            return SoftwareKeyboardTextInputHandler.handleSpaceInput(
                textInputController = textInputController,
                inputConnection = ic,
                shouldDisableDoubleSpaceToPeriod = snapshot.shouldDisableDoubleSpaceToPeriod,
                shouldDisableAutoCapitalize = snapshot.shouldDisableAutoCapitalize,
                shouldDisableSuggestions = snapshot.shouldDisableSuggestions,
                onDoubleSpaceHandled = { suggestionController.onContextReset() },
                onNormalBoundary = {
                    suggestionController.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, ic).committed
                },
                onCommitSpace = {
                    markSelectionUpdateSkipAfterCommit()
                    ic.commitText(" ", 1)
                },
                onStatusBarUpdate = { updateStatusBarText() }
            )
        }

        if (
            handleBoundaryTextBeforeCommit(
                text = text,
                inputConnection = ic,
                shouldDisableSuggestions = snapshot.shouldDisableSuggestions,
                shouldDisableAutoCorrect = snapshot.shouldDisableAutoCorrect
            )
        ) {
            if (::textExpansionController.isInitialized) textExpansionController.scheduleRefresh()
            return true
        }

        markSelectionUpdateSkipAfterCommit()
        if (DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, text)) {
            suggestionController.onContextReset()
        }
        ic.commitText(text, 1)
        if (!snapshot.shouldDisableSuggestions) {
            suggestionController.onCharacterCommitted(text, ic)
        }
        updateStatusBarText()
        if (::textExpansionController.isInitialized) textExpansionController.scheduleRefresh()
        return true
    }

    private fun handleBoundaryTextBeforeCommit(
        text: String,
        inputConnection: InputConnection?,
        shouldDisableSuggestions: Boolean = inputContextState.shouldDisableSuggestions,
        shouldDisableAutoCorrect: Boolean = inputContextState.shouldDisableAutoCorrect
    ): Boolean {
        val ic = inputConnection ?: return false
        if (text.length != 1) return false
        val boundary = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(text[0])
        if (boundary == '\'' || boundary !in it.palsoftware.pastiera.core.Punctuation.BOUNDARY) {
            return false
        }
        if (DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, text)) {
            suggestionController.onContextReset()
        }
        markSelectionUpdateSkipAfterCommit()
        return inputEventRouter.handleBoundaryText(
            context = this,
            text = boundary.toString(),
            inputConnection = ic,
            shouldDisableSuggestions = shouldDisableSuggestions,
            isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !shouldDisableAutoCorrect,
            autoCorrectionManager = autoCorrectionManager,
            updateStatusBar = { updateStatusBarText() }
        )
    }

    private fun handleSoftwareKeyboardModifierKeyDown(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                modifierStateBeforeHold = modifierStateController.captureLogicalState()
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes[keyCode] = SystemClock.uptimeMillis()
                val result = modifierStateController.handleCtrlKeyDown(
                    keyCode,
                    isInputViewActive,
                    onNavModeDeactivated = {
                        navModeController.cancelNotification()
                    }
                )
                if (result.shouldUpdateStatusBar || result.shouldRefreshStatusBar) {
                    updateStatusBarText()
                }
                true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                modifierStateBeforeHold = modifierStateController.captureLogicalState()
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes[keyCode] = SystemClock.uptimeMillis()
                if (symLayoutController.isSymActive()) {
                    symLayoutController.closeSymPage()
                }
                val result = modifierStateController.handleAltKeyDown(keyCode)
                if (result.shouldUpdateStatusBar || result.shouldRefreshStatusBar) {
                    updateStatusBarText()
                }
                true
            }
            KEYCODE_SYM -> {
                modifierDownTimes[keyCode] = SystemClock.uptimeMillis()
                dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_DOWN)
            }
            else -> false
        }
    }

    private fun handleSoftwareKeyboardModifierKeyUp(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val now = SystemClock.uptimeMillis()
                val holdDuration = if (downTime > 0L) now - downTime else 0L
                val wasTap = holdDuration < 300L && !otherKeyInteractedDuringHold && !variationInteractedDuringHold
                val shortcutUsedDuringHold = otherKeyInteractedDuringHold

                val result = modifierStateController.handleCtrlKeyUp(keyCode)
                if (shortcutUsedDuringHold && ctrlOneShot && !ctrlLatchActive) {
                    modifierStateController.ctrlOneShot = false
                }
                if ((shortcutUsedDuringHold || ctrlHeldIntoNewField) && !ctrlLatchFromNavMode) {
                    modifierStateController.clearCtrlState(resetPressedState = true)
                }
                ctrlHeldIntoNewField = false
                if (result.shouldUpdateStatusBar || wasTap || shortcutUsedDuringHold) {
                    updateStatusBarText()
                }
                modifierDownTimes.remove(keyCode)
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierStateBeforeHold = null
                true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                val result = modifierStateController.handleAltKeyUp(keyCode)
                if (result.shouldUpdateStatusBar || result.shouldRefreshStatusBar) {
                    updateStatusBarText()
                }
                modifierDownTimes.remove(keyCode)
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierStateBeforeHold = null
                true
            }
            KEYCODE_SYM -> {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val now = SystemClock.uptimeMillis()
                val holdDuration = if (downTime > 0L) now - downTime else 0L
                if (holdDuration >= 300L && symLayoutController.currentSymPage() == 0) {
                    symChordUsedSinceKeyDown = true
                }
                modifierDownTimes.remove(keyCode)
                // Keep AOSP-rendered text SYM pages synchronous: the held-SYM preview and the
                // activated SYM page use the same view, so posting KEY_UP would draw one frame
                // of the base keyboard between them. Overlay pages replace the touched view and
                // must still be posted to avoid mutating the hierarchy during touch dispatch.
                if (symLayoutController.peekNextSymPage() in 3..4) {
                    uiHandler.post {
                        dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_UP)
                    }
                } else {
                    dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_UP)
                }
                true
            }
            else -> false
        }
    }

    private fun handleSoftwareKeyboardKeyStroke(keyCode: Int): Boolean {
        val consumeCtrlOneShotAfterStroke = ctrlOneShot && !ctrlLatchActive && !ctrlLatchFromNavMode
        val softwareModifierActive =
            symTogglePendingOnKeyUp ||
                symLayoutController.currentSymPage() in 1..4 ||
                altPressed ||
                altPhysicallyPressed ||
                altLatchActive ||
                altOneShot ||
                ctrlPressed ||
                ctrlPhysicallyPressed ||
                ctrlLatchActive ||
                ctrlOneShot ||
                ctrlLatchFromNavMode
        if (!softwareModifierActive) {
            return false
        }
        val (downHandled, upHandled) = try {
            dispatchingSoftwareKeyboardKey = true
            dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_DOWN) to
                dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_UP)
        } finally {
            dispatchingSoftwareKeyboardKey = false
        }
        if ((downHandled || upHandled) && SettingsManager.isQuickLauncherShortcut(this, keyCode)) {
            candidatesBarController.cancelSoftwareKeyboardTouchState()
        }
        if (consumeCtrlOneShotAfterStroke && (downHandled || upHandled)) {
            modifierStateController.ctrlOneShot = false
            updateStatusBarText()
        }
        return downHandled || upHandled
    }

    private fun dispatchSoftwareKeyboardSyntheticKey(keyCode: Int, action: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val metaState = buildSoftwareKeyboardMetaState(keyCode)
        val event = KeyEvent(
            now,
            now,
            action,
            keyCode,
            0,
            metaState
        )
        return if (action == KeyEvent.ACTION_DOWN) {
            onKeyDown(keyCode, event)
        } else {
            onKeyUp(keyCode, event)
        }
    }

    private fun buildSoftwareKeyboardMetaState(keyCode: Int): Int {
        var metaState = 0
        val ctrlActive = keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
        if (ctrlActive) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (shiftPressed || shiftPhysicallyPressed || shiftOneShot || capsLockEnabled) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        if (altPressed || altPhysicallyPressed || altOneShot || altLatchActive) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        return metaState
    }

    private fun buildTrackpadGestureDetector(): TrackpadGestureDetector {
        val gesturesEnabled = SettingsManager.getTrackpadGesturesEnabled(this)
        val eventDeviceSelection = SettingsManager.getTrackpadShizukuDevice(this)
        val fallbackEventDevice = resolveTrackpadEventDevice()
        Log.d(
            TRACKPAD_DEBUG_TAG,
            "buildTrackpadGestureDetector() - gesturesEnabled=$gesturesEnabled, eventDeviceSelection=$eventDeviceSelection, fallbackEventDevice=$fallbackEventDevice"
        )
        return TrackpadGestureDetector(
            isEnabled = { shouldStartShizukuTrackpadDetector() },
            onTouch = { phase, x, y, xRange -> uiHandler.post { handleShizukuTrackpadTouch(phase, x, y, xRange) } },
            scope = trackpadScope,
            eventDeviceSelection = eventDeviceSelection,
            fallbackEventDevice = fallbackEventDevice
        )
    }

    /** A swipe that started while typing; one that started elsewhere is the app's (a scroll). */
    private var shizukuTrackpadTouchDownAt = 0L
    private var shizukuTerminalSwipe = false

    /** Swipes on the keys move a terminal's cursor (Terminal mode > Swipes move the cursor). */
    private fun terminalSwipesMoveCursor(): Boolean =
        terminalModeActive && currentInputConnection != null && !keyboardHiddenForApp && symPage == 0 &&
            SettingsManager.getTerminalModeSwipeCursor(this)

    private var terminalSwipeLastX = 0f
    private var terminalSwipeLastY = 0f
    /** The swipe's direction once it's clear: 0 not yet, 1 across (cursor), 2 up or down (history). */
    private var terminalSwipeAxis = 0
    private var terminalSwipeActive = false

    /**
     * A swipe on the keys in a terminal: across, the cursor moves a character for each half a
     * key's width; up or down, an arrow key for each key's height or so (the shell's history).
     * Each swipe keeps to the direction it starts in.
     */
    private fun terminalCursorSwipe(action: Int, x: Float, y: Float, xRange: TrackpadAxisRange) {
        val keyWidth = (xRange.span / TrackpadCoordinateMapper.KEYS_ACROSS).coerceAtLeast(1f)
        val stepX = keyWidth * TERMINAL_SWIPE_CHARACTER_KEYS
        val stepY = keyWidth * TERMINAL_SWIPE_LINE_KEYS
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                terminalSwipeLastX = x
                terminalSwipeLastY = y
                terminalSwipeAxis = 0
                terminalSwipeActive = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!terminalSwipeActive) return
                val dx = x - terminalSwipeLastX
                val dy = y - terminalSwipeLastY
                if (terminalSwipeAxis == 0) {
                    if (kotlin.math.abs(dx) < stepX * 0.6f && kotlin.math.abs(dy) < stepY * 0.6f) return
                    terminalSwipeAxis = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) 1 else 2
                }
                if (terminalSwipeAxis == 1) {
                    val steps = (dx / stepX).toInt()
                    repeat(kotlin.math.abs(steps)) {
                        sendTerminalKey(if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                    }
                    terminalSwipeLastX += steps * stepX
                } else {
                    val steps = (dy / stepY).toInt()
                    repeat(kotlin.math.abs(steps)) {
                        sendTerminalKey(if (steps > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                    }
                    terminalSwipeLastY += steps * stepY
                }
            }
            else -> terminalSwipeActive = false
        }
    }

    /** One key, pressed and released, sent to the terminal. */
    private fun sendTerminalKey(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
    }

    /**
     * A touch on the keys, read through Shizuku: handled as Android's own keyboard swipes are,
     * but only while a field is being typed in (otherwise the swipe is a scroll, or nothing).
     */
    private fun handleShizukuTrackpadTouch(
        phase: TrackpadGestureDetector.TouchPhase,
        x: Float,
        y: Float,
        xRange: TrackpadAxisRange
    ) {
        val now = android.os.SystemClock.uptimeMillis()
        // In a terminal, swipes move its cursor instead
        if (phase == TrackpadGestureDetector.TouchPhase.DOWN) {
            shizukuTerminalSwipe = terminalSwipesMoveCursor()
        }
        if (shizukuTerminalSwipe) {
            terminalCursorSwipe(
                when (phase) {
                    TrackpadGestureDetector.TouchPhase.DOWN -> MotionEvent.ACTION_DOWN
                    TrackpadGestureDetector.TouchPhase.MOVE -> MotionEvent.ACTION_MOVE
                    TrackpadGestureDetector.TouchPhase.UP -> MotionEvent.ACTION_UP
                },
                x, y, xRange
            )
            return
        }
        val action = when (phase) {
            TrackpadGestureDetector.TouchPhase.DOWN -> {
                val typing = isInputViewActive && inputContextState.isEditable && !terminalModeActive && !keyboardHiddenForApp
                if (!typing) {
                    shizukuTrackpadTouchDownAt = 0L
                    return
                }
                shizukuTrackpadTouchDownAt = now
                MotionEvent.ACTION_DOWN
            }
            TrackpadGestureDetector.TouchPhase.MOVE -> MotionEvent.ACTION_MOVE
            TrackpadGestureDetector.TouchPhase.UP -> MotionEvent.ACTION_UP
        }
        if (shizukuTrackpadTouchDownAt == 0L) return
        val event = MotionEvent.obtain(shizukuTrackpadTouchDownAt, now, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHPAD
        try {
            processTrackpadMotion(event, origin = "shizuku", xRangeOverride = xRange)
        } finally {
            event.recycle()
        }
        if (phase == TrackpadGestureDetector.TouchPhase.UP) shizukuTrackpadTouchDownAt = 0L
    }

    private fun resolveTrackpadEventDevice(): String {
        return TrackpadEventDeviceResolver.resolve(
            physicalKeyboardName = DeviceSpecific.physicalKeyboardName(),
            firmwareIncremental = Build.VERSION.INCREMENTAL.orEmpty()
        )
    }
    
    override fun onDestroy() {
        it.palsoftware.pastiera.inputmethod.extrakeys.ExtraKeysToggle.handler = null
        it.palsoftware.pastiera.otp.OneTimeCodes.onNewCode = null
        it.palsoftware.pastiera.spellcheck.PastieraSpellCheckerService.keyboardController = null
        gifScope.cancel()
        HiddenAppKeyObserver.sink = null
        HiddenAppKeyObserver.interceptor = null
        HiddenAppKeyObserver.hiddenAppInFront = false
        ClicksAccessibilityKeyBridge.unregister(this)
        clicksPowerShiftTapFilter.reset()
        accidentalKeyPressFilter.reset()
        clicksPowerButtonEventMapper.reset()
        expansionAssetScope.cancel()
        super.onDestroy()
        pendingInputDeviceModeRefresh?.let { uiHandler.removeCallbacks(it) }
        pendingInputDeviceModeRefresh = null
        pendingKeyboardSurfaceTransition?.let { uiHandler.removeCallbacks(it) }
        pendingKeyboardSurfaceTransition = null
        clicksConnectionChangePending = false
        clicksDisconnectPending = false
        connectedClicksInputDeviceIds.clear()
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        inputManager = null
        stopClipboardCleanupTimer()
        // Remove listener when service is destroyed
        prefsListener?.let {
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        
        // Cleanup SpeechRecognitionManager
        speechRecognitionManager?.destroy()
        speechRecognitionManager = null

        // Cleanup ClipboardHistoryManager
        if (::suggestionController.isInitialized) {
            suggestionController.destroy()
        }
        clipboardHistoryManager.setHistoryChangeListener(null)
        clipboardHistoryManager.onDestroy()
        typingSoundPlayer.release()

        // Unregister broadcast receiver (deprecated, but kept for backwards compatibility)
        speechResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering broadcast receiver", e)
            }
        }
        
        // Unregister permission result receiver
        permissionResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering permission result receiver", e)
            }
        }
        
        userDictionaryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering user dictionary receiver", e)
            }
        }
        
        speechResultReceiver = null
        multiTapController.cancelAll()
        updateNavModeStatusIcon(false)
        trackpadDecorMotionView?.setOnGenericMotionListener(null)
        trackpadDecorMotionView = null

        // Stop trackpad gesture detection
        trackpadGestureDetector.stop()
        trackpadScope.cancel()
    }

    private fun prepareEnabledExpansionAssets() {
        expansionAssetScope.launch {
            if (::emojiShortcodeSource.isInitialized && SettingsManager.getEmojiShortcodesEnabled(this@PhysicalKeyboardInputMethodService)) {
                emojiShortcodeSource.prepare()
            }
            if (::symbolShortcodeSource.isInitialized && SettingsManager.getSymbolShortcodesEnabled(this@PhysicalKeyboardInputMethodService)) {
                symbolShortcodeSource.prepare()
            }
        }
    }

    override fun onCreateInputView(): View? = keyboardVisibilityController.onCreateInputView()

    /**
     * Creates the candidates view shown when the soft keyboard is disabled.
     * Uses a separate StatusBarController instance to provide identical functionality.
     */
    override fun onCreateCandidatesView(): View? = keyboardVisibilityController.onCreateCandidatesView()

    override fun onStartCandidatesView(info: EditorInfo?, restarting: Boolean) {
        super.onStartCandidatesView(info, restarting)
        isInputViewActive = inputContextState.isEditable
        keyboardVisibilityController.onCandidatesViewStarted()
        updateStatusBarText()
        traceImeVisibility("onStartCandidatesView restarting=$restarting")
    }

    override fun onFinishCandidatesView(finishingInput: Boolean) {
        super.onFinishCandidatesView(finishingInput)
        keyboardVisibilityController.onCandidatesViewFinished(finishingInput)
    }

    /**
     * The standard backend uses the input view for both compact hardware UI and software keys.
     * Only the experimental hardware backend uses Android's separate candidates lifecycle.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        if (hiddenAppSurfaceBlocked()) {
            requestedInputViewShown = false
            return false
        }
        val systemShouldShowInputView = super.onEvaluateInputViewShown()
        val resolvedShowInputView =
            keyboardVisibilityController.onEvaluateInputViewShown(systemShouldShowInputView)
        requestedInputViewShown = resolvedShowInputView
        return resolvedShowInputView
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        val accepted = super.onShowInputRequested(flags, configChange)
        if (hiddenAppSurfaceBlocked()) return false
        if (::keyboardVisibilityController.isInitialized) {
            keyboardVisibilityController.onExplicitShowRequested()
        }
        traceImeVisibility("onShowInputRequested accepted=$accepted flags=$flags")
        return !keyboardVisibilityController.usesCandidatesView()
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        val decor = window?.window?.decorView ?: return
        outInsets ?: return
        if ((keyboardHiddenForApp && !hiddenAppPanelOpen()) || outOfSightLedsOnly() || outOfSightSurfaceHidden()) {
            // Nothing (or only the LEDs) is shown: the app keeps the whole screen and every touch
            ImeInsetsPolicy.applyRenderedContentInsets(outInsets, null, decor.height)
            return
        }
        if (!isFullscreenMode && ::candidatesBarController.isInitialized) {
            // Content and touch geometry come from the same attached, visible child. Neither
            // a requested surface nor Android's cached candidates-started flag is sufficient.
            ImeInsetsPolicy.applyRenderedContentInsets(
                outInsets,
                candidatesBarController.visibleBoundsInWindow(),
                decor.height
            )
        }
    }

    private fun traceImeVisibility(event: String) {
        if (!BuildConfig.DEBUG) return
        val bounds = if (::candidatesBarController.isInitialized) {
            candidatesBarController.visibleBoundsInWindow()
        } else null
        Log.i("PastieraImeVisibility", "$event editor=$currentPackageName active=$isInputViewActive " +
            "inputShown=$isInputViewShown requestedInput=$requestedInputViewShown bounds=$bounds")
    }

    private fun synchronizeCandidatesContainerVisibility() {
        // InputMethodService can leave fullscreenArea INVISIBLE when an already-open input
        // window changes to candidates-only mode. Toggling the public extract-view state makes
        // the framework recompute that container; the second call restores the original state.
        setExtractViewShown(false)
        setExtractViewShown(true)
    }

    private fun requestKeyboardInputView() = requestShowSelf(0)

    /**
     * A GIF picked in the emoji picker. Fields that take GIF content get the file itself
     * (through the FileProvider); anywhere else, or if the download fails, its link is typed.
     */
    private fun sendGif(gif: GifResult) {
        val editorInfo = currentInputEditorInfo ?: return
        // Recent GIFs (GIF search's Recent section, and recently used first)
        GifCollections.addRecent(this, gif)
        val acceptsGif = KlipyGifs.editorAcceptsGif(EditorInfoCompat.getContentMimeTypes(editorInfo))
        // A GIF is a one-off: close the picker straight away
        if (symLayoutController.closeSymPage()) updateStatusBarText()
        if (!acceptsGif) {
            currentInputConnection?.commitText(gif.gifUrl, 1)
            Toast.makeText(this, R.string.gif_sent_as_link, Toast.LENGTH_SHORT).show()
            return
        }
        val pickedInPackage = editorInfo.packageName
        val pickedInField = editorInfo.fieldId
        gifScope.launch {
            val file = try {
                KlipyGifs.downloadToCache(this@PhysicalKeyboardInputMethodService, gif)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            // The field was re-bound during the download: use the current connection, but never
            // send to another app or field (a different chat) than the GIF was picked in
            val connection = currentInputConnection ?: return@launch
            val info = currentInputEditorInfo ?: return@launch
            if (info.packageName != pickedInPackage || info.fieldId != pickedInField) return@launch
            if (file == null) {
                connection.commitText(gif.gifUrl, 1)
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                this@PhysicalKeyboardInputMethodService, "$packageName.fileprovider", file
            )
            val content = InputContentInfoCompat(
                uri,
                ClipDescription(gif.description.ifBlank { "GIF" }, arrayOf("image/gif")),
                null
            )
            val sent = InputConnectionCompat.commitContent(
                connection, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
            )
            if (!sent) connection.commitText(gif.gifUrl, 1)
        }
    }

    /** Hidden app with the panels option and an emoji or symbols panel open. */
    private fun hiddenAppPanelOpen(): Boolean =
        keyboardHiddenForApp && symPage > 0 && hiddenAppAllowsPanels

    /** Hidden apps keep the keyboard's surface closed, except for the status LEDs or an open panel. */
    private fun hiddenAppSurfaceBlocked(): Boolean =
        (keyboardHiddenForApp && !hiddenAppShowsLeds && !hiddenAppPanelOpen()) || outOfSightSurfaceHidden()

    /**
     * The keyboard out of sight while its keys keep working: terminal mode's option (like the
     * Linux desktop: Alt layer, SYM layers typed blind, real Ctrl), or minimal mode in every app.
     */
    private fun outOfSightSurfaceHidden(): Boolean =
        !keyboardHiddenForApp && keyboardOutOfSight && !outOfSightShowsLeds && !outOfSightPanelOpen()

    /** Out of sight, but its status LEDs show (an option). */
    private fun outOfSightLedsOnly(): Boolean =
        !keyboardHiddenForApp && keyboardOutOfSight && outOfSightShowsLeds && !outOfSightPanelOpen()

    /**
     * A page that has to be seen while it's open. Terminals: the clipboard (3) and emoji picker (4);
     * the SYM layers are typed blind there. Minimal mode: every SYM and emoji page.
     */
    private fun outOfSightPanelOpen(): Boolean =
        extraKeysOpen || if (minimalModeActive) symPage > 0 else symPage == 3 || symPage == 4

    private var keyboardOutOfSight = false
    private var outOfSightShowsLeds = false
    /** Minimal mode applies to the field in front (not a terminal or a hidden app). */
    private var minimalModeActive = false
    private var outOfSightSurfaceShown = false

    /** In a hidden app with the panels option: its panel keys, and every key while a panel is open. */
    private fun hiddenAppKeyGoesToPastiera(keyCode: Int): Boolean {
        if (!hiddenAppAllowsPanels) return false
        if (symPage > 0) return true
        // Keys pressed with SYM held are its chords (SYM + Space: the quick launcher): with only
        // SYM reaching the keyboard, SYM would count as a tap and open its panel
        if (symKeyHeld) return true
        val emojiKey = SettingsManager.getEmojiPickerKey(this)
        return keyCode == KEYCODE_SYM || (emojiKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == emojiKey)
    }

    /**
     * In a hidden app, a held character key repeats into the app, and an ordinary text field
     * (not a terminal or X11 view, which read raw keys) answers a held letter with Android's
     * accent picker. The keyboard is hidden there, so the repeats are dropped instead.
     */
    private fun hiddenAppHoldOpensAccentPicker(keyCode: Int): Boolean {
        if (hiddenAppKeyGoesToPastiera(keyCode)) return false
        if (!it.palsoftware.pastiera.shortcuts.KeyCombo.isCharacterKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE) return false
        val inputType = currentInputEditorInfo?.inputType ?: EditorInfo.TYPE_NULL
        return inputType and android.text.InputType.TYPE_MASK_CLASS != EditorInfo.TYPE_NULL
    }

    /** Minimal mode: every app but terminals (their own option) and hidden apps (keys to the app). */
    private fun updateOutOfSight(packageName: String?) {
        minimalModeActive = !terminalModeActive && SettingsManager.getMinimalMode(this) &&
            !SettingsManager.isKeyboardHiddenForApp(this, packageName)
        keyboardOutOfSight = (terminalModeActive && SettingsManager.getTerminalModeHideKeyboard(this)) || minimalModeActive
        outOfSightShowsLeds = keyboardOutOfSight && if (minimalModeActive) {
            SettingsManager.getMinimalModeShowLeds(this)
        } else {
            SettingsManager.getTerminalModeShowLeds(this)
        }
    }

    /** Minimal mode switched by its shortcut: the field in front follows at once. */
    private fun onMinimalModeToggled() {
        val wasOutOfSight = keyboardOutOfSight
        updateOutOfSight(currentInputEditorInfo?.packageName)
        if (::candidatesBarController.isInitialized) candidatesBarController.setLedsOnlyMode(keyboardOutOfSight && outOfSightLedsOnly(), atScreenEdge = keyboardOutOfSight)
        if (keyboardOutOfSight) {
            // Out of sight: let syncHiddenAppPanel hide the surface (or leave the LEDs)
            outOfSightSurfaceShown = true
            syncHiddenAppPanel()
        } else if (wasOutOfSight) {
            outOfSightSurfaceShown = false
            invalidateRenderedStatusSnapshot()
            ensureImeSurfaceVisible()
        }
        updateStatusBarText()
    }

    /** Show the keyboard while a panel is open in a hidden app; hide it (or back to LEDs) afterwards. */
    private fun syncHiddenAppPanel() {
        if (keyboardOutOfSight && !keyboardHiddenForApp) {
            if (::candidatesBarController.isInitialized) candidatesBarController.setLedsOnlyMode(outOfSightLedsOnly(), atScreenEdge = keyboardOutOfSight)
            val show = !outOfSightSurfaceHidden()
            if (show == outOfSightSurfaceShown) return
            outOfSightSurfaceShown = show
            invalidateRenderedStatusSnapshot()
            if (show) ensureImeSurfaceVisible() else hideSurfaceIfHiddenForApp()
            return
        }
        if (!keyboardHiddenForApp) return
        val panelOpen = hiddenAppPanelOpen()
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.setLedsOnlyMode(hiddenAppShowsLeds && !panelOpen)
        }
        if (panelOpen == hiddenAppPanelShown) return
        hiddenAppPanelShown = panelOpen
        invalidateRenderedStatusSnapshot()
        if (panelOpen) {
            ensureImeSurfaceVisible()
        } else {
            hideSurfaceIfHiddenForApp()
        }
        // Re-render in the new mode once the current pass is over
        uiHandler.post { updateStatusBarText() }
    }

    /**
     * Accessibility path for hidden apps that read keys before any input method (Termux:X11):
     * hands the panel keys, and every key while a panel is open, to the keyboard; sends the Titan's
     * Ctrl and Sym on as standard keys ([translateHiddenAppKey]).
     */
    private fun interceptHiddenAppKey(event: KeyEvent): Boolean {
        if (!keyboardHiddenForApp || currentInputConnection == null) return false
        val keyCode = event.keyCode
        if (keyCode in HIDDEN_APP_SYSTEM_KEYS) return false
        val forPastiera = when (event.action) {
            KeyEvent.ACTION_DOWN -> hiddenAppKeyGoesToPastiera(keyCode)
            KeyEvent.ACTION_UP -> keyCode in hiddenAppInterceptedKeys
            else -> false
        }
        if (!forPastiera && translateHiddenAppKey(event)) return true
        val take = when (event.action) {
            KeyEvent.ACTION_DOWN -> hiddenAppKeyGoesToPastiera(keyCode).also { taken ->
                if (taken && event.repeatCount == 0) hiddenAppInterceptedKeys += keyCode
            }
            // Only releases of presses Pastiera took; others belong to the app
            KeyEvent.ACTION_UP -> hiddenAppInterceptedKeys.remove(keyCode)
            else -> false
        }
        if (!take) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> onKeyDown(keyCode, event)
            KeyEvent.ACTION_UP -> onKeyUp(keyCode, event)
        }
        return true
    }

    // The Titan's Ctrl and Sym in hidden apps (Termux:X11). Android names them FUNC3 and
    // AGUI_SYM (Unihertz key codes), or keeps the press and only marks the next key with a Ctrl
    // or Sym meta state; apps such as Termux:X11 can turn neither into keys. The keyboard sends
    // standard Left Ctrl and Right Alt (the desktop layout's Sym) through the input connection.
    private val hiddenAppTranslatedKeys = mutableMapOf<Int, Int>()          // original -> sent modifier
    private val hiddenAppWrappedKeys = mutableMapOf<Int, List<Int>>()       // original -> modifiers around it
    private var hiddenAppStandardCtrlHeld = false

    /** Returns true when [event] was replaced by standard keys sent to the app (and must be consumed). */
    private fun translateHiddenAppKey(event: KeyEvent?): Boolean {
        if (event == null || !keyboardHiddenForApp) return false
        if (!SettingsManager.getHiddenAppStandardModifiers(this)) return false
        val ic = currentInputConnection ?: return false
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val titanModifier = TITAN_MODIFIER_SCAN_CODES[event.scanCode]
        if (titanModifier == null && (code == KeyEvent.KEYCODE_CTRL_LEFT || code == KeyEvent.KEYCODE_CTRL_RIGHT)) {
            // A standard Ctrl (another keyboard): the app gets it itself; never add a second one
            hiddenAppStandardCtrlHeld = down
            return false
        }
        if (titanModifier != null) {
            if (down) {
                if (event.repeatCount == 0) {
                    sendHiddenAppKey(ic, event, KeyEvent.ACTION_DOWN, titanModifier)
                    hiddenAppTranslatedKeys[code] = titanModifier
                    HiddenAppKeyObserver.logKey(event, "sent as ${KeyEvent.keyCodeToString(titanModifier)}")
                }
            } else {
                hiddenAppTranslatedKeys.remove(code)?.let { sendHiddenAppKey(ic, event, KeyEvent.ACTION_UP, it) }
            }
            return true
        }
        if (down) {
            if (KeyEvent.isModifierKey(code)) return false
            val held = hiddenAppTranslatedKeys.values
            val wrap = buildList {
                if (event.isCtrlPressed && !hiddenAppStandardCtrlHeld && KeyEvent.KEYCODE_CTRL_LEFT !in held) {
                    add(KeyEvent.KEYCODE_CTRL_LEFT)
                }
                if (event.metaState and KeyEvent.META_SYM_ON != 0 && KeyEvent.KEYCODE_ALT_RIGHT !in held) {
                    add(KeyEvent.KEYCODE_ALT_RIGHT)
                }
            }
            if (wrap.isEmpty() && code !in hiddenAppWrappedKeys) return false
            if (event.repeatCount == 0) {
                wrap.forEach { sendHiddenAppKey(ic, event, KeyEvent.ACTION_DOWN, it) }
                hiddenAppWrappedKeys[code] = wrap
                // Which modifiers, never which key (no text is logged)
                android.util.Log.i("FluxKeys", "a key marked ${wrap.joinToString { KeyEvent.keyCodeToString(it) }} sent with them")
            }
            val sentWith = hiddenAppWrappedKeys[code].orEmpty()
            val meta = (if (KeyEvent.KEYCODE_CTRL_LEFT in sentWith) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0) or
                (if (KeyEvent.KEYCODE_ALT_RIGHT in sentWith) KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON else 0)
            sendHiddenAppKey(ic, event, KeyEvent.ACTION_DOWN, code, event.repeatCount, meta)
            return true
        }
        val wrap = hiddenAppWrappedKeys.remove(code) ?: return false
        sendHiddenAppKey(ic, event, KeyEvent.ACTION_UP, code)
        wrap.asReversed().forEach { sendHiddenAppKey(ic, event, KeyEvent.ACTION_UP, it) }
        return true
    }

    private fun sendHiddenAppKey(
        ic: InputConnection,
        source: KeyEvent,
        action: Int,
        keyCode: Int,
        repeat: Int = 0,
        metaState: Int = 0
    ) {
        val sent = KeyEvent(
            source.downTime, source.eventTime, action, keyCode, repeat, metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        )
        ic.sendKeyEvent(sent)
        // The status LEDs follow what the app receives
        if (KeyEvent.isModifierKey(keyCode)) observeHiddenAppKey(sent)
    }

    /** Hidden app with status LEDs: mirror modifier keys the app receives, never consume them. */
    private fun observeHiddenAppKey(event: KeyEvent?) {
        if (!hiddenAppShowsLeds || event == null) return
        val changed = observedModifierLeds.onKey(
            event.keyCode, event.action, event.repeatCount, event.downTime, event.eventTime
        )
        if (changed) updateStatusBarText()
    }

    /** Close any keyboard surface once the framework's start/show pass is over. */
    private fun hideSurfaceIfHiddenForApp() {
        if (!hiddenAppSurfaceBlocked() || !::keyboardVisibilityController.isInitialized) return
        uiHandler.post {
            if (hiddenAppSurfaceBlocked()) keyboardVisibilityController.hideForApp()
        }
    }

    /**
     * Evaluates whether the IME should run in fullscreen mode.
     */
    override fun onEvaluateFullscreenMode(): Boolean {
        // Keep the compact candidates surface available outside extract mode.
        return false
    }

    @Deprecated("Deprecated Android callback; kept to clear emoji search capture when the target view is clicked.")
    @Suppress("DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        // Tapping a search bar that waited for typing brings the keyboard bar up
        if (::keyboardVisibilityController.isInitialized && keyboardVisibilityController.shouldRecoverSurfaceOnHardwareKey() &&
            !keyboardHiddenForApp && !keyboardOutOfSight
        ) keyboardVisibilityController.onHardwareInputRequested()
        if (symPage == 4 && ::candidatesBarController.isInitialized) {
            disableEmojiSearchInputCapture()
        }
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (
            symPage != 4 ||
            !::candidatesBarController.isInitialized ||
            !candidatesBarController.isEmojiPickerSearchInputActive()
        ) {
            return
        }
        if (ignoreNextEmojiSearchCursorAnchorUpdate) {
            ignoreNextEmojiSearchCursorAnchorUpdate = false
            return
        }
        // Only a moved selection means the user went back to the app's text. The cursor also
        // moves on screen whenever the keyboard changes height (opening emoji, GIF or symbol
        // search), and that must not take typing away from the search that just opened.
        val start = cursorAnchorInfo?.selectionStart ?: -1
        val end = cursorAnchorInfo?.selectionEnd ?: -1
        if (start < 0 || end < 0) return // the app doesn't report its selection here
        val knownStart = emojiSearchExternalSelectionStart
        val knownEnd = emojiSearchExternalSelectionEnd
        if (knownStart == null || knownEnd == null) {
            emojiSearchExternalSelectionStart = start
            emojiSearchExternalSelectionEnd = end
            return
        }
        if (start == knownStart && end == knownEnd) return
        disableEmojiSearchInputCapture()
    }

    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        shiftLayerLatched = false
        altModifierLayerLatched = false
        lastShiftTapUpTime = 0L
        lastAltTapUpTime = 0L
        modifierStateBeforeHold = null
        symTogglePendingOnKeyUp = false
        symChordUsedSinceKeyDown = false
        symPhysicallyPressed = false

        modifierStateController.resetModifiers(
            preserveNavMode = preserveNavMode,
            onNavModeCancelled = { navModeController.cancelNotification() }
        )
        
        symLayoutController.reset()
        alternateCharacterManager.resetTransientState()
        deactivateVariations()
        refreshStatusBar()
        navModeController.refreshNavModeState()
    }

    private fun disableEmojiSearchInputCapture() {
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.disableEmojiPickerSearchInputCapture()
        }
        emojiSearchExternalSelectionStart = null
        emojiSearchExternalSelectionEnd = null
        emojiSearchCursorAnchorMonitoringRequested = false
        ignoreNextEmojiSearchCursorAnchorUpdate = false
        currentInputConnection?.requestCursorUpdates(0)
    }

    private fun updateEmojiSearchExternalSelectionSnapshot(inputConnection: InputConnection?) {
        val extracted = inputConnection?.getExtractedText(ExtractedTextRequest(), 0)
        emojiSearchExternalSelectionStart = extracted?.selectionStart
        emojiSearchExternalSelectionEnd = extracted?.selectionEnd
    }

    private fun shouldReturnEmojiSearchFocusToApp(inputConnection: InputConnection?): Boolean {
        val extracted = inputConnection?.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val previousStart = emojiSearchExternalSelectionStart
        val previousEnd = emojiSearchExternalSelectionEnd
        emojiSearchExternalSelectionStart = extracted.selectionStart
        emojiSearchExternalSelectionEnd = extracted.selectionEnd
        if (previousStart == null || previousEnd == null) {
            return false
        }
        return previousStart != extracted.selectionStart || previousEnd != extracted.selectionEnd
    }

    private fun ensureEmojiSearchCursorAnchorMonitoring(inputConnection: InputConnection?) {
        if (emojiSearchCursorAnchorMonitoringRequested) {
            return
        }
        val requested = inputConnection?.requestCursorUpdates(
            InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
        ) == true
        if (requested) {
            emojiSearchCursorAnchorMonitoringRequested = true
            ignoreNextEmojiSearchCursorAnchorUpdate = true
        }
    }
    
    /**
     * Shows the surface appropriate for the effective keyboard mode: the full input view in
     * virtual mode, or the candidates/status surface in hardware mode.
     */
    private fun ensureImeSurfaceVisible() {
        keyboardVisibilityController.ensureImeSurfaceVisible()
    }
    /**
     * Updates the status bar through its controller.
     */
    private fun updateStatusBarText() {
        syncHiddenAppPanel()
        val totalStart = ImePerfLogger.mark()
        var variationMs = 0L
        var suggestionsMs = 0L
        var updateBarsMs = 0L

        val pastierinaModeActive = candidatesBarController.isPastierinaModeActive()
        val effectiveSoftwareKeyboardMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(this)
        val variationStart = ImePerfLogger.mark()
        val variationSnapshot = if (pastierinaModeActive) {
            VariationStateController.Snapshot(isActive = false, lastInsertedChar = null, variations = emptyList())
        } else {
            variationStateController.refreshFromCursor(
                currentInputConnection,
                inputContextState.shouldDisableVariations,
                hasActiveSelection = editorHasActiveSelection
            )
        }
        variationMs = ImePerfLogger.elapsedMs(variationStart)
        val clipboardCount = clipboardHistoryManager?.getHistorySize() ?: 0
        
        val modifierSnapshot = modifierStateController.snapshot()
        val state = inputContextState
        // The suggestions' "add to dictionary" chip, unless it's switched off
        val addWordCandidate = suggestionController.pendingAddWord()
            ?.takeIf { SettingsManager.getShowAddWordSuggestion(this) }
        val suggestionsEnabled = SettingsManager.isExperimentalSuggestionsEnabled(this) && SettingsManager.getSuggestionsEnabled(this)
        val suggestionsStart = ImePerfLogger.mark()
        val baseSuggestions = if (suggestionsEnabled) visibleSuggestionStrings() else emptyList()
        suggestionsMs = ImePerfLogger.elapsedMs(suggestionsStart)
        val softwareSymPreviewProjection = buildSoftwareSymPreviewProjection(modifierSnapshot)
        val snapshot = StatusBarController.StatusSnapshot(
            capsLockEnabled = modifierSnapshot.capsLockEnabled,
            shiftPhysicallyPressed = modifierSnapshot.shiftPhysicallyPressed,
            shiftOneShot = modifierSnapshot.shiftOneShot,
            ctrlLatchActive = modifierSnapshot.ctrlLatchActive,
            ctrlPhysicallyPressed = modifierSnapshot.ctrlPhysicallyPressed,
            ctrlOneShot = modifierSnapshot.ctrlOneShot,
            ctrlLatchFromNavMode = modifierSnapshot.ctrlLatchFromNavMode,
            altLatchActive = modifierSnapshot.altLatchActive,
            altPhysicallyPressed = modifierSnapshot.altPhysicallyPressed,
            altOneShot = modifierSnapshot.altOneShot,
            symPage = symPage,
            symHeld = symTogglePendingOnKeyUp,
            symSticky = symSticky,
            emojiHeld = emojiPickerKeyUpPending != KeyEvent.KEYCODE_UNKNOWN,
            emojiSticky = emojiSticky,
            symPhysicallyPressed = symPhysicallyPressed,
            clipboardCount = clipboardCount,
            variations = variationSnapshot.variations,
            suggestions = baseSuggestions,
            addWordCandidate = addWordCandidate,
            lastInsertedChar = variationSnapshot.lastInsertedChar,
            // Granular smart features flags
            shouldDisableSuggestions = state.shouldDisableSuggestions,
            shouldDisableAutoCorrect = state.shouldDisableAutoCorrect,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            shouldDisableDoubleSpaceToPeriod = state.shouldDisableDoubleSpaceToPeriod,
            shouldDisableVariations = state.shouldDisableVariations,
            isEmailField = state.isEmailField,
            shiftLayerLatched = shiftLayerLatched,
            altModifierLayerLatched = altModifierLayerLatched,
            activeKeyboardLayoutName = activeKeyboardLayoutName,
            emojiScreenFromEmojiKey = symLayoutController.openedByEmojiKey,
            softwareSymPreviewLabels = softwareSymPreviewProjection.contentByKeyCode,
            softwareSymPreviewTextLabels = softwareSymPreviewProjection.contentByBaseText,
            softwareCtrlPreviewLabels = buildSoftwareCtrlPreviewLabels(modifierSnapshot),
            softwareCtrlPreviewIconRes = buildSoftwareCtrlPreviewIconRes(modifierSnapshot),
            softwareCtrlPreviewActive = shouldShowSoftwareCtrlPreview(modifierSnapshot),
            softwareAltPreviewLabels = buildSoftwareAltPreviewLabels(modifierSnapshot),
            softwareAltPreviewActive = shouldShowSoftwareAltPreview(modifierSnapshot),
            // Legacy flag for backward compatibility
            shouldDisableSmartFeatures = shouldDisableSmartFeatures
        ).let { if (hiddenAppShowsLeds && !hiddenAppPanelOpen()) observedModifierLeds.applyTo(it) else it }
        updateSystemStatusModifierIcon(snapshot, effectiveSoftwareKeyboardMode)
        val modifierIndicators = SettingsManager.getModifierIndicators(this)
        // Also pass the emoji map while SYM is on (page 1 only)
        val emojiMapText = symLayoutController.emojiMapText()
        // Pass the SYM mappings for the emoji/character grid
        val symMappings = symLayoutController.currentSymMappings()?.toMap()
        // Pass the input connection so the buttons can type
        val inputConnection = currentInputConnection
        val unchangedRenderedState =
            snapshot == lastRenderedStatusSnapshot &&
                emojiMapText == lastRenderedEmojiMapText &&
                symMappings == lastRenderedSymMappings &&
                inputConnection === lastRenderedStatusInputConnection &&
                pastierinaModeActive == lastRenderedPastierinaModeActive &&
                effectiveSoftwareKeyboardMode == lastRenderedSoftwareKeyboardMode &&
                modifierIndicators == lastRenderedModifierIndicators
        if (!unchangedRenderedState) {
            val updateBarsStart = ImePerfLogger.mark()
            candidatesBarController.updateStatusBars(snapshot, emojiMapText, inputConnection, symMappings)
            updateBarsMs = ImePerfLogger.elapsedMs(updateBarsStart)
            lastRenderedStatusSnapshot = snapshot
            lastRenderedEmojiMapText = emojiMapText
            lastRenderedSymMappings = symMappings
            lastRenderedStatusInputConnection = inputConnection
            lastRenderedPastierinaModeActive = pastierinaModeActive
            lastRenderedSoftwareKeyboardMode = effectiveSoftwareKeyboardMode
            lastRenderedModifierIndicators = modifierIndicators
        }
        ImePerfLogger.logDuration(
            label = "updateStatusBarText",
            startNanos = totalStart,
            thresholdMs = 16L,
            details = "variation=${variationMs}ms suggestions=${suggestionsMs}ms updateBars=${updateBarsMs}ms pkg=$currentPackageName"
        )
    }

    private fun buildSoftwareSymPreviewProjection(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): SoftwareKeyboardSymLabels.Projection {
        val shiftActive = modifierSnapshot.capsLockEnabled ||
            modifierSnapshot.shiftPhysicallyPressed ||
            modifierSnapshot.shiftOneShot
        val mappings = symLayoutController.previewNextSoftwareSymPageMappings(shiftActive)
        if (mappings.isEmpty()) {
            return SoftwareKeyboardSymLabels.Projection(emptyMap(), emptyMap())
        }
        return SoftwareKeyboardSymLabels.project(
            page = symLayoutController.nextSoftwareTextSymPage(),
            rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(
                activeKeyboardLayoutName,
                softwareKeyboardLayoutStyle()
            ),
            symMappings = mappings,
            layoutName = activeKeyboardLayoutName
        )
    }

    private fun softwareKeyboardLayoutStyle(): AospKeyboardView.SoftwareLayoutStyle =
        when (SettingsManager.getSoftwareKeyboardLayoutStyle(this)) {
            SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT -> AospKeyboardView.SoftwareLayoutStyle.COMPACT
            SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO -> AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI -> AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO -> AospKeyboardView.SoftwareLayoutStyle.FULL_ISO
        }

    private fun shouldShowSoftwareCtrlPreview(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Boolean {
        return when {
            modifierSnapshot.ctrlLatchActive -> true
            modifierSnapshot.ctrlOneShot -> true
            modifierSnapshot.ctrlPhysicallyPressed -> SettingsManager.getNavModeCtrlHoldEnabled(this)
            else -> false
        }
    }

    private fun buildSoftwareCtrlPreviewLabels(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, String> {
        if (!shouldShowSoftwareCtrlPreview(modifierSnapshot)) {
            return emptyMap()
        }
        return SOFTWARE_PREVIEW_KEY_CODES.mapNotNull { keyCode ->
            val shortcutKeyCode = resolveSoftwareCtrlPreviewShortcutKeyCode(keyCode, modifierSnapshot)
            val mapping = ctrlKeyMap[shortcutKeyCode] ?: return@mapNotNull null
            val label = softwareCtrlPreviewLabel(mapping) ?: return@mapNotNull null
            keyCode to label
        }.toMap()
    }

    private fun buildSoftwareCtrlPreviewIconRes(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, Int> {
        if (!shouldShowSoftwareCtrlPreview(modifierSnapshot)) {
            return emptyMap()
        }
        return SOFTWARE_PREVIEW_KEY_CODES.mapNotNull { keyCode ->
            val shortcutKeyCode = resolveSoftwareCtrlPreviewShortcutKeyCode(keyCode, modifierSnapshot)
            val mapping = ctrlKeyMap[shortcutKeyCode] ?: return@mapNotNull null
            val iconRes = softwareCtrlPreviewIconRes(mapping) ?: return@mapNotNull null
            keyCode to iconRes
        }.toMap()
    }

    private fun shouldShowSoftwareAltPreview(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Boolean =
        modifierSnapshot.altLatchActive ||
            modifierSnapshot.altOneShot ||
            modifierSnapshot.altPhysicallyPressed

    private fun buildSoftwareAltPreviewLabels(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, String> {
        if (!shouldShowSoftwareAltPreview(modifierSnapshot)) {
            return emptyMap()
        }
        val altMappings = AltModifierMappingResolver.resolve(assets, this)
        return SOFTWARE_PREVIEW_KEY_CODES.mapNotNull { keyCode ->
            val label = altMappings[keyCode]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            keyCode to label
        }.toMap()
    }

    private fun resolveSoftwareCtrlPreviewShortcutKeyCode(
        keyCode: Int,
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Int {
        val usesPhysicalNavGrid = modifierSnapshot.ctrlLatchFromNavMode ||
            (modifierSnapshot.ctrlPhysicallyPressed && SettingsManager.getNavModeCtrlHoldEnabled(this))
        if (usesPhysicalNavGrid) {
            return keyCode
        }
        if (!SettingsManager.getLayoutAwareCtrlShortcutsEnabled(this)) {
            return keyCode
        }
        val mappedChar = LayoutMappingRepository.getCharacter(keyCode, isShift = false)
            ?.lowercaseChar()
            ?: return keyCode
        return if (mappedChar in 'a'..'z') {
            KeyEvent.KEYCODE_A + (mappedChar - 'a')
        } else {
            keyCode
        }
    }

    private fun softwareCtrlPreviewLabel(mapping: KeyMappingLoader.CtrlMapping): String? {
        return when (mapping.type) {
            "keycode" -> when (mapping.value) {
                "DPAD_UP" -> "↑"
                "DPAD_DOWN" -> "↓"
                "DPAD_LEFT" -> "←"
                "DPAD_RIGHT" -> "→"
                "DPAD_CENTER" -> "OK"
                "MOVE_HOME" -> "Home"
                "MOVE_END" -> "End"
                "PAGE_UP" -> "PgUp"
                "PAGE_DOWN" -> "PgDn"
                "ESCAPE" -> "Esc"
                "TAB" -> "Tab"
                "FORWARD_DEL" -> "Del"
                else -> mapping.value.removePrefix("KEYCODE_")
            }
            "action" -> when (mapping.value) {
                "copy" -> "Copy"
                "paste" -> "Paste"
                "cut" -> "Cut"
                "undo" -> "Undo"
                "select_all" -> "All"
                "expand_selection_left" -> "Sel ←"
                "expand_selection_right" -> "Sel →"
                "move_word_left" -> "← word"
                "move_word_right" -> "word →"
                "expand_selection_word_left" -> "Sel word ←"
                "expand_selection_word_right" -> "Sel word →"
                "page_start" -> "Start"
                "page_end" -> "End"
                "toggle_minimal_ui" -> "Mini"
                "media_play_pause" -> "Play"
                "media_previous" -> "Prev"
                "media_next" -> "Next"
                else -> mapping.value
            }
            "command" -> mapping.value.substringAfterLast('.').replace('_', ' ').takeIf { it.isNotBlank() }
            "native_ctrl" -> "Ctrl"
            "none" -> null
            else -> null
        }
    }

    private fun softwareCtrlPreviewIconRes(mapping: KeyMappingLoader.CtrlMapping): Int? {
        return when (mapping.type) {
            "keycode" -> when (mapping.value) {
                "DPAD_UP" -> R.drawable.keyboard_arrow_up_24
                "DPAD_DOWN" -> R.drawable.keyboard_arrow_down_24
                "DPAD_LEFT" -> R.drawable.keyboard_arrow_left_24
                "DPAD_RIGHT" -> R.drawable.keyboard_arrow_right_24
                "TAB" -> R.drawable.keyboard_tab_24
                "MOVE_HOME" -> R.drawable.first_page_24
                "MOVE_END" -> R.drawable.last_page_24
                "PAGE_UP" -> R.drawable.keyboard_double_arrow_up_24
                "PAGE_DOWN" -> R.drawable.keyboard_double_arrow_down_24
                "ESCAPE" -> R.drawable.close_24
                "FORWARD_DEL" -> R.drawable.delete_24
                else -> null
            }
            "action" -> when (mapping.value) {
                "copy" -> R.drawable.content_copy_24
                "paste" -> R.drawable.content_paste_24
                "cut" -> R.drawable.content_cut_24
                "undo" -> R.drawable.undo_24
                "select_all" -> R.drawable.select_all_24
                "expand_selection_left" -> R.drawable.text_select_move_back_character_filled_24
                "expand_selection_right" -> R.drawable.text_select_move_forward_character_filled_24
                "move_word_left" -> R.drawable.text_select_move_back_word_24
                "move_word_right" -> R.drawable.text_select_move_forward_word_24
                "expand_selection_word_left" -> R.drawable.text_select_move_back_word_filled_24
                "expand_selection_word_right" -> R.drawable.text_select_move_forward_word_filled_24
                "page_start" -> R.drawable.first_page_24
                "page_end" -> R.drawable.last_page_24
                "toggle_minimal_ui" -> if (SettingsManager.getPastierinaModeActive(this)) {
                    R.drawable.expand_content_24
                } else {
                    R.drawable.collapse_content_24
                }
                "media_play_pause" -> R.drawable.play_pause_24
                "media_previous" -> R.drawable.skip_previous_24
                "media_next" -> R.drawable.skip_next_24
                else -> null
            }
            "command" -> when (mapping.value) {
                "pastiera.toggle_software_keyboard_mode" -> R.drawable.expansion_panels_24
                else -> null
            }
            else -> null
        }
    }
    
    /**
     * Turns variations off.
     */
    private fun deactivateVariations() {
        if (::variationStateController.isInitialized) {
            variationStateController.clear()
        }
    }

    override fun onUnbindInput() {
        pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
        pendingKeyboardSurfaceTransition = null
        isInputViewActive = false
        if (::keyboardVisibilityController.isInitialized) keyboardVisibilityController.onInputUnbound()
        traceImeVisibility("onUnbindInput")
        super.onUnbindInput()
    }

    override fun onBindInput() {
        super.onBindInput()
        traceImeVisibility("onBindInput")
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        AutoCapitalizeHelper.debugPackage = info?.packageName
        info?.let {
            DebugCaptureStore.recordField(
                DebugCaptureStore.FieldInfo(
                    timestampMs = System.currentTimeMillis(),
                    packageName = it.packageName,
                    inputType = it.inputType,
                    imeOptions = it.imeOptions,
                    fieldId = it.fieldId,
                    hasHint = !it.hintText.isNullOrEmpty(),
                    initialSelStart = it.initialSelStart,
                    initialSelEnd = it.initialSelEnd,
                    shiftFieldType = ShiftFieldTypes.of(it)?.id,
                    restarting = restarting
                )
            )
        }
        // Fields that don't say what they are ("Other" in Automatic Shift): typing is followed by key
        otherFieldShift = info != null && info.packageName != packageName &&
            ShiftFieldTypes.of(info) == ShiftFieldTypes.Type.OTHER &&
            ShiftFieldTypes.Type.OTHER in ShiftFieldTypes.enabled(this)
        otherFieldCapitalNext = otherFieldShift
        otherFieldLastChar = null
        // Undo history for this field (none for passwords)
        val historyKey = info?.let { "${it.packageName}:${it.fieldId}:${it.inputType}" }
        val password = info != null && ShiftFieldTypes.of(info) == null &&
            info.inputType and android.text.InputType.TYPE_MASK_CLASS == android.text.InputType.TYPE_CLASS_TEXT
        it.palsoftware.pastiera.core.EditHistory.onFieldStarted(historyKey, !password && info?.inputType != EditorInfo.TYPE_NULL)
        scheduleEditHistoryRecord(300L)
        AutoCapitalizeHelper.cursorAtStart = info != null && info.initialSelStart == 0 && info.initialSelEnd == 0
        // Back out of Niagara's search (opened by the quick launcher): back to the app
        if (!restarting) {
            val editable = info != null && info.inputType != EditorInfo.TYPE_NULL
            info?.packageName?.let { QuickLauncherOpener.foregroundPackage = it }
            // Screen size per app (Shizuku extras)
            it.palsoftware.pastiera.adb.PerAppDensity.onAppInFront(this, info?.packageName)
            QuickLauncherOpener.NiagaraReturn.onInputStarted(info?.packageName, editable)?.let(::returnToApp)
        }
        // Emoji layer profiles: the layer follows the app when switching by app
        if (::alternateCharacterManager.isInitialized) alternateCharacterManager.setEmojiLayerOverride(
            if (it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.switchByApp(this)) {
                it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.forApp(info?.packageName)?.let { profile -> it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles.layerMappings(this, profile) }
            } else null
        )
        terminalModeActive = SettingsManager.isTerminalModeApp(this, info?.packageName) && TerminalMode.apply(info)
        // A new field (a search a Ctrl shortcut opened, say): a latched Ctrl doesn't come along,
        // so typing there isn't taken for more shortcuts
        if (!restarting && !terminalModeActive && ::modifierStateController.isInitialized &&
            SettingsManager.getSmartCtrlOffAfterShortcut(this)
        ) {
            // Still held: it lets go when released
            if (ctrlPhysicallyPressed) ctrlHeldIntoNewField = true else releaseCtrlAfterShortcut()
        }
        // Flux Keyboard: no microphone button in terminal apps
        it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry.setTerminalApp(
            SettingsManager.isTerminalModeApp(this, info?.packageName)
        )
        // Shizuku users: keyboard swipes through Shizuku, unless a source was picked by hand
        SettingsManager.adoptShizukuTrackpadIfUnchosen(this)
        updateOutOfSight(info?.packageName)
        outOfSightSurfaceShown = false
        if (extraKeysOpen) {
            if (info?.packageName != extraKeysPackage) setExtraKeysOpen(false) else uiHandler.post { renderExtraKeys() }
        }
        terminalCtrlKeysDown.clear()
        terminalCtrlSent.clear()
        terminalRawKeysDown.clear()
        terminalEmojiKeysDown.clear()
        pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
        pendingKeyboardSurfaceTransition = null
        super.onStartInput(info, restarting)
        EmojiCompatSupport.onStartInput(info)
        keyboardHiddenForApp = SettingsManager.isKeyboardHiddenForApp(this, info?.packageName)
        HiddenAppKeyObserver.hiddenAppInFront = keyboardHiddenForApp
        val showLeds = keyboardHiddenForApp && SettingsManager.hiddenAppShowsLeds(this, info?.packageName)
        hiddenAppAllowsPanels = keyboardHiddenForApp && SettingsManager.hiddenAppAllowsPanels(this, info?.packageName)
        if (showLeds != hiddenAppShowsLeds || !restarting) observedModifierLeds.reset()
        hiddenAppShowsLeds = showLeds
        if (::candidatesBarController.isInitialized) candidatesBarController.setLedsOnlyMode(showLeds || outOfSightLedsOnly(), atScreenEdge = keyboardOutOfSight && !keyboardHiddenForApp)
        HiddenAppKeyObserver.sink = if (showLeds) ::observeHiddenAppKey else null
        hiddenAppPanelShown = false
        hiddenAppPassedThroughKeys.clear()
        hiddenAppPastieraKeys.clear()
        hiddenAppInterceptedKeys.clear()
        if (keyboardHiddenForApp && ::symLayoutController.isInitialized && symLayoutController.isSymActive()) {
            symLayoutController.closeSymPage()
        }
        // Every hidden app: panel keys (with the panels option) and the Titan's Ctrl and Sym
        HiddenAppKeyObserver.interceptor = if (keyboardHiddenForApp) ::interceptHiddenAppKey else null
        if (keyboardHiddenForApp) {
            // Keep the Linux desktop's keyboard layout in step with the keyboard's Alt map and SYM
            // page (written only when it changed; the chroot picks it up at the next start)
            val appContext = applicationContext
            Thread({
                runCatching { DesktopKeyboardLayout.export(appContext) }
            }, "desktop-layout").start()
        }
        hiddenAppTranslatedKeys.clear()
        hiddenAppWrappedKeys.clear()
        hiddenAppStandardCtrlHeld = false
        hideSurfaceIfHiddenForApp()
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        if (
            !restarting ||
            !SettingsManager.getAutoCapitalizeRespectManualShiftOff(this)
        ) {
            // A manual Shift-off suppresses auto-cap only for the current field session.
            // Text context alone cannot identify a field: every empty editor looks like "|".
            clearAutoCapSuppression()
        }
        DeferredPunctuationSpaceTracker.startField(info)
        bounceKeyFilter.reset()
        clicksPowerShiftTapFilter.reset()
        accidentalKeyPressFilter.reset()
        cancelPendingSelectionDrivenUiWork()
        invalidateRenderedStatusSnapshot()
        editorHasActiveSelection = false
        
        currentPackageName = info?.packageName
        updateDebugImeContextSnapshot(info)
        
        // Reset clipboard overlay when starting new input

        updateInputContextState(info)
        it.palsoftware.pastiera.core.EmoticonSentences.enabled = SettingsManager.getAutoCapAfterEmoticon(this)
        // Private mode (a shortcut) makes every field incognito
        val incognitoField = SettingsManager.isIncognitoField(this, info?.imeOptions ?: 0) ||
            it.palsoftware.pastiera.core.PrivateMode.isOn(this)
        it.palsoftware.pastiera.core.IncognitoTyping.active = incognitoField
        if (::suggestionController.isInitialized) {
            // Words are never learned from passwords, email addresses or web addresses either
            suggestionController.incognito = incognitoField ||
                inputContextState.isPasswordField || inputContextState.restrictedReason != null
        }
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        isInputViewActive = isEditable
        keyboardVisibilityController.onInputStarted(restarting)
        traceImeVisibility("onStartInput restarting=$restarting")
        
        if (restarting) {
            enforceSmartFeatureDisabledState()
        }
        
        if (info != null && isEditable) {
            // The field as the app asked for it, before the flag the keyboard adds for itself
            appInputType = info to info.inputType
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        
        if (isEditable && isReallyEditable && !restarting && isInputViewActive) {
            val shouldShowSurface = keyboardVisibilityController.shouldShowSurfaceOnInputStart(
                autoShowKeyboardEnabled = SettingsManager.getAutoShowKeyboard(this)
            )
            if (shouldShowSurface) {
                ensureImeSurfaceVisible()
            }
        }
        
        if (!restarting) {
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                val inputConnection = currentInputConnection
                val hasValidInputConnection = inputConnection != null

                // A terminal keeps Nav Mode: its keys move the terminal's cursor
                if (terminalModeActive) {
                    // Nothing to reset
                } else if (isReallyEditable && hasValidInputConnection) {
                    // Remember that nav mode was on before entering the text field
                    navModeWasActiveBeforeEditableField = true
                    navModeController.exitNavMode()
                    resetModifierStates(preserveNavMode = false)
                }
            } else if (isEditable || !ctrlLatchFromNavMode) {
                resetModifierStates(preserveNavMode = false)
            }
        }

        initializeInputContext(restarting)
        suggestionController.onContextReset()

        // Always reset shift one-shot when entering a field (both restarting and new field)
        // Then let auto-cap logic decide if it should be enabled
        if (isEditable) {
            modifierStateController.consumeShiftOneShot()
            
            // Handle input field capitalization flags (CAP_CHARACTERS, CAP_WORDS, CAP_SENTENCES)
            AutoCapitalizeHelper.handleInputFieldCapitalizationFlags(
                context = this,
                state = state,
                inputConnection = currentInputConnection,
                enableCapsLock = { modifierStateController.capsLockEnabled = true },
                enableShiftOneShot = { requestAutoCapShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
            
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
            scheduleStartAutoCapRechecks()
        }

        startClipboardCleanupTimer()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        if (terminalModeActive) TerminalMode.apply(info)
        super.onStartInputView(info, restarting)
        hideSurfaceIfHiddenForApp()
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        updateDebugImeContextSnapshot(info)
        attachTrackpadDecorViewMotionHook("onStartInputView")

        updateInputContextState(info)
        isInputViewActive = inputContextState.isEditable
        updateTrackpadCapture()
        traceImeVisibility("onStartInputView restarting=$restarting")
        if (!restarting) holdBarInAutoFocusedSearch(info)
        if (!restarting) restoreAppLanguage(info)
        if (!restarting) offerPasteSuggestion()
        if (!restarting) offerOneTimeCode()
        if (!restarting) {
            contactChips = emptyList()
            refreshContactSuggestions()
        }
        initializeInputContext(restarting)
        suggestionController.onContextReset()
        
        // Read word at cursor immediately when entering a populated text field
        if (!inputContextState.shouldDisableSuggestions) {
            suggestionController.readInitialContext(currentInputConnection)
        }

        val isEditable = inputContextState.isEditable
        val state = inputContextState
        
        // Always reset shift one-shot when entering a field (both restarting and new field)
        // Then let auto-cap logic decide if it should be enabled
        if (isEditable) {
            modifierStateController.consumeShiftOneShot()
            
            // Handle input field capitalization flags (CAP_CHARACTERS, CAP_WORDS, CAP_SENTENCES)
            AutoCapitalizeHelper.handleInputFieldCapitalizationFlags(
                context = this,
                state = state,
                inputConnection = currentInputConnection,
                enableCapsLock = { modifierStateController.capsLockEnabled = true },
                enableShiftOneShot = { requestAutoCapShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
            
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        }

        // Check if trackpad gestures should be started
        if (::trackpadGestureDetector.isInitialized) {
            val gesturesEnabled = SettingsManager.getTrackpadGesturesEnabled(this)
            if (shouldStartShizukuTrackpadDetector() && !trackpadGestureDetector.isRunning()) {
                val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
                val shizukuAuthorized = try { 
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
                } catch (e: Exception) { false }
                
                if (shizukuRunning && shizukuAuthorized) {
                    Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: Gestures enabled and Shizuku ready, starting detector...")
                    trackpadGestureDetector.start()
                } else {
                    Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: Gestures enabled but Shizuku not ready (running=$shizukuRunning, authorized=$shizukuAuthorized)")
                }
            } else if (!shouldStartShizukuTrackpadDetector() && trackpadGestureDetector.isRunning()) {
                Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: stopping Shizuku detector for non-Shizuku provider")
                trackpadGestureDetector.stop()
            } else if (gesturesEnabled && trackpadGestureDetector.isRunning()) {
                Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: Gestures enabled and detector already running, skipping")
            }
        }
    }

    /** A key held longer than a tap (Android's long-press time), judged at its release. */
    private fun isLongHold(event: KeyEvent): Boolean =
        event.eventTime - event.downTime >= android.view.ViewConfiguration.getLongPressTimeout()

    /** Back to [packageName]'s task, as it was. */
    private fun returnToApp(packageName: String) {
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    override fun onFinishInput() {
        ClicksAccessibilityKeyBridge.trackpadCaptured = false
        // Leaving the field: an email or number typed there is complete
        learnContactDetails(endOfEntry = true)
        contactKeysTyped = 0
        contactDetailsSaved = null
        startAutoCapRechecks.forEach { uiHandler.removeCallbacks(it) }
        startAutoCapRechecks.clear()
        // Niagara's search closed: back to the app unless one opens from it meanwhile
        if (QuickLauncherOpener.NiagaraReturn.onInputFinished(currentInputEditorInfo?.packageName)) {
            uiHandler.postDelayed({
                QuickLauncherOpener.NiagaraReturn.decide()?.let(::returnToApp)
            }, QuickLauncherOpener.NiagaraReturn.DECIDE_AFTER_MS)
        }
        clearInlineAutofill()
        symSticky = false
        emojiSticky = false
        pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
        pendingKeyboardSurfaceTransition = null
        super.onFinishInput()
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        keyboardVisibilityController.cancelPendingSurfaceTransition()
        accidentalKeyPressFilter.reset()
        isInputViewActive = false
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        inputContextState = InputContextState.EMPTY
        multiTapController.cancelAll()
        disableEmojiSearchInputCapture()
        resetModifierStates(preserveNavMode = true)
        // If nav mode was on before entering the text field, turn it back on
        if (navModeWasActiveBeforeEditableField) {
            navModeController.enterNavMode()
            navModeWasActiveBeforeEditableField = false
        } else if (!navModeController.isNavModeActive()) {
            hideStatusIcon()
            lastSystemStatusIconResId = null
        }
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        // Finishing a view does not finish the editor session (Back and backend transitions).
        isInputViewActive = !finishingInput && inputContextState.isEditable
        updateTrackpadCapture()
        traceImeVisibility("onFinishInputView finishing=$finishingInput")
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        stopClipboardCleanupTimer()
        if (finishingInput) {
            multiTapController.cancelAll()
            resetModifierStates(preserveNavMode = true)
            suggestionController.onContextReset()
            if (!navModeController.isNavModeActive()) {
                hideStatusIcon()
                lastSystemStatusIconResId = null
            }
        }
    }

    private fun updateDebugImeContextSnapshot(info: EditorInfo?) {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtype = imm.currentInputMethodSubtype
        val resolvedLayout = runCatching {
            AdditionalSubtypeUtils.resolveActiveLayout(assets, this, subtype)
        }.getOrNull()
        DebugCaptureStore.updateImeContext(
            packageName = info?.packageName ?: currentPackageName,
            inputType = info?.inputType,
            subtypeLocale = subtype?.localeString(),
            resolvedLayout = resolvedLayout,
            physicalProfileOverride = physicalKeyboardProfileOverride
        )
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        keyboardVisibilityController.onImeWindowVisibilityChanged(shown = true)
        updateStatusBarText()
        attachTrackpadDecorViewMotionHook("onWindowShown")
    }

    private fun shouldStartShizukuTrackpadDetector(): Boolean {
        return SettingsManager.getTrackpadGesturesEnabled(this) &&
            SettingsManager.getTrackpadProvider(this) == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU
    }

    private fun isNativeImeTrackpadProviderActive(): Boolean {
        return SettingsManager.getTrackpadGesturesEnabled(this) &&
            SettingsManager.getTrackpadProvider(this) == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME
    }

    private fun attachTrackpadDecorViewMotionHook(reason: String) {
        val decorView = window?.window?.decorView
        if (decorView == null) {
            Log.d(TRACKPAD_DEBUG_TAG, "DecorView hook skipped[$reason]: no IME decorView")
            return
        }

        if (trackpadDecorMotionView === decorView) {
            decorView.isFocusableInTouchMode = true
            decorView.requestFocus()
            Log.d(TRACKPAD_DEBUG_TAG, "DecorView hook refreshed[$reason]: view=${decorView.javaClass.simpleName}")
            return
        }

        trackpadDecorMotionView?.setOnGenericMotionListener(null)
        trackpadDecorMotionView = decorView
        decorView.isFocusableInTouchMode = true
        decorView.requestFocus()
        decorView.setOnGenericMotionListener { _, event ->
            handleNativeImeTrackpadMotion(event, origin = "ime_decor")
        }
        Log.d(
            TRACKPAD_DEBUG_TAG,
            "DecorView hook attached[$reason]: view=${decorView.javaClass.simpleName}, focused=${decorView.isFocused}"
        )
    }
    
    /**
     * Gets the locale from an IME subtype.
     * Falls back to the current subtype, then Italian if no subtype is available.
     */
    private fun getLocaleFromSubtype(subtypeOverride: InputMethodSubtype? = null): Locale {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtype = subtypeOverride ?: imm.currentInputMethodSubtype
        val localeString = subtype?.localeString() ?: "it-IT"
        return try {
            AdditionalSubtypeUtils.localeFromSubtypeString(localeString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse locale from subtype: $localeString", e)
            Locale.ITALIAN
        }
    }

    private fun showAddSubstitutionDialog(word: String) {
        val replacement = word.trim()
        if (replacement.isBlank()) return
        val languageCode = getLocaleFromSubtype().language.ifBlank { "it" }
        try {
            val intent = Intent(this, AddSubstitutionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AddSubstitutionActivity.EXTRA_REPLACEMENT, replacement)
                putExtra(AddSubstitutionActivity.EXTRA_LANGUAGE_CODE, languageCode)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open add-substitution dialog", e)
        }
    }

    private fun getAdditionalSuggestionLocalesForActiveInputStyle(): List<Locale> {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtypeLocale = imm.currentInputMethodSubtype?.localeString()
            ?: getLocaleFromSubtype().toLanguageTag()
        val layout = SettingsManager.getKeyboardLayout(this)
        return SettingsManager.getAdditionalSuggestionLocalesForInputStyle(this, subtypeLocale, layout)
            .filterNot { tag -> tag.equals("x-pastiera", ignoreCase = true) }
            .mapNotNull { tag ->
                try {
                    AdditionalSubtypeUtils.localeFromSubtypeString(tag)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse additional suggestion locale: $tag", e)
                    null
                }
            }
    }
    
    /**
     * Called when the user switches IME subtypes (languages).
     * Reloads the dictionary for the new language and switches to the layout specified in the subtype or JSON mapping.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: android.view.inputmethod.InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        rememberAppLanguage(newSubtype)
        
        if (::suggestionController.isInitialized) {
            val newLocale = getLocaleFromSubtype(newSubtype)
            suggestionController.updateLocale(newLocale)
            if (!inputContextState.shouldDisableSuggestions) {
                suggestionController.readInitialContext(currentInputConnection)
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "IME subtype changed, updating locale to: ${newLocale.language}")
            }
        }
        
        val layoutToUse = AdditionalSubtypeUtils.resolveInputStyleLayout(assets, this, newSubtype)

        val currentLayout = SettingsManager.getKeyboardLayout(this)
        if (layoutToUse != currentLayout) {
            Log.d(TAG, "Switching layout for locale ${newSubtype.localeString()}: $layoutToUse (was: $currentLayout)")
            switchToLayout(layoutToUse, showToast = false)
        } else {
            switchToLayout(layoutToUse, showToast = false)
        }
    }
    
    override fun onWindowHidden() {
        pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
        pendingKeyboardSurfaceTransition = null
        super.onWindowHidden()
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.dismissEmojiPickerPopups()
        }
        keyboardVisibilityController.onImeWindowVisibilityChanged(shown = false)
        SoftwareKeyboardAutoDetector.onInputWindowHidden()
        invalidateRenderedStatusSnapshot()
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        multiTapController.finalizeCycle()
        resetModifierStates(preserveNavMode = true)
        suggestionController.onContextReset()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::textExpansionController.isInitialized) textExpansionController.clear()

        val systemLocalesSignature = newConfig.locales.toLanguageTags()
        if (systemLocalesSignature == lastSystemLocalesSignature) {
            Log.d(TAG, "Configuration changed without locale changes; keeping IME subtypes and editor session")
            return
        }
        lastSystemLocalesSignature = systemLocalesSignature

        // Only locale changes require subtype registration. Physical-keyboard connect/disconnect
        // also changes Configuration and must not restart the active editor session.
        Log.d(TAG, "System locales changed, re-registering IME subtypes")
        Handler(Looper.getMainLooper()).postDelayed({
            // First, remove system locales without dictionary that are no longer in system
            // (only when configuration changes, not when manually adding styles)
            AdditionalSubtypeUtils.removeSystemLocalesWithoutDictionary(this)
            // Then, auto-add new system locales without dictionary
            AdditionalSubtypeUtils.autoAddSystemLocalesWithoutDictionary(this)
            AdditionalSubtypeUtils.registerAdditionalSubtypes(this)
        }, 500) // Small delay to ensure system has processed locale changes
    }
    
    /**
     * Called when the cursor position or selection changes in the text field.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        val perfStart = ImePerfLogger.mark()
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (contactChips.isNotEmpty() || contactFieldKind() != null) refreshContactSuggestions()
        
        val state = inputContextState
        val cursorPositionChanged = (oldSelStart != newSelStart) || (oldSelEnd != newSelEnd)
        val collapsedSelection = newSelStart == newSelEnd
        editorHasActiveSelection = !collapsedSelection
        if (cursorPositionChanged && ::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        val forwardByOne = oldSelStart == oldSelEnd &&
            newSelEnd == newSelStart &&
            newSelStart == oldSelStart + 1
        val shouldSkipForCommit = skipNextSelectionUpdateAfterCommit && collapsedSelection && forwardByOne
        // Clear the flag so subsequent cursor moves are always processed.
        if (skipNextSelectionUpdateAfterCommit) {
            skipNextSelectionUpdateAfterCommit = false
        }

        if (
            symPage == 4 &&
            ::candidatesBarController.isInitialized &&
            candidatesBarController.isEmojiPickerSearchInputActive() &&
            !shouldSkipForCommit
        ) {
            // This callback comes from the app editor, not from the internal emoji search EditText.
            // Any external selection/cursor update means hardware typing should return to the app
            // until the user explicitly focuses the emoji search field again.
            disableEmojiSearchInputCapture()
        }
        
        if (cursorPositionChanged && collapsedSelection && !shouldSkipForCommit) {
            if (!forwardByOne) {
                DeferredPunctuationSpaceTracker.clear()
            }
            // Update suggestions on cursor movement (if suggestions enabled)
            if (!state.shouldDisableSuggestions) {
                suggestionController.onCursorMoved(currentInputConnection)
            }
            // Drop add-word candidate if cursor leaves its word
            suggestionController.clearPendingAddWordIfCursorOutside(currentInputConnection)
            
            // Always update status bar (it handles variations/suggestions internally based on flags).
            // Telegram can emit a selection update for every hardware key. Coalescing keeps
            // expensive InputConnection reads from stacking up behind fast typing.
            scheduleStatusBarTextUpdate()
        }
        if (cursorPositionChanged && ::textExpansionController.isInitialized) {
            if (collapsedSelection) textExpansionController.scheduleRefresh()
            else textExpansionController.clear()
        }
        if (SettingsManager.isSuggestionDebugLoggingEnabled(this)) {
            Log.d(
                TAG,
                "onUpdateSelection old=($oldSelStart,$oldSelEnd) new=($newSelStart,$newSelEnd) collapsed=$collapsedSelection forwardByOne=$forwardByOne skipForCommit=$shouldSkipForCommit"
            )
        }
        
        AutoCapitalizeHelper.cursorAtStart = newSelStart == 0 && newSelEnd == 0
        // A Shift turned off by hand holds only until the text changes (a message sent and the
        // box emptied gets its capital again)
        if (suppressedAutoCapContextKey != null && autoCapContextKey() != suppressedAutoCapContextKey) {
            clearAutoCapSuppression()
        }
        if (cursorPositionChanged || oldSelEnd != newSelEnd) scheduleEditHistoryRecord(EDIT_STEP_CHECK_MS)
        // Auto-cap reads editor context through InputConnection. For simple typing feedback,
        // debounce it so remote editors like Telegram don't pay that cost for every character.
        if (cursorPositionChanged && collapsedSelection && forwardByOne) {
            scheduleAutoCapitalizeOnSelectionChange(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        } else {
            pendingSelectionAutoCapCheck?.let { uiHandler.removeCallbacks(it) }
            pendingSelectionAutoCapCheck = null
            checkAutoCapitalizeOnSelectionChange(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
            // Back to the start of the field (a chat app clearing its box after sending): the
            // text may still be there for a moment, so look again once it's gone
            if (newSelStart == 0 && newSelEnd == 0 && oldSelStart > 0) scheduleStartAutoCapRechecks()
        }
        ImePerfLogger.logDuration(
            label = "onUpdateSelection",
            startNanos = perfStart,
            thresholdMs = 16L,
            details = "old=($oldSelStart,$oldSelEnd) new=($newSelStart,$newSelEnd) forwardByOne=$forwardByOne skip=$shouldSkipForCommit pkg=$currentPackageName"
        )
    }

    private fun remapHardwareEvent(keyCode: Int, event: KeyEvent?): ClicksPowerButtonEventMapper.Result {
        val remapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode,
            event,
            physicalKeyboardProfileOverride
        )
        val isClicksPowerKeyboard = !dispatchingClicksAccessibilityKeyEvent &&
            (event
                ?.takeIf { it.deviceId >= 0 }
                ?.let { inputEvent ->
                    inputEvent.deviceId in connectedClicksInputDeviceIds ||
                        InputDevice.getDevice(inputEvent.deviceId)
                            ?.let(DeviceSpecific::isClicksPowerKeyboard) == true
                } == true)
        return clicksPowerButtonEventMapper.map(
            keyCode = remapped.keyCode,
            event = remapped.event,
            isClicksPowerKeyboard = isClicksPowerKeyboard,
            clicksButtonMode = SettingsManager.getClicksButtonMode(this),
            metaButtonMode = SettingsManager.getClicksMetaButtonMode(this),
            altButtonMode = SettingsManager.getClicksAltButtonMode(this),
            microphoneButtonMode = SettingsManager.getClicksMicrophoneButtonMode(this)
        )
    }

    override fun dispatchClicksAccessibilityKeyEvent(event: KeyEvent): Boolean {
        if (currentInputConnection == null || !inputContextState.isEditable) return false

        val dispatch = {
            dispatchingClicksAccessibilityKeyEvent = true
            try {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                    KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
                }
            } finally {
                dispatchingClicksAccessibilityKeyEvent = false
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch()
        } else {
            uiHandler.post(dispatch)
        }
        return true
    }

    override fun dispatchCapturedTrackpadMotion(event: MotionEvent): Boolean =
        handleNativeImeTrackpadMotion(event, origin = "accessibility")

    /** Keyboard swipes stay with the keyboard while a field is typed in (an option). */
    private fun updateTrackpadCapture() {
        ClicksAccessibilityKeyBridge.trackpadCaptured =
            isInputViewActive && inputContextState.isEditable && !terminalModeActive &&
                !keyboardHiddenForApp && SettingsManager.getTrackpadCaptureWhileTyping(this) &&
                isNativeImeTrackpadProviderActive()
    }

    override fun dispatchClicksDirectAction(action: ClicksButtonDirectAction): Boolean {
        if (action != ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER ||
            currentInputConnection == null || !inputContextState.isEditable
        ) {
            return false
        }
        val dispatch = { toggleEmojiPicker() }
        if (Looper.myLooper() == Looper.getMainLooper()) dispatch() else uiHandler.post(dispatch)
        return true
    }

    private fun toggleEmojiPicker() {
        ensureImeSurfaceVisible()
        symLayoutController.openEmojiPickerPage()
        updateStatusBarText()
    }

    /** The dedicated emoji key: the emoji picker, or the emoji layer (SYM settings). */
    private fun toggleEmojiKeyScreen() {
        ensureImeSurfaceVisible()
        symLayoutController.toggleEmojiKeyPage(layer = SettingsManager.getEmojiKeyOpensLayer(this))
        updateStatusBarText()
    }

    private data class AccidentalKeyInput(
        val resolution: PhysicalKeyResolver.Resolution,
        val configuration: AccidentalKeyPressFilter.Configuration
    )

    private fun accidentalKeyInput(keyCode: Int, event: KeyEvent?): AccidentalKeyInput {
        val device = event
            ?.takeIf { it.deviceId >= 0 }
            ?.let { InputDevice.getDevice(it.deviceId) }
        val isPhysicalKeyboard = device != null &&
            !device.isVirtual &&
            device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        val isClicksPowerKeyboard = device?.let(DeviceSpecific::isClicksPowerKeyboard) == true
        val resolved = physicalKeyResolver.resolve(
                keyCode = keyCode,
                event = event,
                profile = ClicksPowerKeyboardLayout.takeIf { isClicksPowerKeyboard },
                clicksState = if (isClicksPowerKeyboard) {
                    ClicksPowerKeyboardController.currentState().keyboard
                } else {
                    null
                }
            )
        val configuredButtonMode = if (isClicksPowerKeyboard) {
            when (keyCode) {
                KeyEvent.KEYCODE_TAB -> SettingsManager.getClicksButtonMode(this)
                KeyEvent.KEYCODE_META_LEFT -> SettingsManager.getClicksMetaButtonMode(this)
                KeyEvent.KEYCODE_F12 -> SettingsManager.getClicksAltButtonMode(this)
                KeyEvent.KEYCODE_F11 -> SettingsManager.getClicksMicrophoneButtonMode(this)
                else -> null
            }
        } else {
            null
        }
        val isConfiguredModifier = when (configuredButtonMode) {
            SettingsManager.ClicksPowerButtonMode.ALT,
            SettingsManager.ClicksPowerButtonMode.SYM,
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA,
            SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER -> true
            SettingsManager.ClicksPowerButtonMode.TAB -> false
            SettingsManager.ClicksPowerButtonMode.NATIVE,
            null -> resolved.isModifier
        }
        return AccidentalKeyInput(
            resolution = resolved.copy(isModifier = isConfiguredModifier),
            configuration = AccidentalKeyPressPolicy.configuration(
                isPhysicalKeyboard = isPhysicalKeyboard,
                isClicksPowerKeyboard = isClicksPowerKeyboard,
                globalOverlapEnabled = SettingsManager.getOverlappingKeysEnabled(this),
                clicksOverlapMode = SettingsManager.getClicksOverlappingKeysMode(this),
                clicksNumberRowMode = SettingsManager.getClicksNumberRowInputMode(this),
                clicksNumberRowRepeatEnabled = SettingsManager.isClicksNumberRowRepeatEnabled(this),
                longPressThresholdMs = SettingsManager.getLongPressThreshold(this)
            )
        )
    }

    private fun replayProtectedNumberKey(
        keyCode: Int,
        replay: AccidentalKeyPressFilter.KeyUpResult.ReplayTap
    ) {
        replayingProtectedNumberKey = true
        try {
            val downHandled = onKeyDown(keyCode, replay.downEvent)
            if (downHandled) {
                onKeyUp(keyCode, replay.upEvent)
            } else {
                currentInputConnection?.let { inputConnection ->
                    inputConnection.sendKeyEvent(replay.downEvent)
                    inputConnection.sendKeyEvent(replay.upEvent)
                }
            }
        } finally {
            replayingProtectedNumberKey = false
        }
    }

    /** A layout-switch chord doesn't count with the emoji key, or from an open emoji/symbol screen. */
    private fun layoutSwitchChordBlocked(keyCode: Int, symPageWasOpen: Boolean): Boolean {
        if (symPageWasOpen || symPage > 0) return true
        val emojiKey = SettingsManager.getEmojiPickerKey(this)
        return emojiKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == emojiKey
    }

    // The app whose text field was last started: a search bar focused in a newly opened app counts
    // as focused by the app, not by a tap
    private var lastInputPackage: String? = null

    /**
     * "Search bars wait for typing": a search bar that an app focuses as it opens doesn't bring up
     * the keyboard bar; the first key (or tapping the bar) does.
     */
    private fun holdBarInAutoFocusedSearch(info: EditorInfo?) {
        val pkg = info?.packageName
        val openedApp = pkg != null && pkg != lastInputPackage
        lastInputPackage = pkg
        if (!openedApp || pkg == packageName || !SettingsManager.getSearchBarWaitsForTyping(this)) return
        if (ShiftFieldTypes.of(info) != ShiftFieldTypes.Type.SEARCH || !::keyboardVisibilityController.isInitialized) return
        uiHandler.post { keyboardVisibilityController.hideForApp() }
    }

    // Language per app: the app whose language was last restored, so it's done once per visit
    private var languageRestoredForPackage: String? = null

    /** On entering another app, switch to the language last used there (if any). */
    private fun restoreAppLanguage(info: EditorInfo?) {
        val pkg = info?.packageName ?: return
        if (pkg == packageName || pkg == languageRestoredForPackage) return
        languageRestoredForPackage = pkg
        if (!SettingsManager.getLanguagePerAppEnabled(this)) return
        val key = SettingsManager.getAppLanguage(this, pkg) ?: return
        SubtypeCycler.switchToSubtypeKey(this, PhysicalKeyboardInputMethodService::class.java, assets, key)
    }

    /** A language chosen while typing in an app becomes that app's language. */
    private fun rememberAppLanguage(subtype: android.view.inputmethod.InputMethodSubtype) {
        val pkg = currentInputEditorInfo?.packageName ?: return
        if (pkg == packageName || !SettingsManager.getLanguagePerAppEnabled(this)) return
        SettingsManager.setAppLanguage(this, pkg, SubtypeCycler.subtypeKey(subtype))
    }

    // Paste suggestion: the chip offering what was just copied, while it is shown
    private var pasteSuggestionShown = false

    // Remember emails and phone numbers (ContactDetails): keys typed in this field, so only what
    // was typed by hand is kept, and the saved ones for this field's chips
    private var contactKeysTyped = 0
    private var contactDetailsSaved: List<String>? = null
    private var contactChips: List<String> = emptyList()

    private fun contactFieldKind(): ContactDetails.Kind? = when {
        inputContextState.isEmailField -> ContactDetails.Kind.EMAIL
        inputContextState.isPhoneField -> ContactDetails.Kind.PHONE
        else -> null
    }

    /** Whether this field may learn and offer emails and numbers: never private or password fields. */
    private fun contactDetailsAllowed(): Boolean {
        if (!SettingsManager.getLearnContactDetails(this)) return false
        val info = currentInputEditorInfo ?: return false
        val state = inputContextState
        return state.isReallyEditable && !state.isPasswordField && !terminalModeActive && !keyboardHiddenForApp &&
            info.packageName != null
    }

    private fun countContactKey(keyCode: Int, event: KeyEvent?) {
        if ((event?.repeatCount ?: 0) != 0 || KeyEvent.isModifierKey(keyCode)) return
        when (keyCode) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KEYCODE_SYM -> return
        }
        contactKeysTyped++
    }

    private fun savedContactDetails(): List<String> = contactDetailsSaved ?: run {
        val store = it.palsoftware.pastiera.core.suggestions.UserDictionaryStore()
        store.loadUserEntries(this)
        store.getSnapshot().map { it.word }.filter { ContactDetails.kindOf(it) != null }
    }.also { contactDetailsSaved = it }

    /**
     * Keeps an email or phone number just typed by hand. [endOfEntry]: Enter, Tab or leaving the
     * field, the only time a number with spaces is known to be complete.
     */
    private fun learnContactDetails(endOfEntry: Boolean) {
        // Incognito: saved ones are still offered, nothing new is kept
        if (!contactDetailsAllowed() || it.palsoftware.pastiera.core.IncognitoTyping.active) return
        val kind = contactFieldKind()
        if (kind == ContactDetails.Kind.PHONE && !endOfEntry) return
        val connection = currentInputConnection ?: return
        val before = runCatching { connection.getTextBeforeCursor(CONTACT_TEXT_LIMIT, 0) }
            .getOrNull()?.toString() ?: return
        // In an email or phone field, only a whole entry: the cursor in the middle means part of it
        if (kind != null && runCatching { connection.getTextAfterCursor(1, 0) }.getOrNull()?.isNotEmpty() == true) return
        val detail = ContactDetails.toLearn(before, kind, contactKeysTyped) ?: return
        val store = it.palsoftware.pastiera.core.suggestions.UserDictionaryStore()
        store.loadUserEntries(this)
        val saved = store.getSnapshot().map { it.word }
        if (ContactDetails.alreadySaved(saved, detail)) return
        store.addWord(this, detail)
        contactDetailsSaved = null
        if (::suggestionController.isInitialized) suggestionController.refreshUserDictionary()
    }

    /** In an email or phone field: the saved ones that go on from what's typed, as chips. */
    private fun refreshContactSuggestions() {
        if (!::candidatesBarController.isInitialized) return
        val kind = contactFieldKind()
        val matches = if (kind == null || pasteSuggestionShown || !contactDetailsAllowed()) {
            emptyList()
        } else {
            val typed = runCatching { currentInputConnection?.getTextBeforeCursor(CONTACT_TEXT_LIMIT, 0) }
                .getOrNull()?.toString().orEmpty()
            ContactDetails.matching(savedContactDetails(), typed, kind)
        }
        if (matches == contactChips) return
        contactChips = matches
        if (matches.isEmpty()) {
            candidatesBarController.clearExpansionSuggestions()
        } else {
            candidatesBarController.showExpansionSuggestions(matches) { chosen ->
                val connection = currentInputConnection ?: return@showExpansionSuggestions
                val typedLength = connection.getTextBeforeCursor(CONTACT_TEXT_LIMIT, 0)?.length ?: 0
                connection.beginBatchEdit()
                connection.deleteSurroundingText(typedLength, 0)
                connection.commitText(chosen, 1)
                connection.endBatchEdit()
                it.palsoftware.pastiera.core.suggestions.UserDictionaryStore().apply {
                    loadUserEntries(this@PhysicalKeyboardInputMethodService)
                    markUsed(this@PhysicalKeyboardInputMethodService, chosen)
                }
                contactDetailsSaved = null
                contactChips = emptyList()
                candidatesBarController.clearExpansionSuggestions()
                refreshBarChips()
            }
        }
        refreshBarChips()
    }

    /** In a new text field, offer text copied within the last minute as a chip to paste it. */
    private fun offerPasteSuggestion() {
        if (!::clipboardHistoryManager.isInitialized || !::candidatesBarController.isInitialized) return
        if (!SettingsManager.getPasteSuggestionEnabled(this)) return
        val state = inputContextState
        if (!state.isReallyEditable || terminalModeActive || keyboardHiddenForApp) return
        val passwordField = state.isPasswordField
        if (passwordField && !SettingsManager.getPasteSuggestionInPasswordFields(this)) return
        val copy = clipboardHistoryManager.recentCopy ?: return
        if (System.currentTimeMillis() - copy.timestamp > PASTE_SUGGESTION_WINDOW_MS) return
        // A password manager's copy only goes into password fields
        if (copy.sensitive && !passwordField) return
        // In a password field the chip never shows the text, and it's pasted exactly as copied
        val label = if (passwordField) PasteSuggestion.MASKED_LABEL else PasteSuggestion.label(copy.text)
        pasteSuggestionShown = true
        candidatesBarController.showExpansionSuggestions(listOf(label)) { _ ->
            clearPasteSuggestion()
            val pasted = if (passwordField) copy.text else SettingsManager.textToPaste(this, copy.text)
            // A copied code into a number field: typed one character at a time, so apps with a
            // box per digit move along as it goes
            if (inputContextState.isNumericField && looksLikeOneTimeCode(pasted)) typeOneTimeCode(pasted)
            else currentInputConnection?.commitText(pasted, 1)
            clipboardHistoryManager.consumeRecentCopy()
            refreshBarChips()
        }
        refreshBarChips()
    }

    /** A one-time code from a notification, offered as a chip; one tap types it. */
    private fun looksLikeOneTimeCode(text: CharSequence): Boolean =
        text.length in 4..10 && text.all { it.isLetterOrDigit() }

    /**
     * A one-time code typed a character at a time, a moment apart, each into the field in focus
     * then: one field takes the whole code; codes split over a box per digit (which move to the
     * next box as each fills) fill every box. Digits go as key presses, which those boxes listen
     * for; letters as text.
     */
    private fun typeOneTimeCode(code: CharSequence) {
        code.forEachIndexed { index, char ->
            uiHandler.postDelayed({
                val ic = currentInputConnection ?: return@postDelayed
                if (char in '0'..'9') {
                    val keyCode = KeyEvent.KEYCODE_0 + (char - '0')
                    val now = SystemClock.uptimeMillis()
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
                    ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
                } else {
                    ic.commitText(char.toString(), 1)
                }
            }, index * 70L)
        }
    }

    private fun offerOneTimeCode() {
        if (!::candidatesBarController.isInitialized || !SettingsManager.getOneTimeCodesEnabled(this)) return
        val state = inputContextState
        if (!state.isReallyEditable || terminalModeActive || keyboardHiddenForApp) return
        val code = it.palsoftware.pastiera.otp.OneTimeCodes.current() ?: return
        pasteSuggestionShown = true
        candidatesBarController.showExpansionSuggestions(listOf(getString(R.string.one_time_code_chip, code))) { _ ->
            clearPasteSuggestion()
            // The newest code by then, if another arrived since the chip was shown
            typeOneTimeCode(it.palsoftware.pastiera.otp.OneTimeCodes.current() ?: code)
            it.palsoftware.pastiera.otp.OneTimeCodes.consume()
            refreshBarChips()
        }
        refreshBarChips()
    }

    // Inline autofill (Android 11+): the password manager's chips in the suggestion bar
    private var inlineAutofillGeneration = 0

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: android.os.Bundle): android.view.inputmethod.InlineSuggestionsRequest? {
        if (!SettingsManager.getInlineAutofillEnabled(this) || terminalModeActive || keyboardHiddenForApp) {
            DebugCaptureStore.recordAutofill(
                "request declined: setting=${SettingsManager.getInlineAutofillEnabled(this)} terminal=$terminalModeActive hidden=$keyboardHiddenForApp"
            )
            return null
        }
        DebugCaptureStore.recordAutofill("request made for ${currentInputEditorInfo?.packageName}")
        val colours = if (::candidatesBarController.isInitialized) {
            runCatching { candidatesBarController.suggestionChipColours() }.getOrNull()
        } else null
        return InlineAutofill.request(this, colours?.first, colours?.second)
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: android.view.inputmethod.InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        DebugCaptureStore.recordAutofill(
            "response: ${suggestions.size} chips" + suggestions.joinToString(prefix = " [", postfix = "]") { s ->
                "${s.info.source}${if (s.info.isPinned) " pinned" else ""}"
            } + " inputView=$isInputViewShown"
        )
        val generation = ++inlineAutofillGeneration
        if (suggestions.isEmpty() || !::candidatesBarController.isInitialized) {
            clearInlineAutofill()
            return false
        }
        InlineAutofill.inflate(this, suggestions, candidatesBarController.suggestionChipHeight()) { views ->
            // A newer response (another field) replaced this one while it was inflating
            if (generation != inlineAutofillGeneration) {
                DebugCaptureStore.recordAutofill("chips dropped: another field took over")
                return@inflate
            }
            DebugCaptureStore.recordAutofill("inflated ${views.size} chips")
            if (views.isEmpty()) {
                clearInlineAutofill()
            } else {
                candidatesBarController.showInlineAutofill(views)
            }
            refreshBarChips()
        }
        return true
    }

    private fun clearInlineAutofill() {
        inlineAutofillGeneration++
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.clearInlineAutofill()
            refreshBarChips()
        }
    }

    private fun clearPasteSuggestion() {
        if (!pasteSuggestionShown) return
        pasteSuggestionShown = false
        candidatesBarController.clearExpansionSuggestions()
        invalidateRenderedStatusSnapshot()
    }

    // Terminal mode (Termux): see TerminalMode
    private var terminalModeActive = false
    // Keys sent to the terminal with Ctrl: their release goes the same way
    private val terminalCtrlKeysDown = mutableSetOf<Int>()
    // Terminal keys (Enter, Backspace, arrows...) passed to the terminal as pressed
    private val terminalRawKeysDown = mutableSetOf<Int>()
    // Pressed key -> (key sent, meta state sent)
    private val terminalCtrlSent = mutableMapOf<Int, Pair<Int, Int>>()

    // Keys handled by Nav Mode in a terminal: their release goes the same way
    private val terminalNavKeysDown = mutableSetOf<Int>()

    /**
     * Nav Mode in a terminal: a double-tapped (locked) Ctrl turns it on there as outside text
     * fields, and its keys (arrows, Home, End, Page Up and Down...) reach the terminal as those
     * keys, moving its cursor; a tap on Ctrl turns it off. A held or tapped Ctrl still sends real
     * Ctrl combos (Ctrl+C).
     */
    private fun terminalNavModeKey(pressedKeyCode: Int, event: KeyEvent?, down: Boolean): Boolean {
        if (!down) return terminalNavKeysDown.remove(pressedKeyCode)
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (ctrlLatchActive && !ctrlLatchFromNavMode && SettingsManager.getNavModeEnabled(this)) {
            navModeController.enterNavMode()
        }
        if (!navModeController.isNavModeActive()) return false
        val keyCode = event.keyCode
        if (KeyEvent.isModifierKey(keyCode) || keyCode == KEYCODE_SYM || keyCode == KeyEvent.KEYCODE_BACK || symPage != 0) {
            return false
        }
        if (event.repeatCount > 0 && pressedKeyCode !in terminalNavKeysDown) return false
        val handled = navModeController.handleNavModeKey(keyCode, event, isKeyDown = true, ctrlKeyMap = ctrlKeyMap) {
            currentInputConnection
        }
        if (handled) terminalNavKeysDown += pressedKeyCode
        return handled
    }

    /**
     * In a terminal, any Ctrl (held, tapped or latched; not Nav Mode's) sends the key as a real
     * Ctrl combo, with Alt and Shift if they are held too, so Ctrl+C, Ctrl+D and the rest reach
     * the shell instead of Pastiera's cursor and copy keys.
     */
    private fun sendTerminalCtrlCombo(pressedKeyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        val keyCode = event.keyCode
        if (KeyEvent.isModifierKey(keyCode) || keyCode == KEYCODE_SYM || keyCode == KeyEvent.KEYCODE_BACK) return false
        if (event.repeatCount > 0 && pressedKeyCode !in terminalCtrlKeysDown) return false
        if (event.repeatCount == 0) {
            val ctrl = event.isCtrlPressed || ctrlPressed || ctrlPhysicallyPressed || ctrlOneShot ||
                (ctrlLatchActive && !ctrlLatchFromNavMode)
            if (!ctrl) return false
            var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            if (event.isAltPressed || altPhysicallyPressed) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            if (event.isShiftPressed || modifierStateController.shiftPhysicallyPressed) {
                meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            }
            terminalCtrlSent[pressedKeyCode] = keyCode to meta
            terminalCtrlKeysDown += pressedKeyCode
            if (ctrlOneShot) {
                ctrlOneShot = false
                updateStatusBarText()
            }
        }
        sendTerminalCtrlKey(event, KeyEvent.ACTION_DOWN, pressedKeyCode)
        return true
    }

    // The emoji key held as Alt in a terminal (it was pressed as Alt; its Shift meta is dropped)
    private var terminalEmojiAltHeld = false

    /** In a terminal with the emoji key set to Alt, the emoji key's events as Alt's. */
    private fun terminalEmojiKeyAsAlt(keyCode: Int, event: KeyEvent?): KeyEvent? {
        if (event == null || keyCode == KeyEvent.KEYCODE_ALT_LEFT) return null
        val emojiKey = SettingsManager.getEmojiPickerKey(this)
        if (emojiKey == KeyEvent.KEYCODE_UNKNOWN || keyCode != emojiKey) return null
        val asAlt = terminalModeActive &&
            TerminalMode.EmojiKeyAction.byId(SettingsManager.getTerminalModeEmojiKeyAction(this)) == TerminalMode.EmojiKeyAction.Alt
        // A release after leaving the terminal still ends the Alt it began
        if (!asAlt && !(terminalEmojiAltHeld && event.action == KeyEvent.ACTION_UP)) return null
        terminalEmojiAltHeld = event.action == KeyEvent.ACTION_DOWN
        return KeyEvent(
            event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_ALT_LEFT, event.repeatCount,
            dropEmojiKeyShift(event.metaState) or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
            event.deviceId, event.scanCode, event.flags, event.source
        )
    }

    /** While the emoji key (Right Shift) is held as Alt, other keys don't count it as Shift. */
    private fun withoutEmojiAltShift(event: KeyEvent?): KeyEvent? {
        if (!terminalEmojiAltHeld || event == null) return null
        val meta = dropEmojiKeyShift(event.metaState)
        if (meta == event.metaState) return null
        return KeyEvent(
            event.downTime, event.eventTime, event.action, event.keyCode, event.repeatCount,
            meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
            event.deviceId, event.scanCode, event.flags, event.source
        )
    }

    private fun dropEmojiKeyShift(meta: Int): Int {
        if (SettingsManager.getEmojiPickerKey(this) != KeyEvent.KEYCODE_SHIFT_RIGHT) return meta
        val withoutRight = meta and KeyEvent.META_SHIFT_RIGHT_ON.inv()
        return if (withoutRight and KeyEvent.META_SHIFT_LEFT_ON == 0) withoutRight and KeyEvent.META_SHIFT_ON.inv() else withoutRight
    }

    // Emoji keys pressed in a terminal and sent as its terminal key (pressed key -> action)
    private val terminalEmojiKeysDown = mutableMapOf<Int, TerminalMode.EmojiKeyAction>()

    /** The emoji key in a terminal, when it's set to a terminal key instead of the emoji picker. */
    private fun sendTerminalEmojiKeyAction(pressedKeyCode: Int, keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        val emojiKey = SettingsManager.getEmojiPickerKey(this)
        if (emojiKey == KeyEvent.KEYCODE_UNKNOWN || keyCode != emojiKey) return false
        val action = TerminalMode.EmojiKeyAction.byId(SettingsManager.getTerminalModeEmojiKeyAction(this))
        if (action == TerminalMode.EmojiKeyAction.EmojiPicker) return false
        if (action == TerminalMode.EmojiKeyAction.ExtraKeys) {
            if (event.repeatCount == 0) {
                terminalEmojiKeysDown[pressedKeyCode] = action
                toggleExtraKeys()
            }
            return true
        }
        if (event.repeatCount > 0) {
            if (action.repeats) sendTerminalActionKey(action, KeyEvent.ACTION_DOWN, event.repeatCount)
            return true
        }
        terminalEmojiKeysDown[pressedKeyCode] = action
        sendTerminalActionKey(action, KeyEvent.ACTION_DOWN, 0)
        return true
    }

    // The extra keys row: Esc, Tab, arrows and the like in the bar's place (terminals: their own set)
    private var extraKeysOpen = false
    private var extraKeysPackage: String? = null
    private var extraKeysShown: List<ExtraKey> = emptyList()
    private val extraKeysLatched = mutableSetOf<ExtraKey>()
    // Physical keys pressed while the row was open, whose release is the row's too
    private val extraKeysKeysDown = mutableSetOf<Int>()

    private fun toggleExtraKeys() = setExtraKeysOpen(!extraKeysOpen)

    private fun setExtraKeysOpen(open: Boolean) {
        if (open && currentInputConnection == null) return
        extraKeysOpen = open
        extraKeysPackage = if (open) currentInputEditorInfo?.packageName else null
        if (!open) extraKeysLatched.clear()
        renderExtraKeys()
        if (keyboardOutOfSight) syncHiddenAppPanel()
        invalidateRenderedStatusSnapshot()
        updateStatusBarText()
    }

    private fun renderExtraKeys() {
        if (!::candidatesBarController.isInitialized) return
        if (!extraKeysOpen) {
            extraKeysShown = emptyList()
            candidatesBarController.setExtraKeys(null)
            return
        }
        extraKeysShown = if (terminalModeActive) SettingsManager.getExtraKeysTerminal(this) else SettingsManager.getExtraKeysText(this)
        candidatesBarController.setExtraKeys(
            ExtraKeysRow(extraKeysShown, extraKeysLatched.toSet(), ::pressExtraKey) { setExtraKeysOpen(false) }
        )
    }

    private fun extraKeysMeta(): Int {
        var meta = 0
        if (ExtraKey.CTRL in extraKeysLatched) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (ExtraKey.ALT in extraKeysLatched) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        return meta
    }

    /** A key on the row, tapped or pressed through its top-row letter. */
    private fun pressExtraKey(key: ExtraKey) {
        val ic = currentInputConnection ?: return
        if (key.isModifier) {
            if (!extraKeysLatched.remove(key)) extraKeysLatched += key
            renderExtraKeys()
            return
        }
        when {
            key.editAction != 0 -> ic.performContextMenuAction(key.editAction)
            key.text != null -> ic.commitText(key.text, 1)
            else -> sendExtraKeyEvent(ic, key.keyCode, extraKeysMeta())
        }
        if (extraKeysLatched.isNotEmpty()) {
            extraKeysLatched.clear()
            renderExtraKeys()
        }
    }

    private fun sendExtraKeyEvent(ic: android.view.inputmethod.InputConnection, keyCode: Int, meta: Int) {
        val now = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
    }

    /**
     * While the row is open, the top row of letters presses its keys (Q the first, P the tenth).
     * With the row's Ctrl or Alt latched, the next key is that key with the modifier instead, so
     * Ctrl then C is Ctrl+C even where C has a place on the row.
     */
    private fun extraKeysPhysicalKey(pressedKeyCode: Int, keyCode: Int, event: KeyEvent?): Boolean {
        if (!extraKeysOpen || event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (KeyEvent.isModifierKey(pressedKeyCode) || pressedKeyCode == KeyEvent.KEYCODE_BACK ||
            pressedKeyCode == KEYCODE_SYM
        ) return false
        val ic = currentInputConnection ?: return false
        if (event.repeatCount > 0) {
            if (pressedKeyCode !in extraKeysKeysDown) return false
            // Arrows and Delete repeat while held; the rest press once
            val key = extraKeysShown.getOrNull(ExtraKeySets.indexForPhysicalKey(pressedKeyCode))
            if (key != null && key.keyCode != KeyEvent.KEYCODE_UNKNOWN && !key.isModifier && extraKeysLatched.isEmpty()) {
                sendExtraKeyEvent(ic, key.keyCode, 0)
            }
            return true
        }
        if (extraKeysLatched.isNotEmpty()) {
            sendExtraKeyEvent(ic, keyCode, extraKeysMeta() or (event.metaState and KeyEvent.META_SHIFT_MASK))
            extraKeysLatched.clear()
            renderExtraKeys()
            extraKeysKeysDown += pressedKeyCode
            return true
        }
        val key = extraKeysShown.getOrNull(ExtraKeySets.indexForPhysicalKey(pressedKeyCode)) ?: return false
        pressExtraKey(key)
        extraKeysKeysDown += pressedKeyCode
        return true
    }

    private fun sendTerminalActionKey(action: TerminalMode.EmojiKeyAction, keyAction: Int, repeat: Int) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(
            KeyEvent(now, now, keyAction, action.keyCode, repeat, action.metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE)
        )
    }

    private fun sendTerminalCtrlKey(source: KeyEvent, action: Int, pressedKeyCode: Int) {
        val (keyCode, meta) = (if (action == KeyEvent.ACTION_UP) terminalCtrlSent.remove(pressedKeyCode)
            else terminalCtrlSent[pressedKeyCode]) ?: return
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(
            KeyEvent(
                source.downTime, source.eventTime, action, keyCode,
                if (action == KeyEvent.ACTION_DOWN) source.repeatCount else 0, meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    // Keys (as pressed) whose press was sent to the app as its own shortcut; their release is dropped
    private val appShortcutKeysDown = mutableSetOf<Int>()

    /**
     * Universal app shortcuts: a standard combo (Ctrl+F, Alt+Down, ...) pressed in an app with
     * a preset is sent as that app's own shortcut. Returns true when the key was handled.
     */
    /** Each app's own shortcuts, read once per installed version of the app. */
    private val discoveredAppActionsCache = HashMap<String, Pair<Long, DiscoveredAppActions>>()

    private fun discoveredAppActions(packageName: String): DiscoveredAppActions {
        val updated = runCatching { packageManager.getPackageInfo(packageName, 0).lastUpdateTime }.getOrDefault(0L)
        discoveredAppActionsCache[packageName]?.let { (time, actions) -> if (time == updated) return actions }
        return AppActionDiscovery.discover(this, packageName).also {
            discoveredAppActionsCache[packageName] = updated to it
        }
    }

    /**
     * With Ctrl and Alt held, a key whose Alt character is a digit counts as that digit, so
     * Ctrl+Alt+1 works on keyboards without a number row.
     */
    private fun appShortcutKeyCode(event: KeyEvent, ctrl: Boolean, alt: Boolean): Pair<Int, Boolean> {
        val keyCode = event.keyCode
        if (!ctrl || !alt || it.palsoftware.pastiera.shortcuts.ShortcutKeys.charOf(keyCode) != null) return keyCode to alt
        // The keyboard's own Alt character, then the keyboard's Alt layer: a digit keeps Alt
        // (Ctrl+Alt+1), "/" or "," drops it (Ctrl+/), for keyboards without those keys
        val hardwareChar = runCatching { event.keyCharacterMap.get(keyCode, KeyEvent.META_ALT_ON).toChar() }
            .getOrNull()?.takeIf { it.code != 0 }
        val layerChar = { runCatching { AltModifierMappingResolver.resolve(assets, this)[keyCode] }.getOrNull()?.singleOrNull() }
        return it.palsoftware.pastiera.shortcuts.ShortcutKeys.translate(hardwareChar)
            ?: it.palsoftware.pastiera.shortcuts.ShortcutKeys.translate(layerChar())
            ?: (keyCode to alt)
    }

    private fun remapAppShortcut(pressedKeyCode: Int, event: KeyEvent?, hasEditableField: Boolean): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return pressedKeyCode in appShortcutKeysDown
        val keyCode = event.keyCode
        if (KeyEvent.isModifierKey(keyCode) || keyCode == KEYCODE_SYM) return false
        if (::candidatesBarController.isInitialized && candidatesBarController.isEmojiPickerSearchInputActive()) return false
        val heldCtrl = event.isCtrlPressed || ctrlPressed || ctrlPhysicallyPressed
        // In a text field a latched Ctrl (and a held one with "held Ctrl uses Nav Mode") is
        // The keyboard's cursor and selection grid; only a held Ctrl the app would get counts there
        val ctrl = if (hasEditableField) {
            heldCtrl && !SettingsManager.getNavModeCtrlHoldEnabled(this)
        } else {
            heldCtrl || ctrlOneShot || (ctrlLatchActive && !ctrlLatchFromNavMode)
        }
        val alt = event.isAltPressed || altPhysicallyPressed
        // Every standard combo holds Ctrl or Alt; plain typing never gets this far
        if (!ctrl && !alt) return false
        val shift = event.isShiftPressed || shiftPressed || modifierStateController.shiftPhysicallyPressed
        val packageName = currentInputEditorInfo?.packageName ?: currentPackageName ?: return false
        val action = AppShortcutRemapper.resolve(
            AppShortcutSettings.config(this),
            packageName,
            appShortcutKeyCode(event, ctrl, alt).let { (code, withAlt) ->
                KeyCombo(code, ctrl = ctrl, alt = withAlt, shift = shift, meta = event.isMetaPressed)
            },
            inTextField = hasEditableField,
            discovered = { discoveredAppActions(packageName) }
        ) ?: return false
        when (action) {
            is ShortcutAction.SendKeys -> {
                val target = action.combo
                val ic = currentInputConnection ?: return false
                val now = SystemClock.uptimeMillis()
                val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, target.keyCode, 0, target.metaState,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, target.keyCode, 0, target.metaState,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
                // Which combo became which, never any text
                Log.i("PastieraAppShortcuts", "$packageName: $keyCode sent as ${target.serialize()}")
            }
            is ShortcutAction.OpenInApp -> {
                // A suggestion the app on this phone doesn't accept lets the key through
                val intent = action.intents.firstNotNullOfOrNull { appIntent ->
                    appIntent.toIntent(packageName).takeIf { it.resolveActivity(packageManager) != null }
                } ?: return false
                try {
                    startActivity(intent)
                } catch (error: Exception) {
                    Log.w("PastieraAppShortcuts", "$packageName: suggestion not opened", error)
                    return false
                }
                Log.i("PastieraAppShortcuts", "$packageName: $keyCode opened ${intent.action}")
            }
            is ShortcutAction.OpenDiscovered -> {
                val intent = AppActionDiscovery.intentFor(this, packageName, action.action) ?: return false
                try {
                    startActivity(intent)
                } catch (error: Exception) {
                    Log.w("PastieraAppShortcuts", "$packageName: app shortcut not opened", error)
                    return false
                }
                Log.i("PastieraAppShortcuts", "$packageName: $keyCode opened the app's ${action.action.id}")
            }
        }
        appShortcutKeysDown += pressedKeyCode
        // The shortcut has run: Ctrl lets go, a one-shot, locked or Nav Mode one alike, so the
        // next key (typing in the search the shortcut opened) isn't another shortcut
        releaseCtrlAfterShortcut()
        return true
    }

    /** Ctrl off after an app's shortcut ran: one-shot, locked or Nav Mode (a held Ctrl stays held). */
    private fun releaseCtrlAfterShortcut() {
        if (!ctrlOneShot && !ctrlLatchActive && !ctrlLatchFromNavMode && !navModeController.isNavModeActive()) return
        val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
        ctrlOneShot = false
        modifierStateController.clearCtrlState(resetPressedState = false)
        if (wasNavModeLatched) {
            navModeController.cancelNotification()
            navModeController.refreshNavModeState()
        }
        updateStatusBarText()
    }

    override fun onKeyLongPress(keyCode_: Int, event_: KeyEvent?): Boolean {
        if (keyboardHiddenForApp && !hiddenAppKeyGoesToPastiera(keyCode_)) return super.onKeyLongPress(keyCode_, event_)
        if (!replayingProtectedNumberKey) {
            val accidentalInput = accidentalKeyInput(keyCode_, event_)
            accidentalKeyPressFilter.shouldConsumeKeyDown(
                keyCode = keyCode_,
                event = event_,
                resolution = accidentalInput.resolution,
                configuration = accidentalInput.configuration
            )?.let { return true }
        }
        val remapped = remapHardwareEvent(keyCode_, event_)
        if (remapped.consume) return true
        val keyCode = remapped.keyCode
        val event = remapped.event
        // Handle long press even when the keyboard is hidden but we still have a valid InputConnection.
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            return super.onKeyLongPress(keyCode, event)
        }
        
        // If the keyboard is hidden but we have an InputConnection, reactivate it
        if (!isInputViewActive) {
            isInputViewActive = true
            if (!isInputViewShown) {
                ensureImeSurfaceVisible()
            }
        }
        
        // Intercept long presses BEFORE Android handles them
        if (alternateCharacterManager.hasAltMapping(keyCode)) {
            // Consume the event to avoid Android's popup
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    // SYM physically held, wherever its keys go; and whether a chord used it in a hidden app
    private var symKeyHeld = false
    private var hiddenAppSymChordUsed = false

    // Back taken from Niagara's search to return to the app: its release is ours too
    private var niagaraBackReturnTo: String? = null

    override fun onKeyDown(keyCode_: Int, event_: KeyEvent?): Boolean {
        // Keycode 0 types nothing: the keyboard light's wake-up key, passed straight on
        if (keyCode_ == KeyEvent.KEYCODE_UNKNOWN) return super.onKeyDown(keyCode_, event_)
        // The quick launcher in front: its keys go to it, not to the app's field beneath
        if (QuickLauncherActivity.ownsKey(event_)) return false
        // Suggestion swipes: typing keeps them picking; Backspace right after one undoes it
        if ((event_?.repeatCount ?: 0) == 0 && !KeyEvent.isModifierKey(keyCode_)) {
            if (keyCode_ == KeyEvent.KEYCODE_DEL) it.palsoftware.pastiera.core.SuggestionSwipeLearning.onDeleted(this)
            else it.palsoftware.pastiera.core.SuggestionSwipeLearning.onTyped()
        }
        // A letter at the very start of a field: its capital decided now, whatever the app sent
        // before (Instagram's DMs open and clear their box without telling the keyboard in time)
        if ((event_?.repeatCount ?: 0) == 0 && keyCode_ in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z &&
            AutoCapitalizeHelper.cursorAtStart && inputContextState.isEditable && !keyboardHiddenForApp &&
            ::modifierStateController.isInitialized && !symLayoutController.isSymActive() &&
            !modifierStateController.shiftOneShot && !modifierStateController.capsLockEnabled &&
            !modifierStateController.shiftPressed && !ctrlPressed && !ctrlOneShot && !ctrlLatchActive &&
            !altPressed && !altOneShot && !altLatchActive && event_?.isShiftPressed != true
        ) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { false },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = inputContextState
            )
        }
        if (otherFieldShift && otherFieldCapital(keyCode_, event_)) return true
        if (keyCode_ == KEYCODE_SYM && (event_?.repeatCount ?: 0) == 0) {
            symKeyHeld = true
            hiddenAppSymChordUsed = false
        }
        if (keyCode_ == KeyEvent.KEYCODE_BACK && (event_?.repeatCount ?: 0) == 0) {
            // Returning happens on the release: done on the press, the release would reach the
            // app and press Back there too
            QuickLauncherOpener.NiagaraReturn.onBackPressed(currentInputEditorInfo?.packageName)?.let { pkg ->
                niagaraBackReturnTo = pkg
                return true
            }
        }
        terminalEmojiKeyAsAlt(keyCode_, event_)?.let { return onKeyDown(KeyEvent.KEYCODE_ALT_LEFT, it) }
        withoutEmojiAltShift(event_)?.let { return onKeyDown(keyCode_, it) }
        if (emojiPickerKeyUpPending != KeyEvent.KEYCODE_UNKNOWN && keyCode_ != emojiPickerKeyUpPending) {
            emojiPickerKeyChorded = true
        }
        if (keyboardHiddenForApp) {
            val firstPress = (event_?.repeatCount ?: 0) == 0
            if (!firstPress && hiddenAppHoldOpensAccentPicker(keyCode_)) {
                // Holding a letter in a hidden app's text field would open Android's own accent
                // picker there (the key repeats reach its TextView): the first press is enough
                return true
            }
            if (!hiddenAppKeyGoesToPastiera(keyCode_)) {
                if (translateHiddenAppKey(event_)) return true
                if (firstPress) hiddenAppPassedThroughKeys += keyCode_
                HiddenAppKeyObserver.logKey(event_, "input method, to the app")
                observeHiddenAppKey(event_)
                return super.onKeyDown(keyCode_, event_)
            }
            HiddenAppKeyObserver.logKey(event_, "input method, to the keyboard")
            if (firstPress) hiddenAppPastieraKeys += keyCode_
            // SYM + Space in a hidden app (the Niagara home screen): the quick launcher, and SYM
            // doesn't then open its panel
            if (firstPress && symKeyHeld && keyCode_ == KeyEvent.KEYCODE_SPACE) {
                symChordUsedSinceKeyDown = true
                symTogglePendingOnKeyUp = false
                hiddenAppSymChordUsed = true
                openQuickLauncher()
                return true
            }
        }
        if ((event_?.repeatCount ?: 0) == 0) {
            when (keyCode_) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB -> learnContactDetails(endOfEntry = true)
                KeyEvent.KEYCODE_SPACE -> learnContactDetails(endOfEntry = false)
            }
        }
        countContactKey(keyCode_, event_)
        val handled = handleKeyDown(keyCode_, event_)
        if (keyboardHiddenForApp || keyboardOutOfSight) syncHiddenAppPanel()
        return handled
    }

    private fun handleKeyDown(keyCode_: Int, event_: KeyEvent?): Boolean {
        val perfStart = ImePerfLogger.mark()
        try {
        if (!replayingProtectedNumberKey) {
            val accidentalInput = accidentalKeyInput(keyCode_, event_)
            accidentalKeyPressFilter.shouldConsumeKeyDown(
                keyCode = keyCode_,
                event = event_,
                resolution = accidentalInput.resolution,
                configuration = accidentalInput.configuration
            )?.let { suppressed ->
                notifyDebugKeyEvent(
                    keyCode = keyCode_,
                    event = event_,
                    action = "KEY_DOWN_SUPPRESSED",
                    origin = "accidental_keys",
                    outputKeyCodeName = suppressed.debugOutput()
                )
                return true
            }
        }
        val remapped = remapHardwareEvent(keyCode_, event_)
        if (remapped.consume) {
            remapped.directAction?.let { ClicksButtonDirectActionExecutor.execute(this, it) }
            return true
        }
        val keyCode = remapped.keyCode
        val event = remapped.event
        clicksPowerShiftTapFilter.shouldConsumeKeyDown(
            isClicksPowerKeyboard = DeviceSpecific.resolveInputProfile(
                event,
                physicalKeyboardProfileOverride
            ).profileId == "clicks_power",
            keyCode = keyCode,
            event = event
        )?.let { suppressed ->
            notifyDebugKeyEvent(
                keyCode = keyCode,
                event = event,
                action = "KEY_DOWN_SUPPRESSED",
                origin = "clicks_shift_bounce",
                outputKeyCodeName = suppressed.debugOutput()
            )
            return true
        }
        bounceKeyFilter.shouldConsumeKeyDown(this, keyCode, event)?.let { suppressed ->
            notifyDebugKeyEvent(
                keyCode = keyCode,
                event = event,
                action = "KEY_DOWN_SUPPRESSED",
                origin = "bounce_keys",
                outputKeyCodeName = suppressed.debugOutput()
            )
            return true
        }

        if (extraKeysPhysicalKey(keyCode_, keyCode, event)) {
            return true
        }
        if (terminalModeActive && sendTerminalEmojiKeyAction(keyCode_, keyCode, event)) {
            return true
        }

        // Whether an emoji/symbol screen was open when this key came (keys can close it)
        val symPageOpenBeforeKey = symPage > 0

        // Check if we have an editable field at the very start
        val info = currentInputEditorInfo
        val initialInputConnection = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = initialInputConnection != null && inputType != EditorInfo.TYPE_NULL
        if (hasEditableField && !isInputViewActive) {
            isInputViewActive = true
        }
        if (pasteSuggestionShown && event?.repeatCount == 0 && !KeyEvent.isModifierKey(keyCode)) {
            // Typing: the paste suggestion goes away (it is offered once per copy)
            clearPasteSuggestion()
            if (::clipboardHistoryManager.isInitialized) clipboardHistoryManager.consumeRecentCopy()
        }
        if (remapAppShortcut(keyCode_, event, hasEditableField)) {
            return true
        }
        if (terminalModeActive && terminalNavModeKey(keyCode_, event, down = true)) {
            return true
        }
        if (terminalModeActive && sendTerminalCtrlCombo(keyCode_, event)) {
            return true
        }
        if (terminalModeActive && TerminalMode.isTerminalKey(keyCode) && symPage == 0 &&
            event?.isAltPressed != true && !altLatchActive && !altOneShot
        ) {
            terminalRawKeysDown += keyCode_
            return super.onKeyDown(keyCode, event)
        }
        if (hasEditableField && ::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }

        if (shouldPlayTypingSound(hasEditableField, keyCode, event)) {
            typingSoundPlayer.play(keyCode)
        }

        // Shift + Backspace (Text input > Shift + Backspace) deletes the character after the
        // cursor. Only a held Shift, which the key event itself may not report on the Titan: an
        // automatic capital or a tapped Shift leaves Backspace as it is. A selection is deleted as usual.
        if (keyCode == KeyEvent.KEYCODE_DEL && hasEditableField && symPage == 0 && !terminalModeActive &&
            SettingsManager.getShiftBackspaceDelete(this) &&
            (shiftPhysicallyPressed || event?.isShiftPressed == true) &&
            event?.isCtrlPressed != true && event?.isAltPressed != true
        ) {
            val ic = initialInputConnection
            if (ic.getSelectedText(0).isNullOrEmpty()) {
                ic.deleteSurroundingTextInCodePoints(0, 1)
                return true
            }
        }

        val emojiSearchCtrlActive = event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
        val emojiSearchCandidateActive =
            hasEditableField &&
                symPage == 4 &&
                keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KEYCODE_SYM &&
                !isPureModifierKey(keyCode) &&
                ::candidatesBarController.isInitialized &&
                candidatesBarController.isEmojiPickerSearchInputActive()
        if (emojiSearchCandidateActive) {
            ensureEmojiSearchCursorAnchorMonitoring(initialInputConnection)
            if (shouldReturnEmojiSearchFocusToApp(initialInputConnection)) {
                disableEmojiSearchInputCapture()
            }
        }
        // Let the picker handle text-editing shortcuts before the generic Ctrl router can
        // touch the app editor selection.
        if (
            emojiSearchCandidateActive &&
            candidatesBarController.isEmojiPickerSearchInputActive() &&
            candidatesBarController.handleEmojiPickerSearchKeyDown(
                event,
                emojiSearchCtrlActive,
                // Search is lower case: no automatic capital or Shift from the app's field. Alt
                // (held, tapped or locked) types the key's Alt character, "1" for 100 emoji, then
                // lets go
                resolveTypedText = { typedEvent ->
                    val altOn = typedEvent.isAltPressed || altPhysicallyPressed || altOneShot || altLatchActive
                    val altChar = if (altOn) alternateCharacterManager.getAltModifierMappings()[typedEvent.keyCode] else null
                    if (altChar != null) {
                        if (!altPhysicallyPressed && !typedEvent.isAltPressed) {
                            altOneShot = false
                            if (altLatchActive) modifierStateController.clearAltState()
                            updateStatusBarText()
                        }
                        altChar
                    } else {
                        getCharacterFromLayout(
                            typedEvent.keyCode,
                            typedEvent,
                            false
                        )?.toString()?.lowercase()
                    }
                }
            )
        ) {
            updateEmojiSearchExternalSelectionSnapshot(initialInputConnection)
            ensureEmojiSearchCursorAnchorMonitoring(initialInputConnection)
            return true
        }
        val emojiSearchInputConnection =
            if (emojiSearchCandidateActive && candidatesBarController.isEmojiPickerSearchInputActive()) {
                candidatesBarController.createEmojiPickerSearchInputConnection()
            } else {
                null
            }
        if (emojiSearchInputConnection != null && emojiSearchCtrlActive) {
            val handled = inputEventRouter.handleCtrlModifiedKey(
                keyCode = keyCode,
                event = event,
                inputConnection = emojiSearchInputConnection,
                ctrlKeyMap = ctrlKeyMap,
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlOneShot = ctrlOneShot,
                ctrlPhysicallyPressed = ctrlPhysicallyPressed || ctrlPressed,
                clearCtrlOneShot = { ctrlOneShot = false },
                updateStatusBar = { updateStatusBarText() },
                callSuper = { false },
                toggleMinimalUi = { keyboardVisibilityController.togglePastierinaMode() }
            )
            if (handled) {
                updateEmojiSearchExternalSelectionSnapshot(initialInputConnection)
                ensureEmojiSearchCursorAnchorMonitoring(initialInputConnection)
                return true
            }
        }

        val expansionShortcutModifierActive = event?.isCtrlPressed == true ||
            event?.isAltPressed == true || event?.isMetaPressed == true || event?.isShiftPressed == true ||
            ctrlPressed || ctrlPhysicallyPressed || ctrlLatchActive || ctrlOneShot || ctrlLatchFromNavMode ||
            altPressed || altPhysicallyPressed || altLatchActive || altOneShot ||
            shiftPressed || modifierStateController.shiftPhysicallyPressed || shiftLayerLatched || shiftOneShot
        if (hasEditableField && !expansionShortcutModifierActive &&
            ::textExpansionController.isInitialized &&
            textExpansionController.handleKeyDown(keyCode)
        ) {
            return true
        }

        if (hasEditableField && keyCode == KEYCODE_SYM && event?.repeatCount == 0) {
            symTogglePendingOnKeyUp = true
            symChordUsedSinceKeyDown = false
            symPhysicallyPressed = true
            updateStatusBarText() // SYM's LED shows it held
        }

        // The emoji key held, or SYM or the emoji key tapped: the next key types its emoji or symbol
        if (
            hasEditableField &&
            event?.repeatCount == 0 &&
            keyCode != KEYCODE_SYM &&
            keyCode != emojiPickerKeyUpPending &&
            keyCode != SettingsManager.getEmojiPickerKey(this) &&
            !isPureModifierKey(keyCode) &&
            keyCode != KeyEvent.KEYCODE_BACK
        ) {
            val shift = event.isShiftPressed || shiftOneShot || capsLockEnabled
            val ctrlOrAlt = event.isCtrlPressed || event.isAltPressed || ctrlPhysicallyPressed || altPhysicallyPressed
            val emojiHeld = emojiPickerKeyUpPending != KeyEvent.KEYCODE_UNKNOWN &&
                KeyEvent.isModifierKey(emojiPickerKeyUpPending) && !ctrlOrAlt
            val text = when {
                emojiHeld || emojiSticky -> symLayoutController.resolveEmojiLayerSymbol(keyCode, shift)
                symSticky -> symLayoutController.resolveChordSymbol(keyCode, shift)
                else -> null
            }
            val wasSticky = symSticky || emojiSticky
            if (emojiHeld || wasSticky) {
                symSticky = false
                emojiSticky = false
                if (!text.isNullOrEmpty()) {
                    val inputConnection = currentInputConnection
                    if (!handleBoundaryTextBeforeCommit(text, inputConnection)) inputConnection?.commitText(text, 1)
                    rememberLayerText(text)
                    updateStatusBarText()
                    return true
                }
                if (wasSticky) updateStatusBarText()
            }
        }

        // Minimal Phone dedicated keys (skip while Alt is active so its configured mapping can run)
        // Gate this behavior to Minimal Phone devices only.
        val altActiveForDedicatedKeys = event?.isAltPressed == true || altLatchActive || altOneShot
        if (
            hasEditableField &&
            isMinimalPhoneHardwareActive() &&
            keyCode == KEYCODE_EM &&
            event?.repeatCount == 0 &&
            !altActiveForDedicatedKeys
        ) {
            if (symPage == 4) {
                symLayoutController.closeSymPage()
                updateStatusBarText()
            } else {
                ensureImeSurfaceVisible()
                symLayoutController.openEmojiPickerPage()
                updateStatusBarText()
            }
            return true
        }
        if (
            hasEditableField &&
            isMinimalPhoneHardwareActive() &&
            keyCode == KEYCODE_MIC &&
            event?.repeatCount == 0 &&
            !altActiveForDedicatedKeys
        ) {
            startSpeechRecognition()
            return true
        }

        // User-selected dedicated emoji picker key (SYM settings > Emoji picker key).
        // Consumed entirely so it never reaches the modifier state machine; outside text
        // fields the key keeps its normal role.
        val emojiPickerKey = SettingsManager.getEmojiPickerKey(this)
        if (
            hasEditableField &&
            emojiPickerKey != KeyEvent.KEYCODE_UNKNOWN &&
            keyCode == emojiPickerKey
        ) {
            if ((event?.repeatCount ?: 0) == 0) {
                if (KeyEvent.isModifierKey(keyCode)) {
                    emojiPickerKeyChorded = event?.isCtrlPressed == true || event?.isAltPressed == true ||
                        ctrlPhysicallyPressed || altPhysicallyPressed
                } else {
                    toggleEmojiKeyScreen()
                }
            }
            emojiPickerKeyUpPending = keyCode
            updateStatusBarText() // the emoji key's LED shows it held
            return true
        }

        if (
            hasEditableField &&
            (symTogglePendingOnKeyUp || event?.isSymPressed == true) &&
            SettingsManager.getPowerShortcutsEnabled(this) &&
            SettingsManager.getQuickLauncherTextFieldShortcuts(this) &&
            SettingsManager.getLauncherShortcut(this, keyCode) != null &&
            event?.repeatCount == 0
        ) {
            symChordUsedSinceKeyDown = true
            if (launcherShortcutController.handleLauncherShortcut(keyCode)) {
                return true
            }
        }

        if (
            hasEditableField &&
            event?.repeatCount == 0 &&
            SettingsManager.getQuickLauncherAltShortcutsOutsideTextFields(this) &&
            SettingsManager.getQuickLauncherAltSpaceInTextFields(this) &&
            SettingsManager.getLauncherShortcut(this, keyCode) != null &&
            (event.isAltPressed || altPressed || altPhysicallyPressed || altLatchActive || altOneShot)
        ) {
            modifierStateController.clearAltState()
            updateStatusBarText()
            if (launcherShortcutController.handleLauncherShortcut(keyCode)) {
                return true
            }
        }

        if (
            hasEditableField &&
            symTogglePendingOnKeyUp &&
            keyCode != KEYCODE_SYM &&
            event?.repeatCount == 0 &&
            !isPureModifierKey(keyCode)
        ) {
            symChordUsedSinceKeyDown = true
            val symChar = symLayoutController.resolveChordSymbol(
                keyCode = keyCode,
                shiftPressed = event.isShiftPressed || shiftOneShot || capsLockEnabled
            )
            if (!symChar.isNullOrEmpty()) {
                val inputConnection = currentInputConnection
                if (!handleBoundaryTextBeforeCommit(symChar, inputConnection)) {
                    inputConnection?.commitText(symChar, 1)
                }
                rememberLayerText(symChar)
                updateStatusBarText()
                return true
            }
        }

        // If any SYM page or clipboard overlay is open, close on BACK and consume
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (extraKeysOpen) {
                setExtraKeysOpen(false)
                return true
            }
            if (candidatesBarController.handleBackPressed()) {
                return true
            }
            if (symLayoutController.isSymActive()) {
                if (symLayoutController.closeSymPage()) {
                    updateStatusBarText()
                    return true
                }
            }
            // A tapped SYM or emoji key waiting for its one key: Back cancels it, as it closes a page
            if (symSticky || emojiSticky) {
                symSticky = false
                emojiSticky = false
                updateStatusBarText()
                return true
            }
            // A user dismissal wins over an in-flight backend switch or queued recovery.
            pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
            pendingKeyboardSurfaceTransition = null
            keyboardVisibilityController.cancelPendingSurfaceTransition()
        }

        val navModeBefore = navModeController.isNavModeActive()
        val ctrlActiveBeforePrelude = event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode

        val isModifierKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT

        if (event?.repeatCount == 0 && isModifierKey) {
            // Pressing a latched key again cancels the visual latch and restores logical state.
            if ((keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) && shiftLayerLatched) {
                shiftLayerLatched = false
                lastShiftTapUpTime = 0L
                // Tapping SHIFT while the visual Shift layer is latched should fully disable Shift.
                // Restoring the pre-hold snapshot here can resurrect stale one-shot/caps state.
                modifierStateController.clearShiftState(resetPressedState = true)
                modifierStateBeforeHold = null
                updateStatusBarText()
                return true
            }
            if ((keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) && altModifierLayerLatched) {
                altModifierLayerLatched = false
                lastAltTapUpTime = 0L
                // Tapping ALT while the visual Device SYM layer is latched should fully disable Alt.
                // Restoring the pre-hold snapshot here can resurrect stale one-shot/latch state.
                modifierStateController.clearAltState(resetPressedState = true)
                modifierStateBeforeHold = null
                updateStatusBarText()
                return true
            }

            modifierStateBeforeHold = modifierStateController.captureLogicalState()
            variationInteractedDuringHold = false
            otherKeyInteractedDuringHold = false
            modifierDownTimes[keyCode] = event.eventTime
        } else if (!isModifierKey && event?.repeatCount == 0) {
            if (
                CompatibilityWorkarounds.isClicksPowerSyntheticShiftChord(
                    profileId = DeviceSpecific.resolveInputProfile(
                        event,
                        physicalKeyboardProfileOverride
                    ).profileId,
                    event = event,
                    activeShiftDownTimes = sequenceOf(
                        modifierDownTimes[KeyEvent.KEYCODE_SHIFT_LEFT],
                        modifierDownTimes[KeyEvent.KEYCODE_SHIFT_RIGHT]
                    ).filterNotNull()
                )
            ) {
                modifierStateBeforeHold?.let(modifierStateController::restoreLogicalState)
                updateStatusBarText()
            }
            otherKeyInteractedDuringHold = true
            lastShiftTapUpTime = 0L
            lastAltTapUpTime = 0L
        }

        multiTapController.resetForNewKey(keyCode)
        if (!isModifierKey) {
            modifierStateController.registerNonModifierKey()
        }
        
        // If NO editable field is active, handle ONLY nav mode
        if (!hasEditableField) {
            val powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(this)
            return inputEventRouter.handleKeyDownWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isShortcutKey = { code -> isShortcutKey(code) },
                    isLauncherPackage = { pkg -> launcherShortcutController.isLauncher(pkg) },
                    handleLauncherShortcut = { key -> launcherShortcutController.handleLauncherShortcut(key) },
                    handlePowerShortcut = { key -> launcherShortcutController.handlePowerShortcut(key) },
                    togglePowerShortcutMode = { message, isNavModeActive -> 
                        launcherShortcutController.togglePowerShortcutMode(
                            showToast = { showPowerShortcutToast(it) },
                            isNavModeActive = isNavModeActive
                        )
                    },
                    callSuper = { super.onKeyDown(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                ),
                ctrlLatchActive = ctrlLatchActive,
                editorInfo = info,
                currentPackageName = currentPackageName,
                powerShortcutsEnabled = powerShortcutsEnabled
            )
        }
        
        val routingResult = inputEventRouter.handleEditableFieldKeyDownPrelude(
            keyCode = keyCode,
            params = InputEventRouter.EditableFieldKeyDownParams(
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlLatchActive = ctrlLatchActive,
                isInputViewActive = isInputViewActive,
                isImeSurfaceRequestedOrShown =
                    !keyboardVisibilityController.shouldRecoverSurfaceOnHardwareKey(),
                hasInputConnection = hasEditableField
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownCallbacks(
                exitNavMode = { navModeController.exitNavMode() },
                ensureImeSurfaceVisible = {
                    keyboardVisibilityController.onHardwareInputRequested()
                },
                callSuper = { super.onKeyDown(keyCode, event) }
            )
        )
        when (routingResult) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> return true
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> return super.onKeyDown(keyCode, event)
            InputEventRouter.EditableFieldRoutingResult.Continue -> {}
        }
        
        // Layout-switch chords never fire from an emoji/symbol screen or with the emoji key: Shift
        // then an action key there (the emoji key on Alt or Shift, Alt closing a layer) is not
        // a request to change layout
        val layoutSwitchChordsAllowed = !layoutSwitchChordBlocked(keyCode, symPageOpenBeforeKey)

        // Handle Alt+Shift for subtype cycling
        if (
            layoutSwitchChordsAllowed &&
            hasEditableField &&
            event != null &&
            event.repeatCount == 0 &&
            SettingsManager.isAltShiftLayoutSwitchEnabled(this) &&
            (((keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) &&
                (event.isAltPressed || altPhysicallyPressed)) ||
                ((keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) &&
                    (event.isShiftPressed || shiftPhysicallyPressed)))
        ) {
            modifierStateController.clearAltState(resetPressedState = true)
            modifierStateController.clearShiftState(resetPressedState = true)

            val showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            SubtypeCycler.cycleToNextSubtype(
                context = this,
                imeServiceClass = PhysicalKeyboardInputMethodService::class.java,
                assets = assets,
                showToast = showToast
            )

            updateStatusBarText()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && event?.repeatCount == 0) {
            // Recover if a previous chord lost its key-up while the input context changed.
            consumeAltEnterUntilKeyUp = false
        }

        // Keep repeats from a consumed Alt+Enter chord away from the editor.
        if (keyCode == KeyEvent.KEYCODE_ENTER && consumeAltEnterUntilKeyUp) {
            return true
        }

        // Handle Alt+Enter for subtype cycling
        if (
            layoutSwitchChordsAllowed &&
            hasEditableField &&
            event != null &&
            event.repeatCount == 0 &&
            SettingsManager.isAltEnterLayoutSwitchEnabled(this) &&
            (keyCode == KeyEvent.KEYCODE_ENTER &&
                    (event.isAltPressed || altPhysicallyPressed))
        ) {
            consumeAltEnterUntilKeyUp = true
            modifierStateController.clearAltState(resetPressedState = true)

            val showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            SubtypeCycler.cycleToNextSubtype(
                context = this,
                imeServiceClass = PhysicalKeyboardInputMethodService::class.java,
                assets = assets,
                showToast = showToast
            )

            updateStatusBarText()
            return true
        }

        // A suggestion picked from the keyboard (Ctrl+Shift+Q/W/E by default): only while the bar
        // shows suggestions, otherwise the keys do what they always did
        if (hasEditableField && event != null && event.repeatCount == 0 && symPage == 0) {
            val slot = SuggestionKeys.slotFor(
                SettingsManager.getSuggestionKeys(this),
                keyCode,
                ctrl = event.isCtrlPressed || ctrlPhysicallyPressed,
                shift = event.isShiftPressed || shiftPhysicallyPressed,
                alt = event.isAltPressed || altPhysicallyPressed
            )
            if (slot != null && visibleSuggestionStrings().isNotEmpty()) {
                suggestionKeyUpPending = keyCode
                acceptSuggestionAtIndex(slot)
                return true
            }
        }

        // Ctrl + Shift + D: add the last word typed to the dictionary
        if (
            hasEditableField &&
            keyCode == KeyEvent.KEYCODE_D &&
            event?.repeatCount == 0 &&
            (event.isCtrlPressed || ctrlPressed || ctrlLatchActive || ctrlOneShot) &&
            (event.isShiftPressed || shiftPhysicallyPressed) &&
            SettingsManager.getAddLastWordShortcut(this)
        ) {
            modifierStateController.clearCtrlState(resetPressedState = true)
            modifierStateController.clearShiftState(resetPressedState = true)
            addLastWordToDictionary()
            updateStatusBarText()
            return true
        }

        // Handle Ctrl+Space for subtype cycling
        if (
            layoutSwitchChordsAllowed &&
            hasEditableField &&
            keyCode == KeyEvent.KEYCODE_SPACE &&
            SettingsManager.isCtrlSpaceLayoutSwitchEnabled(this) &&
            (event?.isCtrlPressed == true || ctrlPressed || ctrlLatchActive || ctrlOneShot)
        ) {
            var shouldUpdateStatusBar = false

            // Clear Alt state if active so we don't leave Alt latched.
            val hadAlt = altLatchActive || altOneShot || altPressed
            if (hadAlt) {
                modifierStateController.clearAltState(resetPressedState = true)
                shouldUpdateStatusBar = true
            }

            // Always reset Ctrl state after Ctrl+Space to avoid leaving it active.
            val hadCtrl = ctrlLatchActive ||
                ctrlOneShot ||
                ctrlPressed ||
                ctrlPhysicallyPressed ||
                ctrlLatchFromNavMode
            if (hadCtrl) {
                val navModeLatched = ctrlLatchFromNavMode
                val keepLockedCtrl = ctrlLatchActive &&
                    !ctrlLatchFromNavMode &&
                    SettingsManager.getCtrlTapLatches(this) &&
                    SettingsManager.getCtrlLatchStaysOnSpace(this)
                if (keepLockedCtrl) {
                    modifierStateController.ctrlOneShot = false
                    modifierStateController.ctrlPressed = false
                    modifierStateController.ctrlPhysicallyPressed = false
                    modifierStateController.ctrlLatchFromNavMode = false
                } else {
                    modifierStateController.clearCtrlState(resetPressedState = true)
                }
                if (navModeLatched && !keepLockedCtrl) {
                    navModeController.cancelNotification()
                    navModeController.refreshNavModeState()
                }
                shouldUpdateStatusBar = true
            }

            // Cycle to the next subtype; with Shift too (Ctrl+Shift+Space), to the previous one
            // (palsoftware/pastiera#267)
            val backwards = event?.isShiftPressed == true || shiftPhysicallyPressed
            if (backwards) modifierStateController.clearShiftState(resetPressedState = true)
            val showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            if (SubtypeCycler.cycleToNextSubtype(this, PhysicalKeyboardInputMethodService::class.java, assets, showToast = showToast, backwards = backwards)) {
                shouldUpdateStatusBar = true
            }

            if (shouldUpdateStatusBar) {
                updateStatusBarText()
            }
            return true
        }

        val ic = currentInputConnection
        val state = inputContextState
        val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !state.shouldDisableAutoCorrect
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL) {
            DeferredPunctuationSpaceTracker.clear()
        }

        clearAltOnBoundaryIfNeeded(keyCode) { updateStatusBarText() }
        if (keyCode == KeyEvent.KEYCODE_ENTER && modifierStateController.consumeShiftOneShot()) {
            updateStatusBarText()
        }

        if (handleEnterAsEditorAction(
                keyCode,
                info,
                ic,
                event,
                isAutoCorrectEnabled,
                ctrlActiveBeforePrelude
            )
        ) {
            return true
        }
        
        val altActiveNow = event?.isAltPressed == true || altLatchActive || altOneShot
        val debugUnicodeOverride = resolveAltMappedUnicodeForDebug(
            keyCode = keyCode,
            altActive = altActiveNow
        )

        // Continue with normal IME logic
        notifyDebugKeyEvent(
            keyCode = keyCode,
            event = event,
            action = "KEY_DOWN",
            origin = "ime_service",
            unicodeCharOverride = debugUnicodeOverride
        )
        val ctrlActiveNow = event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
        when {
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL -> {
                DeferredPunctuationSpaceTracker.clear()
            }
            !altActiveNow && !ctrlActiveNow && ic != null -> {
                val typedText = when {
                    keyCode == KeyEvent.KEYCODE_SPACE -> " "
                    event?.unicodeChar?.takeIf { it != 0 } != null -> event.unicodeChar.toChar().toString()
                    else -> ""
                }
                val insertedDeferredSpace =
                    DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, typedText)
                if (insertedDeferredSpace) {
                    suggestionController.onContextReset()
                    if (typedText.firstOrNull()?.isLetter() == true) {
                        AutoCapitalizeHelper.enableAfterPunctuation(
                            context = this,
                            inputConnection = ic,
                            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
                            onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                            disableShift = { modifierStateController.consumeShiftOneShot() },
                            onUpdateStatusBar = { updateStatusBarText() }
                        )
                    }
                }
            }
        }
        if (
            inputEventRouter.handleConfiguredForwardDeleteAlternatives(
                context = this,
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                altActive = altActiveNow
            )
        ) {
            return true
        }
        if (!altActiveNow) {
            if (
                inputEventRouter.handleTextInputPipeline(
                    context = this,
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    shouldDisableSuggestions = state.shouldDisableSuggestions,
                    shouldDisableAutoCorrect = state.shouldDisableAutoCorrect,
                    shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
                    shouldDisableDoubleSpaceToPeriod = state.shouldDisableDoubleSpaceToPeriod,
                    isAutoCorrectEnabled = isAutoCorrectEnabled,
                    textInputController = textInputController,
                    autoCorrectionManager = autoCorrectionManager,
                    inputContextState = state,
                    enableShiftOneShot = { requestAutoCapShiftOneShot() },
                    editorInfo = info
                ) { updateStatusBarText() }
            ) {
                return true
            }
        }

        if (!altActiveNow && !ctrlActiveNow && handleVietnameseTelexKey(keyCode, event, ic)) {
            return true
        }
        
        val routingDecision = inputEventRouter.routeEditableFieldKeyDown(
            keyCode = keyCode,
            event = event,
            params = InputEventRouter.EditableFieldKeyDownHandlingParams(
                inputConnection = ic,
                isNumericField = isNumericField,
                isInputViewActive = isInputViewActive,
                shiftPressed = shiftPressed,
                shiftLayerLatched = shiftLayerLatched,
                ctrlPressed = ctrlPressed,
                ctrlPhysicallyPressed = ctrlPhysicallyPressed,
                altPressed = altPressed,
                ctrlLatchActive = ctrlLatchActive,
                altLatchActive = altLatchActive,
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlKeyMap = ctrlKeyMap,
                ctrlOneShot = ctrlOneShot,
                altOneShot = altOneShot,
                clearAltOnSpaceEnabled = clearAltOnSpaceEnabled,
                shiftOneShot = shiftOneShot,
                capsLockEnabled = capsLockEnabled,
                cursorUpdateDelayMs = CURSOR_UPDATE_DELAY,
                altMappingsOverride = if (dispatchingSoftwareKeyboardKey) {
                    AltModifierMappingResolver.resolve(assets, this)
                } else {
                    null
                },
                shouldDisableSmartFeatures = shouldDisableSmartFeatures
            ),
            controllers = InputEventRouter.EditableFieldKeyDownControllers(
                modifierStateController = modifierStateController,
                symLayoutController = symLayoutController,
                alternateCharacterManager = alternateCharacterManager,
                variationStateController = variationStateController,
                textInputController = textInputController
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownHandlingCallbacks(
                updateStatusBar = { updateStatusBarText() },
                refreshStatusBar = { refreshStatusBar() },
                disableShiftOneShot = {
                    modifierStateController.consumeShiftOneShot()
                },
                clearAltOneShot = { altOneShot = false },
                clearCtrlOneShot = { ctrlOneShot = false },
                getCharacterFromLayout = { code, keyEvent, isShiftPressed ->
                    getCharacterFromLayout(code, keyEvent, isShiftPressed)
                },
                isAlphabeticKey = { code -> isAlphabeticKey(code) },
                callSuper = { super.onKeyDown(keyCode, event) },
                callSuperWithKey = { defaultKeyCode, defaultEvent ->
                    super.onKeyDown(defaultKeyCode, defaultEvent)
                },
                startSpeechRecognition = { startSpeechRecognition() },
                getMapping = { code -> LayoutMappingRepository.getMapping(code) },
                handleMultiTapCommit = { code, mapping, uppercase, inputConnection, allowLongPress ->
                    handleMultiTapCommit(code, mapping, uppercase, inputConnection, allowLongPress)
                },
                isLongPressSuppressed = { code ->
                    multiTapController.isLongPressSuppressed(code)
                },
                toggleMinimalUi = { keyboardVisibilityController.togglePastierinaMode() },
                handleBoundaryText = { text, inputConnection ->
                    handleBoundaryTextBeforeCommit(text, inputConnection)
                },
                onShiftOneShotToggledOff = { suppressAutoCapRenderingAtCursorIfNeeded() }
            )
        )

        val navModeAfter = navModeController.isNavModeActive()
        if (navModeBefore != navModeAfter) {
            suggestionController.onNavModeToggle()
        }

        return when (routingDecision) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> true
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> super.onKeyDown(keyCode, event)
            InputEventRouter.EditableFieldRoutingResult.Continue -> super.onKeyDown(keyCode, event)
        }
        } finally {
            ImePerfLogger.logDuration(
                label = "onKeyDown",
                startNanos = perfStart,
                thresholdMs = 16L,
                details = "key=${KeyEvent.keyCodeToString(keyCode_)} repeat=${event_?.repeatCount ?: -1} pkg=$currentPackageName"
            )
        }
    }

    private fun shouldPlayTypingSound(hasEditableField: Boolean, keyCode: Int, event: KeyEvent?): Boolean {
        if (!hasEditableField || event?.repeatCount != 0) {
            return false
        }
        return keyCode != KeyEvent.KEYCODE_BACK
    }

    /** An emoji or symbol typed through a layer chord (or a tapped SYM or emoji key) joins its recents. */
    private fun rememberLayerText(text: String) {
        val first = text.codePointAt(0)
        if (first >= 0x1F000 || '\uFE0F' in text) {
            it.palsoftware.pastiera.data.emoji.RecentEmojiManager.addRecentEmoji(this, text)
        } else {
            it.palsoftware.pastiera.data.symbols.SymbolSearch.addRecent(this, text)
        }
    }

    override fun onKeyUp(keyCode_: Int, event_: KeyEvent?): Boolean {
        if (keyCode_ == KeyEvent.KEYCODE_UNKNOWN) return super.onKeyUp(keyCode_, event_)
        if (QuickLauncherActivity.ownsKey(event_)) return false
        if (keyCode_ == KEYCODE_SYM) {
            symKeyHeld = false
            // Released after a chord in a hidden app: no panel
            if (hiddenAppSymChordUsed) {
                hiddenAppSymChordUsed = false
                hiddenAppPastieraKeys.remove(keyCode_)
                symTogglePendingOnKeyUp = false
                return true
            }
        }
        if (keyCode_ == KeyEvent.KEYCODE_BACK && niagaraBackReturnTo != null) {
            val pkg = niagaraBackReturnTo!!
            niagaraBackReturnTo = null
            requestHideSelf(0)
            returnToApp(pkg)
            return true
        }
        terminalEmojiKeyAsAlt(keyCode_, event_)?.let { return onKeyUp(KeyEvent.KEYCODE_ALT_LEFT, it) }
        withoutEmojiAltShift(event_)?.let { return onKeyUp(keyCode_, it) }
        if (keyboardHiddenForApp) {
            // A release follows its press, so neither side is left with a stuck key
            val toPastiera = when {
                hiddenAppPastieraKeys.remove(keyCode_) -> true
                hiddenAppPassedThroughKeys.remove(keyCode_) -> false
                else -> hiddenAppKeyGoesToPastiera(keyCode_)
            }
            if (!toPastiera && translateHiddenAppKey(event_)) return true
            HiddenAppKeyObserver.logKey(event_, if (toPastiera) "input method, to the keyboard" else "input method, to the app")
            if (!toPastiera) {
                observeHiddenAppKey(event_)
                return super.onKeyUp(keyCode_, event_)
            }
        }
        val handled = handleKeyUp(keyCode_, event_)
        if (keyboardHiddenForApp || keyboardOutOfSight) syncHiddenAppPanel()
        return handled
    }

    private fun handleKeyUp(keyCode_: Int, event_: KeyEvent?): Boolean {
        // The release of a key sent to the app as its own shortcut, or to a terminal with Ctrl
        if (appShortcutKeysDown.remove(keyCode_)) return true
        if (extraKeysKeysDown.remove(keyCode_)) return true
        if (terminalRawKeysDown.remove(keyCode_)) return super.onKeyUp(keyCode_, event_)
        // Nav Mode in a terminal sends each key whole on its press
        if (terminalNavModeKey(keyCode_, event_, down = false)) return true
        terminalEmojiKeysDown.remove(keyCode_)?.let { action ->
            if (action != TerminalMode.EmojiKeyAction.ExtraKeys) sendTerminalActionKey(action, KeyEvent.ACTION_UP, 0)
            return true
        }
        if (terminalCtrlKeysDown.remove(keyCode_)) {
            event_?.let { sendTerminalCtrlKey(it, KeyEvent.ACTION_UP, keyCode_) }
            return true
        }
        if (!replayingProtectedNumberKey) {
            when (val result = accidentalKeyPressFilter.onKeyUp(keyCode_, event_)) {
                is AccidentalKeyPressFilter.KeyUpResult.Suppressed -> {
                    notifyDebugKeyEvent(
                        keyCode = keyCode_,
                        event = event_,
                        action = "KEY_UP_SUPPRESSED",
                        origin = "accidental_keys",
                        outputKeyCodeName = result.event.debugOutput()
                    )
                    return true
                }
                is AccidentalKeyPressFilter.KeyUpResult.ReplayTap -> {
                    replayProtectedNumberKey(keyCode_, result)
                    return true
                }
                null -> Unit
            }
        }
        val remapped = remapHardwareEvent(keyCode_, event_)
        if (remapped.consume) return true
        val keyCode = remapped.keyCode
        val event = remapped.event
        // The emoji layer's pages: a pressed emoji with skin tones is typed when it comes up
        if (symLayoutController.handlePagedKeyUp(keyCode, currentInputConnection) { updateStatusBarText() }) {
            updateStatusBarText()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER && consumeAltEnterUntilKeyUp) {
            consumeAltEnterUntilKeyUp = false
            return true
        }
        if (suggestionKeyUpPending != KeyEvent.KEYCODE_UNKNOWN && keyCode == suggestionKeyUpPending) {
            suggestionKeyUpPending = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (emojiPickerKeyUpPending != KeyEvent.KEYCODE_UNKNOWN && keyCode == emojiPickerKeyUpPending) {
            emojiPickerKeyUpPending = KeyEvent.KEYCODE_UNKNOWN
            // Held past a tap without choosing anything: the emoji key just lets go
            val heldWithoutChoice = event != null && isLongHold(event)
            if (KeyEvent.isModifierKey(keyCode) && !emojiPickerKeyChorded && !heldWithoutChoice) {
                if (!emojiSticky && symPage == 0 && SettingsManager.getEmojiStickyTap(this)) {
                    // A tap: the next key types its emoji; a second tap opens the emoji screen
                    emojiSticky = true
                    symSticky = false
                } else {
                    emojiSticky = false
                    toggleEmojiKeyScreen()
                }
            }
            emojiPickerKeyChorded = false
            updateStatusBarText()
            return true
        }
        clicksPowerShiftTapFilter.shouldConsumeKeyUp(keyCode, event)?.let { suppressed ->
            notifyDebugKeyEvent(
                keyCode = keyCode,
                event = event,
                action = "KEY_UP_SUPPRESSED",
                origin = "clicks_shift_bounce",
                outputKeyCodeName = suppressed.debugOutput()
            )
            return true
        }
        bounceKeyFilter.shouldConsumeKeyUp(keyCode, event)?.let { suppressed ->
            notifyDebugKeyEvent(
                keyCode = keyCode,
                event = event,
                action = "KEY_UP_SUPPRESSED",
                origin = "bounce_keys",
                outputKeyCodeName = suppressed.debugOutput()
            )
            return true
        }

        // Check if we have an editable field at the start (same logic as onKeyDown)
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL

        if (
            hasEditableField &&
            symPage == 4 &&
            keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KEYCODE_SYM &&
            !isPureModifierKey(keyCode) &&
            ::candidatesBarController.isInitialized &&
            candidatesBarController.isEmojiPickerSearchInputActive() &&
            candidatesBarController.shouldConsumeEmojiPickerSearchKeyUp(
                event,
                event?.isCtrlPressed == true ||
                    ctrlPressed ||
                    ctrlPhysicallyPressed ||
                    ctrlLatchActive ||
                    ctrlOneShot ||
                    ctrlLatchFromNavMode
            )
        ) {
            return true
        }
        
        // If NO editable field is active, handle ONLY nav mode Ctrl release
        if (!hasEditableField) {
            if (keyCode == KEYCODE_SYM) {
                symTogglePendingOnKeyUp = false
                symChordUsedSinceKeyDown = false
                symPhysicallyPressed = false
            }
            return inputEventRouter.handleKeyUpWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isShortcutKey = { code -> isShortcutKey(code) },
                    isLauncherPackage = { pkg -> launcherShortcutController.isLauncher(pkg) },
                    handleLauncherShortcut = { key -> launcherShortcutController.handleLauncherShortcut(key) },
                    handlePowerShortcut = { key -> launcherShortcutController.handlePowerShortcut(key) },
                    togglePowerShortcutMode = { message, isNavModeActive -> 
                        launcherShortcutController.togglePowerShortcutMode(
                            showToast = { showPowerShortcutToast(it) },
                            isNavModeActive = isNavModeActive
                        )
                    },
                    callSuper = { super.onKeyUp(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                )
            )
        }
        
        // Continue with normal IME logic for text fields
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Always notify the tracker (even when the event is consumed)
        notifyDebugKeyEvent(keyCode, event, "KEY_UP", origin = "ime_service")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val holdDuration = if (downTime > 0) event?.eventTime?.minus(downTime) ?: 0L else 0L
                val isLongHold = holdDuration > MODIFIER_HOLD_MS
                val stickyEnabled = SettingsManager.isStaticVariationBarLayerStickyEnabled(this)
                val isIntentionalHold = variationInteractedDuringHold || (isLongHold && !otherKeyInteractedDuringHold)

                if (isIntentionalHold) {
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    // Sticky layer activation is handled via double-tap, not hold.
                    shiftLayerLatched = false
                    lastShiftTapUpTime = 0L
                    variationInteractedDuringHold = false
                    otherKeyInteractedDuringHold = false
                    modifierStateBeforeHold = null
                    modifierStateController.shiftPressed = false
                    modifierStateController.shiftPhysicallyPressed = false
                    updateStatusBarText()
                } else {
                    val result = modifierStateController.handleShiftKeyUp(keyCode)
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                    val isQuickTap = holdDuration < MODIFIER_HOLD_MS && !variationInteractedDuringHold && !otherKeyInteractedDuringHold
                    if (stickyEnabled && isQuickTap) {
                        val now = event?.eventTime ?: System.currentTimeMillis()
                        if (modifierStateController.shiftDoubleTapLocks && lastShiftTapUpTime > 0L && now - lastShiftTapUpTime <= DOUBLE_TAP_THRESHOLD) {
                            shiftLayerLatched = true
                            lastShiftTapUpTime = 0L
                            updateStatusBarText()
                        } else {
                            lastShiftTapUpTime = now
                        }
                    } else {
                        lastShiftTapUpTime = 0L
                    }
                }
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes.remove(keyCode)
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            // Held for a shortcut (Ctrl+F, Ctrl+C…), even one that opened another field: Ctrl lets go
            val usedForShortcut = otherKeyInteractedDuringHold || ctrlHeldIntoNewField
            ctrlHeldIntoNewField = false
            if (ctrlPressed) {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val holdDuration = if (downTime > 0) event?.eventTime?.minus(downTime) ?: 0L else 0L
                val isLongHold = holdDuration > MODIFIER_HOLD_MS
                val shortcutUsedDuringHold = otherKeyInteractedDuringHold
                val isIntentionalHold = variationInteractedDuringHold || (isLongHold && !otherKeyInteractedDuringHold)

                if (isIntentionalHold) {
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    variationInteractedDuringHold = false
                    otherKeyInteractedDuringHold = false
                    modifierStateBeforeHold = null
                    modifierStateController.ctrlPressed = false
                    modifierStateController.ctrlPhysicallyPressed = false
                    updateStatusBarText()
                } else {
                    val result = modifierStateController.handleCtrlKeyUp(keyCode)
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                }
                // Ctrl key-down enables one-shot; if Ctrl was used as a physically held shortcut,
                // clear that one-shot on release so Ctrl doesn't remain active.
                if (shortcutUsedDuringHold && ctrlOneShot && !ctrlLatchActive) {
                    ctrlOneShot = false
                    updateStatusBarText()
                }
                modifierDownTimes.remove(keyCode)
            }
            if (usedForShortcut && !ctrlLatchFromNavMode && (ctrlOneShot || ctrlLatchActive || ctrlPressed)) {
                modifierStateController.clearCtrlState(resetPressedState = true)
                otherKeyInteractedDuringHold = false
                modifierStateBeforeHold = null
                updateStatusBarText()
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val holdDuration = if (downTime > 0) event?.eventTime?.minus(downTime) ?: 0L else 0L
                val isLongHold = holdDuration > MODIFIER_HOLD_MS
                val stickyEnabled = SettingsManager.isStaticVariationBarLayerStickyEnabled(this)
                val isIntentionalHold = variationInteractedDuringHold || (isLongHold && !otherKeyInteractedDuringHold)

                if (isIntentionalHold) {
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    // Sticky layer activation is handled via double-tap, not hold.
                    altModifierLayerLatched = false
                    lastAltTapUpTime = 0L
                    variationInteractedDuringHold = false
                    otherKeyInteractedDuringHold = false
                    modifierStateBeforeHold = null
                    modifierStateController.altPressed = false
                    modifierStateController.altPhysicallyPressed = false
                    updateStatusBarText()
                } else {
                    val result = modifierStateController.handleAltKeyUp(keyCode)
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                    val isQuickTap = holdDuration < MODIFIER_HOLD_MS && !variationInteractedDuringHold && !otherKeyInteractedDuringHold
                    if (stickyEnabled && isQuickTap) {
                        val now = event?.eventTime ?: System.currentTimeMillis()
                        if (modifierStateController.altDoubleTapLocks && lastAltTapUpTime > 0L && now - lastAltTapUpTime <= DOUBLE_TAP_THRESHOLD) {
                            altModifierLayerLatched = true
                            lastAltTapUpTime = 0L
                            updateStatusBarText()
                        } else {
                            lastAltTapUpTime = now
                        }
                    } else {
                        lastAltTapUpTime = 0L
                    }
                }
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes.remove(keyCode)
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Toggle SYM layout on key release only when SYM was tapped alone.
        if (keyCode == KEYCODE_SYM) {
            symPhysicallyPressed = false
            // Held past a tap without choosing a symbol: SYM just lets go
            val tapped = symTogglePendingOnKeyUp && !symChordUsedSinceKeyDown && !(event != null && isLongHold(event))
            symTogglePendingOnKeyUp = false
            symChordUsedSinceKeyDown = false
            if (tapped) {
                if (!symSticky && symPage == 0 && SettingsManager.getSymStickyTap(this)) {
                    // A tap: the next key types its symbol; a second tap opens the symbols
                    symSticky = true
                    emojiSticky = false
                } else {
                    symSticky = false
                    symLayoutController.toggleSymPage()
                }
            }
            updateStatusBarText()
            return true
        }
        
        if (symLayoutController.handleKeyUp(keyCode, shiftPressed)) {
            return true
        }

        val handled = super.onKeyUp(keyCode, event)
        if (!isPureModifierKey(keyCode) && ::textExpansionController.isInitialized) {
            textExpansionController.scheduleRefresh()
        }
        return handled
    }

    /**
     * Adds the word before the cursor (or the unknown word the suggestions offered) to the
     * dictionary, and says what happened.
     */
    private fun addLastWordToDictionary() {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val word = lastWordIn(before) ?: suggestionController.pendingAddWord()
        val message = when {
            word.isNullOrBlank() -> getString(R.string.add_last_word_none)
            suggestionController.isKnownWordInActiveDictionaries(word) -> getString(R.string.add_last_word_known, word)
            else -> {
                suggestionController.addUserWord(word)
                suggestionController.clearPendingAddWord()
                getString(R.string.add_last_word_added, word)
            }
        }
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Adds an Alt+key -> character mapping.
     */
    fun addAltKeyMapping(keyCode: Int, character: String) {
        alternateCharacterManager.addAltKeyMapping(keyCode, character)
    }

    /**
     * Removes an Alt+key mapping.
     */
    fun removeAltKeyMapping(keyCode: Int) {
        alternateCharacterManager.removeAltKeyMapping(keyCode)
    }
    
    private fun handleNativeImeTrackpadMotion(event: MotionEvent, origin: String): Boolean {
        if (!isNativeImeTrackpadProviderActive()) {
            return false
        }
        if (!DeviceSpecific.isTitan2Device()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return false
        }

        val deviceName = InputDevice.getDevice(event.deviceId)?.name.orEmpty()
        val isTrackpadEvent = event.isFromSource(InputDevice.SOURCE_TOUCHPAD) ||
            deviceName.equals("touchPad", ignoreCase = true)
        if (!isTrackpadEvent) {
            return false
        }

        Log.d(
            TRACKPAD_DEBUG_TAG,
            "NativeMotion[$origin]: action=${motionActionName(event.actionMasked)} source=${event.source}(0x${event.source.toString(16)}) deviceId=${event.deviceId} device='$deviceName' x=${event.x} y=${event.y}"
        )
        // In a terminal, swipes move its cursor instead
        if (terminalSwipesMoveCursor()) {
            terminalCursorSwipe(event.actionMasked, event.x, event.y, nativeImeTrackpadAxisRange(event, MotionEvent.AXIS_X))
            return true
        }
        return processTrackpadMotion(event, origin, xRangeOverride = null)
    }

    /**
     * A keyboard swipe as it happens, from Android or read through Shizuku: picks a suggestion
     * or deletes a word once it ends. [xRangeOverride] is the touch surface's width when the
     * event doesn't come from its own input device.
     */
    private fun processTrackpadMotion(event: MotionEvent, origin: String, xRangeOverride: TrackpadAxisRange?): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val xRange = xRangeOverride ?: nativeImeTrackpadAxisRange(event, MotionEvent.AXIS_X)
                nativeTrackpadGestureStart = NativeTrackpadGestureStart(
                    x = event.x,
                    y = event.y,
                    xRange = xRange,
                    origin = origin,
                    actionName = motionActionName(event.actionMasked),
                    deviceId = event.deviceId,
                    source = event.source,
                    eventTimeUptimeMs = event.eventTime
                )
                DebugCaptureStore.recordRawTrackpadEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = origin,
                    phase = "down",
                    action = motionActionName(event.actionMasked),
                    outcome = "start",
                    startX = event.x,
                    startY = event.y,
                    x = event.x,
                    y = event.y,
                    deltaX = 0f,
                    deltaY = 0f,
                    threshold = nativeImeTrackpadSuggestionSwipeThreshold(),
                    deviceId = event.deviceId,
                    source = event.source,
                    eventTimeUptimeMs = event.eventTime
                )
                nativeTrackpadLastX = event.x
                nativeTrackpadLastY = event.y
                nativeTrackpadLastEventTimeUptimeMs = event.eventTime
                nativeTrackpadGestureHandled = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                nativeTrackpadGestureStart ?: NativeTrackpadGestureStart(
                    x = event.x,
                    y = event.y,
                    xRange = xRangeOverride ?: nativeImeTrackpadAxisRange(event, MotionEvent.AXIS_X),
                    origin = origin,
                    actionName = motionActionName(event.actionMasked),
                    deviceId = event.deviceId,
                    source = event.source,
                    eventTimeUptimeMs = event.eventTime
                ).also { nativeTrackpadGestureStart = it }
                for (index in 0 until event.historySize) {
                    val historicalX = event.getHistoricalX(0, index)
                    val historicalY = event.getHistoricalY(0, index)
                    nativeTrackpadLastX = historicalX
                    nativeTrackpadLastY = historicalY
                    nativeTrackpadLastEventTimeUptimeMs = event.getHistoricalEventTime(index)
                }
                nativeTrackpadLastX = event.x
                nativeTrackpadLastY = event.y
                nativeTrackpadLastEventTimeUptimeMs = event.eventTime
                return true
            }
            MotionEvent.ACTION_UP -> {
                val start = nativeTrackpadGestureStart
                if (start != null && !nativeTrackpadGestureHandled) {
                    handleNativeImeTrackpadSwipeCandidate(
                        start = start,
                        x = nativeTrackpadLastX,
                        y = nativeTrackpadLastY,
                        phase = "up",
                        eventTimeUptimeMs = nativeTrackpadLastEventTimeUptimeMs.takeIf { it > 0L } ?: event.eventTime
                    )
                }
                nativeTrackpadGestureStart = null
                nativeTrackpadGestureHandled = false
                nativeTrackpadLastEventTimeUptimeMs = 0L
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                nativeTrackpadGestureStart = null
                nativeTrackpadGestureHandled = false
                nativeTrackpadLastEventTimeUptimeMs = 0L
                return true
            }
            else -> return false
        }
    }

    private fun handleNativeImeTrackpadSwipeCandidate(
        start: NativeTrackpadGestureStart,
        x: Float,
        y: Float,
        phase: String,
        eventTimeUptimeMs: Long
    ): Boolean {
        val deltaX = x - start.x
        val deltaY = y - start.y
        val upwardDistance = -deltaY
        val leftwardDistance = -deltaX
        val suggestionThreshold = nativeImeTrackpadSuggestionSwipeThreshold()
        val deleteThreshold = nativeImeTrackpadDeleteSwipeThreshold()
        val durationMs = (eventTimeUptimeMs - start.eventTimeUptimeMs).coerceAtLeast(1L)
        val upVelocity = upwardDistance / durationMs
        val leftVelocity = leftwardDistance / durationMs
        val verticalEnough = upwardDistance >= suggestionThreshold
        val mostlyVertical = kotlin.math.abs(deltaX) < upwardDistance / 4f
        val verticalFastEnough = upVelocity >= NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS
        val leftEnough = leftwardDistance >= deleteThreshold
        val mostlyHorizontal = kotlin.math.abs(deltaY) < leftwardDistance / 4f
        val horizontalFastEnough = leftVelocity >= NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS
        Log.d(
            TRACKPAD_DEBUG_TAG,
            "Native candidate[$phase]: startX=${start.x}, startY=${start.y}, x=$x, y=$y, dx=$deltaX, dy=$deltaY, up=$upwardDistance, left=$leftwardDistance, suggestionThreshold=$suggestionThreshold, deleteThreshold=$deleteThreshold, duration=${durationMs}ms, upVelocity=$upVelocity, leftVelocity=$leftVelocity, verticalEnough=$verticalEnough, mostlyVertical=$mostlyVertical, verticalFastEnough=$verticalFastEnough, leftEnough=$leftEnough, mostlyHorizontal=$mostlyHorizontal, horizontalFastEnough=$horizontalFastEnough"
        )
        val candidateThreshold = if (leftwardDistance > upwardDistance) deleteThreshold else suggestionThreshold
        // Flux Keyboard: left, up and right can pick the left, middle and right suggestion,
        // and a swipe down can delete the previous word
        val directional = SettingsManager.getTrackpadSuggestionSwipeDirections(this)
        val rightwardDistance = deltaX
        val downwardDistance = deltaY
        // Direction picks: the dominant direction counts (a 45° wedge each way) and a shorter
        // swipe does, so a swipe works from anywhere on the keys, edges included
        // One key's width (the Titan 2 Elite's keys are about square): sliding from one key onto
        // the next picks, from anywhere on the keys, however slowly within half a second
        val pickDistance = TrackpadCoordinateMapper.pickDistance(
            suggestionThreshold * 0.6f, start.xRange)
        val sideDistance = TrackpadCoordinateMapper.pickDistance(
            SettingsManager.getTrackpadSideSwipeThreshold(this) * it.palsoftware.pastiera.core.SuggestionSwipeLearning.scale(this), start.xRange)
        // After a pause in typing a swipe is a scroll: it picks nothing
        val picksAllowed = it.palsoftware.pastiera.core.SuggestionSwipeLearning.swipesPick(this)
        val horizontalDominant = kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
        val pickFastEnough = TrackpadCoordinateMapper.pickQuickEnough(
            maxOf(kotlin.math.abs(deltaX), kotlin.math.abs(deltaY)),
            if (horizontalDominant) sideDistance else pickDistance,
            durationMs, NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS)
        val rightEnough = horizontalDominant && rightwardDistance >= sideDistance && pickFastEnough
        val leftPicks = horizontalDominant && leftwardDistance >= sideDistance && pickFastEnough
        val upPicks = !horizontalDominant && upwardDistance >= pickDistance && pickFastEnough
        val downEnough = !horizontalDominant && downwardDistance >= deleteThreshold * 0.6f &&
            downwardDistance / durationMs >= NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS
        val direction = when {
            picksAllowed && verticalEnough && mostlyVertical && verticalFastEnough -> NativeTrackpadSwipeDirection.UP
            picksAllowed && directional && upPicks -> NativeTrackpadSwipeDirection.UP
            picksAllowed && directional && rightEnough -> NativeTrackpadSwipeDirection.RIGHT
            picksAllowed && directional && leftPicks -> NativeTrackpadSwipeDirection.LEFT
            !directional &&
                leftEnough &&
                mostlyHorizontal &&
                horizontalFastEnough &&
                SettingsManager.getSwipeToDelete(this) &&
                SettingsManager.getSwipeToDeleteProvider(this) == SettingsManager.SWIPE_TO_DELETE_PROVIDER_NATIVE_IME -> NativeTrackpadSwipeDirection.LEFT
            downEnough && SettingsManager.getTrackpadSwipeDownDeletesWord(this) -> NativeTrackpadSwipeDirection.DOWN
            else -> {
                // A swipe that nearly picked: if a pick follows soon, swipes need a little less
                if (picksAllowed) it.palsoftware.pastiera.core.SuggestionSwipeLearning.onMissed(
                    reached = maxOf(upwardDistance, kotlin.math.abs(deltaX)),
                    needed = if (horizontalDominant) sideDistance else pickDistance
                )
                DebugCaptureStore.recordRawTrackpadEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = start.origin,
                    phase = phase,
                    action = start.actionName,
                    outcome = "candidate",
                    startX = start.x,
                    startY = start.y,
                    x = x,
                    y = y,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    threshold = candidateThreshold,
                    deviceId = start.deviceId,
                    source = start.source,
                    eventTimeUptimeMs = eventTimeUptimeMs
                )
                return false
            }
        }

        val now = System.currentTimeMillis()
        if (now - nativeTrackpadGestureAtMs < 250L) {
            DebugCaptureStore.recordRawTrackpadEvent(
                provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                origin = start.origin,
                phase = phase,
                action = start.actionName,
                outcome = "debounced",
                startX = start.x,
                startY = start.y,
                x = x,
                y = y,
                deltaX = deltaX,
                deltaY = deltaY,
                threshold = when (direction) {
                    NativeTrackpadSwipeDirection.UP, NativeTrackpadSwipeDirection.RIGHT -> suggestionThreshold
                    NativeTrackpadSwipeDirection.LEFT -> if (directional) suggestionThreshold else deleteThreshold
                    NativeTrackpadSwipeDirection.DOWN -> deleteThreshold
                },
                deviceId = start.deviceId,
                source = start.source,
                eventTimeUptimeMs = eventTimeUptimeMs
            )
            nativeTrackpadGestureHandled = true
            return true
        }
        nativeTrackpadGestureAtMs = now
        nativeTrackpadGestureHandled = true

        if (directional && direction != NativeTrackpadSwipeDirection.DOWN) {
            val index = when (direction) {
                NativeTrackpadSwipeDirection.LEFT -> 0
                NativeTrackpadSwipeDirection.RIGHT -> 2
                else -> 1
            }
            Log.d(TRACKPAD_DEBUG_TAG, "Native swipe accepted[$phase]: direction=$direction picks suggestion $index")
            it.palsoftware.pastiera.core.SuggestionSwipeLearning.onPicked(this)
            acceptSuggestionAtIndex(index)
            return true
        }

        when (direction) {
            NativeTrackpadSwipeDirection.RIGHT -> acceptSuggestionAtIndex(2)
            NativeTrackpadSwipeDirection.DOWN -> {
                Log.d(TRACKPAD_DEBUG_TAG, "Native swipe accepted[$phase]: direction=DOWN deletes a word")
                deleteWordFromNativeTrackpadSwipe()
            }
            NativeTrackpadSwipeDirection.UP -> {
                val third = TrackpadCoordinateMapper.third(start.x, start.xRange)
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "Native swipe accepted[$phase]: direction=UP startX=${start.x}, startY=${start.y}, x=$x, y=$y, dx=$deltaX, dy=$deltaY, duration=${durationMs}ms, velocity=$upVelocity, xRange=${start.xRange.min}..${start.xRange.max}, third=$third"
                )
                KeyboardEventTracker.notifySyntheticGestureKeyEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = start.origin,
                    phase = phase,
                    action = start.actionName,
                    direction = direction.name.lowercase(),
                    outcome = "accepted_suggestion_$third",
                    startX = start.x,
                    startY = start.y,
                    x = x,
                    y = y,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    threshold = suggestionThreshold,
                    deviceId = start.deviceId,
                    source = start.source,
                    eventTimeUptimeMs = eventTimeUptimeMs
                )
                it.palsoftware.pastiera.core.SuggestionSwipeLearning.onPicked(this)
                acceptSuggestionAtIndex(third)
            }
            NativeTrackpadSwipeDirection.LEFT -> {
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "Native swipe accepted[$phase]: direction=LEFT startX=${start.x}, startY=${start.y}, x=$x, y=$y, dx=$deltaX, dy=$deltaY, duration=${durationMs}ms, velocity=$leftVelocity"
                )
                KeyboardEventTracker.notifySyntheticGestureKeyEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = start.origin,
                    phase = phase,
                    action = start.actionName,
                    direction = direction.name.lowercase(),
                    outcome = "accepted_delete",
                    startX = start.x,
                    startY = start.y,
                    x = x,
                    y = y,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    threshold = deleteThreshold,
                    deviceId = start.deviceId,
                    source = start.source,
                    eventTimeUptimeMs = eventTimeUptimeMs
                )
                deleteWordFromNativeTrackpadSwipe()
            }
        }
        return true
    }

    private fun nativeImeTrackpadSuggestionSwipeThreshold(): Float {
        // Your setting, scaled by what suggestion swipes learned from your picks and undos
        return SettingsManager.getTrackpadSuggestionSwipeThreshold(this) * it.palsoftware.pastiera.core.SuggestionSwipeLearning.scale(this)
    }

    private fun nativeImeTrackpadDeleteSwipeThreshold(): Float {
        return SettingsManager.getTrackpadDeleteSwipeThreshold(this)
    }

    private fun nativeImeTrackpadAxisRange(event: MotionEvent, axis: Int): TrackpadAxisRange {
        val inputDevice = InputDevice.getDevice(event.deviceId)
        val motionRange = inputDevice?.getMotionRange(axis, event.source)
            ?: inputDevice?.getMotionRange(axis)
        val detectedRange = motionRange?.let { TrackpadAxisRange(it.min, it.max) }
        if (detectedRange?.isValid == true) {
            return detectedRange
        }

        val fallbackMax = when (axis) {
            MotionEvent.AXIS_X -> trackpadDecorMotionView?.width?.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels
            MotionEvent.AXIS_Y -> trackpadDecorMotionView?.height?.takeIf { it > 0 }
                ?: resources.displayMetrics.heightPixels
            else -> 1
        }.toFloat()
        return TrackpadAxisRange(0f, fallbackMax.coerceAtLeast(1f))
    }

    private fun motionActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP"
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            MotionEvent.ACTION_CANCEL -> "ACTION_CANCEL"
            MotionEvent.ACTION_SCROLL -> "ACTION_SCROLL"
            else -> "ACTION_$action"
        }
    }

    private fun deleteWordFromNativeTrackpadSwipe() {
        it.palsoftware.pastiera.core.SuggestionSwipeLearning.onDeleted(this)
        val ic = currentInputConnection
        if (ic == null) {
            Log.w(TRACKPAD_DEBUG_TAG, "Native swipe-to-delete ignored: no InputConnection")
            return
        }
        if (TextSelectionHelper.deleteLastWord(ic)) {
            Log.d(TRACKPAD_DEBUG_TAG, "Native swipe-to-delete deleted previous word")
        } else {
            Log.d(TRACKPAD_DEBUG_TAG, "Native swipe-to-delete found nothing to delete")
        }
    }

    private fun acceptSuggestionAtIndex(third: Int) {
        val visibleSuggestions = visibleSuggestionStrings()

        // Clear latched UI layers when selecting a suggestion via trackpad.
        if (shiftLayerLatched || altModifierLayerLatched) {
            shiftLayerLatched = false
            altModifierLayerLatched = false
            modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
            modifierStateBeforeHold = null
        }
        variationInteractedDuringHold = true

        // Allow gesture only when suggestions bar should be visible/usable
        val addWordCandidate = suggestionController.pendingAddWord()
        val addWordGestureEnabled = SettingsManager.getTrackpadGestureAddWordEnabled(this)
        val canAddWordByGesture = TrackpadAddWordGesturePolicy.canAddWordByGesture(
            third = third,
            addWordGestureEnabled = addWordGestureEnabled,
            fullWidthWhenAddOnlyEnabled = SettingsManager.getTrackpadGestureAddWordFullWidthEnabled(this),
            addWordCandidate = addWordCandidate,
            visibleSuggestions = visibleSuggestions
        )
        val allowGesture =
            symPage == 0 &&
            (visibleSuggestions.isNotEmpty() || canAddWordByGesture) &&
            SettingsManager.getSuggestionsEnabled(this) &&
            !shouldDisableSmartFeatures
        if (!allowGesture) {
            Log.d(
                TAG,
                "Trackpad gesture ignored: bar not visible/usable (sym=$symPage, suggestions=${visibleSuggestions.size})"
            )
            return
        }

        if (canAddWordByGesture) {
            val wordToAdd = addWordCandidate ?: return
            Log.d(TAG, "Adding user word '$wordToAdd' from trackpad gesture")
            uiHandler.post {
                val ic = currentInputConnection
                candidatesBarController.flashSuggestionSlot(2)
                suggestionController.addUserWord(wordToAdd)
                suggestionController.clearPendingAddWord()
                if (ic != null) {
                    AddWordCommitHelper.commitAutoSpaceAfterAddWord(ic)
                }
                updateStatusBarText()
                NotificationHelper.triggerHapticFeedback(this)
            }
            return
        }

        // Log current suggestions
        Log.d(TAG, "Current latestSuggestions: $visibleSuggestions")

        // Map third to suggestion index based on FullSuggestionsBar slot layout
        // slots[0] = left = suggestions[2]
        // slots[1] = center = suggestions[0]
        // slots[2] = right = suggestions[1]
        val suggestionIndex = when (third) {
            0 -> 2  // Left third → suggestions[2]
            1 -> 0  // Center third → suggestions[0]
            2 -> 1  // Right third → suggestions[1]
            else -> return
        }

        val suggestion = visibleSuggestions.getOrNull(suggestionIndex)
        if (suggestion == null) {
            Log.d(TAG, "No suggestion at index $suggestionIndex (third=$third), latestSuggestions=$visibleSuggestions")
            return
        }

        uiHandler.post {
            val ic = currentInputConnection
            if (ic == null) {
                Log.w(TAG, "No InputConnection available")
                return@post
            }

            // Provide visual feedback on the suggestions bar, matching variation press color
            candidatesBarController.flashSuggestionSlot(suggestionIndex)

            if (EmojiSuggestion.isEmoji(suggestion)) {
                EmojiSuggestion.commitAfterWord(ic, suggestion)
                suggestionController.onContextReset()
                NotificationHelper.triggerHapticFeedback(this)
                return@post
            }

            val forceLeadingCapital = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                context = this,
                inputConnection = ic,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize
            ) && SettingsManager.getAutoCapitalizeFirstLetter(this)

            Log.d(TAG, "Accepting suggestion '$suggestion' from third=$third (index=$suggestionIndex)")

            // Use the same logic as SuggestionButtonHandler
            val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val after = ic.getTextAfterCursor(64, 0)?.toString().orEmpty()
            fun isBoundaryChar(ch: Char, prev: Char?, next: Char?): Boolean {
                return it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)
            }

            // Find start of word in 'before'
            var start = before.length
            while (start > 0) {
                val ch = before[start - 1]
                val prev = before.getOrNull(start - 2)
                val next = before.getOrNull(start)
                if (!isBoundaryChar(ch, prev, next)) {
                    start--
                    continue
                }
                break
            }

            // Find end of word in 'after'
            var end = 0
            while (end < after.length) {
                val ch = after[end]
                val prev = if (end == 0) before.lastOrNull() else after[end - 1]
                val next = after.getOrNull(end + 1)
                if (!isBoundaryChar(ch, prev, next)) {
                    end++
                    continue
                }
                break
            }

            val wordBeforeCursor = before.substring(start)
            // Typing in front of a word: a new word keeps the word after the cursor
            val keepAfter = it.palsoftware.pastiera.core.suggestions.WordInFront.keepsWordAfter(suggestion, wordBeforeCursor, after.substring(0, end))
            val wordAfterCursor = if (keepAfter) "" else after.substring(0, end)
            val currentWord = wordBeforeCursor + wordAfterCursor

            val deleteBefore = wordBeforeCursor.length
            val deleteAfter = wordAfterCursor.length
            val replacement = it.palsoftware.pastiera.core.suggestions.CasingHelper.applyCasing(
                suggestion, currentWord, forceLeadingCapital
            )
            val shouldAppendSpace = !replacement.endsWith("'")

            ic.deleteSurroundingText(deleteBefore, deleteAfter)
            val textToCommit = if (shouldAppendSpace) "$replacement " else replacement
            ic.commitText(textToCommit, 1)
            if (shiftOneShot) {
                modifierStateController.consumeShiftOneShot()
            }
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = currentWord,
                after = replacement,
                trigger = DebugCaptureStore.AutoCorrectionTrigger.SUGGESTION_TAP,
                source = "UNKNOWN"
            )

            if (shouldAppendSpace) {
                it.palsoftware.pastiera.core.AutoSpaceTracker.markAutoSpace()
            }

            // CRITICAL FIX: Reset tracker after accepting suggestion to prevent duplicate letters
            // The cursor debounce can cause tracker to be out of sync when user types quickly after accepting
            suggestionController.onContextReset()
            NotificationHelper.triggerHapticFeedback(this)
            Log.d(TAG, "Suggestion '$suggestion' inserted successfully")
        }
    }

    private data class NativeTrackpadGestureStart(
        val x: Float,
        val y: Float,
        val xRange: TrackpadAxisRange,
        val origin: String,
        val actionName: String,
        val deviceId: Int,
        val source: Int,
        val eventTimeUptimeMs: Long
    )

    private enum class NativeTrackpadSwipeDirection {
        UP,
        LEFT,
        RIGHT,
        DOWN
    }
}

/** Keys that always belong to Android, even while a hidden app's keyboard panel is open. */
/** The Titan 2 Elite's Ctrl (scancode 251) and Sym (253), as the standard keys apps understand. */
private val TITAN_MODIFIER_SCAN_CODES = mapOf(
    251 to KeyEvent.KEYCODE_CTRL_LEFT,
    253 to KeyEvent.KEYCODE_ALT_RIGHT
)

private val HIDDEN_APP_SYSTEM_KEYS = setOf(
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_APP_SWITCH,
    KeyEvent.KEYCODE_POWER,
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_DOWN,
    KeyEvent.KEYCODE_VOLUME_MUTE
)

/** The last word in [text], skipping the spaces and punctuation after it; null when there's none. */
internal fun lastWordIn(text: String): String? {
    fun wordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'
    var end = text.length
    while (end > 0 && !text[end - 1].isLetterOrDigit()) end--
    var start = end
    while (start > 0 && wordChar(text[start - 1])) start--
    return text.substring(start, end).trim('\'', '’', '-').takeIf { word -> word.any { it.isLetter() } }
}
