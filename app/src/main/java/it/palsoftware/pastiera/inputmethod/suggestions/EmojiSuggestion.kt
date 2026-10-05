package it.palsoftware.pastiera.inputmethod.suggestions

import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker

/**
 * An emoji offered in the suggestion bar for the word you're typing (Settings > Typing >
 * Auto-correction > Emoji suggestions). Picking it keeps the word and adds the emoji after it.
 */
object EmojiSuggestion {

    /** A suggestion that is an emoji rather than a word: no letters or digits, only symbols. */
    fun isEmoji(suggestion: String): Boolean =
        suggestion.isNotEmpty() &&
            suggestion.none { it.isLetterOrDigit() } &&
            suggestion.codePoints().anyMatch { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }

    /** The emoji after the word before the cursor, separated by a space and followed by one. */
    fun commitAfterWord(inputConnection: InputConnection, emoji: String): Boolean {
        val before = inputConnection.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val separator = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
        val committed = inputConnection.commitText("$separator$emoji ", 1)
        if (committed) AutoSpaceTracker.markAutoSpace()
        return committed
    }
}
