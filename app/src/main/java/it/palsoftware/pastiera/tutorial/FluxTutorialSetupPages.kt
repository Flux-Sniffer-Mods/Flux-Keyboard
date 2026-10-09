package it.palsoftware.pastiera.tutorial

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.RestrictedSettings
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.ShizukuStatus
import it.palsoftware.pastiera.TutorialFeaturePageContent
import it.palsoftware.pastiera.adb.ShizukuBoot
import it.palsoftware.pastiera.apps.isPastieraAccessibilityServiceEnabled
import it.palsoftware.pastiera.getMinimalMode
import it.palsoftware.pastiera.getMinimalModeShowLeds
import it.palsoftware.pastiera.inputmethod.ClicksLauncherButtonAccessibilityService
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.resolveShizukuStatus
import it.palsoftware.pastiera.setMinimalMode
import it.palsoftware.pastiera.setMinimalModeShowLeds

private const val SHIZUKU_RELEASES = "https://github.com/RikkaApps/Shizuku/releases/latest"
private const val TERMUX_RELEASES = "https://github.com/termux/termux-app/releases/latest"
private const val TERMUX_BOOT_RELEASES = "https://github.com/termux/termux-boot/releases/latest"

/** A number that goes up each time the tutorial comes back to the front, to re-check statuses. */
@Composable
private fun rememberResumeCount(): Int {
    val owner = LocalLifecycleOwner.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) count++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Flux Keyboard's own accessibility page where Android has one, else the accessibility list. */
private fun openAccessibilityService(context: Context) {
    val component = ComponentName(context, ClicksLauncherButtonAccessibilityService::class.java)
    val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
    } else null
    val opened = detail != null && runCatching {
        context.startActivity(detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** A numbered step: what to do, its status once done, and its button. */
@Composable
private fun TutorialStep(
    title: String,
    text: String,
    done: String? = null,
    buttons: List<Pair<String, () -> Unit>> = emptyList()
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (done != null) {
            Text(done, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            buttons.forEach { (label, onClick) ->
                OutlinedButton(onClick = onClick) { Text(label) }
            }
        }
    }
}

/**
 * Restricted settings, the way Android makes you do it: try to switch the service on (Android
 * refuses, and only then offers the way round), allow restricted settings in App info, then
 * switch it on for real.
 */
@Composable
fun FluxTutorialPermissionsPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resumes = rememberResumeCount()
    val accessibilityOn = remember(resumes) { isPastieraAccessibilityServiceEnabled(context) }
    val blocked = remember(resumes) { RestrictedSettings.blocked(context) }
    // Tried once already: Android only offers the way round after an attempt
    var tried by remember { mutableStateOf(false) }
    val allowedText = stringResource(R.string.flux_tutorial_permissions_allowed)
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_permissions_title),
        description = stringResource(R.string.flux_tutorial_permissions_description),
        icon = Icons.Filled.Lock,
        tint = MaterialTheme.colorScheme.primary,
        bullets = emptyList(),
        buttonText = stringResource(
            if (accessibilityOn) R.string.flux_tutorial_permissions_on else R.string.flux_tutorial_permissions_open_accessibility
        ),
        buttonEnabled = !accessibilityOn,
        onButtonClick = { openAccessibilityService(context) },
        modifier = modifier,
        extraContent = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_permissions_step1_title),
                    text = stringResource(R.string.flux_tutorial_permissions_step1_text),
                    done = when {
                        accessibilityOn -> stringResource(R.string.flux_tutorial_permissions_on)
                        !blocked -> allowedText
                        else -> null
                    },
                    buttons = listOf(stringResource(R.string.flux_tutorial_permissions_open_accessibility) to {
                        tried = true
                        openAccessibilityService(context)
                    })
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_permissions_step2_title),
                    text = stringResource(R.string.flux_tutorial_permissions_step2_text),
                    done = if (accessibilityOn || !blocked) allowedText else null,
                    buttons = listOf(stringResource(R.string.flux_tutorial_permissions_open_app_info) to {
                        RestrictedSettings.openAppDetails(context)
                    })
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_permissions_step3_title),
                    text = stringResource(R.string.flux_tutorial_permissions_step3_text),
                    done = if (accessibilityOn) stringResource(R.string.flux_tutorial_permissions_on) else null,
                    buttons = if (blocked && !tried) emptyList() else listOf(
                        stringResource(R.string.flux_tutorial_permissions_open_accessibility) to { openAccessibilityService(context) }
                    )
                )
                Text(
                    stringResource(R.string.flux_tutorial_permissions_notifications),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

/** Solderina (the slim bar) or minimal mode (no bar), side by side. */
@Composable
fun FluxTutorialBarPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var minimal by remember { mutableStateOf(SettingsManager.getMinimalMode(context)) }
    var leds by remember { mutableStateOf(SettingsManager.getMinimalModeShowLeds(context)) }
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_bar_title),
        description = stringResource(R.string.flux_tutorial_bar_description),
        icon = Icons.Filled.Dashboard,
        tint = MaterialTheme.colorScheme.tertiary,
        bullets = emptyList(),
        buttonText = stringResource(R.string.flux_tutorial_bar_chosen),
        buttonEnabled = false,
        onButtonClick = {},
        modifier = modifier,
        extraContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BarChoice(
                    title = stringResource(R.string.pastierina_status_bar_buttons_title),
                    text = stringResource(R.string.flux_tutorial_bar_solderina_text),
                    chosen = !minimal
                ) {
                    minimal = false
                    SettingsManager.setMinimalMode(context, false)
                }
                BarChoice(
                    title = stringResource(R.string.minimal_mode_title),
                    text = stringResource(R.string.flux_tutorial_bar_minimal_text),
                    chosen = minimal
                ) {
                    minimal = true
                    SettingsManager.setMinimalMode(context, true)
                }
                if (minimal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                leds = !leds
                                SettingsManager.setMinimalModeShowLeds(context, leds)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.minimal_mode_show_leds_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.minimal_mode_show_leds_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = leds, onCheckedChange = {
                            leds = it
                            SettingsManager.setMinimalModeShowLeds(context, it)
                        })
                    }
                }
            }
        }
    )
}

