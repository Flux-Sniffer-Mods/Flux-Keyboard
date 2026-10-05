package it.palsoftware.pastiera.data.emoji

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Shared emoji search index used by both the settings picker dialog and the inline IME picker.
 *
 * Search terms are loaded from local CLDR-derived assets in assets/common/emoji_search/<locale>.tsv.
 */
object EmojiSearchRepository {
    private const val SEARCH_ASSET_DIR = "common/emoji_search"
    private const val EN_LOCALE = "en"

    data class EmojiSearchResult(
        val entry: EmojiRepository.EmojiEntry,
        val categoryId: String,
        val score: Int
    )

    class EmojiSearchIndex internal constructor(
        internal val items: List<IndexedEmoji>
    )

    internal data class IndexedEmoji(
        val entry: EmojiRepository.EmojiEntry,
        val categoryId: String,
        val categoryOrder: Int,
        val terms: List<SearchTerm>
    )

    internal data class SearchTerm(
        val normalizedText: String,
        val kind: TermKind,
        val preferredLocale: Boolean
    )

    internal data class MetadataRecord(
        val name: String?,
        val keywords: List<String>
    )

    internal enum class TermKind {
        NAME,
        KEYWORD
    }

    private data class RankedResult(
        val result: EmojiSearchResult,
        val categoryOrder: Int
    )

    private val cacheMutex = Mutex()
    private val indexCache = LinkedHashMap<String, EmojiSearchIndex>()
    private const val MAX_CACHED_INDEXES = 3

    suspend fun getSearchIndex(context: Context): EmojiSearchIndex {
        val localeChain = getPreferredLocaleChain(context)
        val cacheKey = localeChain.joinToString("|")

        cacheMutex.withLock {
            indexCache[cacheKey]?.let { existing ->
                return existing
            }
        }

        val built = withContext(Dispatchers.IO) {
            buildIndex(context, localeChain)
        }

        cacheMutex.withLock {
            indexCache[cacheKey]?.let { existing ->
                return existing
            }
            indexCache[cacheKey] = built
            while (indexCache.size > MAX_CACHED_INDEXES) {
                val firstKey = indexCache.entries.firstOrNull()?.key ?: break
                indexCache.remove(firstKey)
            }
            return built
        }
    }

    /**
     * Results are limited to emoji the system font can draw, plus any for which
     * [extraAvailable] returns true (e.g. emoji the current field renders via EmojiCompat).
     */
    fun search(
        index: EmojiSearchIndex,
        query: String,
        limit: Int = 200,
        extraAvailable: ((String) -> Boolean)? = null
    ): List<EmojiSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val normalizedQuery = normalizeSearchText(trimmed)
        if (normalizedQuery.isEmpty()) return emptyList()
        val allowContains = normalizedQuery.length >= 2
        val isAllowed: (String) -> Boolean = { emoji ->
            EmojiRepository.isSystemAvailable(emoji) || extraAvailable?.invoke(emoji) == true
        }

