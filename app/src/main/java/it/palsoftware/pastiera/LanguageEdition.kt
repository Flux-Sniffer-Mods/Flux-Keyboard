package it.palsoftware.pastiera

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * Full releases come in one APK per dictionary language (flux-keyboard-<version>-<language>.apk;
 * the plain APK is English): each carries only its language's dictionary. That language is the
 * edition's: a fresh install opens in its translation, where there is one, and updates fetch the
 * same edition's APK.
 */
object LanguageEdition {
    private const val DICTIONARIES = "common/dictionaries_serialized"

    /** Languages the app itself is translated into (values-<language>), besides English. */
    private val TRANSLATED = setOf("de", "el", "es", "fr", "hy", "it", "pl", "ru", "uk", "vi")

    @Volatile private var cached: String? = null

    /** The edition's language: the one dictionary built in ("en" for the plain APK). */
    fun language(context: Context): String = cached ?: run {
        val bundled = runCatching { context.assets.list(DICTIONARIES) }.getOrNull().orEmpty()
            .filter { it.endsWith("_base.dict") }
            .map { it.removeSuffix("_base.dict") }
        (bundled.singleOrNull() ?: "en").also { cached = it }
    }

    /** The edition's APK name suffix: "-de" for German, nothing for English. */
    fun apkSuffix(context: Context): String = language(context).let { if (it == "en") "" else "-$it" }

    /**
     * A fresh install of a language edition opens in that language's translation (Android 13 and
     * later, as Android's per-app language; it can be changed there), unless one is already set.
     */
    fun applyOnFreshInstall(context: Context) {
        val language = language(context)
        if (language == "en" || language !in TRANSLATED) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val locales = context.getSystemService(LocaleManager::class.java) ?: return
            if (!locales.applicationLocales.isEmpty) return
            locales.applicationLocales = LocaleList.forLanguageTags(language)
        }
    }
}
