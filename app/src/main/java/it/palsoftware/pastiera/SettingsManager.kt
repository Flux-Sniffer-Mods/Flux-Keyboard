package it.palsoftware.pastiera

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.PastieraCommandSource
import it.palsoftware.pastiera.core.Punctuation
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors
import it.palsoftware.pastiera.sym.SymPagesConfig

/**
 * Manages the app settings.
 * Centralizes access to SharedPreferences for the keyboard settings.
 */
object SettingsManager {
    internal const val TAG = "SettingsManager"
    private const val PREFS_NAME = "pastiera_prefs"
    
    // Settings keys
    internal const val KEY_LONG_PRESS_THRESHOLD = "long_press_threshold"
    const val KEY_TYPING_SOUND_MODE = "typing_sound_mode"
    const val KEY_TYPING_SOUND_OUTPUT_MODE = "typing_sound_output_mode"
    const val KEY_TYPING_SOUND_CUSTOM_FILE_NAME = "typing_sound_custom_file_name"
    const val KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME = "typing_sound_custom_display_name"
    const val KEY_TYPING_SOUND_UPDATED_AT = "typing_sound_updated_at"
    internal const val KEY_TAP_HAPTIC_USE_SYSTEM = "tap_haptic_use_system"
    internal const val KEY_TAP_HAPTIC_DURATION_MS = "tap_haptic_duration_ms"
    internal const val KEY_AUTO_CAPITALIZE_FIRST_LETTER = "auto_capitalize_first_letter"
    internal const val KEY_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF =
        "auto_capitalize_respect_manual_shift_off"
    internal const val KEY_AUTO_CAPITALIZE_RESTRICTED_FIELDS =
        "auto_capitalize_restricted_fields"
    internal const val KEY_DOUBLE_SPACE_TO_PERIOD = "double_space_to_period"
    internal const val KEY_SPACED_HYPHEN_TO_EN_DASH = "spaced_hyphen_to_en_dash"
    internal const val KEY_SPACED_HYPHEN_DASH_STYLE = "spaced_hyphen_dash_style"
    internal const val KEY_MID_WORD_QUOTE_TO_APOSTROPHE = "mid_word_quote_to_apostrophe"
    internal const val KEY_FRENCH_PUNCTUATION_SPACING = "french_punctuation_spacing"
    internal const val KEY_FRENCH_PUNCTUATION_ONLY_FRENCH = "french_punctuation_only_french"
    internal const val KEY_COMMA_SPACE = "comma_space"
    internal const val KEY_AUTO_SPACE_PUNCTUATION = "auto_space_punctuation"
    internal const val KEY_SPACE_AFTER_PUNCTUATION = "space_after_punctuation"
    internal const val KEY_EMOTICON_PUNCTUATION = "emoticon_punctuation"
    internal const val KEY_SMART_QUOTES = "smart_quotes"
    internal const val KEY_SMART_QUOTES_STYLE = "smart_quotes_style"
    internal const val KEY_SWIPE_TO_DELETE = "swipe_to_delete"
    internal const val KEY_SWIPE_TO_DELETE_PROVIDER = "swipe_to_delete_provider"
    private const val KEY_AUTO_SHOW_KEYBOARD = "auto_show_keyboard"
    internal const val KEY_CLEAR_ALT_ON_SPACE = "clear_alt_on_space"
    internal const val KEY_SMART_ALT_OFF_AFTER_OPENING = "smart_alt_off_after_opening"
    internal const val KEY_DEVELOPER_OPTIONS_ENABLED = "developer_options_enabled"
    private const val KEY_INCOGNITO_ALWAYS = "incognito_always"
    private const val KEY_PASTE_SUGGESTION = "paste_suggestion_enabled"
    private const val KEY_PASTE_SUGGESTION_PASSWORD_FIELDS = "paste_suggestion_password_fields"
    private const val KEY_LANGUAGE_PER_APP = "language_per_app_enabled"
    internal const val KEY_KEYBOARD_WALLPAPER_COLOURS = "keyboard_theme_wallpaper_colours"
    internal const val KEY_KEYBOARD_BACKGROUND_AUTO_COLOURS = "keyboard_background_auto_colours"
    internal const val KEY_KEYBOARD_BACKGROUND_KEY_OPACITY = "keyboard_background_key_opacity"
    const val KEY_KEYBOARD_BACKGROUND_UPDATED = "keyboard_background_updated"
    internal const val KEY_KEYBOARD_BACKGROUND_FRAMING = "keyboard_background_framing"
    private const val KEY_ONE_TIME_CODES = "one_time_codes_enabled"
    private const val KEY_AUTO_SHIFT_FIELD_TYPES = "auto_shift_field_types"
    private const val KEY_SEARCH_BAR_WAITS_FOR_TYPING = "search_bar_waits_for_typing"
    // Each app's last language, kept apart from settings (not in backups)
    private const val APP_LANGUAGES_PREFS = "app_languages"
    private const val KEY_CLEAN_PASTED_LINKS = "clean_pasted_links" // Strip tracking from pasted links
    internal const val KEY_EMOJI_SUGGESTIONS = "emoji_suggestions_enabled"
    internal const val KEY_SUGGESTIONS_BOLD = "suggestions_bold" // Suggestion bar words in bold
    internal const val KEY_SUGGESTION_KEYS = "suggestion_keys" // Keys that pick a suggestion
    private const val KEY_SPEECH_KEEP_LISTENING = "speech_keep_listening" // Voice input carries on through pauses
    internal const val KEY_CLICKS_KEYBOARD_SEEN = "clicks_keyboard_seen" // A Clicks Power Keyboard has been connected here
    internal const val KEY_INLINE_AUTOFILL = "inline_autofill_enabled"
    internal const val KEY_LED_INDIVIDUAL_COLORS = "led_individual_colors"
    internal const val KEY_LED_LOCKED_ANIMATION = "led_locked_animation"
    internal const val LED_COLOR_KEY_PREFIX = "led_color_"
    private const val KEY_INCOGNITO_FOLLOW_APPS = "incognito_follow_apps"
    internal const val KEY_SMART_CTRL_OFF_AFTER_SHORTCUT = "smart_ctrl_off_after_shortcut"
    internal const val KEY_ALT_CTRL_SPEECH_SHORTCUT = "alt_ctrl_speech_shortcut"
    internal const val KEY_LAYOUT_AWARE_CTRL_SHORTCUTS = "layout_aware_ctrl_shortcuts"
    internal const val KEY_SYM_MAPPINGS_CUSTOM = "sym_mappings_custom"
    internal const val KEY_SYM_MAPPINGS_PAGE2_CUSTOM = "sym_mappings_page2_custom"
    internal const val KEY_AUTO_CORRECT_ENABLED = "auto_correct_enabled"
    internal const val KEY_AUTO_CORRECT_ENABLED_LANGUAGES = "auto_correct_enabled_languages"
    internal const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
    internal const val KEY_SNIPPETS_ENABLED = "snippets_enabled"
    internal const val KEY_SNIPPETS_PREFIX = "snippets_prefix"
    internal const val KEY_SNIPPETS = "snippets_v1"
    internal const val KEY_SNIPPETS_PRESENTATION = "snippets_presentation"
    internal const val KEY_SNIPPETS_EXACT_ON_SPACE = "snippets_exact_on_space"
    internal const val KEY_SNIPPETS_ACCEPT_PREFIX_WITH_SPACE = "snippets_accept_prefix_with_space"
    internal const val KEY_SNIPPETS_ACCEPT_WITH_TAB = "snippets_accept_with_tab"
    internal const val KEY_SNIPPETS_ACCEPT_WITH_ENTER = "snippets_accept_with_enter"
    internal const val KEY_EMOJI_SHORTCODES_ENABLED = "emoji_shortcodes_enabled"
    internal const val KEY_SYMBOL_SHORTCODES_ENABLED = "symbol_shortcodes_enabled"
    internal const val KEY_EMOJI_SYMBOLS_PRESENTATION = "emoji_symbols_presentation"
    internal const val KEY_EMOJI_SYMBOLS_EXACT_ON_SPACE = "emoji_symbols_exact_on_space"
    internal const val KEY_EMOJI_SYMBOLS_ACCEPT_PREFIX_WITH_SPACE = "emoji_symbols_accept_prefix_with_space"
    internal const val KEY_EMOJI_SYMBOLS_ACCEPT_WITH_TAB = "emoji_symbols_accept_with_tab"
    internal const val KEY_EMOJI_SYMBOLS_ACCEPT_WITH_ENTER = "emoji_symbols_accept_with_enter"
    internal const val KEY_EMOJI_SYMBOLS_EXACT_ON_CLOSE = "emoji_symbols_exact_on_close"
    internal const val KEY_ACCENT_MATCHING_ENABLED = "accent_matching_enabled"
    internal const val KEY_AUTO_REPLACE_ON_SPACE_ENTER = "auto_replace_on_space_enter"
    internal const val KEY_MAX_AUTO_REPLACE_DISTANCE = "max_auto_replace_distance"
    internal const val KEY_AUTO_CAPITALIZE_AFTER_PERIOD = "auto_capitalize_after_period"
    internal const val KEY_LONG_PRESS_MODIFIER = "long_press_modifier" // "alt", "shift", "variations", or "sym"
    internal const val KEY_KEYBOARD_LAYOUT = "keyboard_layout" // "qwerty", "azerty", etc.
    internal const val KEY_KEYBOARD_LAYOUT_AUTO_BY_LOCALE = "keyboard_layout_auto_by_locale" // If true, resolve layout from subtype/locale mapping
    const val KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED = "keyboard_layout_auto_mapping_updated"
    internal const val KEY_KEYBOARD_LAYOUT_LIST = "keyboard_layout_list" // JSON array of layout ids for cycling
    internal const val KEY_ALT_SHIFT_LAYOUT_SWITCH = "alt_shift_layout_switch" // Enable Alt+Shift shortcut for layout cycling
    internal const val KEY_ALT_SHIFT_DEFAULT_INITIALIZED = "alt_shift_default_initialized"
    internal const val KEY_TITAN2_ELITE_ROUNDED_CORNERS_ENFORCED_V1 =
        "titan2_elite_rounded_corners_enforced_v1"
    internal const val KEY_ALT_ENTER_LAYOUT_SWITCH = "alt_enter_layout_switch" // Enable Alt+Enter shortcut for layout cycling
    internal const val KEY_CTRL_SPACE_LAYOUT_SWITCH = "ctrl_space_layout_switch" // Enable Ctrl+Space shortcut for layout cycling
    internal const val KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE = "physical_keyboard_profile_override" // auto | key2 | Q25 | titan | titan2 | titan2elite_qwerty | mp01 | clicks_razr | clicks_pixel | clicks_power
    internal const val KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL = "physical_keyboard_currency_symbol" // Currency symbol for dedicated hardware keys
    internal const val KEY_CLICKS_CLOSE_INPUT_ON_DISCONNECT = "clicks_close_input_on_disconnect"
    internal const val KEY_CLICKS_SHOW_KEYBOARD_ONLY_WITH_TEXT_FOCUS = "clicks_show_keyboard_only_with_text_focus"
    internal const val KEY_CLICKS_BLUETOOTH_PERMISSION_EXPLAINED = "clicks_bluetooth_permission_explained"
    internal const val KEY_CLICKS_CHARGING_AUTOMATION = "clicks_charging_automation"
    internal const val KEY_CLICKS_CHARGING_START_PERCENT = "clicks_charging_start_percent"
    internal const val KEY_CLICKS_CHARGING_STOP_PERCENT = "clicks_charging_stop_percent"
    internal const val KEY_CLICKS_MANUAL_CHARGING_UNTIL = "clicks_manual_charging_until"
    internal const val KEY_CLICKS_OVERLAPPING_KEYS_ENABLED = "clicks_overlapping_keys_enabled"
    internal const val KEY_CLICKS_OVERLAPPING_KEYS_MODE = "clicks_overlapping_keys_mode"
    internal const val KEY_CLICKS_NUMBER_ROW_INPUT_MODE = "clicks_number_row_input_mode"
    internal const val KEY_CLICKS_NUMBER_ROW_REPEAT_ENABLED = "clicks_number_row_repeat_enabled"
    internal const val KEY_CLICKS_POWER_KEYBOARD_SNAPSHOTS = "clicks_power_keyboard_snapshots_v1"
    internal const val KEY_CLICKS_BUTTON_MODE = "clicks_button_mode"
    internal const val KEY_CLICKS_META_BUTTON_MODE = "clicks_meta_button_mode"
    internal const val KEY_CLICKS_ALT_BUTTON_MODE = "clicks_alt_button_mode"
    internal const val KEY_CLICKS_MICROPHONE_BUTTON_MODE = "clicks_microphone_button_mode"
    internal const val KEY_CLICKS_RED_BUTTON_BINDING_CHOICE = "clicks_red_button_binding_choice"
    internal const val KEY_CLICKS_RED_BUTTON_BINDING_OUTPUT = "clicks_red_button_binding_output"
    internal const val KEY_CLICKS_KEYBOARD_BUTTON_BINDING_CHOICE = "clicks_keyboard_button_binding_choice"
    internal const val KEY_CLICKS_KEYBOARD_BUTTON_BINDING_OUTPUT = "clicks_keyboard_button_binding_output"
    internal const val KEY_CLICKS_MICROPHONE_BUTTON_BINDING_CHOICE = "clicks_microphone_button_binding_choice"
    internal const val KEY_CLICKS_MICROPHONE_BUTTON_BINDING_OUTPUT = "clicks_microphone_button_binding_output"
    internal const val KEY_RESTORE_SYM_PAGE = "restore_sym_page" // SYM page to restore when returning from settings
    internal const val KEY_PENDING_RESTORE_SYM_PAGE = "pending_restore_sym_page" // Temporary SYM page state saved when opening settings
    internal const val KEY_SYM_PAGES_CONFIG = "sym_pages_config" // Order/enabled pages for SYM
    const val KEY_ALT_MODIFIER_BINDING = "alt_modifier_binding"
    internal const val KEY_SYM_AUTO_CLOSE = "sym_auto_close" // Auto-close SYM layout after key press
    internal const val KEY_SYM_AUTO_CLOSE_ON_TOUCH = "sym_auto_close_on_touch" // Auto-close SYM layout after tapping on-screen SYM keys
    internal const val KEY_SHIFT_TAP_LATCHES = "shift_tap_latches"
    internal const val KEY_ALT_TAP_LATCHES = "alt_tap_latches"
    internal const val KEY_CTRL_TAP_LATCHES = "ctrl_tap_latches"
    internal const val KEY_SHIFT_DOUBLE_TAP_LOCKS = "shift_double_tap_locks"
    internal const val KEY_ALT_DOUBLE_TAP_LOCKS = "alt_double_tap_locks"
    internal const val KEY_CTRL_DOUBLE_TAP_LOCKS = "ctrl_double_tap_locks"
    internal const val KEY_ALT_LATCH_STAYS_ON_SPACE = "alt_latch_stays_on_space"
    internal const val KEY_CTRL_LATCH_STAYS_ON_SPACE = "ctrl_latch_stays_on_space"
    internal const val KEY_EMOJI_PICKER_EXPANDED_HEIGHT = "emoji_picker_expanded_height"
    internal const val KEY_HIDDEN_KEYBOARD_APPS = "hidden_keyboard_apps" // Packages where the keyboard stays hidden
    internal const val KEY_TERMINAL_MODE_ENABLED = "terminal_mode_enabled"
    internal const val KEY_TERMINAL_MODE_APPS = "terminal_mode_apps"
    internal const val KEY_TERMINAL_MODE_SHOW_LEDS = "terminal_mode_show_leds"
    internal const val KEY_TERMINAL_MODE_TERMUX_ADDED = "terminal_mode_termux_added"
    internal const val KEY_EXACT_TYPING_APPS = "exact_typing_apps" // Apps where every character stays as typed
    internal const val KEY_EXACT_TYPING_NO_SUGGESTIONS = "exact_typing_no_suggestions" // Honour the app's no-suggestions flag
    internal const val KEY_TERMINAL_MODE_HIDE_KEYBOARD = "terminal_mode_hide_keyboard"
    internal const val KEY_TERMINAL_MODE_EMOJI_KEY = "terminal_mode_emoji_key"
    internal const val KEY_MINIMAL_MODE = "minimal_mode" // No keyboard bar in any app; the keys work as usual
    internal const val KEY_MINIMAL_MODE_SHOW_LEDS = "minimal_mode_show_leds"
    const val TERMUX_PACKAGE = "com.termux"
    // Per hidden app (package names, one per line)
    internal const val KEY_HIDDEN_APPS_LEDS = "hidden_keyboard_apps_leds"
    internal const val KEY_HIDDEN_APPS_PANELS = "hidden_keyboard_apps_panels"
    internal const val KEY_EMOJI_PICKER_KEY = "emoji_picker_key" // Physical key that toggles the emoji picker (KEYCODE_UNKNOWN = off)
    internal const val KEY_EMOJI_KEY_OPENS_LAYER = "emoji_key_opens_layer" // Emoji key opens the emoji layer instead of the picker
    internal const val KEY_EMOJI_KEY_AUTO_CLOSE = "emoji_key_auto_close" // Emoji key screens close after an emoji
    internal const val KEY_EMOJI_LAYER_RECENTS_KEY = "emoji_layer_recents_key" // Emoji layer key that shows recents
    internal const val KEY_EMOJI_LAYER_GIF_KEY = "emoji_layer_gif_key" // Emoji layer key that opens GIF search
    internal const val KEY_HIDDEN_APP_STANDARD_MODIFIERS = "hidden_app_standard_modifiers" // Titan Ctrl/Sym as standard keys
    internal const val KEY_EMOJI_SEARCH_ENTER_PICKS = "emoji_search_enter_picks" // Enter: first emoji, close
    internal const val KEY_SYMBOL_SEARCH_ENTER_PICKS = "symbol_search_enter_picks" // Enter: first symbol, close
    internal const val KEY_GIF_SEARCH_ENTER_PICKS = "gif_search_enter_picks" // Enter: first GIF (closes)
    internal const val KEY_RECENTS_FIRST_IN_SEARCH = "recents_first_in_search" // recently used first in searches
    internal const val KEY_OFFLINE_MODE = "offline_mode" // nothing goes online (see OfflineMode)
    internal const val KEY_SEARCH_KEY = "search_key" // opens search on the emoji layer, symbols pages, picker
    internal const val KEY_GIF_SHOW_FAVOURITES = "gif_show_favourites" // Favourites section in GIF search
    internal const val KEY_GIF_SHOW_RECENTS = "gif_show_recents" // Recent section in GIF search
    internal const val KEY_EMOJI_PICKER_FOCUS_SEARCH = "emoji_picker_focus_search" // typing searches on open
    internal const val KEY_GIF_FOCUS_SEARCH = "gif_focus_search" // typing searches GIFs on open
    internal const val KEY_EMOJI_LAYER_TYPE_TO_SEARCH = "emoji_layer_type_to_search" // a letter starts emoji search
    internal const val KEY_SYMBOLS_TYPE_TO_SEARCH = "symbols_type_to_search" // a letter starts symbol search
    internal const val KEY_GIFS_ENABLED = "gifs_enabled" // GIF key on the emoji layer, GIF tab in the picker
    internal const val KEY_KLIPY_API_KEY = "klipy_api_key" // User's own KLIPY key (not backed up)
    private const val KEY_DISMISSED_RELEASES = "dismissed_releases" // Set of release tag_names that were dismissed
    private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed" // Whether the first-run tutorial has been completed
    internal const val KEY_LAST_SEEN_WHATS_NEW_VERSION = "last_seen_whats_new_version"
    internal const val KEY_SWIPE_INCREMENTAL_THRESHOLD = "swipe_incremental_threshold" // Distance in DIP for cursor movement
    internal const val KEY_STATIC_VARIATION_BAR_MODE = "static_variation_bar_mode" // Use static variation bar instead of dynamic cursor-based variations
    internal const val KEY_STATIC_VARIATION_BAR_PRESET = "static_variation_bar_preset"
    internal const val KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED = "static_variation_bar_base_layer_enabled" // Toggle top-row preset
    internal const val KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION = "static_variation_bar_modifier_hold_restoration"
    internal const val KEY_VARIATIONS_UPDATED = "variations_updated" // Trigger for reloading variations in input method service
    private const val KEY_CLIPBOARD_HISTORY_ENABLED = "clipboard_history_enabled" // Whether clipboard history is enabled
    private const val KEY_CLIPBOARD_RETENTION_TIME = "clipboard_retention_time" // How long to keep clipboard entries (in minutes)
    internal const val KEY_TRACKPAD_GESTURES_ENABLED = "trackpad_gestures_enabled" // Whether trackpad gesture suggestions are enabled
    internal const val KEY_TRACKPAD_GESTURE_ADD_WORD_ENABLED = "trackpad_gesture_add_word_enabled" // Whether suggestion gestures can trigger add-word
    internal const val KEY_TRACKPAD_GESTURE_ADD_WORD_FULL_WIDTH_ENABLED = "trackpad_gesture_add_word_full_width_enabled"
    internal const val KEY_TRACKPAD_SWIPE_THRESHOLD = "trackpad_swipe_threshold" // Threshold for swipe detection on trackpad
    internal const val KEY_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD = "trackpad_suggestion_swipe_threshold"
    internal const val KEY_TRACKPAD_DELETE_SWIPE_THRESHOLD = "trackpad_delete_swipe_threshold"
    internal const val KEY_TRACKPAD_SIDE_SWIPE_THRESHOLD = "trackpad_side_swipe_threshold"
    internal const val KEY_TRACKPAD_PROVIDER = "trackpad_provider" // shizuku | native_ime
    internal const val KEY_TRACKPAD_PROVIDER_CHOSEN = "trackpad_provider_chosen" // Picked by hand: never changed for you
    internal const val KEY_TRACKPAD_SHIZUKU_DEVICE = "trackpad_shizuku_device"
    internal const val KEY_SHIFT_BACKSPACE_DELETE = "shift_backspace_delete" // Shift + Backspace performs forward delete
    const val KEY_SHOW_ADD_WORD_SUGGESTION = "show_add_word_suggestion" // Offer "add to dictionary" in the suggestions
    const val KEY_LEARN_CONTACT_DETAILS = "learn_contact_details" // Keep emails and phone numbers typed by hand
    const val KEY_LEARN_FREQUENT_WORDS = "learn_frequent_words" // Add words typed often to the dictionary
    const val KEY_ADD_LAST_WORD_SHORTCUT = "add_last_word_shortcut" // Ctrl + Shift + D adds the last word to the dictionary
    internal const val KEY_ALT_BACKSPACE_DELETE = "alt_backspace_delete" // Alt + Backspace performs forward delete
    internal const val KEY_BACKSPACE_AT_START_DELETE = "backspace_at_start_delete" // Backspace at line start performs forward delete
    internal const val KEY_PASTIERINA_MODE_OVERRIDE = "pastierina_mode_override" // pastierina | full_status_bar
    internal const val KEY_PASTIERINA_MODE_ACTIVE = "pastierina_mode_active" // Current effective state
    internal const val KEY_SOFTWARE_KEYBOARD_MODE = "software_keyboard_mode" // auto | force_hardware | force_virtual
    const val KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE = "software_keyboard_mode_runtime_override"
    internal const val KEY_SOFTWARE_KEYBOARD_LAYOUT_STYLE = "software_keyboard_layout_style" // compact | extended_iso | full_ansi | full_iso
    internal const val KEY_SOFTWARE_KEYBOARD_NUMBER_ROW_ENABLED = "software_keyboard_number_row_enabled"
    internal const val KEY_SOFTWARE_KEYBOARD_NEAREST_KEY_TOUCH_ENABLED = "software_keyboard_nearest_key_touch_enabled"
    internal const val KEY_SOFTWARE_KEYBOARD_LEFT_MODIFIER_KEY = "software_keyboard_left_modifier_key"
    internal const val KEY_SOFTWARE_KEYBOARD_RIGHT_MODIFIER_KEY = "software_keyboard_right_modifier_key"
    internal const val KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED = "software_keyboard_long_press_layer_popup_enabled"
    internal const val KEY_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY = "software_keyboard_long_press_layer_popup_below_key"
    internal const val KEY_TITAN2_LAYOUT_ENABLED = "titan2_layout_enabled" // Align OSK with Titan 2 physical layout
    const val KEY_TITAN2_ELITE_MAX_ICON_SHRINK = "titan2_elite_max_icon_shrink"
    const val KEY_TITAN2_ELITE_TOP_CORNER_MULTIPLIER = "titan2_elite_top_corner_multiplier"
    const val KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS = "titan2_elite_rounded_corner_insets"
    const val KEY_TITAN2_ELITE_FILL_CORNERS = "titan2_elite_fill_corners"
    const val KEY_TITAN2_ELITE_STRAIGHT_OUTER_BUTTONS = "titan2_elite_straight_outer_buttons"
    const val KEY_TITAN2_ELITE_STATUS_BAR_LIFT = "titan2_elite_status_bar_lift_dp"
    const val KEY_TITAN2_ELITE_CONTOUR_LEDS = "titan2_elite_contour_leds"
    const val TITAN2_ELITE_DEFAULT_LIFT_DP = 5
    internal const val KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED = "accessibility_live_announcements_enabled" // Whether status bar accessibility live announcements are enabled
    internal const val KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED = "accessibility_read_second_row_enabled" // Whether TalkBack should read quick settings/variations row
    internal const val KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = "accessibility_suggestions_announcement_delay_ms" // Delay before suggestions become accessible again while typing
    internal const val KEY_BOUNCE_KEYS_ENABLED = "bounce_keys_enabled" // Whether repeated same-key taps inside the delay are ignored
    internal const val KEY_BOUNCE_KEYS_DELAY_MS = "bounce_keys_delay_ms" // Minimum delay before the same key can be accepted again
    internal const val KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = "bounce_keys_character_keys_enabled"
    internal const val KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = "bounce_keys_modifier_keys_enabled"
    internal const val KEY_BOUNCE_KEYS_SPACE_ENABLED = "bounce_keys_space_enabled"
    internal const val KEY_BOUNCE_KEYS_ENTER_ENABLED = "bounce_keys_enter_enabled"
    internal const val KEY_BOUNCE_KEYS_BACKSPACE_ENABLED = "bounce_keys_backspace_enabled"
    internal const val KEY_OVERLAPPING_KEYS_ENABLED = "overlapping_keys_enabled"
    internal const val KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE = "global_variation_layout_override" // Optional layout id used for variation ordering across all layouts
    private const val KEY_APP_LANGUAGE_TAG = "app_language_tag" // BCP-47 language tag for app UI (null/blank = system)
    internal const val KEY_APP_ENTER_BEHAVIOR_ENABLED = "app_enter_behavior_enabled"
    internal const val KEY_APP_ENTER_BEHAVIOR_PRESET = "app_enter_behavior_preset"
    internal const val KEY_APP_ENTER_BEHAVIOR_OVERRIDES = "app_enter_behavior_overrides"
    const val KEY_KEYBOARD_THEME_HARDWARE = "keyboard_theme_hardware"
    const val KEY_KEYBOARD_THEME_SOFTWARE = "keyboard_theme_software"
    internal const val KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_HARDWARE = "keyboard_theme_assignment_mode_hardware"
    internal const val KEY_KEYBOARD_THEME_ASSIGNMENT_MODE_SOFTWARE = "keyboard_theme_assignment_mode_software"
    internal const val KEY_KEYBOARD_THEME_LIGHT_HARDWARE = "keyboard_theme_light_hardware"
    internal const val KEY_KEYBOARD_THEME_LIGHT_SOFTWARE = "keyboard_theme_light_software"
    internal const val KEY_KEYBOARD_THEME_DARK_HARDWARE = "keyboard_theme_dark_hardware"
    internal const val KEY_KEYBOARD_THEME_DARK_SOFTWARE = "keyboard_theme_dark_software"
    internal const val KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_HARDWARE = "keyboard_theme_layout_overrides_hardware"
    internal const val KEY_KEYBOARD_THEME_LAYOUT_OVERRIDES_SOFTWARE = "keyboard_theme_layout_overrides_software"
    const val KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED = "fixed"
    const val KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM = "follow_system"
    const val KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN = 1f
    const val KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX = 1.8f
    const val KEYBOARD_THEME_POPUP_STYLE_FLOATING = "floating"
    const val KEYBOARD_THEME_POPUP_STYLE_CLASSIC = "classic"
    internal const val KEY_KEYBOARD_THEME_SAVED_THEMES = "keyboard_theme_saved_themes"
    internal const val KEY_KEYBOARD_THEME_DRAFTS = "keyboard_theme_drafts"
    internal const val KEY_KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE = "keyboard_theme_preview_viewport_scale"
    
