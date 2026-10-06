package it.palsoftware.pastiera.settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.adb.KeyboardBacklight
import it.palsoftware.pastiera.adb.PerAppDensity
import it.palsoftware.pastiera.adb.ScreenDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.ShizukuStatus
import it.palsoftware.pastiera.apps.AppListHelper
import it.palsoftware.pastiera.apps.AppPickerDialog
import it.palsoftware.pastiera.resolveShizukuStatus

/**
 * Shizuku extras: what Flux Keyboard can do through the ADB shell, without root (Shizuku running
 * and allowing Flux Keyboard): the keyboard light, and shortcuts for keys and the quick launcher.
 */
@Composable
fun AdbSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = SettingsManager.getPreferences(context)
    var status by remember { mutableStateOf(resolveShizukuStatus()) }
    var backlightSupported by remember { mutableStateOf(false) }
    var backlightLevel by remember { mutableStateOf(100f) }
    var backlightTimeout by remember { mutableStateOf<Float?>(null) }
    // Shizuku's answer to the permission request
    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ -> status = resolveShizukuStatus() }
        runCatching { rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) } }
    }
    LaunchedEffect(status) {
        if (status != ShizukuStatus.Connected) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            // Allowed just now, or Shizuku started after the keyboard: the light starts following
            backlightSupported = KeyboardBacklight.recheck(context)
            if (backlightSupported) {
                (KeyboardBacklight.brightness() ?: KeyboardBacklight.chosen(context).takeIf { it >= 0 })?.let { backlightLevel = it.toFloat() }
                backlightTimeout = KeyboardBacklight.timeoutSeconds()?.coerceIn(1, 60)?.toFloat()
            }
        }
    }
    fun pref(key: String, default: Boolean = false) = prefs.getBoolean(key, default)
    var followScreen by remember { mutableStateOf(pref(KeyboardBacklight.KEY_FOLLOW_SCREEN)) }
    var followBrightness by remember { mutableStateOf(pref(KeyboardBacklight.KEY_FOLLOW_BRIGHTNESS)) }
    var flash by remember { mutableStateOf(pref(KeyboardBacklight.KEY_NOTIFICATION_FLASH)) }

    FluxScreenScaffold(stringResource(R.string.root_title), onBack, modifier) {
        FluxNote(
            stringResource(
                when (status) {
                    ShizukuStatus.Connected -> R.string.root_note
                    ShizukuStatus.NotAuthorized -> R.string.adb_not_allowed
                    ShizukuStatus.NotConnected -> R.string.adb_not_running
                }
            )
        )
        if (status == ShizukuStatus.NotAuthorized) {
            FluxActionRow(
                linkId = null,
                title = stringResource(R.string.adb_allow_title),
                description = stringResource(R.string.adb_allow_description)
            ) {
                runCatching { rikka.shizuku.Shizuku.requestPermission(4207) }
            }
        }
        if (status != ShizukuStatus.Connected) return@FluxScreenScaffold

        // Shizuku starting itself at boot (no root): it needs WRITE_SECURE_SETTINGS, granted here
        if (remember { it.palsoftware.pastiera.adb.ShizukuBoot.installed(context) }) {
            var bootReady by remember { mutableStateOf(it.palsoftware.pastiera.adb.ShizukuBoot.granted(context)) }
            FluxActionRow(
                linkId = "main.root.shizuku_boot",
                title = stringResource(R.string.shizuku_boot_title),
                description = stringResource(
                    if (bootReady) R.string.shizuku_boot_ready else R.string.shizuku_boot_description
                )
            ) {
                it.palsoftware.pastiera.adb.ShizukuBoot.setUp(context) { ok -> bootReady = ok }
            }
        }

        SettingsSectionDivider(stringResource(R.string.root_section_backlight))
        if (!backlightSupported) {
            FluxNote(
                stringResource(R.string.root_backlight_unsupported) + "\n\n" + stringResource(
                    when (KeyboardBacklight.problem) {
                        KeyboardBacklight.Problem.NoAnswer, KeyboardBacklight.Problem.NoShizuku, null -> R.string.root_backlight_no_answer
                        KeyboardBacklight.Problem.NoService -> R.string.root_backlight_no_service
                    }
                )
            )
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            FluxActionRow(
                linkId = null,
                icon = "\u21BB",
                title = stringResource(R.string.root_backlight_recheck_title),
                description = stringResource(R.string.root_backlight_recheck_description)
            ) {
                scope.launch { backlightSupported = withContext(Dispatchers.IO) { KeyboardBacklight.recheck(context) } }
            }
        }
        // Credit: the vendor calls the light uses were found by PhysiBoard
        if (backlightSupported) FluxNote(stringResource(R.string.root_backlight_physiboard_credit))
        // Written but not kept: the light still goes on and off with the screen, at the phone's own brightness
        if (backlightSupported && KeyboardBacklight.brightnessSupported() == false) {
            FluxNote(stringResource(R.string.root_backlight_brightness_not_kept))
        }
        if (backlightSupported && !followBrightness) {
            RootSliderRow(
                linkId = "root.backlight_level",
                title = stringResource(R.string.root_backlight_level_title),
                value = backlightLevel,
                range = 0f..100f,
                label = "${backlightLevel.toInt()}",
                onChange = { backlightLevel = it },
                onDone = { KeyboardBacklight.setChosen(context, backlightLevel.toInt()) }
            )
        }
        val timeout = backlightTimeout
        if (timeout != null && !followScreen && !followBrightness) {
            RootSliderRow(
                linkId = "root.backlight_timeout",
                title = stringResource(R.string.root_backlight_timeout_title),
                value = timeout,
                range = 1f..60f,
                label = stringResource(R.string.root_backlight_timeout_value, timeout.toInt()),
                onChange = { backlightTimeout = it },
                onDone = { KeyboardBacklight.setTimeoutSeconds(timeout.toInt()) }
            )
        }
        FluxSwitchRow(
            linkId = "root.backlight_screen",
            title = stringResource(R.string.root_backlight_screen_title),
            description = stringResource(R.string.root_backlight_screen_description),
            checked = followScreen,
            onCheckedChange = { on ->
                followScreen = on
                prefs.edit().putBoolean(KeyboardBacklight.KEY_FOLLOW_SCREEN, on).apply()
                KeyboardBacklight.start(context)
            }
        )
        FluxSwitchRow(
            linkId = "root.backlight_brightness",
            title = stringResource(R.string.root_backlight_brightness_title),
            description = stringResource(R.string.root_backlight_brightness_description),
            checked = followBrightness,
            onCheckedChange = { on ->
                followBrightness = on
                prefs.edit().putBoolean(KeyboardBacklight.KEY_FOLLOW_BRIGHTNESS, on).apply()
                KeyboardBacklight.start(context)
            }
        )
        FluxSwitchRow(
            linkId = "root.backlight_flash",
            title = stringResource(R.string.root_backlight_flash_title),
            description = stringResource(R.string.root_backlight_flash_description),
            checked = flash,
            onCheckedChange = { on -> flash = on; prefs.edit().putBoolean(KeyboardBacklight.KEY_NOTIFICATION_FLASH, on).apply() }
        )

        // Screen size presets (wm density): Default, Tablet, Desktop
        SettingsSectionDivider(stringResource(R.string.density_section))
        // The size for the whole phone: each with Apply, the one in use marked
        var base by remember { mutableStateOf(it.palsoftware.pastiera.adb.PerAppDensity.base(context)) }
        ScreenDensity.Preset.entries.forEach { preset ->
            val dpi = remember(preset) { ScreenDensity.dpiFor(context, preset) }
            DensityRow(
                // ▯ the phone, ▭ a tablet, ▬ a desktop
                icon = when (preset) {
                    ScreenDensity.Preset.DEFAULT -> "\u25AF"
                    ScreenDensity.Preset.TABLET -> "\u25AD"
                    ScreenDensity.Preset.DESKTOP -> "\u25AC"
                },
                title = stringResource(preset.titleRes),
                description = stringResource(
                    when (preset) {
                        ScreenDensity.Preset.DEFAULT -> R.string.density_default_description
                        ScreenDensity.Preset.TABLET -> R.string.density_tablet_description
                        ScreenDensity.Preset.DESKTOP -> R.string.density_desktop_description
                    },
                    dpi ?: 0
                ),
                inUse = base == preset.action
            ) {
                ScreenDensity.apply(context, preset)
                base = preset.action
            }
        }
        DensityRow(
            icon = "\u21BA",
            title = stringResource(R.string.density_reset_title),
            description = stringResource(R.string.density_reset_description),
            inUse = base == it.palsoftware.pastiera.adb.PerAppDensity.BASE_RESET
        ) {
            ScreenDensity.reset(context)
            base = it.palsoftware.pastiera.adb.PerAppDensity.BASE_RESET
        }
        PerAppDensitySection()

        SettingsSectionDivider(stringResource(R.string.root_section_shortcuts))
        FluxNote(stringResource(R.string.root_shortcuts_note))
        // Where they're assigned: Key shortcuts (Modifiers & SYM), opened as a setting link
        FluxActionRow(
            linkId = null,
            icon = "\u2318",
            title = stringResource(R.string.key_shortcuts_title),
            description = stringResource(R.string.root_shortcuts_open_description)
        ) {
            context.startActivity(
                android.content.Intent(context, SettingsActivity::class.java)
                    .setData(android.net.Uri.parse("fluxkeyboard://setting/${SettingLinkIds.MODIFIERS_SYM_SHORTCUTS}"))
            )
        }
    }
}

