package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.TextView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.emoji.EmojiRepository
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.data.emoji.EmojiSearchRepository
import it.palsoftware.pastiera.data.emoji.EmojiCompatSupport
import it.palsoftware.pastiera.data.gif.GifCollections
import it.palsoftware.pastiera.data.gif.GifResult
import it.palsoftware.pastiera.data.gif.KlipyGifs
import it.palsoftware.pastiera.data.symbols.Kaomoji
import it.palsoftware.pastiera.data.symbols.SymbolSearch
import java.nio.ByteBuffer
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.palsoftware.pastiera.emojiScreenClosesAfterInput
import it.palsoftware.pastiera.getEmojiPickerExpandedHeight
import it.palsoftware.pastiera.getEmojiSearchEnterPicks
import it.palsoftware.pastiera.getGifFocusSearch
import it.palsoftware.pastiera.getGifSearchEnterPicks
import it.palsoftware.pastiera.getGifShowFavourites
import it.palsoftware.pastiera.getGifShowRecents
import it.palsoftware.pastiera.getKaomojiCloseOnTap
import it.palsoftware.pastiera.getKlipyApiKey
import it.palsoftware.pastiera.getRecentsFirstInSearch
import it.palsoftware.pastiera.getSymAutoClose
import it.palsoftware.pastiera.getSymAutoCloseOnTouch
import it.palsoftware.pastiera.getSymbolSearchEnterPicks
import it.palsoftware.pastiera.getTitan2EliteStraightOuterButtons
import it.palsoftware.pastiera.gifsAvailable

/**
 * Emoji picker view: single vertical list with section headers and bottom tabs.
 */
