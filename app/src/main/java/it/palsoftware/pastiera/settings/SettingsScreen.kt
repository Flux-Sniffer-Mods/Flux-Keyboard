package it.palsoftware.pastiera.settings
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Shield
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import android.net.Uri
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import android.widget.Toast
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks
import kotlinx.coroutines.delay
import it.palsoftware.pastiera.SettingLinkRequest
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymCustomizationActivity
import it.palsoftware.pastiera.apps.AppShortcutsSettingsScreen
import it.palsoftware.pastiera.autocorrect.AutoCorrectionCategoryScreen
import it.palsoftware.pastiera.device.KeyboardsDevicesDestination
import it.palsoftware.pastiera.device.KeyboardsDevicesSettingsScreen
import it.palsoftware.pastiera.device.TrackpadGestureSettingsScreen
import it.palsoftware.pastiera.layout.CustomInputStylesScreen
import it.palsoftware.pastiera.sym.DeviceSymLayerEditorScreen
import it.palsoftware.pastiera.sym.EmojiLayerProfilesScreen
import it.palsoftware.pastiera.theme.LedColorsScreen
import it.palsoftware.pastiera.isOfflineMode

/**
 * Where the settings navigation is.
 */
enum class SettingsDestination {
    Main,
    KeyboardsDevices,
    TextInput,
    Accessibility,
    AutoCorrection,
    Customization,
    NavMode,
    Advanced,
    About,
    CustomInputStyles,
    AppLanguage,
    DeviceSymLayerEditor,
    LedColors,
    EmojiProfiles,
    Modifiers,
    FluxEmojiGifs,
    FluxTitanScreen,
    FluxHiddenApps,
    FluxLinuxDesktop,
    FluxOffline,
    Root,
    KeyboardsLayouts,
    Typing,
    EditingKeys,
    TextExpansion,
    LookSound,
    TrackpadGestures,
    Apps,
    AppShortcuts,
    Developer,
    TerminalMode,
    ExactTyping
}

/** The destination payload of one SettingsActivity, also used by deep links. */
internal data class SettingsPage(
    val destination: SettingsDestination,
    val customizationDestination: String? = null,
    val keyboardThemeTarget: String? = null,
    val keyboardThemeTab: String? = null,
    val navModeKeyCode: Int? = null,
    val keyboardsDevicesDestination: KeyboardsDevicesDestination = KeyboardsDevicesDestination.Main
)

internal fun SettingRoute.toSettingsPage() = SettingsPage(
    destination = destination,
    customizationDestination = customizationDestination,
    keyboardThemeTarget = keyboardThemeTarget?.name,
    keyboardThemeTab = keyboardThemeTab?.name,
    keyboardsDevicesDestination = keyboardsDevicesDestination
)

