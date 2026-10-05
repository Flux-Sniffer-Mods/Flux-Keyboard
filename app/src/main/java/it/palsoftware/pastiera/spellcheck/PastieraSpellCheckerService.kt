package it.palsoftware.pastiera.spellcheck

import android.service.textservice.SpellCheckerService
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import it.palsoftware.pastiera.core.suggestions.AndroidDictionaryRepository
import it.palsoftware.pastiera.core.suggestions.CasingHelper
import it.palsoftware.pastiera.core.suggestions.DictionaryRepository
import it.palsoftware.pastiera.core.suggestions.SuggestionController
import it.palsoftware.pastiera.core.suggestions.SuggestionEngine
import it.palsoftware.pastiera.core.suggestions.UserDictionaryStore
import java.lang.ref.WeakReference
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * Pastiera as Android's spell checker (Settings > System > Languages > Spell checker): apps
 * underline words that aren't in Pastiera's dictionary for the language, words you added
 * included, and offer its suggestions. It uses the keyboard's loaded dictionary when the
 * languages match, so the dictionary isn't loaded twice.
 */
class PastieraSpellCheckerService : SpellCheckerService() {

    override fun createSession(): Session = PastieraSpellSession()

    private inner class PastieraSpellSession : Session() {
        private lateinit var language: String

        override fun onCreate() {
            language = localeOf(locale).language.ifEmpty { Locale.getDefault().language }
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo?.text.orEmpty()
            val cookie = textInfo?.cookie ?: 0
            val sequence = textInfo?.sequence ?: 0
            if (!SpellCheckRules.shouldCheck(word)) return inDictionary(cookie, sequence)
            val result = try {
                check(word, suggestionsLimit.coerceIn(1, 5))
            } catch (error: Exception) {
                Log.w(TAG, "Spell check failed", error)
                null
            } ?: return inDictionary(cookie, sequence)
            if (result.known) return inDictionary(cookie, sequence)
            val suggestions = result.suggestions
                .map { CasingHelper.applyCasing(it, word, false) }
                .filter { !it.equals(word, ignoreCase = false) }
                .distinct()
                .toTypedArray()
            return SuggestionsInfo(
                SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or
                    (if (suggestions.isNotEmpty()) SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS else 0),
                suggestions, cookie, sequence
            )
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int,
            sequentialWords: Boolean
        ): Array<SuggestionsInfo> = textInfos.orEmpty().map { onGetSuggestions(it, suggestionsLimit) }.toTypedArray()

        /**
         * Whole sentences, split into words here so a word keeps its apostrophes: Android's own
         * splitting checked "couldn" out of "couldn't".
         */
        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int
        ): Array<SentenceSuggestionsInfo> = textInfos.orEmpty().map { info ->
            val text = info.text.orEmpty()
            val words = SpellCheckRules.words(text)
            val results = words.map { range ->
                onGetSuggestions(
                    TextInfo(text.substring(range.first, range.last + 1), info.cookie, info.sequence),
                    suggestionsLimit
                )
            }
            SentenceSuggestionsInfo(
                results.toTypedArray(),
                words.map { it.first }.toIntArray(),
                words.map { it.last - it.first + 1 }.toIntArray()
            )
        }.toTypedArray()

        private fun check(word: String, limit: Int): SuggestionController.SpellCheckResult? {
            val plain = word.replace('’', '\'')
            val result = checkAsTyped(plain, limit) ?: return null
            if (result.known || '\'' !in plain) return result
            // A contraction or possessive of a known word (couldn't, it's, Sam's), or a word the
            // dictionary spells without its apostrophe
            val known = SpellCheckRules.apostropheForms(plain)
                .any { checkAsTyped(it, 1)?.known == true }
            return if (known) SuggestionController.SpellCheckResult(true, emptyList()) else result
        }

        private fun checkAsTyped(word: String, limit: Int): SuggestionController.SpellCheckResult? {
            keyboardController?.get()?.spellCheck(language, word, limit)?.let { return it }
            val (repository, engine) = ownDictionary(language) ?: return null
            if (repository.isKnownWord(word)) return SuggestionController.SpellCheckResult(true, emptyList())
            return SuggestionController.SpellCheckResult(false, engine.suggest(word, limit).map { it.candidate })
        }

