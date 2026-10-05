package it.palsoftware.pastiera

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.data.mappings.EmojiLayerProfile
import it.palsoftware.pastiera.data.mappings.EmojiLayerProfiles
import org.json.JSONObject

/**
 * Emoji layer profiles: apply a situation's emoji to the emoji layer, keep your own, or let the
 * layer follow the app you're in.
 */
@Composable
fun EmojiLayerProfilesScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val active = remember(revision) { EmojiLayerProfiles.activeId(context) }
    val custom = remember(revision) { EmojiLayerProfiles.custom(context) }
    var switchByApp by remember { mutableStateOf(EmojiLayerProfiles.switchByApp(context)) }
    var managing by remember { mutableStateOf<EmojiLayerProfile?>(null) }
    var naming by remember { mutableStateOf<EmojiLayerProfile?>(null) }
    var savingCurrent by remember { mutableStateOf(false) }

    fun apply(profile: EmojiLayerProfile) {
        EmojiLayerProfiles.apply(context, profile)
        revision++
        Toast.makeText(context, context.getString(R.string.emoji_profiles_applied, profile.name), Toast.LENGTH_SHORT).show()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported = runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            EmojiLayerProfiles.fromJson(JSONObject(text), newId = true)
        }.getOrNull()
        if (imported == null || imported.mappings.isEmpty()) {
            Toast.makeText(context, R.string.emoji_profiles_import_failed, Toast.LENGTH_LONG).show()
        } else {
            EmojiLayerProfiles.saveCustom(context, imported)
            revision++
        }
    }

    FluxScreenScaffold(stringResource(R.string.emoji_profiles_title), onBack, modifier) {
        FluxNote(stringResource(R.string.emoji_profiles_intro))
        FluxSwitchRow(
            linkId = SettingLinkIds.EMOJI_PROFILES_SWITCH_BY_APP,
            title = stringResource(R.string.emoji_profiles_switch_by_app_title),
            description = stringResource(R.string.emoji_profiles_switch_by_app_description),
            checked = switchByApp,
            onCheckedChange = {
                switchByApp = it
                EmojiLayerProfiles.setSwitchByApp(context, it)
            }
        )

        SettingsSectionDivider(stringResource(R.string.emoji_profiles_situations))
        EmojiLayerProfiles.BUILT_IN.forEach { profile ->
            FluxActionRow(
                linkId = null,
                title = profileTitle(profile, active),
                description = preview(profile),
                onClick = { apply(profile) }
            )
        }

        SettingsSectionDivider(stringResource(R.string.emoji_profiles_yours))
        FluxActionRow(
            linkId = SettingLinkIds.EMOJI_PROFILES_SAVE_CURRENT,
            title = stringResource(R.string.emoji_profiles_save_current_title),
            description = stringResource(R.string.emoji_profiles_save_current_description),
            onClick = { savingCurrent = true }
        )
        FluxActionRow(
            linkId = null,
            title = stringResource(R.string.emoji_profiles_import_title),
            description = stringResource(R.string.emoji_profiles_import_description),
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )
        custom.forEach { profile ->
            FluxActionRow(
                linkId = null,
                title = profileTitle(profile, active),
                description = preview(profile),
                onClick = { managing = profile }
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (savingCurrent) {
        NameDialog(
            initial = context.getString(R.string.emoji_profiles_new_name, custom.size + 1),
            onDismiss = { savingCurrent = false },
            onSave = { name ->
                val current = SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
                    ?: it.palsoftware.pastiera.data.mappings.KeyMappingLoader.loadSymKeyMappings(context.assets)
                val created = EmojiLayerProfiles.createCustom(context, name, current)
                EmojiLayerProfiles.apply(context, created)
                savingCurrent = false
                revision++
            }
        )
    }
    naming?.let { profile ->
        NameDialog(
            initial = profile.name,
            onDismiss = { naming = null },
            onSave = { name ->
                EmojiLayerProfiles.saveCustom(context, profile.copy(name = name))
                naming = null
                revision++
            }
        )
    }
    managing?.let { profile ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(profile.name) },
            text = {
                Column {
                    Text(preview(profile))
                    listOf(
                        R.string.emoji_profiles_apply to { apply(profile) },
                        R.string.emoji_profiles_update to {
                            val current = SettingsManager.getSymMappings(context)
                            if (current.isNotEmpty()) {
                                EmojiLayerProfiles.saveCustom(context, profile.copy(mappings = current))
                                revision++
                            }
                        },
                        R.string.emoji_profiles_rename to { naming = profile },
                        R.string.emoji_profiles_share to {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, profile.name)
                                putExtra(Intent.EXTRA_TEXT, EmojiLayerProfiles.toJson(profile).toString(2))
                            }
                            context.startActivity(Intent.createChooser(send, profile.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        R.string.emoji_profiles_delete to {
                            EmojiLayerProfiles.deleteCustom(context, profile.id)
                            revision++
                        }
                    ).forEach { (label, action) ->
                        TextButton(onClick = { managing = null; action() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { managing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emoji_profiles_name_title)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun profileTitle(profile: EmojiLayerProfile, activeId: String?): String {
    val name = when (profile.id) {
        "everyday" -> stringResource(R.string.emoji_profile_everyday)
        "chatting" -> stringResource(R.string.emoji_profile_chatting)
        "work" -> stringResource(R.string.emoji_profile_work)
        "social" -> stringResource(R.string.emoji_profile_social)
        "love" -> stringResource(R.string.emoji_profile_love)
        "celebrations" -> stringResource(R.string.emoji_profile_celebrations)
        "food" -> stringResource(R.string.emoji_profile_food)
        "travel" -> stringResource(R.string.emoji_profile_travel)
        "gaming" -> stringResource(R.string.emoji_profile_gaming)
        else -> profile.name
    }.takeIf { profile.builtIn } ?: profile.name
    return if (profile.id == activeId) "$name · " + stringResource(R.string.emoji_profiles_in_use) else name
}

/** The top two rows, as they sit on the keys. */
private fun preview(profile: EmojiLayerProfile): String =
    EmojiLayerProfiles.KEYS.take(19).mapNotNull { profile.mappings[it] }.joinToString(" ")