    // Status bar button slot configuration keys
    internal const val KEY_STATUS_BAR_SLOT_LEFT = "status_bar_slot_left"
    internal const val KEY_STATUS_BAR_SLOT_RIGHT_1 = "status_bar_slot_right_1"
    internal const val KEY_STATUS_BAR_SLOT_RIGHT_2 = "status_bar_slot_right_2"
    internal const val KEY_STATUS_BAR_SLOTS_LEFT = "status_bar_slots_left"
    internal const val KEY_STATUS_BAR_SLOTS_RIGHT = "status_bar_slots_right"
    internal const val KEY_PASTIERINA_STATUS_BAR_SLOTS_LEFT = "pastierina_status_bar_slots_left"
    internal const val KEY_MENU_BAR_BUTTONS = "menu_bar_buttons" // The menu bar's buttons, in order
    internal const val KEY_PASTIERINA_STATUS_BAR_SLOTS_RIGHT = "pastierina_status_bar_slots_right"
    internal const val KEY_STATUS_BAR_VARIATIONS_VISIBLE = "status_bar_variations_visible"
    internal const val KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT = "dynamic_variation_bar_slot_count"
    internal const val KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT = "dynamic_variation_bar_resize_to_content"
    const val KEY_MODIFIER_INDICATOR_MODE = "modifier_indicator_mode"
    
