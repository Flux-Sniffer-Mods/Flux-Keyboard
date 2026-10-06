package it.palsoftware.pastiera

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// SettingsManager: the variation bar and its presets, and custom variations. The keys and defaults live in SettingsManager.kt.

/**
 * Returns whether the static variation bar mode is enabled.
 * When enabled, the variation row shows a fixed set of utility keys
 * instead of dynamic cursor-based character variations.
 */
fun SettingsManager.isStaticVariationBarModeEnabled(context: Context): Boolean {
    return getStaticVariationBarPreset(context) != STATIC_VARIATION_PRESET_OFF
}

fun SettingsManager.getStaticVariationBarPreset(context: Context): String {
    val prefs = getPreferences(context)
    val stored = prefs.getString(KEY_STATIC_VARIATION_BAR_PRESET, null)
    // Nothing chosen yet (not even the older on/off switch): Dev's choice
    if (stored == null && !prefs.contains(KEY_STATIC_VARIATION_BAR_MODE)) return STATIC_VARIATION_PRESET_DEV_CHOICE
    val fallback = if (prefs.getBoolean(KEY_STATIC_VARIATION_BAR_MODE, DEFAULT_STATIC_VARIATION_BAR_MODE)) {
        if (prefs.getBoolean(
                KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED,
                DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED
            )
        ) {
            STATIC_VARIATION_PRESET_ALTERNATIVE
        } else {
            STATIC_VARIATION_PRESET_SYMBOLS
        }
    } else {
        STATIC_VARIATION_PRESET_OFF
    }
    return when (stored ?: fallback) {
        STATIC_VARIATION_PRESET_OFF,
        STATIC_VARIATION_PRESET_SYMBOLS,
        STATIC_VARIATION_PRESET_NUMBERS,
        STATIC_VARIATION_PRESET_ALTERNATIVE,
        STATIC_VARIATION_PRESET_DEV_CHOICE -> stored ?: fallback
        else -> fallback
    }
}

fun SettingsManager.setStaticVariationBarPreset(context: Context, preset: String) {
    val normalized = when (preset) {
        STATIC_VARIATION_PRESET_OFF,
        STATIC_VARIATION_PRESET_SYMBOLS,
        STATIC_VARIATION_PRESET_NUMBERS,
        STATIC_VARIATION_PRESET_ALTERNATIVE,
        STATIC_VARIATION_PRESET_DEV_CHOICE -> preset
        else -> STATIC_VARIATION_PRESET_OFF
    }
    getPreferences(context).edit()
        .putString(KEY_STATIC_VARIATION_BAR_PRESET, normalized)
        .putBoolean(KEY_STATIC_VARIATION_BAR_MODE, normalized != STATIC_VARIATION_PRESET_OFF)
        .putBoolean(KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED, normalized == STATIC_VARIATION_PRESET_ALTERNATIVE)
        .apply()

    if (normalized != STATIC_VARIATION_PRESET_OFF) {
        saveStaticVariationRows(
            context = context,
            staticVariations = getStaticVariationBasePreset(normalized),
            staticVariationsShift = getStaticVariationShiftPreset(normalized),
            staticVariationsAlt = getStaticVariationAltPreset(normalized)
        )
    }
}

/**
 * Returns the top-row preset for the static variation bar based on toggle state.
 */
fun SettingsManager.getStaticVariationBasePreset(context: Context): List<String> {
    return getStaticVariationBasePreset(getStaticVariationBarPreset(context))
}

private fun SettingsManager.getStaticVariationBasePreset(preset: String): List<String> {
    return when (preset) {
        STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
        STATIC_VARIATION_PRESET_ALTERNATIVE -> STATIC_VARIATION_BASE_PRESET_ALTERNATIVE
        STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
        else -> STATIC_VARIATION_BASE_PRESET_DEFAULT
    }
}

fun SettingsManager.getDefaultStaticVariationShiftPreset(): List<String> = STATIC_VARIATION_SHIFT_PRESET_DEFAULT

fun SettingsManager.getDefaultStaticVariationAltPreset(): List<String> = STATIC_VARIATION_ALT_PRESET_DEFAULT

private fun SettingsManager.getStaticVariationShiftPreset(preset: String): List<String> {
    return when (preset) {
        STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
        STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
        else -> STATIC_VARIATION_SHIFT_PRESET_DEFAULT
    }
}

private fun SettingsManager.getStaticVariationAltPreset(preset: String): List<String> {
    return when (preset) {
        STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
        STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
        else -> STATIC_VARIATION_ALT_PRESET_DEFAULT
    }
}

fun SettingsManager.getStaticVariationNumbersPreset(): List<String> = STATIC_VARIATION_BASE_PRESET_NUMBERS

fun SettingsManager.getDevChoiceStaticVariationBasePreset(): List<String> = STATIC_VARIATION_BASE_PRESET_DEV_CHOICE

/**
 * Returns true if the static variation layer should remain latched after modifier hold.
 */
fun SettingsManager.isStaticVariationBarLayerStickyEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION, true)
}

/**
 * Sets whether static variation layers should stay latched after modifier hold.
 */
fun SettingsManager.setStaticVariationBarLayerStickyEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION, enabled)
        .apply()
}

/**
 * Returns whether Alt/Alt-Lock should be cleared when pressing Space.
 */
fun SettingsManager.getClearAltOnSpace(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_CLEAR_ALT_ON_SPACE, DEFAULT_CLEAR_ALT_ON_SPACE)
}

