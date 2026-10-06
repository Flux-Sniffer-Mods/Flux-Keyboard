package it.palsoftware.pastiera

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import it.palsoftware.pastiera.shortcuts.UserShortcuts
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import it.palsoftware.pastiera.apps.BuiltInShortcuts
import it.palsoftware.pastiera.settings.FluxNote
import it.palsoftware.pastiera.settings.FluxScreenScaffold
import it.palsoftware.pastiera.settings.SettingsSectionDivider

/**
 * Add a shortcut: apps that offer shortcuts for the home screen (a contact's direct dial, a
 * bookmark, a settings page) hand one over here, and it becomes a quick launcher result.
 */
class UserShortcutsActivity : LocalizedComponentActivity() {
    companion object {
        /**
         * A shortcut provider (flattened ComponentName) to open straight away, from an app's
         * long-press menu in the quick launcher; the screen closes once it answers.
         */
        const val EXTRA_PROVIDER = "provider"

        /** A built-in shortcut kind (call, message, website, termux) to start straight away. */
        const val EXTRA_BUILT_IN = "built_in"

        /** The screens [packageName] offers for making home screen shortcuts, with their names. */
        fun providersOf(context: android.content.Context, packageName: String): List<Pair<ComponentName, String>> {
            val pm = context.packageManager
            return runCatching {
                pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(packageName), 0)
                    .filter { it.activityInfo.exported }
                    .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it.loadLabel(pm).toString() }
                    .filterNot { (component, _) -> UserShortcuts.isUnsupported(context, component.flattenToString()) }
            }.getOrDefault(emptyList())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                UserShortcutsScreen(
                    onBack = { finish() },
                    directProvider = intent.getStringExtra(EXTRA_PROVIDER)?.let(ComponentName::unflattenFromString),
                    directBuiltIn = intent.getStringExtra(EXTRA_BUILT_IN)
                )
            }
        }
    }
}

private data class ShortcutProvider(val component: ComponentName, val label: String, val appName: String, val icon: Drawable?)

@Composable
private fun UserShortcutsScreen(onBack: () -> Unit, directProvider: ComponentName? = null, directBuiltIn: String? = null) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(UserShortcuts.all(context)) }
    val providers = remember {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
            .filterNot { UserShortcuts.isUnsupported(context, ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString()) }
            .map {
                ShortcutProvider(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(pm).toString(),
                    runCatching { pm.getApplicationLabel(it.activityInfo.applicationInfo).toString() }.getOrDefault(""),
                    runCatching { it.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
    }
    var pendingPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingComponent by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val packageName = pendingPackage ?: return@rememberLauncherForActivityResult
        pendingPackage = null
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            if (directProvider != null) onBack()
            return@rememberLauncherForActivityResult
        }
        val added = UserShortcuts.addFromResult(context, packageName, result.data)
        if (added == null) {
            // It pins to the home screen instead: not offered again
            pendingComponent?.let { UserShortcuts.markUnsupported(context, it) }
            Toast.makeText(context, R.string.user_shortcuts_unsupported, Toast.LENGTH_LONG).show()
        } else {
            shortcuts = UserShortcuts.all(context)
            Toast.makeText(context, context.getString(R.string.user_shortcuts_added, added.label), Toast.LENGTH_SHORT).show()
        }
        // Opened from the quick launcher for one app: back to where you were
        if (directProvider != null) onBack()
    }
    var directLaunched by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(directProvider) {
        if (directProvider != null && !directLaunched) {
            directLaunched = true
            pendingPackage = directProvider.packageName
            pendingComponent = directProvider.flattenToString()
            runCatching { picker.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(directProvider)) }
                .onFailure {
                    pendingPackage = null
                    Toast.makeText(context, R.string.user_shortcuts_unsupported, Toast.LENGTH_LONG).show()
                    onBack()
                }
        }
    }

    // The same page layout as every other settings page
    FluxScreenScaffold(stringResource(R.string.user_shortcuts_title), onBack, Modifier) {
        FluxNote(stringResource(R.string.user_shortcuts_intro))
        if (shortcuts.isNotEmpty()) {
            SettingsSectionDivider(stringResource(R.string.user_shortcuts_yours))
            shortcuts.forEach { shortcut ->
                val appName = remember(shortcut.packageName) {
                    runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(shortcut.packageName, 0)
                        ).toString()
                    }.getOrDefault(shortcut.packageName)
                }
                val icon = remember(shortcut.id) { UserShortcuts.icon(context, shortcut) }
                ShortcutRow(icon = icon, title = shortcut.label, description = appName, onClick = null) {
                    IconButton(onClick = {
                        UserShortcuts.remove(context, shortcut.id)
                        shortcuts = UserShortcuts.all(context)
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.user_shortcuts_remove, shortcut.label))
                    }
                }
            }
        }
        SettingsSectionDivider(stringResource(R.string.user_shortcuts_builtin))
        BuiltInShortcuts(
            row = { icon, title, description, onClick -> ShortcutRow(icon = icon, title = title, description = description, onClick = onClick) },
            onAdded = { shortcuts = UserShortcuts.all(context) },
            direct = directBuiltIn,
            // Opened from the quick launcher for one app: back to where you were
            onDirectDone = onBack
        )
        SettingsSectionDivider(stringResource(R.string.user_shortcuts_add_from))
        if (providers.isEmpty()) {
            FluxNote(stringResource(R.string.user_shortcuts_none))
        }
        providers.forEach { provider ->
            ShortcutRow(icon = provider.icon, title = provider.label, description = provider.appName, onClick = {
                pendingPackage = provider.component.packageName
                pendingComponent = provider.component.flattenToString()
                runCatching {
                    picker.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setComponent(provider.component))
                }.onFailure {
                    pendingPackage = null
                    Toast.makeText(context, R.string.user_shortcuts_unsupported, Toast.LENGTH_LONG).show()
                }
            })
        }
    }
}

/** A settings row with an icon: title and description as on the other pages, and an optional action at the end. */
@Composable
private fun ShortcutRow(
    icon: Drawable?,
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ShortcutIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
    }
}

@Composable
private fun ShortcutIcon(icon: Drawable?) {
    val bitmap = remember(icon) { icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() } }
    Box(modifier = Modifier.size(36.dp)) {
        if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(36.dp))
    }
}