    // Public constants for button IDs
    const val STATUS_BAR_BUTTON_NONE = "none"
    const val STATUS_BAR_BUTTON_CLIPBOARD = "clipboard"
    const val STATUS_BAR_BUTTON_MICROPHONE = "microphone"
    const val STATUS_BAR_BUTTON_EMOJI = "emoji"
    const val STATUS_BAR_BUTTON_GIF = "gif"
    const val STATUS_BAR_BUTTON_LANGUAGE = "language"
    const val STATUS_BAR_BUTTON_HAMBURGER = "hamburger"
    const val STATUS_BAR_BUTTON_MINIMAL_UI = "minimal_ui"
    const val STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE = "software_keyboard_mode"
    const val STATUS_BAR_BUTTON_SETTINGS = "settings"
    const val STATUS_BAR_BUTTON_SYMBOLS = "symbols"
    const val STATUS_BAR_BUTTON_UNDO = "undo"
    const val STATUS_BAR_BUTTON_REDO = "redo"
    const val MODIFIER_INDICATOR_BOTTOM_STRIP = "bottom_strip"
    const val MODIFIER_INDICATOR_MENU_BAR = "menu_bar"
    const val MODIFIER_INDICATOR_STATUS_BAR = "status_bar"
    const val MODIFIER_INDICATOR_MODE_BOTTOM = "bottom"
    const val MODIFIER_INDICATOR_MODE_BOTTOM_AND_MENU = "bottom_and_menu"
    const val MODIFIER_INDICATOR_MODE_MENU = "menu"
    const val MODIFIER_INDICATOR_MODE_OFF = "off"

