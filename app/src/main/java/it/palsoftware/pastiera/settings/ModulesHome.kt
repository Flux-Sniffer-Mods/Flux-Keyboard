package it.palsoftware.pastiera.settings

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.modules.FluxModules
import it.palsoftware.pastiera.modules.ModuleReadiness
import it.palsoftware.pastiera.modules.ModuleReadiness.Need
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Flux Titan Hub's home: a tile per module, two to a row, all as tall as the tallest. A tile opens
 * its module (the page its app-list entry opens, with a Turn on bar while it's off), a long-press
 * or its ⚙ its settings. Each module has a switch of its own on the tile (the keyboard's asks
 * first), and a line saying how it's doing or what it still needs; the desktop is still to come.
 */
@Composable
internal fun ModuleTiles(onSettings: (FluxModule) -> Unit = {}, onOpen: (FluxModule) -> Unit) {
    val context = LocalContext.current
    var states by remember { mutableStateOf(FluxModule.entries.associateWith { FluxModules.on(context, it) }) }
    // Read at once (quick), so the tiles open saying it; the games counted as before until counted again
    var statuses by remember { mutableStateOf(FluxModule.entries.associateWith { ModuleReadiness.status(context, it) }) }
    var games by remember { mutableStateOf(knownGames) }
    var check by remember { mutableIntStateOf(0) }
    // Read again on coming back: a module's own page can switch it on or off, or fix what it needs
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                states = FluxModule.entries.associateWith { FluxModules.on(context, it) }
                check++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // How each is doing, every few seconds (a stream starts, the shell comes up); the games once
    LaunchedEffect(check) {
        games = withContext(Dispatchers.IO) {
            runCatching { it.palsoftware.pastiera.gaming.GamesActivity.games(context).size }.getOrNull()
        }?.also { knownGames = it } ?: games
        while (true) {
            statuses = withContext(Dispatchers.IO) { FluxModule.entries.associateWith { ModuleReadiness.status(context, it) } }
            delay(3_000)
        }
    }
    var askKeyboardOff by remember { mutableStateOf(false) }
    fun set(module: FluxModule, on: Boolean) {
        FluxModules.setOn(context, module, on)
        states = states + (module to on)
        check++
    }
    TileGrid(gap = 12.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        FluxModule.entries.forEach { module ->
            val on = states[module] == true
            val (line, needsFix) = tileLine(module, on, statuses[module], games)
            ModuleTile(
                module = module,
                on = on,
                line = line,
                needsFix = needsFix,
                // The keyboard off takes it away as a keyboard: asked first
                onToggle = { turnOn -> if (module == FluxModule.KEYBOARD && !turnOn) askKeyboardOff = true else set(module, turnOn) },
                onOpen = { onOpen(module) },
                onSettings = { onSettings(module) }
            )
        }
    }
    if (askKeyboardOff) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askKeyboardOff = false },
        title = { Text(stringResource(R.string.keyboard_off_title)) },
        text = { Text(stringResource(R.string.keyboard_off_text)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { askKeyboardOff = false; set(FluxModule.KEYBOARD, false) }) {
                Text(stringResource(R.string.keyboard_off_confirm))
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askKeyboardOff = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

/** The games counted last, for the gaming tile's line as the home screen opens again. */
@Volatile private var knownGames: Int? = null

/**
 * A tile's line: what the module does while it's off; on, how it's doing, or the first thing it
 * still needs (true: shown as needing a fix, which its page has).
 */
@Composable
private fun tileLine(module: FluxModule, on: Boolean, status: ModuleReadiness.Status?, games: Int?): Pair<String, Boolean> {
    val description = stringResource(when (module) {
        FluxModule.KEYBOARD -> R.string.module_keyboard_description
        FluxModule.GAMING -> R.string.module_gaming_description
        FluxModule.STREAMING -> R.string.module_streaming_description
        FluxModule.DESKTOP -> R.string.module_desktop_description
    })
    if (!on || status == null) return description to false
    val need = status.needs.firstOrNull()
    val streamingTo = status.streamingTo
    return when {
        module == FluxModule.STREAMING && streamingTo != null -> stringResource(R.string.tile_streaming_to, streamingTo) to false
        need != null -> stringResource(R.string.tile_fix, stringResource(when (need) {
            Need.KEYBOARD_ON -> R.string.tile_need_keyboard_on
            Need.KEYBOARD_CHOSEN -> R.string.tile_need_keyboard_chosen
            Need.SHELL -> R.string.tile_need_shell
            Need.ACCESSIBILITY -> R.string.tile_need_accessibility
            Need.OVERLAY -> R.string.tile_need_overlay
            Need.NOTIFICATIONS -> R.string.tile_need_notifications
            Need.PAIRED -> R.string.tile_need_paired
        })) to true
        module == FluxModule.KEYBOARD -> stringResource(R.string.tile_keyboard_ready) to false
        module == FluxModule.GAMING -> when (games) {
            null -> description
            0 -> stringResource(R.string.tile_gaming_no_games)
            else -> stringResource(R.string.tile_gaming_ready, games)
        } to false
        module == FluxModule.STREAMING ->
            (if (status.listening) stringResource(R.string.tile_streaming_ready, status.paired) else stringResource(R.string.tile_streaming_off)) to false
        // Running, installed or not yet
        module == FluxModule.DESKTOP -> stringResource(when {
            it.palsoftware.pastiera.linux.LinuxDesktop.state.value == it.palsoftware.pastiera.linux.LinuxDesktop.State.Running -> R.string.tile_desktop_running
            it.palsoftware.pastiera.linux.LinuxDesktop.installedMode(LocalContext.current) != null -> R.string.tile_desktop_installed
            else -> R.string.tile_desktop_not_installed
        }) to false
        else -> description to false
    }
}

/** Two tiles to a row, [gap] apart, every tile as tall as the tallest. */
@Composable
private fun TileGrid(gap: androidx.compose.ui.unit.Dp, modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val cell = ((constraints.maxWidth - gapPx) / 2).coerceAtLeast(0)
        val height = measurables.maxOfOrNull { it.maxIntrinsicHeight(cell) } ?: 0
        val tiles = measurables.map { it.measure(androidx.compose.ui.unit.Constraints.fixed(cell, height)) }
        val rows = (tiles.size + 1) / 2
        layout(constraints.maxWidth, rows * height + (rows - 1).coerceAtLeast(0) * gapPx) {
            tiles.forEachIndexed { i, tile -> tile.placeRelative((i % 2) * (cell + gapPx), (i / 2) * (height + gapPx)) }
        }
    }
}

private fun moduleIcon(module: FluxModule): ImageVector = when (module) {
    FluxModule.KEYBOARD -> Icons.Filled.Keyboard
    FluxModule.GAMING -> Icons.Filled.SportsEsports
    FluxModule.STREAMING -> Icons.Filled.WifiTethering
    FluxModule.DESKTOP -> Icons.Filled.DesktopWindows
}

@Composable
internal fun moduleTitle(module: FluxModule): String = stringResource(when (module) {
    FluxModule.KEYBOARD -> R.string.module_keyboard_title
    FluxModule.GAMING -> R.string.game_mode_title
    FluxModule.STREAMING -> R.string.module_streaming_title
    FluxModule.DESKTOP -> R.string.module_desktop_title
})

/**
 * A module's colours: the wallpaper's (the theme's), each module a step further round the hue
 * than the one before it, so the tiles run as a gradient (keyboard, gaming, streaming, then the
 * desktop) and tell apart at a glance while still matching the phone.
 */
@Composable
private fun moduleColors(module: FluxModule): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val container = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.primary
    val turn = when (module) {
        FluxModule.KEYBOARD -> 0f
        FluxModule.GAMING -> HUE_STEP
        FluxModule.STREAMING -> HUE_STEP * 2
        FluxModule.DESKTOP -> HUE_STEP * 3
    }
    return remember(container, accent, turn) { turned(container, turn) to turned(accent, turn) }
}

/** How far round the hue each module is from the one before it. */
private const val HUE_STEP = -35f

private fun turned(color: androidx.compose.ui.graphics.Color, degrees: Float): androidx.compose.ui.graphics.Color {
    if (degrees == 0f) return color
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[0] = ((hsl[0] + degrees) % 360f + 360f) % 360f
    return androidx.compose.ui.graphics.Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ModuleTile(
    module: FluxModule, on: Boolean, line: String, needsFix: Boolean,
    onToggle: (Boolean) -> Unit, onOpen: () -> Unit, onSettings: () -> Unit
) {
    val soon = FluxModules.comingSoon(module)
    val shape = RoundedCornerShape(16.dp)
    val (container, accent) = moduleColors(module)
    Surface(
        shape = shape,
        color = if (on) container else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.heightIn(min = 136.dp).clip(shape)
            .combinedClickable(enabled = !soon, onClick = onOpen, onLongClick = onSettings)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The same height with or without a switch, so every title lines up
            Row(modifier = Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = moduleIcon(module),
                    contentDescription = null,
                    tint = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                // Its settings (the keyboard's tile opens its settings already)
                if (FluxModules.switchable(module) && module != FluxModule.KEYBOARD) {
                    val settingsLabel = stringResource(R.string.settings_title)
                    androidx.compose.material3.IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                        Text(
                            "\u2699",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { contentDescription = settingsLabel }
                        )
                    }
                }
                if (FluxModules.switchable(module)) Switch(checked = on, onCheckedChange = onToggle)
            }
            Text(
                text = moduleTitle(module),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = if (needsFix) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The keyboard's page, the same however it's opened (its tile, its entry in the app list, the
 * keyboard's own Settings button): what it still needs (nothing shown once it's on and chosen),
 * its settings with a search, and a field to try it in.
 */
@Composable
internal fun KeyboardPage(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val on = rememberModuleOn(FluxModule.KEYBOARD)
    FluxScreenScaffold(stringResource(R.string.module_keyboard_title), onBack, modifier) {
        if (!on.value) ModuleOffBar(FluxModule.KEYBOARD) { on.value = true }
        else ReadinessCard(FluxModule.KEYBOARD, readyText = stringResource(R.string.keyboard_ready))
        SettingsSearchArea(
            fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            scope = SettingLinkRegistry.keyboardCategories,
            hint = stringResource(R.string.keyboard_search)
        ) {
            // The keyboard as it's tuned for this phone, a tap away (it only changes the keyboard)
            RecommendedSettingsRow()
            KeyboardSettingsRows { destination -> openSettingsPage(context, SettingsPage(destination)) }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Whether [module] is on, read again on coming back (its tile, or a Quick Settings tile, may switch it). */
@Composable
internal fun rememberModuleOn(module: FluxModule): androidx.compose.runtime.MutableState<Boolean> {
    val context = LocalContext.current
    val on = remember(module) { mutableStateOf(FluxModules.on(context, module)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, module) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on.value = FluxModules.on(context, module)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return on
}

/**
 * A module that's off, opened anyway: says so, with a button to turn it on, rather than
 * turning on by itself as it's opened.
 */
@Composable
internal fun ModuleOffBar(module: FluxModule, onTurnedOn: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.module_off_title, moduleTitle(module)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    stringResource(when (module) {
                        FluxModule.KEYBOARD -> R.string.module_off_keyboard
                        FluxModule.GAMING -> R.string.module_off_gaming
                        FluxModule.STREAMING -> R.string.module_off_streaming
                        FluxModule.DESKTOP -> R.string.module_off_desktop
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            androidx.compose.material3.Button(onClick = {
                FluxModules.setOn(context, module, true)
                onTurnedOn()
            }) { Text(stringResource(R.string.module_turn_on)) }
        }
    }
}

/** The keyboard's settings, a row per section: on its settings page and its page on the home screen. */
@Composable
internal fun KeyboardSettingsRows(onNavigate: (SettingsDestination) -> Unit) {
    SettingsCategoryRow(
        icon = Icons.Filled.Tune,
        title = stringResource(R.string.quick_settings_title),
        description = stringResource(R.string.quick_settings_row_description),
        linkId = "main.quick_settings",
        onClick = { onNavigate(SettingsDestination.QuickSettings) }
    )
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
        title = stringResource(R.string.settings_privacy_clipboard_title),
        description = stringResource(R.string.settings_privacy_clipboard_description),
        linkId = SettingLinkIds.MAIN_ADVANCED,
        onClick = { onNavigate(SettingsDestination.Advanced) }
    )
    // What the shell adds: the keyboard light, screen sizes and shell shortcuts
    SettingsCategoryRow(
        icon = Icons.Filled.Terminal,
        title = stringResource(R.string.shell_extras_title),
        description = stringResource(R.string.shell_extras_description),
        linkId = "main.shell_extras",
        onClick = { onNavigate(SettingsDestination.ShellExtras) }
    )
    // This page as an app of its own in the home screen's app list, as Games and Streaming are
    val context = LocalContext.current
    var inAppList by remember { mutableStateOf(it.palsoftware.pastiera.KeyboardActivity.inAppList(context)) }
    FluxSwitchRow(
        linkId = "main.keyboard_app",
        title = stringResource(R.string.keyboard_app_switch),
        description = stringResource(R.string.keyboard_app_switch_description),
        checked = inAppList
    ) { on ->
        inAppList = on
        it.palsoftware.pastiera.KeyboardActivity.setInAppList(context, on)
    }
}

/**
 * Streaming's settings, the same from its tile and the Streaming app's ⚙: the module switched on
 * or off, what it still needs, the Streaming app in the app list, then its settings by group.
 */
@Composable
internal fun StreamingHomeScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val on = rememberModuleOn(FluxModule.STREAMING)
    FluxScreenScaffold(stringResource(R.string.module_streaming_title), onBack, modifier) {
        FluxSwitchRow(
            linkId = "main.streaming",
            title = stringResource(R.string.module_streaming_title),
            description = stringResource(R.string.module_streaming_switch_description),
            checked = on.value
        ) { checked ->
            on.value = checked
            FluxModules.setOn(context, FluxModule.STREAMING, checked)
        }
        if (on.value) {
            ReadinessCard(FluxModule.STREAMING)
            SettingsSearchArea(
                fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                scope = setOf(SettingsDestination.Streaming),
                hint = stringResource(R.string.streaming_search)
            ) {
            StreamingSettings()
            // The Streaming app in the home screen's app list (on with streaming), at the bottom as the keyboard's is
            SettingsSectionDivider(stringResource(R.string.streaming_app_section))
            var inAppList by remember { mutableStateOf(it.palsoftware.pastiera.streaming.StreamApps.inAppList(context)) }
            FluxSwitchRow(
                linkId = "streaming.app",
                title = stringResource(R.string.streaming_app_switch),
                description = stringResource(R.string.streaming_app_switch_description),
                checked = inAppList
            ) { checked ->
                inAppList = checked
                it.palsoftware.pastiera.streaming.StreamApps.setInAppList(context, checked)
            }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
