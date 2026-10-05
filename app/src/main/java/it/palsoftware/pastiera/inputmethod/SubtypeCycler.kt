package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.setInputMethodAndSubtypeCompat
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.SettingsManager
import java.util.Locale

/**
 * Utility class for cycling between IME subtypes.
 * Can be used both from keyboard shortcuts (Ctrl+Space) and UI buttons.
 */
object SubtypeCycler {
    private const val TAG = "SubtypeCycler"
    private var unifiedSubtypeToast: Toast? = null
    
    /**
     * Cycles to the next IME subtype.
     * 
     * @param context The context (typically the InputMethodService)
     * @param imeServiceClass The class of the IME service (for identifying the IME)
     * @param assets AssetManager to read layout mappings
     * @param showToast If true, shows a toast with the new subtype name and layout
     * @return true if the subtype was changed, false otherwise
     */
    fun cycleToNextSubtype(
        context: Context,
        imeServiceClass: Class<*>,
        assets: AssetManager,
        showToast: Boolean = true,
        backwards: Boolean = false
    ): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false
            
            val currentSubtype = imm.currentInputMethodSubtype
            
            // Get our IME info by searching for package and service name
            val packageName = context.packageName
            val serviceName = imeServiceClass.name
            
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { info ->
                info.packageName == packageName && 
                info.serviceName == serviceName
            } ?: run {
                Log.w(TAG, "IME not found: package=$packageName, service=$serviceName")
                // Log all available IMEs for debugging
                val allImes = imm.getInputMethodList()
                Log.d(TAG, "Available IMEs:")
                allImes.forEach { ime ->
                    Log.d(TAG, "  - ID: ${ime.id}, Package: ${ime.packageName}, Service: ${ime.serviceName}")
                }
                return false
            }
            
            val imeId = inputMethodInfo.id
            Log.d(TAG, "Found IME: $imeId")
            
            // Get all enabled subtypes
            val enabledSubtypes = getCycleableSubtypes(
                context = context,
                assets = assets,
                subtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
            )
            if (enabledSubtypes.isEmpty()) {
                Log.w(TAG, "No subtypes available for cycling")
                return false
            }
            
            // Find current subtype index
            val currentIndex = enabledSubtypes.indexOfFirst { subtype ->
                subtype.localeString() == currentSubtype?.localeString() && 
                subtype.extraValue == currentSubtype?.extraValue
            }
            
            // Next subtype, or the previous one going backwards; wraps round either way
            val nextIndex = cycleIndex(currentIndex, enabledSubtypes.size, backwards)
            
            val nextSubtype = enabledSubtypes[nextIndex]
            val nextLayout = resolveSubtypeCycleLayout(assets, context, nextSubtype)
            
            // Try to switch using setInputMethodAndSubtype
            // This requires the IME window token, which may not always be available
            val result = trySwitchSubtype(imm, imeId, nextSubtype, context)
            