    const val STATIC_VARIATION_PRESET_OFF = "off"
    const val STATIC_VARIATION_PRESET_SYMBOLS = "symbols"
    const val STATIC_VARIATION_PRESET_NUMBERS = "numbers"
    const val STATIC_VARIATION_PRESET_ALTERNATIVE = "alternative"
    const val STATIC_VARIATION_PRESET_DEV_CHOICE = "dev_choice"

    const val ENTER_BEHAVIOR_PRESET_APP_DEFAULT = "app_default"
    const val ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE = "enter_send_shift_newline"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND = "enter_newline_ctrl_send"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY = "enter_newline_only"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_SHIFT_SEND = "enter_newline_shift_send"
    const val ENTER_BEHAVIOR_PRESET_CUSTOM = "custom"

    const val ENTER_BEHAVIOR_APP_DEFAULT = "app_default"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE = "enter_newline"
    const val ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE = "enter_send_shift_newline"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND = "enter_newline_ctrl_send"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE_SHIFT_SEND = "enter_newline_shift_send"

    const val ENTER_SEND_STRATEGY_AUTO = "auto"
    const val ENTER_SEND_STRATEGY_EDITOR_ACTION = "editor_action"
    const val ENTER_SEND_STRATEGY_CTRL_ENTER = "ctrl_enter"
    const val ENTER_SEND_STRATEGY_PLAIN_ENTER = "plain_enter"
    const val ENTER_ADDITIONAL_SEND_SHORTCUT_NONE = "none"
    const val ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER = "sym_enter"
    
    // Default slot assignments
    internal const val DEFAULT_SLOT_LEFT = STATUS_BAR_BUTTON_HAMBURGER
    internal const val DEFAULT_SLOT_RIGHT_1 = STATUS_BAR_BUTTON_EMOJI
    internal const val DEFAULT_SLOT_RIGHT_2 = STATUS_BAR_BUTTON_MICROPHONE
    internal const val DEFAULT_PASTIERINA_SLOT_LEFT = STATUS_BAR_BUTTON_LANGUAGE
    internal const val DEFAULT_PASTIERINA_SLOT_RIGHT = STATUS_BAR_BUTTON_HAMBURGER
    internal const val DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE = true
    internal const val DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 7
    internal const val DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT = false
    internal val DEFAULT_MODIFIER_INDICATORS = setOf(MODIFIER_INDICATOR_BOTTOM_STRIP)
    const val MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 1
    const val MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 9

    internal const val VARIATIONS_FILE_NAME = "variations.json"
    
