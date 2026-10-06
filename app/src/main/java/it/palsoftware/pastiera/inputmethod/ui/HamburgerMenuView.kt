package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonHost
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonId
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonPosition
import it.palsoftware.pastiera.inputmethod.statusbar.CurvedCornerButtonDrawable
import it.palsoftware.pastiera.getMenuBarButtons
import it.palsoftware.pastiera.getTitan2EliteContourLeds
import it.palsoftware.pastiera.getTitan2EliteRoundedCornerInsetsEnabled
import it.palsoftware.pastiera.getTitan2EliteStraightOuterButtons
import it.palsoftware.pastiera.gifsAvailable

/**
 * Overlay menu that replaces the status bar row with fixed buttons.
 */
class HamburgerMenuView(
    private val context: Context,
    private val buttonRegistry: StatusBarButtonRegistry,
    /** The bar's corner-button width: the menu's first and last buttons match it, so they sit
     * where the bar's do and the LEDs between them stay clear (0: equal widths). */
    private val outerButtonWidthPx: () -> Int = { 0 }
) {

    companion object {
        private const val MAX_VERTICAL_PADDING_DP = 8f
        private const val MIN_BUTTON_HEIGHT_DP = 28f
    }

    // Every button the menu can show; which ones and in what order is a setting
    private val menuButtonIds = listOf(
        StatusBarButtonId.ExtraKeys,
        StatusBarButtonId.Symbols,
        StatusBarButtonId.Emoji,
        StatusBarButtonId.Gif,
        StatusBarButtonId.Microphone,
        StatusBarButtonId.Clipboard,
        StatusBarButtonId.Undo,
        StatusBarButtonId.Redo,
        StatusBarButtonId.Language,
        StatusBarButtonId.MinimalUi,
        StatusBarButtonId.SoftwareKeyboardMode,
        StatusBarButtonId.Settings
    )

    private var shownButtonIds: List<StatusBarButtonId> = menuButtonIds
    private var root: FrameLayout? = null
    private var row: LinearLayout? = null
    private val buttonHost = StatusBarButtonHost(context, buttonRegistry)
    private var currentButtons: List<StatusBarButtonHost.HostedButton> = emptyList()
    private var closeButton: ImageView? = null
    private var lastClipboardCount: Int? = null
    private var lastMicrophoneActive: Boolean? = null
    private var lastMicrophoneRms: Float? = null
    private var lastMinimalUiActive: Boolean? = null
    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            buttonHost.themeOverride = value?.let {
                StatusBarButtonStyles.ThemeOverride(
                    normalColor = it.statusBarButton,
                    pressedColor = it.accent,
                    iconColor = it.textAndIcons,
                    cornerRadiusRatio = it.chromeCornerRadiusRatio,
                    borderColor = it.divider,
                    borderWidthPx = dpToPx(1f)
                )
            }
            root?.setBackgroundColor(value?.background ?: Color.BLACK)
            closeButton?.let { applyCloseButtonTheme(it) }
        }

    fun attachTo(parent: FrameLayout) {
        val view = ensureView()
        if (view.parent !== parent) {
            (view.parent as? ViewGroup)?.removeView(view)
            parent.addView(view)
        }
    }

    fun show(callbacks: StatusBarCallbacks, onClose: () -> Unit) {
        val view = ensureView()
        view.visibility = View.VISIBLE
        view.bringToFront()
        view.post {
            buildButtons(callbacks, onClose)
        }
    }

    fun hide() {
        root?.visibility = View.GONE
    }

    fun isVisible(): Boolean = root?.visibility == View.VISIBLE

    fun updateClipboardCount(count: Int) {
        lastClipboardCount = count
        buttonHost.updateClipboardCount(count)
    }

    fun setMicrophoneActive(isActive: Boolean) {
        lastMicrophoneActive = isActive
        buttonHost.setMicrophoneActive(isActive)
    }

    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        lastMicrophoneRms = rmsdB
        buttonHost.updateMicrophoneAudioLevel(rmsdB)
    }

    fun setMinimalUiActive(isActive: Boolean) {
        lastMinimalUiActive = isActive
        buttonHost.setMinimalUiActive(isActive)
    }

    fun refreshLanguageText() {
        buttonHost.refreshLanguageText()
    }

    private fun ensureView(): FrameLayout {
        if (root != null) {
            return root!!
        }
        val verticalPadding = dpToPx(MAX_VERTICAL_PADDING_DP)
        val rowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(0, verticalPadding, 0, verticalPadding)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateButtonSizes(this)
            }
        }
        row = rowView
        root = FrameLayout(context).apply {
            setBackgroundColor(themeOverride?.background ?: Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            addView(rowView)
        }
        return root!!
    }

    private fun buildButtons(callbacks: StatusBarCallbacks, onClose: () -> Unit) {
        val rowView = row ?: return
        clearButtons()

        val menuCallbacks = StatusBarCallbacks(
            onClipboardRequested = {
                onClose()
                callbacks.onClipboardRequested?.invoke()
            },
            onSpeechRecognitionRequested = {
                onClose()
                callbacks.onSpeechRecognitionRequested?.invoke()
            },
            onEmojiPickerRequested = {
                onClose()
                callbacks.onEmojiPickerRequested?.invoke()
            },
            onGifSearchRequested = {
                onClose()
                callbacks.onGifSearchRequested?.invoke()
            },
            onLanguageSwitchRequested = {
                onClose()
                callbacks.onLanguageSwitchRequested?.invoke()
            },
            onHamburgerMenuRequested = null,
            onMinimalUiToggleRequested = {
                callbacks.onMinimalUiToggleRequested?.invoke()
            },
            onSoftwareKeyboardModeToggleRequested = {
                callbacks.onSoftwareKeyboardModeToggleRequested?.invoke()
            },
            onOpenSettings = {
                onClose()
                callbacks.onOpenSettings?.invoke()
            },
            onSymbolsPageRequested = {
                onClose()
                callbacks.onSymbolsPageRequested?.invoke()
            },
            onUndoRequested = {
                onClose()
                callbacks.onUndoRequested?.invoke()
            },
            onRedoRequested = {
                onClose()
                callbacks.onRedoRequested?.invoke()
            },
            onExtraKeysRequested = {
                onClose()
                callbacks.onExtraKeysRequested?.invoke()
            },
            onHapticFeedback = callbacks.onHapticFeedback
        )

        applyDynamicPadding(rowView)
        val buttonHeight = resolveButtonHeight(rowView)
        val closeButtonView = createCloseButton(onClose, buttonHeight)
        closeButton = closeButtonView
        rowView.addView(closeButtonView)
        val fallbackWidth = buttonHeight
        val hostedButtons = mutableListOf<StatusBarButtonHost.HostedButton>()
        // The buttons chosen in Settings, in their order; GIF only when GIFs are on and online
        val byKey = menuButtonIds.associateBy { it.key }
        shownButtonIds = SettingsManager.getMenuBarButtons(context).mapNotNull { byKey[it] }.filter {
            (it != StatusBarButtonId.Gif || SettingsManager.gifsAvailable(context)) &&
                it !in StatusBarButtonRegistry.hiddenForApp
        }
        shownButtonIds.forEach { id ->
            val hosted = buttonHost.getOrCreateButton(
                id,
                buttonHeight,
                menuCallbacks,
                fallbackWidth,
                buttonHeight
            ) ?: return@forEach
            hostedButtons.add(hosted)
            rowView.addView(hosted.container)
        }
        currentButtons = hostedButtons
        // The last button sits in the bottom-right display corner, like the bar's outer buttons;
        // one that was last before (buttons added after it) goes back to a plain button
        hostedButtons.dropLast(1).forEach { buttonHost.setOuterEdge(it.id, null) }
        hostedButtons.lastOrNull()?.let { buttonHost.setOuterEdge(it.id, StatusBarButtonPosition.RIGHT) }

        updateButtonSizes(rowView)
        applyStoredStates()
    }

    private fun clearButtons() {
        buttonHost.detachAll()
        currentButtons = emptyList()
        row?.removeAllViews()
    }

    private fun updateButtonSizes(rowView: LinearLayout) {
        // Close plus the buttons actually made (one the phone can't show, like a GIF button
        // offline, is left out rather than leaving every width unset)
        val totalButtons = rowView.childCount
        if (totalButtons == 0 || totalButtons != currentButtons.size + 1) {
            return
        }
        applyDynamicPadding(rowView)
        val spacing = dpToPx(3f)
        val availableWidth = rowView.width - rowView.paddingLeft - rowView.paddingRight
        if (availableWidth <= 0) {
            return
        }
        val equalWidth = ((availableWidth - spacing * (totalButtons - 1)) / totalButtons).coerceAtLeast(1)
        val outer = outerButtonWidthPx().takeIf { it > 0 && totalButtons >= 3 }
        // Outer buttons as wide as the bar's corner buttons, the rest sharing what's left
        val middleWidth = outer?.let {
            ((availableWidth - 2 * it - spacing * (totalButtons - 1)) / (totalButtons - 2)).coerceAtLeast(1)
        }
        fun widthAt(index: Int): Int = when {
            outer == null || middleWidth == null -> equalWidth
            index == 0 || index == totalButtons - 1 -> outer
            else -> middleWidth
        }
        // MATCH_PARENT, not a pixel height: on the Titan 2 Elite the chrome stretches this row after
        // the normal layout pass, and fixed heights got clipped whenever the row was re-laid out.
        val fill = ViewGroup.LayoutParams.MATCH_PARENT
        for (index in 0 until totalButtons) {
            val child = rowView.getChildAt(index)
            val current = child.layoutParams as? LinearLayout.LayoutParams
            val marginEnd = if (index == totalButtons - 1) 0 else spacing
            val buttonWidth = widthAt(index)
            if (current == null || current.width != buttonWidth || current.height != fill || current.marginEnd != marginEnd) {
                child.layoutParams = (current ?: LinearLayout.LayoutParams(buttonWidth, fill)).apply {
                    width = buttonWidth
                    height = fill
                    this.marginEnd = marginEnd
                }
            }
        }
        currentButtons.forEachIndexed { index, hosted ->
            buttonHost.updateButtonLayout(hosted.id, widthAt(index + 1), fill)
        }
    }

    private fun resolveButtonHeight(rowView: LinearLayout): Int {
        val height = rowView.height - rowView.paddingTop - rowView.paddingBottom
        return if (height > 0) height else dpToPx(39f)
    }

    private fun applyDynamicPadding(rowView: LinearLayout) {
        val rowHeight = rowView.height
        // With contoured LEDs the bar already keeps a band above them: less padding, taller buttons
        val maxPadding = dpToPx(
            if (it.palsoftware.pastiera.SettingsManager.getTitan2EliteContourLeds(context)) 2f else MAX_VERTICAL_PADDING_DP
        )
        if (rowHeight <= 0) {
            return
        }
        val minButtonHeight = dpToPx(MIN_BUTTON_HEIGHT_DP)
        val desiredPadding = ((rowHeight - minButtonHeight) / 2f).toInt().coerceAtLeast(0)
        val padding = minOf(maxPadding, desiredPadding)
        if (rowView.paddingTop != padding || rowView.paddingBottom != padding) {
            rowView.setPadding(rowView.paddingLeft, padding, rowView.paddingRight, padding)
        }
    }

    private fun applyStoredStates() {
        lastClipboardCount?.let { buttonHost.updateClipboardCount(it) }
        lastMicrophoneActive?.let { buttonHost.setMicrophoneActive(it) }
        lastMicrophoneRms?.let { buttonHost.updateMicrophoneAudioLevel(it) }
        lastMinimalUiActive?.let { buttonHost.setMinimalUiActive(it) }
        buttonHost.refreshLanguageText()
    }

    private fun createCloseButton(
        onClose: () -> Unit,
        heightPx: Int
    ): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.status_bar_button_close_menu_description)
            scaleType = ImageView.ScaleType.CENTER
            applyCloseButtonTheme(this, heightPx)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClose()
            }
        }
    }

    private fun applyCloseButtonTheme(button: ImageView, heightPx: Int? = null) {
        val theme = themeOverride
        val height = heightPx?.takeIf { it > 0 }
            ?: button.layoutParams?.height?.takeIf { it > 0 }
            ?: button.height.takeIf { it > 0 }
            ?: dpToPx(39f)
        button.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        val normalColor = theme?.statusBarButton ?: StatusBarButtonStyles.NORMAL_COLOR
        val pressedColor = theme?.accent ?: StatusBarButtonStyles.PRESSED_BLUE
        val cornerRadiusRatio = theme?.chromeCornerRadiusRatio ?: StatusBarButtonStyles.BUTTON_CORNER_RADIUS_RATIO
        val borderWidth = if (theme != null) dpToPx(1f) else 0
        // Leftmost menu button: the bottom-left corner button, like the bar's outer button
        button.setTag(R.id.tag_outer_edge_button, StatusBarButtonPosition.LEFT)
        button.background = if (
            it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context) &&
            !it.palsoftware.pastiera.SettingsManager.getTitan2EliteStraightOuterButtons(context)
        ) {
            CurvedCornerButtonDrawable(
                button, normalColor, pressedColor, height * cornerRadiusRatio,
                theme?.divider, borderWidth, leftEdge = true
            )
        } else {
            StatusBarButtonStyles.createButtonDrawable(
                heightPx = height,
                normalColor = normalColor,
                pressedColor = pressedColor,
                cornerRadiusRatio = cornerRadiusRatio,
                borderColor = theme?.divider,
                borderWidthPx = borderWidth
            )
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