/**
 * Sets whether Alt/Alt-Lock should be cleared when pressing Space.
 */
fun SettingsManager.setClearAltOnSpace(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_CLEAR_ALT_ON_SPACE, enabled)
        .apply()
}

/**
 * Returns the File for variations.json in filesDir.
 */
fun SettingsManager.getVariationsFile(context: Context): File {
    return File(context.filesDir, VARIATIONS_FILE_NAME)
}

/**
 * Helper to load current JSON from file or assets.
 */
private fun SettingsManager.loadCurrentJson(context: Context): JSONObject? {
    return try {
        val variationsFile = getVariationsFile(context)
        val jsonString = if (variationsFile.exists()) {
            variationsFile.readText()
        } else {
            context.assets.open("common/variations/variations.json").bufferedReader().use { it.readText() }
        }
        JSONObject(jsonString)
    } catch (e: Exception) {
        Log.e(TAG, "Error loading current JSON", e)
        null
    }
}

private fun SettingsManager.saveStaticVariationRows(
    context: Context,
    staticVariations: List<String>,
    staticVariationsShift: List<String>,
    staticVariationsAlt: List<String>
) {
    try {
        val currentJson = loadCurrentJson(context)
        val jsonObject = if (currentJson != null) {
            JSONObject(currentJson.toString())
        } else {
            JSONObject()
        }

        val baseArray = org.json.JSONArray()
        staticVariations.forEach { baseArray.put(it) }
        jsonObject.put("staticVariations", baseArray)

        val shiftArray = org.json.JSONArray()
        staticVariationsShift.forEach { shiftArray.put(it) }
        jsonObject.put("staticVariationsShift", shiftArray)

        val altArray = org.json.JSONArray()
        staticVariationsAlt.forEach { altArray.put(it) }
        jsonObject.put("staticVariationsAlt", altArray)

        FileOutputStream(getVariationsFile(context)).use { outputStream ->
            outputStream.write(jsonObject.toString(2).toByteArray())
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error saving static variation rows", e)
    }
}

/**
 * Saves variations to variations.json file in filesDir.
 */
fun SettingsManager.saveVariations(
    context: Context,
    variations: Map<String, List<String>>,
    staticVariations: List<String>? = null,
    staticVariationsShift: List<String>? = null,
    staticVariationsAlt: List<String>? = null
) {
    try {
        val variationsObject = JSONObject()
        for ((letter, chars) in variations) {
            val variationsArray = org.json.JSONArray()
            for (char in chars) {
                variationsArray.put(char)
            }
            variationsObject.put(letter, variationsArray)
        }
        
        val currentJson = loadCurrentJson(context)
        val jsonObject = if (currentJson != null) {
            JSONObject(currentJson.toString())
        } else {
            JSONObject()
        }
        jsonObject.put("variations", variationsObject)

        // Preserve/update staticVariations
        if (staticVariations != null) {
            val staticArray = org.json.JSONArray()
            staticVariations.forEach { staticArray.put(it) }
            jsonObject.put("staticVariations", staticArray)
        }

        // Preserve/update staticVariationsShift
        if (staticVariationsShift != null) {
            val staticArray = org.json.JSONArray()
            staticVariationsShift.forEach { staticArray.put(it) }
            jsonObject.put("staticVariationsShift", staticArray)
        }

        // Preserve/update staticVariationsAlt
        if (staticVariationsAlt != null) {
            val staticArray = org.json.JSONArray()
            staticVariationsAlt.forEach { staticArray.put(it) }
            jsonObject.put("staticVariationsAlt", staticArray)
        }
        
        FileOutputStream(getVariationsFile(context)).use { outputStream ->
            outputStream.write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
        }
        
        notifyVariationsUpdated(context)
        
        Log.d(TAG, "Variations saved to ${getVariationsFile(context).absolutePath}")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving variations", e)
    }
}

fun SettingsManager.saveStaticVariationBasePreset(context: Context, staticVariations: List<String>) {
    try {
        val jsonObject = loadCurrentJson(context) ?: JSONObject()
        val staticArray = org.json.JSONArray()
        staticVariations.forEach { staticArray.put(it) }
        jsonObject.put("staticVariations", staticArray)

        FileOutputStream(getVariationsFile(context)).use { outputStream ->
            outputStream.write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
        }

        notifyVariationsUpdated(context)
        Log.d(TAG, "Static variation base preset saved to ${getVariationsFile(context).absolutePath}")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving static variation base preset", e)
    }
}

/**
 * Resets variations back to defaults by copying defaultvariations.json from assets.
 */
fun SettingsManager.resetVariationsToDefault(context: Context) {
    try {
        val variationsFile = getVariationsFile(context)
        val inputStream = context.assets.open("common/variations/defaultvariations.json")
        FileOutputStream(variationsFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()
        
        notifyVariationsUpdated(context)
        
        Log.d(TAG, "Variations reset to default from assets")
    } catch (e: Exception) {
        Log.e(TAG, "Error resetting variations to default", e)
    }
}

/**
 * Returns true if custom variations file exists.
 */
fun SettingsManager.hasCustomVariations(context: Context): Boolean {
    return getVariationsFile(context).exists()
}

/**
 * Touch the variations_updated flag so the IME reloads variations/static bar content.
 */
fun SettingsManager.notifyVariationsUpdated(context: Context) {
    getPreferences(context).edit()
        .putLong(KEY_VARIATIONS_UPDATED, System.currentTimeMillis())
        .apply()
}
