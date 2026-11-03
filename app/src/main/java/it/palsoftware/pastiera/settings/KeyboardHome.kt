package it.palsoftware.pastiera.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fluxsniffermods.core.FluxApp
import it.palsoftware.pastiera.ImeIdentity
import it.palsoftware.pastiera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Flux Keyboard's home: whether it's ready (on, and the keyboard in use), its settings searched,
 * a row per section, and the other Flux apps. The gear above it opens Settings.
 */
@Composable
internal fun KeyboardHomeContent(searchHint: String = stringResource(R.string.keyboard_search)) {
    val context = LocalContext.current
    KeyboardReadinessCard(readyText = stringResource(R.string.keyboard_ready))
    SettingsSearchArea(
        fieldModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        hint = searchHint
    ) {
        // The keyboard as it's tuned for this phone, a tap away (it only changes the keyboard)
        RecommendedSettingsRow()
        KeyboardSettingsRows { destination -> openSettingsPage(context, SettingsPage(destination)) }
        FluxAppsRows()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The keyboard's page, as Settings opens it. */
@Composable
internal fun KeyboardPage(modifier: Modifier, onBack: () -> Unit) {
    FluxScreenScaffold(stringResource(R.string.module_keyboard_title), onBack, modifier) {
        KeyboardHomeContent()
    }
}

/**
 * The other Flux apps: what each does, a tap to open it, or to get it from its releases. Gaming
 * mode, streaming and the Linux desktop were Flux Titan Hub's; each is an app of its own now.
 */
@Composable
internal fun FluxAppsRows() {
    val context = LocalContext.current
    SettingsSectionDivider(stringResource(io.github.fluxsniffermods.core.R.string.flux_apps_section))
    FluxNote(stringResource(R.string.flux_apps_moved))
    listOf(
        FluxApp.GAMING to io.github.fluxsniffermods.core.R.string.flux_app_gaming,
        FluxApp.STREAMING to io.github.fluxsniffermods.core.R.string.flux_app_streaming,
        FluxApp.DESKTOP to io.github.fluxsniffermods.core.R.string.flux_app_desktop
    ).forEach { (app, what) ->
        val installed = remember(app) { app.installed(context) }
        FluxActionRow(
            linkId = null,
            icon = if (installed) null else "+",
            appPackage = app.packageName.takeIf { installed },
            title = app.title,
            description = stringResource(
                if (installed) io.github.fluxsniffermods.core.R.string.flux_app_open else io.github.fluxsniffermods.core.R.string.flux_app_get,
                stringResource(what)
            )
        ) { app.open(context) }
    }
}

/** Whether the keyboard is switched on in Android, and chosen as the one in use. */
internal data class ImeStatus(val enabled: Boolean, val selected: Boolean)

internal fun imeStatus(context: Context): ImeStatus = runCatching {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabledMethods = imm.enabledInputMethodList
    val enabled = enabledMethods.any { it.packageName == ImeIdentity.packageName || ImeIdentity.matchesImeId(it.id) }
    if (!enabled) return@runCatching ImeStatus(false, false)
    val selected = runCatching {
        ImeIdentity.matchesImeId(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty())
    }.getOrElse {
        // Android 14 and later may refuse to say which keyboard is in use: the only one switched on is
        enabledMethods.size == 1
    }
    ImeStatus(true, selected)
}.getOrDefault(ImeStatus(false, false))

/**
 * What the keyboard still needs (switched on, chosen), each a tap from its fix; one quiet line
 * once it's ready. Checked again on coming back and every few seconds.
 */
@Composable
internal fun KeyboardReadinessCard(modifier: Modifier = Modifier, readyText: String? = null) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(imeStatus(context)) }
    var check by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(check) {
        while (true) {
            status = withContext(Dispatchers.IO) { imeStatus(context) }
            delay(3_000)
        }
    }
    if (status.enabled && status.selected) {
        if (readyText != null) Text(
            "\u2713 " + readyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.readiness_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val on = status.enabled
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                    if (!on) runCatching { context.startActivity(android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    else runCatching { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
                    check++
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("\u25CB", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (on) R.string.readiness_keyboard_chosen else R.string.enable_pastiera),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(stringResource(if (on) R.string.readiness_keyboard_chosen_description else R.string.readiness_keyboard_on_description),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Text("\u203A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
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
}