    // Default values
    internal const val DEFAULT_LONG_PRESS_THRESHOLD = 300L
    const val TYPING_SOUND_MODE_OFF = "off"
    const val TYPING_SOUND_MODE_CLICK = "click"
    const val TYPING_SOUND_MODE_TYPEWRITER = "typewriter"
    const val TYPING_SOUND_MODE_CUSTOM = "custom"
    const val TYPING_SOUND_OUTPUT_MEDIA = "media"
    const val TYPING_SOUND_OUTPUT_SYSTEM = "system"
    const val TYPING_SOUND_OUTPUT_NOTIFICATION = "notification"
    internal const val DEFAULT_TYPING_SOUND_MODE = TYPING_SOUND_MODE_OFF
    internal const val DEFAULT_TYPING_SOUND_OUTPUT_MODE = TYPING_SOUND_OUTPUT_MEDIA
    internal const val DEFAULT_TAP_HAPTIC_USE_SYSTEM = true
    internal const val DEFAULT_TAP_HAPTIC_DURATION_MS = 25L
    internal const val MIN_TAP_HAPTIC_DURATION_MS = 5L
    internal const val MAX_TAP_HAPTIC_DURATION_MS = 80L
    internal const val TYPING_SOUND_CUSTOM_DIR = "typing_sounds"
    internal const val TYPING_SOUND_CUSTOM_PACK_DIR = "custom_pack"
    internal const val TYPING_SOUND_MAX_FILE_BYTES = 2L * 1024L * 1024L
    internal const val TYPING_SOUND_MAX_PACK_BYTES = 16L * 1024L * 1024L
    internal const val TYPING_SOUND_MAX_PACK_FILES = 96
    internal val TYPING_SOUND_GROUPS = setOf("normal", "space", "backspace", "enter", "modifier")
    internal val TYPING_SOUND_AUDIO_EXTENSIONS = setOf("ogg", "wav", "mp3", "m4a")
    internal const val MIN_LONG_PRESS_THRESHOLD = 50L
    internal const val MAX_LONG_PRESS_THRESHOLD = 1000L
    internal const val DEFAULT_SWIPE_INCREMENTAL_THRESHOLD = 9.6f
    internal const val MIN_SWIPE_INCREMENTAL_THRESHOLD = 3f
    internal const val MAX_SWIPE_INCREMENTAL_THRESHOLD = 25f
    internal const val DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER = true
    internal const val DEFAULT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF = true
    internal const val DEFAULT_AUTO_CAPITALIZE_RESTRICTED_FIELDS = false
    internal const val DEFAULT_DOUBLE_SPACE_TO_PERIOD = false
    internal const val DEFAULT_SPACED_HYPHEN_TO_EN_DASH = false
    const val DASH_STYLE_EN = "en_dash"
    const val DASH_STYLE_EM = "em_dash"
    internal const val DEFAULT_SPACED_HYPHEN_DASH_STYLE = DASH_STYLE_EN
    internal const val DEFAULT_MID_WORD_QUOTE_TO_APOSTROPHE = false
    internal const val DEFAULT_FRENCH_PUNCTUATION_SPACING = false
    internal const val DEFAULT_FRENCH_PUNCTUATION_ONLY_FRENCH = false
    internal const val DEFAULT_COMMA_SPACE = false
    internal const val DEFAULT_AUTO_SPACE_PUNCTUATION = Punctuation.DEFAULT_AUTO_SPACE
    internal const val DEFAULT_SPACE_AFTER_PUNCTUATION = ""
    internal const val DEFAULT_SMART_QUOTES = false
    const val SMART_QUOTES_STYLE_GERMAN_GUILLEMETS = "german_guillemets"
    const val SMART_QUOTES_STYLE_FRENCH_GUILLEMETS = "french_guillemets"
    const val SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED = "french_guillemets_narrow_spaced"
    const val SMART_QUOTES_STYLE_GERMAN_LOW_HIGH = "german_low_high"
    const val SMART_QUOTES_STYLE_ENGLISH_CURLY = "english_curly"
    internal const val DEFAULT_SMART_QUOTES_STYLE = SMART_QUOTES_STYLE_GERMAN_GUILLEMETS
    internal const val DEFAULT_SWIPE_TO_DELETE = false
    private const val DEFAULT_AUTO_SHOW_KEYBOARD = true
    internal const val DEFAULT_CLEAR_ALT_ON_SPACE = true
    private const val DEFAULT_ALT_CTRL_SPEECH_SHORTCUT = true
    internal const val DEFAULT_LAYOUT_AWARE_CTRL_SHORTCUTS = false
    internal const val DEFAULT_AUTO_CORRECT_ENABLED = true
    internal const val DEFAULT_SUGGESTIONS_ENABLED = true
    internal const val DEFAULT_SNIPPETS_ENABLED = false
    internal const val DEFAULT_SNIPPETS_PREFIX = "!"
    internal const val DEFAULT_ACCENT_MATCHING_ENABLED = true
    internal const val DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER = false
    internal const val DEFAULT_MAX_AUTO_REPLACE_DISTANCE = 1
    internal const val DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD = true
    internal const val DEFAULT_LONG_PRESS_MODIFIER = "alt"
    internal const val DEFAULT_KEYBOARD_LAYOUT = "qwerty"
    internal const val DEFAULT_KEYBOARD_LAYOUT_AUTO_BY_LOCALE = true
    internal const val DEFAULT_ALT_SHIFT_LAYOUT_SWITCH = false
    internal const val DEFAULT_ALT_ENTER_LAYOUT_SWITCH = false
    internal const val DEFAULT_CTRL_SPACE_LAYOUT_SWITCH = true
    internal const val KEY_TOAST_ON_LAYOUT_SWITCH = "toast_on_layout_switch"
    internal const val DEFAULT_TOAST_ON_LAYOUT_SWITCH = true
    internal const val KEY_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS = "software_keyboard_mode_toggle_toasts"
    internal const val DEFAULT_SOFTWARE_KEYBOARD_MODE_TOGGLE_TOASTS = true
    internal const val DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_ENABLED = true
    internal const val DEFAULT_SOFTWARE_KEYBOARD_LONG_PRESS_LAYER_POPUP_BELOW_KEY = true
    internal const val DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE = "auto"
    internal const val DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL = "€"
    internal const val DEFAULT_CLICKS_CLOSE_INPUT_ON_DISCONNECT = false
    internal const val DEFAULT_CLICKS_SHOW_KEYBOARD_ONLY_WITH_TEXT_FOCUS = true
    internal const val DEFAULT_CLICKS_CHARGING_START_PERCENT = 50
    internal const val DEFAULT_CLICKS_CHARGING_STOP_PERCENT = 55
    internal const val DEFAULT_SYM_AUTO_CLOSE = false
    internal const val DEFAULT_SYM_AUTO_CLOSE_ON_TOUCH = false
    internal const val DEFAULT_MODIFIER_TAP_LATCHES = false
    internal const val DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE = false
    internal const val DEFAULT_BOUNCE_KEYS_ENABLED = false
    internal const val DEFAULT_BOUNCE_KEYS_DELAY_MS = 80L
    internal const val MIN_BOUNCE_KEYS_DELAY_MS = 20L
    internal const val MAX_BOUNCE_KEYS_DELAY_MS = 500L
    internal const val DEFAULT_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = true
    internal const val DEFAULT_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = false
    internal const val DEFAULT_BOUNCE_KEYS_SPACE_ENABLED = true
    internal const val DEFAULT_BOUNCE_KEYS_ENTER_ENABLED = true
    internal const val DEFAULT_BOUNCE_KEYS_BACKSPACE_ENABLED = true
    internal const val DEFAULT_OVERLAPPING_KEYS_ENABLED = false
    internal const val DEFAULT_EMOJI_PICKER_EXPANDED_HEIGHT = true
    internal const val DEFAULT_EMOJI_PICKER_KEY = KeyEvent.KEYCODE_SHIFT_RIGHT // fork default
    // Until chosen: only the symbols layer, first; the emoji and device layers are opt-in
    internal val DEFAULT_SYM_PAGES_CONFIG = SymPagesConfig(
        deviceEnabled = false,
        emojiEnabled = false,
        symbolsEnabled = true,
        symPageOrder = listOf(
            SymPagesConfig.PAGE_SYMBOLS, SymPagesConfig.PAGE_EMOJI, SymPagesConfig.PAGE_DEVICE,
            SymPagesConfig.PAGE_CLIPBOARD, SymPagesConfig.PAGE_EMOJI_PICKER
        )
    )
    internal const val SYM_PAGES_SCHEMA_VERSION = 2
    internal const val DEFAULT_STATIC_VARIATION_BAR_MODE = false
    internal const val DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED = false
    internal const val DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED = true
    internal const val DEFAULT_SUGGESTION_DEBUG_LOGGING = true
    internal const val KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED = "experimental_suggestions_enabled"
    internal const val KEY_SUGGESTION_DEBUG_LOGGING = "suggestion_debug_logging"
    internal const val KEY_IME_OVERLAY_DEBUG_LOGGING = "ime_overlay_debug_logging"
    internal const val KEY_EXPERIMENTAL_CANDIDATES_VIEW_ENABLED = "experimental_candidates_view_enabled"
    internal const val KEY_USE_KEYBOARD_PROXIMITY = "use_keyboard_proximity"
    internal const val KEY_USE_EDIT_TYPE_RANKING = "use_edit_type_ranking"

    internal const val DEFAULT_USE_KEYBOARD_PROXIMITY = false
    internal const val DEFAULT_USE_EDIT_TYPE_RANKING = false
    internal const val DEFAULT_IME_OVERLAY_DEBUG_LOGGING = false
    private const val DEFAULT_CLIPBOARD_HISTORY_ENABLED = true
    private const val DEFAULT_CLIPBOARD_RETENTION_TIME = 120L // 2 hours in minutes
    internal const val DEFAULT_TRACKPAD_GESTURES_ENABLED = false
    internal const val DEFAULT_TRACKPAD_GESTURE_ADD_WORD_ENABLED = true
    internal const val DEFAULT_TRACKPAD_GESTURE_ADD_WORD_FULL_WIDTH_ENABLED = true
    private const val DEFAULT_TRACKPAD_SWIPE_THRESHOLD = 500f
    private const val TITAN2_ELITE_TRACKPAD_SWIPE_THRESHOLD = 40f
    internal const val MIN_TRACKPAD_SWIPE_THRESHOLD = 40f
    /** Flux Keyboard: a deliberate swipe, so scrolling past the suggestions doesn't pick one. */
    internal const val DEFAULT_TRACKPAD_SUGGESTION_SWIPE_THRESHOLD = 100f
    internal const val MAX_TRACKPAD_SWIPE_THRESHOLD = 750f
    const val TRACKPAD_PROVIDER_SHIZUKU = "shizuku"
    const val TRACKPAD_PROVIDER_NATIVE_IME = "native_ime"
    const val TRACKPAD_SHIZUKU_DEVICE_AUTO = "auto"
    internal const val DEFAULT_TRACKPAD_PROVIDER = TRACKPAD_PROVIDER_NATIVE_IME
    internal val TRACKPAD_PROVIDER_VALUES = setOf(
        TRACKPAD_PROVIDER_SHIZUKU,
        TRACKPAD_PROVIDER_NATIVE_IME
    )
    const val SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE = "titan2_keycode"
    const val SWIPE_TO_DELETE_PROVIDER_NATIVE_IME = "native_ime"
    internal const val DEFAULT_SWIPE_TO_DELETE_PROVIDER = SWIPE_TO_DELETE_PROVIDER_NATIVE_IME
    internal val SWIPE_TO_DELETE_PROVIDER_VALUES = setOf(
        SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE,
        SWIPE_TO_DELETE_PROVIDER_NATIVE_IME
    )
    internal const val DEFAULT_SHIFT_BACKSPACE_DELETE = false
    internal const val DEFAULT_ALT_BACKSPACE_DELETE = false
    internal const val DEFAULT_BACKSPACE_AT_START_DELETE = false
    internal const val DEFAULT_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED = false
    internal const val DEFAULT_ACCESSIBILITY_READ_SECOND_ROW_ENABLED = false
    internal const val DEFAULT_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 500L
    internal const val DEFAULT_GLOBAL_VARIATION_LAYOUT_OVERRIDE = ""
    internal const val MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 100L
    internal const val MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 2000L
    internal val STATIC_VARIATION_BASE_PRESET_DEFAULT = listOf("@", "\"", ":", "!", "?", ",", ".")
    internal val STATIC_VARIATION_BASE_PRESET_NUMBERS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    internal val STATIC_VARIATION_BASE_PRESET_ALTERNATIVE = listOf("[", "]", "$", "%", "^", "&", "\\")
    internal val STATIC_VARIATION_BASE_PRESET_DEV_CHOICE = listOf("»", "«", ";", "!", "?", ",", ".", "–", "%")
    internal val STATIC_VARIATION_SHIFT_PRESET_DEFAULT = listOf("{", "}", "€", "=", "~", ";", "¿")
    internal val STATIC_VARIATION_ALT_PRESET_DEFAULT = listOf("<", ">", "¥", "|", "`", "´", "°")

    enum class StatusBarPresentationMode(val storageValue: String) {
        PASTIERINA("pastierina"),
        FULL_STATUS_BAR("full_status_bar")
    }

    enum class SoftwareKeyboardMode(val storageValue: String) {
        AUTO("auto"),
        FORCE_HARDWARE("force_hardware"),
        FORCE_VIRTUAL("force_virtual")
    }

    enum class SoftwareKeyboardLayoutStyle(val storageValue: String) {
        COMPACT("compact"),
        EXTENDED_ISO("extended_iso"),
        FULL_ANSI("full_ansi"),
        FULL_ISO("full_iso")
    }

    enum class SoftwareKeyboardModifierKey(val storageValue: String) {
        CTRL("ctrl"),
        ALT("alt")
    }

    enum class KeyboardThemeTarget {
        HARDWARE,
        SOFTWARE
    }