        private fun inDictionary(cookie: Int, sequence: Int) =
            SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, emptyArray(), cookie, sequence)
    }

    /** A dictionary of its own for a language the keyboard isn't typing in; null if Pastiera has none. */
    private fun ownDictionary(language: String): Pair<DictionaryRepository, SuggestionEngine>? = synchronized(dictionaries) {
        dictionaries[language]?.let { return it }
        if (language !in SpellCheckRules.LANGUAGES) return null
        val locale = Locale(language)
        val repository = AndroidDictionaryRepository(applicationContext, assets, UserDictionaryStore(), baseLocale = locale)
        runBlocking { repository.loadIfNeeded() }
        if (!repository.isReady) return null
        val entry = repository to SuggestionEngine(repository, locale = locale)
        dictionaries.clear() // one extra language at a time
        dictionaries[language] = entry
        return entry
    }

    private val dictionaries = HashMap<String, Pair<DictionaryRepository, SuggestionEngine>>()

    companion object {
        private const val TAG = "PastieraSpellChecker"

        /** The keyboard's suggestions, while it's running (same process). */
        @Volatile
        var keyboardController: WeakReference<SuggestionController>? = null

        private fun localeOf(tag: String?): Locale =
            if (tag.isNullOrEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag.replace('_', '-'))
    }
}

/** What the spell checker looks at, apart from the dictionary. */
object SpellCheckRules {
    /** Android's spell checker picker, or its keyboard settings where that screen isn't reachable. */
    fun openSystemSettings(context: android.content.Context) {
        val spellCheckers = android.content.Intent().setClassName(
            "com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity"
        )
        val fallback = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        for (intent in listOf(spellCheckers, fallback)) {
            val opened = runCatching {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
    }

    private val WORD = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’][\\p{L}\\p{M}\\p{N}]+)*")

    /** Where the words are in [text]: letters and digits, with apostrophes inside a word kept. */
    fun words(text: String): List<IntRange> = WORD.findAll(text).map { it.range }.toList()

    private val CONTRACTION_ENDINGS = listOf("n't", "'s", "'re", "'ve", "'ll", "'d", "'m")

    /** The word a contraction or possessive is made from (couldn't: could, it's: it), or null. */
    fun contractionBase(word: String): String? {
        val plain = word.replace('’', '\'')
        val ending = CONTRACTION_ENDINGS.firstOrNull { plain.endsWith(it, ignoreCase = true) } ?: return null
        return plain.dropLast(ending.length).takeIf { base -> base.isNotEmpty() && base.all { it.isLetter() } }
    }

    /** Contractions whose first part isn't a word of its own (won't: "wo"), and what they're made from */
    private val IRREGULAR_CONTRACTIONS = mapOf(
        "won't" to "will", "can't" to "can", "shan't" to "shall", "ain't" to "is",
        "y'all" to "you", "o'clock" to "clock", "ma'am" to "madam", "'tis" to "it", "'twas" to "it"
    )

    /** Short elided words before an apostrophe: l'homme, d'accord, qu'il, dell'anno, un'altra */
    private const val MAX_ELISION_LENGTH = 6

    /**
     * Known words a word with apostrophes may be checked as: what a contraction or possessive is
     * made from (couldn't: could, won't: will), the word after an elision (l'homme: homme), or
     * the word without its apostrophes. Any one being known makes the word known.
     */
    fun apostropheForms(word: String): List<String> {
        val plain = word.replace('’', '\'')
        val forms = mutableListOf<String>()
        IRREGULAR_CONTRACTIONS[plain.lowercase(Locale.ROOT)]?.let(forms::add)
        contractionBase(plain)?.let(forms::add)
        val apostrophe = plain.lastIndexOf('\'')
        // Not for English endings ('re, 've…): "thye're" isn't known because "re" is
        if (forms.isEmpty() && apostrophe in 1..MAX_ELISION_LENGTH) {
            val prefix = plain.substring(0, apostrophe)
            val rest = plain.substring(apostrophe + 1)
            if (prefix.all { it.isLetter() } && rest.length >= 2) forms += rest
        }
        forms += plain.replace("'", "")
        return forms.distinct()
    }

    /** Languages with a bundled dictionary (assets/common/dictionaries_serialized). */
    val LANGUAGES = setOf("da", "de", "en", "es", "fr", "it", "nl", "no", "pl", "pt", "ru", "uk")

    /**
     * Words worth checking: not numbers, links, addresses, handles, hashtags, acronyms or
     * single letters.
     */
    fun shouldCheck(word: String): Boolean {
        val text = word.trim()
        if (text.length < 2) return false
        if (text.any { it.isDigit() }) return false
        if (text.any { it == '@' || it == '#' || it == '/' || it == ':' || it == '_' }) return false
        if (text.contains('.') && !text.endsWith('.')) return false
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return false
        return true
    }
}
