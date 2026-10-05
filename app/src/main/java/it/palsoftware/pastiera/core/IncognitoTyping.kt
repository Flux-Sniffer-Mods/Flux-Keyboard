package it.palsoftware.pastiera.core

/**
 * Whether the field being typed in is incognito (Settings > Privacy: always, or when the app asks
 * keyboards not to learn). While it is, nothing is learned from it: recent emoji, symbols and
 * GIFs, how often words are used, new words, next-word predictions, emails and phone numbers.
 * What was learned before is still offered. Set as each field starts, cleared when it ends.
 */
object IncognitoTyping {
    @Volatile
    var active: Boolean = false
}
