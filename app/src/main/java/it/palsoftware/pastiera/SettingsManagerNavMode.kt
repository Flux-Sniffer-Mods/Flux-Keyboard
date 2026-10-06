package it.palsoftware.pastiera

import it.palsoftware.pastiera.core.writeTextAtomically
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.legacy.LegacyMigrations
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// SettingsManager: nav mode and its key mappings. The keys and defaults live in SettingsManager.kt.

/**
 * Returns whether nav mode is enabled.
 */
fun SettingsManager.getNavModeEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_NAV_MODE_ENABLED, DEFAULT_NAV_MODE_ENABLED)
}

/**
 * Sets whether nav mode is enabled.
 */
fun SettingsManager.setNavModeEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_NAV_MODE_ENABLED, enabled)
        .apply()
}

/**
 * Returns whether holding Ctrl should use Nav Mode mappings in text fields.
 */
fun SettingsManager.getNavModeCtrlHoldEnabled(context: Context): Boolean {
    return getPreferences(context).getBoolean(KEY_NAV_MODE_CTRL_HOLD_ENABLED, DEFAULT_NAV_MODE_CTRL_HOLD_ENABLED)
}

/**
 * Sets whether holding Ctrl should use Nav Mode mappings in text fields.
 */
fun SettingsManager.setNavModeCtrlHoldEnabled(context: Context, enabled: Boolean) {
    getPreferences(context).edit()
        .putBoolean(KEY_NAV_MODE_CTRL_HOLD_ENABLED, enabled)
        .apply()
}

/**
 * Returns the File for nav mode mappings in filesDir.
 */
fun SettingsManager.getNavModeMappingsFile(context: Context): File {
    return File(context.filesDir, NAV_MODE_MAPPINGS_FILE_NAME)
}

/**
 * Initializes the nav mode mappings file by copying from assets if it doesn't exist.
 */
fun SettingsManager.initializeNavModeMappingsFile(context: Context) {
    val mappingsFile = getNavModeMappingsFile(context)
    if (mappingsFile.exists()) {
        LegacyMigrations.navModeDefaultMappings(context)
        return // File already exists, don't overwrite
    }
    
    try {
        context.assets.open("common/ctrl/$NAV_MODE_MAPPINGS_FILE_NAME").use { input ->
            FileOutputStream(mappingsFile).use { output -> input.copyTo(output) }
        }
        LegacyMigrations.navModeDefaultMappings(context)
        Log.d(TAG, "Nav mode mappings file initialized from assets")
    } catch (e: Exception) {
        Log.e(TAG, "Error initializing nav mode mappings file", e)
    }
}

/**
 * Saves nav mode key mappings to the JSON file in filesDir.
 */
fun SettingsManager.saveNavModeKeyMappings(context: Context, mappings: Map<Int, it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping>) {
    try {
        val keyCodeToName = mapOf(
            KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
            KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
            KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
            KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
            KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
            KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
            KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
            KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
            KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
            KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
            KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
            KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
            KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
        )
        
        val mappingsObject = JSONObject()
        for ((keyCode, mapping) in mappings) {
            val keyName = keyCodeToName[keyCode]
            if (keyName != null) {
                val mappingObject = JSONObject()
                mappingObject.put("type", mapping.type)
                when (mapping.type) {
                    "action" -> mappingObject.put("action", mapping.value)
                    "keycode" -> mappingObject.put("keycode", mapping.value)
                    "command" -> mappingObject.put("command", mapping.value)
                    "native_ctrl" -> { /* type is already set */ }
                    "none" -> { /* type is already set */ }
                }
                mappingsObject.put(keyName, mappingObject)
            }
        }
        
        // Also include all alphabetic keys that might not be in the mappings map
        // but should be saved as "none" if they're not explicitly set
        val allAlphabeticKeys = listOf(
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
        )
        
        // Ensure all alphabetic keys are in the JSON (even if "none")
        allAlphabeticKeys.forEach { keyCode ->
            val keyName = keyCodeToName[keyCode]
            if (keyName != null && !mappingsObject.has(keyName)) {
                val mappingObject = JSONObject()
                mappingObject.put("type", "none")
                mappingsObject.put(keyName, mappingObject)
            }
        }
        
        val jsonObject = JSONObject()
        jsonObject.put("mappings", mappingsObject)
        
        val mappingsFile = getNavModeMappingsFile(context)
        mappingsFile.writeTextAtomically(jsonObject.toString())
        
        // Update timestamp in SharedPreferences to notify the service
        getPreferences(context).edit()
            .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Nav mode key mappings saved")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving nav mode key mappings", e)
    }
}

/**
 * Resets nav mode key mappings to default by deleting the custom file.
 */
fun SettingsManager.resetNavModeKeyMappings(context: Context) {
    try {
        val mappingsFile = getNavModeMappingsFile(context)
        if (mappingsFile.exists()) {
            mappingsFile.delete()
            Log.d(TAG, "Nav mode key mappings reset to default")
        }
        // Re-initialize from assets
        initializeNavModeMappingsFile(context)
        
        // Update timestamp in SharedPreferences to notify the service
        getPreferences(context).edit()
            .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
            .apply()
    } catch (e: Exception) {
        Log.e(TAG, "Error resetting nav mode key mappings", e)
    }
}
