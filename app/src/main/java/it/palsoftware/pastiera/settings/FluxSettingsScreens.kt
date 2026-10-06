package it.palsoftware.pastiera.settings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.palsoftware.pastiera.data.desktop.DesktopKeyboardLayout
import it.palsoftware.pastiera.inputmethod.ClicksLauncherButtonAccessibilityService
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import it.palsoftware.pastiera.RestrictedSettings
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.apps.AppListHelper
import it.palsoftware.pastiera.apps.HiddenKeyboardAppsDialog
import it.palsoftware.pastiera.device.Titan2EliteRoundedCornerRows
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.getEmojiKeyAutoClose
import it.palsoftware.pastiera.getEmojiKeyOpensLayer
import it.palsoftware.pastiera.getEmojiLayerCloseOnKey
import it.palsoftware.pastiera.getEmojiLayerCloseOnTap
import it.palsoftware.pastiera.getEmojiLayerGifKey
import it.palsoftware.pastiera.getEmojiLayerPages
import it.palsoftware.pastiera.getEmojiLayerRecentsKey
import it.palsoftware.pastiera.getEmojiLayerTypeToSearch
import it.palsoftware.pastiera.getEmojiPickerExpandedHeight
import it.palsoftware.pastiera.getEmojiPickerFocusSearch
import it.palsoftware.pastiera.getEmojiPickerKey
import it.palsoftware.pastiera.getEmojiSearchEnterPicks
import it.palsoftware.pastiera.getEmojiStickyTap
import it.palsoftware.pastiera.getGifFocusSearch
import it.palsoftware.pastiera.getGifSearchEnterPicks
import it.palsoftware.pastiera.getGifShowFavourites
import it.palsoftware.pastiera.getGifShowRecents
import it.palsoftware.pastiera.getGifsEnabled
import it.palsoftware.pastiera.getHiddenAppStandardModifiers
import it.palsoftware.pastiera.getHiddenKeyboardApps
import it.palsoftware.pastiera.getKaomojiCloseOnKey
import it.palsoftware.pastiera.getKaomojiCloseOnTap
import it.palsoftware.pastiera.getRecentsFirstInSearch
import it.palsoftware.pastiera.getSearchKey
import it.palsoftware.pastiera.getSymAutoClose
import it.palsoftware.pastiera.getSymAutoCloseOnTouch
import it.palsoftware.pastiera.getSymbolSearchEnterPicks
import it.palsoftware.pastiera.getSymbolsCloseOnKey
import it.palsoftware.pastiera.getSymbolsCloseOnTap
import it.palsoftware.pastiera.getSymbolsPages
import it.palsoftware.pastiera.getSymbolsTypeToSearch
import it.palsoftware.pastiera.getTitan2EliteContourLeds
import it.palsoftware.pastiera.getTitan2EliteFillCorners
import it.palsoftware.pastiera.getTitan2EliteRoundedCornerInsetsEnabled
import it.palsoftware.pastiera.getUserKlipyApiKey
import it.palsoftware.pastiera.hasBuiltInKlipyApiKey
import it.palsoftware.pastiera.isAllowedEmojiPickerKey
import it.palsoftware.pastiera.isOfflineMode
import it.palsoftware.pastiera.setEmojiKeyAutoClose
import it.palsoftware.pastiera.setEmojiKeyOpensLayer
import it.palsoftware.pastiera.setEmojiLayerCloseOnKey
import it.palsoftware.pastiera.setEmojiLayerCloseOnTap
import it.palsoftware.pastiera.setEmojiLayerGifKey
import it.palsoftware.pastiera.setEmojiLayerPages
import it.palsoftware.pastiera.setEmojiLayerRecentsKey
import it.palsoftware.pastiera.setEmojiLayerTypeToSearch
import it.palsoftware.pastiera.setEmojiPickerExpandedHeight
import it.palsoftware.pastiera.setEmojiPickerFocusSearch
import it.palsoftware.pastiera.setEmojiPickerKey
import it.palsoftware.pastiera.setEmojiSearchEnterPicks
import it.palsoftware.pastiera.setEmojiStickyTap
import it.palsoftware.pastiera.setGifFocusSearch
import it.palsoftware.pastiera.setGifSearchEnterPicks
import it.palsoftware.pastiera.setGifShowFavourites
import it.palsoftware.pastiera.setGifShowRecents
import it.palsoftware.pastiera.setGifsEnabled
import it.palsoftware.pastiera.setHiddenAppStandardModifiers
import it.palsoftware.pastiera.setKaomojiCloseOnKey
import it.palsoftware.pastiera.setKaomojiCloseOnTap
import it.palsoftware.pastiera.setKlipyApiKey
import it.palsoftware.pastiera.setOfflineMode
import it.palsoftware.pastiera.setRecentsFirstInSearch
import it.palsoftware.pastiera.setSearchKey
import it.palsoftware.pastiera.setSymAutoClose
import it.palsoftware.pastiera.setSymAutoCloseOnTouch
import it.palsoftware.pastiera.setSymbolSearchEnterPicks
import it.palsoftware.pastiera.setSymbolsCloseOnKey
import it.palsoftware.pastiera.setSymbolsCloseOnTap
import it.palsoftware.pastiera.setSymbolsPages
import it.palsoftware.pastiera.setSymbolsTypeToSearch
import it.palsoftware.pastiera.setTitan2EliteContourLeds
import it.palsoftware.pastiera.setTitan2EliteFillCorners

/*
 * Flux Keyboard settings, one screen per area (Settings > Flux Keyboard):
 *   Emoji & GIFs          - emoji key, emoji layer keys, GIF search
 *   Titan 2 Elite screen  - rounded corners, outer buttons, status bar
 *   Hidden keyboard apps  - apps where the keyboard stays out of sight, and the service they need
 *   Linux desktop         - Ctrl and Sym, and the desktop's keyboard layout
 */

/** The top bar with a back arrow and a scrolling column, as on the other settings screens. */
@Composable
internal fun FluxScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