            if (result) {
                SettingsManager.setKeyboardLayout(context, nextLayout)
                if (showToast) {
                    showUnifiedSubtypeToast(context, nextSubtype, assets)
                }
                Log.d(TAG, "Switched to subtype: ${nextSubtype.localeString()}")
            } else {
                Log.w(TAG, "Could not switch subtype using setInputMethodAndSubtype")
                // Note: switchToNextInputMethod requires an IBinder token and switches between IMEs,
                // not subtypes. For now, we return false if we can't switch using setInputMethodAndSubtype
                return false
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error cycling to next subtype", e)
            false
        }
    }
    
    /** A language as remembered per app: its locale and input style. */
    fun subtypeKey(subtype: InputMethodSubtype): String = subtypeKey(subtype.localeString(), subtype.extraValue)

    internal fun subtypeKey(locale: String, extraValue: String?): String = "$locale|${extraValue.orEmpty()}"

    /**
     * Switches to the enabled language whose [subtypeKey] is [key], if it isn't the current one.
     * Returns whether it switched.
     */
    fun switchToSubtypeKey(context: Context, imeServiceClass: Class<*>, assets: AssetManager, key: String): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
            val current = imm.currentInputMethodSubtype
            if (current != null && subtypeKey(current) == key) return false
            val info = imm.getInputMethodList().firstOrNull {
                it.packageName == context.packageName && it.serviceName == imeServiceClass.name
            } ?: return false
            val target = imm.getEnabledInputMethodSubtypeList(info, true).firstOrNull { subtypeKey(it) == key }
                ?: return false
            val switched = trySwitchSubtype(imm, info.id, target, context)
            if (switched) SettingsManager.setKeyboardLayout(context, resolveSubtypeCycleLayout(assets, context, target))
            switched
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't restore the app's language", e)
            false
        }
    }

    /** Where cycling goes from [current] (-1: unknown) among [count] subtypes, forwards or backwards. */
    internal fun cycleIndex(current: Int, count: Int, backwards: Boolean): Int = when {
        count <= 0 -> 0
        current !in 0 until count -> if (backwards) count - 1 else 0
        backwards -> (current - 1 + count) % count
        else -> (current + 1) % count
    }

    /**
     * Attempts to switch to the specified subtype using setInputMethodAndSubtype.
     * This method requires the IME window token, which may not always be available.
     */
    private fun trySwitchSubtype(
        imm: InputMethodManager,
        imeId: String,
        subtype: InputMethodSubtype,
        context: Context
    ): Boolean {
        return try {
            // Try to get the IME token from the current window
            // This works when the IME is active
            val imeService = context as? android.inputmethodservice.InputMethodService
            val token = imeService?.window?.window?.attributes?.token
            
            if (token != null) {
                setInputMethodAndSubtypeCompat(imm, token, imeId, subtype)
                true
            } else {
                // Token not available
                Log.w(TAG, "IME window token not available")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching subtype", e)
            false
        }
    }
    
    /** Shows layout, primary language, and optional additional suggestion languages. */
    private fun showUnifiedSubtypeToast(context: Context, subtype: InputMethodSubtype, assets: AssetManager) {
        Handler(Looper.getMainLooper()).post {
            try {
                val layoutName = resolveSubtypeCycleLayout(assets, context, subtype)
                val layoutMetadata = try {
                    LayoutFileStore.getLayoutMetadataFromAssets(assets, layoutName)
                        ?: LayoutFileStore.getLayoutMetadata(context, layoutName)
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting layout metadata", e)
                    null
                }

                val layoutDisplayName = layoutMetadata?.name
                    ?.substringBefore(" | ")
                    ?.takeIf { it.isNotBlank() }
                    ?: layoutName
                val primaryLanguage = languageAbbreviation(subtype.localeString())
                val additionalLanguages = SettingsManager
                    .getAdditionalSuggestionLocalesForInputStyle(
                        context,
                        subtype.localeString(),
                        layoutName
                    )
                    .map(::languageAbbreviation)
                    .filterNot { it == primaryLanguage }
                    .distinct()
                val toastText = buildString {
                    append(layoutDisplayName)
                    append(" | ")
                    append(primaryLanguage)
                    if (additionalLanguages.isNotEmpty()) {
                        append(" | ")
                        append(additionalLanguages.joinToString(", "))
                    }
                }
                unifiedSubtypeToast?.cancel()
                unifiedSubtypeToast = Toast.makeText(
                    context.applicationContext,
                    toastText,
                    Toast.LENGTH_SHORT
                )
                unifiedSubtypeToast?.show()
            } catch (e: Exception) {
                // Fallback: use locale if display name fails
                val locale = subtype.localeString().ifBlank { "Unknown" }
                unifiedSubtypeToast?.cancel()
                unifiedSubtypeToast = Toast.makeText(
                    context.applicationContext,
                    locale,
                    Toast.LENGTH_SHORT
                )
                unifiedSubtypeToast?.show()
                Log.e(TAG, "Error showing unified subtype toast, using locale fallback", e)
            }
        }
    }

    private fun languageAbbreviation(locale: String): String = locale
        .trim()
        .replace('_', '-')
        .substringBefore('-')
        .ifBlank { "?" }
        .uppercase(Locale.ROOT)

    fun getCycleableSubtypes(
        context: Context,
        assets: AssetManager,
        subtypes: List<InputMethodSubtype>
    ): List<InputMethodSubtype> {
        val seen = mutableSetOf<String>()
        return subtypes.filter { subtype ->
            val locale = subtype.localeString()
            val layout = resolveSubtypeCycleLayout(assets, context, subtype)
            val hiddenSystemLocale =
                !AdditionalSubtypeUtils.isAdditionalSubtype(subtype) &&
                    SettingsManager.isSystemInputStyleHidden(context, locale, layout)
            !hiddenSystemLocale && seen.add("$locale:$layout")
        }
    }

    fun resolveSubtypeCycleLayout(
        assets: AssetManager,
        context: Context,
        subtype: InputMethodSubtype?
    ): String {
        if (subtype != null) {
            val layoutFromSubtype = AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtype)
            if (!layoutFromSubtype.isNullOrEmpty()) {
                return layoutFromSubtype
            }
            val locale = subtype.localeString().ifBlank { "en_US" }
            return AdditionalSubtypeUtils.getLayoutForLocale(assets, locale, context)
        }

        return SettingsManager.getKeyboardLayout(context)
    }
    
    /**
     * Gets the current subtype display name.
     */
    fun getCurrentSubtypeName(context: Context): String? {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype ?: return null
            
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            currentSubtype.getDisplayName(
                context,
                context.packageName,
                appInfo
            )?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current subtype name", e)
            null
        }
    }
    
    /**
     * Gets all available subtypes for the IME.
     */
    fun getAvailableSubtypes(
        context: Context,
        imeServiceClass: Class<*>
    ): List<InputMethodSubtype> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return emptyList()
            
            val packageName = context.packageName
            val serviceName = imeServiceClass.name
            
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { info ->
                info.packageName == packageName && 
                info.serviceName == serviceName
            } ?: return emptyList()
            
            imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available subtypes", e)
            emptyList()
        }
    }
}
