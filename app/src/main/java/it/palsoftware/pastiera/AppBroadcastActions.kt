package it.palsoftware.pastiera

/**
 * App-internal broadcast actions shared across UI, IME and restore flows.
 * Keep action strings stable for backwards compatibility with existing receivers/senders.
 */
object AppBroadcastActions {
    const val USER_DICTIONARY_UPDATED = "it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED"
    /** A language's dictionary was downloaded (DictionaryDownloads.EXTRA_LANGUAGE says which). */
    const val DICTIONARY_INSTALLED = "it.palsoftware.pastiera.ACTION_DICTIONARY_INSTALLED"
}
