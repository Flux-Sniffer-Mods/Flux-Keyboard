package it.palsoftware.pastiera.data.emoji

import android.content.Context
import it.palsoftware.pastiera.SettingsManager

/**
 * Recent emoji first on the emoji layer (option): the emoji you used last take the layer's first
 * keys, and its own emoji move along by as many.
 *
 * "Used" counts every emoji you type, from the picker or from the layer itself, kept in a list of
 * its own (the picker's Recents only show what the picker typed), so an emoji already on the layer
 * that you use a lot moves to the front instead of showing twice.
 */
object EmojiLayerRecents {
    private const val KEY_USED = "emoji_layer_used"
    private const val MAX_USED = 40

    fun enabled(context: Context): Boolean = SettingsManager.getPreferences(context).getBoolean("emoji_layer_recents_first", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        SettingsManager.getPreferences(context).edit().putBoolean("emoji_layer_recents_first", enabled).apply()
    }

    /** How many keys the recent emoji take (1 to 10, 5 by default). */
    fun count(context: Context): Int = SettingsManager.getPreferences(context).getInt("emoji_layer_recents_count", 5).coerceIn(1, 10)

    fun setCount(context: Context, count: Int) {
        SettingsManager.getPreferences(context).edit().putInt("emoji_layer_recents_count", count.coerceIn(1, 10)).apply()
    }

    /** Every emoji used, newest first. */
    fun used(context: Context): List<String> =
        SettingsManager.getPreferences(context).getString(KEY_USED, null)
            ?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()

    /** An emoji was typed (not in Incognito typing). */
    fun markUsed(context: Context, emoji: String) {
        if (emoji.isBlank() || it.palsoftware.pastiera.core.IncognitoTyping.active) return
        val updated = (listOf(emoji) + used(context).filter { it != emoji }).take(MAX_USED)
        SettingsManager.getPreferences(context).edit().putString(KEY_USED, updated.joinToString("\n")).apply()
    }

    /**
     * The layer's keys with the recent emoji first: [keys] in order (the reserved ones left out
     * by the caller) get the [recent] ones, then the layer's own [mapped] emoji in their key
     * order, without any already shown. Keys left over keep nothing.
     */
    fun arrange(keys: List<Int>, mapped: Map<Int, String>, recent: List<String>, count: Int): Map<Int, String> {
        val front = recent.distinct().take(count)
        val rest = keys.mapNotNull { mapped[it] }.filter { it !in front }
        return keys.zip(front + rest).toMap()
    }
}
