package it.palsoftware.pastiera.data.emoji

import android.content.Context
import it.palsoftware.pastiera.SettingsManager

/**
 * The default emoji skin tone (Emoji & GIFs): emoji that come in skin tones show in it in the
 * emoji picker and on the emoji layer's pages. Holding one still offers every tone.
 */
object EmojiSkinTone {
    private const val PREF = "emoji_default_skin_tone"

    /** The Fitzpatrick modifiers, light to dark: tones 1 to 5 (0 is the plain yellow). */
    val MODIFIERS: List<String> = listOf("🏻", "🏼", "🏽", "🏾", "🏿")

    fun get(context: Context): Int = SettingsManager.getPreferences(context).getInt(PREF, 0).coerceIn(0, MODIFIERS.size)

    fun set(context: Context, tone: Int) {
        SettingsManager.getPreferences(context).edit().putInt(PREF, tone.coerceIn(0, MODIFIERS.size)).apply()
    }

    /** [base] in [tone] when one of its [variants] is that tone alone, otherwise [base]. */
    fun apply(base: String, variants: List<String>, tone: Int): String {
        if (tone !in 1..MODIFIERS.size) return base
        val modifier = MODIFIERS[tone - 1]
        val others = MODIFIERS - modifier
        return variants.firstOrNull { variant -> modifier in variant && others.none { it in variant } } ?: base
    }

    /** [entry] as the picker and the layer show it: in the default tone when it has one. */
    fun preferred(context: Context, entry: EmojiRepository.EmojiEntry): String =
        apply(entry.base, entry.variants, get(context))
}
