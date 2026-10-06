package it.palsoftware.pastiera.settings
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.CornerCalibrationActivity
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.TrackpadDebugActivity
import it.palsoftware.pastiera.TutorialActivity
import it.palsoftware.pastiera.apps.AppListHelper
import it.palsoftware.pastiera.apps.AppPickerDialog

/*
 * The main screen's groups, most used first, each ordered the same way:
 *   Typing               - suggestions and auto-correction, capitals and punctuation, text expansion,
 *                          variations; editing keys and Nav Mode
 *   Keyboards & layouts  - input languages, keyboards and devices, showing the keyboard, Titan 2 Elite screen
 *   Apps                 - app shortcuts, Enter per app, Quick Launcher; terminals and desktops
 *   Look & sound         - theme and LED colours, the status bar, sound and haptics
 * Modifiers & SYM, Emoji, symbols & GIFs, Trackpad & gestures and Privacy & system open their screens directly.
 */

@Composable
fun KeyboardsLayoutsHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit
) {
    val context = LocalContext.current
    var autoShowKeyboard by remember { mutableStateOf(SettingsManager.getAutoShowKeyboard(context)) }
    var searchBarWaits by remember { mutableStateOf(SettingsManager.getSearchBarWaitsForTyping(context)) }
    FluxScreenScaffold(stringResource(R.string.settings_keyboards_layouts_title), onBack, modifier) {
        SettingsCategoryRow(
            icon = Icons.Filled.Language,
            title = stringResource(R.string.custom_input_styles_title),
            description = stringResource(R.string.settings_input_languages_description),
            linkId = SettingLinkIds.MAIN_CUSTOM_INPUT_STYLES,
            onClick = { onNavigate(SettingsDestination.CustomInputStyles) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.keyboards_devices_title),
            description = stringResource(R.string.settings_keyboards_devices_description),
            linkId = SettingLinkIds.MAIN_KEYBOARDS_DEVICES,
            onClick = { onNavigate(SettingsDestination.KeyboardsDevices) }
        )
        FluxSwitchRow(
            linkId = SettingLinkIds.TEXT_INPUT_AUTO_SHOW_KEYBOARD,
            title = stringResource(R.string.auto_show_keyboard_title),
            description = stringResource(R.string.auto_show_keyboard_description),
            checked = autoShowKeyboard,
            onCheckedChange = { enabled ->
                autoShowKeyboard = enabled
                SettingsManager.setAutoShowKeyboard(context, enabled)
            }
        )
        FluxSwitchRow(
            linkId = SettingLinkIds.TEXT_INPUT_SEARCH_BAR_WAITS,
            title = stringResource(R.string.search_bar_waits_title),
            description = stringResource(R.string.search_bar_waits_description),
            checked = searchBarWaits,
            onCheckedChange = { enabled ->
                searchBarWaits = enabled
                SettingsManager.setSearchBarWaitsForTyping(context, enabled)
            }
        )
        if (fluxTitanScreenAvailable(context)) {
            SettingsCategoryRow(
                icon = Icons.Filled.RoundedCorner,
                title = stringResource(R.string.flux_titan_screen_title),
                description = stringResource(R.string.flux_titan_screen_description),
                linkId = SettingLinkIds.MAIN_FLUX_TITAN_SCREEN,
                onClick = { onNavigate(SettingsDestination.FluxTitanScreen) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TypingHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onOpenCustomization: (String) -> Unit = {}
) {
    FluxScreenScaffold(stringResource(R.string.settings_typing_title), onBack, modifier) {
        // Most used first: what you type, then how it's corrected and shaped, then editing
        SettingsCategoryRow(
            icon = Icons.Filled.Spellcheck,
            title = stringResource(R.string.settings_category_auto_correction),
            description = stringResource(R.string.settings_auto_correction_description),
            linkId = SettingLinkIds.MAIN_AUTO_CORRECTION,
            onClick = { onNavigate(SettingsDestination.AutoCorrection) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.TextFields,
            title = stringResource(R.string.settings_capitalisation_punctuation_title),
            description = stringResource(R.string.settings_capitalisation_punctuation_description),
            linkId = SettingLinkIds.MAIN_TEXT_INPUT,
            onClick = { onNavigate(SettingsDestination.TextInput) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.ShortText,
            title = stringResource(R.string.text_expansion_title),
            description = stringResource(R.string.settings_text_expansion_hub_description),
            linkId = SettingLinkIds.TEXT_INPUT_TEXT_EXPANSION,
            onClick = { onNavigate(SettingsDestination.TextExpansion) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.Tune,
            title = stringResource(R.string.variation_customize_title),
            description = stringResource(R.string.settings_variations_popup_description),
            linkId = "customization.variations",
            onClick = { onOpenCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_VARIATIONS) }
        )

        SettingsSectionDivider(stringResource(R.string.settings_section_editing))
        SettingsCategoryRow(
            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
            title = stringResource(R.string.settings_editing_keys_title),
            description = stringResource(R.string.settings_editing_keys_description),
            linkId = SettingLinkIds.MAIN_EDITING_KEYS,
            onClick = { onNavigate(SettingsDestination.EditingKeys) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.OpenWith,
            title = stringResource(R.string.nav_mode_title),
            description = stringResource(R.string.settings_nav_mode_hub_description),
            linkId = SettingLinkIds.MAIN_NAV_MODE,
            onClick = { onNavigate(SettingsDestination.NavMode) }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LookSoundHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onOpenCustomization: (String) -> Unit
) {
    FluxScreenScaffold(stringResource(R.string.settings_look_sound_title), onBack, modifier) {
        SettingsCategoryRow(
            icon = Icons.Filled.Palette,
            title = stringResource(R.string.settings_theme_led_colours_title),
            description = stringResource(R.string.settings_theme_led_colours_description),
            linkId = SettingLinkIds.MAIN_KEYBOARD_THEME,
            onClick = { onOpenCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.Lightbulb,
            title = stringResource(R.string.led_colors_title),
            description = stringResource(R.string.led_colors_description),
            linkId = SettingLinkIds.MAIN_LED_COLORS,
            onClick = { onNavigate(SettingsDestination.LedColors) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.SmartButton,
            title = stringResource(R.string.status_bar_buttons_title),
            description = stringResource(R.string.status_bar_buttons_description),
            linkId = SettingLinkIds.MAIN_STATUS_BAR_BUTTONS,
            onClick = { onOpenCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS) }
        )
        SettingsCategoryRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = stringResource(R.string.settings_category_sounds),
            description = stringResource(R.string.settings_sounds_description),
            linkId = "customization.sounds",
            onClick = { onOpenCustomization("sounds") }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AppsHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    onOpenCustomization: (String) -> Unit
) {
    FluxScreenScaffold(stringResource(R.string.settings_apps_title), onBack, modifier) {
        SettingsCategoryRow(
            icon = Icons.Filled.SwapHoriz,
            title = stringResource(R.string.app_shortcuts_title),
            description = stringResource(R.string.app_shortcuts_description),
            linkId = SettingLinkIds.MAIN_APP_SHORTCUTS,
            onClick = { onNavigate(SettingsDestination.AppShortcuts) }
        )
        SettingsCategoryRow(
            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
            title = stringResource(R.string.app_enter_behaviour_title),
            description = stringResource(R.string.app_enter_behaviour_description),
            linkId = SettingLinkIds.MAIN_APP_ENTER_BEHAVIOR,
            onClick = { onOpenCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR) }
        )
        SettingsCategoryRow(
            icon = Icons.AutoMirrored.Filled.ManageSearch,
            title = stringResource(R.string.starter_launcher_shortcuts_title),
            description = stringResource(R.string.settings_quick_launcher_description),
            linkId = SettingLinkIds.MAIN_LAUNCHER_SHORTCUTS,
            onClick = { onOpenCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS) }
        )

        SettingsSectionDivider(stringResource(R.string.settings_section_terminals))
        SettingsCategoryRow(
            icon = Icons.Filled.Code,
            title = stringResource(R.string.exact_typing_title),
            description = stringResource(R.string.exact_typing_description),
            linkId = SettingLinkIds.MAIN_EXACT_TYPING,
            onClick = { onNavigate(SettingsDestination.ExactTyping) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.Terminal,
            title = stringResource(R.string.terminal_mode_title),
            description = stringResource(R.string.terminal_mode_description),
            linkId = SettingLinkIds.MAIN_TERMINAL_MODE,
            onClick = { onNavigate(SettingsDestination.TerminalMode) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.flux_hidden_apps_title),
            description = stringResource(R.string.flux_hidden_apps_description),
            linkId = SettingLinkIds.MAIN_FLUX_HIDDEN_APPS,
            onClick = { onNavigate(SettingsDestination.FluxHiddenApps) }
        )
        SettingsCategoryRow(
            icon = Icons.Filled.DesktopWindows,
            title = stringResource(R.string.flux_linux_desktop_title),
            description = stringResource(R.string.flux_linux_desktop_description),
            linkId = SettingLinkIds.MAIN_FLUX_LINUX_DESKTOP,
            onClick = { onNavigate(SettingsDestination.FluxLinuxDesktop) }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** How far a swipe on the status bar's swipe pad moves the cursor by one step. */
@Composable
internal fun SwipePadThresholdRow() {
    val context = LocalContext.current
    val prefs = remember { SettingsManager.getPreferences(context) }
    // Stored 3 to 25 (distance per step); the slider shows it inverted so right is more sensitive
    var threshold by remember { mutableStateOf(SettingsManager.getSwipeIncrementalThreshold(context)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "swipe_incremental_threshold") {
                threshold = SettingsManager.getSwipeIncrementalThreshold(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val min = SettingsManager.getMinSwipeIncrementalThreshold()
    val max = SettingsManager.getMaxSwipeIncrementalThreshold()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .settingRow(SettingLinkIds.ADVANCED_SWIPE_INCREMENTAL_THRESHOLD)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.swipe_incremental_threshold_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${String.format("%.1f", threshold)} ${stringResource(R.string.dip_unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = max + min - threshold,
                onValueChange = { inverted ->
                    val actual = max + min - inverted
                    threshold = actual
                    SettingsManager.setSwipeIncrementalThreshold(context, actual)
                },
                valueRange = min..max,
                steps = 16,
                modifier = Modifier
                    .weight(1.0f)
                    .height(24.dp)
            )
        }
    }
}

/** Update checks (GitHub builds only): the nightly channel and the release channel. */
@Composable
internal fun SettingsUpdateRows(context: android.content.Context) {
    var checkingNightly by remember { mutableStateOf(false) }
    var checkingForUpdates by remember { mutableStateOf(false) }
    if (!it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks(context)) return
    if (BuildConfig.RELEASE_CHANNEL == "nightly") {
        SettingsCategoryRow(
            icon = Icons.Filled.Code,
            title = stringResource(if (checkingNightly) R.string.nightly_update_checking else R.string.nightly_update_settings_title),
            description = stringResource(R.string.nightly_update_settings_description),
            enabled = !checkingNightly,
            onClick = {
                checkingNightly = true
                it.palsoftware.pastiera.update.checkForNightlyUpdate(context, ignoreDismissedReleases = false) { result ->
                    checkingNightly = false
                    if (result.hasAnnouncement) {
                        it.palsoftware.pastiera.update.showReleaseNotice(context, result)
                    } else {
                        Toast.makeText(
                            context,
                            if (result.successful) R.string.nightly_update_current else R.string.settings_update_check_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
    val fork = it.palsoftware.pastiera.update.forkUpdatesEnabled()
    SettingsCategoryRow(
        icon = if (fork) Icons.Filled.SystemUpdate else ImageVector.vectorResource(R.drawable.plektra_open_monochrome_24),
        title = stringResource(when {
            checkingForUpdates -> if (fork) R.string.fork_update_checking else R.string.settings_update_checking
            fork -> R.string.fork_update_settings_title
            else -> R.string.settings_update_section_title
        }),
        description = stringResource(
            if (fork) R.string.fork_update_settings_description else R.string.settings_update_section_description
        ),
        enabled = !checkingForUpdates,
        onClick = {
            checkingForUpdates = true
            it.palsoftware.pastiera.update.checkForUpdate(
                context = context,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = false
            ) { result ->
                checkingForUpdates = false
                when {
                    !result.successful -> Toast.makeText(
                        context,
                        context.getString(R.string.settings_update_check_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    result.hasAnnouncement && result.releaseTag != null && result.displayName != null ->
                        it.palsoftware.pastiera.update.showReleaseNotice(context, result)
                    else -> Toast.makeText(
                        context,
                        context.getString(if (fork) R.string.fork_update_current else R.string.settings_update_up_to_date),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )
}

/** Which releases updates offer: full releases, or dev builds too. */
@Composable
internal fun ForkUpdateChannelRow(context: android.content.Context) {
    var channel by remember { mutableStateOf(SettingsManager.getForkUpdateChannel(context)) }
    val dev = channel == SettingsManager.FORK_UPDATE_CHANNEL_DEV
    FluxSwitchRow(
        linkId = SettingLinkIds.FORK_UPDATE_CHANNEL,
        title = stringResource(R.string.fork_update_channel_title),
        description = stringResource(if (dev) R.string.fork_update_channel_dev else R.string.fork_update_channel_stable),
        checked = dev,
        onCheckedChange = { on ->
            channel = if (on) SettingsManager.FORK_UPDATE_CHANNEL_DEV else SettingsManager.FORK_UPDATE_CHANNEL_STABLE
            SettingsManager.setForkUpdateChannel(context, channel)
        }
    )
}

/**
 * Developer options (Privacy & system, behind its switch): calibration, debugging and preview
 * tools that most people never need.
 */
@Composable
fun DeveloperOptionsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    if (settingsChild(context, "developer") == "ime_test") {
        ImeTestScreen(modifier = modifier, onBack = onBack)
        return
    }
    FluxScreenScaffold(stringResource(R.string.developer_options_title), onBack, modifier) {
        FluxNote(stringResource(R.string.developer_options_note))
        if (it.palsoftware.pastiera.update.forkUpdatesEnabled()) ForkUpdateChannelRow(context)
        if (it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2EliteDevice() || SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            FluxActionRow(
                linkId = "advanced.corner_calibration",
                title = stringResource(R.string.corner_calibration_title),
                description = stringResource(R.string.corner_calibration_description),
                onClick = { context.startActivity(android.content.Intent(context, CornerCalibrationActivity::class.java)) }
            )
        }
        FluxActionRow(
            linkId = SettingLinkIds.TRACKPAD_DEBUG,
            title = stringResource(R.string.trackpad_debug_title),
            description = stringResource(R.string.trackpad_debug_description),
            onClick = { context.startActivity(android.content.Intent(context, TrackpadDebugActivity::class.java)) }
        )
        FluxActionRow(
            linkId = SettingLinkIds.ADVANCED_SHOW_RELEASE_NOTES_TUTORIAL,
            title = stringResource(R.string.tutorial_show_release_notes),
            description = stringResource(R.string.tutorial_show_release_notes_description),
            onClick = {
                context.startActivity(android.content.Intent(context, TutorialActivity::class.java).apply {
                    putExtra(TutorialActivity.EXTRA_UPDATE_TUTORIAL, true)
                    putExtra(TutorialActivity.EXTRA_PREVIEW_UPDATE_TUTORIAL, true)
                    putExtra(TutorialActivity.EXTRA_PREVIOUS_VERSION, "0.84beta")
                })
            }
        )
        if (BuildConfig.DEBUG) {
            FluxActionRow(
                linkId = null,
                title = "IME Test Screen",
                description = "Test all input field types and IME actions",
                onClick = { openSettingsChild(context, "developer", "ime_test") }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Terminal mode (Settings > Apps): in Termux and other terminals, Alt and SYM type Pastiera's
 * symbols and Ctrl reaches the shell as a real Ctrl.
 */
@Composable
private fun terminalEmojiKeyLabel(action: it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction): String =
    stringResource(
        when (action) {
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.EmojiPicker -> R.string.terminal_emoji_key_picker
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.Escape -> R.string.terminal_emoji_key_esc
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.Tab -> R.string.terminal_emoji_key_tab
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.PreviousCommand -> R.string.terminal_emoji_key_up
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.Interrupt -> R.string.terminal_emoji_key_ctrl_c
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.EndOfInput -> R.string.terminal_emoji_key_ctrl_d
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.Suspend -> R.string.terminal_emoji_key_ctrl_z
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.ClearScreen -> R.string.terminal_emoji_key_ctrl_l
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.SearchHistory -> R.string.terminal_emoji_key_ctrl_r
            it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.Alt -> R.string.terminal_emoji_key_alt
        }
    )

@Composable
fun TerminalModeScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsManager.getTerminalModeEnabled(context)) }
    var hideKeyboard by remember { mutableStateOf(SettingsManager.getTerminalModeHideKeyboard(context)) }
    var emojiKeyAction by remember {
        mutableStateOf(it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.byId(
            SettingsManager.getTerminalModeEmojiKeyAction(context)))
    }
    var choosingEmojiKey by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(SettingsManager.getTerminalModeApps(context)) }
    var showPicker by remember { mutableStateOf(false) }
    val installed = remember { AppListHelper.getInstalledApps(context).associate { it.packageName to it.appName } }
    FluxScreenScaffold(stringResource(R.string.terminal_mode_title), onBack, modifier) {
        FluxNote(stringResource(R.string.terminal_mode_note))
        FluxSwitchRow(
            linkId = SettingLinkIds.TERMINAL_MODE_ENABLED,
            title = stringResource(R.string.terminal_mode_enabled_title),
            description = stringResource(R.string.terminal_mode_enabled_description),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                SettingsManager.setTerminalModeEnabled(context, it)
            }
        )
        if (enabled) {
            FluxSwitchRow(
                linkId = SettingLinkIds.TERMINAL_MODE_HIDE_KEYBOARD,
                title = stringResource(R.string.terminal_mode_hide_keyboard_title),
                description = stringResource(R.string.terminal_mode_hide_keyboard_description),
                checked = hideKeyboard,
                onCheckedChange = {
                    hideKeyboard = it
                    SettingsManager.setTerminalModeHideKeyboard(context, it)
                }
            )
            if (hideKeyboard) {
                var showLeds by remember { mutableStateOf(SettingsManager.getTerminalModeShowLeds(context)) }
                FluxSwitchRow(
                    linkId = "terminal_mode.show_leds",
                    title = stringResource(R.string.terminal_mode_show_leds_title),
                    description = stringResource(R.string.terminal_mode_show_leds_description),
                    checked = showLeds,
                    onCheckedChange = {
                        showLeds = it
                        SettingsManager.setTerminalModeShowLeds(context, it)
                    }
                )
            }
            FluxActionRow(
                linkId = SettingLinkIds.TERMINAL_MODE_EMOJI_KEY,
                title = stringResource(R.string.terminal_mode_emoji_key_title),
                description = terminalEmojiKeyLabel(emojiKeyAction) + " · " +
                    stringResource(R.string.terminal_mode_emoji_key_description),
                onClick = { choosingEmojiKey = true }
            )
        }
        // Termux, set up for the keyboard by a command pasted into it
        if (remember { it.palsoftware.pastiera.shortcuts.TermuxSetup.installed(context) }) {
            FluxActionRow(
                linkId = "terminal_mode.termux_setup",
                title = stringResource(R.string.termux_setup_title),
                description = stringResource(R.string.termux_setup_description),
                onClick = { it.palsoftware.pastiera.shortcuts.TermuxSetup.copyAndOpen(context) }
            )
        }
        if (choosingEmojiKey) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { choosingEmojiKey = false },
                title = { Text(stringResource(R.string.terminal_mode_emoji_key_title)) },
                text = {
                    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        it.palsoftware.pastiera.inputmethod.TerminalMode.EmojiKeyAction.entries.forEach { action ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    emojiKeyAction = action
                                    SettingsManager.setTerminalModeEmojiKeyAction(context, action.id)
                                    choosingEmojiKey = false
                                }.padding(vertical = 4.dp)
                            ) {
                                androidx.compose.material3.RadioButton(selected = action == emojiKeyAction, onClick = null)
                                Text(terminalEmojiKeyLabel(action), modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { choosingEmojiKey = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        SettingsSectionDivider(stringResource(R.string.terminal_mode_apps))
        apps.forEach { packageName ->
            FluxActionRow(
                linkId = null,
                title = installed[packageName] ?: packageName,
                description = stringResource(R.string.terminal_mode_remove_app, packageName),
                appPackage = packageName,
                onClick = {
                    apps = apps - packageName
                    SettingsManager.setTerminalModeApps(context, apps)
                }
            )
        }
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.terminal_mode_add_app),
            description = stringResource(R.string.terminal_mode_add_app_description),
            onClick = { showPicker = true }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (showPicker) {
        AppPickerDialog(
            excludePackages = remember {
                val ime = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                (ime?.inputMethodList?.map { it.packageName }.orEmpty() + apps).toSet()
            },
            onAppSelected = { app ->
                showPicker = false
                apps = apps + app.packageName
                SettingsManager.setTerminalModeApps(context, apps)
            },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * Exact typing (Settings > Apps): in the apps you pick (SSH clients, code editors, AI agents),
 * every character stays as typed. Suggestions still show and only apply when you pick one.
 */
@Composable
fun ExactTypingScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(SettingsManager.getExactTypingApps(context)) }
    var noSuggestionFields by remember { mutableStateOf(SettingsManager.getExactTypingForNoSuggestionFields(context)) }
    var showPicker by remember { mutableStateOf(false) }
    val installed = remember { AppListHelper.getInstalledApps(context).associate { it.packageName to it.appName } }
    FluxScreenScaffold(stringResource(R.string.exact_typing_title), onBack, modifier) {
        FluxNote(stringResource(R.string.exact_typing_note))
        FluxSwitchRow(
            linkId = SettingLinkIds.EXACT_TYPING_NO_SUGGESTION_FIELDS,
            title = stringResource(R.string.exact_typing_no_suggestions_title),
            description = stringResource(R.string.exact_typing_no_suggestions_description),
            checked = noSuggestionFields,
            onCheckedChange = {
                noSuggestionFields = it
                SettingsManager.setExactTypingForNoSuggestionFields(context, it)
            }
        )
        SettingsSectionDivider(stringResource(R.string.exact_typing_apps))
        apps.forEach { packageName ->
            FluxActionRow(
                linkId = null,
                title = installed[packageName] ?: packageName,
                description = stringResource(R.string.terminal_mode_remove_app, packageName),
                appPackage = packageName,
                onClick = {
                    apps = apps - packageName
                    SettingsManager.setExactTypingApps(context, apps)
                }
            )
        }
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.exact_typing_add_app),
            description = stringResource(R.string.exact_typing_add_app_description),
            onClick = { showPicker = true }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    if (showPicker) {
        AppPickerDialog(
            excludePackages = remember(apps) { apps.toSet() },
            onAppSelected = { app ->
                showPicker = false
                apps = apps + app.packageName
                SettingsManager.setExactTypingApps(context, apps)
            },
            onDismiss = { showPicker = false }
        )
    }
}
