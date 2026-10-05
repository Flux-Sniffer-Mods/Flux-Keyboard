package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Palette
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

/**
 * Flux Keyboard's own tutorial pages: one-step setup, then what's different from Pastiera,
 * each with a button to its setting.
 */

/** The settings the tutorial's buttons open. */
internal val fluxTutorialSettingIds = listOf("flux_emoji.picker_key", "auto_correction.spell_checker", "main.app_shortcuts", "hidden_apps.apps", "led_colors.individual", "main.typing", "main.root")

/** Opens a setting by its link ID, as search and deep links do. */
private fun openTutorialSetting(context: Context, id: String) {
    val entry = SettingLinkRegistry.byId(id) ?: return
    val visible = SettingLinkRegistry.visibleTarget(context, entry)
    val intent = if (visible.route.symCustomization) {
        Intent(context, SymCustomizationActivity::class.java)
            .putExtra(SymCustomizationActivity.EXTRA_SETTING_ID, visible.id)
    } else {
        Intent(context, SettingsActivity::class.java)
            .setData(android.net.Uri.parse("fluxkeyboard://setting/${visible.id}"))
    }
    context.startActivity(intent)
}

@Composable
fun FluxTutorialSetupPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var differing by remember { mutableIntStateOf(RecommendedSettings.differingSettings(context)) }
    val bullets = buildList {
        add(stringResource(R.string.flux_tutorial_setup_bullet_change))
        add(stringResource(R.string.flux_tutorial_setup_bullet_again))
        if (!DeviceSpecific.isTitan2EliteDevice()) add(stringResource(R.string.flux_tutorial_setup_bullet_other_phone))
    }
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_setup_title),
        description = stringResource(R.string.flux_tutorial_setup_description),
        icon = Icons.Filled.AutoFixHigh,
        tint = MaterialTheme.colorScheme.primary,
        bullets = bullets,
        buttonText = if (differing > 0) {
            stringResource(R.string.flux_tutorial_setup_button, differing)
        } else {
            stringResource(R.string.flux_tutorial_setup_applied)
        },
        buttonEnabled = differing > 0,
        onButtonClick = {
            if (!RecommendedSettings.apply(context)) {
                Toast.makeText(context, R.string.flux_tutorial_setup_failed, Toast.LENGTH_SHORT).show()
            }
            differing = RecommendedSettings.differingSettings(context)
        },
        modifier = modifier
    )
}

@Composable
fun FluxTutorialEmojiPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_emoji_title),
        description = stringResource(R.string.flux_tutorial_emoji_description),
        icon = Icons.Filled.EmojiEmotions,
        tint = MaterialTheme.colorScheme.tertiary,
        bullets = listOf(
            stringResource(R.string.flux_tutorial_emoji_bullet_key),
            stringResource(R.string.flux_tutorial_emoji_bullet_pages),
            stringResource(R.string.flux_tutorial_emoji_bullet_gif),
            stringResource(R.string.flux_tutorial_emoji_bullet_symbols),
            stringResource(R.string.flux_tutorial_emoji_bullet_tones),
            stringResource(R.string.flux_tutorial_emoji_bullet_sticky)
        ),
        buttonText = stringResource(R.string.flux_tutorial_emoji_button),
        onButtonClick = { openTutorialSetting(context, "flux_emoji.picker_key") },
        modifier = modifier,
        extraContent = { SkinToneChooser() }
    )
}

/** The default skin tone, as in Emoji & GIFs: a waving hand in each tone, the chosen one marked. */
@Composable
private fun SkinToneChooser() {
    val context = LocalContext.current
    val tones = it.palsoftware.pastiera.data.emoji.EmojiSkinTone
    var chosen by remember { mutableIntStateOf(tones.get(context)) }
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        androidx.compose.material3.Text(stringResource(R.string.emoji_skin_tone_title), style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            (0..tones.MODIFIERS.size).forEach { tone ->
                val hand = "\uD83D\uDC4B" + (if (tone == 0) "" else tones.MODIFIERS[tone - 1])
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (chosen == tone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable {
                        chosen = tone
                        tones.set(context, tone)
                    }
                ) {
                    androidx.compose.material3.Text(hand, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(6.dp))
                }
            }
        }
    }
}

