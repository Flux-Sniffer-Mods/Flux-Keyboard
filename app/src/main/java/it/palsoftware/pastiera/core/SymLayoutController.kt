package it.palsoftware.pastiera.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.sym.SymPagesConfig
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.data.symbols.Kaomoji
import it.palsoftware.pastiera.inputmethod.AlternateCharacterManager
import it.palsoftware.pastiera.activeEmojiLayerGifKey
import it.palsoftware.pastiera.clearRestoreSymPage
import it.palsoftware.pastiera.emojiScreenClosesAfterInput
import it.palsoftware.pastiera.getEmojiLayerCloseOnKey
import it.palsoftware.pastiera.getEmojiLayerPages
import it.palsoftware.pastiera.getEmojiLayerRecentsKey
import it.palsoftware.pastiera.getEmojiLayerTypeToSearch
import it.palsoftware.pastiera.getKaomojiCloseOnKey
import it.palsoftware.pastiera.getRestoreSymPage
import it.palsoftware.pastiera.getSearchKey
import it.palsoftware.pastiera.getSymAutoClose
import it.palsoftware.pastiera.getSymPagesConfig
import it.palsoftware.pastiera.getSymbolsCloseOnKey
import it.palsoftware.pastiera.getSymbolsPages
import it.palsoftware.pastiera.getSymbolsTypeToSearch
import it.palsoftware.pastiera.gifsAvailable
import it.palsoftware.pastiera.shouldApplyFrenchPunctuationSpacing

