package it.palsoftware.pastiera.dictionaries

import android.content.Context
import android.content.Intent
import android.util.Log
import it.palsoftware.pastiera.AppBroadcastActions
import it.palsoftware.pastiera.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Dictionaries that come as downloads: only English is built in, and the keyboard fetches any
 * other language's dictionary the first time it needs one (a language added, or one typed in
 * before this version), then loads it as soon as it arrives.
 */
object DictionaryDownloads {
    private const val TAG = "DictionaryDownloads"

    /**
     * Languages offered as if built in (input styles, Android's language list): the ones earlier
     * versions carried in the app, whose dictionaries now download on first use.
     */
    val OFFERED_LANGUAGES = setOf("da", "de", "en", "es", "fr", "it", "nl", "no", "pl", "pt", "ru", "uk")

    /** Extra in the broadcast sent once a dictionary is in place: its language code. */
    const val EXTRA_LANGUAGE = "language"

    /** Not asked again sooner than this after a failed try (offline, no such dictionary). */
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastTry = ConcurrentHashMap<String, Long>()

    /** Downloads [language]'s dictionary in the background, unless one is on its way or just failed. */
    fun requestMissing(context: Context, language: String) {
        val lang = language.lowercase()
        if (OfflineMode.enabled) return
        val now = System.currentTimeMillis()
        val previous = lastTry[lang]
        if (previous != null && now - previous < RETRY_AFTER_MS) return
        lastTry[lang] = now
        val app = context.applicationContext
        scope.launch {
            val item = DictionaryRepositoryManager.fetchManifest().getOrNull()
                ?.items?.firstOrNull { it.id == "${lang}_base" }
            if (item == null) {
                Log.i(TAG, "No dictionary to download for $lang")
                return@launch
            }
            when (val result = DictionaryRepositoryManager.downloadDictionary(app, item)) {
                is DownloadResult.Success -> {
                    Log.i(TAG, "Downloaded the $lang dictionary")
                    lastTry.remove(lang)
                    app.sendBroadcast(
                        Intent(AppBroadcastActions.DICTIONARY_INSTALLED)
                            .setPackage(app.packageName)
                            .putExtra(EXTRA_LANGUAGE, lang)
                    )
                }
                else -> Log.w(TAG, "Couldn't download the $lang dictionary: $result")
            }
        }
    }
}