@Composable
fun FluxTutorialTypingPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_typing_title),
        description = stringResource(R.string.flux_tutorial_typing_description),
        icon = Icons.Filled.Bolt,
        tint = MaterialTheme.colorScheme.primary,
        bullets = listOf(
            stringResource(R.string.flux_tutorial_typing_bullet_pick),
            stringResource(R.string.flux_tutorial_typing_bullet_swipes),
            stringResource(R.string.flux_tutorial_typing_bullet_learn),
            stringResource(R.string.flux_tutorial_typing_bullet_undo),
            stringResource(R.string.flux_tutorial_typing_bullet_private),
            stringResource(R.string.flux_tutorial_typing_bullet_paste),
            stringResource(R.string.flux_tutorial_typing_bullet_spell),
            stringResource(R.string.flux_tutorial_typing_bullet_punctuation),
            stringResource(R.string.flux_tutorial_typing_bullet_shift),
            stringResource(R.string.flux_tutorial_typing_bullet_language)
        ),
        buttonText = stringResource(R.string.flux_tutorial_typing_button),
        onButtonClick = { openTutorialSetting(context, "auto_correction.spell_checker") },
        modifier = modifier
    )
}

@Composable
fun FluxTutorialAppsPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_apps_title),
        description = stringResource(R.string.flux_tutorial_apps_description),
        icon = Icons.Filled.Apps,
        tint = MaterialTheme.colorScheme.secondary,
        bullets = listOf(
            stringResource(R.string.flux_tutorial_apps_bullet_shortcuts),
            stringResource(R.string.flux_tutorial_apps_bullet_enter),
            stringResource(R.string.flux_tutorial_apps_bullet_exact),
            stringResource(R.string.flux_tutorial_apps_bullet_terminal),
            stringResource(R.string.flux_tutorial_apps_bullet_launcher),
            stringResource(R.string.flux_tutorial_apps_bullet_search),
            stringResource(R.string.flux_tutorial_apps_bullet_updates)
        ),
        buttonText = stringResource(R.string.flux_tutorial_apps_button),
        onButtonClick = { openTutorialSetting(context, "main.app_shortcuts") },
        modifier = modifier
    )
}

/**
 * Extras that need something from you (a permission, or another app), so they start off: each
 * can be set up here, or later in Settings.
 */
