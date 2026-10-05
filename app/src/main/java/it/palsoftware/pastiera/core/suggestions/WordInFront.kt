package it.palsoftware.pastiera.core.suggestions

/**
 * Typing in front of a word (the cursor just before "thing"): the suggestions are for the new word
 * ("some", then a space before "thing") and for the two joined ("something", when it's a word).
 * Picking the new word keeps the word after the cursor; picking the joined one replaces both.
 */
object WordInFront {
    /** The word the suggestions were made for (only the part before the cursor, in front of a word). */
    @Volatile
    var trackedWord: String = ""

    /**
     * Whether picking [suggestion] leaves [wordAfter] (the word after the cursor) in place: the
     * suggestions were for [wordBefore] alone, and it isn't the joined word.
     */
    fun keepsWordAfter(suggestion: String, wordBefore: String, wordAfter: String): Boolean =
        wordAfter.isNotEmpty() && wordBefore.isNotEmpty() &&
            trackedWord.equals(wordBefore, ignoreCase = true) &&
            !suggestion.endsWith(wordAfter, ignoreCase = true)

    /** The letters right after the cursor that the typed word runs into ("thing" in "|thing"). */
    fun followingWord(textAfterCursor: CharSequence?): String =
        textAfterCursor?.takeWhile { it.isLetter() || it == '\'' || it == '’' }?.toString().orEmpty()
}
