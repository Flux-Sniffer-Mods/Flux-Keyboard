package it.palsoftware.pastiera.legacy

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.theme.KeyboardThemePreset
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SettingsManager.KeyboardThemeTarget
import it.palsoftware.pastiera.core.writeTextAtomically
import org.json.JSONObject
import java.io.File
import it.palsoftware.pastiera.getNavModeMappingsFile
import it.palsoftware.pastiera.keyboardThemeAssignmentModeKeyForTarget
import it.palsoftware.pastiera.keyboardThemeDarkKeyForTarget
import it.palsoftware.pastiera.keyboardThemeKeyForTarget
import it.palsoftware.pastiera.keyboardThemeLightKeyForTarget

/**
 * One-time conversions of data saved by earlier versions. Each runs until it has finished once
 * and then does nothing.
 */
internal object LegacyMigrations {
    private const val TAG = "LegacyMigrations"

    private const val KEY_REMOVED_THEMES_MIGRATED = "removed_builtin_themes_migrated"
    private const val KEY_GERMAN_QWERTZ_MIGRATED = "legacy_german_qwertz_default_migrated"
    private const val KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION = "nav_mode_default_mappings_version"
    private const val CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION = 3

    /**
     * A keyboard still coloured like a built-in theme that's gone (Flux Dark, Nord…) moves to the
     * system-matched classic theme. Themes are saved as colours, so they're recognised by their
     * colours; your own and your saved themes stay.
     */
    fun removedBuiltInThemes(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getBoolean(KEY_REMOVED_THEMES_MIGRATED, false)) return
        val removed = removedBuiltInThemePresets()
        fun wasBuiltIn(key: String): Boolean {
            val stored = prefs.getString(key, null) ?: return false
            val json = runCatching { JSONObject(stored) }.getOrNull() ?: return false
            return removed.any { preset ->
                json.optInt("background") == preset.background &&
                    json.optInt("normal_key") == preset.normalKey &&
                    json.optInt("text_and_icons") == preset.textAndIcons &&
                    json.optInt("accent") == preset.accent
            }
        }
        val editor = prefs.edit()
        KeyboardThemeTarget.values().forEach { target ->
            if (wasBuiltIn(SettingsManager.keyboardThemeKeyForTarget(target))) {
                editor.remove(SettingsManager.keyboardThemeKeyForTarget(target))
                editor.putString(
                    SettingsManager.keyboardThemeAssignmentModeKeyForTarget(target),
                    SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
                )
            }
            listOf(
                SettingsManager.keyboardThemeDarkKeyForTarget(target),
                SettingsManager.keyboardThemeLightKeyForTarget(target)
            ).forEach { key ->
                if (wasBuiltIn(key)) editor.remove(key)
            }
        }
        editor.putBoolean(KEY_REMOVED_THEMES_MIGRATED, true).apply()
    }

    /**
     * Nav mode mappings saved before word movement, word selection and the software keyboard
     * toggle were defaults get them on the keys that are still free.
     */
    fun navModeDefaultMappings(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getInt(KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION, 1) >= CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION) {
            return
        }

        try {
            val mappingsFile = SettingsManager.getNavModeMappingsFile(context)
            if (!mappingsFile.exists()) {
                return
            }

            val jsonObject = JSONObject(mappingsFile.readText())
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val defaultWordMappings = mapOf(
                "KEYCODE_N" to "move_word_left",
                "KEYCODE_M" to "move_word_right",
                "KEYCODE_U" to "expand_selection_word_left",
                "KEYCODE_I" to "expand_selection_word_right"
            )

            defaultWordMappings.forEach { (keyName, action) ->
                val existing = mappingsObject.optJSONObject(keyName)
                if (existing == null || existing.optString("type") == "none") {
                    mappingsObject.put(
                        keyName,
                        JSONObject().apply {
                            put("type", "action")
                            put("action", action)
                        }
                    )
                }
            }
            val ctrlBExisting = mappingsObject.optJSONObject("KEYCODE_B")
            if (ctrlBExisting == null || ctrlBExisting.optString("type") == "none") {
                mappingsObject.put(
                    "KEYCODE_B",
                    JSONObject().apply {
                        put("type", "command")
                        put("command", "pastiera.toggle_software_keyboard_mode")
                    }
                )
            }

            mappingsFile.writeTextAtomically(jsonObject.toString())
            prefs.edit()
                .putInt(KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION, CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION)
                .putLong(SettingsManager.KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Nav mode mappings migrated to version $CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating nav mode mappings file", e)
        }
    }

    /** German languages mapped to the old multitap QWERTZ layout move to the QWERTZ layout. */
    fun germanSystemLayoutMapping(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.getBoolean(KEY_GERMAN_QWERTZ_MIGRATED, false)) return

        val file = File(context.filesDir, "locale_layout_mapping.json")
        if (!file.exists() || !file.canRead() || !file.canWrite()) {
            prefs.edit().putBoolean(KEY_GERMAN_QWERTZ_MIGRATED, true).apply()
            return
        }

        try {
            val json = JSONObject(file.readText())
            val germanLocales = listOf("de", "de_DE", "de_AT", "de_CH", "de_LU")
            var changed = false
            germanLocales.forEach { locale ->
                if (json.optString(locale) == LegacySettings.GERMAN_MULTITAP_QWERTZ) {
                    json.put(locale, "qwertz")
                    changed = true
                }
            }
            if (changed) {
                file.writeTextAtomically(json.toString(2))
            }
            prefs.edit().putBoolean(KEY_GERMAN_QWERTZ_MIGRATED, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error migrating legacy German layout mapping", e)
        }
    }

    /**
     * Dictionaries imported before the custom folder existed sat next to the bundled ones; they
     * move into the custom folder (a copy already there wins).
     */
    fun localDictionariesToCustomFolder(context: Context) {
        val rootDir = File(context.filesDir, "dictionaries_serialized")
        val customDir = File(rootDir, "custom").apply { mkdirs() }
        if (!rootDir.exists() || rootDir == customDir) return

        rootDir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name == "custom") return@forEach
            if (file.isFile && file.extension == "dict") {
                val dest = File(customDir, file.name)
                if (!dest.exists()) {
                    val moved = file.renameTo(dest)
                    Log.i(TAG, "Migrating legacy dictionary ${file.name} to custom folder: success=$moved")
                    if (!moved) {
                        file.copyTo(dest, overwrite = false)
                        file.delete()
                    }
                } else {
                    Log.i(TAG, "Removing duplicate legacy dictionary ${file.name}")
                    file.delete()
                }
            }
        }
    }
}