/** A short explanation at the top of a screen or under a section header. */
@Composable
internal fun FluxNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** A row with a title, a description and a switch; tapping anywhere on it toggles. */
@Composable
internal fun FluxSwitchRow(
    linkId: String?,
    title: String,
    description: String,
    checked: Boolean,
    icon: String? = null,
    appPackage: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId) { onCheckedChange(!checked) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (appPackage != null) FluxAppIcon(appPackage) else FluxRowIcon(icon ?: fluxRowIcons[linkId])
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** A row with a title and a description that does something when tapped. */
/** One checkbox of a [FluxCheckTable]: how to read it, and what setting it does. */
internal class CheckCell(val read: () -> Boolean, val linkId: String? = null, val write: (Boolean) -> Unit)

/**
 * One setting with several parts in a table: a row per thing (a label, or none for a single
 * row), a checkbox per column. Keeps related switches together instead of a row each.
 */
@Composable
internal fun FluxCheckTable(
    linkId: String?,
    title: String,
    description: String,
    columns: List<String>,
    rows: List<Pair<String, List<CheckCell?>>>
) {
    val cellWidth = 76.dp
    Column(
        modifier = Modifier.fillMaxWidth().settingRow(linkId).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // A single unnamed row: a pill per column, sharing the width, on or off
        if (rows.size == 1 && rows[0].first.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                columns.zip(rows[0].second).forEach { (column, cell) ->
                    if (cell == null) {
                        Spacer(modifier = Modifier.weight(1f))
                        return@forEach
                    }
                    FluxTogglePill(column, cell, Modifier.weight(1f))
                }
            }
            return@Column
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            columns.forEach { column ->
                Text(
                    column,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(cellWidth)
                )
            }
        }
        rows.forEach { (label, cells) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                cells.forEach { cell ->
                    // An empty cell where the column doesn't apply to the row
                    if (cell == null) {
                        Spacer(modifier = Modifier.width(cellWidth))
                        return@forEach
                    }
                    var checked by remember { mutableStateOf(cell.read()) }
                    Box(
                        modifier = Modifier.width(cellWidth).settingRow(cell.linkId),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = checked,
                            onCheckedChange = { on -> checked = on; cell.write(on) }
                        )
                    }
                }
            }
        }
    }
}

/** A pill that is on (filled) or off (outlined), for one part of a [FluxCheckTable]. */
@Composable
private fun FluxTogglePill(label: String, cell: CheckCell, modifier: Modifier) {
    var checked by remember { mutableStateOf(cell.read()) }
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 48.dp).settingRow(cell.linkId),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (checked) colors.primary else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (checked) colors.onPrimary else colors.onSurface,
        border = if (checked) null else androidx.compose.foundation.BorderStroke(1.dp, colors.outline),
        onClick = { checked = !checked; cell.write(checked) }
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
internal fun FluxActionRow(
    linkId: String?,
    title: String,
    description: String,
    icon: String? = null,
    appPackage: String? = null,
    onClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId, onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (appPackage != null) FluxAppIcon(appPackage) else FluxRowIcon(icon ?: fluxRowIcons[linkId])
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A row's Unicode icon, in the accent colour; nothing for rows without one. */
@Composable
private fun FluxRowIcon(icon: String?) {
    if (icon == null) return
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Text(
            icon,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** App icons drawn so far, by package: lists of apps redraw often. */
private val appIconCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

/** An app's own icon, so a chosen app is told apart at a glance; nothing when it isn't installed. */
@Composable
internal fun FluxAppIcon(packageName: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(packageName) {
        appIconCache[packageName] ?: runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()?.also { appIconCache[packageName] = it }
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
        }
    }
}

/** Text symbols (never emoji: VS15 where a symbol could be drawn as one) for rows by setting. */
private const val TEXT = "\uFE0E"
internal val fluxRowIcons: Map<String?, String> = mapOf(
    // Shizuku extras
    "root.backlight_screen" to "\u263C",          // ☼
    "root.backlight_brightness" to "\u25D0",      // ◐
    "root.backlight_flash" to "\u2726",           // ✦
    "main.root.shizuku_boot" to "\u21BB",         // ↻
    // Terminal mode
    SettingLinkIds.TERMINAL_MODE_ENABLED to "\u276F",       // ❯
    SettingLinkIds.TERMINAL_MODE_HIDE_KEYBOARD to "\u2298", // ⊘
    "terminal_mode.show_leds" to "\u25CF",                  // ●
    SettingLinkIds.TERMINAL_MODE_EMOJI_KEY to "\u263A$TEXT",// ☺
    "terminal_mode.termux_setup" to "\u2398",               // ⎘
    // Trackpad
    "trackpad.capture_while_typing" to "\u21C6",            // ⇆
    "trackpad.suggestion_swipe_directions" to "\u21C4",     // ⇄
    "trackpad.swipe_learning" to "\u2248",                  // ≈
    "trackpad.phone_settings" to "\u2699$TEXT",             // ⚙
    SettingLinkIds.ADVANCED_TRACKPAD_GESTURES to "\u21C5",  // ⇅
    // Privacy and typing
    SettingLinkIds.PRIVACY_CLEAN_LINKS to "\u29C9",         // ⧉
    SettingLinkIds.PRIVACY_INCOGNITO_ALWAYS to "\u25CC",    // ◌
    SettingLinkIds.PRIVACY_INCOGNITO_FOLLOW_APPS to "\u25CC",
    SettingLinkIds.PRIVACY_ONE_TIME_CODES to "#",
    SettingLinkIds.PRIVACY_PASTE_SUGGESTION to "\u2398",    // ⎘
    SettingLinkIds.PRIVACY_PASTE_IN_PASSWORD_FIELDS to "\u2217", // ∗
    "offline.enabled" to "\u2298",                          // ⊘
    SettingLinkIds.AUTO_CORRECTION_SPELL_CHECKER to "\u2713",   // ✓
    "auto_correction.phone_spell_checker" to "\u2713",
    SettingLinkIds.AUTO_CORRECTION_INLINE_AUTOFILL to "\u26BF", // ⚿
    SettingLinkIds.AUTO_CORRECTION_EMOJI_SUGGESTIONS to "\u263A$TEXT",
    "auto_correction.show_add_word" to "+",
    "auto_correction.add_last_word_shortcut" to "+",
    SettingLinkIds.TEXT_INPUT_SEARCH_BAR_WAITS to "\u2315",     // ⌕
    // Emoji, symbols and GIFs
    "flux_emoji.gif_favourites" to "\u2605",                // ★
    "flux_emoji.gif_recents" to "\u21BA",                   // ↺
    "flux_emoji.recents_first" to "\u21BA",
    "flux_emoji.layer_recents_first" to "\u21BA",
    "flux_emoji.search_key" to "\u2315",
    "flux_emoji.search_page" to "\u2315",
    "flux_emoji.emoji_sticky" to "\u263A$TEXT",
    SettingLinkIds.MAIN_EMOJI_PROFILES to "\u263A$TEXT",
    // Apps and the quick launcher
    "quick_launcher.app_shortcuts" to "\u2318",             // ⌘
    "quick_launcher.listed_app_shortcuts" to "\u2318",
    "quick_launcher.add_shortcut" to "+",
    "quick_launcher.termux_scripts" to "\u276F",
    "quick_launcher.niagara_back_returns" to "\u21A9",      // ↩
    SettingLinkIds.APP_SHORTCUTS_ENABLED to "\u2318",
    SettingLinkIds.APP_SHORTCUTS_SUGGESTIONS to "\u2318",
    // LEDs and the app
    SettingLinkIds.LED_INDIVIDUAL_COLORS to "\u25CF",
    SettingLinkIds.LED_LOCKED_ANIMATION to "\u25C9",        // ◉
    "led_colors.emoji_led" to "\u25CF",
    SettingLinkIds.FORK_UPDATE_CHANNEL to "\u21E3",         // ⇣
    SettingLinkIds.DEVELOPER_OPTIONS_ENABLED to "\u2699$TEXT",
    SettingLinkIds.ADVANCED_SHOW_RELEASE_NOTES_TUTORIAL to "\u24D8", // ⓘ
    "advanced.corner_calibration" to "\u25DC"               // ◜
)

/** The Titan 2 Elite screen settings apply here (on the phone, or with its rounded corners on). */
internal fun fluxTitanScreenAvailable(context: Context): Boolean =
    DeviceSpecific.isTitan2EliteDevice() || SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)

/** The keyboard's accessibility service is on: hidden apps' status LEDs, panels, Ctrl and Sym need it. */
internal fun isPastieraAccessibilityServiceOn(context: Context): Boolean {
    val expected = ComponentName(context, ClicksLauncherButtonAccessibilityService::class.java)
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    return manager
        .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
        }
}

