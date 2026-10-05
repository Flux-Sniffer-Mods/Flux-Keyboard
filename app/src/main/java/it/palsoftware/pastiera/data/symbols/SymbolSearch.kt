package it.palsoftware.pastiera.data.symbols

import it.palsoftware.pastiera.core.writeTextAtomically
import android.content.Context
import android.graphics.Paint
import android.os.Build
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Finds symbols by their Unicode names ("degree", "euro", "right arrow", "check mark"). Every
 * Unicode symbol ships with the app ([ASSET], generated from the Unicode data: Android doesn't
 * reliably offer character names to apps), so loading is just reading that list.
 *
 * Nothing checks the fonts up front. A symbol is checked the first time it's about to be shown
 * ([renderable], [canRender]); one the phone can't draw is dropped and never shown again. The
 * answers are kept per Android build (fonts only change with the system). Nothing is downloaded.
 */
object SymbolSearch {
    data class Entry(val symbol: String, val name: String) {
        /** The name's words, split once. */
        val words: List<String> by lazy { name.split(' ', '-').filter { it.isNotEmpty() } }
    }

    private const val ASSET = "symbols/symbols.tsv"
    private const val VERDICTS_FILE = "symbol-glyphs-v1.tsv"
    private val OLD_FILES = listOf("symbol-search-v1.tsv", "symbol-search-v2.tsv")

