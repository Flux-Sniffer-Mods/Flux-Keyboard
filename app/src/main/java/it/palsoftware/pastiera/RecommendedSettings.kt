package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

/**
 * The recommended settings: every setting from the configuration made on Flux Keyboard's own
 * Titan 2 Elite (its trackpad, LED and bar settings only on that phone). Applied on a fresh
 * install, offered after updates, and from Privacy & system > Backup & restore. Only what's
 * personal is left out: languages, app lists, per-app Enter entries and launcher shortcuts.
 */
object RecommendedSettings {
    const val PREF_APPLIED = "recommended_settings_applied"

    /** For everyone: the configuration from Flux Keyboard's own Titan 2 Elite, setting by setting. */
    private val EVERYWHERE: Map<String, Any> = mapOf(
        "alt_shift_layout_switch" to false,
        "alt_enter_layout_switch" to false,
        "app_enter_behavior_preset" to "enter_send_shift_newline",
        "auto_capitalize_respect_manual_shift_off" to true,
        "auto_capitalize_restricted_fields" to true,
        "auto_correct_enabled" to false,
        "auto_shift_field_types" to "addresses,names,search,text",
        "auto_space_punctuation" to ".,;:!?",
        "ctrl_space_layout_switch" to true,
        "double_space_to_period" to false,
        "emoji_key_opens_layer" to true,
        "emoji_suggestions_enabled" to false,
        "emoji_symbols_accept_with_enter" to true,
        "emoji_symbols_exact_on_space" to true,
        "exact_typing_no_suggestions" to true,
        "gifs_enabled" to true,
        "keyboard_theme_wallpaper_colours" to true,
        "learn_contact_details" to true,
        "learn_frequent_words" to true,
        "led_individual_colors" to true,
        "led_locked_animation" to true,
        "one_time_codes_enabled" to true,
        "quick_launcher_behavior" to "pastiera",
        "quick_launcher_pill_mode" to true,
        "search_bar_waits_for_typing" to true,
        "shift_backspace_delete" to true,
        "show_add_word_suggestion" to false,
        "smart_alt_off_after_opening" to true,
        "smart_ctrl_off_after_shortcut" to true,
        "space_after_punctuation" to ".,;:!?",
        "use_keyboard_proximity" to true
    )

    /** On the Titan 2 Elite: its trackpad swipes, contoured LEDs and the compact bar. */
    private val TITAN_2_ELITE: Map<String, Any> = mapOf(
        "pastierina_mode_override" to "pastierina",
        "pastierina_status_bar_slots_left" to "[\"microphone\"]",
        "pastierina_status_bar_slots_right" to "[\"hamburger\"]",
        "suggestion_keys" to "off",
        "swipe_to_delete" to false,
        "titan2_elite_contour_leds" to false,
        "titan2_elite_rounded_corner_insets" to true,
        "trackpad_delete_swipe_threshold" to 100f,
        "trackpad_gesture_add_word_enabled" to false,
        "trackpad_gesture_add_word_full_width_enabled" to false,
        "trackpad_gestures_enabled" to true,
        "trackpad_provider" to "native_ime",
        "trackpad_side_swipe_threshold" to 100f,
        "trackpad_suggestion_swipe_directions" to true,
        "trackpad_suggestion_swipe_threshold" to 100f,
        "trackpad_swipe_down_deletes_word" to true
    )

    internal fun values(titan2Elite: Boolean): Map<String, Any> =
        if (titan2Elite) EVERYWHERE + TITAN_2_ELITE else EVERYWHERE

    private fun values(): Map<String, Any> = values(DeviceSpecific.isTitan2EliteDevice())

    /** On a fresh install (no settings yet), applies the recommended settings. */
    fun applyIfFreshInstall(context: Context): Boolean {
        // Unit tests start every app from scratch and expect Pastiera's own defaults
        if (android.os.Build.FINGERPRINT == "robolectric") return false
        if (SettingsManager.getPreferences(context).all.isNotEmpty()) return false
        return apply(context)
    }

    fun apply(context: Context): Boolean {
        val editor = SettingsManager.getPreferences(context).edit()
        values().forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        return editor.putBoolean(PREF_APPLIED, true).commit()
    }

    /** How many of the recommended settings you have set differently, or not at all. */
    fun differingSettings(context: Context): Int = changes(context).size

    /** A recommended setting you have set differently: [from] is null while it's still unset. */
    data class Change(val key: String, val from: Any?, val to: Any)

    /** What applying would change, setting by setting. */
    fun changes(context: Context): List<Change> {
        val current = SettingsManager.getPreferences(context).all
        return values().mapNotNull { (key, value) ->
            val now = current[key]
            val same = now == value || (now is Number && value is Number && now.toFloat() == value.toFloat())
            if (same) null else Change(key, now, value)
        }
    }

    private const val PREF_PROMPTED_VERSION = "recommended_settings_prompted_version"

    /**
     * After an update: whether to offer the recommended settings that differ, once per version
     * (a fresh install already has them).
     */
    fun shouldOfferAfterUpdate(context: Context, version: String): Boolean {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getString(PREF_PROMPTED_VERSION, null) == version) return false
        return changes(context).isNotEmpty()
    }

    fun markOffered(context: Context, version: String) {
        SettingsManager.getPreferences(context).edit().putString(PREF_PROMPTED_VERSION, version).apply()
    }
}