@Composable
fun FluxTutorialExtrasPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val codesOn = remember(refresh) {
        SettingsManager.getOneTimeCodesEnabled(context) && SettingsManager.hasNotificationAccess(context)
    }
    val niagaraInstalled = remember { context.packageManager.getLaunchIntentForPackage("bitpit.launcher") != null }
    // Termux: the permission to run your ~/.shortcuts scripts from the quick launcher
    val termuxInstalled = remember {
        runCatching { context.packageManager.getApplicationInfo(it.palsoftware.pastiera.shortcuts.UserShortcuts.TERMUX_PACKAGE, 0) }.isSuccess
    }
    val termuxAllowed = remember(refresh) { it.palsoftware.pastiera.shortcuts.TermuxScripts.available(context) }
    val termuxPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) it.palsoftware.pastiera.shortcuts.TermuxScripts.refresh(context)
        refresh++
    }
    val niagaraOn = remember(refresh) {
        SettingsManager.getQuickLauncherBehavior(context) == SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA
    }
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_extras_title),
        description = stringResource(R.string.flux_tutorial_extras_description),
        icon = Icons.Filled.Tune,
        tint = MaterialTheme.colorScheme.secondary,
        bullets = emptyList(),
        buttonText = stringResource(R.string.flux_tutorial_extras_hidden_apps_button),
        onButtonClick = { openTutorialSetting(context, "hidden_apps.apps") },
        modifier = modifier,
        extraContent = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                // Installed from a file: some extras need Android's restricted settings allowed first
                // (always shown, since Android doesn't always say it's blocking until you try)
                ExtraStep(
                    title = stringResource(R.string.restricted_settings_help_title),
                    text = stringResource(R.string.restricted_settings_help_steps),
                    button = stringResource(R.string.restricted_settings_help_open),
                    enabled = true,
                    onClick = { RestrictedSettings.openAppInfo(context) }
                )
                ExtraStep(
                    title = stringResource(R.string.flux_tutorial_extras_codes_title),
                    text = stringResource(
                        if (RestrictedSettings.blocked(context)) R.string.flux_tutorial_extras_codes_restricted
                        else R.string.flux_tutorial_extras_codes_text
                    ),
                    button = stringResource(if (codesOn) R.string.flux_tutorial_extras_on else R.string.flux_tutorial_extras_codes_button),
                    enabled = !codesOn,
                    onClick = {
                        SettingsManager.setOneTimeCodesEnabled(context, true)
                        if (!SettingsManager.hasNotificationAccess(context)) RestrictedSettings.openNotificationAccess(context)
                        refresh++
                    }
                )
                if (termuxInstalled) {
                    ExtraStep(
                        title = stringResource(R.string.flux_tutorial_extras_termux_title),
                        text = stringResource(R.string.flux_tutorial_extras_termux_text),
                        button = stringResource(if (termuxAllowed) R.string.flux_tutorial_extras_on else R.string.flux_tutorial_extras_termux_button),
                        enabled = !termuxAllowed,
                        onClick = {
                            runCatching {
                                termuxPermission.launch(it.palsoftware.pastiera.shortcuts.UserShortcuts.TERMUX_RUN_COMMAND_PERMISSION)
                            }
                        }
                    )
                    // Termux's own side: a command to paste into it (other apps allowed, and set
                    // up for a phone with keys)
                    ExtraStep(
                        title = stringResource(R.string.termux_setup_title),
                        text = stringResource(R.string.termux_setup_description),
                        button = stringResource(R.string.termux_setup_button),
                        enabled = true,
                        onClick = { it.palsoftware.pastiera.shortcuts.TermuxSetup.copyAndOpen(context) }
                    )
                }
                // Shizuku: the Titan 2's keyboard light and the shortcuts that need the ADB shell
                ExtraStep(
                    title = stringResource(R.string.flux_tutorial_extras_shizuku_title),
                    text = stringResource(R.string.flux_tutorial_extras_shizuku_text),
                    button = stringResource(R.string.flux_tutorial_extras_shizuku_button),
                    enabled = true,
                    onClick = { openTutorialSetting(context, "main.root") }
                )
                if (niagaraInstalled) {
                    ExtraStep(
                        title = stringResource(R.string.flux_tutorial_extras_niagara_title),
                        text = stringResource(R.string.flux_tutorial_extras_niagara_text),
                        // Switches both ways: Niagara's search, or back to the built-in quick launcher
                        button = stringResource(
                            if (niagaraOn) R.string.flux_tutorial_extras_niagara_off_button
                            else R.string.flux_tutorial_extras_niagara_button
                        ),
                        enabled = true,
                        onClick = {
                            SettingsManager.setQuickLauncherBehavior(
                                context,
                                if (niagaraOn) SettingsManager.QUICK_LAUNCHER_BEHAVIOR_PASTIERA
                                else SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA
                            )
                            refresh++
                        }
                    )
                }
                ExtraStep(
                    title = stringResource(R.string.flux_tutorial_extras_hidden_apps_title),
                    text = stringResource(R.string.flux_tutorial_extras_hidden_apps_text),
                    button = null,
                    enabled = true,
                    onClick = {}
                )
            }
        }
    )
}

@Composable
private fun ExtraStep(title: String, text: String, button: String?, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleSmall)
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (button != null) {
            androidx.compose.material3.OutlinedButton(onClick = onClick, enabled = enabled) {
                androidx.compose.material3.Text(button)
            }
        }
    }
}