class EmojiPickerView(
    context: Context,
    private val onCloseRequested: (() -> Unit)? = null
) : FrameLayout(context) {

    private var currentInputConnection: InputConnection? = null
    private val recyclerView: RecyclerView
    private val searchField: EditText
    private val loadingView: ProgressBar
    private val emptyView: TextView
    private val tabRow: LinearLayout
    private val vertical: LinearLayout
    private val keyboardSwitcherButton: ImageView
    private val searchPanel: FrameLayout
    private val searchToggleButton: ImageView
    private val closeButton: ImageView
    private var roundedControls = false
    private var roundedIconSize = 0f
    val edgeControls: Pair<View, View> get() = searchToggleButton to closeButton

    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadingJob: Job? = null

    private val compactHeight = dpToPx(COMPACT_HEIGHT_DP)
    private val emojiSize = dpToPx(48f)
    private val spacing = dpToPx(4f)
    private val smallPadding = dpToPx(8f)
    private val recentsApplyTopThreshold = 0

    // Data for sections
    private var sectionItems: List<SectionItem> = emptyList()
    private var itemCategoryIds: List<String> = emptyList()
    private var headerPositions: Map<String, Int> = emptyMap()
    private var selectedCategoryId: String? = null
    private var isTabClickScroll = false
    private var pendingRecentsRefresh = false
    private var pendingRecentsRefreshRequiresTop = false
    private var pendingRecentsRefreshRequiresNotRecents = false
    private var scrollState = RecyclerView.SCROLL_STATE_IDLE

    // Adapter
    private val sectionAdapter: SectionAdapter
    private val searchAdapter: SearchAdapter
    private val columns: Int
    private var regularCategories: List<EmojiRepository.EmojiCategory> = emptyList()
    private var searchIndex: EmojiSearchRepository.EmojiSearchIndex? = null
    // Emoji beyond the system font that the current field renders via EmojiCompat (null = none)
    private var extraAvailable: ((String) -> Boolean)? = null
    private var loadedCompatGeneration: Int = -1
    // Where the search field lives when not in the picker's own panel (e.g. the Pastierina bar)
    private var searchFieldHost: ViewGroup? = null
    private var searchQuery: String = ""
    /** Where a tab asked the grouped list to start, once it's shown. */
    private var pendingGroupJump: Int? = null
    /** The emoji categories the emoji tabs were last built from. */
    private var emojiTabCategories: List<EmojiRepository.EmojiCategory> = emptyList()
    private var searchJob: Job? = null

    // GIF mode (KLIPY): its own grid over the emoji grid, and a tab in the bottom bar
    private var gifMode: Boolean = false
    private var gifJob: Job? = null
    private val gifAdapter = GifAdapter()
    // GIF section headers span the grid's width
    private val gifSpans = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int = if (gifAdapter.isHeader(position)) GIF_COLUMNS else 1
    }
    private val gifTabButton: TextView
    private val gifAttribution: TextView

    /** A GIF was tapped in GIF mode; the input method sends it. */
    var onGifChosen: ((GifResult) -> Unit)? = null

    // Symbol mode: search every Unicode symbol by name (from the SYM symbols pages)
    private var symbolMode: Boolean = false
    /** Symbol search is searching the kaomoji (from the kaomoji pages' search key). */
    private var kaomojiSearch: Boolean = false
    private var symbolJob: Job? = null
    private val symbolAdapter = SymbolAdapter()
    private var isSearchMode: Boolean = false
    private var isSearchPanelVisible: Boolean = false
    private var searchInputCaptureEnabled: Boolean = true
    private var containerReordering: Boolean = false
    private var pendingSearchReplacementRange: IntRange? = null
    private var tabCategoryIds: List<String> = emptyList()
    private var lastSearchResults: List<EmojiSearchRepository.EmojiSearchResult> = emptyList()
    var onSearchPanelVisibilityChanged: ((Boolean) -> Unit)? = null
    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyTheme()
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)

        // Calculate columns based on screen width
        val screenWidth = context.resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - smallPadding * 2
        columns = ((availableWidth + spacing) / (emojiSize + spacing)).coerceAtLeast(4).coerceAtMost(10)

        // Layout container: vertical stack (recycler + bottom tabs)
        vertical = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, compactHeight)
        }

        searchField = EditText(context).apply {
            hint = context.getString(R.string.emoji_picker_search_placeholder)
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = createSearchFieldBackground()
            val padH = dpToPx(8f)
            val padV = dpToPx(5f)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, smallPadding, smallPadding, 0)
            }
            showSoftInputOnFocus = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val newQuery = s?.toString().orEmpty()
                    if (newQuery == searchQuery) return
                    searchQuery = newQuery
                    scheduleSearch()
                }
            })
            setOnClickListener {
                setSearchInputCaptureEnabled(!searchInputCaptureEnabled)
            }
        }
        setSearchInputCaptureEnabled(false)

        searchPanel = FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(themeOverride?.background ?: Color.rgb(24, 24, 24))
            val panelPadding = dpToPx(6f)
            setPadding(panelPadding, panelPadding, panelPadding, panelPadding)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            addView(searchField, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }

        closeButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.close)
            background = createCloseButtonBackground()
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(32f))
            setOnClickListener {
                onCloseRequested?.invoke()
            }
        }

        // RecyclerView with headers and emoji grid
        recyclerView = RecyclerView(context).apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setHasFixedSize(false)
            clipToPadding = false
            setPadding(smallPadding, smallPadding, smallPadding, smallPadding + dpToPx(44f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Recents updates must appear silently ("as if the emoji was always there"),
            // so disable insert/change animations entirely.
            itemAnimator = null
        }

        val gridLayoutManager = GridLayoutManager(context, columns, RecyclerView.VERTICAL, false)
        sectionAdapter = SectionAdapter(columns)
        searchAdapter = SearchAdapter()
        gridLayoutManager.spanSizeLookup = sectionAdapter.spanSizeLookup
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = sectionAdapter

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                when (parent.adapter?.getItemViewType(pos) ?: return) {
                    VIEW_TYPE_HEADER -> {
                        outRect.set(0, spacing, 0, spacing)
                    }
                    VIEW_TYPE_EMOJI -> {
                        val layoutParams = view.layoutParams as? GridLayoutManager.LayoutParams
                        val column = layoutParams?.spanIndex ?: 0
                        outRect.left = if (column == 0) 0 else spacing / 2
                        outRect.right = if (column == columns - 1) 0 else spacing / 2
                        outRect.top = spacing / 2
                        outRect.bottom = spacing / 2
                    }
                }
            }
        })

        // Scroll listener to sync tabs and apply pending recents updates
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                scrollState = newState
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isTabClickScroll = false
                    maybeApplyPendingRecentsRefresh()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isSearchMode || gifMode || symbolMode) return
                if (isTabClickScroll) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return
                val categoryId = itemCategoryIds.getOrNull(firstVisible) ?: return
                if (categoryId != selectedCategoryId) {
                    selectedCategoryId = categoryId
                    updateTabsSelection()
                }
            }
        })

        // Loading and empty views (overlay)
        loadingView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.VISIBLE
        }
        emptyView = TextView(context).apply {
            text = context.getString(R.string.emoji_picker_error)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        // Tabs at bottom (above LEDs) - full width, no scroll
        val tabHeight = dpToPx(32f) // Height cap
        searchToggleButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_search_24)
            contentDescription = context.getString(R.string.emoji_picker_search_label)
            background = createTabBackground(false)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(tabHeight, tabHeight).apply {
                marginEnd = spacing
            }
            setOnClickListener {
                if (searchFieldHost != null) {
                    // Field is always visible in the host: switch typing between it and the app
                    setSearchInputCaptureEnabled(!searchInputCaptureEnabled)
                    if (searchInputCaptureEnabled) searchField.requestFocus()
                } else {
                    setSearchPanelVisible(!isSearchPanelVisible)
                }
            }
        }
        gifTabButton = TextView(context).apply {
            text = context.getString(R.string.gif_tab)
            contentDescription = context.getString(R.string.gif_tab_description)
            gravity = Gravity.CENTER
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            background = createTabBackground(false)
            isClickable = true
            isFocusable = true
            visibility = if (SettingsManager.gifsAvailable(context)) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(dpToPx(40f), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginEnd = spacing
            }
            setOnClickListener { if (gifMode) setGifMode(false) else openGifs() }
        }
        // KLIPY asks for attribution where its content is shown
        gifAttribution = TextView(context).apply {
            text = context.getString(R.string.gif_attribution)
            textSize = 10f
            alpha = 0.7f
            visibility = View.GONE
            setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
            // A header line above the GIFs, not over them
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(smallPadding / 2, 0, smallPadding / 2, 0)
        }
        keyboardSwitcherButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.close)
            background = createTabBackground(false)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(tabHeight, tabHeight).apply {
                marginStart = spacing
            }
        }

        vertical.addView(gifAttribution)
        vertical.addView(
            FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                // Emoji, GIF and symbol results all show in this one grid
                addView(recyclerView)
                // Keep empty/error states inside the result area. A root-level MATCH_PARENT
                // overlay would hide the search field and bottom controls when no emoji matches.
                addView(emptyView)
                addView(searchPanel)
            }
        )
        vertical.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    tabHeight
                )
                // Drawn above the results (emoji, GIF, symbol and kaomoji searches), so nothing
                // scrolled or laid out under it covers its buttons; no shadow
                translationZ = dpToPx(2f).toFloat()
                outlineProvider = null
                addView(searchToggleButton)
                addView(gifTabButton)
                addView(keyboardSwitcherButton)
                // The tabs straight in the bar: ten always fit, and inside a scrolling container
                // the phone kept drawing a stale (empty) picture of them
                addView(tabRow, LinearLayout.LayoutParams(0, tabHeight, 1f))
                addView(closeButton)
            }
        )

        addView(vertical)
        addView(loadingView)

        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            compactHeight
        )

        applyTheme()
        loadCategories()
    }

    fun setInputConnection(connection: InputConnection?) {
        currentInputConnection = connection
    }

    fun configureRoundedControls(enabled: Boolean, rowHeight: Int, iconSize: Float, edgeButtonWidth: Int = 0) {
        roundedControls = enabled
        roundedIconSize = iconSize
        // Reserve the slot; rounded mode uses the chrome's shared SYM close control.
        closeButton.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        val height = if (enabled) rowHeight else dpToPx(32f)
        val bar = closeButton.parent as LinearLayout
        bar.layoutParams = bar.layoutParams.apply { this.height = height }
        tabRow.layoutParams = tabRow.layoutParams.apply { this.height = height }
        tabRow.setPadding(smallPadding / 2, 0, smallPadding / 2, 0)
        for (index in 0 until tabRow.childCount) {
            val category = tabRow.getChildAt(index)
            category.layoutParams = category.layoutParams.apply {
                this.height = if (enabled) ViewGroup.LayoutParams.MATCH_PARENT else dpToPx(32f)
            }
            (category as? ImageView)?.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        fun sizeControls(width: Int) {
            listOf(searchToggleButton, closeButton).forEach { button ->
                button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                    // The same width as the bar's side buttons, so the corners match every panel
                    this.width = if (enabled) {
                        edgeButtonWidth.takeIf { it > 0 }
                            ?: if (button === closeButton) width - (width / 10) * 9 else width / 10
                    } else dpToPx(if (button === closeButton) 36f else 32f)
                    this.height = height
                    marginEnd = if (button === searchToggleButton) spacing else 0
                }
            }
            applyEdgeControlAppearance()
        }
        sizeControls(width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels)
        requestLayout()
    }

    private fun applyEdgeControlAppearance() {
        listOf(searchToggleButton, closeButton).forEach { button ->
            if (roundedControls) {
                button.background = if (button === closeButton) createCloseButtonBackground() else createTabBackground(isSearchPanelVisible)
                if (roundedControls) button.background = android.graphics.drawable.InsetDrawable(
                    // Out to the display's side like every panel's corner keys; only the bottom inset
                    button.background, 0, 0, 0, dpToPx(3f)
                )
                button.setPadding(0, 0, 0, 0)
                button.scaleType = ImageView.ScaleType.MATRIX
                button.drawable?.let { icon ->
                    val scale = roundedIconSize / icon.intrinsicHeight.coerceAtLeast(1)
                    button.imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        // Nudged away from the display curve, unless the buttons are straight
                        val dodge = if (SettingsManager.getTitan2EliteStraightOuterButtons(context)) 0 else dpToPx(8f)
                        postTranslate(
                            (button.layoutParams.width - icon.intrinsicWidth * scale) / 2f +
                                dodge * if (button === searchToggleButton) 1 else -1,
                            (button.layoutParams.height - icon.intrinsicHeight * scale) / 2f - dpToPx(2f)
                        )
                    }
                }
            } else {
                val pad = dpToPx(4f)
                button.setPadding(pad, pad, pad, pad)
                button.scaleType = ImageView.ScaleType.CENTER_INSIDE
                button.background = if (button === closeButton) createCloseButtonBackground() else createTabBackground(isSearchPanelVisible)
                if (roundedControls) button.background = android.graphics.drawable.InsetDrawable(
                    // Out to the display's side like every panel's corner keys; only the bottom inset
                    button.background, 0, 0, 0, dpToPx(3f)
                )
            }
        }
    }

    fun configureSoftwareKeyboardMode(heightPx: Int?, onKeyboardLayoutRequested: (() -> Unit)?) {
        val configuredHeight = configuredHeightPx(context)
        val targetHeight = heightPx?.takeIf { it > 0 } ?: configuredHeight
        updateHeight(targetHeight)
        keyboardSwitcherButton.visibility = if (onKeyboardLayoutRequested != null) View.VISIBLE else View.GONE
        keyboardSwitcherButton.setOnClickListener {
            onKeyboardLayoutRequested?.invoke()
        }
    }

    private fun updateHeight(heightPx: Int) {
        (layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)).also {
            it.height = heightPx
            layoutParams = it
        }
        (vertical.layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)).also {
            it.height = heightPx
            vertical.layoutParams = it
        }
    }

    /**
     * Reloads the emoji data. [resetModes] leaves GIF or symbol search (a fresh opening); a
     * refresh because the field or the emoji font changed keeps them.
     */
    fun refresh(resetModes: Boolean = true) {
        log("refresh(resetModes=$resetModes) gif=$gifMode symbol=$symbolMode")
        refreshGifAvailability()
        if (resetModes) {
            if (gifMode) setGifMode(false)
            if (symbolMode) setSymbolMode(false)
        }
        loadCategories()
    }

    /** True when the focused field (or the downloaded emoji font) changed since the last load. */
    fun isStaleForCurrentEditor(): Boolean = loadedCompatGeneration != EmojiCompatSupport.generation

    private fun isDisplayable(emoji: String): Boolean =
        EmojiRepository.isSystemAvailable(emoji) || extraAvailable?.invoke(emoji) == true

    /** Recents can hold emoji picked in a field that supports more than the current one. */
    private fun displayableRecents(
        category: EmojiRepository.EmojiCategory?
    ): EmojiRepository.EmojiCategory? {
        category ?: return null
        val entries = category.emojis.mapNotNull { EmojiRepository.filterEntry(it, ::isDisplayable) }
        return if (entries.isEmpty()) null else category.copy(emojis = entries)
    }

    fun isSearchInputActive(): Boolean {
        return isSearchPanelVisible && searchInputCaptureEnabled
    }

    fun isSearchPanelShowing(): Boolean {
        return isSearchPanelVisible
    }

    /**
     * Routes on-screen keyboard text input into the emoji search field while the
     * search input capture is active (analogous to the hardware key path).
     */
    fun handleSearchTextInput(text: String): Boolean {
        if (!isSearchInputActive()) return false
        if (text.isNotEmpty()) {
            appendSearchText(text)
        }
        return true
    }

    /**
     * Routes on-screen keyboard backspace into the emoji search field while the
     * search input capture is active.
     */
    fun handleSearchBackspace(): Boolean {
        if (!isSearchInputActive()) return false
        deleteSearchTextBackwards()
        return true
    }

    /**
     * Commits the top emoji search result and closes the picker.
     * Stays neutral when the search input capture is inactive or there are no results.
     */
    // Something was picked since the search last changed: Enter then only closes (it's "done",
    // not "add the first result again")
    private var pickedSinceSearchChange = false

    /**
     * Enter in the search, per its setting: picks the first emoji or symbol (or sends the first
     * GIF) and closes; after a pick, or with no symbol/GIF results, it only closes. Emoji search
     * without results stays neutral.
     */
    fun commitTopSearchResultAndClose() {
        if (!isSearchInputActive()) return
        when {
            gifMode -> {
                if (!SettingsManager.getGifSearchEnterPicks(context)) return
                val top = gifAdapter.first()
                if (top != null) onGifChosen?.invoke(top) else onCloseRequested?.invoke()
            }
            symbolMode -> {
                if (!SettingsManager.getSymbolSearchEnterPicks(context)) return
                // Nothing typed: the list is just the groups to browse, so Enter picks nothing
                val top = symbolAdapter.first()?.takeIf { searchQuery.isNotBlank() }
                if (!pickedSinceSearchChange && top != null) {
                    currentInputConnection?.commitText(top.symbol, 1)
                    if (kaomojiSearch) Kaomoji.addRecent(context, top.symbol) else SymbolSearch.addRecent(context, top.symbol)
                }
                onCloseRequested?.invoke()
            }
            else -> {
                if (!SettingsManager.getEmojiSearchEnterPicks(context)) return
                if (pickedSinceSearchChange) {
                    onCloseRequested?.invoke()
                    return
                }
                val top = lastSearchResults.firstOrNull() ?: return
                onEmojiSelected(
                    it.palsoftware.pastiera.data.emoji.EmojiSkinTone.preferred(context, top.entry),
                    top.categoryId,
                    closeAfterCommit = true
                )
            }
        }
    }

    private fun deleteSearchTextBackwards() {
        val text = searchField.text ?: return
        if (text.isEmpty()) return
        val replacementRange = selectedSearchRange(text.length)
        val start = replacementRange?.first
            ?: minOf(searchField.selectionStart, searchField.selectionEnd).coerceAtLeast(0)
        val end = replacementRange?.last?.plus(1)
            ?: maxOf(searchField.selectionStart, searchField.selectionEnd).coerceAtMost(text.length)
        if (start < end) {
            text.delete(start, end)
            pendingSearchReplacementRange = null
        } else {
            val cursor = searchField.selectionStart.coerceIn(0, text.length)
            if (cursor > 0) {
                text.delete(cursor - 1, cursor)
            }
        }
    }

    fun createSearchInputConnection(): InputConnection? {
        if (!isSearchInputActive()) return null
        focusSearchField()
        val baseConnection = searchField.onCreateInputConnection(EditorInfo()) ?: return null
        return object : InputConnectionWrapper(baseConnection, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return handleSearchInputConnectionKeyEvent(event) || super.sendKeyEvent(event)
            }

            override fun performContextMenuAction(id: Int): Boolean {
                return searchField.onTextContextMenuItem(id) || super.performContextMenuAction(id)
            }
        }
    }

    /**
     * IME hardware keys do not automatically target this EditText.
     * Handle printable keys manually while emoji picker page is open.
     */
    fun handleSearchKeyDown(
        event: KeyEvent,
        ctrlActive: Boolean = event.isCtrlPressed,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (event.isAltPressed || event.isMetaPressed) return false
        if (ctrlActive) {
            focusSearchField()
            if (handleTextEditingCtrlShortcut(event.keyCode)) {
                return true
            }
            val ctrlEvent = event.withCtrlMeta()
            return searchField.onKeyShortcut(ctrlEvent.keyCode, ctrlEvent) ||
                searchField.dispatchKeyEvent(ctrlEvent)
        }
        focusSearchField()

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                deleteSearchTextBackwards()
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                appendSearchText(" ")
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                commitTopSearchResultAndClose()
                true
            }
            else -> {
                val typedText = resolveTypedText?.invoke(event) ?: run {
                    val unicode = event.unicodeChar
                    if (unicode <= 0) {
                        return searchField.dispatchKeyEvent(event)
                    }
                    val ch = unicode.toChar()
                    if (Character.isISOControl(ch)) {
                        return searchField.dispatchKeyEvent(event)
                    }
                    ch.toString()
                }
                appendSearchText(typedText)
                true
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (event.isAltPressed || event.isMetaPressed) return false
        if (event.isCtrlPressed) {
            focusSearchField()
            return searchField.onKeyShortcut(event.keyCode, event) ||
                searchField.dispatchKeyEvent(event) ||
                isTextEditingCtrlShortcut(event.keyCode)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> {
                val unicode = event.unicodeChar
                unicode > 0 && !Character.isISOControl(unicode.toChar())
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent, ctrlActive: Boolean): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (ctrlActive) {
            return true
        }
        return shouldConsumeSearchKeyUp(event)
    }

    private fun handleSearchInputConnectionKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_PAGE_UP ||
                event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        }

        if (event.isCtrlPressed && handleTextEditingCtrlShortcut(event.keyCode)) {
            return true
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveSearchCursorBy(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveSearchCursorBy(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                setSearchSelection(0)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                setSearchSelection(searchField.text?.length ?: 0)
                true
            }
            else -> false
        }
    }

    private fun moveSearchCursorBy(delta: Int) {
        val text = searchField.text ?: return
        if (text.isEmpty()) {
            setSearchSelection(0)
            return
        }
        val anchor = if (delta < 0) {
            minOf(searchField.selectionStart, searchField.selectionEnd)
        } else {
            maxOf(searchField.selectionStart, searchField.selectionEnd)
        }.coerceIn(0, text.length)
        setSearchSelection((anchor + delta).coerceIn(0, text.length))
    }

    private fun setSearchSelection(index: Int) {
        val text = searchField.text ?: return
        Selection.setSelection(text, index.coerceIn(0, text.length))
        pendingSearchReplacementRange = null
    }

    private fun selectedSearchRange(textLength: Int): IntRange? {
        val selectionStart = searchField.selectionStart
        val selectionEnd = searchField.selectionEnd
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, textLength)
            val endExclusive = maxOf(selectionStart, selectionEnd).coerceIn(0, textLength)
            if (start < endExclusive) return start until endExclusive
        }
        return pendingSearchReplacementRange?.let { range ->
            val start = range.first.coerceIn(0, textLength)
            val endExclusive = (range.last + 1).coerceIn(0, textLength)
            if (start < endExclusive) start until endExclusive else null
        }
    }

    private fun isTextEditingCtrlShortcut(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_A ||
            keyCode == KeyEvent.KEYCODE_C ||
            keyCode == KeyEvent.KEYCODE_X ||
            keyCode == KeyEvent.KEYCODE_V
    }

    private fun handleTextEditingCtrlShortcut(keyCode: Int): Boolean {
        focusSearchField()
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                searchField.text?.let { text ->
                    Selection.selectAll(text)
                    pendingSearchReplacementRange = 0 until text.length
                }
                true
            }
            KeyEvent.KEYCODE_C -> searchField.onTextContextMenuItem(android.R.id.copy)
            KeyEvent.KEYCODE_X -> {
                pendingSearchReplacementRange = null
                searchField.onTextContextMenuItem(android.R.id.cut)
            }
            KeyEvent.KEYCODE_V -> {
                pendingSearchReplacementRange = null
                searchField.onTextContextMenuItem(android.R.id.paste)
            }
            else -> false
        }
    }

    private fun focusSearchField() {
        if (!searchField.hasFocus()) {
            searchField.requestFocus()
        }
    }

    private fun KeyEvent.withCtrlMeta(): KeyEvent {
        if (isCtrlPressed) {
            return this
        }
        return KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
            deviceId,
            scanCode,
            flags,
            source
        )
    }
    
    /**
     * Scrolls to the top of the emoji picker.
     * Recents updates are applied only when safe for UX.
     */
    fun scrollToTop() {
        recyclerView.post {
            recyclerView.scrollToPosition(0)
            // Don't force a refresh here to avoid UI jumps while scrolling.
        }
    }

    private fun loadCategories() {
        // Cancel any previous loading job to avoid race conditions
        loadingJob?.cancel()

        // While GIF or symbol search shows its results, the emoji data loads out of sight
        if (!gifMode && !symbolMode) {
            loadingView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        }

        loadedCompatGeneration = EmojiCompatSupport.generation
        val extra = EmojiCompatSupport.extraAvailabilityForCurrentEditor()
        extraAvailable = extra

        loadingJob = liveScope().launch {
            try {
                // Regular categories first: they load the data the recents filter relies on.
                val regularCategories = withContext(Dispatchers.IO) { EmojiRepository.getEmojiCategories(context, extra) }
                val recentCategory = withContext(Dispatchers.IO) {
                    displayableRecents(RecentEmojiManager.getRecentEmojiCategory(context))
                }
                val loadedSearchIndex = withContext(Dispatchers.IO) { EmojiSearchRepository.getSearchIndex(context) }
                this@EmojiPickerView.regularCategories = regularCategories
                this@EmojiPickerView.searchIndex = loadedSearchIndex

                val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
                if (recentCategory != null) allCategories.add(recentCategory)
                allCategories.addAll(regularCategories)

                // Always reset to first category when loading
                selectedCategoryId = allCategories.firstOrNull()?.id

                buildSections(allCategories)
                updateTabs(allCategories)

                loadingView.visibility = View.GONE
                if (gifMode || symbolMode) {
                    // GIF or symbol search is showing its own results and messages
                    recyclerView.visibility = View.VISIBLE
                } else if (allCategories.isEmpty()) {
                    emptyView.text = context.getString(R.string.emoji_picker_error)
                    emptyView.visibility = View.VISIBLE
                } else {
                    if (searchQuery.isNotBlank()) {
                        applySearchNow()
                    } else {
                        setSearchMode(false)
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        // Always start from top when opening emoji picker
                        recyclerView.scrollToPosition(0)
                    }
                }
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation to properly cancel coroutine
            } catch (e: Exception) {
                loadingView.visibility = View.GONE
                if (!gifMode && !symbolMode) {
                    emptyView.text = context.getString(R.string.emoji_picker_error)
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        pickedSinceSearchChange = false
        if (gifMode || symbolMode) {
            // Symbol search is instant; GIF search keeps its own short wait before asking KLIPY
            log("typed: \"$searchQuery\" (scope=${coroutineScope.isActive})")
            applySearchNow()
            return
        }
        searchJob = coroutineScope.launch {
            // Just long enough to take a burst of fast keystrokes as one emoji search
            kotlinx.coroutines.delay(40)
            applySearchNow()
        }
    }

    private fun appendSearchText(text: String) {
        if (text.isEmpty()) return
        val editable = searchField.text ?: return
        val selectionStart = searchField.selectionStart
        val selectionEnd = searchField.selectionEnd
        val replacementRange = selectedSearchRange(editable.length)
        if (replacementRange != null) {
            val start = replacementRange.first
            val end = replacementRange.last + 1
            editable.replace(start, end, text)
            searchField.setSelection(start + text.length)
            pendingSearchReplacementRange = null
        } else if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
            val end = maxOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
            editable.replace(start, end, text)
            searchField.setSelection(start + text.length)
        } else {
            val cursor = selectionStart
                .takeIf { it == selectionEnd }
                ?.coerceIn(0, editable.length)
                ?: editable.length
            editable.insert(cursor, text)
            searchField.setSelection(cursor + text.length)
        }
    }

    fun disableSearchInputCapture() {
        setSearchInputCaptureEnabled(false)
    }

    private fun setSearchInputCaptureEnabled(enabled: Boolean) {
        searchInputCaptureEnabled = enabled
        searchField.isCursorVisible = enabled
        searchField.alpha = if (enabled) 1f else 0.75f
        if (enabled) {
            val editable = searchField.text
            if (editable != null) {
                searchField.setSelection(editable.length)
            }
        } else {
            searchField.clearFocus()
            pendingSearchReplacementRange = null
        }
    }

    /** Opens GIF search (KLIPY): featured GIFs first; typing searches. Needs GIFs turned on. */
    fun openGifs() {
        log("openGifs: enabled=${SettingsManager.gifsAvailable(context)} | ${gridState()}")
        if (!SettingsManager.gifsAvailable(context)) return
        setGifMode(true)
        setSearchPanelVisible(true)
        ensureTabs()
        settleDrawing()
        // Its setting: typing searches at once, or only after tapping the search
        if (!SettingsManager.getGifFocusSearch(context)) setSearchInputCaptureEnabled(false)
    }

    /**
     * A fresh opening of the picker with nothing asked for: its search takes typing at once
     * ([focus]) or waits for a tap (the field stays in view in the Pastierina bar).
     */
    fun applyOpenFocus(focus: Boolean) {
        if (gifMode || symbolMode) return
        if (focus) {
            setSearchPanelVisible(true)
        } else if (searchFieldHost != null) {
            setSearchInputCaptureEnabled(false)
        }
    }

    /** Shows or hides the GIF tab as the setting changes; leaves GIF mode when it's turned off. */
    fun refreshGifAvailability() {
        val enabled = SettingsManager.gifsAvailable(context)
        gifTabButton.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled && gifMode) setGifMode(false)
    }

    private fun setGifMode(enabled: Boolean) {
        if (gifMode == enabled) return
        if (enabled && symbolMode) setSymbolMode(false)
        gifMode = enabled
        pickedSinceSearchChange = false
        gifTabButton.background = createTabBackground(enabled)
        showInGrid(if (enabled) gifAdapter else null, spanCount = GIF_COLUMNS, spanSizeLookup = gifSpans)
        gifAttribution.visibility = if (enabled) View.VISIBLE else View.GONE
        modeTabSelected = 0
        updateModeTabs()
        searchField.hint = context.getString(
            if (enabled) R.string.gif_search_placeholder else R.string.emoji_picker_search_placeholder
        )
        if (enabled) {
            loadGifs(searchQuery)
        } else {
            gifJob?.cancel()
            gifAdapter.submit(emptyList())
            emptyView.visibility = View.GONE
            applySearchNow()
        }
    }

    private fun loadGifs(query: String) {
        gifJob?.cancel()
        val apiKey = SettingsManager.getKlipyApiKey(context)
        if (apiKey.isBlank()) {
            gifAdapter.submit(emptyList())
            showGifMessage(R.string.gif_no_api_key)
            return
        }
        log("gifs: search \"$query\" (scope=${coroutineScope.isActive})")
        val job = liveScope().launch {
            log("gifs: job started")
            // Anything cached for this search shows at once, even if old; if it's fresh, that's it
            val cached = withContext(Dispatchers.IO) {
                runCatching { KlipyGifs.cachedResults(context, query.trim()) }.getOrNull()
            }
            if (cached != null && cached.first.isNotEmpty()) {
                gifAdapter.submit(cached.first, query.trim())
                recyclerView.scrollToPosition(0)
                showGifMessage(null)
                if (cached.second) {
                    log("gifs: ${cached.first.size} cached results for \"$query\"")
                    logGridAfterLayout("gifs (cached)")
                    return@launch
                }
            } else if (query.isNotBlank()) {
                // A short wait for the next keystroke before asking the network (a request the
                // next keystroke overtakes is cancelled anyway)
                delay(120)
            }
            if (gifAdapter.itemCount == 0) showGifMessage(R.string.gif_loading)
            val results = try {
                KlipyGifs.find(context, apiKey, query.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("gifs: failed: $e")
                gifAdapter.submit(emptyList())
                // Say what went wrong (e.g. "KLIPY HTTP 401"), so a failure is never just blank
                emptyView.text = context.getString(R.string.gif_error_detail, e.message ?: e.javaClass.simpleName)
                emptyView.visibility = View.VISIBLE
                return@launch
            }
            // Refreshed behind cached results: only redraw if something changed
            if (results.map { it.id } != gifAdapter.ids()) {
                gifAdapter.submit(results, query.trim())
                recyclerView.scrollToPosition(0)
            }
            // The user's own GIFs may show even when KLIPY found none
            showGifMessage(if (gifAdapter.itemCount == 0) R.string.gif_no_results else null)
            log("gifs: ${results.size} results for \"$query\" | ${gridState()}")
            logGridAfterLayout("gifs")
        }
        job.invokeOnCompletion { cause -> if (cause != null) log("gifs: job ended early: $cause") }
        gifJob = job
    }

    /** GIF/symbol search diagnostics: `logcat -s FluxSearch` (pastiera-flux.sh search-log). */
    private fun log(message: String) {
        Log.i(LOG_TAG, message)
    }

    /** One line describing the grid as it is on screen right now. */
    private fun gridState(): String =
        "picker attached=$isAttachedToWindow shown=$isShown ${width}x$height | grid vis=${visibilityName(recyclerView.visibility)} " +
            "shown=${recyclerView.isShown} ${recyclerView.width}x${recyclerView.height} alpha=${recyclerView.alpha} " +
            "adapter=${recyclerView.adapter?.javaClass?.simpleName} items=${recyclerView.adapter?.itemCount} " +
            "children=${recyclerView.childCount} spans=${(recyclerView.layoutManager as? GridLayoutManager)?.spanCount} | " +
            "message vis=${visibilityName(emptyView.visibility)} \"${emptyView.text}\" | loading vis=${visibilityName(loadingView.visibility)} | " +
            "gif=$gifMode symbol=$symbolMode scope=${coroutineScope.isActive}"

    private fun visibilityName(visibility: Int): String = when (visibility) {
        View.VISIBLE -> "VISIBLE"
        View.INVISIBLE -> "INVISIBLE"
        else -> "GONE"
    }

    private var redrawPosted = false

    /**
     * Lays the results area (grid and messages) out again at its current size and redraws it,
     * once per frame at most. New results normally get there through requestLayout(), which
     * climbs the keyboard's views to the window; on the phone that request can stop at a view
     * that is still waiting for an earlier layout, leaving the new results computed but never
     * drawn. Doing the layout here doesn't depend on the request getting through.
     */
    private fun redrawResults() {
        if (redrawPosted) return
        redrawPosted = true
        post {
            redrawPosted = false
            val area = recyclerView.parent as? ViewGroup ?: return@post
            val waiting = generateSequence(area.parent) { it.parent }
                .filterIsInstance<View>()
                .filter { it.isLayoutRequested && !it.isInLayout }
                .map { it.javaClass.simpleName.ifEmpty { it.javaClass.name } }
                .toList()
            if (area.width > 0 && area.height > 0) {
                area.forceLayout()
                recyclerView.forceLayout()
                area.measure(
                    MeasureSpec.makeMeasureSpec(area.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(area.height, MeasureSpec.EXACTLY)
                )
                area.layout(area.left, area.top, area.right, area.bottom)
            }
            area.invalidate()
            if (waiting.isNotEmpty() || gifMode || symbolMode) {
                log("redrew results: ${recyclerView.childCount} cells shown; views still waiting for layout: $waiting")
            }
        }
    }

    /** Logs the grid once the results have been laid out. */
    private fun logGridAfterLayout(what: String) {
        recyclerView.post { log("$what, after layout: ${gridState()}") }
    }

    /**
     * The scope GIF and symbol search run in. It's cancelled when the picker leaves the screen
     * and recreated when it comes back; if a search starts on screen with it still cancelled,
     * recreate it here rather than launch into a dead scope (nothing would ever show).
     */
    private fun liveScope(): CoroutineScope {
        if (!coroutineScope.isActive && isAttachedToWindow) {
            log("scope was cancelled while on screen: recreating it")
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
        return coroutineScope
    }

    /**
     * GIF and symbol search show their results in the emoji grid itself: [adapter] replaces
     * its content ([spanCount] columns), null puts the emoji content back.
     */
    private fun showInGrid(
        adapter: RecyclerView.Adapter<*>?,
        spanCount: Int,
        spanSizeLookup: GridLayoutManager.SpanSizeLookup = GridLayoutManager.DefaultSpanSizeLookup()
    ) {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        if (adapter != null) {
            lm.spanCount = spanCount
            lm.spanSizeLookup = spanSizeLookup
            recyclerView.adapter = adapter
            recyclerView.visibility = View.VISIBLE
            loadingView.visibility = View.GONE
            log("grid shows ${adapter.javaClass.simpleName} ($spanCount columns)")
            redrawResults()
        } else {
            log("grid back to emoji")
            lm.spanCount = columns
            val emojiAdapter = if (isSearchMode) searchAdapter else sectionAdapter
            lm.spanSizeLookup = if (isSearchMode) searchAdapter.spanSizeLookup else sectionAdapter.spanSizeLookup
            recyclerView.adapter = emojiAdapter
        }
    }

    /** Opens symbol search: every Unicode symbol the fonts can draw, searchable by name. */
    /** Symbol search is showing (GIF prefetching waits for another time). */
    fun isSymbolSearchOpen(): Boolean = symbolMode

    fun openSymbols(kaomoji: Boolean = false) {
        log("openSymbols(kaomoji=$kaomoji) | ${gridState()}")
        if (symbolMode && kaomojiSearch != kaomoji) setSymbolMode(false)
        kaomojiSearch = kaomoji
        setSymbolMode(true)
        setSearchPanelVisible(true)
        ensureTabs()
        settleDrawing()
    }

    private fun setSymbolMode(enabled: Boolean) {
        if (symbolMode == enabled) return
        if (enabled && gifMode) setGifMode(false)
        symbolMode = enabled
        pickedSinceSearchChange = false
        if (!enabled) kaomojiSearch = false
        pendingGroupJump = null
        // Kaomoji are wide: three to a row
        showInGrid(if (enabled) symbolAdapter else null, spanCount = if (kaomojiSearch) 3 else 8)
        modeTabSelected = 0
        updateModeTabs()
        searchField.hint = context.getString(
            when {
                enabled && kaomojiSearch -> R.string.kaomoji_search_placeholder
                enabled -> R.string.symbol_search_placeholder
                else -> R.string.emoji_picker_search_placeholder
            }
        )
        if (enabled) {
            loadSymbols(searchQuery)
        } else {
            symbolJob?.cancel()
            symbolAdapter.submit(emptyList())
            emptyView.visibility = View.GONE
            applySearchNow()
        }
    }

    private fun loadSymbols(query: String) {
        symbolJob?.cancel()
        log("symbols: search \"$query\" (list ready=${SymbolSearch.isReady()}, scope=${coroutineScope.isActive})")
        if (kaomojiSearch && query.isNotBlank()) {
            symbolJob = liveScope().launch {
                val results = withContext(SymbolSearch.dispatcher) { Kaomoji.search(context, query) }
                symbolAdapter.submit(results)
                recyclerView.scrollToPosition(0)
                showGifMessage(if (results.isEmpty()) R.string.kaomoji_no_results else null)
                log("kaomoji: ${results.size} results for \"$query\"")
            }
            return
        }
        if (query.isBlank()) {
            // Nothing typed: every group in order (the symbols page's groups, or every kaomoji),
            // so a tab jumps to where its group starts
            val all = if (kaomojiSearch) Kaomoji.entries(context)
                else it.palsoftware.pastiera.data.symbols.SymbolPages.PAGES.flatten().map { symbol -> SymbolSearch.Entry(symbol, "") }
            symbolAdapter.submit(all)
            showGifMessage(null)
            val target = pendingGroupJump ?: 0
            pendingGroupJump = null
            recyclerView.post { jumpTo(target) }
            return
        }
        val job = liveScope().launch {
            log("symbols: job started")
            // First use on this phone: the list is still being built (then it's cached)
            if (!SymbolSearch.isReady() && symbolAdapter.itemCount == 0) showGifMessage(R.string.symbol_loading)
            val results = try {
                withContext(SymbolSearch.dispatcher) {
                    val started = SystemClock.elapsedRealtime()
                    val all = SymbolSearch.entries(context)
                    val listed = SystemClock.elapsedRealtime()
                    val found = SymbolSearch.search(query, all)
                    val searched = SystemClock.elapsedRealtime()
                    // Symbols the fonts can't draw are dropped; about a screenful is checked
                    // now, the rest as they scroll into view
                    val ordered = if (SettingsManager.getRecentsFirstInSearch(context)) {
                        SymbolSearch.recentsFirst(found, SymbolSearch.recentSymbols(context))
                    } else {
                        found
                    }
                    SymbolSearch.renderable(ordered).also {
                        log(
                            "symbols: list ${listed - started} ms, search ${searched - listed} ms, " +
                                "glyph checks ${SystemClock.elapsedRealtime() - searched} ms"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                log("symbols: failed: $e")
                symbolAdapter.submit(emptyList())
                emptyView.text = context.getString(R.string.symbol_error_detail, e.message ?: e.javaClass.simpleName)
                emptyView.visibility = View.VISIBLE
                return@launch
            }
            symbolAdapter.submit(results)
            recyclerView.scrollToPosition(0)
            showGifMessage(if (results.isEmpty()) R.string.symbol_no_results else null)
            SymbolSearch.saveVerdictsSoon(context)
            log("symbols: ${results.size} results for \"$query\" | ${gridState()}")
            logGridAfterLayout("symbols")
        }
        job.invokeOnCompletion { cause -> if (cause != null) log("symbols: job ended early: $cause") }
        symbolJob = job
    }

    private fun onSymbolChosen(entry: SymbolSearch.Entry) {
        currentInputConnection?.commitText(entry.symbol, 1)
        pickedSinceSearchChange = true
        if (kaomojiSearch) {
            Kaomoji.addRecent(context, entry.symbol)
            if (SettingsManager.getKaomojiCloseOnTap(context)) onCloseRequested?.invoke()
            return
        }
        SymbolSearch.addRecent(context, entry.symbol)
        // Symbols come from the SYM pages, so SYM's auto-close applies
        if (SettingsManager.getSymAutoClose(context) && SettingsManager.getSymAutoCloseOnTouch(context)) {
            onCloseRequested?.invoke()
        }
    }

    private fun showGifMessage(messageRes: Int?) {
        if (messageRes == null) {
            emptyView.visibility = View.GONE
        } else {
            emptyView.text = context.getString(messageRes)
            emptyView.visibility = View.VISIBLE
        }
        redrawResults()
    }

    /** The search key: whatever the picker shows (emoji, GIFs, symbols), its search takes typing. */
    fun focusSearch() {
        setSearchPanelVisible(true)
    }

    /** Opens the search, focused for typing (e.g. from the emoji layer's search button). */
    fun openSearch() {
        setSearchPanelVisible(true)
        ensureTabs()
        settleDrawing()
    }

    private fun setSearchPanelVisible(visible: Boolean) {
        isSearchPanelVisible = visible
        searchPanel.visibility = if (visible && searchFieldHost == null) View.VISIBLE else View.GONE
        searchToggleButton.background = createTabBackground(visible)
        applyEdgeControlAppearance()
        setSearchInputCaptureEnabled(visible)
        if (visible) {
            searchField.requestFocus()
        }
        onSearchPanelVisibilityChanged?.invoke(visible)
    }

    private fun applySearchNow() {
        if (gifMode) {
            loadGifs(searchQuery)
            return
        }
        if (symbolMode) {
            loadSymbols(searchQuery)
            return
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            lastSearchResults = emptyList()
            setSearchMode(false)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.GONE
            recyclerView.visibility = if (sectionAdapter.itemCount > 0) View.VISIBLE else View.GONE
            return
        }

        val index = searchIndex
        if (index == null) {
            lastSearchResults = emptyList()
            setSearchMode(true)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val results = EmojiSearchRepository.search(index, query, extraAvailable = extraAvailable).let { found ->
            // Recently used first (its setting); the rest keep their order
            if (!SettingsManager.getRecentsFirstInSearch(context)) {
                found
            } else {
                val rank = RecentEmojiManager.getRecentEmojis(context).withIndex()
                    .associate { (index, emoji) -> emoji to index }
                found.sortedBy { rank[it.entry.base] ?: Int.MAX_VALUE }
            }
        }
        lastSearchResults = results
        setSearchMode(true)
        searchAdapter.submitList(results)
        if (results.isEmpty()) {
            emptyView.text = context.getString(R.string.emoji_picker_no_results)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.scrollToPosition(0)
        }
        redrawResults()
    }

    private fun setSearchMode(enabled: Boolean) {
        // GIF and symbol search own the grid while active
        if (gifMode || symbolMode) {
            isSearchMode = enabled
            return
        }
        if (isSearchMode == enabled) {
            // Ensure adapter is set correctly if external code changed it during refresh.
            val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
            if (enabled && recyclerView.adapter !== searchAdapter) {
                recyclerView.adapter = searchAdapter
                lm.spanSizeLookup = searchAdapter.spanSizeLookup
            } else if (!enabled && recyclerView.adapter !== sectionAdapter) {
                recyclerView.adapter = sectionAdapter
                lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            }
            tabRow.alpha = if (enabled) 0.55f else 1f
            return
        }

        isSearchMode = enabled
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        if (enabled) {
            recyclerView.adapter = searchAdapter
            lm.spanSizeLookup = searchAdapter.spanSizeLookup
        } else {
            recyclerView.adapter = sectionAdapter
            lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            updateTabsSelection()
        }
        tabRow.alpha = if (enabled) 0.55f else 1f
    }

    private fun buildSections(categories: List<EmojiRepository.EmojiCategory>) {
        val items = mutableListOf<SectionItem>()
        val categoryIds = ArrayList<String>()

        categories.forEach { category ->
            val title = category.displayNameRes?.let { context.getString(it) } ?: category.id
            items.add(SectionItem.Header(category.id, title))
            categoryIds.add(category.id)
            category.emojis.forEach { emojiEntry ->
                items.add(SectionItem.Emoji(category.id, emojiEntry))
                categoryIds.add(category.id)
            }
        }

        rebuildIndexCaches(items, categoryIds)
        sectionAdapter.submitList(items)
    }

    private fun updateTabs(categories: List<EmojiRepository.EmojiCategory>) {
        emojiTabCategories = categories
        // GIF, symbol and kaomoji search have tabs of their own
        if (gifMode || symbolMode) {
            updateModeTabs()
            return
        }
        tabRow.removeAllViews()
        tabCategoryIds = categories.map { it.id }
        if (selectedCategoryId !in tabCategoryIds) {
            selectedCategoryId = tabCategoryIds.firstOrNull()
        }
        val tabHeight = dpToPx(32f)
        categories.forEach { category ->
            val iconRes = EmojiRepository.getCategoryIconRes(category.id)
            val label = category.displayNameRes?.let { context.getString(it) } ?: category.id
            val isSelected = category.id == selectedCategoryId
            val btn = ImageView(context).apply {
                setImageResource(iconRes)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
                background = createTabBackground(isSelected)
                // Icon always visible (alpha 1), background changes
                val pad = dpToPx(4f) // Minimal padding
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, // Use weight
                    if (roundedControls) ViewGroup.LayoutParams.MATCH_PARENT else tabHeight,
                    1f // Equal weight for all tabs
                )
                setOnClickListener {
                    if (gifMode) setGifMode(false)
                    if (symbolMode) setSymbolMode(false)
                    selectedCategoryId = category.id
                    updateTabsSelection()
                    val jump = jump@{
                        isTabClickScroll = true
                        // Recents is always at position 0 when present
                        if (category.id == EmojiRepository.RECENTS_CATEGORY_ID) {
                            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                            recyclerView.post {
                                requestRecentsRefresh(requireTop = true, requireNotRecents = false)
                            }
                        } else {
                            val headerPos = headerPositions[category.id] ?: return@jump
                            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
                        }
                        relayoutToWindow(recyclerView)
                    }
                    if (isSearchMode) {
                        // Searching: the search clears and the emoji jump to the category
                        searchField.setText("")
                        setSearchMode(false)
                        recyclerView.postDelayed({ jump() }, 80)
                    } else {
                        jump()
                    }
                }
            }
            tabRow.addView(btn)
        }
        updateTabsSelection()
        redrawBar()
    }

    /** A tab of a search: its label, and what it does. */
    private class ModeTab(val label: String, val description: String, val onPick: () -> Unit)

    private val gifTabTerms = listOf("LOL", "Love", "Sad", "Wow", "Yes", "No", "Bye")
    private val kaomojiTabWords = listOf(
        "Joy" to "joy", "Love" to "love", "Sad" to "sad", "Mad" to "angry",
        "Wow" to "surprise", "Shy" to "shy", "Hug" to "hug", "Hi" to "hello"
    )
    private val symbolTabLabels = listOf("#", "\u2192", "\u2211", "\u20AC", "\u00A7", "\u2605", "\u266A")
    private var modeTabSelected = 0

    /**
     * GIF, symbol and kaomoji search's own tabs in the bottom bar: GIF favourites and quick
     * searches, the symbols page's groups, the kaomoji's moods and actions.
     */
    private fun updateModeTabs() {
        val tabs = when {
            gifMode -> listOf(ModeTab("\u2605", context.getString(R.string.gif_tab_description)) { pickModeQuery("") }) +
                gifTabTerms.map { term -> ModeTab(term, term) { pickModeQuery(term) } }
            symbolMode && kaomojiSearch -> kaomojiTabWords.map { (label, word) ->
                ModeTab(label, word) {
                    // Where its group starts among every kaomoji
                    jumpToGroup(Kaomoji.groupStart(context, word) ?: 0)
                }
            }
            symbolMode -> symbolTabLabels.mapIndexed { index, label ->
                ModeTab(label, label) {
                    val start = it.palsoftware.pastiera.data.symbols.SymbolPages.PAGES.take(index).sumOf { page -> page.size }
                    jumpToGroup(start)
                }
            }
            else -> return updateTabs(emojiTabCategories)
        }
        modeTabSelected = modeTabSelected.coerceIn(0, tabs.lastIndex.coerceAtLeast(0))
        tabRow.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            tabRow.addView(TextView(context).apply {
                text = tab.label
                contentDescription = tab.description
                gravity = Gravity.CENTER
                maxLines = 1
                textSize = if (tab.label.length > 1) 13f else 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                background = createTabBackground(index == modeTabSelected)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    modeTabSelected = index
                    for (i in 0 until tabRow.childCount) tabRow.getChildAt(i).background = createTabBackground(i == index)
                    tab.onPick()
                    redrawBar()
                }
            })
        }
        tabRow.alpha = 1f
        redrawBar()
    }

    /**
     * A symbol or kaomoji tab: the list jumps to its group. With something typed, the search
     * clears first and the whole list comes back, starting at the group.
     */
    private fun jumpToGroup(position: Int) {
        if (searchField.text.isNullOrEmpty() && symbolAdapter.itemCount > 0) {
            jumpTo(position)
        } else {
            pendingGroupJump = position
            if (searchField.text.isNullOrEmpty()) loadSymbols("") else searchField.setText("")
        }
    }

    private fun jumpTo(position: Int) {
        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(position, 0)
        relayoutToWindow(recyclerView)
    }

    /**
     * Asks for a layout all the way up to the window. A frame above the picker can be left
     * marked as waiting for layout (its corner buttons reshape mid-layout); a plain request then
     * stops at that mark and the change (a jump, new tabs) never reaches the screen.
     */
    private fun relayoutToWindow(from: View) {
        var view: View? = from
        while (view != null) {
            view.forceLayout()
            view.invalidate()
            view = view.parent as? View
        }
        rootView.requestLayout()
    }

    /** A GIF tab: its search in the field (the search runs from there). */
    private fun pickModeQuery(query: String) {
        if (searchField.text?.toString() == query) applySearchNow() else searchField.setText(query)
        searchField.setSelection(searchField.text?.length ?: 0)
    }

    /**
     * The bar, and the keyboard frame round it, drawn afresh: the frame clips the panels to the
     * display's corners and kept an old picture of the bar (no tabs) until something else, like
     * an LED, redrew it.
     */
    private fun redrawBar() {
        relayoutToWindow(tabRow)
        var view: View? = tabRow.parent as? View
        while (view != null) {
            view.invalidateOutline()
            view = view.parent as? View
        }
        post { if (isAttachedToWindow) relayoutToWindow(this) }
    }

    private fun updateTabsSelection() {
        for (i in 0 until tabRow.childCount) {
            val view = tabRow.getChildAt(i) as? ImageView ?: continue
            val categoryId = tabCategoryIds.getOrNull(i)
            val isSelected = categoryId == selectedCategoryId
            // Icon always visible, only background changes
            view.background = createTabBackground(isSelected)
        }
    }

    private fun onEmojiSelected(emoji: String, categoryId: String, closeAfterCommit: Boolean? = null) {
        pickedSinceSearchChange = true
        val inputConnection = currentInputConnection
        // Recents persistence must survive the SYM auto-close: closing the picker evicts this
        // view from its container, which cancels coroutineScope; ATOMIC guarantees the write
        // still runs even when cancellation lands before the coroutine body starts.
        coroutineScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.markUsed(context, emoji)
            val changed = RecentEmojiManager.addRecentEmoji(
                context,
                emoji,
                moveToTopWhenExists = true
            )
            if (changed) {
                val requiresNotRecents = categoryId == EmojiRepository.RECENTS_CATEGORY_ID
                withContext(Dispatchers.Main) {
                    requestRecentsRefresh(requireTop = !requiresNotRecents, requireNotRecents = requiresNotRecents)
                }
            }
        }
        // Commit synchronously before closing: a post{} on a view that the close detaches
        // would only run again when the picker is re-attached (i.e. the next time it opens).
        inputConnection?.commitText(emoji, 1)
        // With an emoji key set, the picker follows the emoji key's own auto-close
        val shouldClose = closeAfterCommit
            ?: SettingsManager.emojiScreenClosesAfterInput(
                context, isPicker = true, openedByEmojiKey = false, byTouch = true
            )
        if (shouldClose) {
            onCloseRequested?.invoke()
        }
    }

    /**
     * Simple refresh of recents from storage.
     * Applies updates only when safe for UX.
     * Compares stored vs displayed recents and updates only if different.
     */
    private fun refreshRecentsFromStorage(allowInsertOrRemove: Boolean) {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                displayableRecents(RecentEmojiManager.getRecentEmojiCategory(context))
            }

            val recentsHeaderIndex = headerPositions[EmojiRepository.RECENTS_CATEGORY_ID]

            // Case 1: Recents in storage but not displayed -> full reload
            if (recentsHeaderIndex == null && recentCategory != null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val newRecentsItems = buildRecentsItems(recentCategory)
                val newItems = newRecentsItems + sectionItems
                rebuildIndexCaches(newItems)
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, newRecentsItems.size) }
                }
                updateTabs(buildAllCategories(recentCategory))
                return@launch
            }

            // Case 2: No recents in storage but displayed -> full reload
            if (recentsHeaderIndex != null && recentCategory == null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size
                val removedCount = nextHeaderIndex - recentsHeaderIndex
                val newItems = sectionItems.toMutableList()
                repeat(removedCount) {
                    newItems.removeAt(recentsHeaderIndex)
                }
                rebuildIndexCaches(newItems)
                if (selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
                    selectedCategoryId = itemCategoryIds.firstOrNull()
                }
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, -removedCount) }
                }
                updateTabs(buildAllCategories(null))
                return@launch
            }

            // Case 3: Both exist -> compare and update if different
            if (recentsHeaderIndex != null && recentCategory != null) {
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size

                val displayedRecents = sectionItems
                    .subList(recentsHeaderIndex + 1, nextHeaderIndex)
                    .filterIsInstance<SectionItem.Emoji>()
                    .map { it.entry.base }

                val storedRecents = recentCategory.emojis.map { it.base }

                // Only update if different
                if (displayedRecents != storedRecents) {
                    val newRecentsItems = buildRecentsItems(recentCategory)

                    val newItems = sectionItems.toMutableList()
                    for (i in recentsHeaderIndex until nextHeaderIndex) {
                        newItems.removeAt(recentsHeaderIndex)
                    }
                    newItems.addAll(recentsHeaderIndex, newRecentsItems)

                    rebuildIndexCaches(newItems)
                    val anchor = if (isAtAbsoluteTop()) null else captureScrollAnchor()
                    sectionAdapter.submitList(newItems) {
                        anchor?.let { restoreScrollAnchor(it, 0) }
                    }
                }
            }
        }
    }

    /**
     * Updates tabs asynchronously when recents section is added/removed.
     */
    private fun updateTabsAsync() {
        liveScope().launch {
            val recentCategory = withContext(Dispatchers.IO) {
                displayableRecents(RecentEmojiManager.getRecentEmojiCategory(context))
            }
            val regularCategories = withContext(Dispatchers.IO) {
                EmojiRepository.getEmojiCategories(context, extraAvailable)
            }

            val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
            if (recentCategory != null) allCategories.add(recentCategory)
            allCategories.addAll(regularCategories)

            updateTabs(allCategories)
        }
    }

    private fun createTabBackground(isSelected: Boolean): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val color = if (theme != null) {
                if (isSelected) colorWithAlpha(theme.accent, 100) else Color.TRANSPARENT
            } else if (isSelected) {
                Color.argb(100, 255, 255, 255)
            } else {
                Color.TRANSPARENT
            }
            setColor(color)
            if (theme != null && isSelected) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun createCloseButtonBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.statusBarButton ?: Color.argb(95, 220, 38, 38))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun createSearchFieldBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.suggestion ?: Color.argb(36, 255, 255, 255))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(7f).toFloat()
        }
    }

    private fun applyTheme() {
        val theme = themeOverride
        val background = theme?.background ?: Color.TRANSPARENT
        @Suppress("NotifyDataSetChanged")
        symbolAdapter.notifyDataSetChanged()
        gifTabButton.setTextColor(theme?.textAndIcons ?: Color.WHITE)
        gifTabButton.background = createTabBackground(gifMode)
        gifAttribution.setTextColor(theme?.textAndIcons ?: Color.WHITE)
        setBackgroundColor(background)
        vertical.setBackgroundColor(background)
        recyclerView.setBackgroundColor(background)
        searchPanel.setBackgroundColor(background)
        loadingView.setBackgroundColor(background)
        emptyView.setBackgroundColor(background)
        searchField.setTextColor(theme?.textAndIcons ?: Color.WHITE)
        searchField.setHintTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 160))
        searchField.background = createSearchFieldBackground()
        closeButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        closeButton.background = createCloseButtonBackground()
        searchToggleButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        searchToggleButton.background = createTabBackground(isSearchPanelVisible)
        keyboardSwitcherButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        keyboardSwitcherButton.background = createTabBackground(false)
        applyEdgeControlAppearance()
        emptyView.setTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 128))
        updateTabsSelection()
        sectionAdapter.notifyDataSetChanged()
        searchAdapter.notifyDataSetChanged()
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun showVariantsPopup(anchor: View, entry: EmojiRepository.EmojiEntry, categoryId: String) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        val itemHorizontalPadding = (12 * density).toInt()
        val itemVerticalPadding = (8 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            gravity = Gravity.CENTER
        }

        var popup: PopupWindow? = null
        val options = listOf(entry.base) + entry.variants
        options.forEach { emoji ->
            val textView = TextView(context).apply {
                text = EmojiCompatSupport.forDisplay(emoji)
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(itemHorizontalPadding, itemVerticalPadding, itemHorizontalPadding, itemVerticalPadding)
                setTextColor(themeOverride?.textAndIcons ?: Color.BLACK)
            }
            textView.setOnClickListener {
                onEmojiSelected(emoji, categoryId)
                popup?.dismiss()
            }
            container.addView(textView)
        }

        container.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        popup = PopupWindow(
            container,
            WRAP_CONTENT,
            WRAP_CONTENT,
            false // Don't take focus to avoid closing emoji picker
        ).apply {
            setBackgroundDrawable(ColorDrawable(themeOverride?.keyPopup ?: Color.parseColor("#EEFFFFFF")))
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12f
        }

        // Position popup above the anchor
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val windowWidth = context.resources.displayMetrics.widthPixels
        val popupWidth = container.measuredWidth
        val popupHeight = container.measuredHeight
        val anchorX = location[0]
        val anchorY = location[1]
        val desiredX = anchorX + (anchor.width - popupWidth) / 2
        val clampedX = desiredX.coerceIn(0, windowWidth - popupWidth)
        val xOffset = clampedX - anchorX
        val desiredYOffset = -(popupHeight + anchor.height)
        val minYOffset = -(anchorY + anchor.height)
        val yOffset = maxOf(desiredYOffset, minYOffset)
        popup.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        log("attached (scope=${coroutineScope.isActive}, gif=$gifMode, symbol=$symbolMode)")
        // Recreate coroutine scope if it was cancelled
        if (!coroutineScope.isActive) {
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            // A GIF or symbol search that was running in the old scope died with it
            if (gifMode) loadGifs(searchQuery)
            if (symbolMode) loadSymbols(searchQuery)
        }
        ensureTabs()
        settleDrawing()
    }

    /**
     * Once the picker or one of its searches is on screen and laid out, the whole keyboard
     * window drawn once, recorded and thrown away. That marks every view in it as drawn; a view
     * the hardware pass left unmarked (the bar's tabs after the keyboard frame reshaped round
     * them) ignores its own redraw requests, so new tabs, the selected tab and jumps to a
     * category stayed off the screen until an LED redrew the frame.
     */
    private fun settleDrawing() {
        post {
            val root = rootView
            if (!isAttachedToWindow || root.width <= 0 || root.height <= 0) return@post
            runCatching {
                val picture = android.graphics.Picture()
                root.draw(picture.beginRecording(root.width, root.height))
                picture.endRecording()
            }
        }
    }

    /**
     * The bottom bar's category tabs, whatever the picker opened on (emoji, GIF, symbol or
     * kaomoji search): loading them dies with the scope when the picker leaves the screen
     * part-way, which left the bar empty until the next full load.
     */
    private fun ensureTabs() {
        if (tabRow.childCount > 0 && (loadingJob?.isActive == true || regularCategories.isNotEmpty())) return
        if (regularCategories.isEmpty() && loadingJob?.isActive != true) loadCategories()
        else if (tabRow.childCount == 0) updateTabsAsync()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        log("detached (reordering=$containerReordering, gif=$gifMode, symbol=$symbolMode)")
        if (!containerReordering) {
            coroutineScope.cancel()
            // Detach outside a host reorder means the picker actually left the screen
            // (IME hidden or SYM closed): reopen fresh instead of resuming the search.
            resetSearchStateQuietly()
        }
    }

    /**
     * Wraps a host-side reordering of this view within its container (remove + re-add in
     * one step, e.g. stacking the software keyboard below). The transient detach must not
     * reset the search state.
     */
    fun reorderingWithinContainer(block: () -> Unit) {
        containerReordering = true
        try {
            block()
        } finally {
            containerReordering = false
        }
    }

    /**
     * Shows the search field in [host] (e.g. the middle of the Pastierina bar, above the grid)
     * instead of the picker's own search panel; null moves it back. While hosted the field is
     * always visible and typing goes into it; the search button switches typing back to the app.
     * Quiet: no onSearchPanelVisibilityChanged, since this runs while the status bar renders.
     */
    fun setSearchFieldHost(host: ViewGroup?) {
        if (host !== searchFieldHost) {
            searchFieldHost = host
            (searchField.parent as? ViewGroup)?.removeView(searchField)
            val padH = dpToPx(8f)
            if (host != null) {
                host.removeAllViews()
                searchField.setPadding(padH, 0, padH, 0)
                searchField.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                val marginH = dpToPx(4f)
                val marginV = dpToPx(3f)
                host.addView(
                    searchField,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply { setMargins(marginH, marginV, marginH, marginV) }
                )
            } else {
                searchField.setPadding(padH, dpToPx(5f), padH, dpToPx(5f))
                searchField.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                searchPanel.addView(
                    searchField,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                    )
                )
                resetSearchStateQuietly()
                return
            }
        }
        if (host != null && !isSearchPanelVisible) {
            isSearchPanelVisible = true
            searchPanel.visibility = View.GONE
            searchToggleButton.background = createTabBackground(true)
            applyEdgeControlAppearance()
            setSearchInputCaptureEnabled(true)
            searchField.requestFocus()
        }
    }

    private fun resetSearchStateQuietly() {
        if (!isSearchPanelVisible) {
            if (searchQuery.isNotEmpty()) {
                searchQuery = ""
                lastSearchResults = emptyList()
                searchField.setText("")
            }
            return
        }
        // Quiet: no onSearchPanelVisibilityChanged notification, the view is off-screen.
        isSearchPanelVisible = false
        searchPanel.visibility = View.GONE
        searchToggleButton.background = createTabBackground(false)
        setSearchInputCaptureEnabled(false)
        searchQuery = ""
        lastSearchResults = emptyList()
        searchField.setText("")
    }

    private inner class SectionAdapter(private val columns: Int) :
        ListAdapter<SectionItem, RecyclerView.ViewHolder>(SectionItemDiffCallback()) {
        override fun onCurrentListChanged(previousList: MutableList<SectionItem>, currentList: MutableList<SectionItem>) {
            redrawResults()
        }

        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> columns
                    else -> 1
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is SectionItem.Header -> VIEW_TYPE_HEADER
                is SectionItem.Emoji -> VIEW_TYPE_EMOJI
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                // Minimal spacer between categories (no text, just 1dp height)
                val spacer = View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    )
                }
                HeaderViewHolder(spacer)
            } else {
                val tv = TextView(parent.context).apply {
                    gravity = Gravity.CENTER
                    textSize = 28.8f
                    minHeight = emojiSize
                    minWidth = emojiSize
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                EmojiViewHolder(tv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is SectionItem.Header -> {
                    // Nothing to bind - it's just a spacer
                }
                is SectionItem.Emoji -> {
                    // In the default skin tone, when it has one
                    val shown = it.palsoftware.pastiera.data.emoji.EmojiSkinTone.preferred(context, item.entry)
                    (holder as EmojiViewHolder).textView.text = EmojiCompatSupport.forDisplay(shown)
                    holder.textView.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                    holder.textView.setOnClickListener {
                        onEmojiSelected(shown, item.categoryId)
                    }
                    holder.textView.setOnLongClickListener {
                        if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                        showVariantsPopup(holder.textView, item.entry, item.categoryId)
                        true
                    }
                }
            }
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    private class SearchEmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private inner class SearchAdapter :
        ListAdapter<EmojiSearchRepository.EmojiSearchResult, SearchEmojiViewHolder>(SearchResultDiffCallback()) {
        override fun onCurrentListChanged(
            previousList: MutableList<EmojiSearchRepository.EmojiSearchResult>,
            currentList: MutableList<EmojiSearchRepository.EmojiSearchResult>
        ) {
            redrawResults()
        }

        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchEmojiViewHolder {
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 28.8f
                minHeight = emojiSize
                minWidth = emojiSize
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return SearchEmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: SearchEmojiViewHolder, position: Int) {
            val item = getItem(position)
            val shown = it.palsoftware.pastiera.data.emoji.EmojiSkinTone.preferred(context, item.entry)
            holder.textView.text = EmojiCompatSupport.forDisplay(shown)
            holder.textView.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
            holder.textView.setOnClickListener {
                onEmojiSelected(shown, item.categoryId)
            }
            holder.textView.setOnLongClickListener {
                if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                showVariantsPopup(holder.textView, item.entry, item.categoryId)
                true
            }
        }

        override fun getItemViewType(position: Int): Int = VIEW_TYPE_EMOJI
    }

    private sealed class SectionItem {
        data class Header(val categoryId: String, val title: String) : SectionItem()
        data class Emoji(val categoryId: String, val entry: EmojiRepository.EmojiEntry) : SectionItem()
    }

    private class SectionItemDiffCallback : DiffUtil.ItemCallback<SectionItem>() {
        override fun areItemsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return when {
                oldItem is SectionItem.Header && newItem is SectionItem.Header ->
                    oldItem.categoryId == newItem.categoryId
                oldItem is SectionItem.Emoji && newItem is SectionItem.Emoji ->
                    oldItem.categoryId == newItem.categoryId && oldItem.entry.base == newItem.entry.base
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return oldItem == newItem
        }
    }

    private class SearchResultDiffCallback :
        DiffUtil.ItemCallback<EmojiSearchRepository.EmojiSearchResult>() {
        override fun areItemsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem.entry.base == newItem.entry.base && oldItem.categoryId == newItem.categoryId
        }

        override fun areContentsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem == newItem
        }
    }

    private data class ScrollAnchor(val position: Int, val offset: Int)

    private fun rebuildIndexCaches(items: List<SectionItem>, categoryIds: List<String>? = null) {
        val headers = mutableMapOf<String, Int>()
        val ids = categoryIds?.toMutableList() ?: ArrayList(items.size)
        if (categoryIds == null) {
            items.forEach { item ->
                ids.add(item.categoryId())
            }
        }
        items.forEachIndexed { index, item ->
            if (item is SectionItem.Header) {
                headers[item.categoryId] = index
            }
        }
        sectionItems = items
        headerPositions = headers
        itemCategoryIds = ids
    }

    private fun buildRecentsItems(recentCategory: EmojiRepository.EmojiCategory): List<SectionItem> {
        val recentsTitle = recentCategory.displayNameRes?.let { context.getString(it) }
            ?: EmojiRepository.RECENTS_CATEGORY_ID
        val items = ArrayList<SectionItem>(recentCategory.emojis.size + 1)
        items.add(SectionItem.Header(EmojiRepository.RECENTS_CATEGORY_ID, recentsTitle))
        recentCategory.emojis.forEach { entry ->
            items.add(SectionItem.Emoji(EmojiRepository.RECENTS_CATEGORY_ID, entry))
        }
        return items
    }

    private fun buildAllCategories(recentCategory: EmojiRepository.EmojiCategory?): List<EmojiRepository.EmojiCategory> {
        return if (recentCategory == null) {
            regularCategories
        } else {
            listOf(recentCategory) + regularCategories
        }
    }

    private fun captureScrollAnchor(): ScrollAnchor? {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return null
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null
        val topView = recyclerView.getChildAt(0)
        val offset = topView?.top ?: 0
        return ScrollAnchor(firstVisible, offset)
    }

    private fun restoreScrollAnchor(anchor: ScrollAnchor, positionDelta: Int) {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        val targetPosition = (anchor.position + positionDelta).coerceAtLeast(0)
        if (targetPosition >= sectionAdapter.itemCount) return
        lm.scrollToPositionWithOffset(targetPosition, anchor.offset)
    }

    private fun requestRecentsRefresh(requireTop: Boolean, requireNotRecents: Boolean) {
        markRecentsRefreshPending(requireTop, requireNotRecents)
        maybeApplyPendingRecentsRefresh()
    }

    private fun markRecentsRefreshPending(requireTop: Boolean, requireNotRecents: Boolean) {
        pendingRecentsRefresh = true
        pendingRecentsRefreshRequiresTop = pendingRecentsRefreshRequiresTop || requireTop
        pendingRecentsRefreshRequiresNotRecents = pendingRecentsRefreshRequiresNotRecents || requireNotRecents
    }

    private fun maybeApplyPendingRecentsRefresh() {
        if (!pendingRecentsRefresh) return
        if (scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        val requiresNotRecents = pendingRecentsRefreshRequiresNotRecents
        val requiresTop = pendingRecentsRefreshRequiresTop && !requiresNotRecents
        if (requiresTop && !isNearTop()) return
        if (requiresNotRecents &&
            selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
            return
        }
        pendingRecentsRefresh = false
        pendingRecentsRefreshRequiresTop = false
        pendingRecentsRefreshRequiresNotRecents = false
        refreshRecentsFromStorage(allowInsertOrRemove = isNearTop())
    }

    private fun isNearTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        return firstVisible != RecyclerView.NO_POSITION && firstVisible <= recentsApplyTopThreshold
    }

    private fun isAtAbsoluteTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible != 0) return false
        val firstView = lm.findViewByPosition(0) ?: return false
        return firstView.top >= recyclerView.paddingTop
    }

    private fun SectionItem.categoryId(): String {
        return when (this) {
            is SectionItem.Header -> categoryId
            is SectionItem.Emoji -> categoryId
        }
    }

    companion object {
        private const val COMPACT_HEIGHT_DP = 177f
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMOJI = 1
        private const val LOG_TAG = "FluxSearch"

        // Shared by every picker: GIF previews decoding at the same time
        private val previewDecodes = Semaphore(2)
        private const val VIEW_TYPE_GIF = 100
        private const val VIEW_TYPE_GIF_HEADER = 102
        private const val GIF_COLUMNS = 3
        private const val VIEW_TYPE_SYMBOL = 101

        fun configuredHeightPx(context: Context): Int {
            val compactHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                COMPACT_HEIGHT_DP,
                context.resources.displayMetrics
            ).toInt()
            return if (SettingsManager.getEmojiPickerExpandedHeight(context)) {
                (compactHeight * 1.5f).toInt()
            } else {
                compactHeight
            }
        }
    }

    /** GIF grid: animated previews, decoded per cell from cached bytes. */
    /** GIF grid rows: a section title, or a GIF (marked when it's a favourite). */
    private sealed class GifItem {
        class Header(val title: String) : GifItem()
        class Gif(val gif: GifResult, val favourite: Boolean) : GifItem()
    }

    private inner class GifAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<GifItem> = emptyList()
        private var results: List<GifResult> = emptyList()
        private var query: String = ""

        /**
         * KLIPY's [list] for [forQuery]. With an empty search the user's favourite and recent
         * GIFs come first, each in its own section; while searching (recently used first on),
         * their GIFs whose titles match lead the results.
         */
        fun submit(list: List<GifResult>, forQuery: String = query) {
            results = list
            query = forQuery
            items = buildItems()
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
            redrawResults()
        }

        /** A favourite was added or taken out: rebuild the sections and stars. */
        fun refresh() = submit(results, query)

        fun ids(): List<String> = results.map { it.id }

        fun first(): GifResult? = items.firstNotNullOfOrNull { (it as? GifItem.Gif)?.gif }

        fun isHeader(position: Int): Boolean = items.getOrNull(position) is GifItem.Header

        private fun buildItems(): List<GifItem> {
            val favourites = GifCollections.favourites(context)
            val favouriteIds = favourites.map { it.id }.toSet()
            val own = mutableListOf<GifItem>()
            val shown = mutableSetOf<String>()
            fun section(titleRes: Int, gifs: List<GifResult>) {
                val fresh = gifs.filter { it.id !in shown }
                if (fresh.isEmpty()) return
                own += GifItem.Header(context.getString(titleRes))
                fresh.forEach { gif ->
                    shown += gif.id
                    own += GifItem.Gif(gif, gif.id in favouriteIds)
                }
            }
            if (query.isBlank()) {
                if (SettingsManager.getGifShowFavourites(context)) section(R.string.gif_section_favourites, favourites)
                if (SettingsManager.getGifShowRecents(context)) {
                    section(R.string.gif_section_recent, GifCollections.recents(context))
                }
                if (own.isNotEmpty() && results.any { it.id !in shown }) {
                    own += GifItem.Header(context.getString(R.string.gif_section_trending))
                }
            } else if (SettingsManager.getRecentsFirstInSearch(context)) {
                GifCollections.matching(favourites + GifCollections.recents(context), query).forEach { gif ->
                    if (shown.add(gif.id)) own += GifItem.Gif(gif, gif.id in favouriteIds)
                }
            }
            return own + results.filter { it.id !in shown }.map { GifItem.Gif(it, it.id in favouriteIds) }
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int =
            if (items[position] is GifItem.Header) VIEW_TYPE_GIF_HEADER else VIEW_TYPE_GIF

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == VIEW_TYPE_GIF_HEADER) {
                val title = TextView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(dpToPx(8f), dpToPx(8f), dpToPx(8f), dpToPx(4f))
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    alpha = 0.8f
                }
                return object : RecyclerView.ViewHolder(title) {}
            }
            val cell = FrameLayout(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(84f)).apply {
                    val margin = dpToPx(2f)
                    setMargins(margin, margin, margin, margin)
                }
            }
            val image = ImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = createTabBackground(false)
                clipToOutline = true
                isClickable = true
                isFocusable = true
            }
            val star = TextView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply { setMargins(0, dpToPx(2f), dpToPx(4f), 0) }
                text = "\u2605"
                textSize = 14f
                setTextColor(Color.rgb(0xFF, 0xD5, 0x4F))
                setShadowLayer(3f, 0f, 1f, Color.BLACK)
                visibility = View.GONE
            }
            cell.addView(image)
            cell.addView(star)
            return GifHolder(cell, image, star)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is GifItem.Header -> (holder.itemView as TextView).apply {
                    text = item.title
                    setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                }
                is GifItem.Gif -> (holder as GifHolder).bind(item.gif, item.favourite)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder as? GifHolder)?.clear()
        }
    }

    private inner class GifHolder(
        cell: View,
        private val image: ImageView,
        private val star: TextView
    ) : RecyclerView.ViewHolder(cell) {
        private var job: Job? = null

        fun bind(gif: GifResult, favourite: Boolean) {
            clear()
            image.contentDescription = gif.description
            star.visibility = if (favourite) View.VISIBLE else View.GONE
            image.setOnClickListener { onGifChosen?.invoke(gif) }
            // Long-press: add to the favourite GIFs, or take out
            image.setOnLongClickListener {
                val nowFavourite = GifCollections.toggleFavourite(context, gif)
                Toast.makeText(
                    context,
                    if (nowFavourite) R.string.gif_favourite_added else R.string.gif_favourite_removed,
                    Toast.LENGTH_SHORT
                ).show()
                gifAdapter.refresh()
                true
            }
            job = coroutineScope.launch {
                val drawable = try {
                    val bytes = KlipyGifs.previewBytes(context, gif.previewUrl)
                    // Decoding is the costly part: two at a time, so a screenful of GIFs
                    // can't crowd out the rest of the keyboard (symbol search, typing)
                    previewDecodes.withPermit {
                        withContext(Dispatchers.IO) {
                            ensureActive() // scrolled away while waiting
                            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@launch
                }
                image.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        }

        fun clear() {
            job?.cancel()
            job = null
            (image.drawable as? AnimatedImageDrawable)?.stop()
            image.setImageDrawable(null)
        }
    }

    /** Symbol grid: plain characters, named for accessibility. */
    private inner class SymbolAdapter : RecyclerView.Adapter<SymbolHolder>() {
        private var items: List<SymbolSearch.Entry> = emptyList()

        fun submit(list: List<SymbolSearch.Entry>) {
            items = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
            redrawResults()
        }

        fun first(): SymbolSearch.Entry? = items.firstOrNull()

        /** Drops a symbol the fonts turned out not to draw. */
        fun remove(entry: SymbolSearch.Entry) {
            val index = items.indexOf(entry)
            if (index < 0) return
            items = items.toMutableList().also { it.removeAt(index) }
            notifyItemRemoved(index)
            redrawResults()
        }

        override fun getItemCount(): Int = items.size

        override fun getItemViewType(position: Int): Int = VIEW_TYPE_SYMBOL

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymbolHolder {
            val cell = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44f))
                gravity = Gravity.CENTER
                textSize = 22f
                isClickable = true
                isFocusable = true
            }
            return SymbolHolder(cell)
        }

        override fun onBindViewHolder(holder: SymbolHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class SymbolHolder(private val cell: TextView) : RecyclerView.ViewHolder(cell) {
        fun bind(entry: SymbolSearch.Entry) {
            if (kaomojiSearch) {
                // Kaomoji: shrunk to fit their cell, on one line
                cell.setAutoSizeTextTypeUniformWithConfiguration(8, 18, 1, TypedValue.COMPLEX_UNIT_SP)
                cell.maxLines = 1
                cell.text = entry.symbol
                cell.contentDescription = entry.name
                cell.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                cell.setOnClickListener { onSymbolChosen(entry) }
                return
            }
            cell.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
            cell.maxLines = Int.MAX_VALUE
            cell.textSize = 22f
            // Further down the results, symbols are checked as they come into view
            val newlyChecked = !SymbolSearch.isChecked(entry.symbol)
            val drawable = SymbolSearch.canRender(entry.symbol)
            if (newlyChecked) SymbolSearch.saveVerdictsSoon(context)
            if (!drawable) {
                cell.text = ""
                cell.contentDescription = null
                cell.setOnClickListener(null)
                recyclerView.post { symbolAdapter.remove(entry) }
                return
            }
            cell.text = entry.symbol
            cell.contentDescription = entry.name
            cell.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
            cell.setOnClickListener { onSymbolChosen(entry) }
        }
    }
}
