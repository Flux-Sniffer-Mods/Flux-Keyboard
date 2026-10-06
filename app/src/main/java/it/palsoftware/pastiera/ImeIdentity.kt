package it.palsoftware.pastiera

import android.view.inputmethod.InputMethodInfo
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService

object ImeIdentity {
    val packageName: String = BuildConfig.APPLICATION_ID
    val serviceClassName: String = PhysicalKeyboardInputMethodService::class.java.name
    val imeId: String = imeIdForPackage(packageName)

    fun imeIdForPackage(appPackageName: String): String {
        return "$appPackageName/$serviceClassName"
    }

    private fun shortImeIdForPackage(appPackageName: String): String {
        return "$appPackageName/.inputmethod.PhysicalKeyboardInputMethodService"
    }

    /** This keyboard in [inputMethods] (one of Android's input method lists), or null. */
    fun findIn(inputMethods: List<InputMethodInfo>, appPackageName: String = packageName): InputMethodInfo? =
        inputMethods.firstOrNull { it.packageName == appPackageName && it.serviceName == serviceClassName }

    fun matchesImeId(value: String?, appPackageName: String = packageName): Boolean {
        if (value.isNullOrBlank()) return false
        return value == imeIdForPackage(appPackageName) || value == shortImeIdForPackage(appPackageName)
    }
}