/**
 * App settings screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    initialDestination: String? = null,
    initialCustomizationDestination: String? = null,
    initialKeyboardThemeTarget: String? = null,
    settingLinkRequest: SettingLinkRequest? = null
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val currentEntry = remember { context.settingsActivity().intent.settingsPage() }
    val currentDestination = currentEntry.destination
    var highlightSettingId by rememberSaveable { mutableStateOf<String?>(null) }
    var linkSheetEntry by remember { mutableStateOf<SettingEntry?>(null) }

    fun navigateTo(destination: SettingsDestination) {
        openSettingsPage(context, SettingsPage(destination))
    }
    fun navigateBack() { context.settingsActivity().finish() }
    fun openCustomization(destination: String?, keyboardThemeTarget: String? = null, keyboardThemeTab: String? = null) {
        openSettingsPage(context, SettingsPage(SettingsDestination.Customization,
            destination, keyboardThemeTarget, keyboardThemeTab))
    }
    fun navigateToNavMode(keyCode: Int?) {
        openSettingsPage(context, SettingsPage(SettingsDestination.NavMode, navModeKeyCode = keyCode))
    }

    /**
     * Navigates to a settings entry (e.g. from search or a deep link) and asks
     * its row to flash and scroll into view. Never changes any value.
     */
    fun openSettingEntry(entry: SettingEntry) {
        linkSheetEntry = null
        val visibleEntry = SettingLinkRegistry.visibleTarget(context, entry)
        val route = visibleEntry.route
        if (route.symCustomization) {
            context.startActivity(Intent(context, SymCustomizationActivity::class.java).apply {
                putExtra(SymCustomizationActivity.EXTRA_SETTING_ID, visibleEntry.id)
            })
            return
        }
        val target = route.toSettingsPage()
        if (currentEntry != target) {
            context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                data = android.net.Uri.parse("fluxkeyboard://setting/${visibleEntry.id}")
            })
            return
        }
        highlightSettingId = visibleEntry.id
    }

    // Deep link (fluxkeyboard://setting/<id>) arriving via intent or onNewIntent
    LaunchedEffect(settingLinkRequest?.serial) {
        val request = settingLinkRequest ?: return@LaunchedEffect
        val entry = SettingLinkRegistry.byId(request.id)
        if (entry == null) {
            Toast.makeText(context, R.string.settings_link_unavailable_toast, Toast.LENGTH_SHORT)
                .show()
        } else {
            openSettingEntry(entry)
        }
    }
    LaunchedEffect(highlightSettingId) {
        if (highlightSettingId != null) {
            // Must outlast the blink sequence in settingRow (~1.65 s) so the
            // outline fades out gently after the last blink.
            delay(1800)
            highlightSettingId = null
        }
    }

    // Automatic update check on screen open (only once, respecting dismissed releases)
    if (currentDestination == SettingsDestination.Main && shouldUseGithubUpdateChecks(context)) {
        LaunchedEffect(Unit) {
            it.palsoftware.pastiera.update.checkForUpdateNotices(
                context = context,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = true
            ) { result ->
                if (result.hasAnnouncement && result.releaseTag != null && result.displayName != null) {
                    it.palsoftware.pastiera.update.showReleaseNotice(context, result)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalSettingHighlightId provides highlightSettingId,
        LocalSettingLinkLongPress provides ({ id -> linkSheetEntry = SettingLinkRegistry.byId(id) })
    ) {
    val entry = currentEntry
        when (entry.destination) {
            SettingsDestination.Main -> {
                SettingsMainScreen(
                    modifier = modifier,
                    context = context,
                    onOpenSettingEntry = { target ->
                        context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                            data = android.net.Uri.parse("fluxkeyboard://setting/${target.id}")
                        })
                    },
                    onNavigate = { destination -> navigateTo(destination) },
                    onBackClick = { navigateBack() }
                )
            }
            SettingsDestination.KeyboardsLayouts -> {
                KeyboardsLayoutsHubScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavigate = { destination -> navigateTo(destination) }
                )
            }
            SettingsDestination.Typing -> {
                TypingHubScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavigate = { destination -> navigateTo(destination) },
                    onOpenCustomization = { destination -> openCustomization(destination) }
                )
            }
            SettingsDestination.EditingKeys -> {
                TextInputSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    page = TextInputPage.EditingKeys,
                    onNavModeSettingsClick = { navigateToNavMode(null) }
                )
            }
            SettingsDestination.TextExpansion -> {
                TextExpansionSettingsScreen(onBack = { navigateBack() })
            }
            SettingsDestination.LookSound -> {
                LookSoundHubScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavigate = { destination -> navigateTo(destination) },
                    onOpenCustomization = { destination -> openCustomization(destination) }
                )
            }
            SettingsDestination.TrackpadGestures -> {
                TrackpadGestureSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.Apps -> {
                AppsHubScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavigate = { destination -> navigateTo(destination) },
                    onOpenCustomization = { destination -> openCustomization(destination) }
                )
            }
            SettingsDestination.TerminalMode -> {
                TerminalModeScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.ExactTyping -> {
                ExactTypingScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.Developer -> {
                DeveloperOptionsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.AppShortcuts -> {
                AppShortcutsSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.KeyboardsDevices -> {
                KeyboardsDevicesSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavModeSettingsClick = { keyCode ->
                        navigateToNavMode(keyCode)
                    },
                    onOpenKeyboardTheme = {
                        openCustomization(
                            SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
                            SettingsActivity.KEYBOARD_THEME_TARGET_SOFTWARE
                        )
                    },
                    destination = entry.keyboardsDevicesDestination,
                    onDestinationChange = { destination ->
                        if (destination == KeyboardsDevicesDestination.Main) navigateBack()
                        else openSettingsPage(context, entry.copy(keyboardsDevicesDestination = destination))
                    }
                )
            }
            SettingsDestination.TextInput -> {
                TextInputSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    page = TextInputPage.CapitalisationPunctuation
                )
            }
            SettingsDestination.Accessibility -> {
                AccessibilitySettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AutoCorrection -> {
                AutoCorrectionCategoryScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.Customization -> {
                CustomizationSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    initialDestination = entry.customizationDestination,
                    initialKeyboardThemeTarget = entry.keyboardThemeTarget,
                    initialKeyboardThemeTab = entry.keyboardThemeTab,
                    onOpenModifiers = { navigateTo(SettingsDestination.Modifiers) }
                )
            }
            SettingsDestination.NavMode -> {
                NavModeSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    initialKeyCode = entry.navModeKeyCode
                )
            }
            SettingsDestination.Advanced -> {
                AdvancedSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onNavigate = { destination -> navigateTo(destination) }
                )
            }
            SettingsDestination.FluxEmojiGifs -> {
                FluxEmojiGifsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.FluxTitanScreen -> {
                FluxTitanScreenSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.FluxHiddenApps -> {
                FluxHiddenAppsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.FluxLinuxDesktop -> {
                FluxLinuxDesktopScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.FluxOffline -> {
                FluxOfflineScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.Root -> {
                AdbSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.About -> {
                AboutScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.CustomInputStyles -> {
                CustomInputStylesScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            SettingsDestination.AppLanguage -> {
                AppLanguageSettingsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.EmojiProfiles -> {
                EmojiLayerProfilesScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.LedColors -> {
                LedColorsScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.DeviceSymLayerEditor -> {
                DeviceSymLayerEditorScreen(modifier = modifier, onBack = { navigateBack() })
            }
            SettingsDestination.Modifiers -> {
                ModifierSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenSymLayers = {
                        context.startActivity(
Intent(context, SymCustomizationActivity::class.java)
                        )
                    },
                    onOpenKeyShortcuts = {
                        openCustomization(SettingsActivity.CUSTOMIZATION_DESTINATION_KEY_SHORTCUTS)
                    },
                    onOpenNavMode = {
                        navigateToNavMode(null)
                    }
                )
            }
        }
    }

    // Share/copy sheet for the settings entry currently being long-pressed
    linkSheetEntry?.let { entry ->
        SettingLinkSheet(entry = entry, onDismiss = { linkSheetEntry = null })
    }
}

@Composable
private fun SettingsMainScreen(
    modifier: Modifier,
    context: Context,
    onNavigate: (SettingsDestination) -> Unit,
    onBackClick: () -> Unit,
    onOpenSettingEntry: (SettingEntry) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Both lists keep their place while the other is shown, and when you come back from a page
    val resultsScrollState = rememberScrollState()
    val categoriesScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchResults = remember(searchQuery, context) {
        SettingLinkRegistry.searchGrouped(context, searchQuery)
    }
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
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    SettingsSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            if (searchQuery.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(resultsScrollState)
                ) {
                    if (searchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        // Grouped under the main categories, the best-matching category first
                        searchResults.forEach { (category, entries) ->
                            SettingsSectionDivider(
                                stringResource(SettingLinkRegistry.destinationTitles[category] ?: R.string.settings_title)
                            )
                            entries.forEach { entry ->
                                SettingSearchResultRow(
                                    entry = entry,
                                    onClick = {
                                        keyboardController?.hide()
                                        onOpenSettingEntry(entry)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .verticalScroll(categoriesScrollState)
                ) {
                    SettingsCategoryRow(
                        icon = Icons.Filled.TextFields,
                        title = stringResource(R.string.settings_typing_title),
                        description = stringResource(R.string.settings_typing_description),
                        linkId = SettingLinkIds.MAIN_TYPING,
                        onClick = { onNavigate(SettingsDestination.Typing) }
                    )
                    SettingsCategoryRow(
                        icon = Icons.Filled.Keyboard,
                        title = stringResource(R.string.settings_keyboards_layouts_title),
                        description = stringResource(R.string.settings_keyboards_layouts_description),
                        linkId = SettingLinkIds.MAIN_KEYBOARDS_LAYOUTS,
                        onClick = { onNavigate(SettingsDestination.KeyboardsLayouts) }
                    )
                    SettingsCategoryRow(
                        iconRes = R.drawable.modifier_keys_24,
                        title = stringResource(R.string.settings_modifiers_sym_title),
                        description = stringResource(R.string.settings_modifiers_sym_description),
                        linkId = SettingLinkIds.MAIN_MODIFIERS,
                        onClick = { onNavigate(SettingsDestination.Modifiers) }
                    )
                    SettingsCategoryRow(
                        icon = Icons.Filled.EmojiEmotions,
                        title = stringResource(R.string.settings_emoji_symbols_gifs_title),
                        description = stringResource(R.string.settings_emoji_symbols_gifs_description),
                        linkId = SettingLinkIds.MAIN_FLUX_EMOJI_GIFS,
                        onClick = { onNavigate(SettingsDestination.FluxEmojiGifs) }
                    )
                    SettingsCategoryRow(
                        icon = Icons.Filled.Apps,
                        title = stringResource(R.string.settings_apps_title),
                        description = stringResource(R.string.settings_apps_description),
                        linkId = SettingLinkIds.MAIN_APPS,
                        onClick = { onNavigate(SettingsDestination.Apps) }
                    )
                    SettingsCategoryRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_look_sound_title),
                        description = stringResource(R.string.settings_look_sound_description),
                        linkId = SettingLinkIds.MAIN_LOOK_SOUND,
                        onClick = { onNavigate(SettingsDestination.LookSound) }
                    )
                    SettingsCategoryRow(
                        icon = Icons.Filled.Shield,
                        title = stringResource(R.string.settings_privacy_system_title),
                        description = if (SettingsManager.isOfflineMode(context)) {
                            stringResource(R.string.flux_offline_on)
                        } else {
                            stringResource(R.string.settings_privacy_system_description)
                        },
                        linkId = SettingLinkIds.MAIN_ADVANCED,
                        onClick = { onNavigate(SettingsDestination.Advanced) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun SettingsCategoryRow(
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    linkId: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (description == null) 56.dp else 72.dp)
            .settingRow(linkId?.takeIf { enabled }, onClick.takeIf { enabled })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (description != null) {
                    Text(
                        text = description,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

