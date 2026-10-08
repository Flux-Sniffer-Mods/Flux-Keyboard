package it.palsoftware.pastiera.settings

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.apps.AppPickerDialog
import it.palsoftware.pastiera.apps.InstalledApp
import it.palsoftware.pastiera.gaming.GameAction
import it.palsoftware.pastiera.gaming.GameLibrary
import it.palsoftware.pastiera.gaming.GameProfile
import it.palsoftware.pastiera.gaming.GameProfiles
import it.palsoftware.pastiera.gaming.GameStyle
import it.palsoftware.pastiera.gaming.TrackpadRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gaming mode: its switches, the game profiles, games found in game launchers, and each
 * profile's keys and trackpad.
 */
@Composable
fun GameModeScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(GameProfiles.all(context)) }
    var editing by remember { mutableStateOf<GameProfile?>(null) }
    var enabled by remember { mutableStateOf(GameProfiles.enabled(context)) }
    var portrait by remember { mutableStateOf(GameProfiles.keepPortrait(context)) }
    var picking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    fun refresh() { profiles = GameProfiles.all(context) }

    editing?.let { profile ->
        GameProfileEditor(profile, onDone = { saved ->
            if (saved != null) GameProfiles.save(context, saved)
            editing = null
            refresh()
        }, onDelete = {
            GameProfiles.delete(context, profile.id)
            editing = null
            refresh()
        })
        return
    }

    FluxScreenScaffold(stringResource(R.string.game_mode_title), onBack, modifier) {
        FluxNote(stringResource(R.string.game_mode_note))
        FluxSwitchRow(
            linkId = "main.root.game_mode",
            title = stringResource(R.string.game_mode_title),
            description = stringResource(R.string.game_mode_switch_description),
            checked = enabled
        ) {
            enabled = it
            GameProfiles.setEnabled(context, it)
        }
        FluxSwitchRow(
            linkId = null,
            title = stringResource(R.string.game_mode_portrait_title),
            description = stringResource(R.string.game_mode_portrait_description),
            checked = portrait
        ) {
            portrait = it
            GameProfiles.setKeepPortrait(context, it)
        }
        SettingsSectionDivider(stringResource(R.string.game_mode_profiles))
        profiles.forEach { profile ->
            FluxActionRow(
                linkId = null,
                title = profile.name,
                description = profile.style.label + " · " +
                    profile.packages.joinToString { pkg -> appLabel(context, pkg) }.ifEmpty { stringResource(R.string.game_mode_no_apps) }
            ) { editing = profile }
        }
        FluxActionRow(
            linkId = null,
            icon = "+",
            title = stringResource(R.string.game_mode_add),
            description = stringResource(R.string.game_mode_add_description)
        ) { picking = true }
        FluxActionRow(
            linkId = null,
            icon = "⇩",
            title = stringResource(R.string.game_mode_import),
            description = stringResource(R.string.game_mode_import_description)
        ) { importing = true }
    }

    if (picking) {
        AppPickerDialog(
            onAppSelected = { app: InstalledApp ->
                picking = false
                editing = GameProfiles.newProfile(app.appName, GameStyle.GAMEPAD, setOf(app.packageName))
            },
            onDismiss = { picking = false }
        )
    }
    if (importing) {
        GameImportDialog(onPicked = { name, pkg ->
            importing = false
            editing = GameProfiles.newProfile(name, GameStyle.GAMEPAD, setOf(pkg))
        }, onDismiss = { importing = false })
    }
}

private fun appLabel(context: android.content.Context, pkg: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

/** The games of the game launchers installed, one tap making a profile for it. */
@Composable
private fun GameImportDialog(onPicked: (String, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        games = withContext(Dispatchers.IO) {
            GameLibrary.launchers(context).flatMap { launcher ->
                GameLibrary.games(launcher.packageName).map { it to launcher.packageName } +
                    // The launcher itself, for a profile covering all its games
                    listOf(launcher.appName to launcher.packageName)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_mode_import)) },
        text = {
            Column {
                val list = games
                when {
                    list == null -> Text(stringResource(R.string.game_mode_import_reading))
                    list.isEmpty() -> Text(stringResource(R.string.game_mode_import_none))
                    else -> list.forEach { (name, pkg) ->
                        Text(
                            "$name  (${appLabel(context, pkg)})",
                            modifier = Modifier.fillMaxWidth().clickable { onPicked(name, pkg) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** One profile: its name, style, apps, trackpad halves and every key's action. */
@Composable
private fun GameProfileEditor(profile: GameProfile, onDone: (GameProfile?) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(profile) }
    var addingApp by remember { mutableStateOf(false) }
    FluxScreenScaffold(current.name, { onDone(current) }, Modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { current = current.copy(name = it) },
                label = { Text(stringResource(R.string.game_mode_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceRow(stringResource(R.string.game_mode_style), current.style.label, GameStyle.entries.map { it.label }) { index ->
                val style = GameStyle.entries[index]
                if (style != current.style) {
                    val fresh = GameProfiles.newProfile(current.name, style, current.packages)
                    current = fresh.copy(id = current.id)
                }
            }
            ChoiceRow(stringResource(R.string.game_mode_left_half), current.leftHalf.label, TrackpadRole.entries.map { it.label }) {
                current = current.copy(leftHalf = TrackpadRole.entries[it])
            }
            ChoiceRow(stringResource(R.string.game_mode_right_half), current.rightHalf.label, TrackpadRole.entries.map { it.label }) {
                current = current.copy(rightHalf = TrackpadRole.entries[it])
            }
            Text(stringResource(R.string.game_mode_apps), style = MaterialTheme.typography.titleMedium)
            current.packages.forEach { pkg ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(appLabel(context, pkg), modifier = Modifier.weight(1f))
                    TextButton(onClick = { current = current.copy(packages = current.packages - pkg) }) {
                        Text(stringResource(R.string.game_mode_remove))
                    }
                }
            }
            TextButton(onClick = { addingApp = true }) { Text(stringResource(R.string.game_mode_add_app)) }
            Text(stringResource(R.string.game_mode_keys), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.game_mode_keys_note), style = MaterialTheme.typography.bodySmall)
            val asIs = stringResource(R.string.game_mode_key_as_is)
            GameProfiles.REMAPPABLE_KEYS.forEach { key ->
                val name = KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_").replace("_LEFT", "").replace("DEL", "BACKSPACE")
                ChoiceRow(name, current.keys[key]?.label ?: asIs, listOf(asIs) + GameAction.entries.map { it.label }) { index ->
                    current = current.copy(
                        keys = if (index == 0) current.keys - key else current.keys + (key to GameAction.entries[index - 1])
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { onDone(current) }) { Text(stringResource(R.string.game_mode_save)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.game_mode_delete)) }
            }
        }
    }
    if (addingApp) {
        AppPickerDialog(
            onAppSelected = { app ->
                addingApp = false
                current = current.copy(packages = current.packages + app.packageName)
            },
            onDismiss = { addingApp = false },
            excludePackages = current.packages
        )
    }
}

/** A label and its value, the value picked from a menu. */
@Composable
private fun ChoiceRow(title: String, value: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onPick(index) })
            }
        }
    }
}