@Composable
private fun BarChoice(title: String, text: String, chosen: Boolean, onChoose: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (chosen) 2.dp else 1.dp,
            if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChoose)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (chosen) {
                    Text(
                        stringResource(R.string.flux_tutorial_bar_chosen),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Shizuku, start to finish: install it, start it, allow Flux Keyboard, then have it start again
 * by itself after a restart (Shizuku's own Start on boot, or Termux:Boot).
 */
@Composable
fun FluxTutorialShizukuPageContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resumes = rememberResumeCount()
    var refresh by remember { mutableIntStateOf(0) }
    val installed = remember(resumes, refresh) { ShizukuBoot.installed(context) }
    val status = remember(resumes, refresh) { resolveShizukuStatus() }
    val shellRunning = remember(resumes, refresh) { it.palsoftware.pastiera.adb.shell.BuiltInShell.running() }
    val shellPaired = remember(resumes, refresh) { it.palsoftware.pastiera.adb.shell.ShellSetup.paired(context) }
    val bootGranted = remember(resumes, refresh) { installed && ShizukuBoot.granted(context) }
    val termuxInstalled = remember(resumes) {
        runCatching { context.packageManager.getApplicationInfo("com.termux", 0) }.isSuccess
    }
    val termuxBootInstalled = remember(resumes) {
        runCatching { context.packageManager.getApplicationInfo("com.termux.boot", 0) }.isSuccess
    }
    TutorialFeaturePageContent(
        title = stringResource(R.string.flux_tutorial_shizuku_title),
        description = stringResource(R.string.flux_tutorial_shizuku_description),
        icon = Icons.Filled.Terminal,
        tint = MaterialTheme.colorScheme.secondary,
        bullets = buildList {
            if (DeviceSpecific.isTitan2EliteDevice()) add(stringResource(R.string.flux_tutorial_shizuku_bullet_swipes))
            if (DeviceSpecific.isTitan2Device()) add(stringResource(R.string.flux_tutorial_shizuku_bullet_light))
            add(stringResource(R.string.flux_tutorial_shizuku_bullet_shortcuts))
        },
        buttonText = stringResource(R.string.flux_tutorial_extras_shizuku_button),
        onButtonClick = { openTutorialSettingById(context, "main.root") },
        modifier = modifier,
        extraContent = {
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (it.palsoftware.pastiera.adb.shell.ShellSetup.supported()) {
                    TutorialStep(
                        title = stringResource(R.string.flux_tutorial_shell_title),
                        text = stringResource(R.string.flux_tutorial_shell_text),
                        done = when {
                            shellRunning -> stringResource(R.string.flux_tutorial_shizuku_running)
                            shellPaired -> stringResource(R.string.flux_tutorial_shell_paired)
                            else -> null
                        },
                        buttons = if (shellRunning) emptyList() else listOf(
                            stringResource(R.string.flux_tutorial_shell_button) to {
                                openTutorialSettingById(context, "main.root.built_in_shell")
                            }
                        )
                    )
                    if (shellPaired) return@Column
                    TutorialStep(
                        title = stringResource(R.string.flux_tutorial_shell_or_shizuku_title),
                        text = stringResource(R.string.flux_tutorial_shell_or_shizuku_text)
                    )
                }
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_shizuku_install_title),
                    text = stringResource(R.string.flux_tutorial_shizuku_install_text),
                    done = if (installed) stringResource(R.string.flux_tutorial_shizuku_installed) else null,
                    buttons = listOf(stringResource(R.string.flux_tutorial_shizuku_install_button) to { openLink(context, SHIZUKU_RELEASES) })
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_shizuku_start_title),
                    text = stringResource(R.string.flux_tutorial_shizuku_start_text),
                    done = if (status != ShizukuStatus.NotConnected) stringResource(R.string.flux_tutorial_shizuku_running) else null,
                    buttons = if (!installed) emptyList() else listOf(stringResource(R.string.flux_tutorial_shizuku_start_button) to {
                        context.packageManager.getLaunchIntentForPackage(ShizukuBoot.SHIZUKU_PACKAGE)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ?.let { runCatching { context.startActivity(it) } }
                        Unit
                    })
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_shizuku_allow_title),
                    text = stringResource(R.string.flux_tutorial_shizuku_allow_text),
                    done = if (status == ShizukuStatus.Connected) stringResource(R.string.flux_tutorial_shizuku_allowed) else null,
                    buttons = if (status != ShizukuStatus.NotAuthorized) emptyList() else listOf(
                        stringResource(R.string.flux_tutorial_shizuku_allow_button) to {
                            it.palsoftware.pastiera.adb.ShizukuPermission.request(context) { refresh++ }
                        }
                    )
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_shizuku_boot_title),
                    text = stringResource(R.string.flux_tutorial_shizuku_boot_text)
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_shizuku_boot_own_title),
                    text = stringResource(R.string.flux_tutorial_shizuku_boot_own_text),
                    done = if (bootGranted) stringResource(R.string.flux_tutorial_shizuku_allowed) else null,
                    buttons = if (status != ShizukuStatus.Connected) emptyList() else listOf(
                        stringResource(R.string.flux_tutorial_shizuku_boot_own_button) to {
                            ShizukuBoot.setUp(context) { refresh++ }
                        }
                    )
                )
                TutorialStep(
                    title = stringResource(R.string.flux_tutorial_shizuku_boot_termux_title),
                    text = stringResource(R.string.flux_tutorial_shizuku_boot_termux_text),
                    buttons = buildList {
                        if (!termuxInstalled) add(stringResource(R.string.flux_tutorial_shizuku_get_termux) to { openLink(context, TERMUX_RELEASES) })
                        if (!termuxBootInstalled) add(stringResource(R.string.flux_tutorial_shizuku_get_termux_boot) to { openLink(context, TERMUX_BOOT_RELEASES) })
                        if (termuxInstalled) add(stringResource(R.string.flux_tutorial_shizuku_copy_setup) to {
                            it.palsoftware.pastiera.shortcuts.TermuxSetup.copyAndOpen(context, shizuku = true)
                        })
                    }
                )
            }
        }
    )
}