        return index.items.mapNotNull { item ->
            val score = scoreItem(item, trimmed, normalizedQuery, allowContains)
            if (score <= 0) return@mapNotNull null
            val entry = EmojiRepository.filterEntry(item.entry, isAllowed) ?: return@mapNotNull null
            RankedResult(
                result = EmojiSearchResult(
                    entry = entry,
                    categoryId = item.categoryId,
                    score = score
                ),
                categoryOrder = item.categoryOrder
            )
        }
            .sortedWith(
                compareByDescending<RankedResult> { it.result.score }
                    .thenBy { it.categoryOrder }
                    .thenBy { it.result.entry.base }
            )
            .map { it.result }
            .take(limit)
    }

    fun clearCache() {
        indexCache.clear()
    }

    /** Index already built for the current locales, without waiting; null until it is. */
    fun cachedSearchIndex(context: Context): EmojiSearchIndex? {
        val cacheKey = getPreferredLocaleChain(context).joinToString("|")
        return indexCache[cacheKey]
    }

    /** Words that name too many emoji, or none in particular, to suggest one for. */
    private val GENERIC_WORDS = setOf(
        "button", "symbol", "sign", "mark", "face", "hand", "up", "down", "left", "right", "arrow",
        "time", "done", "double", "fast", "person", "man", "woman", "people", "light", "flag",
        "small", "large", "big", "black", "white", "open", "closed", "square", "circle", "other",
        "the", "and", "with", "for", "you", "not", "new", "top", "end", "back", "on", "off", "free", "ok"
    )

    /**
     * One emoji for a word you typed, for the suggestion bar: an emoji whose name is the word
     * ("pizza", "rocket"), otherwise the first (in picker order) of the emoji that have it as a
     * keyword ("happy", "love"), unless the word is generic or names too many emoji.
     */
    fun suggestionFor(
        index: EmojiSearchIndex,
        word: String,
        extraAvailable: ((String) -> Boolean)? = null
    ): String? {
        if (word.length < 3) return null
        val query = normalizeSearchText(word)
        if (query.isEmpty() || query in GENERIC_WORDS) return null
        val available: (String) -> Boolean = { emoji ->
            EmojiRepository.isSystemAvailable(emoji) || extraAvailable?.invoke(emoji) == true
        }
        index.items.firstOrNull { item ->
            item.terms.any { it.kind == TermKind.NAME && it.normalizedText == query } && available(item.entry.base)
        }?.let { return it.entry.base }
        val byKeyword = index.items.filter { item ->
            item.terms.any { it.kind == TermKind.KEYWORD && it.normalizedText == query } && available(item.entry.base)
        }
        if (byKeyword.isEmpty() || byKeyword.size > MAX_KEYWORD_EMOJI) return null
        return byKeyword.minByOrNull { it.categoryOrder }?.entry?.base
    }

    private const val MAX_KEYWORD_EMOJI = 30

    private fun scoreItem(
        item: IndexedEmoji,
        rawQuery: String,
        normalizedQuery: String,
        allowContains: Boolean
    ): Int {
        if (item.entry.base == rawQuery) return 2000
        if (item.entry.variants.any { it == rawQuery }) return 1900

        var best = 0
        for (term in item.terms) {
            val value = term.normalizedText
            if (value.isEmpty()) continue
            val localeBonus = if (term.preferredLocale) 25 else 0

            val candidateScore = when {
                value == normalizedQuery -> when (term.kind) {
                    TermKind.NAME -> 1700 + localeBonus
                    TermKind.KEYWORD -> 1600 + localeBonus
                }

                value.startsWith(normalizedQuery) -> when (term.kind) {
                    TermKind.NAME -> 1400 + localeBonus
                    TermKind.KEYWORD -> 1300 + localeBonus
                }

                allowContains && value.contains(normalizedQuery) -> when (term.kind) {
                    TermKind.NAME -> 1000 + localeBonus
                    TermKind.KEYWORD -> 900 + localeBonus
                }

                else -> 0
            }
            if (candidateScore > best) best = candidateScore
        }

        if (best == 0) return 0
        // Stable preference for canonical category order.
        return best - item.categoryOrder
    }

    private suspend fun buildIndex(context: Context, localeChain: List<String>): EmojiSearchIndex {
        // Index every emoji; search() filters per call to what the current field can show.
        val categories = EmojiRepository.getAllEmojiCategories(context)
        val metadataByEmoji = loadMetadataMap(context, localeChain)

        val items = ArrayList<IndexedEmoji>()
        categories.forEachIndexed { categoryOrder, category ->
            category.emojis.forEach { entry ->
                val terms = LinkedHashMap<String, SearchTerm>()

                addTermsForEmoji(
                    terms = terms,
                    metadataByEmoji = metadataByEmoji,
                    emoji = entry.base
                )
                entry.variants.forEach { variant ->
                    addTermsForEmoji(
                        terms = terms,
                        metadataByEmoji = metadataByEmoji,
                        emoji = variant
                    )
                }

                // The search files cover about two thirds of the emoji. Name the rest from
                // system locale data (flags) or their parts' names (symbols, ZWJ sequences).
                if (terms.isEmpty()) {
                    fallbackTerms(entry.base, metadataByEmoji, localeChain).forEach { term ->
                        terms.putIfAbsent(term.normalizedText, term)
                    }
                }

                // If no metadata is available, still index the literal emoji string as a fallback.
                if (terms.isEmpty()) {
                    val literal = normalizeSearchText(entry.base)
                    if (literal.isNotEmpty()) {
                        terms[literal] = SearchTerm(literal, TermKind.KEYWORD, false)
                    }
                }

                items.add(
                    IndexedEmoji(
                        entry = entry,
                        categoryId = category.id,
                        categoryOrder = categoryOrder,
                        terms = terms.values.toList()
                    )
                )
            }
        }

        return EmojiSearchIndex(items = items)
    }

    private fun addTermsForEmoji(
        terms: MutableMap<String, SearchTerm>,
        metadataByEmoji: Map<String, List<Pair<MetadataRecord, Boolean>>>,
        emoji: String
    ) {
        val records = metadataByEmoji[metadataKey(emoji)].orEmpty()
        records.forEach { (record, preferredLocale) ->
            record.name?.let { name ->
                val normalized = normalizeSearchText(name)
                if (normalized.isNotEmpty() && normalized !in terms) {
                    terms[normalized] = SearchTerm(normalized, TermKind.NAME, preferredLocale)
                }
            }
            record.keywords.forEach { keyword ->
                val normalized = normalizeSearchText(keyword)
                if (normalized.isNotEmpty() && normalized !in terms) {
                    terms[normalized] = SearchTerm(normalized, TermKind.KEYWORD, preferredLocale)
                }
            }
        }
    }

    /** Metadata keys ignore U+FE0F, which the emoji data and search files use inconsistently. */
    internal fun metadataKey(emoji: String): String = emoji.replace("\uFE0F", "")

    /**
     * Search terms for an emoji the search files don't cover: localized country names for
     * flags, otherwise the metadata (or Unicode character names) of the sequence's parts.
     */
    @VisibleForTesting
    internal fun fallbackTerms(
        emoji: String,
        metadataByEmoji: Map<String, List<Pair<MetadataRecord, Boolean>>>,
        localeChain: List<String>
    ): List<SearchTerm> {
        val out = LinkedHashMap<String, SearchTerm>()
        fun add(text: String?, kind: TermKind, preferred: Boolean = false) {
            val normalized = normalizeSearchText(text ?: return)
            if (normalized.isNotEmpty() && normalized !in out) {
                out[normalized] = SearchTerm(normalized, kind, preferred)
            }
        }

        val flag = flagCodes(emoji)
        if (flag != null) {
            val (region, subdivision) = flag
            subdivision?.let { add(SUBDIVISION_NAMES[it], TermKind.NAME) }
            val country = Locale("", region)
            localeChain.forEachIndexed { index, tag ->
                add(country.getDisplayCountry(Locale.forLanguageTag(tag)), TermKind.NAME, index == 0)
            }
            add(country.getDisplayCountry(Locale.ENGLISH), TermKind.NAME)
            add(region, TermKind.KEYWORD)
            add("flag", TermKind.KEYWORD)
            return out.values.toList()
        }

        // ZWJ sequences, keycaps and text-style symbols: describe each part
        val partNames = ArrayList<String>()
        emoji.split('\u200D').forEach { part ->
            val core = coreOf(part)
            if (core.isEmpty()) return@forEach
            val records = metadataByEmoji[metadataKey(core)]
            if (!records.isNullOrEmpty()) {
                records.forEach { (record, preferred) ->
                    add(record.name, TermKind.NAME, preferred)
                    record.keywords.forEach { add(it, TermKind.KEYWORD, preferred) }
                }
                records.first().first.name?.let(partNames::add)
            } else {
                val names = ArrayList<String>()
                core.codePoints().forEach { cp ->
                    Character.getName(cp)?.lowercase(Locale.ROOT)?.let(names::add)
                }
                names.forEach { add(it, TermKind.NAME) }
                if (names.isNotEmpty()) partNames.add(names.joinToString(" "))
            }
        }
        if (partNames.size > 1) add(partNames.joinToString(" "), TermKind.NAME)
        if (emoji.contains('\u20E3')) add("keycap", TermKind.KEYWORD)
        return out.values.toList()
    }

    /** Region code (and subdivision tag, e.g. "gbeng") for flag emoji, else null. */
    private fun flagCodes(emoji: String): Pair<String, String?>? {
        val cps = emoji.codePoints().toArray()
        if (cps.size == 2 && cps.all { it in 0x1F1E6..0x1F1FF }) {
            val region = String(charArrayOf('A' + (cps[0] - 0x1F1E6), 'A' + (cps[1] - 0x1F1E6)))
            return region to null
        }
        // Subdivision flags: black flag, tag letters, cancel tag
        if (cps.size > 3 && cps.first() == 0x1F3F4 && cps.last() == 0xE007F &&
            cps.drop(1).dropLast(1).all { it in 0xE0061..0xE007A }
        ) {
            val tag = String(cps.drop(1).dropLast(1).map { 'a' + (it - 0xE0061) }.toCharArray())
            return tag.take(2).uppercase(Locale.ROOT) to tag
        }
        return null
    }

    /** [part] without presentation selectors, keycap marks and skin-tone modifiers. */
    private fun coreOf(part: String): String {
        val sb = StringBuilder()
        part.codePoints().forEach { cp ->
            if (cp != 0xFE0F && cp != 0x20E3 && cp !in 0x1F3FB..0x1F3FF) sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    private val SUBDIVISION_NAMES = mapOf(
        "gbeng" to "England",
        "gbsct" to "Scotland",
        "gbwls" to "Wales"
    )

    private fun loadMetadataMap(
        context: Context,
        localeChain: List<String>
    ): Map<String, List<Pair<MetadataRecord, Boolean>>> {
        val out = LinkedHashMap<String, MutableList<Pair<MetadataRecord, Boolean>>>()
        localeChain.forEachIndexed { index, localeTag ->
            val isPreferred = index == 0
            val records = loadMetadataAsset(context, localeTag) ?: return@forEachIndexed
            records.forEach { (emoji, record) ->
                out.getOrPut(emoji) { mutableListOf() }.add(record to isPreferred)
            }
        }
        return out
    }

    private fun loadMetadataAsset(
        context: Context,
        localeTag: String
    ): Map<String, MetadataRecord>? {
        val candidates = localeAssetCandidates(localeTag)
        for (candidate in candidates) {
            val assetPath = "$SEARCH_ASSET_DIR/$candidate.tsv"
            val parsed = runCatching {
                context.assets.open(assetPath).use { input ->
                    BufferedReader(InputStreamReader(input)).lineSequence()
                        .mapNotNull { line ->
                            if (line.isBlank()) return@mapNotNull null
                            val parts = line.split('\t')
                            if (parts.isEmpty()) return@mapNotNull null
                            val emoji = parts.getOrNull(0)?.trim().orEmpty()
                            if (emoji.isEmpty()) return@mapNotNull null
                            val name = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                            val keywords = parts.getOrNull(2)
                                ?.split('|')
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                .orEmpty()
                            metadataKey(emoji) to MetadataRecord(name = name, keywords = keywords)
                        }
                        .toMap()
                }
            }.getOrNull()

            if (!parsed.isNullOrEmpty()) {
                return parsed
            }
        }
        return null
    }

    private fun localeAssetCandidates(localeTag: String): List<String> {
        val normalized = localeTag.replace('-', '_').lowercase(Locale.ROOT)
        val languageOnly = normalized.substringBefore('_')
        return buildList {
            if (normalized.isNotBlank()) add(normalized)
            if (languageOnly.isNotBlank() && languageOnly != normalized) add(languageOnly)
        }.distinct()
    }

    @VisibleForTesting
    internal fun getPreferredLocaleChain(context: Context): List<String> {
        val locales = context.resources.configuration.locales
        val out = ArrayList<String>()
        for (i in 0 until locales.size()) {
            val locale = locales[i] ?: continue
            val tag = locale.toLanguageTag()
            if (tag.isNotBlank()) out.add(tag)
            val language = locale.language
            if (!language.isNullOrBlank()) out.add(language)
        }
        out.add(EN_LOCALE)
        return out.distinct()
    }

    @VisibleForTesting
    internal fun normalizeSearchText(text: String): String {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty()) return ""

        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val stripped = buildString(decomposed.length) {
            var previousSpace = false
            decomposed.forEach { ch ->
                val type = Character.getType(ch)
                if (type == Character.NON_SPACING_MARK.toInt()) return@forEach
                if (Character.isLetterOrDigit(ch)) {
                    append(ch)
                    previousSpace = false
                } else if (Character.isWhitespace(ch) || ch == '-' || ch == '_') {
                    if (!previousSpace && isNotEmpty()) {
                        append(' ')
                        previousSpace = true
                    }
                }
            }
            if (isNotEmpty() && last() == ' ') {
                deleteCharAt(lastIndex)
            }
        }
        return stripped
    }
}
