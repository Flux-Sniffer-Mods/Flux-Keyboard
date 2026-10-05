package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi

/**
 * Inline autofill (Android 11+): password managers and Android's autofill offer their chips
 * (saved logins, one-time codes, addresses) to the keyboard, which shows them in the suggestion
 * bar. The chips are drawn by the autofill service; tapping one fills the field.
 */
@RequiresApi(Build.VERSION_CODES.R)
object InlineAutofill {
    private const val MAX_CHIPS = 6
    private const val BUTTON_HEIGHT_DP = 36
    private const val CHIP_INSET_DP = 4
    private const val CHIP_MIN_HEIGHT_DP = 24
    private const val CHIP_MAX_HEIGHT_DP = 64
    private const val CHIP_MIN_WIDTH_DP = 64
    private const val CHIP_MAX_WIDTH_DP = 260

    /**
     * The chips Android asks the password manager for: see-through, with [textColour] text, so
     * the bar draws the suggestion buttons' own background behind them ([chipColour] there).
     */
    fun request(context: Context, chipColour: Int? = null, textColour: Int? = null): InlineSuggestionsRequest {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val ui = InlineSuggestionUi.newStyleBuilder()
        if (chipColour != null && textColour != null) {
            ui.setChipStyle(
                androidx.autofill.inline.common.ViewStyle.Builder()
                    .setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    .setPadding(dp(12), 0, dp(12), 0)
                    .build()
            )
            ui.setTitleStyle(
                androidx.autofill.inline.common.TextViewStyle.Builder()
                    .setTextColor(textColour)
                    .setTextSize(15f)
                    .build()
            )
            ui.setSubtitleStyle(
                androidx.autofill.inline.common.TextViewStyle.Builder()
                    .setTextColor((textColour and 0x00FFFFFF) or (0xB3 shl 24))
                    // The title's size, so both sit on one line (smaller, it rode higher)
                    .setTextSize(15f)
                    .build()
            )
            ui.setSingleIconChipStyle(
                androidx.autofill.inline.common.ViewStyle.Builder()
                    .setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    .setPadding(dp(8), 0, dp(8), 0)
                    .build()
            )
        }
        val style = UiVersions.newStylesBuilder()
            .addStyle(ui.build())
            .build()
        val spec = InlinePresentationSpec.Builder(Size(dp(CHIP_MIN_WIDTH_DP), dp(CHIP_MIN_HEIGHT_DP)), Size(dp(CHIP_MAX_WIDTH_DP), dp(CHIP_MAX_HEIGHT_DP)))
            .setStyle(style)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(MAX_CHIPS)
            .build()
    }

    /** Inflates every chip, then hands them over in the service's order (pinned ones last). */
    fun inflate(context: Context, suggestions: List<InlineSuggestion>, heightPx: Int? = null, onReady: (List<View>) -> Unit) {
        val ordered = suggestions.take(MAX_CHIPS).sortedBy { it.info.isPinned }
        val views = arrayOfNulls<View>(ordered.size)
        var pending = ordered.size
        // A set size inside the request's: a chip left to size itself (wrap content) came out
        // 0 px wide on the Titan 2 Elite, there but invisible. Wide enough for a login's name,
        // sharing the bar when there are several
        val density = context.resources.displayMetrics.density
        // A little shorter than the bar's suggestion buttons: the bar centres each chip in a
        // button-shaped background of its own
        val height = ((heightPx ?: (BUTTON_HEIGHT_DP * density).toInt()) - (CHIP_INSET_DP * 2 * density).toInt())
            .coerceIn((CHIP_MIN_HEIGHT_DP * density).toInt(), (CHIP_MAX_HEIGHT_DP * density).toInt())
        val share = (context.resources.displayMetrics.widthPixels * 0.7f / ordered.size.coerceAtLeast(1)).toInt()
        val width = share.coerceIn((CHIP_MIN_WIDTH_DP * density).toInt(), (CHIP_MAX_WIDTH_DP * density).toInt())
        val size = Size(width, height)
        ordered.forEachIndexed { index, suggestion ->
            try {
                suggestion.inflate(context, size, context.mainExecutor) { view ->
                    // Drawn above the keyboard window: below it (the default), the bar's own
                    // backgrounds and corner fills can cover the chip, leaving an empty space
                    (view as? android.widget.inline.InlineContentView)?.setZOrderedOnTop(true)
                    view?.layoutParams = ViewGroup.LayoutParams(width, height)
                    if (view == null) DebugCaptureStore.recordAutofill("chip ${index + 1} didn't inflate")
                    views[index] = view
                    if (--pending == 0) onReady(views.filterNotNull())
                }
            } catch (error: Exception) {
                DebugCaptureStore.recordAutofill("chip ${index + 1} failed: ${error.javaClass.simpleName}: ${error.message}")
                if (--pending == 0) onReady(views.filterNotNull())
            }
        }
    }
}
