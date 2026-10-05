package it.palsoftware.pastiera.data.symbols

import android.content.Context
import it.palsoftware.pastiera.core.IncognitoTyping
import it.palsoftware.pastiera.core.writeTextAtomically
import java.io.File
import java.util.Locale

/**
 * Kaomoji for the symbols page's kaomoji key (P): pages of 24, one per key (Q goes back a page,
 * P on to the next), grouped by mood, expression, action and animal (assets/common/kaomoji.txt).
 * The kaomoji you typed last come first, on a page of their own, like the emoji and symbol recents.
 * The first page keeps A for search ([SEARCH_KEY_PAGE]), so it holds one fewer.
 *
 * Each kaomoji is searchable by its group's words (its section, its group and the words after
 * "|") and by its own names after a tab (lenny face, table flip).
 */
object Kaomoji {
    const val PER_PAGE = 24
    /** The page that shows the search key (on A). */
    const val SEARCH_KEY_PAGE = 0
    private const val ASSET = "common/kaomoji.txt"
    private const val RECENTS_FILE = "kaomoji_recents.txt"
    private const val MAX_RECENTS = PER_PAGE - 1

    @Volatile private var entries: List<SymbolSearch.Entry>? = null

    /** Every kaomoji, in their groups' order, named by their groups' words and their own. */
    fun entries(context: Context): List<SymbolSearch.Entry> = entries ?: runCatching {
        parse(context.assets.open(ASSET).bufferedReader().readLines())
    }.getOrDefault(emptyList()).also { entries = it }

    /** Every kaomoji, in their groups' order. */
    fun all(context: Context): List<String> = entries(context).map { it.symbol }

    @Volatile private var groupStarts: List<Pair<List<String>, Int>>? = null

    /**
     * Where the first group whose heading words include [word] starts in [entries]; null when
     * none does. By heading, not by each kaomoji's names: a kaomoji's own names can carry another
     * group's word (a sad one named for a hug).
     */
    fun groupStart(context: Context, word: String): Int? {
        val starts = groupStarts ?: runCatching {
            groupStarts(context.assets.open(ASSET).bufferedReader().readLines())
        }.getOrDefault(emptyList()).also { groupStarts = it }
        val key = word.lowercase(Locale.ROOT)
        return starts.firstOrNull { (words, _) -> key in words }?.second
    }

    /** Each group's heading words and the position its first new kaomoji has in [parse]'s list. */
    internal fun groupStarts(lines: List<String>): List<Pair<List<String>, Int>> {
        val seen = HashSet<String>()
        val starts = mutableListOf<Pair<List<String>, Int>>()
        var pending: List<String>? = null
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("## ")) {
                val heading = line.removePrefix("## ")
                if ('|' in heading) pending = words(heading.replace('·', ' ').replace('|', ' '))
                continue
            }
            val kaomoji = line.substringBefore('\t')
            if (!seen.add(kaomoji)) continue
            pending?.let { starts += it to seen.size - 1 }
            pending = null
        }
        return starts
    }

    internal fun parse(lines: List<String>): List<SymbolSearch.Entry> {
        val names = LinkedHashMap<String, MutableSet<String>>()
        var groupWords = emptyList<String>()
        for (line in lines) {
            if (line.isBlank()) continue
            if (line.startsWith("## ")) {
                // "## Mood · Joy | happy glad": a group, or a note (no "|")
                val heading = line.removePrefix("## ")
                if ('|' in heading) groupWords = words(heading.replace('·', ' ').replace('|', ' '))
                continue
            }
            val kaomoji = line.substringBefore('\t')
            val own = if ('\t' in line) words(line.substringAfter('\t')) else emptyList()
            names.getOrPut(kaomoji) { LinkedHashSet() }.apply { addAll(own); addAll(groupWords) }
        }
        return names.map { (kaomoji, words) -> SymbolSearch.Entry(kaomoji, words.joinToString(" ")) }
    }

    private fun words(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it != "and" }

    /** Kaomoji whose names hold every word of [query], best first, recent ones ahead. */
    fun search(context: Context, query: String): List<SymbolSearch.Entry> {
        val found = SymbolSearch.search(query, entries(context))
        return SymbolSearch.recentsFirst(found, recents(context))
    }

    /** The pages: the recents first when there are any, then every kaomoji, [PER_PAGE] a page. */
    fun pages(context: Context): List<List<String>> {
        val recents = recents(context).take(PER_PAGE - 1)
        val all = all(context)
        if (recents.isNotEmpty()) return listOf(recents) + all.chunked(PER_PAGE)
        // No recents yet: the first group's page holds the search key instead
        return listOf(all.take(PER_PAGE - 1)) + all.drop(PER_PAGE - 1).chunked(PER_PAGE)
    }

    /** The kaomoji typed last, newest first. */
    fun recents(context: Context): List<String> = runCatching {
        File(context.applicationContext.filesDir, RECENTS_FILE).takeIf { it.isFile }
            ?.readLines()?.filter { it.isNotEmpty() }
    }.getOrNull().orEmpty()

    /** A kaomoji was typed (not in Incognito typing): first among the recents. */
    fun addRecent(context: Context, kaomoji: String) {
        if (IncognitoTyping.active || kaomoji.isBlank()) return
        val updated = (listOf(kaomoji) + recents(context).filter { it != kaomoji }).take(MAX_RECENTS)
        runCatching { File(context.applicationContext.filesDir, RECENTS_FILE).writeTextAtomically(updated.joinToString("\n")) }
    }
}
