package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.data.mappings.CustomDeviceSymProfile
import it.palsoftware.pastiera.data.mappings.CustomDeviceSymProfiles
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.json.JSONObject

/**
 * Your own Device SYM profiles: start blank or from a curated profile, choose which keyboards it
 * replaces, set what each key types, and export or import it. Custom profiles are kept with the
 * other settings, so Pastiera's backup includes them.
 */
@Composable
fun DeviceSymLayerEditorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // Bumped after every change, so the lists re-read the stored profiles
    var revision by remember { mutableIntStateOf(0) }
    var openProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = remember(openProfileId, revision) { openProfileId?.let { CustomDeviceSymProfiles.get(context, it) } }

    if (profile != null) {
        DeviceSymProfileScreen(
            modifier = modifier,
            profile = profile,
            onChanged = { revision++ },
            onBack = { openProfileId = null }
        )
        return
    }

    val profiles = remember(revision) { CustomDeviceSymProfiles.all(context) }
    var cloning by remember { mutableStateOf(false) }
    var choosingLayer by remember { mutableStateOf(false) }
    var curated by remember { mutableStateOf<String?>(null) }
    val choice = remember(revision) { CustomDeviceSymProfiles.choice(context) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            CustomDeviceSymProfiles.fromJson(JSONObject(text), newId = true)
        }.getOrNull()
        if (imported == null) {
            Toast.makeText(context, R.string.alt_key_editor_import_failed, Toast.LENGTH_LONG).show()
        } else {
            CustomDeviceSymProfiles.save(context, imported)
            revision++
            openProfileId = imported.id
        }
    }

    FluxScreenScaffold(stringResource(R.string.alt_key_editor_title), onBack, modifier) {
        FluxNote(stringResource(R.string.alt_key_editor_intro))
        FluxActionRow(
            linkId = "hardware.alt_editor.in_use",
            title = stringResource(R.string.alt_key_editor_in_use_title),
            description = choiceLabel(choice, profiles),
            onClick = { choosingLayer = true }
        )

        SettingsSectionDivider(stringResource(R.string.alt_key_editor_curated_section))
        CustomDeviceSymProfiles.BUNDLED.forEach { id ->
            FluxActionRow(
                linkId = null,
                title = bundledProfileLabel(id) +
                    if (choice == id) " · " + stringResource(R.string.alt_key_editor_in_use_badge) else "",
                description = remember(id) { curatedPreview(context, id) },
                onClick = { curated = id }
            )
        }

        SettingsSectionDivider(stringResource(R.string.alt_key_editor_create_section))
        FluxActionRow(
            linkId = "hardware.alt_editor.blank",
            title = stringResource(R.string.alt_key_editor_blank_profile_title),
            description = stringResource(R.string.alt_key_editor_blank_profile_description),
            onClick = {
                val name = context.getString(R.string.alt_key_editor_new_profile_name, profiles.size + 1)
                openProfileId = CustomDeviceSymProfiles.create(context, name).id
                revision++
            }
        )
        FluxActionRow(
            linkId = "hardware.alt_editor.clone",
            title = stringResource(R.string.alt_key_editor_clone_profile_title),
            description = stringResource(R.string.alt_key_editor_clone_profile_description),
            onClick = { cloning = true }
        )
        FluxActionRow(
            linkId = "hardware.alt_editor.import",
            title = stringResource(R.string.alt_key_editor_import_title),
            description = stringResource(R.string.alt_key_editor_transfer_description),
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )

        SettingsSectionDivider(stringResource(R.string.alt_key_editor_your_profiles_section))
        if (profiles.isEmpty()) {
            FluxNote(stringResource(R.string.alt_key_editor_no_profiles))
        }
        profiles.forEach { item ->
            FluxActionRow(
                linkId = null,
                title = item.name,
                description = profileSummary(item),
                onClick = { openProfileId = item.id }
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (choosingLayer) {
        AlertDialog(
            onDismissRequest = { choosingLayer = false },
            title = { Text(stringResource(R.string.alt_key_editor_in_use_title)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    val options = listOf(CustomDeviceSymProfiles.AUTO) + CustomDeviceSymProfiles.BUNDLED + profiles.map { it.profileRef }
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                CustomDeviceSymProfiles.setChoice(context, option)
                                choosingLayer = false
                                revision++
                            }
                        ) {
                            androidx.compose.material3.RadioButton(selected = option == choice, onClick = null)
                            Text(choiceLabel(option, profiles), modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingLayer = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    curated?.let { id ->
        val label = bundledProfileLabel(id)
        AlertDialog(
            onDismissRequest = { curated = null },
            title = { Text(label) },
            text = { Text(curatedPreview(context, id)) },
            confirmButton = {
                TextButton(onClick = {
                    CustomDeviceSymProfiles.setChoice(context, id)
                    curated = null
                    revision++
                }) { Text(stringResource(R.string.alt_key_editor_use)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val copy = CustomDeviceSymProfiles.create(
                        context,
                        context.getString(R.string.alt_key_editor_copy_name, label),
                        CustomDeviceSymProfiles.bundledMappings(context.assets, id)
                    )
                    curated = null
                    revision++
                    openProfileId = copy.id
                }) { Text(stringResource(R.string.alt_key_editor_copy_to_edit)) }
            }
        )
    }
    if (cloning) {
        AlertDialog(
            onDismissRequest = { cloning = false },
            title = { Text(stringResource(R.string.alt_key_editor_clone_profile_title)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    CustomDeviceSymProfiles.BUNDLED.forEach { bundled ->
                        val label = bundledProfileLabel(bundled)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val copy = CustomDeviceSymProfiles.create(
                                        context,
                                        context.getString(R.string.alt_key_editor_copy_name, label),
                                        CustomDeviceSymProfiles.bundledMappings(context.assets, bundled)
                                    )
                                    cloning = false
                                    revision++
                                    openProfileId = copy.id
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { cloning = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun DeviceSymProfileScreen(
    modifier: Modifier,
    profile: CustomDeviceSymProfile,
    onChanged: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var editingDetails by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save(updated: CustomDeviceSymProfile) {
        CustomDeviceSymProfiles.save(context, updated)
        onChanged()
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(CustomDeviceSymProfiles.toJson(profile).toString(2))
            }
        }.isSuccess
        Toast.makeText(
            context,
            if (written) R.string.alt_key_editor_exported else R.string.alt_key_editor_export_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    FluxScreenScaffold(profile.name, onBack, modifier) {
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.alt_key_editor_details_title),
            description = profileSummary(profile),
            onClick = { editingDetails = true }
        )

        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.alt_key_editor_use),
            description = stringResource(
                if (CustomDeviceSymProfiles.choice(context) == profile.profileRef) R.string.alt_key_editor_in_use_now
                else R.string.alt_key_editor_use_description
            ),
            onClick = {
                CustomDeviceSymProfiles.setChoice(context, profile.profileRef)
                onChanged()
            }
        )

        SettingsSectionDivider(stringResource(R.string.alt_key_editor_mappings_title))
        FluxNote(stringResource(R.string.alt_key_editor_keys_description))
        val keys = (CustomDeviceSymProfiles.EDITABLE_KEYS + profile.mappings.keys).distinct()
        keys.forEach { keyCode ->
            val value = profile.mappings[keyCode]
            Surface(Modifier.fillMaxWidth().clickable { editingKey = keyCode }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = keyLabel(keyCode),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(88.dp)
                    )
                    Text(
                        text = value ?: stringResource(R.string.alt_key_editor_key_unset),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSectionDivider(stringResource(R.string.alt_key_editor_transfer_title))
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.alt_key_editor_export_title),
            description = stringResource(R.string.alt_key_editor_export_description),
            onClick = { exportLauncher.launch("${profile.name.replace(Regex("[^A-Za-z0-9 _-]"), "")}.json") }
        )
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.alt_key_editor_share_title),
            description = stringResource(R.string.alt_key_editor_share_description),
            onClick = { shareProfile(context, profile) }
        )
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.alt_key_editor_delete_title),
            description = stringResource(R.string.alt_key_editor_delete_description),
            onClick = { confirmDelete = true }
        )
        Spacer(Modifier.height(16.dp))
    }

    if (editingDetails) {
        ProfileDetailsDialog(
            profile = profile,
            onSave = { save(it); editingDetails = false },
            onDismiss = { editingDetails = false }
        )
    }
    editingKey?.let { keyCode ->
        var text by remember(keyCode) { mutableStateOf(profile.mappings[keyCode].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text(stringResource(R.string.alt_key_editor_key_dialog_title, keyLabel(keyCode))) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(8) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.alt_key_editor_key_dialog_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val mappings = if (text.isEmpty()) profile.mappings - keyCode else profile.mappings + (keyCode to text)
                    save(profile.copy(mappings = mappings))
                    editingKey = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editingKey = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.alt_key_editor_delete_title)) },
            text = { Text(stringResource(R.string.alt_key_editor_delete_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    CustomDeviceSymProfiles.delete(context, profile.id)
                    confirmDelete = false
                    onChanged()
                    onBack()
                }) { Text(stringResource(R.string.alt_key_editor_delete_title)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** The name, and the keyboards whose Device SYM layer this profile replaces. */
@Composable
private fun ProfileDetailsDialog(
    profile: CustomDeviceSymProfile,
    onSave: (CustomDeviceSymProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var match by remember { mutableStateOf(profile.matchProfiles) }
    val detected = remember { DeviceSpecific.detectedInputProfiles().map { it.profileId.lowercase() }.toSet() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alt_key_editor_details_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.alt_key_editor_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.alt_key_editor_match_label), style = MaterialTheme.typography.bodyMedium)
                CustomDeviceSymProfiles.BUNDLED.forEach { bundled ->
                    val checked = bundled in match
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            match = if (checked) match - bundled else match + bundled
                        }
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            text = bundledProfileLabel(bundled) +
                                if (bundled.lowercase() in detected) " · " + stringResource(R.string.alt_key_editor_this_keyboard) else "",
                            modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(profile.copy(name = name.trim(), matchProfiles = match)) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun profileSummary(profile: CustomDeviceSymProfile): String {
    val keys = pluralStringResource(R.plurals.alt_key_editor_key_count, profile.mappings.size, profile.mappings.size)
    val applies = if (profile.matchProfiles.isEmpty()) {
        stringResource(R.string.alt_key_editor_applies_manually)
    } else {
        stringResource(
            R.string.alt_key_editor_applies_to,
            CustomDeviceSymProfiles.BUNDLED.filter { it in profile.matchProfiles }.map { bundledProfileLabel(it) }.joinToString(", ")
        )
    }
    return "$keys · $applies"
}

@Composable
private fun pluralStringResource(id: Int, count: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(id, count, *args)

@Composable
internal fun bundledProfileLabel(profileId: String): String = when (profileId) {
    "key2" -> stringResource(R.string.keyboard_profile_option_key2)
    "Q25" -> stringResource(R.string.keyboard_profile_option_q25)
    "titan" -> stringResource(R.string.keyboard_profile_option_titan)
    "titan2" -> stringResource(R.string.keyboard_profile_option_titan2)
    "titan2elite_qwerty" -> stringResource(R.string.keyboard_profile_option_titan2elite_qwerty)
    "mp01" -> stringResource(R.string.keyboard_profile_option_mp01)
    "clicks_razr" -> stringResource(R.string.keyboard_profile_option_clicks_razr)
    "clicks_pixel" -> stringResource(R.string.keyboard_profile_option_clicks_pixel)
    "clicks_power" -> stringResource(R.string.clicks_power_keyboard_title)
    "virtual" -> stringResource(R.string.alt_key_editor_virtual_profile)
    else -> profileId
}

private fun keyLabel(keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_GRAVE -> "` (\$)"
    KeyEvent.KEYCODE_COMMA -> ","
    KeyEvent.KEYCODE_PERIOD -> "."
    else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
}

private fun shareProfile(context: Context, profile: CustomDeviceSymProfile) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, profile.name)
        putExtra(Intent.EXTRA_TEXT, CustomDeviceSymProfiles.toJson(profile).toString(2))
    }
    context.startActivity(Intent.createChooser(send, profile.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** What the Device SYM layer in use is called: automatic (with the keyboard's profile), curated, or yours. */
@Composable
private fun choiceLabel(choice: String, profiles: List<CustomDeviceSymProfile>): String = when {
    choice == CustomDeviceSymProfiles.AUTO -> stringResource(
        R.string.alt_key_editor_in_use_auto,
        bundledProfileLabel(it.palsoftware.pastiera.data.mappings.DeviceSymProfileResolver.resolve(LocalContext.current))
    )
    choice.startsWith(CustomDeviceSymProfiles.REF_PREFIX) ->
        profiles.firstOrNull { it.profileRef == choice }?.name ?: stringResource(R.string.alt_key_editor_in_use_auto, "")
    else -> bundledProfileLabel(choice)
}

/** The top letter row of a curated profile, as it sits on the keys. */
private fun curatedPreview(context: Context, id: String): String {
    val mappings = CustomDeviceSymProfiles.bundledMappings(context.assets, id)
    return CustomDeviceSymProfiles.EDITABLE_KEYS.take(19).mapNotNull { mappings[it] }.joinToString(" ")
}