    data class KeyboardThemeSettings(
        val background: Int,
        val divider: Int,
        val normalKey: Int,
        val specialKey: Int,
        val textAndIcons: Int,
        val ledInactive: Int,
        val ledActive: Int,
        val ledLocked: Int,
        val accent: Int,
        val cursorSwipe: Int = accent,
        val keyPopup: Int = specialKey,
        val keyPopupSelected: Int = accent,
        val suggestion: Int = normalKey,
        val statusBarButton: Int = specialKey,
        val keyCornerRadiusRatio: Float = 0.08f,
        val chromeCornerRadiusRatio: Float = 0.08f,
        val keyHeightScale: Float = 1f,
        val numberRowHeightScale: Float = 0.8f,
        val keyWidthScale: Float = 1f,
        val rowGapScale: Float = 0f,
        val distributeHorizontalSpacing: Boolean = true,
        val ortholinear: Boolean = false,
        val showLeds: Boolean = true,
        val suggestionsHeightScale: Float = 1f,
        val variationsHeightScale: Float = 1f,
        val keyPopupStyle: String = KEYBOARD_THEME_POPUP_STYLE_FLOATING,
        val keyPopupAttached: Boolean = true,
        val keyPopupTailEnabled: Boolean = true,
        val keyPreviewAfterLongPress: Boolean = false,
        val keyAlternatesPopupEnabled: Boolean = true
    ) {
        fun toKeyboardThemeColors(): KeyboardThemeColors =
            KeyboardThemeColors(
                background = background,
                divider = divider,
                normalKey = normalKey,
                specialKey = specialKey,
                textAndIcons = textAndIcons,
                ledInactive = ledInactive,
                ledActive = ledActive,
                ledLocked = ledLocked,
                accent = accent,
                cursorSwipe = cursorSwipe,
                keyPopup = keyPopup,
                keyPopupSelected = keyPopupSelected,
                suggestion = suggestion,
                statusBarButton = statusBarButton,
                keyCornerRadiusRatio = keyCornerRadiusRatio,
                chromeCornerRadiusRatio = chromeCornerRadiusRatio,
                suggestionsHeightScale = suggestionsHeightScale,
                variationsHeightScale = variationsHeightScale
            )
    }

    data class NamedKeyboardTheme(
        val name: String,
        val theme: KeyboardThemeSettings
    )

    data class KeyboardThemeDraft(
        val name: String,
        val theme: KeyboardThemeSettings,
        val populatedFields: Set<String> = emptySet()
    )

    data class KeyboardThemeLayoutOverride(
        val locale: String?,
        val layout: String?,
        val theme: KeyboardThemeSettings
    )

    /**
     * Returns the SharedPreferences instance for the keyboard.
     */
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppLanguageTag(context: Context): String? {
        return getPreferences(context).getString(KEY_APP_LANGUAGE_TAG, null)?.takeIf { it.isNotBlank() }
    }

    fun setAppLanguageTag(context: Context, languageTag: String?) {
        getPreferences(context).edit()
            .putString(KEY_APP_LANGUAGE_TAG, languageTag?.takeIf { it.isNotBlank() })
            .apply()
    }

    /** Classic Cloud: the light built-in theme, and the default for a fixed theme. */
    internal val CLASSIC_CLOUD = KeyboardThemeSettings(
        background = 0xFFCCD2DC.toInt(),
        divider = 0xFF9EA5AF.toInt(),
        normalKey = 0xFFFFFFFF.toInt(),
        specialKey = 0xFFAFB6C2.toInt(),
        textAndIcons = 0xFF000000.toInt(),
        ledInactive = 0xFFAEB5C0.toInt(),
        ledActive = 0xFF007AFF.toInt(),
        ledLocked = 0xFFFF9500.toInt(),
        accent = 0xFF007AFF.toInt(),
        cursorSwipe = 0xFF007AFF.toInt(),
        keyPopup = 0xFFFFFFFF.toInt(),
        keyPopupSelected = 0xFF007AFF.toInt(),
        suggestion = 0xFFCCD2DC.toInt(),
        statusBarButton = 0xFFAFB6C2.toInt(),
        keyCornerRadiusRatio = 0.118f,
        chromeCornerRadiusRatio = 0.09f,
        // Sizes from Flux Keyboard's own Titan 2 Elite configuration
        keyHeightScale = 1.259f,
        numberRowHeightScale = 0.971f,
        keyWidthScale = 0.941f,
        rowGapScale = 1.05f,
        showLeds = false,
        suggestionsHeightScale = 0.9f,
        variationsHeightScale = 0.88f,
        keyPopupStyle = KEYBOARD_THEME_POPUP_STYLE_CLASSIC
    )

    /** Classic Midnight: the dark built-in theme. */
    internal val CLASSIC_MIDNIGHT = KeyboardThemeSettings(
        background = 0xFF1C1C1E.toInt(),
        divider = 0xFF4A4A4D.toInt(),
        normalKey = 0xFF3A3A3C.toInt(),
        specialKey = 0xFF2C2C2E.toInt(),
        textAndIcons = 0xFFFFFFFF.toInt(),
        ledInactive = 0xFF404044.toInt(),
        ledActive = 0xFF0A84FF.toInt(),
        ledLocked = 0xFFFF9F0A.toInt(),
        accent = 0xFF0A84FF.toInt(),
        cursorSwipe = 0xFF0A84FF.toInt(),
        keyPopup = 0xFF3A3A3C.toInt(),
        keyPopupSelected = 0xFF0A84FF.toInt(),
        suggestion = 0xFF202124.toInt(),
        statusBarButton = 0xFF2C2C2E.toInt(),
        keyCornerRadiusRatio = 0.118f,
        chromeCornerRadiusRatio = 0.09f,
        // Sizes from Flux Keyboard's own Titan 2 Elite configuration
        keyHeightScale = 1.259f,
        numberRowHeightScale = 0.971f,
        keyWidthScale = 0.941f,
        rowGapScale = 1.05f,
        showLeds = false,
        suggestionsHeightScale = 0.9f,
        variationsHeightScale = 0.88f,
        keyPopupStyle = KEYBOARD_THEME_POPUP_STYLE_CLASSIC
    )

    const val KEYBOARD_BACKGROUND_KEY_OPACITY_DEFAULT = 35

    internal fun Any?.isJsonInt(): Boolean {
        val number = this as? Number ?: return false
        val doubleValue = number.toDouble()
        return doubleValue.isFinite() &&
            doubleValue % 1.0 == 0.0 &&
            doubleValue >= Int.MIN_VALUE.toDouble() &&
            doubleValue <= Int.MAX_VALUE.toDouble()
    }

    internal fun Any?.isFiniteJsonNumber(): Boolean =
        (this as? Number)?.toDouble()?.isFinite() == true

    // Flux Keyboard: trackpad swipe directions
    internal const val KEY_TRACKPAD_SUGGESTION_SWIPE_DIRECTIONS = "trackpad_suggestion_swipe_directions"
    internal const val KEY_TRACKPAD_SWIPE_DOWN_DELETES_WORD = "trackpad_swipe_down_deletes_word"