/** A title and a slider with its value. */
/** A screen size for the whole phone: Apply sets it; the one in use says so instead. */
@Composable
private fun DensityRow(icon: String, title: String, description: String, inUse: Boolean, onApply: () -> Unit) {
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Text(
                icon,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                androidx.compose.material3.Text(
                    description,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (inUse) {
                androidx.compose.material3.OutlinedButton(onClick = onApply, enabled = false) {
                    androidx.compose.material3.Text(stringResource(R.string.density_in_use))
                }
            } else {
                androidx.compose.material3.Button(onClick = onApply) {
                    androidx.compose.material3.Text(stringResource(R.string.density_apply))
                }
            }
        }
    }
}

@Composable
private fun RootSliderRow(
    linkId: String,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit
) {
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId)) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = onChange,
                onValueChangeFinished = onDone,
                valueRange = range
            )
        }
    }
}

/**
 * Screen size per app: the apps given their own preset, each changed or removed with a tap, and
 * any app added from a searchable list.
 */
@Composable
private fun PerAppDensitySection() {
    val context = LocalContext.current
    val presets = ScreenDensity.Preset.entries
    var apps by remember { mutableStateOf(PerAppDensity.apps(context)) }
    var showPicker by remember { mutableStateOf(false) }
    // The app whose preset is being chosen
    var choosing by remember { mutableStateOf<String?>(null) }
    val names = remember { AppListHelper.getInstalledApps(context).associate { it.packageName to it.appName } }
    SettingsSectionDivider(stringResource(R.string.density_per_app_section))
    FluxNote(stringResource(R.string.density_per_app_note))
    apps.toSortedMap(compareBy { names[it] ?: it }).forEach { (pkg, action) ->
        val preset = ScreenDensity.Preset.byAction(action)
        FluxActionRow(
            linkId = null,
            title = names[pkg] ?: pkg,
            description = preset?.let { p -> stringResource(p.titleRes) } ?: action,
            appPackage = pkg
        ) { choosing = pkg }
    }
    FluxActionRow(
        linkId = null,
        icon = "+",
        title = stringResource(R.string.density_per_app_add),
        description = stringResource(R.string.density_per_app_add_description)
    ) { showPicker = true }
    if (showPicker) {
        AppPickerDialog(
            excludePackages = apps.keys + context.packageName,
            onAppSelected = { app -> showPicker = false; choosing = app.packageName },
            onDismiss = { showPicker = false }
        )
    }
    choosing?.let { pkg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosing = null },
            title = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    FluxAppIcon(pkg)
                    androidx.compose.material3.Text(names[pkg] ?: pkg)
                }
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    presets.forEach { preset ->
                        androidx.compose.material3.TextButton(onClick = {
                            PerAppDensity.setApp(context, pkg, preset)
                            apps = PerAppDensity.apps(context)
                            choosing = null
                        }) { androidx.compose.material3.Text(stringResource(preset.titleRes)) }
                    }
                }
            },
            confirmButton = {
                if (pkg in apps) {
                    androidx.compose.material3.TextButton(onClick = {
                        PerAppDensity.setApp(context, pkg, null)
                        apps = PerAppDensity.apps(context)
                        choosing = null
                    }) { androidx.compose.material3.Text(stringResource(R.string.density_per_app_remove)) }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { choosing = null }) {
                    androidx.compose.material3.Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