// ------------------------------------------------------------------ Emoji & GIFs


/** Rows on Emoji, symbols & GIFs' search page (opened when settings search lands on one). */
private val FLUX_EMOJI_SEARCH_PAGE_IDS = setOf(
    "flux_emoji.focus_picker", "flux_emoji.focus_gif", "flux_emoji.type_to_search_layer",
    "flux_emoji.type_to_search_symbols", "flux_emoji.search_key", "flux_emoji.enter_emoji",
    "flux_emoji.recents_first"
)

@Composable
fun FluxEmojiGifsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var emojiPickerKey by remember {
        mutableStateOf(SettingsManager.getEmojiPickerKey(context))
    }
    var showEmojiPickerKeyDialog by remember { mutableStateOf(false) }
    var emojiKeyOpensLayer by remember { mutableStateOf(SettingsManager.getEmojiKeyOpensLayer(context)) }
    var emojiKeyAutoClose by remember { mutableStateOf(SettingsManager.getEmojiKeyAutoClose(context)) }
    var emojiLayerRecentsKey by remember { mutableStateOf(SettingsManager.getEmojiLayerRecentsKey(context)) }
    var showRecentsKeyDialog by remember { mutableStateOf(false) }
    var gifsEnabled by remember { mutableStateOf(SettingsManager.getGifsEnabled(context)) }
    var klipyApiKey by remember { mutableStateOf(SettingsManager.getUserKlipyApiKey(context)) }
    var emojiLayerGifKey by remember { mutableStateOf(SettingsManager.getEmojiLayerGifKey(context)) }
    var showGifKeyDialog by remember { mutableStateOf(false) }
    var recentsFirst by remember { mutableStateOf(SettingsManager.getRecentsFirstInSearch(context)) }
    var searchKey by remember { mutableStateOf(SettingsManager.getSearchKey(context)) }
    var showSearchKeyDialog by remember { mutableStateOf(false) }
    var gifFavourites by remember { mutableStateOf(SettingsManager.getGifShowFavourites(context)) }
    var gifRecents by remember { mutableStateOf(SettingsManager.getGifShowRecents(context)) }
    var emojiPickerExpandedHeight by remember { mutableStateOf(SettingsManager.getEmojiPickerExpandedHeight(context)) }
    var emojiSticky by remember { mutableStateOf(SettingsManager.getEmojiStickyTap(context)) }

    val highlightedSettingId = LocalSettingHighlightId.current
    var searchPage by remember { mutableStateOf(settingsChild(context, "flux_emoji") == "search") }
    LaunchedEffect(highlightedSettingId) {
        if (highlightedSettingId in FLUX_EMOJI_SEARCH_PAGE_IDS) searchPage = true
    }

    FluxScreenScaffold(
        stringResource(if (searchPage) R.string.flux_search_page_title else R.string.settings_emoji_symbols_gifs_title),
        onBack,
        modifier
    ) {
        if (searchPage) {
            // Search in each screen, one table: ready to type, letters searching, Enter picking.
            // A layer shown as pages types what its keys hold, so letters don't search there.
            val emojiPagesOn = SettingsManager.getEmojiLayerPages(context)
            val symbolPagesOn = SettingsManager.getSymbolsPages(context)
            FluxCheckTable(
                linkId = "flux_emoji.enter_emoji",
                title = stringResource(R.string.search_table_title),
                description = stringResource(R.string.search_table_description),
                columns = listOf(
                    stringResource(R.string.enter_picks_emoji),
                    stringResource(R.string.enter_picks_symbols),
                    stringResource(R.string.enter_picks_gifs)
                ),
                rows = buildList {
                    add(stringResource(R.string.search_table_ready) to listOf(
                        CheckCell({ SettingsManager.getEmojiPickerFocusSearch(context) }, "flux_emoji.focus_picker") { SettingsManager.setEmojiPickerFocusSearch(context, it) },
                        null,
                        CheckCell({ SettingsManager.getGifFocusSearch(context) }, "flux_emoji.focus_gif") { SettingsManager.setGifFocusSearch(context, it) }
                    ))
                    if (!emojiPagesOn || !symbolPagesOn) add(stringResource(R.string.search_table_letters) to listOf(
                        if (emojiPagesOn) null else CheckCell({ SettingsManager.getEmojiLayerTypeToSearch(context) }, "flux_emoji.type_to_search_layer") { SettingsManager.setEmojiLayerTypeToSearch(context, it) },
                        if (symbolPagesOn) null else CheckCell({ SettingsManager.getSymbolsTypeToSearch(context) }, "flux_emoji.type_to_search_symbols") { SettingsManager.setSymbolsTypeToSearch(context, it) },
                        null
                    ))
                    add(stringResource(R.string.search_table_enter) to listOf(
                        CheckCell({ SettingsManager.getEmojiSearchEnterPicks(context) }) { SettingsManager.setEmojiSearchEnterPicks(context, it) },
                        CheckCell({ SettingsManager.getSymbolSearchEnterPicks(context) }) { SettingsManager.setSymbolSearchEnterPicks(context, it) },
                        CheckCell({ SettingsManager.getGifSearchEnterPicks(context) }) { SettingsManager.setGifSearchEnterPicks(context, it) }
                    ))
                }
            )
            FluxActionRow(
                linkId = "flux_emoji.search_key",
                title = stringResource(R.string.search_key_title),
                description = stringResource(R.string.search_key_description) + "\n" +
                    if (searchKey == KeyEvent.KEYCODE_UNKNOWN) {
                        stringResource(R.string.emoji_layer_recents_key_off)
                    } else {
                        stringResource(R.string.emoji_layer_recents_key_current, getLetterFromKeyCode(searchKey))
                    },
                onClick = { showSearchKeyDialog = true }
            )


            SettingsSectionDivider(stringResource(R.string.flux_section_recents_search))
            FluxSwitchRow(
                linkId = "flux_emoji.recents_first",
                title = stringResource(R.string.flux_recents_first_title),
                description = stringResource(R.string.flux_recents_first_description),
                checked = recentsFirst,
                onCheckedChange = { enabled ->
                    recentsFirst = enabled
                    SettingsManager.setRecentsFirstInSearch(context, enabled)
                }
            )
        } else {
            FluxNote(stringResource(R.string.flux_emoji_gifs_note))

            SettingsSectionDivider(stringResource(R.string.flux_section_emoji_key))

            Surface(
                modifier = Modifier.settingRow("flux_emoji.picker_key")
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { showEmojiPickerKeyDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEmotions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.emoji_picker_key_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = emojiPickerKeyLabel(context, emojiPickerKey),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            if (emojiPickerKey != KeyEvent.KEYCODE_UNKNOWN) {
                FluxSwitchRow(
                    linkId = "flux_emoji.emoji_sticky",
                    title = stringResource(R.string.emoji_sticky_title),
                    description = stringResource(R.string.emoji_sticky_description),
                    checked = emojiSticky,
                    onCheckedChange = { emojiSticky = it; SettingsManager.setEmojiStickyTap(context, it) }
                )
                // What the emoji key opens
                Column(
                    modifier = Modifier.settingRow("flux_emoji.key_target")
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.emoji_key_target_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    listOf(false to R.string.emoji_key_target_picker, true to R.string.emoji_key_target_layer)
                        .forEach { (layer, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    emojiKeyOpensLayer = layer
                                    SettingsManager.setEmojiKeyOpensLayer(context, layer)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = emojiKeyOpensLayer == layer,
                                    onClick = {
                                        emojiKeyOpensLayer = layer
                                        SettingsManager.setEmojiKeyOpensLayer(context, layer)
                                    }
                                )
                                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                }

                HorizontalDivider()

                // Auto-close for the emoji key's screens, separate from SYM auto-close
                Row(
                    modifier = Modifier.settingRow("flux_emoji.key_auto_close")
                        .fillMaxWidth()
                        .clickable {
                            emojiKeyAutoClose = !emojiKeyAutoClose
                            SettingsManager.setEmojiKeyAutoClose(context, emojiKeyAutoClose)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.emoji_key_auto_close_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.emoji_key_auto_close_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = emojiKeyAutoClose,
                        onCheckedChange = { enabled ->
                            emojiKeyAutoClose = enabled
                            SettingsManager.setEmojiKeyAutoClose(context, enabled)
                        }
                    )
                }

                HorizontalDivider()
            }

            SettingsSectionDivider(stringResource(R.string.flux_section_emoji_layer))
            // The emoji layer and the symbols page as pages (Q back, P on), recents first
            var layerPages by remember { mutableStateOf(SettingsManager.getEmojiLayerPages(context)) }
            var symbolPagesOn by remember { mutableStateOf(SettingsManager.getSymbolsPages(context)) }
            FluxCheckTable(
                linkId = "flux_emoji.layer_pages",
                title = stringResource(R.string.pages_table_title),
                description = stringResource(R.string.pages_table_description),
                columns = listOf(stringResource(R.string.close_after_emoji_layer), stringResource(R.string.close_after_symbols)),
                rows = listOf(
                    "" to listOf(
                        CheckCell({ SettingsManager.getEmojiLayerPages(context) }) {
                            layerPages = it
                            SettingsManager.setEmojiLayerPages(context, it)
                        },
                        CheckCell({ SettingsManager.getSymbolsPages(context) }, "flux_emoji.symbols_pages") {
                            symbolPagesOn = it
                            SettingsManager.setSymbolsPages(context, it)
                        }
                    )
                )
            )
            // Profiles fill the emoji layer's own keys: only there without pages
            if (!layerPages) {
                FluxActionRow(
                    linkId = SettingLinkIds.MAIN_EMOJI_PROFILES,
                    title = stringResource(R.string.emoji_profiles_title),
                    description = stringResource(R.string.emoji_profiles_description),
                    onClick = { openSettingsPage(context, SettingsPage(SettingsDestination.EmojiProfiles)) }
                )
            }
            // The default skin tone: the picker and the layer's pages show emoji in it
            var skinTone by remember { mutableStateOf(it.palsoftware.pastiera.data.emoji.EmojiSkinTone.get(context)) }
            Column(
                modifier = Modifier.settingRow("flux_emoji.skin_tone")
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.emoji_skin_tone_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.emoji_skin_tone_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..it.palsoftware.pastiera.data.emoji.EmojiSkinTone.MODIFIERS.size).forEach { tone ->
                        val hand = "\uD83D\uDC4B" + (if (tone == 0) "" else it.palsoftware.pastiera.data.emoji.EmojiSkinTone.MODIFIERS[tone - 1])
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (skinTone == tone) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable {
                                skinTone = tone
                                it.palsoftware.pastiera.data.emoji.EmojiSkinTone.set(context, tone)
                            }
                        ) {
                            Text(text = hand, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
            // The Recents key and recent emoji first only matter on a layer that isn't pages
            if (!layerPages || !symbolPagesOn) {
                // Recents key on the emoji layer
                Surface(
                    modifier = Modifier.settingRow("flux_emoji.recents_key")
                        .fillMaxWidth()
                        .clickable { showRecentsKeyDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.emoji_layer_recents_key_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (emojiLayerRecentsKey == KeyEvent.KEYCODE_UNKNOWN) {
                                    stringResource(R.string.emoji_layer_recents_key_off)
                                } else {
                                    stringResource(R.string.emoji_layer_recents_key_current, getLetterFromKeyCode(emojiLayerRecentsKey))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (!layerPages) {
                // Recent emoji first on the layer, and how many keys they take
                var layerRecentsFirst by remember { mutableStateOf(it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.enabled(context)) }
                var layerRecentsCount by remember { mutableStateOf(it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.count(context)) }
                FluxSwitchRow(
                    linkId = "flux_emoji.layer_recents_first",
                    title = stringResource(R.string.emoji_layer_recents_first_title),
                    description = stringResource(R.string.emoji_layer_recents_first_description),
                    checked = layerRecentsFirst,
                    onCheckedChange = { enabled ->
                        layerRecentsFirst = enabled
                        it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.setEnabled(context, enabled)
                    }
                )
                if (layerRecentsFirst) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.emoji_layer_recents_count, layerRecentsCount),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        androidx.compose.material3.Slider(
                            value = layerRecentsCount.toFloat(),
                            onValueChange = { value ->
                                layerRecentsCount = value.toInt()
                                it.palsoftware.pastiera.data.emoji.EmojiLayerRecents.setCount(context, layerRecentsCount)
                            },
                            valueRange = 1f..10f,
                            steps = 8
                        )
                    }
                }

            }

            SettingsSectionDivider(stringResource(R.string.flux_section_sym_picker))
            // When each page closes after a pick: typed with a key or tapped on screen
            FluxCheckTable(
                linkId = "sym.auto_close",
                title = stringResource(R.string.close_after_title),
                description = stringResource(R.string.close_after_description),
                columns = listOf(stringResource(R.string.close_after_typed), stringResource(R.string.close_after_tapped)),
                rows = listOf(
                    stringResource(R.string.close_after_emoji_layer) to listOf(
                        CheckCell({ SettingsManager.getEmojiLayerCloseOnKey(context) }) { SettingsManager.setEmojiLayerCloseOnKey(context, it) },
                        CheckCell({ SettingsManager.getEmojiLayerCloseOnTap(context) }) { SettingsManager.setEmojiLayerCloseOnTap(context, it) }
                    ),
                    stringResource(R.string.close_after_symbols) to listOf(
                        CheckCell({ SettingsManager.getSymbolsCloseOnKey(context) }) { SettingsManager.setSymbolsCloseOnKey(context, it) },
                        CheckCell({ SettingsManager.getSymbolsCloseOnTap(context) }) { SettingsManager.setSymbolsCloseOnTap(context, it) }
                    ),
                    stringResource(R.string.close_after_kaomoji) to listOf(
                        CheckCell({ SettingsManager.getKaomojiCloseOnKey(context) }) { SettingsManager.setKaomojiCloseOnKey(context, it) },
                        CheckCell({ SettingsManager.getKaomojiCloseOnTap(context) }) { SettingsManager.setKaomojiCloseOnTap(context, it) }
                    ),
                    stringResource(R.string.close_after_other) to listOf(
                        CheckCell({ SettingsManager.getSymAutoClose(context) }) { SettingsManager.setSymAutoClose(context, it) },
                        CheckCell({ SettingsManager.getSymAutoCloseOnTouch(context) }) { SettingsManager.setSymAutoCloseOnTouch(context, it) }
                    )
                )
            )
            FluxSwitchRow(
                linkId = "sym.emoji_height",
                title = stringResource(R.string.emoji_picker_expanded_height_title),
                description = stringResource(R.string.emoji_picker_expanded_height_description),
                checked = emojiPickerExpandedHeight,
                onCheckedChange = { enabled ->
                    emojiPickerExpandedHeight = enabled
                    SettingsManager.setEmojiPickerExpandedHeight(context, enabled)
                }
            )

            SettingsSectionDivider(stringResource(R.string.flux_section_gif_search))
            if (SettingsManager.isOfflineMode(context)) {
                FluxNote(stringResource(R.string.flux_gifs_offline_note))
            }

            // GIF search (KLIPY): a GIF key on the emoji layer and a GIF tab in the picker
            Column(
                modifier = Modifier.settingRow("flux_emoji.gif_search")
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gif_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.gif_settings_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = gifsEnabled,
                        onCheckedChange = { enabled ->
                            gifsEnabled = enabled
                            SettingsManager.setGifsEnabled(context, enabled)
                        }
                    )
                }
                if (gifsEnabled) {
                    OutlinedTextField(
                        value = klipyApiKey,
                        onValueChange = { value ->
                            klipyApiKey = value
                            SettingsManager.setKlipyApiKey(context, value)
                        },
                        label = { Text(stringResource(R.string.gif_api_key_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(
                            if (SettingsManager.hasBuiltInKlipyApiKey()) R.string.gif_api_key_help_builtin
                            else R.string.gif_api_key_help
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.palsoftware.pastiera.data.gif.KlipyGifs.SIGNUP_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) {
                        Text(stringResource(R.string.gif_get_key))
                    }
                    // The emoji layer key that opens GIF search
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showGifKeyDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.emoji_layer_gif_key_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (emojiLayerGifKey == KeyEvent.KEYCODE_UNKNOWN) {
                                    stringResource(R.string.emoji_layer_recents_key_off)
                                } else {
                                    stringResource(R.string.emoji_layer_recents_key_current, getLetterFromKeyCode(emojiLayerGifKey))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            SettingsSectionDivider(stringResource(R.string.flux_section_gif_library))
            FluxSwitchRow(
                linkId = "flux_emoji.gif_favourites",
                title = stringResource(R.string.flux_gif_favourites_title),
                description = stringResource(R.string.flux_gif_favourites_description),
                checked = gifFavourites,
                onCheckedChange = { enabled ->
                    gifFavourites = enabled
                    SettingsManager.setGifShowFavourites(context, enabled)
                }
            )
            FluxSwitchRow(
                linkId = "flux_emoji.gif_recents",
                title = stringResource(R.string.flux_gif_recents_title),
                description = stringResource(R.string.flux_gif_recents_description),
                checked = gifRecents,
                onCheckedChange = { enabled ->
                    gifRecents = enabled
                    SettingsManager.setGifShowRecents(context, enabled)
                }
            )


            SettingsSectionDivider(stringResource(R.string.flux_section_search))
            FluxActionRow(
                linkId = "flux_emoji.search_page",
                title = stringResource(R.string.flux_search_page_title),
                description = stringResource(R.string.flux_search_page_description),
                onClick = { openSettingsChild(context, "flux_emoji", "search") }
            )
        }

        // The search key: press the letter key to use, like the Recents and GIF keys
        if (showSearchKeyDialog) {
            EmojiLayerKeyDialog(
                title = stringResource(R.string.search_key_title),
                description = stringResource(R.string.search_key_description),
                currentKey = searchKey,
                letterFor = ::getLetterFromKeyCode,
                onKeyPressed = { keyCode ->
                    SettingsManager.setSearchKey(context, keyCode).also { if (it) searchKey = keyCode }
                },
                onTurnOff = {
                    SettingsManager.setSearchKey(context, KeyEvent.KEYCODE_UNKNOWN)
                    searchKey = KeyEvent.KEYCODE_UNKNOWN
                },
                onDismiss = { showSearchKeyDialog = false }
            )
        }

        // Dedicated emoji picker key: press the key to use (works with whatever keys the device has)
        if (showEmojiPickerKeyDialog) {
            val keyCaptureFocus = remember { FocusRequester() }
            var rejectedKey by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showEmojiPickerKeyDialog = false },
                title = { Text(stringResource(R.string.emoji_picker_key_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(keyCaptureFocus)
                            .focusable()
                            .onPreviewKeyEvent { event ->
                                val native = event.nativeKeyEvent
                                // Let Back close the dialog as usual
                                if (native.keyCode == KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                                if (native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                                    if (SettingsManager.isAllowedEmojiPickerKey(native.keyCode, native.isPrintingKey)) {
                                        SettingsManager.setEmojiPickerKey(context, native.keyCode)
                                        emojiPickerKey = native.keyCode
                                        showEmojiPickerKeyDialog = false
                                    } else {
                                        rejectedKey = true
                                    }
                                }
                                true
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.emoji_picker_key_description),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.emoji_picker_key_current,
                                emojiPickerKeyLabel(context, emojiPickerKey)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(
                                if (rejectedKey) R.string.emoji_picker_key_rejected
                                else R.string.emoji_picker_key_press_prompt
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rejectedKey) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    LaunchedEffect(Unit) {
                        runCatching { keyCaptureFocus.requestFocus() }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            SettingsManager.setEmojiPickerKey(context, KeyEvent.KEYCODE_UNKNOWN)
                            emojiPickerKey = KeyEvent.KEYCODE_UNKNOWN
                            showEmojiPickerKeyDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.emoji_picker_key_turn_off))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmojiPickerKeyDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Recents key on the emoji layer: press the letter key to use, like the emoji key
        if (showRecentsKeyDialog) {
            EmojiLayerKeyDialog(
                title = stringResource(R.string.emoji_layer_recents_key_title),
                description = stringResource(R.string.emoji_layer_recents_key_description),
                currentKey = emojiLayerRecentsKey,
                letterFor = ::getLetterFromKeyCode,
                onKeyPressed = { keyCode ->
                    SettingsManager.setEmojiLayerRecentsKey(context, keyCode).also { if (it) emojiLayerRecentsKey = keyCode }
                },
                onTurnOff = {
                    SettingsManager.setEmojiLayerRecentsKey(context, KeyEvent.KEYCODE_UNKNOWN)
                    emojiLayerRecentsKey = KeyEvent.KEYCODE_UNKNOWN
                },
                onDismiss = { showRecentsKeyDialog = false }
            )
        }

        // GIF key on the emoji layer: the same, for opening GIF search
        if (showGifKeyDialog) {
            EmojiLayerKeyDialog(
                title = stringResource(R.string.emoji_layer_gif_key_title),
                description = stringResource(R.string.emoji_layer_gif_key_description),
                currentKey = emojiLayerGifKey,
                letterFor = ::getLetterFromKeyCode,
                onKeyPressed = { keyCode ->
                    SettingsManager.setEmojiLayerGifKey(context, keyCode).also { if (it) emojiLayerGifKey = keyCode }
                },
                onTurnOff = {
                    SettingsManager.setEmojiLayerGifKey(context, KeyEvent.KEYCODE_UNKNOWN)
                    emojiLayerGifKey = KeyEvent.KEYCODE_UNKNOWN
                },
                onDismiss = { showGifKeyDialog = false }
            )
        }
    }
}

private fun getLetterFromKeyCode(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_Q -> "Q"
        KeyEvent.KEYCODE_W -> "W"
        KeyEvent.KEYCODE_E -> "E"
        KeyEvent.KEYCODE_R -> "R"
        KeyEvent.KEYCODE_T -> "T"
        KeyEvent.KEYCODE_Y -> "Y"
        KeyEvent.KEYCODE_U -> "U"
        KeyEvent.KEYCODE_I -> "I"
        KeyEvent.KEYCODE_O -> "O"
        KeyEvent.KEYCODE_P -> "P"
        KeyEvent.KEYCODE_A -> "A"
        KeyEvent.KEYCODE_S -> "S"
        KeyEvent.KEYCODE_D -> "D"
        KeyEvent.KEYCODE_F -> "F"
        KeyEvent.KEYCODE_G -> "G"
        KeyEvent.KEYCODE_H -> "H"
        KeyEvent.KEYCODE_J -> "J"
        KeyEvent.KEYCODE_K -> "K"
        KeyEvent.KEYCODE_L -> "L"
        KeyEvent.KEYCODE_Z -> "Z"
        KeyEvent.KEYCODE_X -> "X"
        KeyEvent.KEYCODE_C -> "C"
        KeyEvent.KEYCODE_V -> "V"
        KeyEvent.KEYCODE_B -> "B"
        KeyEvent.KEYCODE_N -> "N"
        KeyEvent.KEYCODE_M -> "M"
        else -> "?"
    }
}

internal fun emojiPickerKeyLabel(context: Context, keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_UNKNOWN -> context.getString(R.string.emoji_picker_key_off)
    KeyEvent.KEYCODE_SHIFT_RIGHT -> context.getString(R.string.emoji_picker_key_right_shift)
    KeyEvent.KEYCODE_SHIFT_LEFT -> context.getString(R.string.emoji_picker_key_left_shift)
    KeyEvent.KEYCODE_ALT_RIGHT -> context.getString(R.string.emoji_picker_key_right_alt)
    KeyEvent.KEYCODE_ALT_LEFT -> context.getString(R.string.emoji_picker_key_left_alt)
    KeyEvent.KEYCODE_CTRL_RIGHT -> context.getString(R.string.emoji_picker_key_right_ctrl)
    KeyEvent.KEYCODE_CTRL_LEFT -> context.getString(R.string.emoji_picker_key_left_ctrl)
    KeyEvent.KEYCODE_FUNCTION -> "Fn"
    else -> KeyEvent.keyCodeToString(keyCode)
        .removePrefix("KEYCODE_")
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}

/**
 * Press-to-assign dialog for an emoji layer key (Recents, GIF): waits for a letter key press.
 * [onKeyPressed] stores it and returns false when it isn't allowed (not a layer key, or the
 * other special key).
 */
@Composable
private fun EmojiLayerKeyDialog(
    title: String,
    description: String,
    currentKey: Int,
    letterFor: (Int) -> String,
    onKeyPressed: (Int) -> Boolean,
    onTurnOff: () -> Unit,
    onDismiss: () -> Unit
) {
    val keyFocus = remember { FocusRequester() }
    var rejected by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(keyFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        // Let Back close the dialog as usual
                        if (native.keyCode == KeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                        if (native.action == KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                            if (onKeyPressed(native.keyCode)) onDismiss() else rejected = true
                        }
                        true
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (currentKey == KeyEvent.KEYCODE_UNKNOWN) {
                        stringResource(R.string.emoji_layer_recents_key_off)
                    } else {
                        stringResource(R.string.emoji_layer_recents_key_current, letterFor(currentKey))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        if (rejected) R.string.emoji_layer_key_rejected
                        else R.string.emoji_layer_recents_key_press_prompt
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            LaunchedEffect(Unit) {
                runCatching { keyFocus.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onTurnOff()
                onDismiss()
            }) {
                Text(stringResource(R.string.emoji_layer_recents_key_turn_off))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ------------------------------------------------------------------ Titan 2 Elite screen

@Composable
fun FluxTitanScreenSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    FluxScreenScaffold(stringResource(R.string.flux_titan_screen_title), onBack, modifier) {
        FluxNote(stringResource(R.string.flux_titan_screen_note))
        // Every Titan 2 Elite setting lives here: the rounded status bar first, then the screen fit
        var roundedCorners by remember { mutableStateOf(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) }
        SettingsSectionDivider(stringResource(R.string.titan2_elite_section_status_bar))
        Titan2EliteRoundedCornerRows(onRoundedCornersChanged = { roundedCorners = it })
        if (DeviceSpecific.isTitan2EliteDevice() || roundedCorners) {
            SettingsSectionDivider(stringResource(R.string.titan2_elite_section_screen))

            var contourLeds by remember {
                mutableStateOf(SettingsManager.getTitan2EliteContourLeds(context))
            }
            var fillCorners by remember {
                mutableStateOf(SettingsManager.getTitan2EliteFillCorners(context))
            }
            Surface(modifier = Modifier.fillMaxWidth().settingRow("titan_screen.corner_style")) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.titan2_elite_corner_style_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.titan2_elite_corner_style_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            true to R.string.titan2_elite_corner_style_contoured,
                            false to R.string.titan2_elite_corner_style_straight
                        ).forEach { (contoured, label) ->
                            FilterChip(
                                selected = contourLeds == contoured,
                                onClick = {
                                    contourLeds = contoured
                                    SettingsManager.setTitan2EliteContourLeds(context, contoured)
                                    fillCorners = SettingsManager.getTitan2EliteFillCorners(context)
                                },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                }
            }

            Surface(modifier = Modifier.fillMaxWidth().settingRow("titan_screen.fill_corners")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(R.string.titan2_elite_fill_corners_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.titan2_elite_fill_corners_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (contourLeds) {
                            Text(stringResource(R.string.titan2_elite_off_with_contour_leds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Switch(
                        checked = fillCorners,
                        enabled = !contourLeds,
                        onCheckedChange = { enabled ->
                            fillCorners = enabled
                            SettingsManager.setTitan2EliteFillCorners(context, enabled)
                        }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Hidden keyboard apps

@Composable
fun FluxHiddenAppsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityOn by remember { mutableStateOf(isPastieraAccessibilityServiceOn(context)) }
    // Back from Android's accessibility settings: show the service's new state
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessibilityOn = isPastieraAccessibilityServiceOn(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FluxScreenScaffold(stringResource(R.string.flux_hidden_apps_title), onBack, modifier) {
        FluxNote(stringResource(R.string.flux_hidden_apps_note))
        var hiddenKeyboardApps by remember {
            mutableStateOf(SettingsManager.getHiddenKeyboardApps(context))
        }
        var showHiddenAppsDialog by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .settingRow("hidden_apps.apps") { showHiddenAppsDialog = true }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.hidden_keyboard_apps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                val hiddenAppNames = remember(hiddenKeyboardApps) {
                    val installed = AppListHelper.getCachedInstalledApps()
                        ?.associateBy { app -> app.packageName }
                    hiddenKeyboardApps.map { pkg -> installed?.get(pkg)?.appName ?: pkg }
                }
                if (hiddenKeyboardApps.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        hiddenKeyboardApps.take(8).forEach { pkg -> FluxAppIcon(pkg, 24.dp) }
                    }
                }
                Text(
                    text = if (hiddenKeyboardApps.isEmpty()) {
                        stringResource(R.string.hidden_keyboard_apps_description)
                    } else {
                        hiddenAppNames.joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showHiddenAppsDialog) {
            HiddenKeyboardAppsDialog(onDismiss = {
                showHiddenAppsDialog = false
                hiddenKeyboardApps = SettingsManager.getHiddenKeyboardApps(context)
            })
        }

        SettingsSectionDivider(stringResource(R.string.flux_section_accessibility))
        FluxActionRow(
            linkId = "hidden_apps.accessibility",
            title = stringResource(R.string.flux_accessibility_title),
            description = stringResource(
                when {
                    accessibilityOn -> R.string.flux_accessibility_on
                    RestrictedSettings.blocked(context) -> R.string.restricted_settings_accessibility
                    else -> R.string.flux_accessibility_off
                }
            ),
            onClick = { RestrictedSettings.openAccessibility(context) }
        )
    }
}

// ------------------------------------------------------------------ Linux desktop

@Composable
fun FluxLinuxDesktopScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var standardCtrlSym by remember { mutableStateOf(SettingsManager.getHiddenAppStandardModifiers(context)) }
    var layoutWrittenAt by remember {
        mutableStateOf(DesktopKeyboardLayout.file(context)?.takeIf { it.isFile }?.lastModified())
    }

    FluxScreenScaffold(stringResource(R.string.flux_linux_desktop_title), onBack, modifier) {
        FluxNote(stringResource(R.string.flux_linux_desktop_note))

        SettingsSectionDivider(stringResource(R.string.flux_section_keys))
        FluxSwitchRow(
            linkId = "linux_desktop.standard_ctrl_sym",
            title = stringResource(R.string.flux_standard_ctrl_sym_title),
            description = stringResource(R.string.flux_standard_ctrl_sym_description),
            checked = standardCtrlSym,
            onCheckedChange = { enabled ->
                standardCtrlSym = enabled
                SettingsManager.setHiddenAppStandardModifiers(context, enabled)
            }
        )

        SettingsSectionDivider(stringResource(R.string.flux_section_layout))
        val writtenAt = layoutWrittenAt
        FluxActionRow(
            linkId = "linux_desktop.keyboard_layout",
            title = stringResource(R.string.flux_desktop_layout_title),
            description = stringResource(R.string.flux_desktop_layout_description) + "\n" +
                if (writtenAt == null) {
                    stringResource(R.string.flux_desktop_layout_not_written)
                } else {
                    stringResource(
                        R.string.flux_desktop_layout_written,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(writtenAt))
                    )
                },
            onClick = {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching { DesktopKeyboardLayout.export(context) }.getOrNull()
                    }
                    if (file != null) layoutWrittenAt = file.lastModified()
                    Toast.makeText(
                        context,
                        if (file != null) R.string.flux_desktop_layout_written_toast else R.string.flux_desktop_layout_failed_toast,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}

// ------------------------------------------------------------------ Offline mode

@Composable
fun FluxOfflineScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var offline by remember { mutableStateOf(SettingsManager.isOfflineMode(context)) }
    FluxScreenScaffold(stringResource(R.string.flux_offline_title), onBack, modifier) {
        FluxNote(stringResource(R.string.flux_offline_note))
        FluxSwitchRow(
            linkId = "offline.enabled",
            title = stringResource(R.string.flux_offline_switch_title),
            description = stringResource(R.string.flux_offline_switch_description),
            checked = offline,
            onCheckedChange = { enabled ->
                offline = enabled
                SettingsManager.setOfflineMode(context, enabled)
            }
        )
        SettingsSectionDivider(stringResource(R.string.flux_section_offline_affects))
        FluxNote(stringResource(R.string.flux_offline_affects))
    }
}