    // Symbol search's own thread (reading the list and every search): its work never waits
    // behind GIF downloads, GIF decoding or the emoji data loading on the shared pools
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "symbol-search").apply { isDaemon = true }
    }

    /** Where symbol searches run. */
    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /** How many results are checked before a search shows them: about a screenful. */
    const val CHECK_BEFORE_SHOWING = 96

    @Volatile
    private var index: List<Entry>? = null
    private val loading = AtomicBoolean(false)

    // Code point -> whether the fonts can draw it; persisted per Android build
    private val verdicts = ConcurrentHashMap<Int, Boolean>()
    @Volatile
    private var verdictsLoaded = false
    private val verdictsDirty = AtomicBoolean(false)
    private val saveScheduled = AtomicBoolean(false)

    /** True once the list is in memory. */
    fun isReady(): Boolean = index != null

    /** Every bundled symbol (read once; a few tens of milliseconds). */
    @Synchronized
    fun entries(context: Context): List<Entry> {
        index?.let { return it }
        val appContext = context.applicationContext
        OLD_FILES.forEach { File(appContext.filesDir, it).delete() }
        loadVerdicts(appContext)
        val list = readAsset(appContext)
        index = list
        return list
    }

    /** Reads the list on symbol search's thread, once; call when a symbols page opens. */
    fun prewarm(context: Context) {
        if (index != null || !loading.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        executor.execute {
            try {
                entries(appContext)
            } finally {
                loading.set(false)
            }
        }
    }

    /** The bundled symbols with their names. */
    fun readAsset(context: Context): List<Entry> =
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parseList(it.readText()) }

    /** "code point (hex) TAB name" lines; # starts a comment. */
    fun parseList(text: String): List<Entry> = text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val codePoint = line.substring(0, tab).toIntOrNull(16) ?: return@mapNotNull null
            Entry(String(Character.toChars(codePoint)), line.substring(tab + 1).lowercase(Locale.ROOT))
        }
        .toList()

    /**
     * [found] ready to show: symbols known not to render are dropped; the first [checkFirst]
     * that haven't been checked yet are checked now (the rest when they're about to be shown).
     */
    fun renderable(
        found: List<Entry>,
        checkFirst: Int = CHECK_BEFORE_SHOWING,
        hasGlyph: (String) -> Boolean = ::fontHasGlyph
    ): List<Entry> {
        var checked = 0
        return found.filter { entry ->
            val known = verdicts[entry.symbol.codePointAt(0)]
            when {
                known != null -> known
                checked < checkFirst -> {
                    checked++
                    canRender(entry.symbol, hasGlyph)
                }
                else -> true // checked when it's about to be shown
            }
        }
    }

    /** Whether the phone's fonts can draw [symbol]; checked once, then remembered. */
    fun canRender(symbol: String, hasGlyph: (String) -> Boolean = ::fontHasGlyph): Boolean {
        val codePoint = symbol.codePointAt(0)
        verdicts[codePoint]?.let { return it }
        val drawable = runCatching { hasGlyph(symbol) }.getOrDefault(false)
        verdicts[codePoint] = drawable
        verdictsDirty.set(true)
        return drawable
    }

    /** Whether [symbol] has been checked already. */
    fun isChecked(symbol: String): Boolean = verdicts.containsKey(symbol.codePointAt(0))

    /** Saves new answers a moment later on a background thread (batching several checks). */
    fun saveVerdictsSoon(context: Context) {
        if (!verdictsDirty.get() || !saveScheduled.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Thread({
            try {
                Thread.sleep(1500)
                if (verdictsDirty.getAndSet(false)) {
                    saveVerdicts(File(appContext.filesDir, VERDICTS_FILE), Build.FINGERPRINT, verdicts)
                }
            } finally {
                saveScheduled.set(false)
            }
        }, "symbol-search-save").start()
    }

    private fun loadVerdicts(context: Context) {
        if (verdictsLoaded) return
        verdictsLoaded = true
        loadVerdicts(File(context.filesDir, VERDICTS_FILE), Build.FINGERPRINT)?.let { verdicts.putAll(it) }
    }

    /** Saved answers, if they were made on this Android build ([fingerprint]). */
    fun loadVerdicts(file: File, fingerprint: String): Map<Int, Boolean>? {
        if (!file.isFile) return null
        return runCatching {
            val lines = file.readLines()
            if (lines.firstOrNull() != fingerprint) return null
            lines.drop(1).mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@mapNotNull null
                val codePoint = line.substring(0, tab).toIntOrNull(16) ?: return@mapNotNull null
                codePoint to (line.substring(tab + 1) == "1")
            }.toMap()
        }.getOrNull()
    }

    fun saveVerdicts(file: File, fingerprint: String, answers: Map<Int, Boolean>) {
        runCatching {
            val text = buildString {
                append(fingerprint).append('\n')
                answers.forEach { (codePoint, drawable) ->
                    append(Integer.toHexString(codePoint)).append('\t').append(if (drawable) '1' else '0').append('\n')
                }
            }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(text)
            temp.renameTo(file)
        }
    }

    private const val RECENTS_FILE = "symbol-recents.txt"
    const val MAX_RECENTS = 40

    /** Symbols picked from symbol search, newest first. */
    fun recentSymbols(context: Context): List<String> = runCatching {
        File(context.applicationContext.filesDir, RECENTS_FILE).takeIf { it.isFile }
            ?.readLines()?.filter { it.isNotEmpty() }
    }.getOrNull().orEmpty()

    /** A symbol was picked: it goes to the top of the recents. */
    fun addRecent(context: Context, symbol: String) {
        if (it.palsoftware.pastiera.core.IncognitoTyping.active) return
        val updated = (listOf(symbol) + recentSymbols(context).filter { it != symbol }).take(MAX_RECENTS)
        runCatching { File(context.applicationContext.filesDir, RECENTS_FILE).writeTextAtomically(updated.joinToString("\n")) }
    }

    /** [found] with the [recents] among them first (newest first), the rest in their order. */
    fun recentsFirst(found: List<Entry>, recents: List<String>): List<Entry> {
        if (recents.isEmpty()) return found
        val rank = recents.withIndex().associate { (index, symbol) -> symbol to index }
        return found.sortedBy { rank[it.symbol] ?: Int.MAX_VALUE }
    }

    /** Forgets every answer (tests). */
    fun clearVerdicts() {
        verdicts.clear()
        verdictsDirty.set(false)
    }

    // Words that pad Unicode names without saying what a symbol is ("greek small letter pi")
    private val FILLER = setOf("sign", "greek", "small", "capital", "letter", "with", "symbol", "mark", "of", "the", "and")

    // Everyday words for symbols whose Unicode names differ (™ is "trade mark sign", ¶ "pilcrow")
    private val ALIASES = mapOf(
        "trademark" to "trade mark", "tm" to "trade mark", "paragraph" to "pilcrow",
        "approx" to "almost equal", "approximately" to "almost equal", "tick" to "check mark",
        "times" to "multiplication sign", "sqrt" to "square root", "root" to "square root",
        "sum" to "summation", "deg" to "degree"
    )

    /**
     * Symbols whose name contains every word of [query], or its everyday alias, or that are the
     * query itself. Best matches first: whole words over word starts, relative to how long the
     * name is (filler words aside). A blank query returns everything.
     */
    fun search(query: String, entries: List<Entry>, limit: Int = 400): List<Entry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return entries
        val lower = trimmed.lowercase(Locale.ROOT)
        val queries = listOfNotNull(lower, ALIASES[lower]).map { it.split(Regex("\\s+")) }
        return entries.asSequence()
            .mapNotNull { entry ->
                if (entry.symbol == trimmed) return@mapNotNull entry to Double.MAX_VALUE
                val best = queries
                    .filter { words -> words.all { entry.name.contains(it) } }
                    .maxOfOrNull { words -> score(entry.words, words) }
                    ?: return@mapNotNull null
                entry to best
            }
            .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.name.length })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun score(nameWords: List<String>, words: List<String>): Double {
        val points = words.sumOf { word ->
            when {
                word in nameWords -> 2
                nameWords.any { it.startsWith(word) } -> 1
                else -> 0
            }.toInt()
        }
        return points.toDouble() / maxOf(1, nameWords.count { it !in FILLER })
    }

    // Paint isn't thread-safe: one per thread (searches check in the background, scrolling on
    // the main thread)
    private val glyphPaint = ThreadLocal.withInitial { Paint() }

    private fun fontHasGlyph(symbol: String): Boolean = glyphPaint.get()!!.hasGlyph(symbol)
}
