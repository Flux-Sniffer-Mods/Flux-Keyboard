package it.palsoftware.pastiera.clipboard

/** The paste suggestion chip's label: the copied text on one short line. */
object PasteSuggestion {
    private const val MAX_LABEL_LENGTH = 24

    /**
     * The chip's label in a password field: masked, and always the same length so it doesn't
     * give away how long the copied text is.
     */
    const val MASKED_LABEL = "\u2398 \u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"

    fun label(text: String): String {
        val oneLine = text.trim().replace(Regex("\\s+"), " ")
        val shown = if (oneLine.length > MAX_LABEL_LENGTH) oneLine.substring(0, cutBefore(oneLine, MAX_LABEL_LENGTH - 1)).trimEnd() + "…" else oneLine
        // ⎘ (copy/paste): a text symbol, not an emoji, like every indicator
        return "\u2398 $shown"
    }

    /** Where to cut [text] at or before [index] without splitting a character (an emoji, an accent). */
    private fun cutBefore(text: String, index: Int): Int {
        val characters = java.text.BreakIterator.getCharacterInstance().apply { setText(text) }
        return if (characters.isBoundary(index)) index else characters.preceding(index).coerceAtLeast(0)
    }
}
