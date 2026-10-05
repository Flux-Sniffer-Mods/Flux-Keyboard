package it.palsoftware.pastiera.data.emoji

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.emoji2.text.EmojiCompat

/**
 * Lets the emoji picker offer emoji the phone's own font cannot draw, the same way Gboard does:
 * through EmojiCompat and the emoji font Google Play services downloads. No root and no system
 * font change are involved.
 *
 * Extra emoji are only offered in text fields that declare they render through EmojiCompat
 * (AppCompat/Material text fields do this automatically), so nothing is inserted that the app
 * would show as an empty box. Without Play services, or in other fields, the picker keeps
 * showing exactly what the system font supports.
 */
object EmojiCompatSupport {

    /** Bumped whenever the set of extra emoji the current field can show may have changed. */
    @Volatile
    var generation: Int = 0
        private set

    @Volatile
    private var editorMetadataVersion: Int? = null

    @Volatile
    private var initCallbackRegistered = false

    /** Starts loading the downloadable emoji font. Safe to call repeatedly. */
    fun ensureLoaded(context: Context) {
        // Offline mode: no downloadable font (the system font's emoji only)
        if (it.palsoftware.pastiera.OfflineMode.enabled) return
        runCatching {
            val compat = EmojiCompat.init(context.applicationContext) ?: return
            if (!initCallbackRegistered) {
                initCallbackRegistered = true
                compat.registerInitCallback(object : EmojiCompat.InitCallback() {
                    override fun onInitialized() {
                        generation++
                    }
                })
            }
            when (compat.loadState) {
                // The androidx.startup initializer defers loading until an Activity resumes,
                // which never happens in a keyboard-only process, so load explicitly.
                EmojiCompat.LOAD_STATE_DEFAULT,
                EmojiCompat.LOAD_STATE_FAILED -> compat.load()
                else -> Unit
            }
        }
    }

    /** Call from the IME's onStartInput with the new field's EditorInfo. */
    fun onStartInput(info: EditorInfo?) {
        val version = editorMetadataVersion(info)
        if (version != editorMetadataVersion) {
            editorMetadataVersion = version
            generation++
        }
    }

    /**
     * Predicate for emoji the system font can't draw but the current field can, or null when the
     * field doesn't render through EmojiCompat or the downloaded font isn't ready yet.
     */
    fun extraAvailabilityForCurrentEditor(): ((String) -> Boolean)? {
        val version = editorMetadataVersion ?: return null
        val compat = readyInstance() ?: return null
        return { emoji ->
            runCatching {
                compat.getEmojiMatch(emoji, version) == EmojiCompat.EMOJI_SUPPORTED
            }.getOrDefault(false)
        }
    }

    /** Text for a picker cell: drawn from the downloaded font where the system font can't. */
    fun forDisplay(emoji: String): CharSequence {
        val compat = readyInstance() ?: return emoji
        return runCatching { compat.process(emoji) }.getOrNull() ?: emoji
    }

    /** EmojiCompat metadata version the field renders with, or null if it doesn't use EmojiCompat. */
    internal fun editorMetadataVersion(info: EditorInfo?): Int? {
        val extras = info?.extras ?: return null
        if (!extras.containsKey(EmojiCompat.EDITOR_INFO_METAVERSION_KEY)) return null
        return extras.getInt(EmojiCompat.EDITOR_INFO_METAVERSION_KEY)
    }

    private fun readyInstance(): EmojiCompat? = runCatching {
        EmojiCompat.get().takeIf { it.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED }
    }.getOrNull()
}