/** Making the keyboard yours: layouts, LEDs, colours, pictures and the menu bar. */
@Composable
fun FluxTutorialPersonalisePageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_personalise_title),
        description = stringResource(R.string.flux_tutorial_personalise_description),
        icon = Icons.Filled.Palette,
        tint = MaterialTheme.colorScheme.tertiary,
        bullets = buildList {
            add(stringResource(R.string.flux_tutorial_personalise_bullet_layouts))
            add(stringResource(R.string.flux_tutorial_personalise_bullet_theme))
            add(stringResource(R.string.flux_tutorial_personalise_bullet_leds))
            if (DeviceSpecific.isTitan2EliteDevice()) add(stringResource(R.string.flux_tutorial_personalise_bullet_contour))
            add(stringResource(R.string.flux_tutorial_personalise_bullet_colours))
            add(stringResource(R.string.flux_tutorial_personalise_bullet_menu))
        },
        buttonText = stringResource(R.string.flux_tutorial_personalise_button),
        onButtonClick = { openTutorialSetting(context, "led_colors.individual") },
        modifier = modifier
    )
}

/**
 * Your choices: matters of taste the recommended settings leave alone, each a switch. Shown in
 * the tutorial and after applying the recommended settings.
 */
@Composable
fun FluxTutorialChoicesPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_choices_title),
        description = stringResource(R.string.flux_tutorial_choices_description),
        icon = Icons.Filled.Tune,
        tint = MaterialTheme.colorScheme.primary,
        bullets = emptyList(),
        buttonText = stringResource(R.string.flux_tutorial_choices_button),
        onButtonClick = { openTutorialSetting(context, "main.typing") },
        modifier = modifier,
        extraContent = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                ChoiceSwitch(
                    R.string.flux_choice_auto_correct_title, R.string.flux_choice_auto_correct_text,
                    { SettingsManager.getAutoCorrectEnabled(context) }
                ) { SettingsManager.setAutoCorrectEnabled(context, it) }
                ChoiceSwitch(
                    R.string.flux_choice_double_space_title, R.string.flux_choice_double_space_text,
                    { SettingsManager.getDoubleSpaceToPeriod(context) }
                ) { SettingsManager.setDoubleSpaceToPeriod(context, it) }
                ChoiceSwitch(
                    R.string.flux_choice_emoji_suggestions_title, R.string.flux_choice_emoji_suggestions_text,
                    { SettingsManager.getEmojiSuggestionsEnabled(context) }
                ) { SettingsManager.setEmojiSuggestionsEnabled(context, it) }
                ChoiceSwitch(
                    R.string.flux_choice_gifs_title, R.string.flux_choice_gifs_text,
                    { SettingsManager.getGifsEnabled(context) }
                ) { SettingsManager.setGifsEnabled(context, it) }
                ChoiceSwitch(
                    R.string.flux_choice_emoji_layer_title, R.string.flux_choice_emoji_layer_text,
                    { SettingsManager.getEmojiKeyOpensLayer(context) }
                ) { SettingsManager.setEmojiKeyOpensLayer(context, it) }
                ChoiceSwitch(
                    R.string.emoji_layer_pages_title, R.string.emoji_layer_pages_description,
                    { SettingsManager.getEmojiLayerPages(context) }
                ) { SettingsManager.setEmojiLayerPages(context, it) }
                ChoiceSwitch(
                    R.string.symbols_pages_title, R.string.symbols_pages_description,
                    { SettingsManager.getSymbolsPages(context) }
                ) { SettingsManager.setSymbolsPages(context, it) }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    ChoiceSwitch(
                        R.string.flux_choice_wallpaper_title, R.string.flux_choice_wallpaper_text,
                        { SettingsManager.getKeyboardWallpaperColours(context) }
                    ) { SettingsManager.setKeyboardWallpaperColours(context, it) }
                }
            }
        }
    )
}

@Composable
private fun ChoiceSwitch(titleRes: Int, textRes: Int, read: () -> Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var checked by remember { androidx.compose.runtime.mutableStateOf(read()) }
    // The pager composes this page before the one beside it applies the recommended settings:
    // follow the stored value so a switch never shows a stale state
    androidx.compose.runtime.DisposableEffect(Unit) {
        val prefs = SettingsManager.getPreferences(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> checked = read() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        checked = read()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked; onChange(checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            androidx.compose.material3.Text(
                stringResource(textRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}
