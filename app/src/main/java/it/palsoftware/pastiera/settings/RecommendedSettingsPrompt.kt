package it.palsoftware.pastiera.settings
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.ReleaseNoteRow
import it.palsoftware.pastiera.SettingsManager

/**
 * After an update: the recommended settings that differ from yours, each as "from X to Y",
 * with Apply all or Keep mine. Shown once per version, never for looks.
 */
@Composable
fun RecommendedSettingsUpdatePrompt(version: String) {
    val context = LocalContext.current
    var changes by remember { mutableStateOf(
        if (RecommendedSettings.shouldOfferAfterUpdate(context, version)) RecommendedSettings.changes(context)
        else emptyList()
    ) }
    if (changes.isEmpty()) return
    fun close() {
        RecommendedSettings.markOffered(context, version)
        changes = emptyList()
    }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text(stringResource(R.string.recommended_update_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.recommended_update_text), modifier = Modifier.padding(bottom = 12.dp))
                RecommendedChangesList(changes)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                RecommendedSettings.apply(context)
                close()
            }) { Text(stringResource(R.string.recommended_update_apply)) }
        },
        dismissButton = {
            TextButton(onClick = { close() }) { Text(stringResource(R.string.recommended_update_later)) }
        }
    )
}

/**
 * Each recommended setting that would change, its name, from what, to what: one row each, as
 * What's new lists its changes.
 */
@Composable
internal fun RecommendedChangesList(changes: List<RecommendedSettings.Change>) {
    val context = LocalContext.current
    changes.forEach { change ->
        ReleaseNoteRow(
            text = stringResource(
                R.string.recommended_update_change,
                recommendedLabel(context, change.key),
                recommendedValue(context, change.key, change.from),
                recommendedValue(context, change.key, change.to)
            ),
            icon = Icons.Filled.CheckCircle,
            tint = MaterialTheme.colorScheme.secondary,
            prominent = false
        )
    }
}

/** A recommended setting's name, from recommended_label_<key>, or the key made readable. */
internal fun recommendedLabel(context: Context, key: String): String {
    val id = context.resources.getIdentifier("recommended_label_$key", "string", context.packageName)
    return if (id != 0) context.getString(id) else key.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** A setting's value as people read it. */
internal fun recommendedValue(context: Context, key: String, value: Any?): String = when (value) {
    null -> context.getString(R.string.recommended_value_unset)
    is Boolean -> context.getString(if (value) R.string.recommended_value_on else R.string.recommended_value_off)
    is Float -> value.toInt().toString()
    is String -> when {
        key == "pastierina_mode_override" && value == "pastierina" -> BuildConfig.COMPACT_MODE_NAME
        key == "suggestion_keys" -> when (value) {
            "off" -> context.getString(R.string.recommended_value_off)
            "ctrl_digits" -> "Ctrl+1/2/3"
            else -> "Ctrl+Shift+Q/W/E"
        }
        key.endsWith("_punctuation") -> value.toCharArray().joinToString(" ")
            .ifEmpty { context.getString(R.string.recommended_value_off) }
        key == "trackpad_provider" -> if (value == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU) "Shizuku" else "Android"
        value.startsWith("[") -> value.trim('[', ']').replace("\"", "").replace(",", ", ")
        else -> value.replace(",", ", ")
    }
    else -> value.toString()
}