/**
 * Themes that used to be built in, so a keyboard still coloured like one can move to the
 * system-matched classic theme (they were saved as colours, not by name).
 */
internal fun removedBuiltInThemePresets(): List<KeyboardThemePreset> = listOf(
    KeyboardThemePreset("Flux Dark", 0xFF000000.toInt(), 0xFF2C3136.toInt(), 0xFF15191D.toInt(), 0xFF2B3138.toInt(), 0xFFEFEFEF.toInt(), 0xFF303030.toInt(), 0xFF6496FF.toInt(), 0xFFF76300.toInt(), 0xFF6496FF.toInt(), keyCornerRadiusRatio = 0.10f, chromeCornerRadiusRatio = 0.10f),
    KeyboardThemePreset("Flux Light", 0xFFF8FAFC.toInt(), 0xFFC7CDD4.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E6EE.toInt(), 0xFF171A1F.toInt(), 0xFFD1D5DB.toInt(), 0xFF276EF1.toInt(), 0xFFD65A00.toInt(), 0xFF276EF1.toInt(), keyCornerRadiusRatio = 0.10f, chromeCornerRadiusRatio = 0.10f),
    KeyboardThemePreset("Cloud Tap", 0xFFE1E3E7.toInt(), 0xFFD4D7DD.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF050505.toInt(), 0xFFC2C6CE.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9500.toInt(), 0xFF0A84FF.toInt(), 0xFF0A84FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF0A84FF.toInt(), 0xFFDDE0E5.toInt(), 0xFFFFFFFF.toInt(), 0.18186983f, 0.35f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, true, false, 0.9f, 0.88f, true),
    KeyboardThemePreset("Moon Tap", 0xFF111111.toInt(), 0xFF303030.toInt(), 0xFF3A3A3C.toInt(), 0xFF3A3A3C.toInt(), 0xFFF8F8F8.toInt(), 0xFF303030.toInt(), 0xFF409CFF.toInt(), 0xFFFF9F0A.toInt(), 0xFF409CFF.toInt(), 0xFF409CFF.toInt(), 0xFF3A3A3C.toInt(), 0xFF409CFF.toInt(), 0xFF171717.toInt(), 0xFF1C1C1E.toInt(), 0.18186983f, 0.35f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, true, false, 0.9f, 0.88f, true),
    KeyboardThemePreset("ePaper", 0xFFF2F2F2.toInt(), 0xFFB8B8B8.toInt(), 0xFFFAFAFA.toInt(), 0xFFDDDDDD.toInt(), 0xFF111111.toInt(), 0xFFB0B0B0.toInt(), 0xFF555555.toInt(), 0xFF111111.toInt(), 0xFF3F8C96.toInt()),
    KeyboardThemePreset("High Contrast", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0D0D0D.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF555555.toInt(), 0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFFFFEA00.toInt()),
    KeyboardThemePreset("Warm", 0xFF241F1A.toInt(), 0xFF6F6255.toInt(), 0xFF352E27.toInt(), 0xFF5B4734.toInt(), 0xFFFFF1DD.toInt(), 0xFF665A4E.toInt(), 0xFFE0B05D.toInt(), 0xFFE06A4B.toInt(), 0xFFE0B05D.toInt()),
    KeyboardThemePreset("Solarized Dark", 0xFF002B36.toInt(), 0xFF586E75.toInt(), 0xFF073642.toInt(), 0xFF16424D.toInt(), 0xFFEEE8D5.toInt(), 0xFF586E75.toInt(), 0xFF2AA198.toInt(), 0xFFB58900.toInt(), 0xFF2AA198.toInt()),
    KeyboardThemePreset("Solarized Light", 0xFFFDF6E3.toInt(), 0xFF93A1A1.toInt(), 0xFFFFFBEC.toInt(), 0xFFEEE8D5.toInt(), 0xFF073642.toInt(), 0xFFB8B7AA.toInt(), 0xFF268BD2.toInt(), 0xFFCB4B16.toInt(), 0xFF268BD2.toInt()),
    KeyboardThemePreset("Monokai", 0xFF272822.toInt(), 0xFF75715E.toInt(), 0xFF3E3D32.toInt(), 0xFF49483E.toInt(), 0xFFF8F8F2.toInt(), 0xFF75715E.toInt(), 0xFFA6E22E.toInt(), 0xFFFFD866.toInt(), 0xFF66D9EF.toInt()),
    KeyboardThemePreset("Dracula", 0xFF282A36.toInt(), 0xFF6272A4.toInt(), 0xFF343746.toInt(), 0xFF44475A.toInt(), 0xFFF8F8F2.toInt(), 0xFF6272A4.toInt(), 0xFFFF79C6.toInt(), 0xFFF1FA8C.toInt(), 0xFFBD93F9.toInt()),
    KeyboardThemePreset("Nord", 0xFF2E3440.toInt(), 0xFF4C566A.toInt(), 0xFF3B4252.toInt(), 0xFF434C5E.toInt(), 0xFFECEFF4.toInt(), 0xFF4C566A.toInt(), 0xFF88C0D0.toInt(), 0xFFEBCB8B.toInt(), 0xFF88C0D0.toInt()),
    KeyboardThemePreset("Volcanic Dusk", 0xFF1B141A.toInt(), 0xFF5D3B4F.toInt(), 0xFF2A2028.toInt(), 0xFF723650.toInt(), 0xFFFFEDF5.toInt(), 0xFF66515F.toInt(), 0xFFFF5D9E.toInt(), 0xFFFFB000.toInt(), 0xFFFF5D9E.toInt())
).flatMap { preset ->
    when (preset.name) {
        "Flux Dark", "Flux Light" -> listOf(
            preset,
            preset.copy(name = preset.name.replace("Flux", "Transparent"), background = preset.background and 0x00FFFFFF)
        )
        else -> listOf(preset)
    }
}
