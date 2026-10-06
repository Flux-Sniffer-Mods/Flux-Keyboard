package it.palsoftware.pastiera.legacy

import android.content.SharedPreferences
import it.palsoftware.pastiera.AltModifierBinding
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.PastieraCommandSource
import it.palsoftware.pastiera.SettingsManager.LauncherShortcut
import org.json.JSONObject

/**
 * Settings saved in the shapes earlier versions used. They're read and converted to today's
 * format; nothing new is written in these shapes.
 */
internal object LegacySettings {
    /** The Alt layer binding, saved here before it became the Alt modifier binding. */
    const val KEY_ALT_CHARACTER_LAYER_BINDING = "alt_character_layer_binding"

    /**
     * Global switches for every hidden app, before each hidden app had its own: modifier LEDs
     * drawn over it, and the emoji and symbols panels.
     */
    const val KEY_HIDDEN_APPS_SHOW_LEDS = "hidden_keyboard_apps_show_leds"
    const val KEY_HIDDEN_APPS_ALLOW_PANELS = "hidden_keyboard_apps_allow_panels"

    /** SYM pages saved before the page order: emoji, symbols and clipboard, or the reverse. */
    const val SYM_PAGES_EMOJI_FIRST_FIELD = "emojiFirst"

    /**
     * Moves an Alt binding saved under its old key to [newKey] and returns it, or returns null
     * when there's none.
     */
    fun moveAltCharacterLayerBinding(prefs: SharedPreferences, newKey: String): AltModifierBinding? {
        val stored = prefs.getString(KEY_ALT_CHARACTER_LAYER_BINDING, null) ?: return null
        val binding = AltModifierBinding.fromPersistedValue(stored)
        prefs.edit()
            .putString(newKey, binding.persistedValue)
            .remove(KEY_ALT_CHARACTER_LAYER_BINDING)
            .apply()
        return binding
    }

    /** The SYM page order for a config saved with only the emoji-first flag. */
    fun symPageOrderFromEmojiFirst(emojiFirst: Boolean): List<String> {
        val cyclePages = mutableListOf(
            SymPagesConfig.PAGE_EMOJI,
            SymPagesConfig.PAGE_SYMBOLS,
            SymPagesConfig.PAGE_CLIPBOARD
        )
        if (!emojiFirst) cyclePages.reverse()
        return cyclePages + SymPagesConfig.PAGE_EMOJI_PICKER
    }

    /**
     * An untouched SYM pages config saved before the device page existed; it gets the device
     * page turned on.
     */
    fun isDefaultFromBeforeDevicePage(config: SymPagesConfig): Boolean =
        !config.deviceEnabled &&
            config.emojiEnabled &&
            config.symbolsEnabled &&
            !config.clipboardEnabled &&
            !config.emojiPickerEnabled &&
            config.normalizedOrder() == SymPagesConfig.DEFAULT_ORDER

    /** What a launcher shortcut saved before command launch specs opens, from its type. */
    fun launcherShortcutLaunchSpec(type: String, shortcut: JSONObject): CommandLaunchSpec? =
        when (type) {
            LauncherShortcut.TYPE_APP -> shortcut.optString("packageName")
                .takeIf { it.isNotBlank() }
                ?.let { CommandLaunchSpec.AppPackage(it) }
            LauncherShortcut.TYPE_QUICK_LAUNCHER ->
                CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
            else -> null
        }

    /** Earlier names of a layout that suggestion languages may still be saved under. */
    fun suggestionLayoutAliases(layout: String): List<String> =
        when (layout.trim()) {
            "qwertz" -> listOf(GERMAN_MULTITAP_QWERTZ)
            else -> emptyList()
        }

    /** The German layout that used to be the default for German. */
    const val GERMAN_MULTITAP_QWERTZ = "german_multitap_qwertz"
}
