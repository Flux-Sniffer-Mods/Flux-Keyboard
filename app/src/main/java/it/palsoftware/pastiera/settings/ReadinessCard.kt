package it.palsoftware.pastiera.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.modules.FluxModule
import it.palsoftware.pastiera.modules.ModuleReadiness
import it.palsoftware.pastiera.modules.ModuleReadiness.Need
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A module's ready checklist, at the top of its pages: only what it still needs, each a tap from
 * its fix; nothing at all once it's ready. Checked again on coming back and every few seconds
 * (the shell starts by itself, a pairing comes from the TV).
 */
@Composable
internal fun ReadinessCard(module: FluxModule, modifier: Modifier = Modifier, readyText: String? = null) {
    val context = LocalContext.current
    var needs by remember(module) { mutableStateOf(ModuleReadiness.needs(context, module)) }
    var check by remember { mutableStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(module, check) {
        while (true) {
            needs = withContext(Dispatchers.IO) { ModuleReadiness.needs(context, module) }
            delay(3_000)
        }
    }
    if (needs.isEmpty()) {
        // Ready: one quiet line, where the page says so
        if (readyText != null) Text(
            "\u2713 " + readyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        return
    }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
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
            needs.forEach { need -> NeedRow(module, need) { check++ } }
        }
    }
}

/** One thing still needed: what it is, why, and a tap to fix it. */
@Composable
private fun NeedRow(module: FluxModule, need: Need, onDone: () -> Unit) {
    val context = LocalContext.current
    val shellPaired = remember { runCatching { it.palsoftware.pastiera.adb.shell.ShellSetup.paired(context) }.getOrDefault(false) }
    val title = stringResource(when (need) {
        Need.KEYBOARD_ON -> R.string.enable_pastiera
        Need.KEYBOARD_CHOSEN -> R.string.readiness_keyboard_chosen
        Need.ACCESSIBILITY -> R.string.readiness_accessibility
        Need.SHELL -> R.string.root_title
        Need.OVERLAY -> R.string.readiness_overlay
        Need.NOTIFICATIONS -> R.string.readiness_notifications
        Need.PAIRED -> R.string.readiness_paired
    })
    val description = when (need) {
        Need.KEYBOARD_ON -> stringResource(R.string.readiness_keyboard_on_description)
        Need.KEYBOARD_CHOSEN -> stringResource(R.string.readiness_keyboard_chosen_description)
        Need.ACCESSIBILITY -> stringResource(R.string.readiness_accessibility_description)
        Need.SHELL -> stringResource(when (module) {
            FluxModule.STREAMING -> R.string.shell_access_for_streaming
            FluxModule.DESKTOP -> R.string.shell_access_for_desktop
            else -> R.string.shell_access_for_gaming
        }) +
            " " + stringResource(if (shellPaired) R.string.shell_access_paired else R.string.shell_access_off)
        Need.OVERLAY -> stringResource(R.string.readiness_overlay_description)
        Need.NOTIFICATIONS -> stringResource(if (module == FluxModule.STREAMING) R.string.readiness_notifications_streaming else R.string.readiness_notifications_gaming)
        Need.PAIRED -> stringResource(R.string.readiness_paired_description) +
            if (!it.palsoftware.pastiera.streaming.StreamHostService.enabled(context)) " " + stringResource(R.string.readiness_paired_start) else ""
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { fix(context, need); onDone() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("○", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

/** The fix for [need]: the page where it's switched on, or the switch itself. */
private fun fix(context: Context, need: Need) {
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    when (need) {
        Need.KEYBOARD_ON -> open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        Need.KEYBOARD_CHOSEN -> runCatching {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        Need.ACCESSIBILITY -> it.palsoftware.pastiera.RestrictedSettings.openAccessibility(context)
        Need.SHELL -> openSettingsPage(context, SettingsPage(SettingsDestination.Root))
        Need.OVERLAY -> open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        Need.NOTIFICATIONS -> open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        // Pairing starts from Moonlight: the phone answering it first
        Need.PAIRED -> {
            if (!it.palsoftware.pastiera.streaming.StreamHostService.enabled(context)) {
                runCatching { it.palsoftware.pastiera.streaming.StreamHostService.setEnabled(context, true) }
            }
            Toast.makeText(context, R.string.readiness_paired_description, Toast.LENGTH_LONG).show()
        }
    }
}
