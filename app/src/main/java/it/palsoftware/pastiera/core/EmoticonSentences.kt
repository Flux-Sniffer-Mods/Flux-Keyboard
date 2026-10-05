package it.palsoftware.pastiera.core

/**
 * "Capital after an emoticon": a text emoticon such as :) ;-) :D <3 or ^_^ ends a sentence as a
 * full stop does, so the next word starts with a capital (and double space adds no full stop
 * after it). Set from its setting as each field starts.
 */
object EmoticonSentences {
    @Volatile
    var enabled: Boolean = true

    // Eyes, an optional nose or tear, a mouth; or one of the common others. A whole word only
    // (after a space or at the start), so "(see above)" or a link doesn't count
    private val EMOTICON = Regex(
        """(?:^|\s)(?:[:;=][-'^o]?[)(\]\[DPpOo3/\\|*$@SsbcX>]|<3|\^\^|\^_\^|[xX]D|T_T|-_-|[oO]_[oO])$"""
    )

    /** Whether [text] (without the spaces after it) ends with a text emoticon. */
    fun endsWithEmoticon(text: CharSequence): Boolean = enabled && EMOTICON.containsMatchIn(text)
}
