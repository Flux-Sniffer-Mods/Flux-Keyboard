package it.palsoftware.pastiera.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.modules.FluxModules

/**
 * Flux Titan Hub's home: a tile per module, two to a row. A tile opens its module's settings; a
 * module that's off is switched on as it's opened. Gaming mode and streaming have a switch of
 * their own on the tile; the keyboard is always there; the desktop is still to come.
 */
@Composable
internal fun ModuleTiles(onOpen: (FluxModule) -> Unit) {
    val context = LocalContext.current
    var states by remember { mutableStateOf(FluxModule.entries.associateWith { FluxModules.on(context, it) }) }
    fun set(module: FluxModule, on: Boolean) {
        FluxModules.setOn(context, module, on)
        states = states + (module to on)
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FluxModule.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { module ->
                    ModuleTile(
                        module = module,
                        on = states[module] == true,
                        modifier = Modifier.weight(1f),
                        onToggle = { on -> set(module, on) },
                        onOpen = {
                            if (FluxModules.switchable(module) && states[module] != true) set(module, true)
                            onOpen(module)
                        }
                    )
                }
            }
        }
    }
}

private fun moduleIcon(module: FluxModule): ImageVector = when (module) {
    FluxModule.KEYBOARD -> Icons.Filled.Keyboard
    FluxModule.GAMING -> Icons.Filled.SportsEsports
    FluxModule.STREAMING -> Icons.Filled.Cast
    FluxModule.DESKTOP -> Icons.Filled.DesktopWindows
}

@Composable
private fun moduleTitle(module: FluxModule): String = stringResource(when (module) {
    FluxModule.KEYBOARD -> R.string.module_keyboard_title
    FluxModule.GAMING -> R.string.game_mode_title
    FluxModule.STREAMING -> R.string.module_streaming_title
    FluxModule.DESKTOP -> R.string.module_desktop_title
})

@Composable
private fun ModuleTile(module: FluxModule, on: Boolean, modifier: Modifier, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val soon = FluxModules.comingSoon(module)
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.heightIn(min = 136.dp).clip(shape).clickable(enabled = !soon, onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = moduleIcon(module),
                    contentDescription = null,
                    tint = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (FluxModules.switchable(module)) Switch(checked = on, onCheckedChange = onToggle)
            }
            Text(
                text = moduleTitle(module),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (soon) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(when (module) {
                    FluxModule.KEYBOARD -> R.string.module_keyboard_description
                    FluxModule.GAMING -> R.string.module_gaming_description
                    FluxModule.STREAMING -> R.string.module_streaming_description
                    FluxModule.DESKTOP -> R.string.module_coming_soon
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The keyboard's own settings, under its tile. */
@Composable
internal fun KeyboardHomeScreen(modifier: Modifier, onBack: () -> Unit, onNavigate: (SettingsDestination) -> Unit) {
    FluxScreenScaffold(stringResource(R.string.module_keyboard_title), onBack, modifier) {
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
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Streaming's settings, under its tile: the module switched on or off, then its settings. */
@Composable
internal fun StreamingHomeScreen(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(FluxModules.on(context, FluxModule.STREAMING)) }
    FluxScreenScaffold(stringResource(R.string.module_streaming_title), onBack, modifier) {
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.module_streaming_title),
            description = stringResource(R.string.module_streaming_switch_description),
            checked = on
        ) { checked ->
            on = checked
            FluxModules.setOn(context, FluxModule.STREAMING, checked)
        }
        if (on) StreamingSettings()
        Spacer(modifier = Modifier.height(16.dp))
    }
}
