package it.palsoftware.pastiera

import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.shortcuts.AppActionDiscovery
import it.palsoftware.pastiera.shortcuts.AppCategory
import it.palsoftware.pastiera.shortcuts.AppIntent
import it.palsoftware.pastiera.shortcuts.AppShortcutAppSettings
import it.palsoftware.pastiera.shortcuts.AppShortcutConfig
import it.palsoftware.pastiera.shortcuts.AppShortcutPresets
import it.palsoftware.pastiera.shortcuts.AppShortcutRemapper
import it.palsoftware.pastiera.shortcuts.AppShortcutSettings
import it.palsoftware.pastiera.shortcuts.DiscoveredAction
import it.palsoftware.pastiera.shortcuts.KeyCombo
import it.palsoftware.pastiera.shortcuts.ShortcutScope
import it.palsoftware.pastiera.shortcuts.StandardShortcut

/*
 * Settings > Apps > App shortcuts: the standard combos, the apps they are sent to
 * (the built-in presets plus any you add), and one screen per app to change its shortcuts.
 */

private const val CHILD_SECTION = "app_shortcuts"

/** The stored settings, kept current while the screen is open. */
@Composable
private fun rememberAppShortcutConfig(): AppShortcutConfig {
    val context = LocalContext.current
    var config by remember { mutableStateOf(AppShortcutSettings.config(context)) }
    val prefs = remember { SettingsManager.getPreferences(context) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppShortcutSettings.KEY_APPS || key == AppShortcutSettings.KEY_ENABLED) {
                config = AppShortcutSettings.config(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return config
}

@Composable
fun AppShortcutsSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val appPackage = remember { settingsChild(context, CHILD_SECTION) }
    if (appPackage != null) {
        AppShortcutsAppScreen(modifier = modifier, packageName = appPackage, onBack = onBack)
        return
    }
    val config = rememberAppShortcutConfig()
    val installed = remember { AppListHelper.getInstalledApps(context).map { it.packageName }.toSet() }
    var showAppPicker by remember { mutableStateOf(false) }
    var showNotInstalled by remember { mutableStateOf(false) }

    FluxScreenScaffold(stringResource(R.string.app_shortcuts_title), onBack, modifier) {
        FluxNote(stringResource(R.string.app_shortcuts_intro))
        FluxSwitchRow(
            linkId = SettingLinkIds.APP_SHORTCUTS_ENABLED,
            title = stringResource(R.string.app_shortcuts_enabled_title),
            description = stringResource(R.string.app_shortcuts_enabled_description),
            checked = config.enabled,
            onCheckedChange = { AppShortcutSettings.setEnabled(context, it) }
        )
        FluxSwitchRow(
            linkId = SettingLinkIds.APP_SHORTCUTS_SUGGESTIONS,
            title = stringResource(R.string.app_shortcuts_suggestions_title),
            description = stringResource(R.string.app_shortcuts_suggestions_description),
            checked = config.suggestionsEnabled,
            onCheckedChange = { AppShortcutSettings.setSuggestionsEnabled(context, it) }
        )

        SettingsSectionDivider(stringResource(R.string.app_shortcuts_section_standard))
        FluxNote(stringResource(R.string.app_shortcuts_keys_note))
        StandardShortcut.entries.forEach { shortcut ->
            // As pressed on this keyboard; shortcuts it can't make aren't listed
            val keys = pressLabel(shortcut.combo) ?: return@forEach
            ComboRow(
                title = stringResource(shortcut.titleRes),
                combo = keys,
                note = if (shortcut.scope == ShortcutScope.OutsideTextFields) {
                    stringResource(R.string.app_shortcuts_scope_outside_text_fields)
                } else {
                    null
                }
            )
        }

        SettingsSectionDivider(stringResource(R.string.app_shortcuts_section_apps))
        val customApps = config.apps.filterKeys { AppShortcutPresets.forPackage(it) == null }
        val rows = AppShortcutPresets.all.map { Triple(it.packageName, it.appName, it.category) } +
            customApps.map { (pkg, settings) -> Triple(pkg, settings.appName ?: pkg, AppCategory.Other) }
        val (installedRows, otherRows) = rows.partition { it.first in installed }
        // Installed apps first, by category; the others only when asked for
        val groups = listOf(installedRows, if (showNotInstalled) otherRows else emptyList()).flatMap { part ->
            part.groupBy { it.third }.toSortedMap(compareBy { it.ordinal }).toList()
        }
        groups.forEach { (category, apps) ->
            Text(
                text = stringResource(category.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            apps.forEach { (packageName, appName, _) ->
                val keys = StandardShortcut.entries.count { shortcut ->
                    AppShortcutRemapper.effectiveShortcut(config, packageName, shortcut)
                        ?.let { it != shortcut.combo } == true
                }
                val suggestions = StandardShortcut.entries.count { shortcut ->
                    AppShortcutRemapper.suggestedIntents(config, packageName, shortcut).isNotEmpty()
                }
                AppRow(
                    packageName = packageName,
                    title = appName,
                    description = buildList {
                        if (keys > 0) add(context.resources.getQuantityString(R.plurals.app_shortcuts_remapped_count, keys, keys))
                        if (suggestions > 0) add(context.resources.getQuantityString(R.plurals.app_shortcuts_suggested_count, suggestions, suggestions))
                        if (packageName !in installed) add(context.getString(R.string.app_shortcuts_not_installed))
                    }.joinToString(" · "),
                    checked = AppShortcutRemapper.isAppEnabled(config, packageName),
                    enabled = config.enabled,
                    onCheckedChange = { on ->
                        val current = config.apps[packageName] ?: AppShortcutAppSettings()
                        AppShortcutSettings.setApp(context, packageName, current.copy(enabled = on))
                    },
                    onClick = { openSettingsChild(context, CHILD_SECTION, packageName) }
                )
            }
        }
        if (otherRows.isNotEmpty()) {
            FluxSwitchRow(
                linkId = null,
                title = stringResource(R.string.app_shortcuts_show_not_installed),
                description = context.resources.getQuantityString(
                    R.plurals.app_shortcuts_not_installed_count, otherRows.size, otherRows.size
                ),
                checked = showNotInstalled,
                onCheckedChange = { showNotInstalled = it }
            )
        }
        Surface(modifier = Modifier.fillMaxWidth().settingRow(null) { showAppPicker = true }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.app_shortcuts_add_app), style = MaterialTheme.typography.titleMedium)
            }
        }
        FluxNote(stringResource(R.string.app_shortcuts_research_note))
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showAppPicker) {
        AppPickerDialog(
            excludePackages = remember { keyboardPackages(context) },
            onAppSelected = { app ->
                showAppPicker = false
                if (config.apps[app.packageName] == null && AppShortcutPresets.forPackage(app.packageName) == null) {
                    AppShortcutSettings.setApp(context, app.packageName, AppShortcutAppSettings(appName = app.appName))
                }
                openSettingsChild(context, CHILD_SECTION, app.packageName)
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

/** One app: what each standard combo becomes in it, and changing that. */
@Composable
private fun AppShortcutsAppScreen(modifier: Modifier, packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val config = rememberAppShortcutConfig()
    val preset = AppShortcutPresets.forPackage(packageName)
    val settings = config.apps[packageName] ?: AppShortcutAppSettings()
    val title = preset?.appName ?: settings.appName ?: packageName
    var editing by remember { mutableStateOf<StandardShortcut?>(null) }
    val discovered = remember(packageName) { AppActionDiscovery.discover(context, packageName) }

    fun save(update: (AppShortcutAppSettings) -> AppShortcutAppSettings) {
        AppShortcutSettings.setApp(context, packageName, update(config.apps[packageName] ?: AppShortcutAppSettings()))
    }

    FluxScreenScaffold(title, onBack, modifier) {
        FluxNote(
            when {
                preset?.source != null -> stringResource(R.string.app_shortcuts_app_source, preset.source)
                preset != null -> stringResource(R.string.app_shortcuts_app_suggested_only)
                else -> stringResource(R.string.app_shortcuts_app_custom)
            }
        )
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.app_shortcuts_app_enabled_title),
            description = stringResource(R.string.app_shortcuts_app_enabled_description, title),
            appPackage = packageName,
            checked = settings.enabled,
            onCheckedChange = { on -> save { it.copy(enabled = on) } }
        )
        SettingsSectionDivider(stringResource(R.string.app_shortcuts_section_mapping))
        StandardShortcut.entries.forEach { shortcut ->
            val target = AppShortcutRemapper.effectiveShortcut(config, packageName, shortcut)
            val suggestion = AppShortcutRemapper.suggestedIntents(config, packageName, shortcut).firstOrNull()
            val fromApp = AppShortcutRemapper.discoveredAction(config, packageName, shortcut, discovered)
            val changed = settings.overrides.containsKey(shortcut)
            val keys = pressLabel(shortcut.combo) ?: return@forEach
            ComboRow(
                title = stringResource(shortcut.titleRes),
                combo = keys,
                note = when {
                    target == null && suggestion != null -> suggestionLabel(suggestion)
                    target == null && fromApp != null -> discoveredLabel(fromApp)
                    target == null -> stringResource(R.string.app_shortcuts_not_remapped)
                    target == shortcut.combo -> stringResource(R.string.app_shortcuts_same_in_app)
                    else -> stringResource(R.string.app_shortcuts_sent_as, keyComboLabel(target))
                } + if (changed) " · " + stringResource(R.string.app_shortcuts_changed_by_you) else "",
                onClick = { editing = shortcut }
            )
        }
        SettingsSectionDivider(stringResource(R.string.app_shortcuts_section_discovered))
        FluxNote(stringResource(R.string.app_shortcuts_discovered_note))
        val numbered = listOf(
            StandardShortcut.AppAction1, StandardShortcut.AppAction2,
            StandardShortcut.AppAction3, StandardShortcut.AppAction4
        )
        // The first four have Ctrl+Alt+1 to 4; the rest are listed so you know they exist
        val offered = discovered.launcherShortcuts.mapIndexed { index, action ->
            action.label to (numbered.getOrNull(index)?.let { pressLabel(it.combo) } ?: "")
        } + listOfNotNull(discovered.settings?.let {
            stringResource(R.string.app_shortcuts_opens_settings) to (pressLabel(StandardShortcut.Settings.combo) ?: "")
        })
        if (offered.isEmpty()) {
            FluxNote(stringResource(R.string.app_shortcuts_discovered_none))
        }
        offered.forEach { (label, combo) -> ComboRow(title = label, combo = combo, note = null) }
        if (settings.overrides.isNotEmpty() && preset != null) {
            FluxActionRow(
                linkId = null,
                title = stringResource(R.string.app_shortcuts_reset_app),
                description = stringResource(R.string.app_shortcuts_reset_app_description),
                onClick = { save { it.copy(overrides = emptyMap()) } }
            )
        }
        if (preset == null) {
            FluxActionRow(
                linkId = null,
                title = stringResource(R.string.app_shortcuts_remove_app),
                description = stringResource(R.string.app_shortcuts_remove_app_description),
                onClick = {
                    AppShortcutSettings.setApp(context, packageName, null)
                    onBack()
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    editing?.let { shortcut ->
        ShortcutCaptureDialog(
            title = stringResource(shortcut.titleRes),
            standard = shortcut.combo,
            presetCombo = preset?.shortcuts?.get(shortcut),
            onSave = { combo ->
                save { it.copy(overrides = it.overrides + (shortcut to combo)) }
                editing = null
            },
            onUsePreset = {
                save { it.copy(overrides = it.overrides - shortcut) }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/**
 * Press the app's shortcut on the keyboard. Save sends it for the standard combo,
 * "Don't remap" leaves the standard combo alone in this app.
 */
@Composable
private fun ShortcutCaptureDialog(
    title: String,
    standard: KeyCombo,
    presetCombo: KeyCombo?,
    onSave: (KeyCombo?) -> Unit,
    onUsePreset: () -> Unit,
    onDismiss: () -> Unit
) {
    var captured by remember { mutableStateOf<KeyCombo?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.app_shortcuts_capture_instruction, keyComboLabel(standard)),
                    style = MaterialTheme.typography.bodyMedium
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            val native = keyEvent.nativeKeyEvent
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                            if (KeyEvent.isModifierKey(native.keyCode) || native.keyCode == KeyEvent.KEYCODE_BACK) {
                                return@onPreviewKeyEvent native.keyCode != KeyEvent.KEYCODE_BACK
                            }
                            captured = KeyCombo(
                                native.keyCode,
                                ctrl = native.isCtrlPressed,
                                alt = native.isAltPressed,
                                shift = native.isShiftPressed,
                                meta = native.isMetaPressed
                            )
                            true
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = captured?.let { keyComboLabel(it) }
                            ?: stringResource(R.string.app_shortcuts_capture_waiting),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (presetCombo != null) {
                    Text(
                        stringResource(R.string.app_shortcuts_capture_preset, keyComboLabel(presetCombo)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.app_shortcuts_dont_remap)) }
                    TextButton(onClick = onUsePreset) { Text(stringResource(R.string.app_shortcuts_use_preset)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(captured) }, enabled = captured != null) {
                Text(stringResource(R.string.app_shortcuts_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
private fun ComboRow(title: String, combo: String, note: String?, onClick: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(null, onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = combo,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(112.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    packageName: String,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(null, onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FluxAppIcon(packageName)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

/** What a suggested intent opens, for the app's screen. */
@Composable
private fun suggestionLabel(intent: AppIntent): String = when (intent.action) {
    AppIntent.ACTION_SEND -> stringResource(R.string.app_shortcuts_suggested_share)
    AppIntent.ACTION_SEARCH -> stringResource(R.string.app_shortcuts_suggested_search)
    else -> stringResource(R.string.app_shortcuts_suggested_link, intent.data.orEmpty())
}

@Composable
private fun discoveredLabel(action: DiscoveredAction): String =
    if (action.id == "settings" && action.label.isEmpty()) {
        stringResource(R.string.app_shortcuts_opens_settings)
    } else {
        stringResource(R.string.app_shortcuts_opens_discovered, action.label)
    }

/** Keyboards (input methods) never get app shortcuts. */
private fun keyboardPackages(context: android.content.Context): Set<String> {
    val manager = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
        as? android.view.inputmethod.InputMethodManager ?: return emptySet()
    return manager.inputMethodList.map { it.packageName }.toSet()
}

/** "Ctrl+Shift+R", "Alt+↓", "?" */
internal fun keyComboLabel(combo: KeyCombo): String {
    if (combo.shift && !combo.ctrl && !combo.alt && !combo.meta) {
        when (combo.keyCode) {
            KeyEvent.KEYCODE_SLASH -> return "?"
            KeyEvent.KEYCODE_3 -> return "#"
        }
    }
    return buildString {
        if (combo.ctrl) append("Ctrl+")
        if (combo.alt) append("Alt+")
        if (combo.shift) append("Shift+")
        if (combo.meta) append("Meta+")
        append(keyName(combo.keyCode))
    }
}

internal fun keyName(keyCode: Int): String = when (keyCode) {
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (keyCode - KeyEvent.KEYCODE_A)).toString()
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (keyCode - KeyEvent.KEYCODE_0)).toString()
    KeyEvent.KEYCODE_ENTER -> "Enter"
    KeyEvent.KEYCODE_ESCAPE -> "Esc"
    KeyEvent.KEYCODE_TAB -> "Tab"
    KeyEvent.KEYCODE_SPACE -> "Space"
    KeyEvent.KEYCODE_DEL -> "Backspace"
    KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    KeyEvent.KEYCODE_SLASH -> "/"
    KeyEvent.KEYCODE_POUND -> "#"
    KeyEvent.KEYCODE_COMMA -> ","
    KeyEvent.KEYCODE_PERIOD -> "."
    KeyEvent.KEYCODE_MINUS -> "-"
    KeyEvent.KEYCODE_EQUALS -> "="
    KeyEvent.KEYCODE_LEFT_BRACKET -> "["
    KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
    else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}

/** [combo] as pressed on the keyboard in use ("Ctrl+Alt+W (1)"), or null when it can't be made. */
@Composable
private fun pressLabel(combo: KeyCombo): String? {
    val context = LocalContext.current
    val altLayer = remember {
        runCatching { it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver.resolve(context.assets, context) }
            .getOrDefault(emptyMap())
    }
    return it.palsoftware.pastiera.shortcuts.ShortcutKeys.howToPress(
        combo, altLayer, it.palsoftware.pastiera.shortcuts.ShortcutKeys::keyboardHas, ::keyComboLabel, ::keyName
    )
}