    /**
     * Returns the state of automatically showing the keyboard when a field gains focus.
     */
    fun getAutoShowKeyboard(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_SHOW_KEYBOARD, DEFAULT_AUTO_SHOW_KEYBOARD)
    }
    
    /**
     * Sets the state of automatically showing the keyboard when a field gains focus.
     */
    fun setAutoShowKeyboard(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_SHOW_KEYBOARD, enabled)
            .apply()
    }

    /**
     * Returns whether Alt+Ctrl shortcut for speech recognition is enabled.
     */
    fun getAltCtrlSpeechShortcutEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, DEFAULT_ALT_CTRL_SPEECH_SHORTCUT)
    }

    /**
     * Sets whether Alt+Ctrl shortcut for speech recognition is enabled.
     */
    fun setAltCtrlSpeechShortcutEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, enabled)
            .apply()
    }

    enum class ClicksPowerButtonMode(val persistedValue: String) {
        NATIVE("native"),
        QUICK_LAUNCHER("quick_launcher"),
        OPEN_PASTIERA("open_pastiera"),
        TOGGLE_KEYBOARD_MODE("toggle_keyboard_mode"),
        TOGGLE_EMOJI_PICKER("toggle_emoji_picker"),
        ALT("alt"),
        TAB("tab"),
        SYM("sym");

        companion object {
            fun fromPersistedValue(value: String?): ClicksPowerButtonMode =
                entries.firstOrNull { it.persistedValue == value } ?: NATIVE
        }
    }

    internal fun ByteArray.encodeClicksRemapOutput(): String = joinToString(separator = "") {
        "%02x".format(it.toInt() and 0xff)
    }

    internal fun String.decodeClicksRemapOutput(): ByteArray? {
        if (length != 4) return null
        return runCatching {
            byteArrayOf(substring(0, 2).toInt(16).toByte(), substring(2, 4).toInt(16).toByte())
        }.getOrNull()
    }

    /**
     * Keyboard swipes per app, with Scroll assistant on for every app: off, kept from the listed
     * apps (blocked there), or kept only in the listed apps (blocked everywhere else).
     */
    enum class TrackpadAppMode(val id: String) { OFF("off"), BLOCK("block"), KEEP("keep") }

    /** Voice input keeps listening through pauses until you stop it or stay silent. On by default. */
    fun getSpeechKeepListening(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_SPEECH_KEEP_LISTENING, true)

    /** How long a pause (seconds) ends voice input while it keeps listening. 3 s by default. */
    fun getSpeechPauseTimeoutSeconds(context: Context): Int =
        getPreferences(context).getInt("speech_pause_timeout_seconds", 3).coerceIn(1, 20)

    fun setSpeechPauseTimeoutSeconds(context: Context, seconds: Int) {
        getPreferences(context).edit().putInt("speech_pause_timeout_seconds", seconds.coerceIn(1, 20)).apply()
    }

    fun setSpeechKeepListening(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_SPEECH_KEEP_LISTENING, enabled).apply()
    }

    /** The Titan 2 layout option applies here: a Titan 2, or it's already on elsewhere (so it can be turned off). */
    fun titan2LayoutApplies(context: Context): Boolean =
        it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2Device() || isTitan2LayoutEnabled(context)

    /** Links the keyboard pastes lose their tracking parameters and mobile hosts. On by default. */
    fun getCleanPastedLinks(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_CLEAN_PASTED_LINKS, true)

    fun setCleanPastedLinks(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_CLEAN_PASTED_LINKS, enabled).apply()
    }

    /** [text] as the keyboard pastes it: links cleaned when that's on. */
    fun textToPaste(context: Context, text: String): String =
        if (getCleanPastedLinks(context)) it.palsoftware.pastiera.clipboard.LinkCleaner.clean(text) else text

    /** Remember the language per app: each app gets back the language last used in it. */
    fun getLanguagePerAppEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_LANGUAGE_PER_APP, true)

    fun setLanguagePerAppEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_LANGUAGE_PER_APP, enabled).apply()
        if (!enabled) context.getSharedPreferences(APP_LANGUAGES_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** The language (a subtype key, see SubtypeCycler.subtypeKey) last used in [packageName]. */
    fun getAppLanguage(context: Context, packageName: String): String? =
        context.getSharedPreferences(APP_LANGUAGES_PREFS, Context.MODE_PRIVATE).getString(packageName, null)

    fun setAppLanguage(context: Context, packageName: String, subtypeKey: String) {
        context.getSharedPreferences(APP_LANGUAGES_PREFS, Context.MODE_PRIVATE).edit()
            .putString(packageName, subtypeKey).apply()
    }

    /** In a search bar an app focuses as it opens, the keyboard bar shows only once you type. */
    fun getSearchBarWaitsForTyping(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_SEARCH_BAR_WAITS_FOR_TYPING, false)

    fun setSearchBarWaitsForTyping(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_SEARCH_BAR_WAITS_FOR_TYPING, enabled).apply()
    }

    /** The kinds of text field with automatic Shift (ShiftFieldTypes ids); null before it was set. */
    fun getAutoShiftFieldTypes(context: Context): Set<String>? =
        getPreferences(context).getString(KEY_AUTO_SHIFT_FIELD_TYPES, null)
            ?.split(',')?.filter { it.isNotBlank() }?.toSet()

    fun setAutoShiftFieldTypes(context: Context, ids: Set<String>) {
        getPreferences(context).edit().putString(KEY_AUTO_SHIFT_FIELD_TYPES, ids.sorted().joinToString(",")).apply()
    }

    /** One-time codes from notifications, offered as a chip (needs notification access). */
    fun getOneTimeCodesEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_ONE_TIME_CODES, false)

    fun setOneTimeCodesEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ONE_TIME_CODES, enabled).apply()
        if (!enabled) it.palsoftware.pastiera.otp.OneTimeCodes.consume()
    }

    /** Whether Android lets the app read notifications (for one-time codes). */
    fun hasNotificationAccess(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun getPasteSuggestionEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_PASTE_SUGGESTION, true)

    fun setPasteSuggestionEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_PASTE_SUGGESTION, enabled).apply()
    }

    /** The paste suggestion in password fields too, masked (a password you just copied). */
    fun getPasteSuggestionInPasswordFields(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_PASTE_SUGGESTION_PASSWORD_FIELDS, true)

    fun setPasteSuggestionInPasswordFields(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_PASTE_SUGGESTION_PASSWORD_FIELDS, enabled).apply()
    }

    /** Incognito typing everywhere: the keyboard learns nothing from what you type. */
    fun getIncognitoAlways(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_INCOGNITO_ALWAYS, false)

    fun setIncognitoAlways(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_INCOGNITO_ALWAYS, enabled).apply()
    }

    /** Incognito in fields whose app asks keyboards not to learn (private tabs, some messengers). */
    fun getIncognitoFollowApps(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_INCOGNITO_FOLLOW_APPS, true)

    fun setIncognitoFollowApps(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_INCOGNITO_FOLLOW_APPS, enabled).apply()
    }

    /** Whether typing in this field is incognito (see [getIncognitoAlways], [getIncognitoFollowApps]). */
    fun isIncognitoField(context: Context, imeOptions: Int): Boolean =
        getIncognitoAlways(context) ||
            (getIncognitoFollowApps(context) &&
                imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)

    /** Developer options (calibration, debugging and preview tools) are shown in the settings. */
    /** Developer options: on by default in dev builds (x.yy-flux.<time>), off in full releases. */
    fun getDeveloperOptionsEnabled(context: Context): Boolean =
        getPreferences(context).getBoolean(KEY_DEVELOPER_OPTIONS_ENABLED, BuildConfig.VERSION_NAME.contains("-flux."))

    fun setDeveloperOptionsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_DEVELOPER_OPTIONS_ENABLED, enabled).apply()
    }

    /**
     * Special JSON field for the language name.
     */
    internal const val LANGUAGE_NAME_KEY = "__name"

    /**
     * A launcher shortcut.
     * Its type says what it opens (an app, a shortcut, a command…).
     */
    data class LauncherShortcut(
        val type: String = TYPE_APP, // Tipo di azione: "app", "shortcut", ecc.
        val packageName: String? = null, // For type "app"
        val appName: String? = null, // For type "app"
        val action: String? = null, // For type "shortcut" and others
        val data: String? = null, // Extra data for other types
        val commandId: String? = null,
        val commandSource: String? = null,
        val commandKind: String? = null,
        val commandTitle: String? = null,
        val commandSubtitle: String? = null,
        val commandLaunch: CommandLaunchSpec? = null
    ) {
        companion object {
            const val TYPE_APP = "app"
            const val TYPE_SHORTCUT = "shortcut"
            const val TYPE_QUICK_LAUNCHER = "quick_launcher"
            const val TYPE_COMMAND = "command"
            // Aggiungi altri tipi in futuro qui
        }
    }
    
    internal const val KEY_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
    internal const val KEY_LAUNCHER_SHORTCUTS_ENABLED = "launcher_shortcuts_enabled"
    internal const val KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED = "quick_launcher_default_assigned"
    internal const val KEY_QUICK_LAUNCHER_AUTO_START_SINGLE = "quick_launcher_auto_start_single"
    internal const val KEY_QUICK_LAUNCHER_LIMIT_RESULTS = "quick_launcher_limit_results"
    internal const val KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS = "quick_launcher_text_field_shortcuts"
    internal const val KEY_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS = "quick_launcher_alt_space_in_text_fields"
    internal const val KEY_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS = "quick_launcher_alt_shortcuts_outside_text_fields"
    internal const val KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT = "quick_launcher_respect_keyboard_layout"
    internal const val KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING = "quick_launcher_typo_tolerant_ranking"
    internal const val KEY_QUICK_LAUNCHER_WIDTH_PERCENT = "quick_launcher_width_percent"
    internal const val KEY_QUICK_LAUNCHER_PILL_MODE = "quick_launcher_pill_mode"
    internal const val KEY_QUICK_LAUNCHER_BEHAVIOR = "quick_launcher_behavior"
    internal const val KEY_QUICK_LAUNCHER_ANIMATION_DURATION_MS = "quick_launcher_animation_duration_ms"
    internal const val KEY_COMMAND_SURFACE_SOURCES = "command_surface_sources"
    internal const val KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS = "quick_launcher_command_customizations"
    internal const val KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES = "quick_launcher_highlight_favorites"
    internal const val KEY_QUICK_LAUNCHER_FAVORITE_COLOR = "quick_launcher_favorite_color"
    internal const val KEY_QUICK_LAUNCHER_ICON_COLORS = "quick_launcher_icon_colors"
    internal const val KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST = "quick_launcher_show_alias_first"
    internal const val KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT = "quick_launcher_static_top_highlight"
    internal const val KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = "quick_launcher_static_top_highlight_color"
    internal const val DEFAULT_LAUNCHER_SHORTCUTS_ENABLED = false
    internal const val DEFAULT_QUICK_LAUNCHER_AUTO_START_SINGLE = false
    internal const val DEFAULT_QUICK_LAUNCHER_LIMIT_RESULTS = false
    internal const val DEFAULT_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS = true
    internal const val DEFAULT_QUICK_LAUNCHER_ALT_SPACE_IN_TEXT_FIELDS = false
    internal const val DEFAULT_QUICK_LAUNCHER_ALT_SHORTCUTS_OUTSIDE_TEXT_FIELDS = false
    internal const val DEFAULT_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT = true
    internal const val DEFAULT_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING = true
    internal const val DEFAULT_QUICK_LAUNCHER_WIDTH_PERCENT = 100
    internal const val DEFAULT_QUICK_LAUNCHER_PILL_MODE = false
    internal const val DEFAULT_QUICK_LAUNCHER_ANIMATION_DURATION_MS = 120
    const val QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR = Int.MIN_VALUE
    internal const val DEFAULT_QUICK_LAUNCHER_FAVORITE_COLOR = QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR
    internal const val DEFAULT_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = 0x7A4285F4
    const val QUICK_LAUNCHER_BEHAVIOR_PASTIERA = "pastiera"
    const val QUICK_LAUNCHER_BEHAVIOR_NIAGARA = "niagara"
    const val QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS = 0
    const val QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS = 320
    
    // Nav mode settings
    internal const val KEY_NAV_MODE_ENABLED = "nav_mode_enabled"
    internal const val DEFAULT_NAV_MODE_ENABLED = true
    internal const val KEY_NAV_MODE_CTRL_HOLD_ENABLED = "nav_mode_ctrl_hold_enabled"
    internal const val DEFAULT_NAV_MODE_CTRL_HOLD_ENABLED = false
    internal const val NAV_MODE_MAPPINGS_FILE_NAME = "ctrl_key_mappings.json"
    internal const val KEY_NAV_MODE_MAPPINGS_UPDATED = "nav_mode_mappings_updated"

    internal fun LauncherShortcut.isQuickLauncherCommand(): Boolean {
        return type == LauncherShortcut.TYPE_QUICK_LAUNCHER ||
            commandId == PastieraCommandSource.COMMAND_QUICK_LAUNCHER ||
            commandLaunch == CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
    }

    data class CommandSourceVisibility(
        val sourceId: String,
        val quickLauncherEnabled: Boolean
    )

    data class QuickLauncherCommandCustomization(
        val commandId: String,
        val favorite: Boolean = false,
        val hidden: Boolean = false,
        val customSearch: String = "",
        val favoriteOrder: Int = Int.MAX_VALUE,
        val color: Int? = null
    )

    // Flux Keyboard: which releases updates offer
    internal const val KEY_FORK_UPDATE_CHANNEL = "fork_update_channel"
    const val FORK_UPDATE_CHANNEL_STABLE = "stable"
    const val FORK_UPDATE_CHANNEL_DEV = "dev"

    // Flux Keyboard: quick launcher extras
    internal const val KEY_QUICK_LAUNCHER_APP_SHORTCUTS = "quick_launcher_app_shortcuts"
    internal const val KEY_NIAGARA_BACK_RETURNS = "quick_launcher_niagara_back_returns"
    internal const val KEY_QUICK_LAUNCHER_LISTED_APP_SHORTCUTS = "quick_launcher_listed_app_shortcuts"

    // Power Shortcuts settings
    internal const val KEY_POWER_SHORTCUTS_ENABLED = "power_shortcuts_enabled"
    internal const val DEFAULT_POWER_SHORTCUTS_ENABLED = true

    enum class ClicksOverlappingKeysMode(val persistedValue: String) {
        OFF("off"),
        ADJACENT_ONLY("adjacent_only"),
        ALL_NON_MODIFIERS("all_non_modifiers");

        companion object {
            fun fromPersistedValue(value: String?): ClicksOverlappingKeysMode =
                entries.firstOrNull { it.persistedValue == value } ?: OFF
        }
    }

    enum class ClicksNumberRowInputMode(val persistedValue: String) {
        NORMAL("normal"),
        IGNORE_WHILE_ADJACENT_KEY_HELD("ignore_while_adjacent_key_held"),
        IGNORE_WHILE_ANY_KEY_HELD("ignore_while_any_key_held"),
        LONG_PRESS("long_press"),
        IGNORE_ALL("ignore_all");

        companion object {
            fun fromPersistedValue(value: String?): ClicksNumberRowInputMode = when (value) {
                // Compatibility with the first, uncommitted implementation installed on test devices.
                "ignore_while_other_key_held" -> IGNORE_WHILE_ANY_KEY_HELD
                else -> entries.firstOrNull { it.persistedValue == value } ?: NORMAL
            }
        }
    }

    /** Keys that type or edit text, or that Android and the keyboard need while typing. */
    internal val EMOJI_PICKER_KEY_DENYLIST: Set<Int> = setOf(
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_FORWARD_DEL,
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_SYM,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER
    )

    internal val PACKAGE_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    // Niagara Launcher and Termux:X11 take the keys without the keyboard on screen
    const val DEFAULT_HIDDEN_KEYBOARD_APPS = "bitpit.launcher\ncom.termux.x11"

    // Flux Keyboard: sticky SYM and emoji keys, and the emoji key's own LED
    internal const val KEY_SYM_STICKY_TAP = "sym_sticky_tap"
    internal const val KEY_EMOJI_STICKY_TAP = "emoji_sticky_tap"
    internal const val KEY_EMOJI_KEY_LED = "emoji_key_led"

    /** Keys of the emoji layer (letters) that can become its Recents key. */
    val EMOJI_LAYER_KEYS: List<Int> = listOf(
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T,
        KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P,
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G,
        KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L,
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V,
        KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
    )

    /**
     * Returns the set of dismissed release tag names.
     * @param context The context
     * @return Set of release tag names that were dismissed by the user
     */
    fun getDismissedReleases(context: Context): Set<String> {
        val prefs = getPreferences(context)
        val dismissedString = prefs.getString(KEY_DISMISSED_RELEASES, null) ?: return emptySet()
        return if (dismissedString.isBlank()) {
            emptySet()
        } else {
            dismissedString.split(",").toSet()
        }
    }
    
    /**
     * Adds a release tag name to the dismissed releases set.
     * @param context The context
     * @param tagName The release tag name to dismiss
     */
    fun addDismissedRelease(context: Context, tagName: String) {
        val dismissed = getDismissedReleases(context).toMutableSet()
        dismissed.add(tagName)
        val dismissedString = dismissed.joinToString(",")
        getPreferences(context).edit()
            .putString(KEY_DISMISSED_RELEASES, dismissedString)
            .apply()
    }
    
    /**
     * Checks if a release tag name has been dismissed.
     * @param context The context
     * @param tagName The release tag name to check
     * @return true if the release was dismissed, false otherwise
     */
    fun isReleaseDismissed(context: Context, tagName: String): Boolean {
        return getDismissedReleases(context).contains(tagName)
    }
    
    /**
     * Checks if the tutorial has been completed.
     * @param context The context
     * @return true if the tutorial has been completed, false otherwise
     */
    fun isTutorialCompleted(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Marks the tutorial as completed.
     * @param context The context
     */
    fun setTutorialCompleted(context: Context) {
        getPreferences(context).edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, true)
            .putString(KEY_LAST_SEEN_WHATS_NEW_VERSION, BuildConfig.VERSION_NAME)
            .apply()
    }
    
    /**
     * Resets the tutorial completion status, allowing it to be shown again.
     * @param context The context
     */
    fun resetTutorialCompleted(context: Context) {
        getPreferences(context).edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, false)
            .apply()
    }

    fun shouldShowWhatsNew(context: Context, currentVersion: String): Boolean {
        if (!isTutorialCompleted(context)) return false
        val normalizedCurrent = currentVersion.trim()
        if (normalizedCurrent.isBlank()) return false

        val lastSeen = getPreferences(context).getString(KEY_LAST_SEEN_WHATS_NEW_VERSION, null)
        return lastSeen != normalizedCurrent
    }

    fun getLastSeenWhatsNewVersion(context: Context): String? {
        return getPreferences(context)
            .getString(KEY_LAST_SEEN_WHATS_NEW_VERSION, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun markWhatsNewSeen(context: Context, version: String) {
        val normalizedVersion = version.trim()
        if (normalizedVersion.isBlank()) return

        getPreferences(context).edit()
            .putString(KEY_LAST_SEEN_WHATS_NEW_VERSION, normalizedVersion)
            .apply()
    }

    /**
     * Returns whether clipboard history is enabled.
     * @param context The context
     * @return true if clipboard history is enabled, false otherwise
     */
    fun getClipboardHistoryEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, DEFAULT_CLIPBOARD_HISTORY_ENABLED)
    }

    /**
     * Sets whether clipboard history is enabled.
     * @param context The context
     * @param enabled Whether to enable clipboard history
     */
    fun setClipboardHistoryEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the clipboard retention time in minutes.
     * Entries older than this will be automatically deleted (unless pinned).
     * @param context The context
     * @return Retention time in minutes (e.g. 120 = 2 hours)
     */
    fun getClipboardRetentionTime(context: Context): Long {
        return getPreferences(context).getLong(KEY_CLIPBOARD_RETENTION_TIME, DEFAULT_CLIPBOARD_RETENTION_TIME)
    }

    /**
     * Sets the clipboard retention time in minutes.
     * @param context The context
     * @param minutes Retention time in minutes (e.g. 120 = 2 hours)
     */
    fun setClipboardRetentionTime(context: Context, minutes: Long) {
        getPreferences(context).edit()
            .putLong(KEY_CLIPBOARD_RETENTION_TIME, minutes)
            .apply()
    }

    // Custom Input Styles (Additional Subtypes)
    internal const val KEY_CUSTOM_INPUT_STYLES = "custom_input_styles"
    internal const val KEY_INPUT_STYLE_SUGGESTION_LOCALES = "input_style_suggestion_locales"
    internal const val KEY_HIDDEN_SYSTEM_INPUT_STYLES = "hidden_system_input_styles"

    // ========================
    // Status Bar Button Slots
    // ========================

    data class StatusBarSlotDefaults(
        val left: String,
        val right1: String,
        val right2: String
    )

    /** Buttons the menu bar (the ☰ button's row) can show, in their default order. */
    val MENU_BAR_BUTTON_OPTIONS: List<String> = listOf(
        STATUS_BAR_BUTTON_SYMBOLS,
        STATUS_BAR_BUTTON_EMOJI,
        STATUS_BAR_BUTTON_GIF,
        STATUS_BAR_BUTTON_MICROPHONE,
        STATUS_BAR_BUTTON_CLIPBOARD,
        STATUS_BAR_BUTTON_UNDO,
        STATUS_BAR_BUTTON_REDO,
        STATUS_BAR_BUTTON_LANGUAGE,
        STATUS_BAR_BUTTON_MINIMAL_UI,
        STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE,
        STATUS_BAR_BUTTON_SETTINGS
    )

    /**
     * Until chosen, the menu bar leaves out the symbols, emoji and GIF buttons (the SYM and emoji
     * keys open those), Solderina and keyboard mode; and the language button while only one
     * input language is on.
     */
    internal val MENU_BAR_OFF_BY_DEFAULT = setOf(
        STATUS_BAR_BUTTON_SYMBOLS,
        STATUS_BAR_BUTTON_EMOJI,
        STATUS_BAR_BUTTON_GIF,
        STATUS_BAR_BUTTON_MINIMAL_UI,
        STATUS_BAR_BUTTON_SOFTWARE_KEYBOARD_MODE
    )

    data class AppEnterBehaviorOverride(
        val packageName: String,
        val behavior: String,
        val sendStrategy: String = ENTER_SEND_STRATEGY_AUTO,
        val additionalSendShortcut: String = ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    )

}