class SymLayoutController(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val alternateCharacterManager: AlternateCharacterManager
) {

    companion object {
        private const val PREF_CURRENT_SYM_PAGE = "current_sym_page"
        /**
         * Labels of the emoji layer's Recents key, as text symbols rather than emoji: shows the
         * recent emoji / back to the layer. U+FE0E keeps the arrow from turning into an emoji.
         */
        const val RECENTS_KEY_LABEL = "\u21BA"         // ↺
        const val RECENTS_BACK_LABEL = "\u21A9\uFE0E"  // ↩ (text presentation)
        /**
         * Label of the search key on the emoji layer and the symbols pages: a text symbol, so it
         * isn't mistaken for an emoji the key would type.
         */
        const val SEARCH_KEY_LABEL = "\u2315"         // ⌕
        /** Label of the emoji layer's GIF key. */
        const val GIF_KEY_LABEL = "GIF"
        /** Labels of the symbols page's kaomoji key: open the kaomoji, next page of them. */
        const val KAOMOJI_KEY_LABEL = "(ᵔᴗᵔ)"
        /**
         * On the first kaomoji page, L goes back to the symbols. The key draws the emoji page's
         * symbols icon in its place; this is only its marker (and what a screen reader reads).
         */
        const val SYMBOLS_KEY_LABEL = "&"
        const val KAOMOJI_NEXT_LABEL = "\u203A"  // ›
        const val KAOMOJI_PREVIOUS_LABEL = "\u2039"  // ‹
        /** On the kaomoji pages, Q goes back a page (from the first, round to the last). */
        const val KAOMOJI_PREVIOUS_KEY = KeyEvent.KEYCODE_Q
        /** On the first kaomoji page (the recents, when there are any), A searches the kaomoji. */
        const val KAOMOJI_SEARCH_KEY = KeyEvent.KEYCODE_A
        /** Layers shown as pages: Q the page before, P the next (round and round). */
        const val PAGE_PREVIOUS_KEY = KeyEvent.KEYCODE_Q
        const val PAGE_NEXT_KEY = KeyEvent.KEYCODE_P
        /** A recents page's search key (A) and its other button (L: GIFs, or the kaomoji). */
        const val PAGE_SEARCH_KEY = KeyEvent.KEYCODE_A
        const val PAGE_EXTRA_KEY = KeyEvent.KEYCODE_L

        /** The symbols page shows kaomoji now (its keys are drawn taller, letters under them). */
        /** The emoji layer's category pages, once read. */
        @Volatile
        private var categoryEmoji: Pair<Int, List<String>>? = null

        /** Keys of the paged layer shown now holding starters rather than recents (their own tint). */
        @Volatile
        var starterKeys: Set<Int> = emptySet()
            private set

        @Volatile
        var kaomojiShown: Boolean = false
            private set

        /** The symbols page's kaomoji key: P, unless Recents or search is on P. */
        fun kaomojiKey(context: Context): Int {
            val key = KeyEvent.KEYCODE_P
            return if (key == SettingsManager.getEmojiLayerRecentsKey(context) || key == SettingsManager.getSearchKey(context)) {
                KeyEvent.KEYCODE_UNKNOWN
            } else key
        }
    }

    private enum class SymPage {
        DEVICE,
        EMOJI,
        SYMBOLS,
        CLIPBOARD,
        EMOJI_PICKER
    }

    enum class SymKeyResult {
        NOT_HANDLED,
        CONSUME,
        CALL_SUPER
    }

    private var symPage: Int = prefs.getInt(PREF_CURRENT_SYM_PAGE, 0)

    /**
     * The emoji key (not the Sym cycle) opened the current page: the emoji layer is allowed even
     * when it isn't in the cycle, and its auto-close follows the emoji key's own setting.
     */
    var openedByEmojiKey: Boolean = false
        private set

    /** Where the search key was pressed. */
    enum class SearchTarget { EMOJI_LAYER, SYMBOLS, KAOMOJI, PICKER }

    /** The search key was pressed: the input method opens that screen's search. */
    var onSearchKey: ((SearchTarget) -> Unit)? = null

    /** The emoji layer's GIF key was pressed: the input method opens GIF search. */
    var onEmojiLayerGifKey: (() -> Unit)? = null

    /** Type to search: a letter on the emoji layer (true) or a symbols page (false), and its text. */
    var onTypeToSearch: ((emoji: Boolean, text: String) -> Unit)? = null

    /** The emoji layer shows recent emoji on its keys (its Recents key was pressed). */
    var emojiLayerShowsRecents: Boolean = false
        private set

    /** The symbols page shows recent symbols on its keys (its Recents key was pressed). */
    var symbolsShowRecents: Boolean = false
        private set

    /** The kaomoji page the symbols page's keys show (-1: its own symbols). */
    var symbolsKaomojiPage: Int = -1
        private set(value) {
            field = value
            kaomojiShown = value >= 0
        }

    // ---- Layers as pages (the emoji layer and the symbols page, like the kaomoji) ----

    private enum class PageKind { RECENTS, LAYER, KAOMOJI }

    /**
     * One page of a layer shown as pages: what each key types, and on a recents page the keys
     * holding starters from the first pages rather than recents (drawn in a tint of their own).
     */
    private class LayerPage(val keys: Map<Int, String>, val kind: PageKind, val starters: Set<Int> = emptySet())

    /**
     * The recents as they were when the layer opened or turned to its recents page: using one
     * doesn't move the keys under you; they're read again on the next page turn.
     */
    private var emojiRecentsShown: List<String>? = null
    private var symbolRecentsShown: List<String>? = null
    private var kaomojiRecentsShown: List<String>? = null

    /** The page each layer shows (-1: not opened yet, so its first page with anything on it). */
    private var emojiPageIndex = -1
    private var symbolsPageIndex = -1

    /** The skin tones (and other variants) of an emoji, shown on their own page while held; null when not. */
    private var emojiVariants: List<String>? = null

    /** A pressed emoji with variants: typed when its key comes up, its variants if it's held. */
    private var pendingEmojiKey = KeyEvent.KEYCODE_UNKNOWN
    private var pendingEmoji: String? = null
    private var pendingEmojiCloses = false
    /** The key whose hold opened the variants: its release is spent. */
    private var variantsHeldKey = KeyEvent.KEYCODE_UNKNOWN

    /** [emoji] and its variants (skin tones), or empty when it has none. */
    private fun variantsOf(emoji: String): List<String> {
        emojiInOrder() // loads the emoji data the variants come from
        val others = it.palsoftware.pastiera.data.emoji.EmojiRepository.getVariantsForEmoji(emoji)
        return if (others.isEmpty()) emptyList() else (listOf(emoji) + others).distinct()
    }

    /** Holding an emoji on the emoji layer's pages (key or touch): its variants on a page of their own. */
    fun showEmojiVariants(emoji: String): Boolean {
        if (pagedLayer() != true) return false
        val variants = variantsOf(emoji).takeIf { it.size > 1 } ?: return false
        emojiVariants = variants.take(pageSlots.size)
        return true
    }

    /** Back from the variants page to the page it came from. */
    fun closeEmojiVariants(): Boolean {
        if (emojiVariants == null) return false
        emojiVariants = null
        return true
    }

    /** Records an emoji typed from the layer's pages as a recent. */
    private fun emojiTyped(text: String) {
        RecentEmojiManager.addRecentEmoji(context, text)
        it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.markUsed(context, text)
    }

    /**
     * A key coming up on the emoji layer's pages: a pressed emoji with variants that wasn't held
     * long enough to show them is typed now. True when the release was the layer's.
     */
    fun handlePagedKeyUp(keyCode: Int, inputConnection: InputConnection?, updateStatusBar: () -> Unit): Boolean {
        if (variantsHeldKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == variantsHeldKey) {
            variantsHeldKey = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        val emoji = pendingEmoji ?: return false
        if (keyCode != pendingEmojiKey) return false
        pendingEmoji = null
        pendingEmojiKey = KeyEvent.KEYCODE_UNKNOWN
        inputConnection?.commitText(emoji, 1)
        emojiTyped(emoji)
        if (pendingEmojiCloses) closeSymAndUpdate(updateStatusBar)
        return true
    }

    private fun emojiPaged(): Boolean = SettingsManager.getEmojiLayerPages(context)
    private fun symbolsPaged(): Boolean = SettingsManager.getSymbolsPages(context)

    /** Every key but the page arrows and search (A), in order (W along the rows): 23 per page. */
    private val pageSlots: List<Int> = SettingsManager.EMOJI_LAYER_KEYS.filter {
        it != PAGE_PREVIOUS_KEY && it != PAGE_NEXT_KEY && it != PAGE_SEARCH_KEY
    }

    private fun sequential(items: List<String>, skip: Set<Int> = emptySet()): Map<Int, String> =
        pageSlots.filter { it !in skip }.zip(items).toMap()

    /**
     * A recents page: the [recents] first, the free keys after them topped up with [starters]
     * (the first pages' own, those not already there), so a new layer isn't empty.
     */
    private fun recentsPage(recents: List<String>, starters: List<String>, kind: PageKind, skip: Set<Int>): LayerPage {
        val slots = pageSlots.filter { it !in skip }
        val shown = recents.take(slots.size)
        // A starter you already have among the recents isn't shown twice, in any skin tone or
        // presentation (☺ and ☺️, 👍 and 👍🏽)
        val recentKeys = recents.mapTo(HashSet()) { sameItemKey(it) }
        val fill = starters.filter { sameItemKey(it) !in recentKeys }.take(slots.size - shown.size)
        val keys = slots.zip(shown + fill).toMap()
        return LayerPage(keys, kind, starters = slots.drop(shown.size).take(fill.size).toSet())
    }

    /**
     * A recents view on a layer that isn't paged: the [recents] along the keys (W first, along
     * the rows) except the [keep] ones, which keep their job, then the layer's [own] items not
     * already among them on the keys left, marked as starters.
     */
    private fun recentsWithStarters(recents: List<String>, own: List<String>, keep: Set<Int>): MutableMap<Int, String> {
        val slots = SettingsManager.EMOJI_LAYER_KEYS.filter { it !in keep }
        val shown = recents.take(slots.size)
        val recentKeys = shown.mapTo(HashSet()) { sameItemKey(it) }
        val fill = own.filter { sameItemKey(it) !in recentKeys }.take(slots.size - shown.size)
        starterKeys = slots.drop(shown.size).take(fill.size).toSet()
        return slots.zip(shown + fill).toMap().toMutableMap()
    }

    /** The same emoji, symbol or kaomoji whatever its skin tone, presentation or spacing. */
    private fun sameItemKey(item: String): String = buildString {
        var i = 0
        while (i < item.length) {
            val cp = item.codePointAt(i)
            i += Character.charCount(cp)
            if (cp == 0xFE0F || cp == 0xFE0E || cp in 0x1F3FB..0x1F3FF) continue
            appendCodePoint(cp)
        }
    }.trim()

    /**
     * The emoji layer's pages: your recent emoji (topped up with the first smileys), then every
     * emoji the phone can draw, one category running straight on into the next. Nothing you
     * mapped yourself: the pages are for finding, the recents are yours.
     */
    private fun emojiPages(): List<LayerPage> {
        val gifs = SettingsManager.gifsAvailable(context)
        val all = emojiInOrder()
        val recents = emojiRecentsShown ?: RecentEmojiManager.getRecentEmojis(context).also { emojiRecentsShown = it }
        val pages = mutableListOf(recentsPage(recents, all, PageKind.RECENTS, if (gifs) setOf(PAGE_EXTRA_KEY) else emptySet()))
        all.chunked(pageSlots.size).forEach { emoji -> pages += LayerPage(sequential(emoji), PageKind.LAYER) }
        return pages
    }

    /**
     * Every emoji in the categories' order, each in the default skin tone when it has one (the
     * other tones by holding it); read once per tone, as the emoji picker reads them.
     */
    private fun emojiInOrder(): List<String> {
        val tone = it.palsoftware.pastiera.data.emoji.EmojiSkinTone.get(context)
        categoryEmoji?.takeIf { it.first == tone }?.let { return it.second }
        return runCatching {
            kotlinx.coroutines.runBlocking {
                it.palsoftware.pastiera.data.emoji.EmojiRepository.getEmojiCategories(context)
            }.filter { category -> category.id != it.palsoftware.pastiera.data.emoji.EmojiRepository.RECENTS_CATEGORY_ID }
                .flatMap { category ->
                    category.emojis.map { entry ->
                        it.palsoftware.pastiera.data.emoji.EmojiSkinTone.apply(entry.base, entry.variants, tone)
                    }
                }
        }.getOrDefault(emptyList()).also { emoji -> if (emoji.isNotEmpty()) categoryEmoji = tone to emoji }
    }

    /**
     * The symbols page's pages: your recent symbols (topped up with the first symbols), the
     * symbols running straight on page to page, then the kaomoji: your recent ones (topped up
     * the same way) and every kaomoji, group running into group.
     */
    private fun symbolPages(): List<LayerPage> {
        val symbols = it.palsoftware.pastiera.data.symbols.SymbolPages.PAGES.flatten()
        val recents = symbolRecentsShown
            ?: it.palsoftware.pastiera.data.symbols.SymbolSearch.recentSymbols(context).also { symbolRecentsShown = it }
        val pages = mutableListOf(recentsPage(recents, symbols, PageKind.RECENTS, setOf(PAGE_EXTRA_KEY)))
        symbols.chunked(pageSlots.size).forEach { pages += LayerPage(sequential(it), PageKind.LAYER) }
        val kaomoji = Kaomoji.all(context)
        val kaomojiRecents = kaomojiRecentsShown ?: Kaomoji.recents(context).also { kaomojiRecentsShown = it }
        pages += recentsPage(kaomojiRecents, kaomoji, PageKind.KAOMOJI, setOf(PAGE_EXTRA_KEY))
        kaomoji.chunked(pageSlots.size).forEach { pages += LayerPage(sequential(it), PageKind.KAOMOJI) }
        return pages
    }

    private fun pagesFor(emoji: Boolean): List<LayerPage> = if (emoji) emojiPages() else symbolPages()

    /** The page a layer shows: the recents when it opens, then wherever Q and P took it. */
    private fun pageIndex(emoji: Boolean, pages: List<LayerPage>): Int {
        val stored = if (emoji) emojiPageIndex else symbolsPageIndex
        val index = if (stored in pages.indices) stored else 0
        if (emoji) emojiPageIndex = index else symbolsPageIndex = index
        return index
    }

    private fun setPageIndex(emoji: Boolean, index: Int, pages: List<LayerPage>) {
        if (emoji) emojiPageIndex = index else {
            symbolsPageIndex = index
            kaomojiShown = pages[index].kind == PageKind.KAOMOJI
        }
    }

    /** The keys a paged layer shows: its page, the arrows on Q and P, and the recents page's buttons. */
    private fun pagedMappings(emoji: Boolean): Map<Int, String> {
        if (emoji) emojiVariants?.let { variants ->
            // An emoji's variants: one per key, ‹ back to its page
            starterKeys = emptySet()
            return sequential(variants).toMutableMap().apply { put(PAGE_PREVIOUS_KEY, KAOMOJI_PREVIOUS_LABEL) }
        }
        val pages = pagesFor(emoji)
        val page = pages[pageIndex(emoji, pages)]
        if (!emoji) kaomojiShown = page.kind == PageKind.KAOMOJI
        starterKeys = page.starters
        val shown = page.keys.toMutableMap()
        shown[PAGE_PREVIOUS_KEY] = KAOMOJI_PREVIOUS_LABEL
        shown[PAGE_NEXT_KEY] = KAOMOJI_NEXT_LABEL
        // Search on every page: the emoji's, the symbols' or the kaomoji's
        shown[PAGE_SEARCH_KEY] = SEARCH_KEY_LABEL
        // The kaomoji's first page: L back to the symbols, as the symbols' L goes to the kaomoji
        if (!emoji && pages.indexOfFirst { it.kind == PageKind.KAOMOJI } == pages.indexOf(page)) {
            shown[PAGE_EXTRA_KEY] = SYMBOLS_KEY_LABEL
        }
        if (page.kind == PageKind.RECENTS) {
            if (!emoji) shown[PAGE_EXTRA_KEY] = KAOMOJI_KEY_LABEL
            else if (SettingsManager.gifsAvailable(context)) shown[PAGE_EXTRA_KEY] = GIF_KEY_LABEL
        }
        return shown
    }

    /** The current layer is shown as pages: true for the emoji layer, false for symbols, null if neither. */
    private fun pagedLayer(): Boolean? = when (currentPageType()) {
        SymPage.EMOJI -> if (emojiPaged()) true else null
        SymPage.SYMBOLS -> if (symbolsPaged()) false else null
        else -> null
    }

    private fun turnPage(by: Int): Boolean {
        val emoji = pagedLayer() ?: return false
        emojiVariants = null
        forgetShownRecents()
        val pages = pagesFor(emoji)
        val index = pageIndex(emoji, pages)
        // Symbols and kaomoji each go round on their own: the last symbol page leads back to
        // the first symbol page (recents), not on into the kaomoji, and the kaomoji the same way
        val kaomoji = pages[index].kind == PageKind.KAOMOJI
        val group = pages.indices.filter { (pages[it].kind == PageKind.KAOMOJI) == kaomoji }
        val at = group.indexOf(index).coerceAtLeast(0)
        setPageIndex(emoji, group[(at + by + group.size) % group.size], pages)
        return true
    }

    /** Q (or its ‹ tapped): back from an emoji's variants, else the page before (round to the last). */
    fun previousPage(): Boolean = when {
        closeEmojiVariants() -> true
        pagedLayer() != null -> turnPage(-1)
        else -> previousKaomojiPage()
    }

    /** P (or its › tapped): the next page, the last going round to the first. */
    fun nextPage(): Boolean = if (pagedLayer() != null) turnPage(1) else nextKaomojiPage()

    /** The kaomoji key: with the symbols as pages, to the kaomoji pages; otherwise on through them. */
    fun kaomojiKeyPressed(): Boolean {
        if (pagedLayer() != false) return nextKaomojiPage()
        val pages = symbolPages()
        val first = pages.indexOfFirst { it.kind == PageKind.KAOMOJI }.takeIf { it >= 0 } ?: return false
        forgetShownRecents()
        setPageIndex(false, first, symbolPages())
        return true
    }

    /** A page turn: the recents pages read the recents again (until the next turn). */
    private fun forgetShownRecents() {
        emojiRecentsShown = null
        symbolRecentsShown = null
        kaomojiRecentsShown = null
    }

    /** Q on the kaomoji pages: the page before, the first going round to the last. */
    fun previousKaomojiPage(): Boolean {
        if (currentPageType() != SymPage.SYMBOLS || symbolsKaomojiPage < 0) return false
        val pages = Kaomoji.pages(context).size
        symbolsKaomojiPage = (symbolsKaomojiPage - 1 + pages) % pages
        return true
    }

    /** The kaomoji key: the kaomoji, then the next page of them, the last going round to the first. */
    fun nextKaomojiPage(): Boolean {
        if (currentPageType() != SymPage.SYMBOLS) return false
        symbolsShowRecents = false
        val pages = Kaomoji.pages(context).size
        if (pages == 0) return false
        symbolsKaomojiPage = (symbolsKaomojiPage + 1) % pages
        return true
    }

    private fun leavePage() {
        openedByEmojiKey = false
        emojiLayerShowsRecents = false
        symbolsShowRecents = false
        symbolsKaomojiPage = -1
        emojiPageIndex = -1
        symbolsPageIndex = -1
        emojiVariants = null
        forgetShownRecents()
        starterKeys = emptySet()
        pendingEmoji = null
        pendingEmojiKey = KeyEvent.KEYCODE_UNKNOWN
        variantsHeldKey = KeyEvent.KEYCODE_UNKNOWN
    }

    init {
        alignSymPageToConfig(SettingsManager.getSymPagesConfig(context))
    }

    fun currentSymPage(): Int {
        alignSymPageToConfig()
        return symPage
    }

    fun isSymActive(): Boolean = currentSymPage() > 0

    fun toggleSymPage(): Int {
        val config = SettingsManager.getSymPagesConfig(context)
        alignSymPageToConfig(config)
        val pages = buildActivePages(config)
        symPage = nextSymPageValue(pages)
        leavePage()
        persistSymPage()
        return symPage
    }

    fun peekNextSymPage(): Int {
        val config = SettingsManager.getSymPagesConfig(context)
        alignSymPageToConfig(config)
        return nextSymPageValue(buildActivePages(config))
    }

    fun closeSymPage(): Boolean {
        if (symPage == 0) {
            return false
        }
        symPage = 0
        leavePage()
        persistSymPage()
        return true
    }
    
    fun openClipboardPage(): Boolean {
        val clipboardPageValue = SymPage.CLIPBOARD.toPrefValue()
        
        // Toggle behavior: open if closed, close if already open
        // Always allow direct access to clipboard page, even if disabled in cycling settings
        if (symPage == clipboardPageValue) {
            closeSymPage()
            return false
        }
        symPage = clipboardPageValue
        leavePage()
        persistSymPage()
        return true
    }

    fun openEmojiPickerPage(): Boolean {
        val emojiPickerPageValue = SymPage.EMOJI_PICKER.toPrefValue()
        
        // Toggle behavior: open if closed, close if already open
        // Always allow direct access to emoji picker page
        if (symPage == emojiPickerPageValue) {
            closeSymPage()
            return false
        }
        symPage = emojiPickerPageValue
        leavePage()
        persistSymPage()
        return true
    }

    /** The emoji key: toggles the emoji picker, or the emoji layer when [layer]. */
    fun toggleEmojiKeyPage(layer: Boolean): Boolean {
        val target = (if (layer) SymPage.EMOJI else SymPage.EMOJI_PICKER).toPrefValue()
        if (symPage == target) {
            closeSymPage()
            return false
        }
        symPage = target
        leavePage()
        openedByEmojiKey = true
        persistSymPage()
        return true
    }

    /** The emoji layer's Recents key: recent emoji on the keys, or back to the layer. */
    fun toggleEmojiLayerRecents(): Boolean {
        when (currentPageType()) {
            SymPage.EMOJI -> emojiLayerShowsRecents = !emojiLayerShowsRecents
            SymPage.SYMBOLS -> {
                symbolsShowRecents = !symbolsShowRecents
                symbolsKaomojiPage = -1
            }
            else -> return false
        }
        return true
    }

    /**
     * The symbols page's keys, as the emoji layer's: its own symbols with the Recents and search
     * keys, or the recent symbols (most recent on Q, then along the rows) while Recents is shown.
     */
    private fun symbolsMappings(): Map<Int, String>? {
        if (symbolsPaged()) return pagedMappings(emoji = false)
        val base = alternateCharacterManager.getSymMappings2()
        val recentsKey = SettingsManager.getEmojiLayerRecentsKey(context)
        val kaomojiKey = kaomojiKey(context)
        if (kaomojiKey != KeyEvent.KEYCODE_UNKNOWN) {
            val kaomojiPages = Kaomoji.pages(context)
            val page = kaomojiPages.getOrNull(symbolsKaomojiPage)
            if (page != null) {
                // Kaomoji on every key, the kaomoji key going on to the next page (round to the first)
                val searchPage = symbolsKaomojiPage == Kaomoji.SEARCH_KEY_PAGE
                val keys = SettingsManager.EMOJI_LAYER_KEYS.filter {
                    it != kaomojiKey && it != KAOMOJI_PREVIOUS_KEY && !(searchPage && it == KAOMOJI_SEARCH_KEY)
                }
                val shown = keys.zip(page).toMap().toMutableMap()
                shown[KAOMOJI_PREVIOUS_KEY] = KAOMOJI_PREVIOUS_LABEL
                if (searchPage) shown[KAOMOJI_SEARCH_KEY] = SEARCH_KEY_LABEL
                shown[kaomojiKey] = KAOMOJI_NEXT_LABEL
                return shown
            }
        }
        val withKaomoji = if (kaomojiKey != KeyEvent.KEYCODE_UNKNOWN) {
            base?.toMutableMap()?.apply { put(kaomojiKey, KAOMOJI_KEY_LABEL) }
        } else base
        return symbolsMappingsWithoutKaomoji(withKaomoji, recentsKey, kaomojiKey)
    }

    private fun symbolsMappingsWithoutKaomoji(base: Map<Int, String>?, recentsKey: Int, kaomojiKey: Int): Map<Int, String>? {
        if (recentsKey == KeyEvent.KEYCODE_UNKNOWN) return withSearchKey(base)
        if (symbolsShowRecents) {
            // The recents run round the keys that keep their job (Recents, search, kaomoji),
            // topped up with the page's own symbols
            val searchKey = SettingsManager.getSearchKey(context)
            val keep = setOf(recentsKey, searchKey, kaomojiKey)
            val ownSymbols = base.orEmpty().filterKeys { it !in keep }.values.toList()
            val shown = recentsWithStarters(
                it.palsoftware.pastiera.data.symbols.SymbolSearch.recentSymbols(context), ownSymbols, keep
            )
            shown[recentsKey] = RECENTS_BACK_LABEL
            if (searchKey != KeyEvent.KEYCODE_UNKNOWN) shown[searchKey] = SEARCH_KEY_LABEL
            if (kaomojiKey != KeyEvent.KEYCODE_UNKNOWN) shown[kaomojiKey] = KAOMOJI_KEY_LABEL
            return shown
        }
        return withSearchKey(base)?.toMutableMap()?.apply { put(recentsKey, RECENTS_KEY_LABEL) }
    }

    /**
     * The emoji layer's keys: its own emoji, or the recent ones (most recent on Q, then along the
     * rows) while Recents is shown. The Recents key keeps its toggle label either way.
     */
    /** A symbols page's keys, with the search key showing its label. */
    private fun withSearchKey(mappings: Map<Int, String>?): Map<Int, String>? {
        val searchKey = SettingsManager.getSearchKey(context)
        if (mappings == null || searchKey == KeyEvent.KEYCODE_UNKNOWN) return mappings
        return mappings.toMutableMap().apply { put(searchKey, SEARCH_KEY_LABEL) }
    }

    private fun emojiLayerMappings(): Map<Int, String> {
        if (emojiPaged()) return pagedMappings(emoji = true)
        val base = alternateCharacterManager.getSymMappings()
        val recentsKey = SettingsManager.getEmojiLayerRecentsKey(context)
        val showingRecents = emojiLayerShowsRecents && recentsKey != KeyEvent.KEYCODE_UNKNOWN
        // The GIF and search keys keep their jobs, recent emoji shown or not
        val gifKey = SettingsManager.activeEmojiLayerGifKey(context)
        val searchKey = SettingsManager.getSearchKey(context)
        if (recentsKey == KeyEvent.KEYCODE_UNKNOWN && gifKey == KeyEvent.KEYCODE_UNKNOWN &&
            searchKey == KeyEvent.KEYCODE_UNKNOWN
        ) return base
        val shown = if (showingRecents) {
            // The recents run round the keys that keep their job, topped up with the layer's own
            val keep = setOf(recentsKey, gifKey, searchKey)
            recentsWithStarters(
                RecentEmojiManager.getRecentEmojis(context, SettingsManager.EMOJI_LAYER_KEYS.size),
                base.filterKeys { it !in keep }.values.toList(),
                keep
            )
        } else if (it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.enabled(context)) {
            // Recent emoji first: the ones you used last on the first free keys, the layer's
            // own moved along by as many
            val recents = it.palsoftware.pastiera.data.emoji.EmojiLayerRecents
            val keys = SettingsManager.EMOJI_LAYER_KEYS.filter { key -> key != recentsKey && key != gifKey && key != searchKey }
            recents.arrange(keys, base, recents.used(context), recents.count(context)).toMutableMap()
        } else {
            base.toMutableMap()
        }
        if (recentsKey != KeyEvent.KEYCODE_UNKNOWN) {
            shown[recentsKey] = if (emojiLayerShowsRecents) RECENTS_BACK_LABEL else RECENTS_KEY_LABEL
        }
        if (gifKey != KeyEvent.KEYCODE_UNKNOWN) shown[gifKey] = GIF_KEY_LABEL
        if (searchKey != KeyEvent.KEYCODE_UNKNOWN) shown[searchKey] = SEARCH_KEY_LABEL
        return shown
    }

    fun openEmojiPage(): Boolean {
        val emojiPageValue = SymPage.EMOJI.toPrefValue()

        if (symPage == emojiPageValue) {
            closeSymPage()
            return false
        }
        symPage = emojiPageValue
        leavePage()
        // Asked for directly (the corner button): the emoji layer opens whether or not it's in
        // the SYM cycle, as with the emoji key
        openedByEmojiKey = true
        persistSymPage()
        return true
    }

    fun openSymbolsPage(): Boolean {
        val symbolsPageValue = SymPage.SYMBOLS.toPrefValue()
        
        // Toggle behavior: open if closed, close if already open
        // Always allow direct access to symbols page, even if disabled in cycling settings
        if (symPage == symbolsPageValue) {
            closeSymPage()
            return false
        }
        symPage = symbolsPageValue
        leavePage()
        persistSymPage()
        return true
    }

    fun reset() {
        symPage = 0
        leavePage()
        persistSymPage()
    }

    fun restoreSymPageIfNeeded(onStatusBarUpdate: () -> Unit) {
        val restoreSymPage = SettingsManager.getRestoreSymPage(context)
        if (restoreSymPage > 0) {
            val config = SettingsManager.getSymPagesConfig(context)
            val pages = buildActivePages(config)
            val allowedValues = pages.map { it.toPrefValue() }
            symPage = when {
                restoreSymPage in allowedValues -> restoreSymPage
                allowedValues.isNotEmpty() -> allowedValues.first()
                else -> 0
            }
            persistSymPage()
            SettingsManager.clearRestoreSymPage(context)
            Handler(Looper.getMainLooper()).post {
                onStatusBarUpdate()
            }
        }
    }

    fun emojiMapText(): String {
        return if (currentPageType() == SymPage.EMOJI) alternateCharacterManager.buildEmojiMapText() else ""
    }

    fun currentSymMappings(): Map<Int, String>? {
        return when (currentPageType()) {
            SymPage.DEVICE -> withSearchKey(alternateCharacterManager.getDeviceSymMappings())
            SymPage.EMOJI -> emojiLayerMappings()
            SymPage.SYMBOLS -> symbolsMappings()
            SymPage.CLIPBOARD -> null // Clipboard doesn't use mappings
            SymPage.EMOJI_PICKER -> null // Emoji picker doesn't use mappings
            else -> null
        }
    }

    fun previewNextSoftwareSymPageMappings(shiftPressed: Boolean): Map<Int, String> {
        val nextTextPage = nextSoftwareTextPageType() ?: return emptyMap()
        return mappingsForPage(nextTextPage, shiftPressed)
    }

    fun nextSoftwareTextSymPage(): Int {
        return nextSoftwareTextPageType()?.toPrefValue() ?: 0
    }

    private fun nextSoftwareTextPageType(): SymPage? {
        val config = SettingsManager.getSymPagesConfig(context)
        alignSymPageToConfig(config)
        val pages = buildActivePages(config)
        if (pages.isEmpty()) {
            return null
        }
        val cycle = listOf(null) + pages
        val currentPage = currentPageType()
        val currentIndex = cycle.indexOf(currentPage).takeIf { it >= 0 } ?: 0
        return (1..cycle.size).asSequence()
            .map { offset -> cycle[(currentIndex + offset) % cycle.size] }
            .firstOrNull { it == SymPage.DEVICE || it == SymPage.EMOJI || it == SymPage.SYMBOLS }
    }

    private fun nextSymPageValue(pages: List<SymPage>): Int {
        val cycle = mutableListOf(0)
        cycle.addAll(pages.map { it.toPrefValue() })
        if (cycle.size <= 1) {
            return 0
        }
        val currentIndex = cycle.indexOf(symPage).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + 1) % cycle.size
        return cycle[nextIndex]
    }

    private fun mappingsForPage(pageToUse: SymPage, shiftPressed: Boolean): Map<Int, String> {
        return when (pageToUse) {
            SymPage.DEVICE -> alternateCharacterManager.getDeviceSymMappings()
            SymPage.EMOJI -> if (shiftPressed) {
                alternateCharacterManager.getSymMappings() + alternateCharacterManager.getSymMappingsUppercase()
            } else {
                alternateCharacterManager.getSymMappings()
            }
            SymPage.SYMBOLS -> if (shiftPressed) {
                alternateCharacterManager.getSymMappings2() + alternateCharacterManager.getSymMappings2Uppercase()
            } else {
                alternateCharacterManager.getSymMappings2()
            }
            else -> emptyMap()
        }
    }

    /**
     * Resolves the character for a physical SYM+key chord without opening
     * the visual SYM layout. If a text SYM page is already active, use it.
     * Otherwise use the first enabled text page in configured order.
     */
    /** What [keyCode] types on the emoji layer, for the emoji key held or tapped before it. */
    fun resolveEmojiLayerSymbol(keyCode: Int, shiftPressed: Boolean): String? =
        if (shiftPressed) {
            alternateCharacterManager.getSymMappingsUppercase()[keyCode] ?: alternateCharacterManager.getSymMappings()[keyCode]
        } else {
            alternateCharacterManager.getSymMappings()[keyCode]
        }

    fun resolveChordSymbol(keyCode: Int, shiftPressed: Boolean): String? {
        val pageToUse = when (currentPageType()) {
            SymPage.DEVICE, SymPage.EMOJI, SymPage.SYMBOLS -> currentPageType()
            else -> preferredChordPage()
        } ?: return null

        return when (pageToUse) {
            SymPage.DEVICE -> alternateCharacterManager.getDeviceSymMappings()[keyCode]
            SymPage.EMOJI -> {
                if (shiftPressed) {
                    alternateCharacterManager.getSymMappingsUppercase()[keyCode] ?: alternateCharacterManager.getSymMappings()[keyCode]
                } else {
                    alternateCharacterManager.getSymMappings()[keyCode]
                }
            }
            SymPage.SYMBOLS -> {
                if (shiftPressed) {
                    alternateCharacterManager.getSymMappings2Uppercase()[keyCode] ?: alternateCharacterManager.getSymMappings2()[keyCode]
                } else {
                    alternateCharacterManager.getSymMappings2()[keyCode]
                }
            }
            else -> null
        }
    }

    fun handleKeyWhenActive(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        ctrlLatchActive: Boolean,
        altLatchActive: Boolean,
        updateStatusBar: () -> Unit,
        handleBoundaryText: (String, InputConnection?) -> Boolean = { _, _ -> false }
    ): SymKeyResult {
        val page = currentPageType()
        // The emoji layer opened with the emoji key follows the emoji key's auto-close
        val autoCloseEnabled = if (page == SymPage.EMOJI && openedByEmojiKey) {
            SettingsManager.emojiScreenClosesAfterInput(
                context, isPicker = false, openedByEmojiKey = true, byTouch = false
            )
        } else when (page) {
            SymPage.EMOJI -> SettingsManager.getEmojiLayerCloseOnKey(context)
            SymPage.SYMBOLS -> SettingsManager.getSymbolsCloseOnKey(context)
            else -> SettingsManager.getSymAutoClose(context)
        }

        // A layer shown as pages: Q and P turn them, the recents page's buttons, and every other
        // letter types what its key holds on this page (and makes it a recent)
        val pagedEmoji = pagedLayer()
        if (pagedEmoji != null && event?.isAltPressed != true && event?.isCtrlPressed != true &&
            !altLatchActive && !ctrlLatchActive && keyCode in SettingsManager.EMOJI_LAYER_KEYS
        ) {
            val first = (event?.repeatCount ?: 0) == 0
            // Held past a press: the pressed emoji's variants on a page of their own
            if (!first && pendingEmoji != null && keyCode == pendingEmojiKey) {
                val emoji = pendingEmoji!!
                pendingEmoji = null
                pendingEmojiKey = KeyEvent.KEYCODE_UNKNOWN
                variantsHeldKey = keyCode
                if (showEmojiVariants(emoji)) updateStatusBar()
                return SymKeyResult.CONSUME
            }
            // The variants page: each key types its variant, Q goes back
            emojiVariants?.takeIf { pagedEmoji }?.let { variants ->
                if (!first) return SymKeyResult.CONSUME
                if (keyCode == PAGE_PREVIOUS_KEY) {
                    if (closeEmojiVariants()) updateStatusBar()
                    return SymKeyResult.CONSUME
                }
                val text = sequential(variants)[keyCode] ?: return SymKeyResult.CONSUME
                inputConnection?.commitText(text, 1)
                emojiTyped(text)
                emojiVariants = null
                if (autoCloseEnabled) closeSymAndUpdate(updateStatusBar) else updateStatusBar()
                return SymKeyResult.CONSUME
            }
            val pages = pagesFor(pagedEmoji)
            val current = pages[pageIndex(pagedEmoji, pages)]
            when {
                keyCode == PAGE_PREVIOUS_KEY -> if (first && previousPage()) updateStatusBar()
                keyCode == PAGE_NEXT_KEY -> if (first && nextPage()) updateStatusBar()
                keyCode == PAGE_SEARCH_KEY -> if (first) onSearchKey?.invoke(
                    when {
                        current.kind == PageKind.KAOMOJI -> SearchTarget.KAOMOJI
                        pagedEmoji -> SearchTarget.EMOJI_LAYER
                        else -> SearchTarget.SYMBOLS
                    }
                )
                current.kind == PageKind.RECENTS && keyCode == PAGE_EXTRA_KEY && !pagedEmoji ->
                    if (first && kaomojiKeyPressed()) updateStatusBar()
                // Back from the kaomoji's first page to the symbols' first
                !pagedEmoji && keyCode == PAGE_EXTRA_KEY &&
                    pages.indexOfFirst { it.kind == PageKind.KAOMOJI } == pages.indexOf(current) -> if (first) {
                    forgetShownRecents()
                    setPageIndex(false, 0, symbolPages())
                    updateStatusBar()
                }
                current.kind == PageKind.RECENTS && keyCode == PAGE_EXTRA_KEY && SettingsManager.gifsAvailable(context) ->
                    if (first) onEmojiLayerGifKey?.invoke()
                else -> {
                    val text = current.keys[keyCode] ?: return SymKeyResult.CONSUME
                    if (!first) return SymKeyResult.CONSUME
                    // An emoji with skin tones: typed when the key comes up, its tones if it's held
                    if (pagedEmoji && variantsOf(text).size > 1) {
                        pendingEmoji = text
                        pendingEmojiKey = keyCode
                        pendingEmojiCloses = autoCloseEnabled
                        return SymKeyResult.CONSUME
                    }
                    if (inputConnection == null) return SymKeyResult.CONSUME
                    if (current.kind == PageKind.KAOMOJI) {
                        inputConnection.commitText(text, 1)
                        Kaomoji.addRecent(context, text)
                    } else {
                        if (
                            !handleBoundaryText(text, inputConnection) &&
                            (
                                text.length != 1 ||
                                    !SettingsManager.shouldApplyFrenchPunctuationSpacing(context) ||
                                    !it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, text[0])
                            )
                        ) {
                            inputConnection.commitText(text, 1)
                        }
                        if (pagedEmoji) {
                            emojiTyped(text)
                        } else {
                            it.palsoftware.pastiera.data.symbols.SymbolSearch.addRecent(context, text)
                        }
                    }
                    val close = if (current.kind == PageKind.KAOMOJI) SettingsManager.getKaomojiCloseOnKey(context) else autoCloseEnabled
                    if (close) closeSymAndUpdate(updateStatusBar)
                }
            }
            return SymKeyResult.CONSUME
        }

        // The GIF key, recent emoji shown or not
        val gifKey = SettingsManager.activeEmojiLayerGifKey(context)
        if (page == SymPage.EMOJI && gifKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == gifKey) {
            if ((event?.repeatCount ?: 0) == 0) onEmojiLayerGifKey?.invoke()
            return SymKeyResult.CONSUME
        }
        // The kaomoji key, and the kaomoji on the keys while they're shown
        val kaomojiKey = kaomojiKey(context)
        if (page == SymPage.SYMBOLS && kaomojiKey != KeyEvent.KEYCODE_UNKNOWN && !symbolsPaged() &&
            event?.isAltPressed != true && event?.isCtrlPressed != true && !altLatchActive && !ctrlLatchActive
        ) {
            if (keyCode == kaomojiKey) {
                if ((event?.repeatCount ?: 0) == 0 && nextKaomojiPage()) updateStatusBar()
                return SymKeyResult.CONSUME
            }
            if (symbolsKaomojiPage >= 0 && keyCode == KAOMOJI_PREVIOUS_KEY) {
                if ((event?.repeatCount ?: 0) == 0 && previousKaomojiPage()) updateStatusBar()
                return SymKeyResult.CONSUME
            }
            if (symbolsKaomojiPage == Kaomoji.SEARCH_KEY_PAGE && keyCode == KAOMOJI_SEARCH_KEY) {
                if ((event?.repeatCount ?: 0) == 0) onSearchKey?.invoke(SearchTarget.KAOMOJI)
                return SymKeyResult.CONSUME
            }
            if (symbolsKaomojiPage >= 0) {
                val kaomoji = symbolsMappings()?.get(keyCode) ?: return SymKeyResult.CONSUME
                if ((event?.repeatCount ?: 0) == 0) {
                    inputConnection?.commitText(kaomoji, 1)
                    Kaomoji.addRecent(context, kaomoji)
                }
                if (SettingsManager.getKaomojiCloseOnKey(context)) closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.CONSUME
            }
        }
        val recentsKey = SettingsManager.getEmojiLayerRecentsKey(context)
        if ((page == SymPage.EMOJI || page == SymPage.SYMBOLS) && recentsKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == recentsKey) {
            if ((event?.repeatCount ?: 0) == 0 && toggleEmojiLayerRecents()) {
                updateStatusBar()
            }
            return SymKeyResult.CONSUME
        }

        // The search key: that screen's search (the picker's only reaches here when its search
        // isn't taking typing)
        val searchKey = SettingsManager.getSearchKey(context)
        if (searchKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == searchKey &&
            event?.isAltPressed != true && event?.isCtrlPressed != true && !altLatchActive && !ctrlLatchActive
        ) {
            val target = when (page) {
                SymPage.EMOJI -> SearchTarget.EMOJI_LAYER
                SymPage.SYMBOLS -> SearchTarget.SYMBOLS
                SymPage.DEVICE -> SearchTarget.SYMBOLS
                SymPage.EMOJI_PICKER -> SearchTarget.PICKER
                else -> null
            }
            if (target != null) {
                if ((event?.repeatCount ?: 0) == 0) onSearchKey?.invoke(target)
                return SymKeyResult.CONSUME
            }
        }

        // Type to search (its settings): a plain letter starts emoji or symbol search with it
        val typeToSearch = when (page) {
            // Not while the keys hold recents: a letter types the recent on it
            SymPage.EMOJI -> !emojiLayerShowsRecents && SettingsManager.getEmojiLayerTypeToSearch(context)
            SymPage.SYMBOLS -> !symbolsShowRecents && SettingsManager.getSymbolsTypeToSearch(context)
            SymPage.DEVICE -> SettingsManager.getSymbolsTypeToSearch(context)
            else -> false
        }
        if (typeToSearch && event != null && keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z &&
            !event.isAltPressed && !event.isCtrlPressed && !altLatchActive && !ctrlLatchActive
        ) {
            if (event.repeatCount == 0) {
                val typed = event.unicodeChar.takeIf { it > 0 }?.toChar() ?: ('a' + (keyCode - KeyEvent.KEYCODE_A))
                onTypeToSearch?.invoke(page == SymPage.EMOJI, typed.toString())
            }
            return SymKeyResult.CONSUME
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.CALL_SUPER
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (autoCloseEnabled) {
                    closeSymAndUpdate(updateStatusBar)
                    return SymKeyResult.CALL_SUPER
                }
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.NOT_HANDLED
            }
        }

        val symChar = when (page) {
            SymPage.DEVICE -> alternateCharacterManager.getDeviceSymMappings()[keyCode]
            SymPage.EMOJI -> emojiLayerMappings()[keyCode]
            SymPage.SYMBOLS -> symbolsMappings()?.get(keyCode)
            SymPage.CLIPBOARD -> null // Clipboard doesn't use key mappings
            SymPage.EMOJI_PICKER -> null // Emoji picker doesn't use key mappings
            else -> null
        }

        if (symChar != null && inputConnection != null) {
            if (
                !handleBoundaryText(symChar, inputConnection) &&
                (
                    symChar.length != 1 ||
                        !SettingsManager.shouldApplyFrenchPunctuationSpacing(context) ||
                        !it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, symChar[0])
                )
            ) {
                inputConnection.commitText(symChar, 1)
            }
            // Symbols typed from the symbols page are its recents; emoji from the layer count as used
            if (page == SymPage.SYMBOLS || page == SymPage.DEVICE) it.palsoftware.pastiera.data.symbols.SymbolSearch.addRecent(context, symChar)
            if (page == SymPage.EMOJI) it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.markUsed(context, symChar)
            if (autoCloseEnabled) {
                closeSymAndUpdate(updateStatusBar)
            }
            return SymKeyResult.CONSUME
        }

        return SymKeyResult.NOT_HANDLED
    }

    fun handleKeyUp(keyCode: Int, shiftPressed: Boolean): Boolean {
        return alternateCharacterManager.handleKeyUp(keyCode, isSymActive(), shiftPressed)
    }

    fun emojiMapTextForLayout(): String = alternateCharacterManager.buildEmojiMapText()

    private fun closeSymAndUpdate(updateStatusBar: () -> Unit) {
        if (closeSymPage()) {
            updateStatusBar()
        }
    }

    private fun buildActivePages(config: SymPagesConfig = SettingsManager.getSymPagesConfig(context)): List<SymPage> {
        return config.enabledOrderedPages().mapNotNull { pageId ->
            when (pageId) {
                SymPagesConfig.PAGE_DEVICE -> SymPage.DEVICE
                SymPagesConfig.PAGE_EMOJI -> SymPage.EMOJI
                SymPagesConfig.PAGE_SYMBOLS -> SymPage.SYMBOLS
                SymPagesConfig.PAGE_CLIPBOARD -> SymPage.CLIPBOARD
                SymPagesConfig.PAGE_EMOJI_PICKER -> SymPage.EMOJI_PICKER
                else -> null
            }
        }
    }

    private fun preferredChordPage(config: SymPagesConfig = SettingsManager.getSymPagesConfig(context)): SymPage? {
        return buildActivePages(config).firstOrNull {
            it == SymPage.DEVICE || it == SymPage.EMOJI || it == SymPage.SYMBOLS
        }
    }

    private fun currentPageType(): SymPage? {
        alignSymPageToConfig()
        return when (symPage) {
            5 -> SymPage.DEVICE
            1 -> SymPage.EMOJI
            2 -> SymPage.SYMBOLS
            3 -> SymPage.CLIPBOARD
            4 -> SymPage.EMOJI_PICKER
            else -> null
        }
    }

    private fun SymPage.toPrefValue(): Int = when (this) {
        SymPage.DEVICE -> 5
        SymPage.EMOJI -> 1
        SymPage.SYMBOLS -> 2
        SymPage.CLIPBOARD -> 3
        SymPage.EMOJI_PICKER -> 4
    }

    private fun alignSymPageToConfig(config: SymPagesConfig = SettingsManager.getSymPagesConfig(context)) {
        val allowedValues = buildActivePages(config).map { it.toPrefValue() }
        // The emoji layer opened with the emoji key stays, even when it isn't in the Sym cycle
        if (openedByEmojiKey && symPage == SymPage.EMOJI.toPrefValue()) return
        if (allowedValues.isEmpty()) {
            if (symPage != 0 && symPage !in 2..5) {
                // Allow symbols page (2), clipboard page (3) and emoji picker page (4) even if all cycling pages are disabled
                symPage = 0
                persistSymPage()
            }
            return
        }

        if (symPage == 0) {
            return
        }

        // Allow symbols page (2), clipboard page (3) and emoji picker page (4) to remain active even if disabled in cycling settings
        if (symPage in 2..5) {
            return
        }

        if (symPage !in allowedValues) {
            symPage = allowedValues.first()
            persistSymPage()
        }
    }

    private fun persistSymPage() {
        prefs.edit().putInt(PREF_CURRENT_SYM_PAGE, symPage).apply()
    }

}
